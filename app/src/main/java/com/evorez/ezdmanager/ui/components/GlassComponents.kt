package com.evorez.ezdmanager.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

object ColorOsMotion {
    val FluidEaseOut = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val IosNavigationEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    fun <T> fluidSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 360f
    )

    fun <T> snappySpring(): SpringSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 480f
    )
}

@Composable
fun rememberEzdHaptic(): () -> Unit {
    val visuals = LocalEzdVisuals.current
    val haptic = LocalHapticFeedback.current
    return remember(visuals.hapticsEnabled, haptic) {
        {
            if (visuals.hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

/**
 * True circular reveal shape expanding radially from [originFraction] (0..1 in X/Y)
 * for smooth 120Hz ColorOS-inspired circular app/tool launch transitions.
 */
fun circleRevealShape(
    progress: Float,
    originFraction: Offset = Offset(0.5f, 0.48f)
): Shape = GenericShape { size, _ ->
    val cx = size.width * originFraction.x.coerceIn(0f, 1f)
    val cy = size.height * originFraction.y.coerceIn(0f, 1f)
    val maxR = maxOf(
        hypot(cx, cy),
        hypot(size.width - cx, cy),
        hypot(cx, size.height - cy),
        hypot(size.width - cx, size.height - cy)
    )
    val radius = (maxR * progress.coerceIn(0.02f, 1.05f)).coerceAtLeast(16f)
    addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
}

@Stable
class CardLaunchOrigin {
    var lastBounds by mutableStateOf<Rect?>(null)
}

val LocalCardLaunchOrigin = staticCompositionLocalOf<CardLaunchOrigin?> { null }

@Composable
fun Modifier.captureLaunchBounds(): Modifier {
    val tracker = LocalCardLaunchOrigin.current ?: return this
    var myBounds by remember { mutableStateOf<Rect?>(null) }
    return this
        .onGloballyPositioned { coords ->
            myBounds = coords.boundsInRoot()
        }
        .pointerInput(tracker) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.changes.any { it.pressed }) {
                        myBounds?.let { tracker.lastBounds = it }
                    }
                }
            }
        }
}

@Composable
fun UserAvatarBadge(
    photoPath: String,
    size: Dp = 56.dp,
    borderColor: Color = LocalEzdVisuals.current.accent,
    modifier: Modifier = Modifier
) {
    val decodedBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = photoPath) {
        value = if (photoPath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val f = java.io.File(photoPath)
                    if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
                }.getOrNull()
            }
        } else null
    }
    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap!!,
            contentDescription = "Profile Photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, borderColor, CircleShape)
        )
    } else {
        Image(
            painter = painterResource(R.drawable.evorez_avatar),
            contentDescription = "Default Profile Avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, borderColor, CircleShape)
        )
    }
}

@Composable
fun CircularLaunchReveal(
    launchKey: Any?,
    originFraction: Offset = Offset(0.5f, 0.45f),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    if (visuals.isLiteMode || visuals.reduceMotion) {
        Box(modifier = modifier) { content() }
        return
    }
    var revealed by remember(launchKey) { mutableStateOf(false) }
    LaunchedEffect(launchKey) {
        revealed = true
    }
    val progress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.04f,
        animationSpec = tween(360, easing = ColorOsMotion.FluidEaseOut),
        label = "circularRevealProgress"
    )
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.92f,
        animationSpec = ColorOsMotion.fluidSpring(),
        label = "circularLaunchScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.35f,
        animationSpec = tween(220, easing = ColorOsMotion.FluidEaseOut),
        label = "circularLaunchAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                if (progress < 0.995f) {
                    shape = circleRevealShape(progress, originFraction)
                    clip = true
                }
            }
    ) {
        content()
    }
}

