package com.evorez.ezdmanager.ui.theme

import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.model.AccentColorPreset
import com.evorez.ezdmanager.model.AppAnimStyle
import com.evorez.ezdmanager.model.AppFontStyle
import com.evorez.ezdmanager.model.BackgroundTexturePreset
import com.evorez.ezdmanager.model.EzdThemeStyle
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
    val blurIntensity: Float = 0.78f,
    val reduceMotion: Boolean = false,
    val animStyle: AppAnimStyle = AppAnimStyle.IOS_CARD_MORPH,
    val bgTopGlow: Color,
    val bgBase: Color,
    val bgBottomGlow: Color,
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorder: Color,
    val accent: Color,
    val subtleRedAccent: Color,
    val iconGlowColor: Color,
    val pillBackground: Color,
    val pillActiveGlass: Color,
    val successColor: Color,
    val warningColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val solidSurfaceBase: Color = bgBase,
    val solidSurfaceElevated: Color = bgBase,
    val texturePreset: BackgroundTexturePreset = BackgroundTexturePreset.PLAIN,
    val textureOpacity: Float = 0.35f,
    val fontStyle: AppFontStyle = AppFontStyle.SYSTEM
)

val LocalEzdVisuals = staticCompositionLocalOf {
    buildVisualTokens(EzdThemeStyle.AMOLED_OBSIDIAN, ThemeMode.DARK, systemDark = true)
}

fun buildVisualTokens(
    style: EzdThemeStyle,
    mode: ThemeMode = ThemeMode.DARK,
    systemDark: Boolean = true,
    accentPreset: AccentColorPreset = AccentColorPreset.THEME_DEFAULT,
    blurIntensity: Float = 0.78f,
    reduceMotion: Boolean = false,
    animStyle: AppAnimStyle = AppAnimStyle.IOS_CARD_MORPH,
    texturePreset: BackgroundTexturePreset = BackgroundTexturePreset.PLAIN,
    textureOpacity: Float = 0.35f,
    fontStyle: AppFontStyle = AppFontStyle.SYSTEM
): EzdVisualTokens {
    val raw = buildBaseVisualTokens(style, mode, systemDark)
    val customAccent = accentPreset.argb?.let { Color(it) }
    val clampedBlur = if (raw.isLiteMode) 0f else blurIntensity.coerceIn(0f, 1f)
    val solidBase = opaqueBlend(raw.glassSurface, raw.bgBase)
    val solidElevated = opaqueBlend(raw.glassSurfaceElevated, raw.bgBase)
    return raw.copy(
        blurAllowed = raw.blurAllowed && clampedBlur > 0.03f,
        blurIntensity = clampedBlur,
        reduceMotion = reduceMotion || raw.isLiteMode,
        animStyle = animStyle,
        accent = customAccent ?: raw.accent,
        subtleRedAccent = customAccent ?: raw.subtleRedAccent,
        iconGlowColor = customAccent ?: raw.iconGlowColor,
        pillActiveGlass = customAccent?.copy(alpha = 0.24f) ?: raw.pillActiveGlass,
        solidSurfaceBase = solidBase,
        solidSurfaceElevated = solidElevated,
        texturePreset = texturePreset,
        textureOpacity = textureOpacity.coerceIn(0f, 1f),
        fontStyle = fontStyle
    )
}

private fun opaqueBlend(foreground: Color, backdrop: Color): Color {
    val a = foreground.alpha
    return Color(
        red = foreground.red * a + backdrop.red * (1f - a),
        green = foreground.green * a + backdrop.green * (1f - a),
        blue = foreground.blue * a + backdrop.blue * (1f - a),
        alpha = 1f
    )
}

