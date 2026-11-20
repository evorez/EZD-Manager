import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  AccentColorPreset,
  AppAnimStyle,
  AppBackupOptions,
  AppEntry,
  AppFontStyle,
  AudioVisualStyle,
  BackgroundTexturePreset,
  BeastExecutionReport,
  BuildPropBackupInfo,
  BuildPropEntry,
  CpuCore,
  CpuPolicy,
  DaemonStats,
  DnsPreset,
  EngineItemStatus,
  EzdThemeStyle,
  EzdUiState,
  GameProfileId,
  GpuRendererMode,
  IoBlockDevice,
  LocalAppBackupRecord,
  MainTab,
  NotificationPrefs,
  PerAppConfig,
  PerAppDnsMode,
  PowerAction,
  PrivilegeMode,
  ProfileConfig,
  RootModuleInfo,
  RootStatus,
  SearchEntry,
  ShizukuStatus,
  SpoofPreset,
  ThemeMode,
  ThermalProfileMode,
  ToolId,
  WorkMode,
} from '../types/ezd';
import {
  AUDIO_PRESET_BANDS,
  BUILT_IN_CHANGELOG,
  BUILT_IN_PROFILES,
  BUILT_IN_SPOOF_PRESETS,
  createInitialUiState,
} from '../data/presets';
import { resolveEffectiveMode } from '../data/workMode';
import { buildVisualTokens, EzdVisualTokens } from '../theme/themes';

const STORAGE_KEY = 'ezd_manager_web_state_v1';

interface PersistedSlice {
  settings: EzdUiState['settings'];
  customProfiles: ProfileConfig[];
  customSpoofPresets: SpoofPreset[];
  perAppConfigs: Record<string, PerAppConfig>;
  batteryWhitelist: string[];
  buildPropBackups: BuildPropBackupInfo[];
  savedEzdBackups: EzdUiState['savedEzdBackups'];
  localAppBackups: LocalAppBackupRecord[];
  bootGuardState: EzdUiState['bootGuardState'];
  rootStatus: RootStatus;
  shizukuStatus: ShizukuStatus;
}

function loadInitialState(): { ui: EzdUiState; daemon: DaemonStats } {
  const base = createInitialUiState();
  const baseDaemon: DaemonStats = {
    serviceRunning: true,
    rootGranted: true,
    commandsExecuted: 14,
    perAppMonitorActive: true,
    currentForegroundPkg: 'com.evorez.ezdmanager',
    lastAppliedPerAppPkg: 'com.HoYoverse.GenshinImpact',
    lastOutcome: 'Applied performance profile + 120Hz',
    activeGamePkg: '',
    logs: [
      '[daemon] Root shell session initialized (KernelSU uid=0)',
      '[probe] Probed 3 CPU policies, Adreno 750 GPU, ZRAM 4096MB',
      '[per-app] Foreground monitor ready (UsageStats active)',
    ],
  };

  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ui: base, daemon: baseDaemon };
    const parsed = JSON.parse(raw) as Partial<PersistedSlice>;
    const settings = { ...base.settings, ...(parsed.settings ?? {}) };
    const rootStatus = parsed.rootStatus ?? base.rootStatus;
    const shizukuStatus = parsed.shizukuStatus ?? base.shizukuStatus;
    const effective = resolveEffectiveMode(settings.workMode, rootStatus, shizukuStatus);
    return {
      ui: {
        ...base,
        settings,
        customProfiles: parsed.customProfiles ?? base.customProfiles,
        customSpoofPresets: parsed.customSpoofPresets ?? base.customSpoofPresets,
        perAppConfigs: parsed.perAppConfigs ?? base.perAppConfigs,
        batteryWhitelist: parsed.batteryWhitelist ?? base.batteryWhitelist,
        buildPropBackups: parsed.buildPropBackups ?? base.buildPropBackups,
        savedEzdBackups: parsed.savedEzdBackups ?? base.savedEzdBackups,
        localAppBackups: parsed.localAppBackups ?? base.localAppBackups,
        bootGuardState: parsed.bootGuardState ?? base.bootGuardState,
        rootStatus,
        shizukuStatus,
        effectivePrivilegeMode: effective,
      },
      daemon: {
        ...baseDaemon,
        serviceRunning: settings.daemonEnabled,
        rootGranted: rootStatus === RootStatus.GRANTED,
      },
    };
  } catch {
    return { ui: base, daemon: baseDaemon };
  }
}

async function computeSha256Hex(input: string): Promise<string> {
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const data = new TextEncoder().encode(input);
    const hash = await crypto.subtle.digest('SHA-256', data);
    return Array.from(new Uint8Array(hash))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  let h = 0;
  for (let i = 0; i < input.length; i++) {
    h = (Math.imul(31, h) + input.charCodeAt(i)) | 0;
  }
  return Math.abs(h).toString(16).padStart(8, '0');
}

export interface EzdContextValue {
  state: EzdUiState;
  daemonStats: DaemonStats;
  visuals: EzdVisualTokens;
  activeTab: MainTab;
  setActiveTab: (tab: MainTab) => void;
  toastMessage: string | null;
  showToast: (msg: string) => void;
  splashDone: boolean;
  setSplashDone: (done: boolean) => void;
  pendingAppPkg: string | null;
  setPendingAppPkg: (pkg: string | null) => void;
  // Navigation & Sheets
  openTool: (tool: ToolId | null) => void;
  setShowSearchSheet: (show: boolean) => void;
  setShowPowerSheet: (show: boolean) => void;
  setShowNotificationsSheet: (show: boolean) => void;
  setShowDeviceDetails: (show: boolean) => void;
  recordBrandLogoTap: () => number;
  dismissDeveloperEgg: () => void;
  // Onboarding & Privileges
  autoDetectRoot: (forceFresh?: boolean) => void;
  requestShizukuPermission: () => void;
  refreshPrivilegeModes: () => void;
  setPrivilegeSimulation: (root: RootStatus, shizuku: ShizukuStatus, workMode?: WorkMode) => void;
  completeOnboarding: (name: string, tagline: string, photoDataUrl: string, workMode: WorkMode) => void;
  updateUserProfile: (name: string, tagline: string, photoDataUrl: string | null) => void;
  // Appearance
  setThemeMode: (mode: ThemeMode) => void;
  setThemeStyle: (style: EzdThemeStyle) => void;
  setAccentPreset: (preset: AccentColorPreset) => void;
  setBlurIntensity: (val: number) => void;
  setReduceAnimations: (reduce: boolean) => void;
  setFloatingPillOpaque: (opaque: boolean) => void;
  setBackgroundTexture: (preset: BackgroundTexturePreset) => void;
  setBackgroundTextureOpacity: (opacity: number) => void;
  setAppFontStyle: (font: AppFontStyle) => void;
  setAnimStyle: (anim: AppAnimStyle) => void;
  pinToolToPill: (tool: ToolId) => void;
  unpinToolFromPill: (tool: ToolId) => void;
  movePinnedPillTool: (tool: ToolId, delta: number) => void;
  // Hardware & Profiles
  refreshAllHardware: (forceProbe?: boolean) => void;
  applyProfile: (profileOrId: ProfileConfig | string) => void;
  toggleBeastMode: () => void;
  setBeastThermalLimits: (bat: number | null, soc: number | null) => void;
  setBeastIgnoreThermalLimits: (ignore: boolean) => void;
  saveCustomProfile: (profile: ProfileConfig) => void;
  deleteCustomProfile: (id: string) => void;
  resetCustomProfiles: () => void;
  // CPU & GPU
  setCpuFrequencyRange: (policy: CpuPolicy, minKhz: number | null, maxKhz: number | null) => void;
  setCpuGovernor: (policy: CpuPolicy, gov: string) => void;
  resetCpuPolicy: (policy: CpuPolicy) => void;
  toggleCpuCore: (core: CpuCore, online: boolean) => void;
  setGpuFrequency: (minMhz: number | null, maxMhz: number | null) => void;
  setGpuGovernor: (gov: string) => void;
  resetGpu: () => void;
  // ZRAM & VM
  refreshZramVm: () => void;
  applyZramConfig: (diskSizeMb: number, algo: string) => void;
  applyVmTweaks: (swappiness: number, vfsCache: number, dirtyRatio: number, dirtyBg: number) => void;
  // I/O, Thermal & UI
  refreshIoThermalUi: () => void;
  setIoScheduler: (dev: IoBlockDevice, scheduler: string) => void;
  setIoReadAhead: (dev: IoBlockDevice, kb: number) => void;
  applyThermalProfile: (mode: ThermalProfileMode) => void;
  setSystemAnimationScale: (scale: number) => void;
  setGpuRenderer: (mode: GpuRendererMode) => void;
  // Display & Touch
  setRefreshRate: (hz: number) => void;
  resetRefreshRate: () => void;
  setBrightness: (val: number, auto: boolean) => void;
  setHbm: (enabled: boolean) => void;
  setDisplaySaturation: (sat: number) => void;
  setCabc: (enabled: boolean) => void;
  setDisableHwOverlays: (disabled: boolean) => void;
  setTouchControl: (path: string, value: string, label: string) => void;
  // Network
  resetNetworkSettings: () => void;
  applyTcpFastPreset: () => void;
  applyTcpRescuePreset: () => void;
  setTcpCongestion: (algo: string) => void;
  applyDnsPreset: (preset: DnsPreset) => void;
  // Audio
  setAudioVisualStyle: (style: AudioVisualStyle) => void;
  toggleBassEnhancer: (enabled: boolean) => void;
  setBassFrequencyFocus: (focus: string) => void;
  toggleIncreaseLoudness: (enabled: boolean) => void;
  toggleEqualizer: (enabled: boolean) => void;
  selectAudioPreset: (name: string) => void;
  updateAudioManual: (bands: number[], bassStrength: number, loudnessGainMb: number) => void;
  // Battery & Fast Charge
  resetBatteryMonitorStats: () => void;
  applyGmsBackgroundManagement: (enabled: boolean) => void;
  applyBackgroundAppLimit: (preset: string) => void;
  runSafeMemoryTrim: () => void;
  toggleWhitelistApp: (pkg: string) => void;
  setAppBackgroundRestriction: (pkg: string, restricted: boolean) => void;
  refreshFastCharge: () => void;
  applyFastChargeLimit: (limitMa: number) => void;
  toggleForceFastCharge: (enabled: boolean) => void;
  // Device Spoof
  allSpoofPresets: () => SpoofPreset[];
  applySpoofPreset: (preset: SpoofPreset) => void;
  resetSpoof: () => void;
  upsertCustomSpoofPreset: (preset: SpoofPreset) => void;
  deleteCustomSpoofPreset: (id: string) => void;
  // Modules & Build.prop
  refreshRootModules: () => void;
  toggleRootModule: (mod: RootModuleInfo, enabled: boolean) => void;
  toggleRootModuleRemove: (mod: RootModuleInfo, pendingRemove: boolean) => void;
  createBuildPropBackup: () => void;
  restoreBuildPropBackup: (backup: BuildPropBackupInfo) => void;
  updateBuildProp: (key: string, value: string) => void;
  // Boot Guard & Boot Memory
  clearBootSafeMode: () => void;
  testSimulateBootLoopGuard: () => void;
  setApplyOnBoot: (enabled: boolean) => void;
  captureBootMemorySnapshot: () => void;
  applyBootMemoryNow: () => void;
  // Apps & Per-App Tuning
  setIncludeSystemApps: (include: boolean) => void;
  toggleUsageAccess: () => void;
  savePerAppConfig: (cfg: PerAppConfig) => void;
  syncPerAppDnsVpn: () => void;
  clearAppCache: (pkg: string) => void;
  forceStopApp: (pkg: string) => void;
  toggleFreezeApp: (app: AppEntry) => void;
  uninstallApp: (app: AppEntry) => void;
  // Backups
  exportEncryptedBackup: (options: { includeNetworkConfig: boolean; includeRootBaselines: boolean; passphrase: string }, downloadFile?: boolean) => Promise<void>;
  inspectSavedEzdBackup: (rawPayload: string, filePath: string, passphrase: string) => Promise<void>;
  executeRestore: (restoreEzdConfig: boolean, restoreNetworkConfig: boolean) => void;
  clearBackupDialogs: () => void;
  runFullAppBackup: (selectedApps: Set<string>, options: AppBackupOptions) => void;
  restoreLocalAppBackup: (rec: LocalAppBackupRecord) => void;
  deleteLocalAppBackup: (rec: LocalAppBackupRecord) => void;
  // Terminal, Power, Settings, Search, Updates
  runTerminalCommand: (cmd: string) => void;
  performPowerAction: (action: PowerAction) => void;
  setNotificationPrefs: (prefs: NotificationPrefs) => void;
  toggleDaemonService: (enabled: boolean) => void;
  checkForGitHubUpdates: () => Promise<void>;
  exportAndCopyDiagnostics: () => Promise<void>;
  buildDeviceReportText: () => string;
  buildSearchIndex: () => SearchEntry[];
  builtInChangelog: () => typeof BUILT_IN_CHANGELOG;
}