@Composable
fun EzdAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    Box(
        modifier = modifier.drawBehind {
            drawRect(color = visuals.bgBase)
            if (visuals.gradientsAllowed) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(visuals.bgTopGlow, Color.Transparent),
                        center = Offset(size.width * 0.25f, 0f),
                        radius = size.width * 1.15f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(visuals.bgBottomGlow, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height),
                        radius = size.width * 0.95f
                    )
                )
            }
        }
    ) {
        content()
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    highlightBorder: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val performHaptic = rememberEzdHaptic()
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = if (highlightBorder) visuals.accent.copy(alpha = 0.52f) else visuals.glassBorder
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animatePress = !visuals.isLiteMode && !visuals.reduceMotion && onClick != null
    val pressScale = if (!animatePress) {
        1f
    } else {
        animateFloatAsState(
            targetValue = if (pressed) 0.96f else 1f,
            animationSpec = tween(durationMillis = 100, easing = ColorOsMotion.IosNavigationEasing),
            label = "glassPressScale"
        ).value
    }

    val glassGradientBrush = remember(visuals.gradientsAllowed, visuals.glassSurfaceElevated, visuals.glassSurface) {
        if (visuals.gradientsAllowed) {
            Brush.linearGradient(
                colors = listOf(
                    visuals.glassSurfaceElevated.copy(alpha = 0.40f),
                    visuals.glassSurface.copy(alpha = 0.14f)
                )
            )
        } else null
    }

    Surface(
        modifier = modifier
            .then(
                if (animatePress) {
                    Modifier.graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                } else Modifier
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = {
                            performHaptic()
                            onClick()
                        }
                    )
                } else Modifier
            ),
        shape = shape,
        color = visuals.glassSurface,
        border = BorderStroke(if (highlightBorder) 1.2.dp else 1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (visuals.isDark || visuals.isLiteMode) 0.dp else 2.dp
    ) {
        Box(
            modifier = Modifier.then(
                if (glassGradientBrush != null) {
                    Modifier.background(glassGradientBrush)
                } else Modifier
            ),
            content = content
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    highlightBorder: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        cornerRadius = cornerRadius,
        highlightBorder = highlightBorder,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun GlowIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    glowColor: Color = LocalEzdVisuals.current.iconGlowColor,
    size: Dp = 22.dp
) {
    val visuals = LocalEzdVisuals.current
    Box(
        modifier = modifier
            .size(size + 12.dp)
            .drawBehind {
                if (visuals.gradientsAllowed) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.18f), Color.Transparent),
                            center = center,
                            radius = this.size.minDimension * 0.55f
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Boolean = true
) {
    val visuals = LocalEzdVisuals.current
    val performHaptic = rememberEzdHaptic()
    val onAccent = if (visuals.isDark &&
        (visuals.accent.red * 0.299f + visuals.accent.green * 0.587f + visuals.accent.blue * 0.114f) > 0.65f
    ) Color(0xFF0B0E14) else Color.White

    Button(
        onClick = {
            performHaptic()
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        colors = if (accent) {
            ButtonDefaults.buttonColors(
                containerColor = visuals.accent,
                contentColor = onAccent
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = visuals.glassSurfaceElevated,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        },
        border = if (!accent) BorderStroke(1.dp, visuals.glassBorder) else null,
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val visuals = LocalEzdVisuals.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = visuals.accent,
            activeTrackColor = visuals.accent,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "Confirm",
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    val visuals = LocalEzdVisuals.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = if (visuals.isLiteMode) visuals.glassSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        },
        confirmButton = {
            if (onConfirm != null) {
                GlassButton(text = confirmText, onClick = onConfirm)
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (onConfirm != null) {
            { TextButton(onClick = onDismiss) { Text(dismissText) } }
        } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val visuals = LocalEzdVisuals.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (visuals.isLiteMode) visuals.glassSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalEzdVisuals.current.accent,
            fontWeight = FontWeight.Bold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

object AppIconCache {
    private val cache = object : LruCache<String, ImageBitmap>(120) {}

    suspend fun getOrLoad(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bmp = drawableToBitmap(drawable, 96)
                val img = bmp.asImageBitmap()
                cache.put(packageName, img)
                img
            }.getOrNull()
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}

@Composable
fun AppIconItem(
    packageName: String,
    contentDescription: String,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        iconBitmap = AppIconCache.getOrLoad(context, packageName)
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(size * 0.72f)
        )
    }
}

/**
 * Floating frosted-glass single-line capsule TopBrandBar (matches clean floating header pill style,
 * compacts smoothly on scroll, and respects display cutout & status bar insets).
 */
@Composable
fun TopBrandBar(
    title: String,
    collapseFraction: Float,
    unreadNotifications: Int,
    onLogoClick: () -> Unit = {},
    onSearchClick: () -> Unit,
    onPowerClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val f = collapseFraction.coerceIn(0f, 1f)
    val verticalPad = lerp(10.dp, 6.dp, f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = verticalPad),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Floating Frosted Brand Pill (single clean title, circular logo)
        Surface(
            shape = CircleShape,
            color = visuals.pillBackground,
            border = BorderStroke(1.dp, visuals.glassBorder),
            shadowElevation = if (visuals.isDark || visuals.isLiteMode) 0.dp else 6.dp,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.evorez_logo_circular),
                    contentDescription = "Evorez Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(lerp(32.dp, 28.dp, f))
                        .clip(CircleShape)
                        .border(1.dp, visuals.glassBorder, CircleShape)
                        .clickable(onClick = onLogoClick)
                        .testTag("top_brand_logo")
                )
                Text(
                    text = title,
                    fontSize = lerp(17.sp, 15.sp, f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Right Floating Frosted Action Capsule
        Surface(
            shape = CircleShape,
            color = visuals.pillBackground,
            border = BorderStroke(1.dp, visuals.glassBorder),
            shadowElevation = if (visuals.isDark || visuals.isLiteMode) 2.dp else 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toolbar_search")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toolbar_notifications")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge(containerColor = visuals.accent) {
                                    Text(unreadNotifications.coerceAtMost(9).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onPowerClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toolbar_power")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Power & Reboot",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toolbar_settings")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = visuals.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CollapsibleSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    trailingBadge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ColorOsMotion.fluidSpring(),
        label = "collapseArrow"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!trailingBadge.isNullOrBlank()) {
                    StatusPill(label = trailingBadge, accent = visuals.accent)
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
        }
        if (visuals.isLiteMode) {
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    content = content
                )
            }
        } else {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(ColorOsMotion.fluidSpring()) + fadeIn(tween(200, easing = ColorOsMotion.FluidEaseOut)),
                exit = shrinkVertically(ColorOsMotion.fluidSpring()) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
