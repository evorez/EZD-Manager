package com.evorez.ezdmanager.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.model.MainTab
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun tabIcons(tab: MainTab): Pair<ImageVector, ImageVector> = when (tab) {
    MainTab.HOME -> Icons.Rounded.Home to Icons.Outlined.Home
    MainTab.PERF -> Icons.Rounded.Speed to Icons.Outlined.Speed
    MainTab.APPS -> Icons.Rounded.Apps to Icons.Outlined.Apps
    MainTab.TOOLS -> Icons.Rounded.Build to Icons.Outlined.Build
}

/**
 * Enhanced Floating Pill Navigation Bar:
 * - 4 intelligently arranged tabs (Home, Performance, Apps, Tools).
 * - Gradient effect on pill: Brighter/darker center with contrasting depth.
 * - Dynamic accent color on left & right circular caps in continuous circular orbital motion.
 * - Decent multi-layer pill shadow (ambient accent glow + deep dark spot shadow).
 * - Finger-following sliding indicator capsule and opaque / frosted blur toggle support.
 */
@Composable
fun FloatingPillNavBar(
    selectedTab: MainTab,
    pagerPosition: Float = selectedTab.ordinal.toFloat(),
    backdropLayer: GraphicsLayer? = null,
    opaque: Boolean = false,
    onSelectTab: (MainTab) -> Unit,
    onDragPagerOffset: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val performHaptic = rememberEzdHaptic()
    val density = LocalDensity.current
    val tabs = MainTab.entries
    val pillShape = RoundedCornerShape(36.dp)
    val isLite = visuals.isLiteMode

    var pillPosInRoot by remember { mutableStateOf(Offset.Zero) }
    var pillWidthPx by remember { mutableFloatStateOf(0f) }

    // Live finger-following drag state (in fractional tab space 0..lastIndex)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(pagerPosition) }

    LaunchedEffect(pagerPosition, isDragging) {
        if (!isDragging) {
            dragFraction = pagerPosition
        }
    }

    val activeFraction = if (isDragging) dragFraction else pagerPosition
    val clampedFraction = activeFraction.coerceIn(0f, tabs.lastIndex.toFloat())

    // Infinite orbiting motion for aesthetic color bubbles around the entire pill perimeter
    val infiniteTransition = rememberInfiniteTransition(label = "pillBubbleOrbit")
    val orbitProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pillOrbitProgress"
    )

    // Real RenderEffect: blur(0..20dp * blurIntensity) + saturate(150%) (disabled if opaque)
    val glassRenderEffect = remember(visuals.blurAllowed, visuals.blurIntensity, density, opaque) {
        if (opaque || !visuals.blurAllowed || visuals.blurIntensity <= 0.02f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else {
            val radiusPx = with(density) { (20f * visuals.blurIntensity).dp.toPx() }.coerceAtLeast(0.5f)
            val blur = AndroidRenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            val satMatrix = ColorMatrix().apply { setSaturation(1.50f) }
            AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(satMatrix), blur)
                .asComposeRenderEffect()
        }
    }

    // Decent pill border with accent highlights on left and right edges
    val pillBorderBrush = remember(visuals.accent, visuals.isDark, isLite) {
        if (isLite) {
            Brush.horizontalGradient(listOf(visuals.glassBorder, visuals.glassBorder))
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    visuals.accent.copy(alpha = 0.55f),
                    if (visuals.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.45f),
                    visuals.accent.copy(alpha = 0.55f)
                )
            )
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp, start = 14.dp, end = 14.dp)
            .widthIn(max = 384.dp)
            .fillMaxWidth()
            .height(68.dp)
            .onGloballyPositioned { coords ->
                pillPosInRoot = coords.positionInRoot()
                pillWidthPx = coords.size.width.toFloat()
            }
            .then(
                if (isLite) Modifier
                else Modifier.shadow(
                    elevation = 20.dp,
                    shape = pillShape,
                    ambientColor = visuals.accent.copy(alpha = 0.38f),
                    spotColor = Color.Black.copy(alpha = 0.85f)
                )
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = pillShape,
            color = if (isLite) visuals.pillBackground else Color.Transparent,
            border = BorderStroke(1.2.dp, pillBorderBrush),
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(pillShape)
            ) {
                if (!isLite) {
                    // 1. Live underlying content drawn directly inside the pill and blurred with RenderEffect
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
                                    // Fallback soft frost tint below API 31 scaled cleanly by blurIntensity
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && visuals.blurIntensity > 0.02f) {
                                        drawRect(
                                            color = if (visuals.isDark) {
                                                Color(20, 15, 40).copy(alpha = 0.48f * visuals.blurIntensity)
                                            } else {
                                                Color.White.copy(alpha = 0.45f * visuals.blurIntensity)
                                            }
                                        )
                                    }
                                }
                        )
                    }

                    // 2. Custom Gradient: Brighter/Darker on center & Accent color on Left & Right (Circular Motion)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val w = size.width
                        val h = size.height
                        val capRadius = h / 2f

                        // A. Center Brighter/Darker gradient (darker obsidian core in dark mode, brighter in light)
                        val centerColor = if (visuals.isDark) {
                            Color(10, 7, 22).copy(alpha = if (opaque) 0.98f else 0.42f)
                        } else {
                            Color(255, 255, 255).copy(alpha = if (opaque) 0.98f else 0.68f)
                        }
                        val edgeColor = if (visuals.isDark) {
                            Color(24, 17, 48).copy(alpha = if (opaque) 0.98f else 0.35f)
                        } else {
                            Color(234, 240, 255).copy(alpha = if (opaque) 0.98f else 0.50f)
                        }

                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(edgeColor, centerColor, edgeColor),
                                startX = 0f,
                                endX = w
                            )
                        )

                        if (!visuals.reduceMotion && !isLite) {
                            // AESTHETIC ORBITING COLOR BUBBLES around the entire inside perimeter of the pill
                            val xMin = capRadius
                            val xMax = (w - capRadius).coerceAtLeast(xMin)
                            val straightLength = xMax - xMin
                            val orbitR = capRadius * 0.72f
                            val arcLength = (Math.PI * orbitR).toFloat()
                            val totalPerimeter = (2f * straightLength) + (2f * arcLength)

                            fun getOrbitOffset(phaseOffset: Float): Offset {
                                if (totalPerimeter <= 0f) return Offset(w / 2f, h / 2f)
                                val d = ((orbitProgress + phaseOffset) % 1f) * totalPerimeter
                                return when {
                                    d < straightLength -> {
                                        val frac = d / straightLength.coerceAtLeast(1f)
                                        Offset(xMin + frac * straightLength, h / 2f - orbitR)
                                    }
                                    d < straightLength + arcLength -> {
                                        val arcDist = d - straightLength
                                        val angle = (-Math.PI / 2.0 + (arcDist / orbitR)).toDouble()
                                        Offset(
                                            (xMax + orbitR * cos(angle)).toFloat(),
                                            (h / 2f + orbitR * sin(angle)).toFloat()
                                        )
                                    }
                                    d < (2f * straightLength) + arcLength -> {
                                        val frac = (d - (straightLength + arcLength)) / straightLength.coerceAtLeast(1f)
                                        Offset(xMax - frac * straightLength, h / 2f + orbitR)
                                    }
                                    else -> {
                                        val arcDist = d - ((2f * straightLength) + arcLength)
                                        val angle = (Math.PI / 2.0 + (arcDist / orbitR)).toDouble()
                                        Offset(
                                            (xMin + orbitR * cos(angle)).toFloat(),
                                            (h / 2f + orbitR * sin(angle)).toFloat()
                                        )
                                    }
                                }
                            }

                            // 5 aesthetic color bubbles around the inside of the pill
                            val bubbleColors = listOf(
                                Triple(visuals.accent, 0.00f, capRadius * 1.25f),
                                Triple(Color(0xFF00E5FF), 0.20f, capRadius * 1.05f),
                                Triple(Color(0xFFB388FF), 0.44f, capRadius * 1.15f),
                                Triple(Color(0xFFFF5252), 0.66f, capRadius * 1.00f),
                                Triple(Color(0xFFFFD166), 0.85f, capRadius * 0.95f)
                            )

                            bubbleColors.forEach { (color, phase, radius) ->
                                val center = getOrbitOffset(phase)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            color.copy(alpha = if (opaque) 0.48f else 0.35f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = center
                                )
                            }
                        } else {
                            // When Reduce Animation is enabled (or Lite mode):
                            // Orbiting bubbles disappear, and ONLY ONE color bubble follows the active tab!
                            val tabWidth = (w - with(density) { 12.dp.toPx() }) / tabs.size
                            val activeTabCenterX = with(density) { 6.dp.toPx() } + (clampedFraction * tabWidth) + (tabWidth / 2f)
                            val singleFollowerCenter = Offset(activeTabCenterX, h / 2f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        visuals.accent.copy(alpha = if (opaque) 0.52f else 0.40f),
                                        Color.Transparent
                                    ),
                                    center = singleFollowerCenter,
                                    radius = capRadius * 1.35f
                                ),
                                radius = capRadius * 1.35f,
                                center = singleFollowerCenter
                            )
                        }
                    }

                    // Top specular highlight edge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = if (visuals.isDark) 0.16f else 0.45f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // 3. Finger-following sliding active capsule + interactive tab strip
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .pointerInput(tabs.size) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragFraction = pagerPosition
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onDragEnd = {
                                    val snappedIdx = dragFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                                    val snappedTab = tabs[snappedIdx]
                                    if (snappedTab != selectedTab) {
                                        performHaptic()
                                    }
                                    onSelectTab(snappedTab)
                                    isDragging = false
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val width = (pillWidthPx - with(density) { 12.dp.toPx() }).coerceAtLeast(1f)
                                    val tabStepWidth = width / tabs.size
                                    dragFraction = (dragFraction + (dragAmount / tabStepWidth))
                                        .coerceIn(0f, tabs.lastIndex.toFloat())
                                    onDragPagerOffset?.invoke(dragFraction)
                                }
                            )
                        }
                ) {
                    val slotWidth = maxWidth / tabs.size
                    val slotWidthPx = with(density) { slotWidth.toPx() }
                    val indicatorOffsetPx = (clampedFraction * slotWidthPx).roundToInt()

                    // Sliding Liquid-Glass Active Capsule
                    val indicatorBrush = remember(visuals.accent, isLite) {
                        if (isLite) null
                        else Brush.verticalGradient(
                            colors = listOf(
                                visuals.accent.copy(alpha = 0.28f),
                                visuals.accent.copy(alpha = 0.14f)
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(indicatorOffsetPx, 0) }
                            .width(slotWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .then(
                                if (indicatorBrush != null) Modifier.background(indicatorBrush)
                                else Modifier.background(visuals.pillActiveGlass)
                            )
                            .border(
                                width = 1.dp,
                                color = visuals.accent.copy(alpha = 0.48f),
                                shape = CircleShape
                            )
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val highlightedIdx = clampedFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                        tabs.forEachIndexed { idx, tab ->
                            FloatingPillTabItem(
                                tab = tab,
                                selected = idx == highlightedIdx,
                                isLite = isLite,
                                reduceMotion = visuals.reduceMotion,
                                onClick = {
                                    performHaptic()
                                    onSelectTab(tab)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
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
    isLite: Boolean,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val (activeIcon, inactiveIcon) = tabIcons(tab)
    val instant = isLite || reduceMotion

    val itemTint = if (instant) {
        if (selected) visuals.accent else MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        animateColorAsState(
            targetValue = if (selected) visuals.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(160, easing = ColorOsMotion.IosNavigationEasing),
            label = "pillTint"
        ).value
    }

    val iconScale = if (instant) 1f else animateFloatAsState(
        targetValue = if (selected) 1.07f else 0.96f,
        animationSpec = tween(160, easing = ColorOsMotion.IosNavigationEasing),
        label = "pillIconScale"
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
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) activeIcon else inactiveIcon,
                contentDescription = tab.label,
                tint = itemTint,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        if (!instant) {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                    }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tab.label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = itemTint,
                maxLines = 1
            )
        }
    }
}
