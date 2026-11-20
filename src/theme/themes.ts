import {
  AccentColorPreset,
  AppFontStyle,
  BackgroundTexturePreset,
  EzdThemeStyle,
  ThemeCategory,
  ThemeMode,
} from '../types/ezd';

export interface EzdThemeMeta {
  style: EzdThemeStyle;
  displayName: string;
  subtitle: string;
  category: ThemeCategory;
  prefersDark: boolean;
}

export const THEME_STYLES_META: EzdThemeMeta[] = [
  { style: EzdThemeStyle.MILK_GLASS, displayName: 'Milk Glass', subtitle: 'Clean frosted white with soft ice-blue accent', category: ThemeCategory.WHITE, prefersDark: false },
  { style: EzdThemeStyle.WARM_BEIGE, displayName: 'Warm Beige', subtitle: 'Cream parchment glass with warm espresso accent', category: ThemeCategory.WHITE, prefersDark: false },
  { style: EzdThemeStyle.ARCTIC_MINT, displayName: 'Arctic Mint', subtitle: 'Crisp pearl white with refreshing teal-emerald', category: ThemeCategory.WHITE, prefersDark: false },
  { style: EzdThemeStyle.PEARL_ROSE, displayName: 'Pearl Rose', subtitle: 'Soft alabaster glass with muted rosewood tint', category: ThemeCategory.WHITE, prefersDark: false },
  { style: EzdThemeStyle.SKY_FROST, displayName: 'Sky Frost', subtitle: 'Airy daylight frost with deep cerulean blue', category: ThemeCategory.WHITE, prefersDark: false },

  { style: EzdThemeStyle.AMOLED_OBSIDIAN, displayName: 'AMOLED Obsidian', subtitle: 'Pure #000000 black with crisp ice-cobalt accent', category: ThemeCategory.BLACK, prefersDark: true },
  { style: EzdThemeStyle.MIDNIGHT_COBALT, displayName: 'Midnight Cobalt', subtitle: 'Deep obsidian glass with sapphire blue trim', category: ThemeCategory.BLACK, prefersDark: true },
  { style: EzdThemeStyle.EMERALD_MATRIX, displayName: 'Emerald Matrix', subtitle: 'AMOLED black with clean cyber mint-emerald', category: ThemeCategory.BLACK, prefersDark: true },
  { style: EzdThemeStyle.TITANIUM_SMOKE, displayName: 'Titanium Smoke', subtitle: 'Carbon black glass with cool platinum steel', category: ThemeCategory.BLACK, prefersDark: true },
  { style: EzdThemeStyle.AMETHYST_DUSK, displayName: 'Amethyst Dusk', subtitle: 'Velvet black glass with soft indigo-violet', category: ThemeCategory.BLACK, prefersDark: true },
  { style: EzdThemeStyle.CRIMSON_VELVET, displayName: 'Crimson Velvet', subtitle: 'AMOLED black with restrained dark ruby glow', category: ThemeCategory.BLACK, prefersDark: true },

  { style: EzdThemeStyle.EVOREZ_REDLINE, displayName: 'Evorez Redline', subtitle: 'Signature obsidian steel with crimson accent line', category: ThemeCategory.SPECIAL, prefersDark: true },
  { style: EzdThemeStyle.CHAMPAGNE_GOLD, displayName: 'Champagne Gold', subtitle: 'Deep obsidian glass with warm 24K gold trim', category: ThemeCategory.SPECIAL, prefersDark: true },
  { style: EzdThemeStyle.CYBER_TELEMETRY, displayName: 'Cyber Telemetry', subtitle: 'Space-black console with dual cyan-emerald glow', category: ThemeCategory.SPECIAL, prefersDark: true },
];

export const ACCENT_PRESETS_META: { preset: AccentColorPreset; label: string; hex: string | null }[] = [
  { preset: AccentColorPreset.THEME_DEFAULT, label: 'Theme Auto', hex: null },
  { preset: AccentColorPreset.CRIMSON, label: 'Crimson', hex: '#E13242' },
  { preset: AccentColorPreset.ELECTRIC_BLUE, label: 'Cobalt', hex: '#3B82F6' },
  { preset: AccentColorPreset.CYBER_CYAN, label: 'Cyan', hex: '#22D3EE' },
  { preset: AccentColorPreset.EMERALD, label: 'Emerald', hex: '#22C55E' },
  { preset: AccentColorPreset.AMETHYST, label: 'Amethyst', hex: '#A78BFA' },
  { preset: AccentColorPreset.AMBER, label: 'Gold', hex: '#FBBF24' },
  { preset: AccentColorPreset.ROSE, label: 'Rose', hex: '#FB7185' },
];

