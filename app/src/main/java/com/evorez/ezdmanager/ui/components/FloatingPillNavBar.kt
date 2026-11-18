package com.evorez.ezdmanager.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.model.MainTab
import com.evorez.ezdmanager.model.ToolId
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private fun tabIcons(tab: MainTab): Pair<ImageVector, ImageVector> = when (tab) {
    MainTab.HOME -> Icons.Rounded.Home to Icons.Outlined.Home
    MainTab.TOOLS -> Icons.Rounded.Build to Icons.Outlined.Build
    MainTab.APPS -> Icons.Rounded.Apps to Icons.Outlined.Apps
    MainTab.SETTINGS -> Icons.Rounded.Settings to Icons.Outlined.Settings
}

fun pinnedToolIcon(tool: ToolId): ImageVector = when (tool) {
    ToolId.CPU_GPU_CONTROL -> Icons.Rounded.Memory
    ToolId.ZRAM_VM -> Icons.Rounded.SdStorage
    ToolId.KERNEL_TWEAKS -> Icons.Rounded.Tune
    ToolId.DISPLAY -> Icons.Rounded.DisplaySettings
    ToolId.TOUCH -> Icons.Rounded.TouchApp
    ToolId.NETWORK -> Icons.Rounded.Dns
    ToolId.AUDIO_ENHANCER -> Icons.Rounded.GraphicEq
    ToolId.BATTERY_SAVING -> Icons.Rounded.BatterySaver
    ToolId.FAST_CHARGE -> Icons.Rounded.Bolt
    ToolId.DEVICE_SPOOF -> Icons.Rounded.Smartphone
    ToolId.PROFILES -> Icons.Rounded.Speed
    ToolId.MODULES -> Icons.Rounded.Extension
    ToolId.BUILD_PROP -> Icons.Rounded.EditNote
    ToolId.BOOT_GUARD -> Icons.Rounded.Shield
    ToolId.EZD_BACKUP -> Icons.Rounded.SettingsBackupRestore
    ToolId.APP_BACKUP -> Icons.Rounded.InstallMobile
    ToolId.ROOT_TERMINAL -> Icons.Rounded.Terminal
}

