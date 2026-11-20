export enum RootStatus {
  NOT_REQUESTED = 'NOT_REQUESTED',
  CHECKING = 'CHECKING',
  GRANTED = 'GRANTED',
  DENIED = 'DENIED',
  UNAVAILABLE = 'UNAVAILABLE',
}

export enum MainTab {
  HOME = 'home',
  TOOLS = 'tools',
  APPS = 'apps',
  SETTINGS = 'settings',
}

export enum WorkMode {
  AUTO = 'AUTO',
  ROOT = 'ROOT',
  SHIZUKU = 'SHIZUKU',
  BASIC = 'BASIC',
}

export enum ShizukuStatus {
  NOT_INSTALLED = 'NOT_INSTALLED',
  NOT_RUNNING = 'NOT_RUNNING',
  PERMISSION_REQUIRED = 'PERMISSION_REQUIRED',
  GRANTED = 'GRANTED',
}

export enum PrivilegeMode {
  ROOT = 'ROOT',
  SHIZUKU = 'SHIZUKU',
  BASIC = 'BASIC',
}

export enum AppAnimStyle {
  IOS_CARD_MORPH = 'IOS_CARD_MORPH',
  IOS_PUSH = 'IOS_PUSH',
  FADE_LEFT = 'FADE_LEFT',
  SLIDE_UP = 'SLIDE_UP',
}

export enum ThemeMode {
  DARK = 'DARK',
  LIGHT = 'LIGHT',
  SYSTEM = 'SYSTEM',
  LITE = 'LITE',
}

export enum BackgroundTexturePreset {
  PLAIN = 'PLAIN',
  PAPER = 'PAPER',
  MOON = 'MOON',
  SILVER = 'SILVER',
}

export enum AppFontStyle {
  SYSTEM = 'SYSTEM',
  SERIF = 'SERIF',
  TYPEWRITER = 'TYPEWRITER',
}

export enum ThemeCategory {
  WHITE = 'WHITE',
  BLACK = 'BLACK',
  SPECIAL = 'SPECIAL',
}

export enum EzdThemeStyle {
  MILK_GLASS = 'MILK_GLASS',
  WARM_BEIGE = 'WARM_BEIGE',
  ARCTIC_MINT = 'ARCTIC_MINT',
  PEARL_ROSE = 'PEARL_ROSE',
  SKY_FROST = 'SKY_FROST',
  AMOLED_OBSIDIAN = 'AMOLED_OBSIDIAN',
  MIDNIGHT_COBALT = 'MIDNIGHT_COBALT',
  EMERALD_MATRIX = 'EMERALD_MATRIX',
  TITANIUM_SMOKE = 'TITANIUM_SMOKE',
  AMETHYST_DUSK = 'AMETHYST_DUSK',
  CRIMSON_VELVET = 'CRIMSON_VELVET',
  EVOREZ_REDLINE = 'EVOREZ_REDLINE',
  CHAMPAGNE_GOLD = 'CHAMPAGNE_GOLD',
  CYBER_TELEMETRY = 'CYBER_TELEMETRY',
}

export enum AccentColorPreset {
  THEME_DEFAULT = 'THEME_DEFAULT',
  CRIMSON = 'CRIMSON',
  ELECTRIC_BLUE = 'ELECTRIC_BLUE',
  CYBER_CYAN = 'CYBER_CYAN',
  EMERALD = 'EMERALD',
  AMETHYST = 'AMETHYST',
  AMBER = 'AMBER',
  ROSE = 'ROSE',
}

export enum AppSortOption {
  NAME = 'NAME',
  CONFIGURED = 'CONFIGURED',
  PACKAGE = 'PACKAGE',
}

export enum AppCategoryFilter {
  ALL = 'ALL',
  CONFIGURED = 'CONFIGURED',
  WHITELISTED = 'WHITELISTED',
}