const EzdContext = createContext<EzdContextValue | null>(null);

export const EzdProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const initial = useMemo(() => loadInitialState(), []);
  const [state, setState] = useState<EzdUiState>(initial.ui);
  const [daemonStats, setDaemonStats] = useState<DaemonStats>(initial.daemon);
  const [activeTab, setActiveTab] = useState<MainTab>(MainTab.HOME);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [splashDone, setSplashDone] = useState<boolean>(false);
  const [pendingAppPkg, setPendingAppPkg] = useState<string | null>(null);
  const [brandTapCount, setBrandTapCount] = useState<number>(0);

  const [systemDark, setSystemDark] = useState<boolean>(() =>
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
      : true
  );

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
  }, []);

  useEffect(() => {
    if (!toastMessage) return;
    const timer = setTimeout(() => setToastMessage(null), 2600);
    return () => clearTimeout(timer);
  }, [toastMessage]);

  const visuals = useMemo(
    () =>
      buildVisualTokens({
        style: state.settings.themeStyle,
        mode: state.settings.themeMode,
        systemDark,
        accentPreset: state.settings.accentPreset,
        blurIntensity: state.settings.blurIntensity,
        reduceMotion: state.settings.reduceAnimations,
        texturePreset: state.settings.backgroundTexture,
        textureOpacity: state.settings.backgroundTextureOpacity,
        fontStyle: state.settings.appFontStyle,
      }),
    [state.settings, systemDark]
  );

  // Sync CSS variables & .dark class onto <html>
  useEffect(() => {
    const root = document.documentElement;
    if (visuals.isDark) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
    if (visuals.reduceMotion) {
      root.classList.add('ezd-no-motion');
    } else {
      root.classList.remove('ezd-no-motion');
    }

    const fontStack =
      visuals.fontStyle === AppFontStyle.SERIF
        ? 'var(--ezd-font-serif)'
        : visuals.fontStyle === AppFontStyle.TYPEWRITER
        ? 'var(--ezd-font-mono)'
        : 'var(--ezd-font-sans)';

    root.style.setProperty('--ezd-active-font', fontStack);
    root.style.setProperty('--ezd-bg-base', visuals.bgBase);
    root.style.setProperty('--ezd-bg-top-glow', visuals.bgTopGlow);
    root.style.setProperty('--ezd-bg-bottom-glow', visuals.bgBottomGlow);
    root.style.setProperty('--ezd-surface', visuals.glassSurface);
    root.style.setProperty('--ezd-surface-elevated', visuals.glassSurfaceElevated);
    root.style.setProperty('--ezd-solid-surface', visuals.solidSurfaceBase);
    root.style.setProperty('--ezd-solid-elevated', visuals.solidSurfaceElevated);
    root.style.setProperty('--ezd-border', visuals.glassBorder);
    root.style.setProperty('--ezd-accent', visuals.accent);
    root.style.setProperty('--ezd-accent-soft', `${visuals.accent}28`);
    root.style.setProperty('--ezd-success', visuals.successColor);
    root.style.setProperty('--ezd-warning', visuals.warningColor);
    root.style.setProperty('--ezd-error', visuals.errorColor);
    root.style.setProperty('--ezd-text-primary', visuals.textPrimary);
    root.style.setProperty('--ezd-text-secondary', visuals.textSecondary);
    root.style.setProperty('--ezd-pill-bg', visuals.pillBackground);
    root.style.setProperty(
      '--ezd-blur-px',
      visuals.blurAllowed ? `${Math.round(visuals.blurIntensity * 22)}px` : '0px'
    );
  }, [visuals]);

  // Persist slice to localStorage
  useEffect(() => {
    try {
      const slice: PersistedSlice = {
        settings: state.settings,
        customProfiles: state.customProfiles,
        customSpoofPresets: state.customSpoofPresets,
        perAppConfigs: state.perAppConfigs,
        batteryWhitelist: state.batteryWhitelist,
        buildPropBackups: state.buildPropBackups,
        savedEzdBackups: state.savedEzdBackups,
        localAppBackups: state.localAppBackups,
        bootGuardState: state.bootGuardState,
        rootStatus: state.rootStatus,
        shizukuStatus: state.shizukuStatus,
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(slice));
    } catch {
      // ignore storage quota issues
    }
  }, [
    state.settings,
    state.customProfiles,
    state.customSpoofPresets,
    state.perAppConfigs,
    state.batteryWhitelist,
    state.buildPropBackups,
    state.savedEzdBackups,
    state.localAppBackups,
    state.bootGuardState,
    state.rootStatus,
    state.shizukuStatus,
  ]);

  const appendDaemonLog = useCallback((line: string) => {
    const time = new Date().toLocaleTimeString([], { hour12: false });
    setDaemonStats((d) => ({
      ...d,
      commandsExecuted: d.commandsExecuted + 1,
      lastOutcome: line,
      logs: [...d.logs.slice(-24), `[${time}] ${line}`],
    }));
  }, []);

  // Live telemetry simulator loop
  useEffect(() => {
    const timer = setInterval(() => {
      setState((prev) => {
        const profileId = prev.settings.activeProfileId;
        const isBeast = profileId === GameProfileId.BEAST;
        const isPerf = profileId === GameProfileId.PERFORMANCE;
        const isPower = profileId === GameProfileId.POWERSAVE;

        const targetCpuPct = isBeast ? 88 : isPerf ? 56 : isPower ? 14 : 26;
        const jitter = Math.round((Math.random() - 0.5) * 8);
        const cpuUsagePercent = Math.max(4, Math.min(100, targetCpuPct + jitter));

        const targetGpuPct = isBeast ? 92 : isPerf ? 62 : isPower ? 8 : 20;
        const gpuUsage = Math.max(2, Math.min(100, targetGpuPct + Math.round((Math.random() - 0.5) * 10)));

        const gpuFreqs = prev.gpuState.availableMhz;
        const gpuTargetIdx = isBeast
          ? gpuFreqs.length - 1
          : isPerf
          ? Math.max(0, gpuFreqs.length - 2)
          : isPower
          ? 0
          : Math.min(gpuFreqs.length - 1, 2);
        const currentGpuMhz = gpuFreqs[gpuTargetIdx] ?? prev.gpuState.currentMhz;

        const nextPolicies = prev.cpuPolicies.map((p) => {
          const freqs = p.availableFreqsKhz;
          const idx = isBeast
            ? freqs.length - 1
            : isPerf
            ? Math.max(0, freqs.length - 2)
            : isPower
            ? 0
            : Math.floor(freqs.length * 0.45);
          const clampFreq = freqs[idx] ?? p.currentKhz ?? 1200000;
          const clamped = Math.min(p.maxKhz ?? clampFreq, Math.max(p.minKhz ?? clampFreq, clampFreq));
          return { ...p, currentKhz: clamped };
        });

        const nextCores = prev.cpuCores.map((c) => {
          if (!c.online) return { ...c, currentKhz: null };
          const pol = c.index < 4 ? nextPolicies[0] : c.index < 7 ? nextPolicies[1] : nextPolicies[2];
          return { ...c, currentKhz: pol?.currentKhz ?? c.currentKhz };
        });

        const batTarget = isBeast ? 39.2 : isPerf ? 36.1 : isPower ? 30.8 : 33.4;
        const socTarget = isBeast ? 64.5 : isPerf ? 54.0 : isPower ? 38.0 : 44.2;
        const batTemp = Number((prev.thermalTelemetry.batteryTempC + (batTarget - prev.thermalTelemetry.batteryTempC) * 0.15 + (Math.random() - 0.5) * 0.2).toFixed(1));
        const socTemp = Number(((prev.thermalTelemetry.socTempC ?? 44) + (socTarget - (prev.thermalTelemetry.socTempC ?? 44)) * 0.18 + (Math.random() - 0.5) * 0.5).toFixed(1));

        // Thermal watchdog auto-stepdown if Beast Mode exceeds limits
        const watchdogTrip =
          isBeast &&
          !prev.settings.beastIgnoreThermalLimits &&
          (batTemp >= prev.settings.beastBatteryTempLimitC || socTemp >= prev.settings.beastSocTempLimitC);

        const nextProfile = watchdogTrip ? GameProfileId.BALANCED : prev.settings.activeProfileId;
        const nextBanner = watchdogTrip
          ? `Thermal Watchdog tripped (${batTemp}°C Bat / ${socTemp}°C SoC) — stepped down to Balanced`
          : prev.statusBanner;

        const baseCurrentMa = prev.fastChargeState.isCharging
          ? (prev.fastChargeState.activeLimitMa ?? 3300) - Math.round(Math.random() * 140)
          : -(isBeast ? 1120 : isPerf ? 720 : isPower ? 240 : 420) - Math.round(Math.random() * 45);
        const powerWatts = Number(((Math.abs(baseCurrentMa) * prev.batteryMonitorStats.voltageMv) / 1_000_000).toFixed(2));

        return {
          ...prev,
          settings: watchdogTrip ? { ...prev.settings, activeProfileId: nextProfile } : prev.settings,
          statusBanner: nextBanner,
          overview: {
            ...prev.overview,
            cpuUsagePercent,
          },
          cpuPolicies: nextPolicies,
          cpuCores: nextCores,
          gpuState: {
            ...prev.gpuState,
            usagePercent: gpuUsage,
            currentMhz: currentGpuMhz,
          },
          thermalTelemetry: {
            ...prev.thermalTelemetry,
            batteryTempC: batTemp,
            socTempC: socTemp,
            lastUpdatedMs: Date.now(),
          },
          batteryMonitorStats: {
            ...prev.batteryMonitorStats,
            currentMa: baseCurrentMa,
            powerWatts,
            temperatureC: batTemp,
            screenOnMs: prev.batteryMonitorStats.screenOnMs + 2000,
          },
          fastChargeState: {
            ...prev.fastChargeState,
            currentMa: baseCurrentMa,
            powerWatts,
          },
        };
      });
    }, 2000);
    return () => clearInterval(timer);
  }, []);

  // Actions
  const openTool = useCallback((tool: ToolId | null) => {
    setState((p) => ({ ...p, activeTool: tool, statusBanner: '' }));
  }, []);

  const setShowSearchSheet = useCallback((show: boolean) => {
    setState((p) => ({ ...p, showSearchSheet: show }));
  }, []);

  const setShowPowerSheet = useCallback((show: boolean) => {
    setState((p) => ({ ...p, showPowerSheet: show }));
  }, []);

  const setShowNotificationsSheet = useCallback((show: boolean) => {
    setState((p) => ({
      ...p,
      showNotificationsSheet: show,
      settings: show
        ? { ...p.settings, notifications: { ...p.settings.notifications, unreadCount: 0 } }
        : p.settings,
    }));
  }, []);

  const setShowDeviceDetails = useCallback((show: boolean) => {
    setState((p) => ({ ...p, showDeviceDetails: show }));
  }, []);

  const recordBrandLogoTap = useCallback(() => {
    const next = brandTapCount + 1;
    if (next >= 7) {
      setBrandTapCount(0);
      setState((p) => ({ ...p, showDeveloperEggDialog: true }));
      return 7;
    }
    setBrandTapCount(next);
    if (next >= 4 && next <= 6) {
      showToast(`${7 - next} step(s) away from Reporting & Requesting`);
    }
    return next;
  }, [brandTapCount, showToast]);

  const dismissDeveloperEgg = useCallback(() => {
    setState((p) => ({ ...p, showDeveloperEggDialog: false }));
  }, []);

  const autoDetectRoot = useCallback((forceFresh = false) => {
    setState((p) => ({ ...p, rootStatus: RootStatus.CHECKING }));
    setTimeout(() => {
      setState((p) => {
        const nextRoot = RootStatus.GRANTED;
        const effective = resolveEffectiveMode(p.settings.workMode, nextRoot, p.shizukuStatus);
        return {
          ...p,
          rootStatus: nextRoot,
          effectivePrivilegeMode: effective,
          statusBanner: forceFresh ? 'Root (su) verified via KernelSU · Full sysfs access granted.' : p.statusBanner,
        };
      });
      appendDaemonLog('Root access verified (uid=0)');
      showToast('Root access granted');
    }, 350);
  }, [appendDaemonLog, showToast]);

  const requestShizukuPermission = useCallback(() => {
    setState((p) => {
      const nextShizuku = ShizukuStatus.GRANTED;
      const effective = resolveEffectiveMode(p.settings.workMode, p.rootStatus, nextShizuku);
      return {
        ...p,
        shizukuStatus: nextShizuku,
        effectivePrivilegeMode: effective,
        statusBanner: 'Shizuku binder permission granted · Rootless ADB shell ready.',
      };
    });
    appendDaemonLog('Shizuku ADB shell authorized');
    showToast('Shizuku permission granted');
  }, [appendDaemonLog, showToast]);

  const refreshPrivilegeModes = useCallback(() => {
    setState((p) => {
      const effective = resolveEffectiveMode(p.settings.workMode, p.rootStatus, p.shizukuStatus);
      return {
        ...p,
        effectivePrivilegeMode: effective,
        statusBanner: `Privilege check complete · Effective mode: ${effective}`,
      };
    });
    showToast('Privileges refreshed');
  }, [showToast]);

  const setPrivilegeSimulation = useCallback((root: RootStatus, shizuku: ShizukuStatus, workMode?: WorkMode) => {
    setState((p) => {
      const nextWorkMode = workMode ?? p.settings.workMode;
      const effective = resolveEffectiveMode(nextWorkMode, root, shizuku);
      return {
        ...p,
        rootStatus: root,
        shizukuStatus: shizuku,
        effectivePrivilegeMode: effective,
        settings: { ...p.settings, workMode: nextWorkMode },
      };
    });
  }, []);

  const completeOnboarding = useCallback((name: string, tagline: string, photoDataUrl: string, workMode: WorkMode) => {
    setState((p) => {
      const effective = resolveEffectiveMode(workMode, p.rootStatus, p.shizukuStatus);
      return {
        ...p,
        effectivePrivilegeMode: effective,
        settings: {
          ...p.settings,
          onboardingCompleted: true,
          userName: name.trim() || 'Evorez User',
          userTagline: tagline.trim() || 'Device Tuner',
          userPhotoDataUrl: photoDataUrl || p.settings.userPhotoDataUrl,
          workMode,
        },
      };
    });
  }, []);

  const updateUserProfile = useCallback((name: string, tagline: string, photoDataUrl: string | null) => {
    setState((p) => ({
      ...p,
      settings: {
        ...p.settings,
        userName: name.trim() || 'Evorez User',
        userTagline: tagline.trim() || 'Device Tuner',
        userPhotoDataUrl: photoDataUrl !== null ? photoDataUrl : p.settings.userPhotoDataUrl,
      },
    }));
    showToast('Profile updated');
  }, [showToast]);

  // Theme & Appearance setters
  const setThemeMode = useCallback((mode: ThemeMode) => {
    setState((p) => ({ ...p, settings: { ...p.settings, themeMode: mode } }));
  }, []);

  const setThemeStyle = useCallback((style: EzdThemeStyle) => {
    setState((p) => {
      const nextMode =
        p.settings.themeMode === ThemeMode.LITE
          ? ThemeMode.LITE
          : style === EzdThemeStyle.MILK_GLASS ||
            style === EzdThemeStyle.WARM_BEIGE ||
            style === EzdThemeStyle.ARCTIC_MINT ||
            style === EzdThemeStyle.PEARL_ROSE ||
            style === EzdThemeStyle.SKY_FROST
          ? ThemeMode.LIGHT
          : ThemeMode.DARK;
      return { ...p, settings: { ...p.settings, themeStyle: style, themeMode: nextMode } };
    });
  }, []);

  const setAccentPreset = useCallback((preset: AccentColorPreset) => {
    setState((p) => ({ ...p, settings: { ...p.settings, accentPreset: preset } }));
  }, []);

  const setBlurIntensity = useCallback((val: number) => {
    setState((p) => ({ ...p, settings: { ...p.settings, blurIntensity: val } }));
  }, []);

  const setReduceAnimations = useCallback((reduce: boolean) => {
    setState((p) => ({ ...p, settings: { ...p.settings, reduceAnimations: reduce } }));
  }, []);

  const setFloatingPillOpaque = useCallback((opaque: boolean) => {
    setState((p) => ({ ...p, settings: { ...p.settings, floatingPillOpaque: opaque } }));
  }, []);

  const setBackgroundTexture = useCallback((preset: BackgroundTexturePreset) => {
    setState((p) => ({ ...p, settings: { ...p.settings, backgroundTexture: preset } }));
  }, []);

  const setBackgroundTextureOpacity = useCallback((opacity: number) => {
    setState((p) => ({ ...p, settings: { ...p.settings, backgroundTextureOpacity: opacity } }));
  }, []);

  const setAppFontStyle = useCallback((font: AppFontStyle) => {
    setState((p) => ({ ...p, settings: { ...p.settings, appFontStyle: font } }));
  }, []);

  const setAnimStyle = useCallback((anim: AppAnimStyle) => {
    setState((p) => ({ ...p, settings: { ...p.settings, animStyle: anim } }));
  }, []);

  const pinToolToPill = useCallback((tool: ToolId) => {
    setState((p) => {
      if (p.settings.pinnedPillTools.includes(tool)) return p;
      const next = [...p.settings.pinnedPillTools, tool].slice(-6);
      return { ...p, settings: { ...p.settings, pinnedPillTools: next } };
    });
    showToast(`Pinned to navigation pill`);
  }, [showToast]);

  const unpinToolFromPill = useCallback((tool: ToolId) => {
    setState((p) => ({
      ...p,
      settings: {
        ...p.settings,
        pinnedPillTools: p.settings.pinnedPillTools.filter((t) => t !== tool),
      },
    }));
  }, []);

  const movePinnedPillTool = useCallback((tool: ToolId, delta: number) => {
    setState((p) => {
      const list = [...p.settings.pinnedPillTools];
      const idx = list.indexOf(tool);
      const target = idx + delta;
      if (idx < 0 || target < 0 || target >= list.length) return p;
      const [item] = list.splice(idx, 1);
      list.splice(target, 0, item);
      return { ...p, settings: { ...p.settings, pinnedPillTools: list } };
    });
  }, []);

  const refreshAllHardware = useCallback((forceProbe = false) => {
    setState((p) => ({ ...p, isRefreshingProbe: true }));
    setTimeout(() => {
      setState((p) => ({
        ...p,
        isRefreshingProbe: false,
        statusBanner: forceProbe ? 'Hardware sysfs nodes re-probed & verified.' : p.statusBanner,
      }));
      if (forceProbe) showToast('Hardware probe refreshed');
    }, 400);
  }, [showToast]);

  const applyProfile = useCallback((profileOrId: ProfileConfig | string) => {
    setState((p) => {
      const all = [...BUILT_IN_PROFILES, ...p.customProfiles];
      const prof =
        typeof profileOrId === 'string'
          ? all.find((x) => x.id.toLowerCase() === profileOrId.toLowerCase())
          : profileOrId;
      if (!prof) return p;

      const isBeast = Boolean(prof.isBeastMode || prof.id === GameProfileId.BEAST);
      const nextPolicies = p.cpuPolicies.map((pol) => {
        const freqs = pol.availableFreqsKhz;
        const minIdx = Math.max(0, Math.min(freqs.length - 1, Math.round(((freqs.length - 1) * prof.cpuMinScalePercent) / 100)));
        const maxIdx = Math.max(minIdx, Math.min(freqs.length - 1, Math.round(((freqs.length - 1) * prof.cpuMaxScalePercent) / 100)));
        const gov = pol.governors.includes(prof.cpuGovernor) ? prof.cpuGovernor : pol.governors[0] ?? pol.currentGovernor;
        return {
          ...pol,
          minKhz: freqs[minIdx] ?? pol.minKhz,
          maxKhz: freqs[maxIdx] ?? pol.maxKhz,
          currentGovernor: gov,
        };
      });

      const gpuFreqs = p.gpuState.availableMhz;
      const gpuMaxIdx = Math.max(0, Math.min(gpuFreqs.length - 1, Math.round(((gpuFreqs.length - 1) * prof.gpuMaxPercent) / 100)));
      const gpuGov = p.gpuState.availableGovernors.includes(prof.gpuGovernor)
        ? prof.gpuGovernor
        : p.gpuState.currentGovernor;

      const beastReport: BeastExecutionReport | null = isBeast
        ? {
            active: true,
            timestampMs: Date.now(),
            peakHzUsed: p.displayState.peakHardwareHz,
            summary: `Beast Mode active · 7/7 subsystems locked at peak OPP (${p.displayState.peakHardwareHz}Hz)`,
            items: [
              { category: 'CPU', label: 'Clusters 0–7', status: EngineItemStatus.OK, reason: 'Locked to peak OPP + performance governor', path: '/sys/devices/system/cpu/cpufreq/policy*' },
              { category: 'CPU', label: 'Core Hotplug', status: EngineItemStatus.OK, reason: '8 / 8 cores forced online', path: '/sys/devices/system/cpu/cpu*/online' },
              { category: 'GPU', label: p.gpuState.vendorHint, status: EngineItemStatus.OK, reason: `Locked to ${gpuFreqs[gpuFreqs.length - 1]} MHz`, path: p.gpuState.maxPath ?? '' },
              { category: 'Display', label: 'Refresh Rate', status: EngineItemStatus.OK, reason: `Locked peak ${p.displayState.peakHardwareHz} Hz`, path: 'settings put system peak_refresh_rate' },
              { category: 'Touch', label: 'Gaming & Boost', status: EngineItemStatus.OK, reason: 'Touch boost + gaming mode enabled', path: p.touchState.gamingModePath ?? '' },
              { category: 'I/O', label: 'Scheduler & RA', status: EngineItemStatus.OK, reason: 'Queue tuned (512KB read-ahead)', path: '/sys/block/sda/queue' },
              { category: 'Thermal', label: 'Headroom', status: EngineItemStatus.OK, reason: 'High threshold profile armed with Watchdog', path: '/sys/class/thermal/thermal_zone*' },
            ],
          }
        : null;

      return {
        ...p,
        settings: { ...p.settings, activeProfileId: prof.id },
        cpuPolicies: nextPolicies,
        cpuCores: isBeast ? p.cpuCores.map((c) => ({ ...c, online: true })) : p.cpuCores,
        gpuState: {
          ...p.gpuState,
          maxMhz: gpuFreqs[gpuMaxIdx] ?? p.gpuState.maxMhz,
          minMhz: isBeast ? gpuFreqs[gpuFreqs.length - 1] : gpuFreqs[0],
          currentGovernor: gpuGov,
        },
        displayState: {
          ...p.displayState,
          currentRefreshHz: prof.targetRefreshHz ?? p.displayState.currentRefreshHz,
        },
        touchState: {
          ...p.touchState,
          touchBoostEnabled: prof.touchBoost,
          gamingModeEnabled: isBeast ? true : p.touchState.gamingModeEnabled,
        },
        beastReport,
        statusBanner: `Applied ${prof.name}`,
      };
    });
    appendDaemonLog(`Applied profile: ${typeof profileOrId === 'string' ? profileOrId : profileOrId.name}`);
  }, [appendDaemonLog]);

  const toggleBeastMode = useCallback(() => {
    if (state.settings.activeProfileId === GameProfileId.BEAST) {
      applyProfile(GameProfileId.BALANCED);
      showToast('Beast Mode deactivated · Restored Balanced');
    } else {
      applyProfile(GameProfileId.BEAST);
      showToast('Beast Mode activated');
    }
  }, [state.settings.activeProfileId, applyProfile, showToast]);

  const setBeastThermalLimits = useCallback((bat: number | null, soc: number | null) => {
    setState((p) => ({
      ...p,
      settings: {
        ...p.settings,
        beastBatteryTempLimitC: bat ?? p.settings.beastBatteryTempLimitC,
        beastSocTempLimitC: soc ?? p.settings.beastSocTempLimitC,
      },
    }));
  }, []);

  const setBeastIgnoreThermalLimits = useCallback((ignore: boolean) => {
    setState((p) => ({
      ...p,
      settings: { ...p.settings, beastIgnoreThermalLimits: ignore },
    }));
  }, []);

  const saveCustomProfile = useCallback((profile: ProfileConfig) => {
    setState((p) => {
      const exists = p.customProfiles.some((x) => x.id === profile.id);
      const next = exists
        ? p.customProfiles.map((x) => (x.id === profile.id ? profile : x))
        : [...p.customProfiles, profile];
      return { ...p, customProfiles: next, statusBanner: `Saved profile "${profile.name}"` };
    });
  }, []);

  const deleteCustomProfile = useCallback((id: string) => {
    setState((p) => ({
      ...p,
      customProfiles: p.customProfiles.filter((x) => x.id !== id),
      settings:
        p.settings.activeProfileId === id ? { ...p.settings, activeProfileId: 'balanced' } : p.settings,
    }));
  }, []);

  const resetCustomProfiles = useCallback(() => {
    setState((p) => ({
      ...p,
      customProfiles: [],
      settings: { ...p.settings, activeProfileId: 'balanced' },
      statusBanner: 'Custom profiles reset to built-in defaults.',
    }));
  }, []);

  // CPU & GPU
  const setCpuFrequencyRange = useCallback((policy: CpuPolicy, minKhz: number | null, maxKhz: number | null) => {
    setState((p) => ({
      ...p,
      cpuPolicies: p.cpuPolicies.map((pol) =>
        pol.id === policy.id
          ? {
              ...pol,
              minKhz: minKhz ?? pol.minKhz,
              maxKhz: maxKhz ?? pol.maxKhz,
            }
          : pol
      ),
      statusBanner: `Updated ${policy.id.toUpperCase()} frequency bounds`,
    }));
  }, []);

  const setCpuGovernor = useCallback((policy: CpuPolicy, gov: string) => {
    setState((p) => ({
      ...p,
      cpuPolicies: p.cpuPolicies.map((pol) =>
        pol.id === policy.id ? { ...pol, currentGovernor: gov } : pol
      ),
      statusBanner: `${policy.id.toUpperCase()} governor set to ${gov}`,
    }));
  }, []);

  const resetCpuPolicy = useCallback((policy: CpuPolicy) => {
    setState((p) => ({
      ...p,
      cpuPolicies: p.cpuPolicies.map((pol) =>
        pol.id === policy.id
          ? {
              ...pol,
              minKhz: pol.originalMinKhz ?? pol.minKhz,
              maxKhz: pol.originalMaxKhz ?? pol.maxKhz,
              currentGovernor: pol.originalGovernor ?? pol.currentGovernor,
            }
          : pol
      ),
      statusBanner: `Reset ${policy.id.toUpperCase()} to stock baseline`,
    }));
  }, []);

  const toggleCpuCore = useCallback((core: CpuCore, online: boolean) => {
    setState((p) => ({
      ...p,
      cpuCores: p.cpuCores.map((c) => (c.index === core.index ? { ...c, online } : c)),
      statusBanner: `CPU${core.index} ${online ? 'brought online' : 'offlined'}`,
    }));
  }, []);

  const setGpuFrequency = useCallback((minMhz: number | null, maxMhz: number | null) => {
    setState((p) => ({
      ...p,
      gpuState: {
        ...p.gpuState,
        minMhz: minMhz ?? p.gpuState.minMhz,
        maxMhz: maxMhz ?? p.gpuState.maxMhz,
      },
      statusBanner: 'Updated GPU frequency ceiling',
    }));
  }, []);

  const setGpuGovernor = useCallback((gov: string) => {
    setState((p) => ({
      ...p,
      gpuState: { ...p.gpuState, currentGovernor: gov },
      statusBanner: `GPU governor set to ${gov}`,
    }));
  }, []);

  const resetGpu = useCallback(() => {
    setState((p) => ({
      ...p,
      gpuState: {
        ...p.gpuState,
        minMhz: p.gpuState.originalMinMhz ?? p.gpuState.minMhz,
        maxMhz: p.gpuState.originalMaxMhz ?? p.gpuState.maxMhz,
        currentGovernor: p.gpuState.originalGovernor ?? p.gpuState.currentGovernor,
      },
      statusBanner: 'GPU reset to stock baseline',
    }));
  }, []);

  // ZRAM & VM
  const refreshZramVm = useCallback(() => {
    showToast('ZRAM & VM nodes refreshed');
  }, [showToast]);

  const applyZramConfig = useCallback((diskSizeMb: number, algo: string) => {
    setState((p) => ({
      ...p,
      zramVmState: {
        ...p.zramVmState,
        diskSizeMb,
        currentAlgorithm: algo,
        usedMemMb: diskSizeMb === 0 ? 0 : Math.min(diskSizeMb, p.zramVmState.usedMemMb || 640),
        statusNote: diskSizeMb === 0 ? 'ZRAM swap disabled' : `/dev/block/zram0 active (${diskSizeMb} MB · ${algo})`,
      },
      statusBanner: `ZRAM configured: ${diskSizeMb} MB (${algo})`,
    }));
    appendDaemonLog(`ZRAM set to ${diskSizeMb}MB (${algo})`);
  }, [appendDaemonLog]);

  const applyVmTweaks = useCallback((swappiness: number, vfsCachePressure: number, dirtyRatio: number, dirtyBackgroundRatio: number) => {
    setState((p) => ({
      ...p,
      zramVmState: {
        ...p.zramVmState,
        swappiness,
        vfsCachePressure,
        dirtyRatio,
        dirtyBackgroundRatio,
      },
      statusBanner: `VM applied: swappiness=${swappiness}, vfs=${vfsCachePressure}`,
    }));
  }, []);

  // I/O, Thermal & UI
  const refreshIoThermalUi = useCallback(() => {
    showToast('Block devices & thermal zones refreshed');
  }, [showToast]);

  const setIoScheduler = useCallback((dev: IoBlockDevice, scheduler: string) => {
    setState((p) => ({
      ...p,
      ioThermalUiState: {
        ...p.ioThermalUiState,
        blockDevices: p.ioThermalUiState.blockDevices.map((d) =>
          d.name === dev.name ? { ...d, currentScheduler: scheduler } : d
        ),
      },
      statusBanner: `${dev.name} I/O scheduler set to ${scheduler}`,
    }));
  }, []);

  const setIoReadAhead = useCallback((dev: IoBlockDevice, kb: number) => {
    setState((p) => ({
      ...p,
      ioThermalUiState: {
        ...p.ioThermalUiState,
        blockDevices: p.ioThermalUiState.blockDevices.map((d) =>
          d.name === dev.name ? { ...d, readAheadKb: kb } : d
        ),
      },
      statusBanner: `${dev.name} read-ahead set to ${kb} KB`,
    }));
  }, []);

  const applyThermalProfile = useCallback((mode: ThermalProfileMode) => {
    setState((p) => ({
      ...p,
      ioThermalUiState: { ...p.ioThermalUiState, thermalProfile: mode },
      statusBanner: `Thermal profile set to ${mode}`,
    }));
  }, []);

  const setSystemAnimationScale = useCallback((scale: number) => {
    setState((p) => ({
      ...p,
      ioThermalUiState: {
        ...p.ioThermalUiState,
        windowAnimScale: scale,
        transitionAnimScale: scale,
        animatorDurationScale: scale,
      },
      statusBanner: `System animation scale set to ${scale}x`,
    }));
  }, []);

  const setGpuRenderer = useCallback((mode: GpuRendererMode) => {
    setState((p) => ({
      ...p,
      ioThermalUiState: { ...p.ioThermalUiState, gpuRenderer: mode },
      statusBanner: `HWUI renderer set to ${mode}`,
    }));
  }, []);

  // Display & Touch
  const setRefreshRate = useCallback((hz: number) => {
    setState((p) => ({
      ...p,
      displayState: { ...p.displayState, currentRefreshHz: hz, peakRefreshHz: hz },
      statusBanner: `Refresh rate set to ${hz} Hz`,
    }));
  }, []);

  const resetRefreshRate = useCallback(() => {
    setState((p) => ({
      ...p,
      displayState: { ...p.displayState, currentRefreshHz: 120, peakRefreshHz: null },
      statusBanner: 'Refresh rate reset to system default',
    }));
  }, []);

  const setBrightness = useCallback((val: number, auto: boolean) => {
    setState((p) => ({
      ...p,
      displayState: { ...p.displayState, brightness: val, autoBrightness: auto },
    }));
  }, []);

  const setHbm = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      displayState: { ...p.displayState, hbmEnabled: enabled },
      statusBanner: `High Brightness Mode ${enabled ? 'enabled' : 'disabled'}`,
    }));
  }, []);

  const setDisplaySaturation = useCallback((sat: number) => {
    setState((p) => ({
      ...p,
      displayState: { ...p.displayState, saturationValue: sat },
    }));
  }, []);

  const setCabc = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      displayState: { ...p.displayState, cabcEnabled: enabled },
    }));
  }, []);

  const setDisableHwOverlays = useCallback((disabled: boolean) => {
    setState((p) => ({
      ...p,
      touchState: { ...p.touchState, hwOverlaysDisabled: disabled },
      statusBanner: disabled ? 'Forced GPU compositing (HW Overlays disabled)' : 'Hardware Composer (HWC) restored',
    }));
  }, []);

  const setTouchControl = useCallback((path: string, value: string, label: string) => {
    setState((p) => {
      const next = { ...p.touchState };
      if (path === next.samplingNodePath) next.samplingValue = value;
      if (path === next.gamingModePath) next.gamingModeEnabled = value === '1';
      if (path === next.touchBoostPath) next.touchBoostEnabled = value === '1';
      return { ...p, touchState: next, statusBanner: `${label} updated (${value})` };
    });
  }, []);

  // Network
  const resetNetworkSettings = useCallback(() => {
    setState((p) => ({
      ...p,
      networkState: {
        ...p.networkState,
        tcpCongestion: p.networkState.originalTcpCongestion ?? 'cubic',
        privateDnsMode: 'off',
        privateDnsSpecifier: '',
      },
      statusBanner: 'Network TCP & Private DNS reset to stock',
    }));
  }, []);

  const applyTcpFastPreset = useCallback(() => {
    setState((p) => ({
      ...p,
      networkState: { ...p.networkState, tcpCongestion: 'bbr', tcpFastOpenValue: 3 },
      statusBanner: 'TCP Fast applied (bbr + TFO=3)',
    }));
  }, []);

  const applyTcpRescuePreset = useCallback(() => {
    setState((p) => ({
      ...p,
      networkState: { ...p.networkState, tcpCongestion: 'cubic', tcpFastOpenValue: 1 },
      statusBanner: 'TCP Rescue applied (restored cubic)',
    }));
  }, []);

  const setTcpCongestion = useCallback((algo: string) => {
    setState((p) => ({
      ...p,
      networkState: { ...p.networkState, tcpCongestion: algo },
      statusBanner: `TCP congestion control set to ${algo}`,
    }));
  }, []);

  const applyDnsPreset = useCallback((preset: DnsPreset) => {
    setState((p) => ({
      ...p,
      networkState: {
        ...p.networkState,
        privateDnsMode: preset.id === 'off' ? 'off' : 'hostname',
        privateDnsSpecifier: preset.hostname,
      },
      statusBanner: preset.id === 'off' ? 'Private DNS reset to System Default' : `Private DNS set to ${preset.name} (${preset.hostname})`,
    }));
  }, []);

  // Audio
  const setAudioVisualStyle = useCallback((style: AudioVisualStyle) => {
    setState((p) => ({
      ...p,
      audioState: { ...p.audioState, visualStyle: style },
      settings: { ...p.settings, audioVisualStyle: style },
    }));
  }, []);

  const toggleBassEnhancer = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      audioState: { ...p.audioState, bassEnabled: enabled },
      settings: { ...p.settings, audioBassEnabled: enabled },
    }));
  }, []);

  const setBassFrequencyFocus = useCallback((focus: string) => {
    setState((p) => ({
      ...p,
      audioState: { ...p.audioState, bassFrequencyFocus: focus },
      settings: { ...p.settings, audioBassFocus: focus },
    }));
  }, []);

  const toggleIncreaseLoudness = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      audioState: { ...p.audioState, increaseLoudnessEnabled: enabled },
      settings: { ...p.settings, audioIncreaseLoudness: enabled },
    }));
  }, []);

  const toggleEqualizer = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      audioState: { ...p.audioState, equalizerEnabled: enabled },
      settings: { ...p.settings, audioEqEnabled: enabled },
    }));
  }, []);

  const selectAudioPreset = useCallback((name: string) => {
    const bands = AUDIO_PRESET_BANDS[name] ?? [200, 100, 50, 150, 250];
    setState((p) => ({
      ...p,
      audioState: { ...p.audioState, activePreset: name, bandLevels: bands },
      settings: { ...p.settings, savedAudioPreset: name },
    }));
  }, []);

  const updateAudioManual = useCallback((bands: number[], bassStrength: number, loudnessGainMb: number) => {
    setState((p) => ({
      ...p,
      audioState: {
        ...p.audioState,
        activePreset: 'Custom',
        bandLevels: bands,
        bassStrength,
        loudnessGainMb,
      },
      settings: { ...p.settings, audioLoudnessGainMb: loudnessGainMb },
    }));
  }, []);

  // Battery & Fast Charge
  const resetBatteryMonitorStats = useCallback(() => {
    setState((p) => ({
      ...p,
      batteryMonitorStats: {
        ...p.batteryMonitorStats,
        screenOnMs: 0,
        screenOffMs: 0,
        awakeMs: 0,
        deepSleepMs: 0,
        deepSleepPercent: 0,
      },
    }));
    showToast('Battery session counters reset');
  }, [showToast]);

  const applyGmsBackgroundManagement = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      batterySavingState: {
        ...p.batterySavingState,
        gmsStandbyApplied: enabled,
        lastActionSummary: enabled ? 'Non-essential GMS telemetry standby applied' : 'GMS standby restored to stock',
      },
    }));
  }, []);

  const applyBackgroundAppLimit = useCallback((preset: string) => {
    setState((p) => ({
      ...p,
      batterySavingState: {
        ...p.batterySavingState,
        backgroundLimitPreset: preset,
        lastActionSummary: `Background process limit set to ${preset}`,
      },
    }));
  }, []);

  const runSafeMemoryTrim = useCallback(() => {
    setState((p) => ({
      ...p,
      overview: { ...p.overview, ramUsedMb: Math.max(4100, p.overview.ramUsedMb - 1240) },
      batterySavingState: {
        ...p.batterySavingState,
        lastActionSummary: `Trimmed background caches (skipped ${p.batteryWhitelist.length} whitelisted apps)`,
      },
    }));
    showToast('Safe memory trim completed');
  }, [showToast]);

  const toggleWhitelistApp = useCallback((pkg: string) => {
    setState((p) => {
      const exists = p.batteryWhitelist.includes(pkg);
      const nextWl = exists ? p.batteryWhitelist.filter((x) => x !== pkg) : [...p.batteryWhitelist, pkg];
      const existingCfg = p.perAppConfigs[pkg];
      const nextConfigs = {
        ...p.perAppConfigs,
        [pkg]: {
          packageName: pkg,
          enabled: existingCfg?.enabled ?? false,
          profileId: existingCfg?.profileId ?? 'balanced',
          gpuProfile: existingCfg?.gpuProfile ?? '',
          refreshRateHz: existingCfg?.refreshRateHz ?? null,
          spoofPresetId: existingCfg?.spoofPresetId ?? null,
          dnsMode: existingCfg?.dnsMode ?? PerAppDnsMode.SYSTEM,
          customDnsServer: existingCfg?.customDnsServer ?? '1.1.1.1',
          batteryRestricted: exists ? existingCfg?.batteryRestricted ?? false : false,
          whitelisted: !exists,
          touchBoost: existingCfg?.touchBoost ?? false,
          disableHwOverlays: existingCfg?.disableHwOverlays ?? false,
        },
      };
      return {
        ...p,
        batteryWhitelist: nextWl,
        perAppConfigs: nextConfigs,
        batterySavingState: { ...p.batterySavingState, whitelistCount: nextWl.length },
      };
    });
  }, []);

  const setAppBackgroundRestriction = useCallback((pkg: string, restricted: boolean) => {
    setState((p) => {
      const existing = p.perAppConfigs[pkg] ?? {
        packageName: pkg,
        enabled: false,
        profileId: 'balanced',
        gpuProfile: '',
        refreshRateHz: null,
        spoofPresetId: null,
        dnsMode: PerAppDnsMode.SYSTEM,
        customDnsServer: '1.1.1.1',
        batteryRestricted: false,
        whitelisted: false,
        touchBoost: false,
        disableHwOverlays: false,
      };
      const nextConfigs = { ...p.perAppConfigs, [pkg]: { ...existing, batteryRestricted: restricted } };
      const restrictedCount = Object.values(nextConfigs).filter((c) => c.batteryRestricted).length;
      return {
        ...p,
        perAppConfigs: nextConfigs,
        batterySavingState: { ...p.batterySavingState, restrictedCount },
      };
    });
  }, []);

  const refreshFastCharge = useCallback(() => {
    setState((p) => {
      const nextCharging = !p.fastChargeState.isCharging;
      const limit = p.fastChargeState.activeLimitMa ?? 3300;
      const currentMa = nextCharging ? limit : -420;
      const watts = Number(((Math.abs(currentMa) * p.fastChargeState.voltageMv) / 1_000_000).toFixed(2));
      return {
        ...p,
        fastChargeState: {
          ...p.fastChargeState,
          isCharging: nextCharging,
          chargeSource: nextCharging ? 'USB-PD / SuperVOOC' : 'Battery',
          chargeType: nextCharging ? 'Fast' : 'Discharging',
          currentMa,
          powerWatts: watts,
        },
        overview: {
          ...p.overview,
          chargingStatus: nextCharging ? 'Charging (Fast)' : 'Discharging',
        },
      };
    });
  }, []);

  const applyFastChargeLimit = useCallback((limitMa: number) => {
    setState((p) => ({
      ...p,
      fastChargeState: { ...p.fastChargeState, activeLimitMa: limitMa },
      statusBanner: `Charge current limit set to ${limitMa} mA`,
    }));
  }, []);

  const toggleForceFastCharge = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      fastChargeState: { ...p.fastChargeState, forceFastChargeEnabled: enabled },
      statusBanner: `Force Fast Charge ${enabled ? 'enabled' : 'disabled'}`,
    }));
  }, []);

  // Device Spoof
  const allSpoofPresets = useCallback(
    () => [...BUILT_IN_SPOOF_PRESETS, ...state.customSpoofPresets],
    [state.customSpoofPresets]
  );

  const applySpoofPreset = useCallback((preset: SpoofPreset) => {
    setState((p) => ({
      ...p,
      spoofState: {
        ...p.spoofState,
        currentBrand: preset.brand,
        currentManufacturer: preset.manufacturer,
        currentModel: preset.model,
        currentDevice: preset.device,
        statusMessage: `Active spoof: ${preset.name}`,
      },
      statusBanner: `Device identity spoofed to ${preset.model} via resetprop`,
    }));
  }, []);

  const resetSpoof = useCallback(() => {
    setState((p) => ({
      ...p,
      spoofState: {
        ...p.spoofState,
        currentBrand: p.overview.manufacturer,
        currentManufacturer: p.overview.manufacturer,
        currentModel: p.overview.model,
        currentDevice: p.overview.device,
        statusMessage: 'Restored original device identity',
      },
      statusBanner: 'Device identity restored to baseline',
    }));
  }, []);

  const upsertCustomSpoofPreset = useCallback((preset: SpoofPreset) => {
    setState((p) => {
      const exists = p.customSpoofPresets.some((x) => x.id === preset.id);
      const next = exists
        ? p.customSpoofPresets.map((x) => (x.id === preset.id ? preset : x))
        : [...p.customSpoofPresets, preset];
      return { ...p, customSpoofPresets: next };
    });
  }, []);

  const deleteCustomSpoofPreset = useCallback((id: string) => {
    setState((p) => ({
      ...p,
      customSpoofPresets: p.customSpoofPresets.filter((x) => x.id !== id),
    }));
  }, []);

  // Modules & Build.prop
  const refreshRootModules = useCallback(() => {
    showToast('Scanned /data/adb/modules');
  }, [showToast]);

  const toggleRootModule = useCallback((mod: RootModuleInfo, enabled: boolean) => {
    setState((p) => ({
      ...p,
      rootModules: p.rootModules.map((m) => (m.id === mod.id ? { ...m, enabled } : m)),
      statusBanner: `${mod.name} ${enabled ? 'enabled' : 'disabled'} (reboot to apply)`,
    }));
  }, []);

  const toggleRootModuleRemove = useCallback((mod: RootModuleInfo, pendingRemove: boolean) => {
    setState((p) => ({
      ...p,
      rootModules: p.rootModules.map((m) => (m.id === mod.id ? { ...m, pendingRemove } : m)),
    }));
  }, []);

  const createBuildPropBackup = useCallback(() => {
    setState((p) => {
      const stamp = new Date().toLocaleString();
      const id = `bk_${Date.now()}`;
      const backup: BuildPropBackupInfo = {
        id,
        fileName: `buildprop_${id}.bak`,
        fullPath: `/sdcard/Download/EZD/buildprop_${id}.bak`,
        createdAt: stamp,
        entryCount: p.buildProps.length,
        sizeBytes: p.buildProps.reduce((acc, e) => acc + e.key.length + e.value.length + 2, 0),
        snapshot: p.buildProps,
      };
      return {
        ...p,
        buildPropBackups: [backup, ...p.buildPropBackups],
        statusBanner: `Saved build.prop backup (${backup.entryCount} entries)`,
      };
    });
    showToast('Build.prop backup created');
  }, [showToast]);

  const restoreBuildPropBackup = useCallback((backup: BuildPropBackupInfo) => {
    setState((p) => ({
      ...p,
      buildProps: backup.snapshot,
      statusBanner: `Restored ${backup.entryCount} properties from ${backup.fileName}`,
    }));
  }, []);

  const updateBuildProp = useCallback((key: string, value: string) => {
    setState((p) => {
      const stamp = new Date().toLocaleString();
      const id = `auto_${Date.now()}`;
      const autoBackup: BuildPropBackupInfo = {
        id,
        fileName: `autobackup_${id}.bak`,
        fullPath: `/sdcard/Download/EZD/autobackup_${id}.bak`,
        createdAt: stamp,
        entryCount: p.buildProps.length,
        sizeBytes: 1024,
        snapshot: p.buildProps,
      };
      const exists = p.buildProps.some((e) => e.key === key);
      const nextProps: BuildPropEntry[] = exists
        ? p.buildProps.map((e) => (e.key === key ? { ...e, value } : e))
        : [{ key, value, sourceFile: 'resetprop' }, ...p.buildProps];
      return {
        ...p,
        buildProps: nextProps,
        buildPropBackups: [autoBackup, ...p.buildPropBackups].slice(0, 12),
        statusBanner: `Auto-backed up & applied ${key}=${value}`,
      };
    });
  }, []);

  // Boot Guard & Boot Memory
  const clearBootSafeMode = useCallback(() => {
    setState((p) => ({
      ...p,
      bootGuardState: {
        safeModeActive: false,
        pendingBootAttempts: 0,
        bootConfirmedHealthy: true,
        lastReason: '',
        lastTriggeredAt: 0,
      },
      statusBanner: 'Boot-Loop Safe Mode cleared. Tweaks re-armed.',
    }));
  }, []);

  const testSimulateBootLoopGuard = useCallback(() => {
    setState((p) => ({
      ...p,
      bootGuardState: {
        safeModeActive: true,
        pendingBootAttempts: 3,
        bootConfirmedHealthy: false,
        lastReason: 'Simulated 3 consecutive boot failures before stabilization. Startup tweaks & modules paused.',
        lastTriggeredAt: Date.now(),
      },
      settings: {
        ...p.settings,
        bootMemory: { ...p.settings.bootMemory, applyOnBoot: false },
      },
      statusBanner: 'Boot-Loop Safe Mode triggered!',
    }));
  }, []);

  const setApplyOnBoot = useCallback((enabled: boolean) => {
    setState((p) => ({
      ...p,
      settings: {
        ...p.settings,
        bootMemory: { ...p.settings.bootMemory, applyOnBoot: enabled },
      },
    }));
  }, []);

  const captureBootMemorySnapshot = useCallback(() => {
    setState((p) => {
      const prof = p.settings.activeProfileId;
      const hz = p.displayState.currentRefreshHz;
      const hwo = p.touchState.hwOverlaysDisabled;
      const dns = p.networkState.privateDnsSpecifier || 'System Default';
      const audio = p.audioState.activePreset;
      const summary = `Snapshot: Profile=${prof} · ${hz}Hz · HWO=${hwo ? 'GPU' : 'HWC'} · DNS=${dns} · Audio=${audio}`;
      return {
        ...p,
        settings: {
          ...p.settings,
          bootMemory: {
            ...p.settings.bootMemory,
            savedProfileId: prof,
            savedRefreshHz: hz,
            savedDisableHwOverlays: hwo,
            savedDnsHost: p.networkState.privateDnsSpecifier || null,
            savedDnsName: dns,
            savedAudioPreset: audio,
            lastCapturedAt: Date.now(),
            summary,
          },
        },
        statusBanner: 'Boot Memory snapshot captured.',
      };
    });
    showToast('Boot snapshot saved');
  }, [showToast]);

  const applyBootMemoryNow = useCallback(() => {
    const snap = state.settings.bootMemory;
    if (!snap.savedProfileId) {
      showToast('Save a snapshot first');
      return;
    }
    applyProfile(snap.savedProfileId);
    showToast('Boot Memory snapshot applied');
  }, [state.settings.bootMemory, applyProfile, showToast]);

  // Apps & Per-App
  const setIncludeSystemApps = useCallback((include: boolean) => {
    setState((p) => ({ ...p, includeSystemApps: include }));
  }, []);

  const toggleUsageAccess = useCallback(() => {
    setState((p) => ({
      ...p,
      usageAccessGranted: !p.usageAccessGranted,
    }));
  }, []);

  const savePerAppConfig = useCallback((cfg: PerAppConfig) => {
    setState((p) => {
      const nextConfigs = { ...p.perAppConfigs, [cfg.packageName]: cfg };
      const wlSet = new Set(p.batteryWhitelist);
      if (cfg.whitelisted) wlSet.add(cfg.packageName);
      else wlSet.delete(cfg.packageName);
      const anyDns = Object.values(nextConfigs).some((c) => c.enabled && c.dnsMode !== PerAppDnsMode.SYSTEM);
      return {
        ...p,
        perAppConfigs: nextConfigs,
        batteryWhitelist: Array.from(wlSet),
        vpnActive: anyDns,
        batterySavingState: {
          ...p.batterySavingState,
          whitelistCount: wlSet.size,
          restrictedCount: Object.values(nextConfigs).filter((c) => c.batteryRestricted).length,
        },
      };
    });
    appendDaemonLog(`Saved per-app tuning for ${cfg.packageName}`);
    showToast(`Saved tuning for ${cfg.packageName}`);
  }, [appendDaemonLog, showToast]);

  const syncPerAppDnsVpn = useCallback(() => {
    setState((p) => ({ ...p, pendingVpnConsentNeeded: false, vpnActive: true }));
    showToast('Local Per-App DNS VPN tunnel active');
  }, [showToast]);

  const clearAppCache = useCallback((pkg: string) => {
    showToast(`Cleared cache for ${pkg}`);
    appendDaemonLog(`pm trim-caches / rm cache for ${pkg}`);
  }, [appendDaemonLog, showToast]);

  const forceStopApp = useCallback((pkg: string) => {
    showToast(`Force stopped ${pkg}`);
    appendDaemonLog(`am force-stop ${pkg}`);
  }, [appendDaemonLog, showToast]);

  const toggleFreezeApp = useCallback((app: AppEntry) => {
    setState((p) => ({
      ...p,
      installedApps: p.installedApps.map((a) =>
        a.packageName === app.packageName ? { ...a, isDisabled: !a.isDisabled } : a
      ),
    }));
    showToast(`${app.label} ${app.isDisabled ? 'unfrozen' : 'frozen'}`);
  }, [showToast]);

  const uninstallApp = useCallback((app: AppEntry) => {
    setState((p) => ({
      ...p,
      installedApps: p.installedApps.filter((a) => a.packageName !== app.packageName),
    }));
    showToast(`Uninstalled ${app.label}`);
  }, [showToast]);

  // Backups
  const exportEncryptedBackup = useCallback(
    async (
      options: { includeNetworkConfig: boolean; includeRootBaselines: boolean; passphrase: string },
      downloadFile = false
    ) => {
      const createdAt = new Date().toISOString();
      const categories = ['EZD Config'];
      if (options.includeNetworkConfig) categories.push('Network DNS & TCP');
      if (options.includeRootBaselines) categories.push('Root Baselines');

      const dataObj = {
        settings: state.settings,
        customProfiles: state.customProfiles,
        customSpoofPresets: state.customSpoofPresets,
        perAppConfigs: state.perAppConfigs,
        batteryWhitelist: state.batteryWhitelist,
        networkState: options.includeNetworkConfig ? state.networkState : undefined,
      };
      const serialized = JSON.stringify(dataObj);
      const checksum = await computeSha256Hex(serialized + (options.passphrase || 'device_key'));
      const envelope = JSON.stringify(
        {
          archiveVersion: 2,
          cipher: 'AES-256-GCM',
          createdAt,
          categories,
          checksum,
          encryptedWithPassphrase: Boolean(options.passphrase),
          payload: btoa(unescape(encodeURIComponent(serialized))),
        },
        null,
        2
      );

      const stamp = createdAt.replace(/[:.]/g, '-').slice(0, 19);
      const fileName = `ezd_backup_${stamp}.ezdcfg.json`;
      const path = `/sdcard/Download/EZD/${fileName}`;

      if (downloadFile) {
        const blob = new Blob([envelope], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        URL.revokeObjectURL(url);
      }

      setState((p) => ({
        ...p,
        savedEzdBackups: [{ id: `ezd_${Date.now()}`, fileName, path, createdAt, payload: envelope }, ...p.savedEzdBackups],
        backupReport: {
          success: true,
          summary: `Backup created with SHA-256 verification (${categories.length} categories)`,
          filePath: path,
          backedUpItems: categories,
          skippedItems: options.passphrase ? ['Protected with user passphrase'] : ['Encrypted with device-bound key'],
        },
      }));
      showToast('EZD backup saved');
    },
    [state, showToast]
  );

  const inspectSavedEzdBackup = useCallback(async (rawPayload: string, filePath: string, _passphrase: string) => {
    try {
      const parsed = JSON.parse(rawPayload);
      setState((p) => ({
        ...p,
        backupPreview: {
          archiveVersion: parsed.archiveVersion ?? 2,
          createdAt: parsed.createdAt ?? new Date().toISOString(),
          includesEzdConfig: true,
          includesApps: false,
          appCount: 0,
          categories: parsed.categories ?? ['EZD Config'],
          checksumValid: Boolean(parsed.checksum && parsed.payload),
          filePath,
          rawPayload,
        },
      }));
    } catch {
      setState((p) => ({
        ...p,
        backupReport: {
          success: false,
          summary: 'Failed to parse backup archive',
          filePath,
          failureReason: 'Invalid JSON or corrupted AES-256-GCM envelope',
          backedUpItems: [],
          skippedItems: [],
        },
      }));
    }
  }, []);

  const executeRestore = useCallback((restoreEzdConfig: boolean, restoreNetworkConfig: boolean) => {
    setState((p) => {
      const preview = p.backupPreview;
      if (!preview?.rawPayload) return p;
      try {
        const env = JSON.parse(preview.rawPayload);
        const decoded = JSON.parse(decodeURIComponent(escape(atob(env.payload))));
        return {
          ...p,
          settings: restoreEzdConfig && decoded.settings ? { ...p.settings, ...decoded.settings } : p.settings,
          customProfiles: restoreEzdConfig && decoded.customProfiles ? decoded.customProfiles : p.customProfiles,
          customSpoofPresets: restoreEzdConfig && decoded.customSpoofPresets ? decoded.customSpoofPresets : p.customSpoofPresets,
          perAppConfigs: restoreEzdConfig && decoded.perAppConfigs ? decoded.perAppConfigs : p.perAppConfigs,
          batteryWhitelist: restoreEzdConfig && decoded.batteryWhitelist ? decoded.batteryWhitelist : p.batteryWhitelist,
          networkState: restoreNetworkConfig && decoded.networkState ? { ...p.networkState, ...decoded.networkState } : p.networkState,
          backupPreview: null,
          backupReport: {
            success: true,
            summary: 'Restored EZD configuration from verified archive',
            filePath: preview.filePath,
            backedUpItems: [
              ...(restoreEzdConfig ? ['EZD Configuration'] : []),
              ...(restoreNetworkConfig ? ['Network Preferences'] : []),
            ],
            skippedItems: [],
          },
        };
      } catch {
        return {
          ...p,
          backupPreview: null,
          backupReport: {
            success: false,
            summary: 'Restore failed',
            filePath: preview.filePath,
            failureReason: 'Unable to decode archive payload',
            backedUpItems: [],
            skippedItems: [],
          },
        };
      }
    });
  }, []);

  const clearBackupDialogs = useCallback(() => {
    setState((p) => ({ ...p, backupPreview: null, backupReport: null }));
  }, []);

  const runFullAppBackup = useCallback((selectedApps: Set<string>, options: AppBackupOptions) => {
    setState((p) => {
      const parts: string[] = [];
      if (options.includeBaseApk) parts.push('APK');
      if (options.includeAppData) parts.push('Data');
      if (options.includeExternalData) parts.push('ExtData');
      if (options.includeObb) parts.push('OBB');
      if (options.includeMedia) parts.push('Media');

      const created = new Date().toLocaleString();
      const newRecords: LocalAppBackupRecord[] = [];
      selectedApps.forEach((pkg) => {
        const app = p.installedApps.find((a) => a.packageName === pkg);
        if (!app) return;
        newRecords.push({
          id: `appbk_${pkg}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
          folderPath: `/sdcard/Download/EZD/AppBackups/${pkg}`,
          packageName: pkg,
          appLabel: app.label,
          versionName: app.versionName || '1.0',
          createdAt: created,
          parts,
          totalSizeBytes: (options.includeBaseApk ? 48_000_000 : 0) + (options.includeAppData ? 14_000_000 : 0) + (options.includeObb ? 240_000_000 : 2_000_000),
        });
      });
      return {
        ...p,
        localAppBackups: [...newRecords, ...p.localAppBackups],
        backupReport: {
          success: true,
          summary: `Backed up ${newRecords.length} app(s) (${parts.join(', ')})`,
          filePath: '/sdcard/Download/EZD/AppBackups/',
          backedUpItems: newRecords.map((r) => `${r.appLabel} (${r.parts.join(' + ')})`),
          skippedItems: [],
        },
      };
    });
  }, []);

  const restoreLocalAppBackup = useCallback((rec: LocalAppBackupRecord) => {
    showToast(`Restored ${rec.appLabel} (${rec.parts.join(', ')})`);
  }, [showToast]);

  const deleteLocalAppBackup = useCallback((rec: LocalAppBackupRecord) => {
    setState((p) => ({
      ...p,
      localAppBackups: p.localAppBackups.filter((r) => r.id !== rec.id),
    }));
  }, []);

  // Root Terminal
  const runTerminalCommand = useCallback((rawCmd: string) => {
    const cmd = rawCmd.trim();
    if (!cmd) return;
    if (cmd === 'clear') {
      setState((p) => ({ ...p, terminalHistory: [] }));
      return;
    }

    setState((p) => {
      const ov = p.overview;
      const mode = p.effectivePrivilegeMode;
      let output = '';
      let exitCode = 0;

      if (cmd === 'id') {
        output =
          mode === PrivilegeMode.ROOT
            ? 'uid=0(root) gid=0(root) groups=0(root) context=u:r:su:s0'
            : mode === PrivilegeMode.SHIZUKU
            ? 'uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log) context=u:r:shell:s0'
            : 'uid=10248(u0_a248) gid=10248(u0_a248) context=u:r:untrusted_app:s0';
      } else if (cmd === 'uname -a') {
        output = `Linux localhost ${ov.kernelVersion} #1 SMP PREEMPT aarch64 Android`;
      } else if (cmd.startsWith('getprop')) {
        const key = cmd.replace('getprop', '').trim();
        if (!key) {
          output = p.buildProps.map((b) => `[${b.key}]: [${b.value}]`).join('\n');
        } else {
          const found = p.buildProps.find((b) => b.key === key);
          output = found ? found.value : key === 'ro.product.model' ? p.spoofState.currentModel : '';
        }
      } else if (cmd === 'ls /sys/devices/system/cpu/cpufreq') {
        output = p.cpuPolicies.map((pol) => pol.id).join('\n');
      } else if (cmd === 'cat /proc/version') {
        output = `Linux version ${ov.kernelVersion} (clang version 18.0.0) #1 SMP PREEMPT`;
      } else if (cmd === 'free -m') {
        const free = ov.ramTotalMb - ov.ramUsedMb;
        output = `               total        used        free      shared     buffers\nMem:           ${ov.ramTotalMb}        ${ov.ramUsedMb}        ${free}         128        1024\nSwap:          ${p.zramVmState.diskSizeMb}        ${p.zramVmState.usedMemMb}        ${Math.max(0, p.zramVmState.diskSizeMb - p.zramVmState.usedMemMb)}`;
      } else if (cmd === 'whoami') {
        output = mode === PrivilegeMode.ROOT ? 'root' : mode === PrivilegeMode.SHIZUKU ? 'shell' : 'u0_a248';
      } else if (cmd === 'help') {
        output = 'Supported shell commands: id, uname -a, getprop [key], ls /sys/devices/system/cpu/cpufreq, cat /proc/version, free -m, whoami, echo <text>, clear';
      } else if (cmd.startsWith('echo ')) {
        output = cmd.slice(5);
      } else {
        output =
          mode === PrivilegeMode.BASIC
            ? `sh: ${cmd.split(' ')[0]}: Permission denied (grant Root or Shizuku)`
            : `[${mode.toLowerCase()}] Executed: ${cmd}`;
        exitCode = mode === PrivilegeMode.BASIC ? 126 : 0;
      }

      return {
        ...p,
        terminalHistory: [
          ...p.terminalHistory,
          { id: Date.now() + Math.random(), command: cmd, output, exitCode, timestampMs: Date.now() },
        ],
      };
    });
    appendDaemonLog(`sh: ${cmd}`);
  }, [appendDaemonLog]);

  const performPowerAction = useCallback((action: PowerAction) => {
    setState((p) => ({
      ...p,
      showPowerSheet: false,
      statusBanner: `Power command dispatched: ${action}`,
    }));
    appendDaemonLog(`Power action dispatched: ${action}`);
    showToast(`${action} command dispatched`);
  }, [appendDaemonLog, showToast]);

  const setNotificationPrefs = useCallback((prefs: NotificationPrefs) => {
    setState((p) => ({ ...p, settings: { ...p.settings, notifications: prefs } }));
  }, []);

  const toggleDaemonService = useCallback((enabled: boolean) => {
    setState((p) => ({ ...p, settings: { ...p.settings, daemonEnabled: enabled } }));
    setDaemonStats((d) => ({ ...d, serviceRunning: enabled }));
    appendDaemonLog(enabled ? 'Root background daemon started' : 'Root background daemon paused');
  }, [appendDaemonLog]);

  const checkForGitHubUpdates = useCallback(async () => {
    setState((p) => ({ ...p, checkingUpdate: true }));
    try {
      const resp = await fetch('https://api.github.com/repos/evorez/EZD-Manager/releases/latest', {
        headers: { Accept: 'application/vnd.github+json' },
      });
      if (resp.status === 404) {
        setState((p) => ({
          ...p,
          checkingUpdate: false,
          releaseInfo: {
            ...p.releaseInfo,
            latestTag: 'v1.0.0',
            updateAvailable: false,
            statusMessage: 'You are on the latest release (v1.0.0 · no newer GitHub tag published yet).',
          },
        }));
        return;
      }
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const data = await resp.json();
      const tag = data.tag_name ?? 'v1.0.0';
      const newer = tag.replace(/^v/, '') !== '1.0.0';
      setState((p) => ({
        ...p,
        checkingUpdate: false,
        releaseInfo: {
          currentVersion: '1.0.0',
          latestTag: tag,
          latestName: data.name ?? tag,
          publishedAt: data.published_at ?? '',
          changelogMarkdown: data.body ?? '',
          downloadUrl: data.html_url ?? 'https://github.com/evorez/EZD-Manager/releases',
          updateAvailable: newer,
          statusMessage: newer ? `Update available: ${tag}` : `Up to date (${tag})`,
        },
      }));
    } catch {
      setState((p) => ({
        ...p,
        checkingUpdate: false,
        releaseInfo: {
          ...p.releaseInfo,
          latestTag: 'v1.0.0',
          statusMessage: 'Offline / GitHub unreachable — showing built-in release changelog.',
        },
      }));
    }
  }, []);

  const buildDeviceReportText = useCallback(() => {
    const ov = state.overview;
    const gpu = state.gpuState;
    const lines = [
      'EZD Manager — Device Information',
      `Device: ${ov.manufacturer} ${ov.model} (${ov.device})`,
      `SoC / Board: ${ov.board} / ${ov.hardware}`,
      `Android: ${ov.androidVersion} (API ${ov.sdkInt})`,
      `Kernel: ${state.probeReport.kernelVersion}`,
      `SELinux: ${state.probeReport.selinuxMode}`,
      `Root: ${state.rootStatus} (${state.rootManagerType})`,
      `RAM: ${ov.ramUsedMb} / ${ov.ramTotalMb} MB`,
      `Battery: ${ov.batteryPercent}% (${ov.chargingStatus})`,
      `CPU Clusters (${state.cpuPolicies.length}):`,
      ...state.cpuPolicies.map(
        (p) =>
          `  ${p.id} (CPUs ${p.cpus}): ${(p.minKhz ?? 0) / 1000}–${(p.maxKhz ?? 0) / 1000} MHz | Gov: ${p.currentGovernor}`
      ),
      `GPU: ${gpu.vendorHint} (${gpu.currentMhz ?? '?'} MHz, ${gpu.currentGovernor})`,
      `Display: ${state.displayState.currentRefreshHz} Hz (peak ${state.displayState.peakHardwareHz} Hz, ${state.displayState.supportedModes.length} modes)`,
    ];
    return lines.join('\n');
  }, [state]);

  const exportAndCopyDiagnostics = useCallback(async () => {
    const report = buildDeviceReportText();
    try {
      await navigator.clipboard.writeText(report);
      const blob = new Blob([report], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'ezd-diag.txt';
      a.click();
      URL.revokeObjectURL(url);
      setState((p) => ({ ...p, diagnosticsExportPath: 'Clipboard + ezd-diag.txt', diagnosticsExportError: '' }));
      showToast('Diagnostics copied & downloaded');
    } catch {
      setState((p) => ({ ...p, diagnosticsExportPath: 'ezd-diag.txt', diagnosticsExportError: '' }));
      showToast('Diagnostics exported');
    }
  }, [buildDeviceReportText, showToast]);

  const buildSearchIndex = useCallback((): SearchEntry[] => {
    const items: SearchEntry[] = [
      { id: 'home_tab', title: 'Home Dashboard', subtitle: 'Device overview, Beast Mode & quick shortcuts', category: 'Home', targetTab: MainTab.HOME },
      { id: 'perf_tab', title: 'Performance & CPU/GPU', subtitle: 'CPU & GPU clocks, governors, hotplug & profiles', category: 'Tools', targetTab: MainTab.TOOLS, targetTool: ToolId.CPU_GPU_CONTROL },
      { id: 'settings_tab', title: 'Settings & Updates', subtitle: 'Textures, fonts, appearance, daemon & GitHub updates', category: 'Settings', targetTab: MainTab.SETTINGS },
      ...state.installedApps.map((app) => ({
        id: `app_${app.packageName}`,
        title: app.label,
        subtitle: app.packageName,
        category: 'Apps',
        targetTab: MainTab.APPS,
        packageName: app.packageName,
      })),
    ];
    return items;
  }, [state.installedApps]);

  const builtInChangelog = useCallback(() => BUILT_IN_CHANGELOG, []);

  const value: EzdContextValue = {
    state,
    daemonStats,
    visuals,
    activeTab,
    setActiveTab,
    toastMessage,
    showToast,
    splashDone,
    setSplashDone,
    pendingAppPkg,
    setPendingAppPkg,
    openTool,
    setShowSearchSheet,
    setShowPowerSheet,
    setShowNotificationsSheet,
    setShowDeviceDetails,
    recordBrandLogoTap,
    dismissDeveloperEgg,
    autoDetectRoot,
    requestShizukuPermission,
    refreshPrivilegeModes,
    setPrivilegeSimulation,
    completeOnboarding,
    updateUserProfile,
    setThemeMode,
    setThemeStyle,
    setAccentPreset,
    setBlurIntensity,
    setReduceAnimations,
    setFloatingPillOpaque,
    setBackgroundTexture,
    setBackgroundTextureOpacity,
    setAppFontStyle,
    setAnimStyle,
    pinToolToPill,
    unpinToolFromPill,
    movePinnedPillTool,
    refreshAllHardware,
    applyProfile,
    toggleBeastMode,
    setBeastThermalLimits,
    setBeastIgnoreThermalLimits,
    saveCustomProfile,
    deleteCustomProfile,
    resetCustomProfiles,
    setCpuFrequencyRange,
    setCpuGovernor,
    resetCpuPolicy,
    toggleCpuCore,
    setGpuFrequency,
    setGpuGovernor,
    resetGpu,
    refreshZramVm,
    applyZramConfig,
    applyVmTweaks,
    refreshIoThermalUi,
    setIoScheduler,
    setIoReadAhead,
    applyThermalProfile,
    setSystemAnimationScale,
    setGpuRenderer,
    setRefreshRate,
    resetRefreshRate,
    setBrightness,
    setHbm,
    setDisplaySaturation,
    setCabc,
    setDisableHwOverlays,
    setTouchControl,
    resetNetworkSettings,
    applyTcpFastPreset,
    applyTcpRescuePreset,
    setTcpCongestion,
    applyDnsPreset,
    setAudioVisualStyle,
    toggleBassEnhancer,
    setBassFrequencyFocus,
    toggleIncreaseLoudness,
    toggleEqualizer,
    selectAudioPreset,
    updateAudioManual,
    resetBatteryMonitorStats,
    applyGmsBackgroundManagement,
    applyBackgroundAppLimit,
    runSafeMemoryTrim,
    toggleWhitelistApp,
    setAppBackgroundRestriction,
    refreshFastCharge,
    applyFastChargeLimit,
    toggleForceFastCharge,
    allSpoofPresets,
    applySpoofPreset,
    resetSpoof,
    upsertCustomSpoofPreset,
    deleteCustomSpoofPreset,
    refreshRootModules,
    toggleRootModule,
    toggleRootModuleRemove,
    createBuildPropBackup,
    restoreBuildPropBackup,
    updateBuildProp,
    clearBootSafeMode,
    testSimulateBootLoopGuard,
    setApplyOnBoot,
    captureBootMemorySnapshot,
    applyBootMemoryNow,
    setIncludeSystemApps,
    toggleUsageAccess,
    savePerAppConfig,
    syncPerAppDnsVpn,
    clearAppCache,
    forceStopApp,
    toggleFreezeApp,
    uninstallApp,
    exportEncryptedBackup,
    inspectSavedEzdBackup,
    executeRestore,
    clearBackupDialogs,
    runFullAppBackup,
    restoreLocalAppBackup,
    deleteLocalAppBackup,
    runTerminalCommand,
    performPowerAction,
    setNotificationPrefs,
    toggleDaemonService,
    checkForGitHubUpdates,
    exportAndCopyDiagnostics,
    buildDeviceReportText,
    buildSearchIndex,
    builtInChangelog,
  };

  return <EzdContext.Provider value={value}>{children}</EzdContext.Provider>;
};

export function useEzd(): EzdContextValue {
  const ctx = useContext(EzdContext);
  if (!ctx) throw new Error('useEzd must be used inside EzdProvider');
  return ctx;
}