private fun buildBaseVisualTokens(
    style: EzdThemeStyle,
    mode: ThemeMode = ThemeMode.DARK,
    systemDark: Boolean = true
): EzdVisualTokens {
    if (mode == ThemeMode.LITE) {
        return EzdVisualTokens(
            isDark = true,
            isLiteMode = true,
            blurAllowed = false,
            gradientsAllowed = false,
            blurIntensity = 0f,
            reduceMotion = true,
            bgTopGlow = Color(0xFF0A0607),
            bgBase = Color(0xFF0A0607),
            bgBottomGlow = Color(0xFF0A0607),
            glassSurface = Color(0xFF150B0D),
            glassSurfaceElevated = Color(0xFF1E1013),
            glassBorder = Color(0x3CF5ECE0),
            accent = Color(0xFFD64552),
            subtleRedAccent = Color(0xFFD64552),
            iconGlowColor = Color(0xFFD64552),
            pillBackground = Color(0xFF180D10),
            pillActiveGlass = Color(0x28D64552),
            successColor = Color(0xFF4ADE80),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF5ECE0),
            textSecondary = Color(0xFFD0C1B0)
        )
    }

    // Determine effective style if user switched ThemeMode between Dark/Light/System independently
    val wantDark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LITE -> true
    }

    val effectiveStyle = when {
        wantDark && !style.prefersDark -> EzdThemeStyle.AMOLED_OBSIDIAN
        !wantDark && style.prefersDark -> EzdThemeStyle.MILK_GLASS
        else -> style
    }

    return when (effectiveStyle) {
        // --- 5 WHITE / LIGHT THEMES ---
        EzdThemeStyle.MILK_GLASS -> EzdVisualTokens(
            isDark = false,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFFEAF2FF),
            bgBase = Color(0xFFF4F6FA),
            bgBottomGlow = Color(0xFFE9EEF7),
            glassSurface = Color(0xCCFFFFFF),
            glassSurfaceElevated = Color(0xEBFFFFFF),
            glassBorder = Color(0x241B253B),
            accent = Color(0xFF2563EB),
            subtleRedAccent = Color(0xFF2563EB),
            iconGlowColor = Color(0xFF3B82F6),
            pillBackground = Color(0xE8FFFFFF),
            pillActiveGlass = Color(0x1E2563EB),
            successColor = Color(0xFF16A34A),
            warningColor = Color(0xFFD97706),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569)
        )

        EzdThemeStyle.WARM_BEIGE -> EzdVisualTokens(
            isDark = false,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFFF7EDE0),
            bgBase = Color(0xFFF5EFE6),
            bgBottomGlow = Color(0xFFEDE2D3),
            glassSurface = Color(0xD6FBF7F0),
            glassSurfaceElevated = Color(0xEDFFFDF9),
            glassBorder = Color(0x296E473B),
            accent = Color(0xFF9A5B3C),
            subtleRedAccent = Color(0xFF9A5B3C),
            iconGlowColor = Color(0xFFB36E4B),
            pillBackground = Color(0xEAFAF4EB),
            pillActiveGlass = Color(0x229A5B3C),
            successColor = Color(0xFF2F7D4E),
            warningColor = Color(0xFFB45309),
            textPrimary = Color(0xFF2C1E16),
            textSecondary = Color(0xFF6B5548)
        )

        EzdThemeStyle.ARCTIC_MINT -> EzdVisualTokens(
            isDark = false,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFFE2FAF2),
            bgBase = Color(0xFFF1F9F6),
            bgBottomGlow = Color(0xFFE4F2EE),
            glassSurface = Color(0xD2FFFFFF),
            glassSurfaceElevated = Color(0xEEFFFFFF),
            glassBorder = Color(0x260F766E),
            accent = Color(0xFF0D9488),
            subtleRedAccent = Color(0xFF0D9488),
            iconGlowColor = Color(0xFF14B8A6),
            pillBackground = Color(0xE8F8FFFC),
            pillActiveGlass = Color(0x200D9488),
            successColor = Color(0xFF15803D),
            warningColor = Color(0xFFD97706),
            textPrimary = Color(0xFF0F2926),
            textSecondary = Color(0xFF476965)
        )

        EzdThemeStyle.PEARL_ROSE -> EzdVisualTokens(
            isDark = false,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFFFCEBF0),
            bgBase = Color(0xFFFAF4F6),
            bgBottomGlow = Color(0xFFF3E9ED),
            glassSurface = Color(0xD4FFFFFF),
            glassSurfaceElevated = Color(0xEDFFFFFF),
            glassBorder = Color(0x26883C52),
            accent = Color(0xFFBE3B60),
            subtleRedAccent = Color(0xFFBE3B60),
            iconGlowColor = Color(0xFFD14D72),
            pillBackground = Color(0xEAFFF9FA),
            pillActiveGlass = Color(0x1EBE3B60),
            successColor = Color(0xFF15803D),
            warningColor = Color(0xFFB45309),
            textPrimary = Color(0xFF281219),
            textSecondary = Color(0xFF6E4B56)
        )

        EzdThemeStyle.SKY_FROST -> EzdVisualTokens(
            isDark = false,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFFDCF0FF),
            bgBase = Color(0xFFF2F8FD),
            bgBottomGlow = Color(0xFFE6F0FA),
            glassSurface = Color(0xD4FFFFFF),
            glassSurfaceElevated = Color(0xEDFFFFFF),
            glassBorder = Color(0x260284C7),
            accent = Color(0xFF0284C7),
            subtleRedAccent = Color(0xFF0284C7),
            iconGlowColor = Color(0xFF38BDF8),
            pillBackground = Color(0xEAF7FBFF),
            pillActiveGlass = Color(0x200284C7),
            successColor = Color(0xFF16A34A),
            warningColor = Color(0xFFD97706),
            textPrimary = Color(0xFF0C1E2E),
            textSecondary = Color(0xFF4B647A)
        )

        // --- 6 BLACK / DARK THEMES ---
        EzdThemeStyle.AMOLED_OBSIDIAN -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF0E1525),
            bgBase = Color(0xFF000000),
            bgBottomGlow = Color(0xFF090B12),
            glassSurface = Color(0xB8111318),
            glassSurfaceElevated = Color(0xDE191C24),
            glassBorder = Color(0x24FFFFFF),
            accent = Color(0xFF6BA4FF),
            subtleRedAccent = Color(0xFF6BA4FF),
            iconGlowColor = Color(0xFF6BA4FF),
            pillBackground = Color(0xE0111216),
            pillActiveGlass = Color(0x26FFFFFF),
            successColor = Color(0xFF34D399),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF5F7FA),
            textSecondary = Color(0xFF9EA8B8)
        )

        EzdThemeStyle.MIDNIGHT_COBALT -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF0C1F4A),
            bgBase = Color(0xFF03050C),
            bgBottomGlow = Color(0xFF071128),
            glassSurface = Color(0xBA0C1324),
            glassSurfaceElevated = Color(0xDE131D34),
            glassBorder = Color(0x3660A5FA),
            accent = Color(0xFF3B82F6),
            subtleRedAccent = Color(0xFF3B82F6),
            iconGlowColor = Color(0xFF60A5FA),
            pillBackground = Color(0xE00C1322),
            pillActiveGlass = Color(0x303B82F6),
            successColor = Color(0xFF34D399),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF1F5F9),
            textSecondary = Color(0xFF94A3B8)
        )

        EzdThemeStyle.EMERALD_MATRIX -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF072B1F),
            bgBase = Color(0xFF000302),
            bgBottomGlow = Color(0xFF051A13),
            glassSurface = Color(0xBA0B1612),
            glassSurfaceElevated = Color(0xDE11211C),
            glassBorder = Color(0x3622C55E),
            accent = Color(0xFF22C55E),
            subtleRedAccent = Color(0xFF22C55E),
            iconGlowColor = Color(0xFF4ADE80),
            pillBackground = Color(0xE00A1410),
            pillActiveGlass = Color(0x3022C55E),
            successColor = Color(0xFF4ADE80),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF0FDF4),
            textSecondary = Color(0xFF9CA3AF)
        )

        EzdThemeStyle.TITANIUM_SMOKE -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF1F242D),
            bgBase = Color(0xFF08090C),
            bgBottomGlow = Color(0xFF12151B),
            glassSurface = Color(0xBF17191F),
            glassSurfaceElevated = Color(0xE322252D),
            glassBorder = Color(0x2EE2E8F0),
            accent = Color(0xFFCBD5E1),
            subtleRedAccent = Color(0xFFCBD5E1),
            iconGlowColor = Color(0xFF94A3B8),
            pillBackground = Color(0xE314161B),
            pillActiveGlass = Color(0x28FFFFFF),
            successColor = Color(0xFF34D399),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFFA1A1AA)
        )

        EzdThemeStyle.AMETHYST_DUSK -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF251247),
            bgBase = Color(0xFF05030A),
            bgBottomGlow = Color(0xFF140B26),
            glassSurface = Color(0xBA120E1D),
            glassSurfaceElevated = Color(0xDE1C152B),
            glassBorder = Color(0x36A78BFA),
            accent = Color(0xFFA78BFA),
            subtleRedAccent = Color(0xFFA78BFA),
            iconGlowColor = Color(0xFFC4B5FD),
            pillBackground = Color(0xE0110D1B),
            pillActiveGlass = Color(0x30A78BFA),
            successColor = Color(0xFF34D399),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF5F3FF),
            textSecondary = Color(0xFFA89EC9)
        )

        EzdThemeStyle.CRIMSON_VELVET -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF2A0912),
            bgBase = Color(0xFF000000),
            bgBottomGlow = Color(0xFF160409),
            glassSurface = Color(0xBA140C10),
            glassSurfaceElevated = Color(0xDE1E1218),
            glassBorder = Color(0x33E11D48),
            accent = Color(0xFFFB7185),
            subtleRedAccent = Color(0xFFFB7185),
            iconGlowColor = Color(0xFFF43F5E),
            pillBackground = Color(0xE0120B0E),
            pillActiveGlass = Color(0x2CF43F5E),
            successColor = Color(0xFF34D399),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFFDF2F4),
            textSecondary = Color(0xFFB8A3AA)
        )

        // --- 3 SPECIAL EDITION THEMES ---
        EzdThemeStyle.EVOREZ_REDLINE -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF26090D),
            bgBase = Color(0xFF040406),
            bgBottomGlow = Color(0xFF120608),
            glassSurface = Color(0xBF121216),
            glassSurfaceElevated = Color(0xE01A1A20),
            glassBorder = Color(0x36FFFFFF),
            accent = Color(0xFFE13242),
            subtleRedAccent = Color(0xFFE13242),
            iconGlowColor = Color(0xFFE13242),
            pillBackground = Color(0xE3101014),
            pillActiveGlass = Color(0x2EE13242),
            successColor = Color(0xFF34D399),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFF4F4F6),
            textSecondary = Color(0xFFA2A8B4)
        )

        EzdThemeStyle.CHAMPAGNE_GOLD -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF2A2008),
            bgBase = Color(0xFF040302),
            bgBottomGlow = Color(0xFF161105),
            glassSurface = Color(0xBF14110B),
            glassSurfaceElevated = Color(0xE01E1A11),
            glassBorder = Color(0x3AF59E0B),
            accent = Color(0xFFFBBF24),
            subtleRedAccent = Color(0xFFFBBF24),
            iconGlowColor = Color(0xFFF59E0B),
            pillBackground = Color(0xE3120F0A),
            pillActiveGlass = Color(0x2CFBBF24),
            successColor = Color(0xFF4ADE80),
            warningColor = Color(0xFFFDE047),
            textPrimary = Color(0xFFFEFCE8),
            textSecondary = Color(0xFFC2B79B)
        )

        EzdThemeStyle.CYBER_TELEMETRY -> EzdVisualTokens(
            isDark = true,
            isLiteMode = false,
            blurAllowed = true,
            gradientsAllowed = true,
            bgTopGlow = Color(0xFF062733),
            bgBase = Color(0xFF020408),
            bgBottomGlow = Color(0xFF081F1A),
            glassSurface = Color(0xBA0B131B),
            glassSurfaceElevated = Color(0xDE111D28),
            glassBorder = Color(0x3806B6D4),
            accent = Color(0xFF22D3EE),
            subtleRedAccent = Color(0xFF22D3EE),
            iconGlowColor = Color(0xFF06B6D4),
            pillBackground = Color(0xE00A1118),
            pillActiveGlass = Color(0x2E22D3EE),
            successColor = Color(0xFF34D399),
            warningColor = Color(0xFFFBBF24),
            textPrimary = Color(0xFFECFEFF),
            textSecondary = Color(0xFF94B0C2)
        )
    }
}