export enum ToolId {
  CPU_GPU_CONTROL = 'CPU_GPU_CONTROL',
  ZRAM_VM = 'ZRAM_VM',
  KERNEL_TWEAKS = 'KERNEL_TWEAKS',
  DISPLAY = 'DISPLAY',
  TOUCH = 'TOUCH',
  NETWORK = 'NETWORK',
  AUDIO_ENHANCER = 'AUDIO_ENHANCER',
  BATTERY_SAVING = 'BATTERY_SAVING',
  FAST_CHARGE = 'FAST_CHARGE',
  DEVICE_SPOOF = 'DEVICE_SPOOF',
  PROFILES = 'PROFILES',
  MODULES = 'MODULES',
  BUILD_PROP = 'BUILD_PROP',
  BOOT_GUARD = 'BOOT_GUARD',
  EZD_BACKUP = 'EZD_BACKUP',
  APP_BACKUP = 'APP_BACKUP',
  ROOT_TERMINAL = 'ROOT_TERMINAL',
}

export enum PowerAction {
  REBOOT = 'REBOOT',
  SOFT_REBOOT = 'SOFT_REBOOT',
  RECOVERY = 'RECOVERY',
  BOOTLOADER = 'BOOTLOADER',
  POWER_OFF = 'POWER_OFF',
  EDL = 'EDL',
}

export enum PerAppDnsMode {
  SYSTEM = 'SYSTEM',
  CUSTOM_DNS = 'CUSTOM_DNS',
  DOT = 'DOT',
  DOH = 'DOH',
}

export enum GameProfileId {
  BEAST = 'beast',
  POWERSAVE = 'powersave',
  BALANCED = 'balanced',
  PERFORMANCE = 'performance',
  CUSTOM = 'custom',
}

export enum AudioVisualStyle {
  BASS_PULSE = 'BASS_PULSE',
  PRISM_BARS = 'PRISM_BARS',
  SILK_WAVE = 'SILK_WAVE',
  CYBER_MATRIX = 'CYBER_MATRIX',
}

export enum ThermalProfileMode {
  STOCK = 'STOCK',
  COOL = 'COOL',
  BALANCED = 'BALANCED',
  PERFORMANCE = 'PERFORMANCE',
  MAX_HEADROOM = 'MAX_HEADROOM',
}

export enum GpuRendererMode {
  SYSTEM_DEFAULT = 'SYSTEM_DEFAULT',
  SKIA_GL = 'SKIA_GL',
  SKIA_VK = 'SKIA_VK',
}

export enum EngineItemStatus {
  OK = 'OK',
  FAILED = 'FAILED',
  UNSUPPORTED = 'UNSUPPORTED',
}

export interface NotificationPrefs {
  daemonAlerts: boolean;
  batteryAlerts: boolean;
  unreadCount: number;
  lastNotificationText: string;
}

export interface DeviceOverview {
  manufacturer: string;
  model: string;
  device: string;
  board: string;
  hardware: string;
  androidVersion: string;
  sdkInt: number;
  kernelVersion: string;
  ramUsedMb: number;
  ramTotalMb: number;
  batteryPercent: number;
  chargingStatus: string;
  cpuUsagePercent: number | null;
}

export interface CpuPolicy {
  id: string;
  baselineKey: string;
  cpus: string;
  currentKhz: number | null;
  minKhz: number | null;
  maxKhz: number | null;
  availableFreqsKhz: number[];
  governors: string[];
  currentGovernor: string;
  maxWritable: boolean;
  minWritable: boolean;
  govWritable: boolean;
  originalMinKhz: number | null;
  originalMaxKhz: number | null;
  originalGovernor: string | null;
}

export interface CpuCore {
  index: number;
  online: boolean;
  hotplugSupported: boolean;
  currentKhz: number | null;
}

export interface GpuState {
  supported: boolean;
  vendorHint: string;
  currentMhz: number | null;
  minMhz: number | null;
  maxMhz: number | null;
  usagePercent: number | null;
  availableMhz: number[];
  currentGovernor: string;
  availableGovernors: string[];
  minPath: string | null;
  maxPath: string | null;
  govPath: string | null;
  originalMinMhz: number | null;
  originalMaxMhz: number | null;
  originalGovernor: string | null;
  reason: string;
  probeStatus: 'Applied' | 'ReadOnly' | 'Pending';
  pathsTried: string[];
  existingNodes: string[];
  writableNodes: string[];
}

export interface DisplayModeInfo {
  modeId: number;
  width: number;
  height: number;
  refreshRateHz: number;
}

