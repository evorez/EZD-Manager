package com.evorez.ezdmanager.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
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
import androidx.compose.ui.graphics.ShaderBrush
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
import com.evorez.ezdmanager.model.BackgroundTexturePreset
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.hypot

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
        targetValue = if (revealed) 1f else 0.94f,
        animationSpec = ColorOsMotion.fluidSpring(),
        label = "circularLaunchScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = 1f
                if (progress < 0.995f) {
                    shape = circleRevealShape(progress, originFraction)
                    clip = true
                }
            }
    ) {
        content()
    }
}

private object ProceduralTextureCache {
    private val cache = mutableMapOf<String, ShaderBrush>()

    fun getBrush(preset: BackgroundTexturePreset, isDark: Boolean): ShaderBrush? {
        if (preset == BackgroundTexturePreset.PLAIN) return null
        val key = "${preset.name}_$isDark"
        cache[key]?.let { return it }

        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rng = Random(preset.ordinal * 9187L + if (isDark) 31L else 7L)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (preset) {
            BackgroundTexturePreset.PAPER -> {
                // Soft tactile paper base tint + cross-hatched cotton fibers & micro-specks
                canvas.drawColor(
                    if (isDark) android.graphics.Color.argb(38, 210, 198, 178)
                    else android.graphics.Color.argb(120, 234, 224, 206)
                )
                for (i in 0 until 900) {
                    val x = rng.nextFloat() * size
                    val y = rng.nextFloat() * size
                    val len = 6f + rng.nextFloat() * 14f
                    val angle = rng.nextFloat() * Math.PI * 2.0
                    val dx = (kotlin.math.cos(angle) * len).toFloat()
                    val dy = (kotlin.math.sin(angle) * len).toFloat()
                    val warm = rng.nextBoolean()
                    paint.strokeWidth = 0.8f + rng.nextFloat() * 0.9f
                    paint.color = when {
                        isDark && warm -> android.graphics.Color.argb(24, 235, 220, 195)
                        isDark -> android.graphics.Color.argb(26, 20, 16, 12)
                        warm -> android.graphics.Color.argb(46, 176, 152, 120)
                        else -> android.graphics.Color.argb(42, 255, 250, 240)
                    }
                    // Wrap tile seams for seamless repeating
                    for (ox in listOf(-size, 0, size)) {
                        for (oy in listOf(-size, 0, size)) {
                            if (x + ox in -20f..(size + 20f) && y + oy in -20f..(size + 20f)) {
                                canvas.drawLine(x + ox, y + oy, x + ox + dx, y + oy + dy, paint)
                            }
                        }
                    }
                }
                for (i in 0 until 650) {
                    paint.color = if (isDark) {
                        android.graphics.Color.argb(22, 240, 230, 210)
                    } else {
                        android.graphics.Color.argb(34, 145, 124, 96)
                    }
                    canvas.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, 0.7f + rng.nextFloat() * 0.9f, paint)
                }
            }

            BackgroundTexturePreset.MOON -> {
                // Lunar basalt dust + subtle overlapping circular craters
                canvas.drawColor(
                    if (isDark) android.graphics.Color.argb(32, 140, 155, 185)
                    else android.graphics.Color.argb(110, 210, 216, 226)
                )
                for (i in 0 until 26) {
                    val cx = rng.nextFloat() * size
                    val cy = rng.nextFloat() * size
                    val r = 10f + rng.nextFloat() * 26f
                    for (ox in listOf(-size, 0, size)) {
                        for (oy in listOf(-size, 0, size)) {
                            val px = cx + ox
                            val py = cy + oy
                            if (px + r >= -4f && px - r <= size + 4f && py + r >= -4f && py - r <= size + 4f) {
                                paint.style = Paint.Style.FILL
                                paint.color = if (isDark) {
                                    android.graphics.Color.argb(20, 12, 16, 28)
                                } else {
                                    android.graphics.Color.argb(34, 150, 160, 178)
                                }
                                canvas.drawCircle(px, py, r, paint)

                                paint.style = Paint.Style.STROKE
                                paint.strokeWidth = 1.4f
                                paint.color = if (isDark) {
                                    android.graphics.Color.argb(28, 210, 222, 245)
                                } else {
                                    android.graphics.Color.argb(46, 248, 250, 255)
                                }
                                canvas.drawCircle(px - 1f, py - 1f, r, paint)
                            }
                        }
                    }
                }
                paint.style = Paint.Style.FILL
                for (i in 0 until 950) {
                    paint.color = if (isDark) {
                        android.graphics.Color.argb(26, 220, 230, 255)
                    } else {
                        android.graphics.Color.argb(36, 118, 128, 145)
                    }
                    canvas.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, 0.7f + rng.nextFloat() * 1.1f, paint)
                }
            }