export const TEXTURE_PRESETS_META: { preset: BackgroundTexturePreset; label: string; subtitle: string }[] = [
  { preset: BackgroundTexturePreset.PLAIN, label: 'Plain', subtitle: 'Clean ambient surface' },
  { preset: BackgroundTexturePreset.PAPER, label: 'Paper', subtitle: 'Warm tactile paper fiber grain' },
  { preset: BackgroundTexturePreset.MOON, label: 'Moon', subtitle: 'Cratered lunar stone sheen' },
  { preset: BackgroundTexturePreset.SILVER, label: 'Silver', subtitle: 'Brushed metallic silver grain' },
];

export const FONT_STYLES_META: { style: AppFontStyle; label: string }[] = [
  { style: AppFontStyle.SYSTEM, label: 'System Default' },
  { style: AppFontStyle.SERIF, label: 'Serif (Roman)' },
  { style: AppFontStyle.TYPEWRITER, label: 'Typewriter' },
];

export interface EzdVisualTokens {
  isDark: boolean;
  isLiteMode: boolean;
  blurAllowed: boolean;
  gradientsAllowed: boolean;
  blurIntensity: number;
  reduceMotion: boolean;
  bgTopGlow: string;
  bgBase: string;
  bgBottomGlow: string;
  glassSurface: string;
  glassSurfaceElevated: string;
  solidSurfaceBase: string;
  solidSurfaceElevated: string;
  glassBorder: string;
  accent: string;
  accentOnColor: string;
  pillBackground: string;
  successColor: string;
  warningColor: string;
  errorColor: string;
  textPrimary: string;
  textSecondary: string;
  texturePreset: BackgroundTexturePreset;
  textureOpacity: number;
  fontStyle: AppFontStyle;
}