export interface DisplayState {
  currentRefreshHz: number;
  supportedModes: DisplayModeInfo[];
  peakRefreshHz: number | null;
  minRefreshHz: number | null;
  brightness: number;
  autoBrightness: boolean;
  hbmSupported: boolean;
  hbmPath: string | null;
  hbmEnabled: boolean;
  cabcSupported: boolean;
  cabcEnabled: boolean;
  cabcPath: string | null;
  saturationSupported: boolean;
  saturationValue: number;
  kcalSupported: boolean;
  kcalSatValue: number;
  peakHardwareHz: number;
}

export interface TouchState {
  samplingNodePath: string | null;
  samplingValue: string;
  gamingModePath: string | null;
  gamingModeEnabled: boolean;
  sensitivityPath: string | null;
  sensitivityValue: number;
  touchBoostPath: string | null;
  touchBoostEnabled: boolean;
  hwOverlaysDisabled: boolean;
  statusNote: string;
}

export interface DnsPreset {
  id: string;
  name: string;
  hostname: string;
  primaryIp: string;
  dohUrl: string;
  badgeTag: string;
}

export interface NetworkState {
  interfaceName: string;
  transportType: string;
  tcpCongestion: string;
  availableTcp: string[];
  tcpFastOpenSupported: boolean;
  tcpFastOpenValue: number;
  privateDnsMode: string;
  privateDnsSpecifier: string;
  originalTcpCongestion: string | null;
  vpnActive: boolean;
}

export interface AudioState {
  activePreset: string;
  bassEnabled: boolean;
  equalizerEnabled: boolean;
  increaseLoudnessEnabled: boolean;
  bassStrength: number;
  bassFrequencyFocus: string;
  loudnessGainMb: number;
  visualStyle: AudioVisualStyle;
  bandLevels: number[];
  bandsHz: number[];
  engineReady: boolean;
  bassEngineReady: boolean;
  loudnessEngineReady: boolean;
  statusNote: string;
}

export interface AppBackupOptions {
  includeBaseApk: boolean;
  includeAppData: boolean;
  includeExternalData: boolean;
  includeObb: boolean;
  includeMedia: boolean;
}

export interface LocalAppBackupRecord {
  id: string;
  folderPath: string;
  packageName: string;
  appLabel: string;
  versionName: string;
  createdAt: string;
  parts: string[];
  totalSizeBytes: number;
}

export interface BootMemoryConfig {
  applyOnBoot: boolean;
  savedProfileId: string | null;
  savedRefreshHz: number | null;
  savedDisableHwOverlays: boolean;
  savedDnsHost: string | null;
  savedDnsName: string | null;
  savedAudioPreset: string | null;
  lastCapturedAt: number;
  summary: string;
}

export interface SpoofPreset {
  id: string;
  name: string;
  manufacturer: string;
  brand: string;
  model: string;
  device: string;
  isCustom?: boolean;
}

export interface SpoofState {
  resetpropAvailable: boolean;
  currentModel: string;
  currentBrand: string;
  currentManufacturer: string;
  currentDevice: string;
  hasBaseline: boolean;
  statusMessage: string;
}

export interface ProfileConfig {
  id: string;
  name: string;
  description: string;
  isBuiltIn: boolean;
  cpuGovernor: string;
  cpuMaxScalePercent: number;
  cpuMinScalePercent: number;
  gpuGovernor: string;
  gpuMaxPercent: number;
  targetRefreshHz: number | null;
  touchBoost: boolean;
  isBeastMode?: boolean;
}

export interface AppEntry {
  packageName: string;
  label: string;
  isSystemApp: boolean;
  versionName: string;
  isDisabled: boolean;
  colorHex?: string;
}

export interface PerAppConfig {
  packageName: string;
  enabled: boolean;
  profileId: string;
  gpuProfile: string;
  refreshRateHz: number | null;
  spoofPresetId: string | null;
  dnsMode: PerAppDnsMode;
  customDnsServer: string;
  batteryRestricted: boolean;
  whitelisted: boolean;
  touchBoost: boolean;
  disableHwOverlays: boolean;
}

export interface BatterySavingState {
  gmsStandbyApplied: boolean;
  backgroundLimitPreset: string;
  trimSupported: boolean;
  restrictedCount: number;
  whitelistCount: number;
  lastActionSummary: string;
}

