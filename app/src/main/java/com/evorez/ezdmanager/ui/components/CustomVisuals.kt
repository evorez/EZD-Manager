package com.evorez.ezdmanager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.model.AudioState
import com.evorez.ezdmanager.model.AudioVisualStyle
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 1. CUSTOM DNS PROVIDER VECTOR / CANVAS LOGOS
// ============================================================================

@Composable
fun DnsProviderLogo(
    presetId: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = minOf(w, h) / 2f
        val cx = w / 2f
        val cy = h / 2f

        when (presetId) {
            "cloudflare" -> {
                // Warm dark circle + Cloudflare orange-gold flame cloud
                drawCircle(color = Color(0xFF2B1608), radius = r)
                drawCircle(color = Color(0xFFF6821F).copy(alpha = 0.65f), radius = r, style = Stroke(width = w * 0.05f))
                val orange = Color(0xFFF38020)
                val gold = Color(0xFFFAAD3F)
                drawCircle(color = orange, radius = w * 0.21f, center = Offset(cx - w * 0.04f, cy + h * 0.02f))
                drawCircle(color = orange, radius = w * 0.16f, center = Offset(cx + w * 0.16f, cy + h * 0.07f))
                drawCircle(color = orange, radius = w * 0.14f, center = Offset(cx - w * 0.20f, cy + h * 0.09f))
                drawRoundRect(
                    color = orange,
                    topLeft = Offset(cx - w * 0.27f, cy + h * 0.05f),
                    size = Size(w * 0.56f, h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.09f)
                )
                drawCircle(color = gold, radius = w * 0.09f, center = Offset(cx + w * 0.17f, cy - h * 0.05f))
            }

            "google" -> {
                // Google 4-color segmented circle + stylized G crossbar
                drawCircle(color = Color(0xFF0E1726), radius = r)
                val stroke = Stroke(width = w * 0.15f, cap = StrokeCap.Butt)
                val inset = w * 0.23f
                val arcSize = Size(w - inset * 2, h - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(Color(0xFFEA4335), startAngle = -140f, sweepAngle = 95f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                drawArc(Color(0xFFFBBC05), startAngle = 145f, sweepAngle = 75f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                drawArc(Color(0xFF34A853), startAngle = 45f, sweepAngle = 100f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                drawArc(Color(0xFF4285F4), startAngle = -20f, sweepAngle = 70f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
                drawLine(
                    color = Color(0xFF4285F4),
                    start = Offset(cx, cy),
                    end = Offset(cx + w * 0.28f, cy),
                    strokeWidth = w * 0.14f,
                    cap = StrokeCap.Round
                )
            }

            "adguard" -> {
                // Emerald shield with crisp white checkmark
                drawCircle(color = Color(0xFF0B241A), radius = r)
                val shield = Path().apply {
                    moveTo(cx, h * 0.16f)
                    lineTo(w * 0.78f, h * 0.28f)
                    quadraticTo(w * 0.77f, h * 0.65f, cx, h * 0.85f)
                    quadraticTo(w * 0.23f, h * 0.65f, w * 0.22f, h * 0.28f)
                    close()
                }
                drawPath(path = shield, color = Color(0xFF68BC71))
                val check = Path().apply {
                    moveTo(w * 0.36f, h * 0.50f)
                    lineTo(w * 0.47f, h * 0.61f)
                    lineTo(w * 0.65f, h * 0.40f)
                }
                drawPath(path = check, color = Color.White, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            "quad9" -> {
                // Cobalt/indigo orbital security ring with crisp '9' motif
                drawCircle(color = Color(0xFF101936), radius = r)
                drawCircle(color = Color(0xFF5386E4), radius = r * 0.76f, style = Stroke(width = w * 0.07f))
                drawCircle(color = Color(0xFF93C5FD), radius = r * 0.25f, center = Offset(cx, cy - h * 0.06f), style = Stroke(width = w * 0.08f))
                drawLine(
                    color = Color(0xFF93C5FD),
                    start = Offset(cx + r * 0.24f, cy - h * 0.06f),
                    end = Offset(cx + r * 0.06f, cy + r * 0.40f),
                    strokeWidth = w * 0.085f,
                    cap = StrokeCap.Round
                )
            }

            else -> {
                // System / Custom Globe Ring
                val ringColor = visuals.accent
                drawCircle(color = ringColor.copy(alpha = 0.16f), radius = r)
                drawCircle(color = ringColor, radius = r * 0.62f, style = Stroke(width = w * 0.06f))
                drawOval(
                    color = ringColor,
                    topLeft = Offset(cx - r * 0.30f, cy - r * 0.62f),
                    size = Size(r * 0.60f, r * 1.24f),
                    style = Stroke(width = w * 0.05f)
                )
                drawLine(
                    color = ringColor,
                    start = Offset(cx - r * 0.60f, cy),
                    end = Offset(cx + r * 0.60f, cy),
                    strokeWidth = w * 0.05f
                )
            }
        }
    }
}

// ============================================================================
// 2. AUDIO ENHANCER MULTI-STYLE REACTIVE VISUALIZER
// ============================================================================

@Composable
fun AudioEnhancerVisualizer(
    audioState: AudioState,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val style = audioState.visualStyle

    val (primaryColor, secondaryColor) = when (style) {
        AudioVisualStyle.BASS_PULSE -> Color(0xFFFF3B5C) to Color(0xFFFF9F1C)
        AudioVisualStyle.PRISM_BARS -> Color(0xFF22D3EE) to Color(0xFF3B82F6)
        AudioVisualStyle.SILK_WAVE -> Color(0xFFA78BFA) to Color(0xFFF472B6)
        AudioVisualStyle.CYBER_MATRIX -> Color(0xFF34D399) to Color(0xFF14B8A6)
    }

    // Lite mode: zero-lag static telemetry indicator strip (zero infinite animation ticks)
    if (visuals.isLiteMode) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(84.dp),
            shape = RoundedCornerShape(20.dp),
            color = visuals.glassSurfaceElevated,
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(style.label, style = MaterialTheme.typography.titleSmall, color = primaryColor)
                    Text(
                        text = "Bass ${if (audioState.bassEnabled) "${audioState.bassStrength / 10}%" else "Muted"} · EQ ${if (audioState.equalizerEnabled) audioState.activePreset else "Bypass"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill("Lite Mode", primaryColor)
            }
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "audioVizLoop")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (audioState.bassEnabled) 1800 else 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "audioPhase"
    )

    val bassNorm = if (audioState.bassEnabled) (audioState.bassStrength / 1000f).coerceIn(0.15f, 1f) else 0.12f
    val eqActive = audioState.equalizerEnabled
    val bandEnergies = remember(audioState.bandLevels, eqActive) {
        if (!eqActive || audioState.bandLevels.isEmpty()) List(5) { 0.2f }
        else audioState.bandLevels.map { ((it + 1500f) / 3000f).coerceIn(0.12f, 1f) }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (visuals.isDark) Color(0xFF07090F) else visuals.glassSurfaceElevated,
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val w = size.width
                val h = size.height
                val cy = h / 2f
                val cx = w / 2f

                when (style) {
                    AudioVisualStyle.BASS_PULSE -> {
                        // Radial dual sub-woofer pulse + orbiting frequency ring
                        val pulse1 = (0.5f + 0.5f * sin(phase * 2f)) * bassNorm
                        val pulse2 = (0.5f + 0.5f * sin(phase * 2f - 1.1f)) * bassNorm
                        val baseRadius = minOf(w, h) * 0.24f

                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(primaryColor.copy(alpha = 0.36f * bassNorm), Color.Transparent),
                                center = Offset(cx, cy),
                                radius = baseRadius * (1.7f + pulse1 * 0.9f)
                            )
                        )
                        for (ring in 1..3) {
                            val rFactor = baseRadius * (0.75f + ring * 0.42f + (if (ring % 2 == 0) pulse1 else pulse2) * 0.38f)
                            drawCircle(
                                color = if (ring == 1) secondaryColor.copy(alpha = 0.85f) else primaryColor.copy(alpha = 0.65f / ring),
                                radius = rFactor,
                                center = Offset(cx, cy),
                                style = Stroke(width = (4.5f - ring).dp.toPx())
                            )
                        }
                        // Side sub-bars synced with bass energy
                        val barCount = 14
                        val barW = 4.dp.toPx()
                        for (i in 0 until barCount) {
                            val t = i / (barCount - 1f)
                            val wave = 0.3f + 0.7f * abs(sin(phase * 2f + i * 0.45f)) * bassNorm
                            val bh = h * 0.65f * wave
                            val lx = cx - baseRadius * 1.65f - i * 8.dp.toPx()
                            val rx = cx + baseRadius * 1.65f + i * 8.dp.toPx()
                            val col = lerpColor(primaryColor, secondaryColor, t)
                            if (lx > 0f) {
                                drawRoundRect(col.copy(alpha = 0.75f), Offset(lx, cy - bh / 2f), Size(barW, bh), CornerRadius(barW))
                            }
                            if (rx < w) {
                                drawRoundRect(col.copy(alpha = 0.75f), Offset(rx, cy - bh / 2f), Size(barW, bh), CornerRadius(barW))
                            }
                        }
                    }

                    AudioVisualStyle.PRISM_BARS -> {
                        val bars = 26
                        val slot = w / bars
                        val barW = slot * 0.56f
                        for (i in 0 until bars) {
                            val t = i / (bars - 1f)
                            val bandIdx = (t * (bandEnergies.size - 1)).toInt().coerceIn(0, bandEnergies.lastIndex)
                            val mod = 0.35f + 0.65f * abs(sin(phase * 1.5f + i * 0.38f))
                            val amp = ((bandEnergies[bandIdx] * 0.65f + bassNorm * 0.35f) * mod).coerceIn(0.12f, 1f)
                            val bh = h * 0.86f * amp
                            val x = i * slot + (slot - barW) / 2f
                            val col = lerpColor(primaryColor, secondaryColor, t)
                            drawRoundRect(
                                brush = Brush.verticalGradient(listOf(col, secondaryColor.copy(alpha = 0.45f))),
                                topLeft = Offset(x, cy - bh / 2f),
                                size = Size(barW, bh),
                                cornerRadius = CornerRadius(barW / 2f)
                            )
                        }
                    }

                    AudioVisualStyle.SILK_WAVE -> {
                        for (waveIdx in 0..2) {
                            val path = Path()
                            val steps = 48
                            val ampScale = (0.34f - waveIdx * 0.06f) * h * (0.5f + bassNorm * 0.7f)
                            val freq = 2.2f + waveIdx * 0.9f
                            val wavePhase = phase * (1f + waveIdx * 0.35f) + waveIdx * 1.4f
                            for (s in 0..steps) {
                                val x = (s / steps.toFloat()) * w
                                val env = sin((s / steps.toFloat()) * PI).toFloat()
                                val y = cy + sin((s / steps.toFloat()) * freq * PI.toFloat() + wavePhase) * ampScale * env
                                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.92f - waveIdx * 0.25f),
                                        secondaryColor.copy(alpha = 0.92f - waveIdx * 0.25f)
                                    )
                                ),
                                style = Stroke(width = (3.4f - waveIdx * 0.7f).dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    AudioVisualStyle.CYBER_MATRIX -> {
                        val pts = 24
                        val path = Path()
                        val dotOffsets = mutableListOf<Offset>()
                        for (i in 0 until pts) {
                            val t = i / (pts - 1f)
                            val bandIdx = (t * (bandEnergies.size - 1)).toInt().coerceIn(0, bandEnergies.lastIndex)
                            val v = (0.25f + 0.75f * abs(cos(phase * 1.8f + i * 0.55f))) * (bandEnergies[bandIdx] * 0.65f + bassNorm * 0.35f)
                            val x = t * w
                            val y = h * 0.88f - v * (h * 0.74f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            if (i % 3 == 0) dotOffsets.add(Offset(x, y))
                        }
                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor)),
                            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        dotOffsets.forEach { pt ->
                            drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = pt)
                            drawCircle(color = Color.White, radius = 1.6.dp.toPx(), center = pt)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Text(
                    text = "${style.label} · ${audioState.activePreset}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMono,
                    color = primaryColor
                )
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * c,
        green = a.green + (b.green - a.green) * c,
        blue = a.blue + (b.blue - a.blue) * c,
        alpha = a.alpha + (b.alpha - a.alpha) * c
    )
}

// ============================================================================
// 3. CUSTOM APP START CIRCULAR BRAND LAUNCH ANIMATION
// ============================================================================

@Composable
fun BrandLaunchSplash(
    onFinished: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    if (visuals.isLiteMode) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    var started by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = when {
            exiting -> 1.16f
            started -> 1f
            else -> 0.55f
        },
        animationSpec = ColorOsMotion.fluidSpring(),
        label = "splashLogoScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = when {
            exiting -> 0f
            started -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (exiting) 260 else 320, easing = ColorOsMotion.FluidEaseOut),
        label = "splashAlpha"
    )
    val ringRotation by rememberInfiniteTransition(label = "splashOrbit").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "splashOrbitDeg"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(680L)
        exiting = true
        delay(270L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(visuals.bgBase.copy(alpha = contentAlpha.coerceIn(0f, 1f)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = logoScale
                scaleY = logoScale
                alpha = contentAlpha
            }
        ) {
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(visuals.accent.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
                    rotateArcDraw(ringRotation, visuals.accent)
                }
                Image(
                    painter = painterResource(R.drawable.evorez_logo_circular),
                    contentDescription = "Evorez Circular Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, visuals.accent.copy(alpha = 0.55f), CircleShape)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "EZD MANAGER",
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "EVOREZ · EVOLVE & RISE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMono,
                color = visuals.accent
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateArcDraw(deg: Float, accent: Color) {
    drawArc(
        brush = Brush.sweepGradient(
            listOf(Color.Transparent, accent.copy(alpha = 0.85f), Color.White.copy(alpha = 0.9f), Color.Transparent)
        ),
        startAngle = deg,
        sweepAngle = 210f,
        useCenter = false,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )
}

// ============================================================================
// 4. 5-TAP BRAND LOGO DEVELOPER EASTER EGG DIALOG
// ============================================================================

@Composable
fun DeveloperEggDialog(onDismiss: () -> Unit) {
    val visuals = LocalEzdVisuals.current
    GlassDialog(
        title = "Evorez System Secret",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.evorez_logo_circular),
                contentDescription = "Evorez Circular Emblem",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(2.dp, visuals.accent, CircleShape)
            )
            Text(
                text = "𝙳𝚎𝚟𝚎𝚕𝚘𝚙𝚎𝚛 𝚖𝚘𝚍𝚎 𝙴𝚗𝚊𝚋𝚕𝚎𝚍 \n𝐁𝐔𝐓 𝐇𝐄𝐑𝐄'𝐒 𝐍𝐎𝐓𝐇𝐈𝐍𝐆 𝐅𝐎𝐑 𝐃𝐄𝐕𝐄𝐋𝐎𝐏𝐄𝐑 😂",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