private fun buildColorScheme(tokens: EzdVisualTokens): ColorScheme {
    return if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = if (tokens.accent == Color(0xFFCBD5E1) || tokens.accent == Color(0xFFFBBF24) || tokens.accent == Color(0xFF22D3EE)) Color(0xFF0A0D12) else Color.White,
            primaryContainer = tokens.accent.copy(alpha = 0.22f),
            onPrimaryContainer = tokens.textPrimary,
            secondary = tokens.accent.copy(alpha = 0.85f),
            onSecondary = Color(0xFF0F1115),
            secondaryContainer = tokens.solidSurfaceElevated,
            onSecondaryContainer = tokens.textPrimary,
            background = tokens.bgBase,
            onBackground = tokens.textPrimary,
            surface = tokens.solidSurfaceBase,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.solidSurfaceElevated,
            onSurfaceVariant = tokens.textSecondary,
            surfaceDim = tokens.bgBase,
            surfaceBright = tokens.solidSurfaceElevated,
            surfaceContainerLowest = tokens.bgBase,
            surfaceContainerLow = tokens.solidSurfaceBase,
            surfaceContainer = tokens.solidSurfaceBase,
            surfaceContainerHigh = tokens.solidSurfaceElevated,
            surfaceContainerHighest = tokens.solidSurfaceElevated,
            outline = tokens.glassBorder,
            outlineVariant = if (tokens.isLiteMode) Color(0x33F5ECE0) else Color(0x26FFFFFF),
            error = Color(0xFFFF5A6E),
            onError = Color.White
        )
    } else {
        val subtleTintSurface = opaqueBlend(tokens.accent.copy(alpha = 0.035f), tokens.solidSurfaceBase)
        val elevatedClean = opaqueBlend(Color.White.copy(alpha = 0.94f), tokens.bgBase)
        val cleanLowest = Color.White
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = Color.White,
            primaryContainer = opaqueBlend(tokens.accent.copy(alpha = 0.14f), Color.White),
            onPrimaryContainer = tokens.textPrimary,
            secondary = tokens.accent.copy(alpha = 0.85f),
            onSecondary = Color.White,
            secondaryContainer = opaqueBlend(tokens.accent.copy(alpha = 0.10f), Color.White),
            onSecondaryContainer = tokens.textPrimary,
            background = tokens.bgBase,
            onBackground = tokens.textPrimary,
            surface = elevatedClean,
            onSurface = tokens.textPrimary,
            surfaceVariant = subtleTintSurface,
            onSurfaceVariant = tokens.textSecondary,
            surfaceDim = tokens.bgBottomGlow,
            surfaceBright = cleanLowest,
            surfaceContainerLowest = cleanLowest,
            surfaceContainerLow = elevatedClean,
            surfaceContainer = elevatedClean,
            surfaceContainerHigh = subtleTintSurface,
            surfaceContainerHighest = subtleTintSurface,
            outline = tokens.glassBorder,
            outlineVariant = Color(0x220F172A),
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }
}

