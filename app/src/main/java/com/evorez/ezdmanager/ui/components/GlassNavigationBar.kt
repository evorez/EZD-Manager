package com.evorez.ezdmanager.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.model.MainTab
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import kotlin.math.roundToInt

private fun tabIcons(tab: MainTab): Pair<ImageVector, ImageVector> = when (tab) {
    MainTab.HOME -> Icons.Rounded.Home to Icons.Outlined.Home
    MainTab.CPU -> Icons.Rounded.Memory to Icons.Outlined.Memory
    MainTab.GPU -> Icons.Rounded.Speed to Icons.Outlined.Speed
    MainTab.APPS -> Icons.Rounded.Apps to Icons.Outlined.Apps
    MainTab.TOOLS -> Icons.Rounded.Build to Icons.Outlined.Build
}

/**
 * 5-tab GlassNavigationBar (Home, CPU, GPU, Apps, Tools) with frosted blur fallback,
 * Lite Mode solid clean surface, and smooth finger-tracking active pill lens.
 */
@Composable
fun GlassNavigationBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val tabs = MainTab.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    val targetFraction = if (dragging) dragFraction else selectedIndex.toFloat()
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (dragging) Spring.StiffnessHigh else Spring.StiffnessMediumLow
        ),
        label = "navLens"
    )

    val shape = RoundedCornerShape(34.dp)

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(66.dp)
            .clip(shape)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(tabs.size, widthPx) {
                if (widthPx <= 0f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val slot = widthPx / tabs.size
                        dragFraction = ((offset.x / slot) - 0.5f).coerceIn(0f, (tabs.size - 1).toFloat())
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val slot = widthPx / tabs.size
                        dragFraction = (dragFraction + dragAmount / slot).coerceIn(0f, (tabs.size - 1).toFloat())
                        val hovered = dragFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                        if (tabs[hovered] != selectedTab) {
                            onSelectTab(tabs[hovered])
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        val snapped = dragFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                        dragging = false
                        onSelectTab(tabs[snapped])
                    },
                    onDragCancel = { dragging = false }
                )
            },
        shape = shape,
        color = if (visuals.isLiteMode) visuals.pillBackground else Color.Transparent,
        border = BorderStroke(1.dp, visuals.glassBorder),
        shadowElevation = if (visuals.isDark || visuals.isLiteMode) 4.dp else 12.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!visuals.isLiteMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (visuals.blurAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(18.dp)
                            } else Modifier
                        )
                        .background(visuals.pillBackground)
                )
            }

            if (widthPx > 0f) {
                val insetPx = with(density) { 5.dp.toPx() }
                val usablePx = (widthPx - insetPx * 2f).coerceAtLeast(1f)
                val slotPx = usablePx / tabs.size
                val lensOffset = (insetPx + animatedFraction * slotPx).roundToInt()
                val slotDp = with(density) { slotPx.toDp() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(lensOffset, 0) }
                        .align(Alignment.CenterStart)
                        .width(slotDp)
                        .fillMaxHeight()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            if (visuals.gradientsAllowed) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        visuals.subtleRedAccent.copy(alpha = 0.26f),
                                        visuals.pillActiveGlass
                                    )
                                )
                            } else {
                                Brush.linearGradient(listOf(visuals.pillActiveGlass, visuals.pillActiveGlass))
                            }
                        )
                        .border(
                            1.dp,
                            visuals.subtleRedAccent.copy(alpha = if (dragging) 0.8f else 0.45f),
                            RoundedCornerShape(28.dp)
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEach { tab ->
                    val selected = tab == selectedTab
                    val (activeIcon, inactiveIcon) = tabIcons(tab)
                    val color by animateColorAsState(
                        targetValue = if (selected) visuals.subtleRedAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .clickable { onSelectTab(tab) }
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
                                tint = color,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = color,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