            BackgroundTexturePreset.SILVER -> {
                // Fine brushed anodized silver metallic grain
                canvas.drawColor(
                    if (isDark) android.graphics.Color.argb(34, 175, 186, 204)
                    else android.graphics.Color.argb(120, 216, 222, 230)
                )
                for (y in 0 until size) {
                    val wave = kotlin.math.sin(y * 0.14).toFloat() * 0.5f + 0.5f
                    val alpha = if (isDark) (10 + (wave * 18).toInt()) else (18 + (wave * 32).toInt())
                    paint.strokeWidth = 1f
                    paint.color = if (isDark) {
                        android.graphics.Color.argb(alpha, 215, 224, 240)
                    } else {
                        android.graphics.Color.argb(alpha, 152, 162, 176)
                    }
                    canvas.drawLine(0f, y.toFloat(), size.toFloat(), y.toFloat(), paint)
                }
                for (i in 0 until 750) {
                    val x = rng.nextFloat() * size
                    val y = rng.nextFloat() * size
                    val len = 18f + rng.nextFloat() * 44f
                    paint.strokeWidth = 0.8f
                    paint.color = if (isDark) {
                        android.graphics.Color.argb(24, 255, 255, 255)
                    } else {
                        android.graphics.Color.argb(44, 255, 255, 255)
                    }
                    canvas.drawLine(x, y, x + len, y + 0.8f, paint)
                    if (x + len > size) {
                        canvas.drawLine(x - size, y, x - size + len, y + 0.8f, paint)
                    }
                }
            }

            BackgroundTexturePreset.PLAIN -> Unit
        }

        val shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val brush = ShaderBrush(shader)
        cache[key] = brush
        return brush
    }
}

@Composable
fun EzdAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val textureBrush = remember(visuals.texturePreset, visuals.isDark) {
        ProceduralTextureCache.getBrush(visuals.texturePreset, visuals.isDark)
    }

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
            if (textureBrush != null && visuals.textureOpacity > 0.01f) {
                drawRect(
                    brush = textureBrush,
                    alpha = visuals.textureOpacity.coerceIn(0f, 1f)
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
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = if (highlightBorder) visuals.accent.copy(alpha = 0.52f) else visuals.glassBorder
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animatePress = !visuals.isLiteMode && !visuals.reduceMotion && onClick != null
    val pressScale = if (!animatePress) {
        1f
    } else {
        animateFloatAsState(
            targetValue = if (pressed) 0.97f else 1f,
            animationSpec = tween(durationMillis = 100, easing = ColorOsMotion.IosNavigationEasing),
            label = "glassPressScale"
        ).value
    }

    val glassGradientBrush = remember(visuals.gradientsAllowed, visuals.glassSurfaceElevated, visuals.glassSurface, visuals.isDark) {
        if (visuals.gradientsAllowed && visuals.isDark) {
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
                    }
                } else Modifier
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        // Solid opaque surface backing underneath prevents transparency glitches across back navigation or light mode
        color = if (visuals.isDark) visuals.solidSurfaceBase else visuals.solidSurfaceElevated,
        border = BorderStroke(if (highlightBorder) 1.2.dp else 1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (visuals.isDark || visuals.isLiteMode) 0.dp else 3.dp
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (visuals.isDark) Modifier.background(visuals.glassSurface) else Modifier
                )
                .then(
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
    val onAccent = if (visuals.isDark &&
        (visuals.accent.red * 0.299f + visuals.accent.green * 0.587f + visuals.accent.blue * 0.114f) > 0.65f
    ) Color(0xFF0B0E14) else Color.White

    Button(
        onClick = onClick,
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
        containerColor = visuals.solidSurfaceElevated,
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
        containerColor = visuals.solidSurfaceElevated,
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