function getBaseTokens(style: EzdThemeStyle, mode: ThemeMode, systemDark: boolean): EzdVisualTokens {
  if (mode === ThemeMode.LITE) {
    return {
      isDark: true,
      isLiteMode: true,
      blurAllowed: false,
      gradientsAllowed: false,
      blurIntensity: 0,
      reduceMotion: true,
      bgTopGlow: '#0A0607',
      bgBase: '#0A0607',
      bgBottomGlow: '#0A0607',
      glassSurface: '#150B0D',
      glassSurfaceElevated: '#1E1013',
      solidSurfaceBase: '#150B0D',
      solidSurfaceElevated: '#1E1013',
      glassBorder: 'rgba(245, 236, 224, 0.24)',
      accent: '#D64552',
      accentOnColor: '#FFFFFF',
      pillBackground: '#180D10',
      successColor: '#4ADE80',
      warningColor: '#FBBF24',
      errorColor: '#FF5A6E',
      textPrimary: '#F5ECE0',
      textSecondary: '#D0C1B0',
      texturePreset: BackgroundTexturePreset.PLAIN,
      textureOpacity: 0.35,
      fontStyle: AppFontStyle.SYSTEM,
    };
  }

  const wantDark =
    mode === ThemeMode.DARK ? true : mode === ThemeMode.LIGHT ? false : systemDark;

  const meta = THEME_STYLES_META.find((m) => m.style === style);
  const effectiveStyle =
    wantDark && meta && !meta.prefersDark
      ? EzdThemeStyle.AMOLED_OBSIDIAN
      : !wantDark && meta && meta.prefersDark
      ? EzdThemeStyle.MILK_GLASS
      : style;

  const baseDefaults = {
    isLiteMode: false,
    blurAllowed: true,
    gradientsAllowed: true,
    blurIntensity: 0.78,
    reduceMotion: false,
    texturePreset: BackgroundTexturePreset.PLAIN,
    textureOpacity: 0.35,
    fontStyle: AppFontStyle.SYSTEM,
  };

  switch (effectiveStyle) {
    case EzdThemeStyle.MILK_GLASS:
      return {
        ...baseDefaults,
        isDark: false,
        bgTopGlow: '#EAF2FF',
        bgBase: '#F4F6FA',
        bgBottomGlow: '#E9EEF7',
        glassSurface: 'rgba(255, 255, 255, 0.80)',
        glassSurfaceElevated: 'rgba(255, 255, 255, 0.92)',
        solidSurfaceBase: '#FFFFFF',
        solidSurfaceElevated: '#FFFFFF',
        glassBorder: 'rgba(27, 37, 59, 0.14)',
        accent: '#2563EB',
        accentOnColor: '#FFFFFF',
        pillBackground: 'rgba(255, 255, 255, 0.91)',
        successColor: '#16A34A',
        warningColor: '#D97706',
        errorColor: '#DC2626',
        textPrimary: '#0F172A',
        textSecondary: '#475569',
      };
    case EzdThemeStyle.WARM_BEIGE:
      return {
        ...baseDefaults,
        isDark: false,
        bgTopGlow: '#F7EDE0',
        bgBase: '#F5EFE6',
        bgBottomGlow: '#EDE2D3',
        glassSurface: 'rgba(251, 247, 240, 0.84)',
        glassSurfaceElevated: 'rgba(255, 253, 249, 0.93)',
        solidSurfaceBase: '#FAF6EE',
        solidSurfaceElevated: '#FFFDF9',
        glassBorder: 'rgba(110, 71, 59, 0.16)',
        accent: '#9A5B3C',
        accentOnColor: '#FFFFFF',
        pillBackground: 'rgba(250, 244, 235, 0.92)',
        successColor: '#2F7D4E',
        warningColor: '#B45309',
        errorColor: '#DC2626',
        textPrimary: '#2C1E16',
        textSecondary: '#6B5548',
      };
    case EzdThemeStyle.ARCTIC_MINT:
      return {
        ...baseDefaults,
        isDark: false,
        bgTopGlow: '#E2FAF2',
        bgBase: '#F1F9F6',
        bgBottomGlow: '#E4F2EE',
        glassSurface: 'rgba(255, 255, 255, 0.82)',
        glassSurfaceElevated: 'rgba(255, 255, 255, 0.93)',
        solidSurfaceBase: '#FCFFFE',
        solidSurfaceElevated: '#FFFFFF',
        glassBorder: 'rgba(15, 118, 110, 0.15)',
        accent: '#0D9488',
        accentOnColor: '#FFFFFF',
        pillBackground: 'rgba(248, 255, 252, 0.91)',
        successColor: '#15803D',
        warningColor: '#D97706',
        errorColor: '#DC2626',
        textPrimary: '#0F2926',
        textSecondary: '#476965',
      };
    case EzdThemeStyle.PEARL_ROSE:
      return {
        ...baseDefaults,
        isDark: false,
        bgTopGlow: '#FCEBF0',
        bgBase: '#FAF4F6',
        bgBottomGlow: '#F3E9ED',
        glassSurface: 'rgba(255, 255, 255, 0.83)',
        glassSurfaceElevated: 'rgba(255, 255, 255, 0.93)',
        solidSurfaceBase: '#FFFDFE',
        solidSurfaceElevated: '#FFFFFF',
        glassBorder: 'rgba(136, 60, 82, 0.15)',
        accent: '#BE3B60',
        accentOnColor: '#FFFFFF',
        pillBackground: 'rgba(255, 249, 250, 0.92)',
        successColor: '#15803D',
        warningColor: '#B45309',
        errorColor: '#DC2626',
        textPrimary: '#281219',
        textSecondary: '#6E4B56',
      };
    case EzdThemeStyle.SKY_FROST:
      return {
        ...baseDefaults,
        isDark: false,
        bgTopGlow: '#DCF0FF',
        bgBase: '#F2F8FD',
        bgBottomGlow: '#E6F0FA',
        glassSurface: 'rgba(255, 255, 255, 0.83)',
        glassSurfaceElevated: 'rgba(255, 255, 255, 0.93)',
        solidSurfaceBase: '#FAFDFF',
        solidSurfaceElevated: '#FFFFFF',
        glassBorder: 'rgba(2, 132, 199, 0.15)',
        accent: '#0284C7',
        accentOnColor: '#FFFFFF',
        pillBackground: 'rgba(247, 251, 255, 0.92)',
        successColor: '#16A34A',
        warningColor: '#D97706',
        errorColor: '#DC2626',
        textPrimary: '#0C1E2E',
        textSecondary: '#4B647A',
      };
    case EzdThemeStyle.MIDNIGHT_COBALT:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#0C1F4A',
        bgBase: '#03050C',
        bgBottomGlow: '#071128',
        glassSurface: 'rgba(12, 19, 36, 0.73)',
        glassSurfaceElevated: 'rgba(19, 29, 52, 0.87)',
        solidSurfaceBase: '#090F1E',
        solidSurfaceElevated: '#101A30',
        glassBorder: 'rgba(96, 165, 250, 0.21)',
        accent: '#3B82F6',
        accentOnColor: '#FFFFFF',
        pillBackground: 'rgba(12, 19, 34, 0.88)',
        successColor: '#34D399',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#F1F5F9',
        textSecondary: '#94A3B8',
      };
    case EzdThemeStyle.EMERALD_MATRIX:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#072B1F',
        bgBase: '#000302',
        bgBottomGlow: '#051A13',
        glassSurface: 'rgba(11, 22, 18, 0.73)',
        glassSurfaceElevated: 'rgba(17, 33, 28, 0.87)',
        solidSurfaceBase: '#08110E',
        solidSurfaceElevated: '#0E1C17',
        glassBorder: 'rgba(34, 197, 94, 0.21)',
        accent: '#22C55E',
        accentOnColor: '#052E16',
        pillBackground: 'rgba(10, 20, 16, 0.88)',
        successColor: '#4ADE80',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#F0FDF4',
        textSecondary: '#9CA3AF',
      };
    case EzdThemeStyle.TITANIUM_SMOKE:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#1F242D',
        bgBase: '#08090C',
        bgBottomGlow: '#12151B',
        glassSurface: 'rgba(23, 25, 31, 0.75)',
        glassSurfaceElevated: 'rgba(34, 37, 45, 0.89)',
        solidSurfaceBase: '#13151A',
        solidSurfaceElevated: '#1E2128',
        glassBorder: 'rgba(226, 232, 240, 0.18)',
        accent: '#CBD5E1',
        accentOnColor: '#0A0D12',
        pillBackground: 'rgba(20, 22, 27, 0.89)',
        successColor: '#34D399',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#F8FAFC',
        textSecondary: '#A1A1AA',
      };
    case EzdThemeStyle.AMETHYST_DUSK:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#251247',
        bgBase: '#05030A',
        bgBottomGlow: '#140B26',
        glassSurface: 'rgba(18, 14, 29, 0.73)',
        glassSurfaceElevated: 'rgba(28, 21, 43, 0.87)',
        solidSurfaceBase: '#0E0B18',
        solidSurfaceElevated: '#181226',
        glassBorder: 'rgba(167, 139, 250, 0.21)',
        accent: '#A78BFA',
        accentOnColor: '#0A0D12',
        pillBackground: 'rgba(17, 13, 27, 0.88)',
        successColor: '#34D399',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#F5F3FF',
        textSecondary: '#A89EC9',
      };
    case EzdThemeStyle.CRIMSON_VELVET:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#2A0912',
        bgBase: '#000000',
        bgBottomGlow: '#160409',
        glassSurface: 'rgba(20, 12, 16, 0.73)',
        glassSurfaceElevated: 'rgba(30, 18, 24, 0.87)',
        solidSurfaceBase: '#0E080B',
        solidSurfaceElevated: '#1A0F14',
        glassBorder: 'rgba(225, 29, 72, 0.20)',
        accent: '#FB7185',
        accentOnColor: '#1F070E',
        pillBackground: 'rgba(18, 11, 14, 0.88)',
        successColor: '#34D399',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#FDF2F4',
        textSecondary: '#B8A3AA',
      };
    case EzdThemeStyle.EVOREZ_REDLINE:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#26090D',
        bgBase: '#040406',
        bgBottomGlow: '#120608',
        glassSurface: 'rgba(18, 18, 22, 0.75)',
        glassSurfaceElevated: 'rgba(26, 26, 32, 0.88)',
        solidSurfaceBase: '#0E0E12',
        solidSurfaceElevated: '#17171D',
        glassBorder: 'rgba(255, 255, 255, 0.21)',
        accent: '#E13242',
        accentOnColor: '#FFFFFF',
        pillBackground: 'rgba(16, 16, 20, 0.89)',
        successColor: '#34D399',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#F4F4F6',
        textSecondary: '#A2A8B4',
      };
    case EzdThemeStyle.CHAMPAGNE_GOLD:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#2A2008',
        bgBase: '#040302',
        bgBottomGlow: '#161105',
        glassSurface: 'rgba(20, 17, 11, 0.75)',
        glassSurfaceElevated: 'rgba(30, 26, 17, 0.88)',
        solidSurfaceBase: '#100D08',
        solidSurfaceElevated: '#1B170F',
        glassBorder: 'rgba(245, 158, 11, 0.23)',
        accent: '#FBBF24',
        accentOnColor: '#0A0D12',
        pillBackground: 'rgba(18, 15, 10, 0.89)',
        successColor: '#4ADE80',
        warningColor: '#FDE047',
        errorColor: '#FF5A6E',
        textPrimary: '#FEFCE8',
        textSecondary: '#C2B79B',
      };
    case EzdThemeStyle.CYBER_TELEMETRY:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#062733',
        bgBase: '#020408',
        bgBottomGlow: '#081F1A',
        glassSurface: 'rgba(11, 19, 27, 0.73)',
        glassSurfaceElevated: 'rgba(17, 29, 40, 0.87)',
        solidSurfaceBase: '#080F16',
        solidSurfaceElevated: '#0F1A25',
        glassBorder: 'rgba(6, 182, 212, 0.22)',
        accent: '#22D3EE',
        accentOnColor: '#0A0D12',
        pillBackground: 'rgba(10, 17, 24, 0.88)',
        successColor: '#34D399',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#ECFEFF',
        textSecondary: '#94B0C2',
      };
    case EzdThemeStyle.AMOLED_OBSIDIAN:
    default:
      return {
        ...baseDefaults,
        isDark: true,
        bgTopGlow: '#0E1525',
        bgBase: '#000000',
        bgBottomGlow: '#090B12',
        glassSurface: 'rgba(17, 19, 24, 0.72)',
        glassSurfaceElevated: 'rgba(25, 28, 36, 0.87)',
        solidSurfaceBase: '#0C0E12',
        solidSurfaceElevated: '#161921',
        glassBorder: 'rgba(255, 255, 255, 0.14)',
        accent: '#6BA4FF',
        accentOnColor: '#0A0D12',
        pillBackground: 'rgba(17, 18, 22, 0.88)',
        successColor: '#34D399',
        warningColor: '#FBBF24',
        errorColor: '#FF5A6E',
        textPrimary: '#F5F7FA',
        textSecondary: '#9EA8B8',
      };
  }
}