fun resolveFontFamily(fontStyle: AppFontStyle): FontFamily = when (fontStyle) {
    AppFontStyle.SYSTEM -> FontFamily.Default
    AppFontStyle.SERIF -> FontFamily.Serif
    AppFontStyle.TYPEWRITER -> FontFamily.Monospace
}

fun buildEzdTypography(fontStyle: AppFontStyle): Typography {
    val family = resolveFontFamily(fontStyle)
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 27.sp,
            lineHeight = 33.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.3).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 12.sp
        )
    )
}

val EzdTypography = buildEzdTypography(AppFontStyle.SYSTEM)

val EzdShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun EzdTheme(
    themeMode: ThemeMode,
    themeStyle: EzdThemeStyle = EzdThemeStyle.AMOLED_OBSIDIAN,
    accentPreset: AccentColorPreset = AccentColorPreset.THEME_DEFAULT,
    blurIntensity: Float = 0.78f,
    reduceAnimations: Boolean = false,
    animStyle: AppAnimStyle = AppAnimStyle.IOS_CARD_MORPH,
    texturePreset: BackgroundTexturePreset = BackgroundTexturePreset.PLAIN,
    textureOpacity: Float = 0.35f,
    fontStyle: AppFontStyle = AppFontStyle.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemAnimScale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val prefersReducedMotion = reduceAnimations || systemAnimScale <= 0.01f
    val systemDark = isSystemInDarkTheme()
    val tokens = buildVisualTokens(
        style = themeStyle,
        mode = themeMode,
        systemDark = systemDark,
        accentPreset = accentPreset,
        blurIntensity = blurIntensity,
        reduceMotion = prefersReducedMotion,
        animStyle = animStyle,
        texturePreset = texturePreset,
        textureOpacity = textureOpacity,
        fontStyle = fontStyle
    )
    val colorScheme = buildColorScheme(tokens)
    val typography = buildEzdTypography(fontStyle)

    CompositionLocalProvider(LocalEzdVisuals provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = EzdShapes,
            content = content
        )
    }
}