export interface BackupPreview {
  archiveVersion: number;
  createdAt: string;
  includesEzdConfig: boolean;
  includesApps: boolean;
  appCount: number;
  categories: string[];
  checksumValid: boolean;
  filePath: string;
  rawPayload?: string;
}

export interface BackupReport {
  success: boolean;
  summary: string;
  filePath: string;
  failureReason?: string;
  backedUpItems: string[];
  skippedItems: string[];
}

export interface TerminalEntry {
  id: number;
  command: string;
  output: string;
  exitCode: number;
  timestampMs: number;
}

export interface DaemonStats {
  serviceRunning: boolean;
  rootGranted: boolean;
  commandsExecuted: number;
  perAppMonitorActive: boolean;
  currentForegroundPkg: string;
  lastAppliedPerAppPkg: string;
  lastOutcome: string;
  activeGamePkg: string;
  logs: string[];
}

export interface SearchEntry {
  id: string;
  title: string;
  subtitle: string;
  category: string;
  targetTab: MainTab;
  targetTool?: ToolId;
  packageName?: string;
}

export interface ZramVmState {
  zramSupported: boolean;
  diskSizeMb: number;
  usedMemMb: number;
  origDataMb: number;
  currentAlgorithm: string;
  availableAlgorithms: string[];
  swappiness: number;
  vfsCachePressure: number;
  dirtyRatio: number;
  dirtyBackgroundRatio: number;
  statusNote: string;
  probeStatus: 'Applied' | 'ReadOnly' | 'Pending';
  pathsTried: string[];
}

export interface IoBlockDevice {
  name: string;
  sysfsPath: string;
  currentScheduler: string;
  availableSchedulers: string[];
  readAheadKb: number;
}

export interface IoThermalUiState {
  blockDevices: IoBlockDevice[];
  thermalProfile: ThermalProfileMode;
  thermalZoneCount: number;
  thermalBackendNote: string;
  windowAnimScale: number;
  transitionAnimScale: number;
  animatorDurationScale: number;
  gpuRenderer: GpuRendererMode;
  ioStatusNote: string;
}

export interface RootModuleInfo {
  id: string;
  name: string;
  version: string;
  author: string;
  description: string;
  enabled: boolean;
  pendingRemove: boolean;
  managerType: string;
  hasWebUi: boolean;
  webUiHtml?: string;
}

export interface BatteryMonitorStats {
  levelPercent: number;
  isCharging: boolean;
  chargeSource: string;
  currentMa: number;
  voltageMv: number;
  powerWatts: number;
  temperatureC: number;
  chargeRatePctPerHr: number;
  activeDrainPctPerHr: number;
  idleDrainPctPerHr: number;
  avgActiveMa: number;
  avgIdleMa: number;
  screenOnMs: number;
  screenOffMs: number;
  awakeMs: number;
  deepSleepMs: number;
  deepSleepPercent: number;
  isScreenOn: boolean;
}

export interface BuildPropEntry {
  key: string;
  value: string;
  sourceFile: string;
}

export interface BuildPropBackupInfo {
  id: string;
  fileName: string;
  fullPath: string;
  createdAt: string;
  entryCount: number;
  sizeBytes: number;
  snapshot: BuildPropEntry[];
}

export interface BootGuardState {
  safeModeActive: boolean;
  pendingBootAttempts: number;
  bootConfirmedHealthy: boolean;
  lastReason: string;
  lastTriggeredAt: number;
}

export interface GitHubReleaseInfo {
  currentVersion: string;
  latestTag: string;
  latestName: string;
  publishedAt: string;
  changelogMarkdown: string;
  downloadUrl: string;
  updateAvailable: boolean;
  statusMessage: string;
}

export interface EngineStepResult {
  category: string;
  label: string;
  status: EngineItemStatus;
  reason: string;
  path: string;
}

export interface BeastExecutionReport {
  active: boolean;
  timestampMs: number;
  peakHzUsed: number;
  items: EngineStepResult[];
  summary: string;
}

export interface ThermalTelemetry {
  batteryTempC: number;
  socTempC: number | null;
  socZonePath: string | null;
  socZoneType: string;
  lastUpdatedMs: number;
}

