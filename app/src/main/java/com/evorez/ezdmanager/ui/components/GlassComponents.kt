package com.evorez.ezdmanager.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
                        radius = size.width * 1.1f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(visuals.bgBottomGlow, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height),
                        radius = size.width * 0.9f
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
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = if (highlightBorder) visuals.subtleRedAccent.copy(alpha = 0.7f) else visuals.glassBorder

    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = visuals.glassSurface,
        border = BorderStroke(if (highlightBorder) 1.2.dp else 1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (visuals.isDark || visuals.isLiteMode) 0.dp else 2.dp
    ) {
        Box(
            modifier = Modifier.then(
                if (visuals.gradientsAllowed) {
                    Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                visuals.glassSurfaceElevated.copy(alpha = 0.45f),
                                visuals.glassSurface.copy(alpha = 0.15f)
                            )
                        )
                    )
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

/**
 * Neutral icon with a soft radial theme glow rather than bulky colored icon boxes.
 */
@Composable
fun GlowIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    glowColor: Color = LocalEzdVisuals.current.iconGlowColor,
    size: Dp = 24.dp
) {
    val visuals = LocalEzdVisuals.current
    Box(
        modifier = modifier
            .size(size + 14.dp)
            .drawBehind {
                if (visuals.gradientsAllowed) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.28f), Color.Transparent),
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
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = if (accent) {
            ButtonDefaults.buttonColors(
                containerColor = visuals.subtleRedAccent,
                contentColor = Color.White
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
            thumbColor = visuals.subtleRedAccent,
            activeTrackColor = visuals.subtleRedAccent,
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
            color = LocalEzdVisuals.current.subtleRedAccent,
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

/**
 * Loads and caches real installed app icons via PackageManager into an LRU bitmap cache.
 */
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
        GlowIcon(
            icon = Icons.Rounded.Apps,
            contentDescription = contentDescription,
            size = size * 0.6f,
            modifier = modifier.size(size)
        )
    }
}

/**
 * Telegram-inspired scroll-responsive collapsing header respecting display cutout & status bar insets.
 */
@Composable
fun CollapsingHeaderBar(
    title: String,
    subtitle: String,
    collapseFraction: Float,
    unreadNotifications: Int,
    onSearchClick: () -> Unit,
    onPowerClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val f = collapseFraction.coerceIn(0f, 1f)
    val titleSize = lerp(24.sp, 17.sp, f)
    val topPadding = lerp(10.dp, 4.dp, f)
    val bottomPadding = lerp(12.dp, 6.dp, f)
    val containerColor = lerp(
        Color.Transparent,
        if (visuals.isLiteMode) visuals.glassSurfaceElevated else visuals.pillBackground,
        f
    )

    Surface(
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        Box {
            if (visuals.blurAllowed && f > 0.2f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(12.dp)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = topPadding, bottom = bottomPadding)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.evorez_logo),
                            contentDescription = "EZD Manager",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(lerp(36.dp, 28.dp, f))
                                .clip(CircleShape)
                                .border(1.dp, visuals.subtleRedAccent.copy(alpha = 0.6f), CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                fontSize = titleSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            AnimatedVisibility(visible = f < 0.7f && subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toolbar_search")
                        ) {
                            GlowIcon(Icons.Rounded.Search, contentDescription = "Search", size = 20.dp)
                        }
                        IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toolbar_notifications")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifications > 0) {
                                        Badge(containerColor = visuals.subtleRedAccent) {
                                            Text(unreadNotifications.coerceAtMost(9).toString())
                                        }
                                    }
                                }
                            ) {
                                GlowIcon(
                                    icon = Icons.Rounded.Notifications,
                                    contentDescription = "Notifications",
                                    size = 20.dp
                                )
                            }
                        }
                        IconButton(
                            onClick = onPowerClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toolbar_power")
                        ) {
                            GlowIcon(
                                icon = Icons.Rounded.PowerSettingsNew,
                                contentDescription = "Power & Reboot",
                                tint = visuals.subtleRedAccent,
                                size = 20.dp
                            )
                        }
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toolbar_settings")
                        ) {
                            GlowIcon(Icons.Rounded.Settings, contentDescription = "Settings", size = 20.dp)
                        }
                    }
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
        animationSpec = spring(),
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
                    StatusPill(label = trailingBadge, accent = visuals.subtleRedAccent)
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring()) + fadeIn(),
            exit = shrinkVertically(spring()) + fadeOut()
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
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
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