/**
 * Floating Pill Navigation Bar driven strictly by a single [pagerState]:
 * - Bottom tabs: Home, Tools, Apps, Settings.
 * - Real-time finger-follow slide via pointerInput + detectHorizontalDragGestures linked directly
 *   to PagerState.scroll(MutatePriority.UserInput) so indicator and HorizontalPager animate in the same frame.
 * - Soft glare / magnifier lens highlight (no circular bubbles).
 * - Spring snap on release & fast-fling page step, light haptic tick on tab boundary crossing.
 * - Pinned tool shortcuts bar docked inside the pill when tools are dropped onto it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingPillNavBar(
    pagerState: PagerState,
    backdropLayer: GraphicsLayer? = null,
    opaque: Boolean = false,
    pinnedTools: List<ToolId> = emptyList(),
    isDropTargetHovered: Boolean = false,
    onPillBoundsChanged: (Rect) -> Unit = {},
    onOpenPinnedTool: (ToolId) -> Unit = {},
    onRemovePinnedTool: (ToolId) -> Unit = {},
    onMovePinnedTool: (ToolId, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tabs = MainTab.entries
    val pillShape = RoundedCornerShape(32.dp)
    val isLite = visuals.isLiteMode

    var pillPosInRoot by remember { mutableStateOf(Offset.Zero) }
    var isDraggingPill by remember { mutableStateOf(false) }
    var fingerHoverTabIdx by remember { mutableStateOf<Int?>(null) }

    val clampedFraction by remember(pagerState, tabs.size) {
        derivedStateOf {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, tabs.lastIndex.toFloat())
        }
    }

    val hoverScale by animateFloatAsState(
        targetValue = if (isDropTargetHovered) 1.03f else 1f,
        animationSpec = ColorOsMotion.fluidSpring(),
        label = "pillDropScale"
    )

    val glassRenderEffect = remember(visuals.blurAllowed, visuals.blurIntensity, density, opaque) {
        if (opaque || !visuals.blurAllowed || visuals.blurIntensity <= 0.02f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else {
            val radiusPx = with(density) { (20f * visuals.blurIntensity).dp.toPx() }.coerceAtLeast(0.5f)
            val blur = AndroidRenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            val satMatrix = ColorMatrix().apply { setSaturation(1.45f) }
            AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(satMatrix), blur)
                .asComposeRenderEffect()
        }
    }

    val pillBorderBrush = remember(visuals.accent, visuals.isDark, isLite, isDropTargetHovered) {
        if (isDropTargetHovered) {
            Brush.horizontalGradient(listOf(visuals.accent, visuals.accent.copy(alpha = 0.85f), visuals.accent))
        } else if (isLite) {
            Brush.horizontalGradient(listOf(visuals.glassBorder, visuals.glassBorder))
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    visuals.accent.copy(alpha = 0.62f),
                    if (visuals.isDark) Color.White.copy(alpha = 0.24f) else Color(0x551E293B),
                    visuals.accent.copy(alpha = 0.62f)
                )
            )
        }
    }

    // Opaque enough container so scrolling content behind never bleeds through
    val pillSurfaceColor = if (isLite || opaque) {
        visuals.solidSurfaceElevated
    } else {
        visuals.solidSurfaceElevated.copy(alpha = 0.94f)
    }

    val barHeight = if (pinnedTools.isEmpty()) 68.dp else 108.dp

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .height(barHeight)
            .graphicsLayer {
                if (!isLite && !visuals.reduceMotion) {
                    scaleX = hoverScale
                    scaleY = hoverScale
                }
            }
            .onGloballyPositioned { coords ->
                pillPosInRoot = coords.positionInRoot()
                onPillBoundsChanged(coords.boundsInRoot())
            }
            .then(
                if (isLite) Modifier
                else Modifier.shadow(
                    elevation = if (isDropTargetHovered) 24.dp else 18.dp,
                    shape = pillShape,
                    ambientColor = visuals.accent.copy(alpha = if (isDropTargetHovered) 0.55f else 0.34f),
                    spotColor = Color.Black.copy(alpha = if (visuals.isDark) 0.85f else 0.38f)
                )
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = pillShape,
            color = pillSurfaceColor,
            border = BorderStroke(if (isDropTargetHovered) 1.8.dp else 1.dp, pillBorderBrush),
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(pillShape)
            ) {
                if (!isLite) {
                    if (!opaque) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .then(
                                    if (glassRenderEffect != null) {
                                        Modifier.graphicsLayer { renderEffect = glassRenderEffect }
                                    } else Modifier
                                )
                                .drawBehind {
                                    val layer = backdropLayer
                                    if (layer != null) {
                                        translate(left = -pillPosInRoot.x, top = -pillPosInRoot.y) {
                                            drawLayer(layer)
                                        }
                                    }
                                }
                        )
                    }

                    // Soft glass glare & subtle active tab lens illumination (NO circular bubbles)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val w = size.width
                        val h = size.height
                        val liveFrac = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .coerceIn(0f, tabs.lastIndex.toFloat())
                        val hPad = with(density) { 6.dp.toPx() }
                        val slotW = ((w - hPad * 2f) / tabs.size).coerceAtLeast(1f)
                        val glareCenterX = hPad + liveFrac * slotW + slotW / 2f
                        val tabRowCenterY = if (pinnedTools.isEmpty()) h / 2f else h - with(density) { 34.dp.toPx() }

                        // Gentle center-weighted tonal depth
                        val edgeColor = if (visuals.isDark) Color(18, 22, 34).copy(alpha = 0.32f) else Color(241, 245, 252).copy(alpha = 0.40f)
                        val midColor = if (visuals.isDark) Color(8, 10, 16).copy(alpha = 0.20f) else Color.White.copy(alpha = 0.52f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(edgeColor, midColor, edgeColor),
                                startX = 0f,
                                endX = w
                            )
                        )

                        // Soft glare / magnifier highlight under selected tab
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    visuals.accent.copy(alpha = if (isDraggingPill) 0.30f else 0.20f),
                                    visuals.accent.copy(alpha = 0.06f),
                                    Color.Transparent
                                ),
                                center = Offset(glareCenterX, tabRowCenterY),
                                radius = slotW * 0.92f
                            ),
                            radius = slotW * 0.92f,
                            center = Offset(glareCenterX, tabRowCenterY)
                        )

                        // Top white glass glare edge highlight
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (visuals.isDark) 0.14f else 0.60f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = h * 0.26f
                            )
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Optional Pinned Tools Drop Zone / Shortcut Strip inside top of Pill
                    if (pinnedTools.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(horizontal = 10.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pinnedTools.forEachIndexed { idx, tool ->
                                var menuOpen by remember(tool) { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        shape = CircleShape,
                                        color = visuals.accent.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, visuals.accent.copy(alpha = 0.38f)),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .clip(CircleShape)
                                            .combinedClickable(
                                                onClick = { onOpenPinnedTool(tool) },
                                                onLongClick = { menuOpen = true }
                                            )
                                            .testTag("pinned_pill_tool_${tool.name}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = pinnedToolIcon(tool),
                                                contentDescription = tool.title,
                                                tint = visuals.accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = tool.title,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        if (idx > 0) {
                                            DropdownMenuItem(
                                                text = { Text("Move Left") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) },
                                                onClick = {
                                                    menuOpen = false
                                                    onMovePinnedTool(tool, -1)
                                                }
                                            )
                                        }
                                        if (idx < pinnedTools.lastIndex) {
                                            DropdownMenuItem(
                                                text = { Text("Move Right") },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                                                onClick = {
                                                    menuOpen = false
                                                    onMovePinnedTool(tool, +1)
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Remove Shortcut") },
                                            leadingIcon = { Icon(Icons.Rounded.Close, null) },
                                            onClick = {
                                                menuOpen = false
                                                onRemovePinnedTool(tool)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = visuals.glassBorder
                        )
                    }

                    // Main 4-Tab Finger-Follow Interactive Strip
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .pointerInput(tabs.size, pagerState) {
                                var dragChannel: Channel<Float>? = null
                                var dragJob: kotlinx.coroutines.Job? = null
                                val velocityTracker = VelocityTracker()
                                var lastCrossedTab = pagerState.currentPage

                                detectHorizontalDragGestures(
                                    onDragStart = { startOffset ->
                                        isDraggingPill = true
                                        velocityTracker.resetTracking()
                                        val slotW = (size.width.toFloat() / tabs.size).coerceAtLeast(1f)
                                        fingerHoverTabIdx = (startOffset.x / slotW).toInt().coerceIn(0, tabs.lastIndex)
                                        lastCrossedTab = clampedFraction.roundToInt().coerceIn(0, tabs.lastIndex)

                                        dragJob?.cancel()
                                        dragChannel?.close()
                                        val ch = Channel<Float>(Channel.UNLIMITED)
                                        dragChannel = ch
                                        dragJob = scope.launch {
                                            pagerState.scroll(MutatePriority.UserInput) {
                                                for (deltaPx in ch) {
                                                    scrollBy(deltaPx)
                                                }
                                            }
                                        }
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                                        val slotW = (size.width.toFloat() / tabs.size).coerceAtLeast(1f)
                                        val fingerX = change.position.x.coerceIn(0f, size.width.toFloat())
                                        fingerHoverTabIdx = (fingerX / slotW).toInt().coerceIn(0, tabs.lastIndex)

                                        val targetFrac = ((fingerX - slotW * 0.5f) / slotW)
                                            .coerceIn(0f, tabs.lastIndex.toFloat())
                                        val currentFrac = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                            .coerceIn(0f, tabs.lastIndex.toFloat())
                                        val pageStepPx = (
                                            pagerState.layoutInfo.pageSize +
                                                pagerState.layoutInfo.pageSpacing
                                            ).toFloat().coerceAtLeast(size.width.toFloat())

                                        val deltaPagePx = (targetFrac - currentFrac) * pageStepPx
                                        dragChannel?.trySend(deltaPagePx)

                                        val crossed = targetFrac.roundToInt().coerceIn(0, tabs.lastIndex)
                                        if (crossed != lastCrossedTab) {
                                            lastCrossedTab = crossed
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = {
                                        isDraggingPill = false
                                        fingerHoverTabIdx = null
                                        dragChannel?.close()
                                        dragChannel = null
                                        val vx = velocityTracker.calculateVelocity().x
                                        val curFrac = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                            .coerceIn(0f, tabs.lastIndex.toFloat())
                                        val targetPage = when {
                                            vx > 480f -> ceil(curFrac).toInt()
                                            vx < -480f -> floor(curFrac).toInt()
                                            else -> curFrac.roundToInt()
                                        }.coerceIn(0, tabs.lastIndex)
                                        scope.launch {
                                            dragJob?.join()
                                            pagerState.animateScrollToPage(
                                                page = targetPage,
                                                animationSpec = ColorOsMotion.fluidSpring()
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        isDraggingPill = false
                                        fingerHoverTabIdx = null
                                        dragChannel?.close()
                                        dragChannel = null
                                        val targetPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                            .roundToInt()
                                            .coerceIn(0, tabs.lastIndex)
                                        scope.launch {
                                            dragJob?.join()
                                            pagerState.animateScrollToPage(
                                                page = targetPage,
                                                animationSpec = ColorOsMotion.fluidSpring()
                                            )
                                        }
                                    }
                                )
                            }
                    ) {
                        val slotWidth = maxWidth / tabs.size
                        val slotWidthPx = with(density) { slotWidth.toPx() }

                        val indicatorBrush = remember(visuals.accent, isLite, visuals.isDark) {
                            if (isLite) null
                            else Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (visuals.isDark) 0.16f else 0.70f),
                                    visuals.accent.copy(alpha = 0.24f),
                                    visuals.accent.copy(alpha = 0.12f)
                                )
                            )
                        }

                        // Glare / Magnifier Selection Lens synced 1:1 with PagerState
                        Box(
                            modifier = Modifier
                                .offset {
                                    val frac = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                        .coerceIn(0f, tabs.lastIndex.toFloat())
                                    IntOffset((frac * slotWidthPx).roundToInt(), 0)
                                }
                                .width(slotWidth)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .then(
                                    if (indicatorBrush != null) Modifier.background(indicatorBrush)
                                    else Modifier.background(visuals.pillActiveGlass)
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (visuals.isDark) 0.28f else 0.85f),
                                            visuals.accent.copy(alpha = 0.55f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val highlightedIdx = clampedFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                            tabs.forEachIndexed { idx, tab ->
                                val isUnderFinger = isDraggingPill && fingerHoverTabIdx == idx
                                val distFromIndicator = abs(clampedFraction - idx.toFloat())
                                FloatingPillTabItem(
                                    tab = tab,
                                    selected = idx == highlightedIdx,
                                    magnified = isUnderFinger,
                                    proximityBoost = (!isLite && !visuals.reduceMotion && distFromIndicator < 0.8f)
                                        .let { if (it) (1f - distFromIndicator).coerceIn(0f, 1f) else 0f },
                                    isLite = isLite,
                                    reduceMotion = visuals.reduceMotion,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                page = idx,
                                                animationSpec = ColorOsMotion.fluidSpring()
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }

                if (isDropTargetHovered) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .clip(CircleShape)
                            .background(visuals.accent)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Drop to Pin Tool",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingPillTabItem(
    tab: MainTab,
    selected: Boolean,
    magnified: Boolean,
    proximityBoost: Float,
    isLite: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val (activeIcon, inactiveIcon) = tabIcons(tab)
    val instant = isLite || reduceMotion

    val itemTint = if (instant) {
        if (selected || magnified) visuals.accent else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        animateColorAsState(
            targetValue = if (selected || magnified) visuals.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(140, easing = ColorOsMotion.IosNavigationEasing),
            label = "pillTint"
        ).value
    }

    val targetScale = when {
        instant -> 1f
        magnified -> 1.16f
        selected -> 1.07f + proximityBoost * 0.03f
        else -> 0.96f + proximityBoost * 0.05f
    }

    val itemScale = if (instant) 1f else animateFloatAsState(
        targetValue = targetScale,
        animationSpec = ColorOsMotion.snappySpring(),
        label = "pillMagnifierScale"
    ).value

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .testTag("nav_tab_${tab.route}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                if (!instant) {
                    scaleX = itemScale
                    scaleY = itemScale
                }
            }
        ) {
            Icon(
                imageVector = if (selected || magnified) activeIcon else inactiveIcon,
                contentDescription = tab.label,
                tint = itemTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tab.label,
                fontSize = 10.sp,
                fontWeight = if (selected || magnified) FontWeight.SemiBold else FontWeight.Normal,
                color = itemTint,
                maxLines = 1
            )
        }
    }
}