export interface FastChargeState {
  currentMa: number;
  voltageMv: number;
  powerWatts: number;
  isCharging: boolean;
  chargeSource: string;
  chargeType: string;
  activeLimitMa: number | null;
  currentLimitPath: string | null;
  forceFastChargePath: string | null;
  forceFastChargeEnabled: boolean;
  existingNodes: string[];
  writableNodes: string[];
  pathsTried: string[];
  probeStatus: 'Applied' | 'ReadOnly' | 'Pending';
  reason: string;
}

export interface ProbeReport {
  kernelVersion: string;
  selinuxMode: string;
  rootType: string;
  displayPeakRateHz: number;
}

export interface UserSettings {
  onboardingCompleted: boolean;
  userName: string;
  userTagline: string;
  userPhotoDataUrl: string;
  themeMode: ThemeMode;
  themeStyle: EzdThemeStyle;
  animStyle: AppAnimStyle;
  workMode: WorkMode;
  notifications: NotificationPrefs;
  activeProfileId: string;
  daemonEnabled: boolean;
  savedAudioPreset: string;
  audioBassEnabled: boolean;
  audioEqEnabled: boolean;
  audioVisualStyle: AudioVisualStyle;
  audioBassFocus: string;
  bootMemory: BootMemoryConfig;
  accentPreset: AccentColorPreset;
  blurIntensity: number;
  reduceAnimations: boolean;
  floatingPillOpaque: boolean;
  beastBatteryTempLimitC: number;
  beastSocTempLimitC: number;
  beastIgnoreThermalLimits: boolean;
  audioIncreaseLoudness: boolean;
  audioLoudnessGainMb: number;
  backgroundTexture: BackgroundTexturePreset;
  backgroundTextureOpacity: number;
  appFontStyle: AppFontStyle;
  pinnedPillTools: ToolId[];
}

export interface SavedEzdBackup {
  id: string;
  fileName: string;
  path: string;
  createdAt: string;
  payload: string;
}

export interface EzdUiState {
  settings: UserSettings;
  rootStatus: RootStatus;
  shizukuStatus: ShizukuStatus;
  effectivePrivilegeMode: PrivilegeMode;
  rootManagerType: string;
  usageAccessGranted: boolean;
  writeSettingsGranted: boolean;
  notificationsGranted: boolean;
  edlSupported: boolean;
  busy: boolean;
  isRefreshingProbe: boolean;
  statusBanner: string;
  activeTool: ToolId | null;
  showSearchSheet: boolean;
  showPowerSheet: boolean;
  showNotificationsSheet: boolean;
  showDeviceDetails: boolean;
  showDeveloperEggDialog: boolean;
  pendingVpnConsentNeeded: boolean;
  vpnActive: boolean;
  includeSystemApps: boolean;
  appsLoading: boolean;
  checkingUpdate: boolean;
  diagnosticsExportPath: string;
  diagnosticsExportError: string;
  overview: DeviceOverview;
  probeReport: ProbeReport;
  cpuPolicies: CpuPolicy[];
  cpuCores: CpuCore[];
  cpuReason: string;
  cpuPathsTried: string[];
  gpuState: GpuState;
  displayState: DisplayState;
  touchState: TouchState;
  networkState: NetworkState;
  audioState: AudioState;
  zramVmState: ZramVmState;
  ioThermalUiState: IoThermalUiState;
  fastChargeState: FastChargeState;
  thermalTelemetry: ThermalTelemetry;
  batteryMonitorStats: BatteryMonitorStats;
  batterySavingState: BatterySavingState;
  spoofState: SpoofState;
  customSpoofPresets: SpoofPreset[];
  customProfiles: ProfileConfig[];
  installedApps: AppEntry[];
  perAppConfigs: Record<string, PerAppConfig>;
  batteryWhitelist: string[];
  rootModules: RootModuleInfo[];
  buildProps: BuildPropEntry[];
  buildPropBackups: BuildPropBackupInfo[];
  bootGuardState: BootGuardState;
  beastReport: BeastExecutionReport | null;
  terminalHistory: TerminalEntry[];
  releaseInfo: GitHubReleaseInfo;
  savedEzdBackups: SavedEzdBackup[];
  backupPreview: BackupPreview | null;
  backupReport: BackupReport | null;
  localAppBackups: LocalAppBackupRecord[];
}
