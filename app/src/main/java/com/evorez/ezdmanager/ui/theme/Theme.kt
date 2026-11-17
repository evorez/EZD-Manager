package com.evorez.ezdmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.model.ThemeMode

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold)
)

@Immutable
data class EzdVisualTokens(
    val isDark: Boolean,
    val isLiteMode: Boolean,
    val blurAllowed: Boolean,
    val gradientsAllowed: Boolean,
    val bgTopGlow: Color,
    val bgBase: Color,
    val bgBottomGlow: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorder: Color,
    val subtleRedAccent: Color,
    val iconGlowColor: Color,
    val pillBackground: Color,
    val pillActiveGlass: Color,
    val successColor: Color,
    val warningColor: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val LocalEzdVisuals = staticCompositionLocalOf {
    buildVisualTokens(ThemeMode.DARK, systemDark = true)
}

fun buildVisualTokens(mode: ThemeMode, systemDark: Boolean): EzdVisualTokens {
    val resolvedDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LITE -> true
    }

    return when {
        mode == ThemeMode.LITE -> {
            // Lite Mode: Zero blur, zero decorative gradients, clean red-black surfaces, warm beige/off-white text & surfaces
            EzdVisualTokens(
                isDark = true,
                isLiteMode = true,
                blurAllowed = false,
                gradientsAllowed = false,
                bgTopGlow = Color(0xFF0A0506),
                bgBase = Color(0xFF0A0506),
                bgBottomGlow = Color(0xFF0A0506),
                glassSurface = Color(0xFF170B0D),
                glassSurfaceElevated = Color(0xFF211013),
                glassBorder = Color(0x55D93848),
                subtleRedAccent = Color(0xFFE04856),
                iconGlowColor = Color(0xFFE04856),
                pillBackground = Color(0xFF1A0D10),
                pillActiveGlass = Color(0x36E04856),
                successColor = Color(0xFF4ADE80),
                warningColor = Color(0xFFFBBF24),
                textPrimary = Color(0xFFF5ECE0),     // Warm beige/off-white text
                textSecondary = Color(0xFFD6C7B6)    // Muted warm beige
            )
        }
        resolvedDark -> {
            // Primary AMOLED Black + Frosted Glass + Subtle Red Accent
            EzdVisualTokens(
                isDark = true,
                isLiteMode = false,
                blurAllowed = true,
                gradientsAllowed = true,
                bgTopGlow = Color(0xFF2B070D),
                bgBase = Color(0xFF000000),
                bgBottomGlow = Color(0xFF160408),
                glassSurface = Color(0xB80E0E13),
                glassSurfaceElevated = Color(0xD617171F),
                glassBorder = Color(0x3DE03E4D),
                subtleRedAccent = Color(0xFFE53949),
                iconGlowColor = Color(0xFFE53949),
                pillBackground = Color(0xCC0D0D12),
                pillActiveGlass = Color(0x38E53949),
                successColor = Color(0xFF34D399),
                warningColor = Color(0xFFFBBF24),
                textPrimary = Color(0xFFF5F6FA),
                textSecondary = Color(0xFFAAB2C2)
            )
        }
        else -> {
            // Light Mode: Crisp translucent white glass + subtle red accent
            EzdVisualTokens(
                isDark = false,
                isLiteMode = false,
                blurAllowed = true,
                gradientsAllowed = true,
                bgTopGlow = Color(0xFFFFEAEF),
                bgBase = Color(0xFFF6F7FA),
                bgBottomGlow = Color(0xFFECEFF6),
                glassSurface = Color(0xD4FFFFFF),
                glassSurfaceElevated = Color(0xEBFFFFFF),
                glassBorder = Color(0x2CD92B3C),
                subtleRedAccent = Color(0xFFD92B3C),
                iconGlowColor = Color(0xFFD92B3C),
                pillBackground = Color(0xE0FFFFFF),
                pillActiveGlass = Color(0x20D92B3C),
                successColor = Color(0xFF15803D),
                warningColor = Color(0xFFB45309),
                textPrimary = Color(0xFF12141A),
                textSecondary = Color(0xFF535B6B)
            )
        }
    }
}

private fun buildColorScheme(tokens: EzdVisualTokens): ColorScheme {
    return if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.subtleRedAccent,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = tokens.subtleRedAccent.copy(alpha = 0.20f),
            onPrimaryContainer = tokens.textPrimary,
            secondary = if (tokens.isLiteMode) Color(0xFFE8D8C5) else Color(0xFFFF7582),
            onSecondary = Color(0xFF140B0C),
            background = tokens.bgBase,
            onBackground = tokens.textPrimary,
            surface = tokens.glassSurface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.glassSurfaceElevated,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.glassBorder,
            outlineVariant = if (tokens.isLiteMode) Color(0x33F5ECE0) else Color(0x24FFFFFF),
            error = Color(0xFFFF5A6E),
            onError = Color(0xFFFFFFFF)
        )
    } else {
        lightColorScheme(
            primary = tokens.subtleRedAccent,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = tokens.subtleRedAccent.copy(alpha = 0.14f),
            onPrimaryContainer = tokens.textPrimary,
            secondary = Color(0xFF8C2D38),
            onSecondary = Color(0xFFFFFFFF),
            background = tokens.bgBase,
            onBackground = tokens.textPrimary,
            surface = tokens.glassSurface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.glassSurfaceElevated,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.glassBorder,
            outlineVariant = Color(0x1E000000),
            error = Color(0xFFDC2626),
            onError = Color(0xFFFFFFFF)
        )
    }
}

val EzdTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp
    )
)

val EzdShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun EzdTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val tokens = buildVisualTokens(themeMode, systemDark)
    val colorScheme = buildColorScheme(tokens)

    CompositionLocalProvider(LocalEzdVisuals provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EzdTypography,
            shapes = EzdShapes,
            content = content
        )
    }
}