export function buildVisualTokens(params: {
  style: EzdThemeStyle;
  mode: ThemeMode;
  systemDark: boolean;
  accentPreset: AccentColorPreset;
  blurIntensity: number;
  reduceMotion: boolean;
  texturePreset: BackgroundTexturePreset;
  textureOpacity: number;
  fontStyle: AppFontStyle;
}): EzdVisualTokens {
  const raw = getBaseTokens(params.style, params.mode, params.systemDark);
  const customHex = ACCENT_PRESETS_META.find((a) => a.preset === params.accentPreset)?.hex ?? null;
  const accent = customHex ?? raw.accent;
  const accentOnColor =
    accent === '#CBD5E1' || accent === '#FBBF24' || accent === '#22D3EE' || accent === '#6BA4FF'
      ? '#0A0D12'
      : '#FFFFFF';
  const clampedBlur = raw.isLiteMode ? 0 : Math.min(1, Math.max(0, params.blurIntensity));

  return {
    ...raw,
    accent,
    accentOnColor,
    blurAllowed: raw.blurAllowed && clampedBlur > 0.03,
    blurIntensity: clampedBlur,
    reduceMotion: params.reduceMotion || raw.isLiteMode,
    texturePreset: params.texturePreset,
    textureOpacity: Math.min(1, Math.max(0, params.textureOpacity)),
    fontStyle: params.fontStyle,
  };
}
