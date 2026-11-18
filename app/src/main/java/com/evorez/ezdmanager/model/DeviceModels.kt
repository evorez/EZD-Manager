package com.evorez.ezdmanager.model

import androidx.compose.runtime.Immutable

enum class RootStatus {
    NOT_REQUESTED,
    CHECKING,
    GRANTED,
    DENIED,
    UNAVAILABLE
}

enum class MainTab(val route: String, val label: String) {
    HOME("home", "Home"),
    PERF("perf", "Performance"),
    APPS("apps", "Apps"),
    TOOLS("tools", "Tools")
}

enum class WorkMode(val label: String, val subtitle: String) {
    AUTO("Auto", "Prefer Root -> fallback to Shizuku -> Basic"),
    ROOT("Root (su)", "Full kernel sysfs, CPU/GPU, ZRAM, Resetprop & Modules"),
    SHIZUKU("Shizuku", "Elevated ADB shell (Display, Apps, DNS, Battery, Anim)"),
    BASIC("Basic", "Safe read-only telemetry & non-root battery monitor")
}

enum class ShizukuStatus(val label: String) {
    NOT_INSTALLED("Not Installed"),
    NOT_RUNNING("Service Stopped"),
    PERMISSION_REQUIRED("Permission Needed"),
    GRANTED("Granted")
}

enum class PrivilegeMode(val label: String) {
    ROOT("Root"),
    SHIZUKU("Shizuku"),
    BASIC("Monitor")
}

enum class AppAnimStyle(val label: String, val subtitle: String) {
    IOS_CARD_MORPH("Card Morph (iOS)", "Expands smoothly from tapped card and shrinks back"),
    IOS_PUSH("iOS Push", "Smooth horizontal parallax slide with dim scrim"),
    FADE_LEFT("Fade Left", "Silky directional fade & subtle left glide"),
    SLIDE_UP("Modal Rise", "Crisp vertical spring sheet entrance")
}

enum class ThemeMode(val label: String, val description: String) {
    DARK("Dark", "Deep AMOLED dark frosted glass"),
    LIGHT("Light", "Crisp daylight frosted glass"),
    SYSTEM("System", "Follows Android system dark/light theme"),
    LITE("Lite Mode", "Zero blur or gradients · Clean ultra-fast surfaces")
}

enum class ThemeCategory(val title: String) {
    WHITE("Light & Frosted White"),
    BLACK("AMOLED & Dark Glass"),
    SPECIAL("Special Edition")
}

enum class EzdThemeStyle(
    val displayName: String,
    val subtitle: String,
    val category: ThemeCategory,
    val prefersDark: Boolean
) {
    // 5 Light / Frosted White themes
    MILK_GLASS("Milk Glass", "Clean frosted white with soft ice-blue accent", ThemeCategory.WHITE, false),
    WARM_BEIGE("Warm Beige", "Cream parchment glass with warm espresso accent", ThemeCategory.WHITE, false),
    ARCTIC_MINT("Arctic Mint", "Crisp pearl white with refreshing teal-emerald", ThemeCategory.WHITE, false),
    PEARL_ROSE("Pearl Rose", "Soft alabaster glass with muted rosewood tint", ThemeCategory.WHITE, false),
    SKY_FROST("Sky Frost", "Airy daylight frost with deep cerulean blue", ThemeCategory.WHITE, false),

    // 6 AMOLED & Dark Glass themes
    AMOLED_OBSIDIAN("AMOLED Obsidian", "Pure #000000 black with crisp ice-cobalt accent", ThemeCategory.BLACK, true),
    MIDNIGHT_COBALT("Midnight Cobalt", "Deep obsidian glass with sapphire blue trim", ThemeCategory.BLACK, true),
    EMERALD_MATRIX("Emerald Matrix", "AMOLED black with clean cyber mint-emerald", ThemeCategory.BLACK, true),
    TITANIUM_SMOKE("Titanium Smoke", "Carbon black glass with cool platinum steel", ThemeCategory.BLACK, true),
    AMETHYST_DUSK("Amethyst Dusk", "Velvet black glass with soft indigo-violet", ThemeCategory.BLACK, true),
    CRIMSON_VELVET("Crimson Velvet", "AMOLED black with restrained dark ruby glow", ThemeCategory.BLACK, true),

    // 3 Special Edition themes
    EVOREZ_REDLINE("Evorez Redline", "Signature obsidian steel with crimson accent line", ThemeCategory.SPECIAL, true),
    CHAMPAGNE_GOLD("Champagne Gold", "Deep obsidian glass with warm 24K gold trim", ThemeCategory.SPECIAL, true),
    CYBER_TELEMETRY("Cyber Telemetry", "Space-black console with dual cyan-emerald glow", ThemeCategory.SPECIAL, true)
}

/**
 * Dock position for the in-app Floating Frosted Pill Navigation Bar (old build behavior).
 */
enum class DockPosition(val label: String) {
    BOTTOM("Bottom"),
    LEFT("Left"),
    RIGHT("Right")
}

/**
 * Anchor position for the optional system-wide Top Edge Quick-Access Floating Pill Overlay.
 */
enum class PillPosition(val label: String) {
    TOP_LEFT("Top Left"),
    TOP_CENTER("Top Center"),
    TOP_RIGHT("Top Right")
}

enum class AppSortOption(val label: String) {
    NAME("Name"),
    CONFIGURED("Configured First"),
    PACKAGE("Package Name")
}

enum class AppCategoryFilter(val label: String) {
    ALL("All"),
    CONFIGURED("Configured"),
    WHITELISTED("Whitelisted")
}

enum class ToolId(
    val title: String,
    val subtitle: String
) {
    CPU_GPU_CONTROL("CPU & GPU", "Unified CPU clusters, hotplug & GPU clocks"),
    ZRAM_VM("ZRAM & VM", "Swap disksize, compression algorithm & VM cache"),
    KERNEL_TWEAKS("I/O, Thermal & UI", "I/O scheduler, thermal profile, anims & renderer"),
    DISPLAY("Display", "Refresh rate, brightness, HBM & saturation"),
    TOUCH("Touch & HW Overlay", "Touch boost, sampling rate & HW overlay compositing"),
    NETWORK("Network", "TCP Fast/Rescue, congestion & DNS"),
    AUDIO_ENHANCER("Audio Enhancer", "Bass enhancer, EQ bands & reactive visualizers"),
    BATTERY_SAVING("Battery Saving", "GMS control, background limit, trim & whitelist"),
    DEVICE_SPOOF("Device Spoof", "Editable presets, preview & resetprop restore"),
    PROFILES("Profiles", "Balanced, Performance, Powersave & Custom"),
    MODULES("Root Modules", "Magisk / KernelSU modules & WebUI"),
    BUILD_PROP("Build.prop Editor", "Safe property editor with auto-backup"),
    BOOT_GUARD("Boot Guard", "3-boot safe mode & module auto-disable"),
    EZD_BACKUP("EZD Backup", "Encrypted backup & restore for EZD Manager config"),
    APP_BACKUP("App Backup", "Backup & restore APK, Data, Ext Data, OBB & Media"),
    ROOT_TERMINAL("Shell Terminal", "Interactive Root / Shizuku shell command runner")
}

enum class PowerAction(
    val title: String,
    val description: String,
    val destructiveNote: String
) {
    REBOOT("Reboot", "Full hardware restart", "Closes all running apps and restarts Android."),
    SOFT_REBOOT("Soft Reboot", "Restart Zygote & SurfaceFlinger only", "Faster restart of Android UI/framework without full kernel reboot."),
    RECOVERY("Reboot to Recovery", "Boot into recovery environment", "Leaves normal Android OS and boots recovery."),
    BOOTLOADER("Reboot to Bootloader", "Boot into fastboot / bootloader", "Leaves normal Android OS and enters bootloader mode."),
    POWER_OFF("Power Off", "Shut down device completely", "Powers off the device safely."),
    EDL("EDL Mode", "Emergency Download Mode (Qualcomm)", "Hardware-specific emergency flash mode.")
}

enum class PerAppDnsMode(val label: String) {
    SYSTEM("System DNS"),
    CUSTOM_DNS("Custom DNS"),
    DOT("DNS over TLS"),
    DOH("DNS over HTTPS")
}

enum class GameProfileId(val id: String, val label: String, val description: String) {
    BEAST("beast", "Beast Mode", "Locks 100% CPU/GPU, unthrottles governor & forces all cores online"),
    POWERSAVE("powersave", "Power Save", "GPU 10% · CPU 30% battery saver"),
    BALANCED("balanced", "Balanced", "GPU 30% · CPU 50% optimal efficiency"),
    PERFORMANCE("performance", "Performance", "GPU 70% · CPU 70% boost with 120Hz"),
    CUSTOM("custom", "Custom", "User-configured tuning")
}

enum class GamePanelShortcut(val label: String) {
    CPU_LOCK("CPU Lock"),
    PROFILE("Profile"),
    FPS("FPS Meter"),
    HBM("HBM"),
    TOUCH_BOOST("Touch"),
    REFRESH_RATE("Hz Mode"),
    WIFI("WiFi"),
    ROTATION("Rotate"),
    DND("DND"),
    SCREENSHOT("Shot"),
    DEVICE_INFO("Open EZD")
}

@Immutable
data class NotificationPrefs(
    val daemonAlerts: Boolean = true,
    val batteryAlerts: Boolean = false,
    val unreadCount: Int = 0,
    val lastNotificationText: String = "Daemon ready"
)

@Immutable
data class DeviceOverview(
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val board: String = "",
    val hardware: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val kernelVersion: String = "",
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val batteryPercent: Int = -1,
    val chargingStatus: String = "Unknown",
    val cpuUsagePercent: Int? = null
)

@Immutable
data class CpuPolicy(
    val id: String,
    val baselineKey: String,
    val cpus: String,
    val currentKhz: Long?,
    val minKhz: Long?,
    val maxKhz: Long?,
    val availableFreqsKhz: List<Long>,
    val governors: List<String>,
    val currentGovernor: String,
    val maxWritable: Boolean,
    val minWritable: Boolean,
    val govWritable: Boolean,
    val originalMinKhz: Long?,
    val originalMaxKhz: Long?,
    val originalGovernor: String?
)

@Immutable
data class CpuCore(
    val index: Int,
    val online: Boolean,
    val hotplugSupported: Boolean,
    val currentKhz: Long?
)

@Immutable
data class GpuState(
    val supported: Boolean = false,
    val vendorHint: String = "Unavailable",
    val currentMhz: Long? = null,
    val minMhz: Long? = null,
    val maxMhz: Long? = null,
    val usagePercent: Int? = null,
    val availableMhz: List<Long> = emptyList(),
    val currentGovernor: String = "",
    val availableGovernors: List<String> = emptyList(),
    val minPath: String? = null,
    val maxPath: String? = null,
    val govPath: String? = null,
    val divisorToMhz: Long = 1_000_000L,
    val originalMinRaw: Long? = null,
    val originalMaxRaw: Long? = null,
    val originalGovernor: String? = null,
    val reason: String = "Checking GPU sysfs interfaces..."
)

@Immutable
data class DisplayModeInfo(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float
)

@Immutable
data class DisplayState(
    val currentRefreshHz: Float = 60f,
    val supportedModes: List<DisplayModeInfo> = emptyList(),
    val peakRefreshHz: Float? = null,
    val minRefreshHz: Float? = null,
    val brightness: Int = 128,
    val autoBrightness: Boolean = false,
    val hbmSupported: Boolean = false,
    val hbmPath: String? = null,
    val hbmEnabled: Boolean = false,
    val cabcSupported: Boolean = false,
    val cabcEnabled: Boolean = false,
    val cabcPath: String? = null,
    val saturationSupported: Boolean = true,
    val saturationValue: Float = 1.0f,
    val kcalSupported: Boolean = false,
    val kcalSatValue: Int = 255
)

@Immutable
data class TouchState(
    val samplingNodePath: String? = null,
    val samplingValue: String = "",
    val gamingModePath: String? = null,
    val gamingModeEnabled: Boolean = false,
    val sensitivityPath: String? = null,
    val sensitivityValue: Int = 0,
    val touchBoostPath: String? = null,
    val touchBoostEnabled: Boolean = false,
    val hwOverlaysDisabled: Boolean = false,
    val statusNote: String = ""
)

@Immutable
data class DnsPreset(
    val id: String,
    val name: String,
    val hostname: String,
    val primaryIp: String,
    val dohUrl: String,
    val badgeTag: String
)

@Immutable
data class NetworkState(
    val interfaceName: String = "Unknown",
    val transportType: String = "Unavailable",
    val tcpCongestion: String = "Unavailable",
    val availableTcp: List<String> = emptyList(),
    val tcpFastOpenSupported: Boolean = false,
    val tcpFastOpenValue: Int = 0,
    val privateDnsMode: String = "off",
    val privateDnsSpecifier: String = "",
    val originalTcpCongestion: String? = null,
    val vpnActive: Boolean = false
)

enum class AudioVisualStyle(val label: String, val subtitle: String) {
    BASS_PULSE("Bass Pulse", "Crimson & Amber sub-harmonic shockwaves"),
    PRISM_BARS("Prism Bars", "Cyan & Electric Blue mirrored bars"),
    SILK_WAVE("Silk Wave", "Violet & Magenta flowing ribbons"),
    CYBER_MATRIX("Cyber Trace", "Emerald & Teal precision telemetry")
}

@Immutable
data class AudioState(
    val activePreset: String = "Dynamic",
    val bassEnabled: Boolean = true,
    val equalizerEnabled: Boolean = true,
    val bassStrength: Int = 380,
    val bassFrequencyFocus: String = "Punch 60Hz",
    val loudnessGainMb: Int = 150,
    val visualStyle: AudioVisualStyle = AudioVisualStyle.BASS_PULSE,
    val bandLevels: List<Int> = listOf(200, 100, 50, 150, 250),
    val bandsHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val engineReady: Boolean = false,
    val bassEngineReady: Boolean = false,
    val statusNote: String = ""
)

@Immutable
data class AppBackupOptions(
    val includeBaseApk: Boolean = true,
    val includeAppData: Boolean = true,
    val includeExternalData: Boolean = true,
    val includeObb: Boolean = false,
    val includeMedia: Boolean = false
)

@Immutable
data class LocalAppBackupRecord(
    val id: String,
    val folderPath: String,
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val createdAt: String,
    val parts: List<String>,
    val totalSizeBytes: Long
)

@Immutable
data class BootMemoryConfig(
    val applyOnBoot: Boolean = false,
    val savedProfileId: String? = null,
    val savedRefreshHz: Float? = null,
    val savedDisableHwOverlays: Boolean = false,
    val savedDnsHost: String? = null,
    val savedDnsName: String? = null,
    val savedAudioPreset: String? = null,
    val lastCapturedAt: Long = 0L,
    val summary: String = "No boot snapshot saved yet"
)

@Immutable
data class SpoofPreset(
    val id: String,
    val name: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val isCustom: Boolean = false
)

@Immutable
data class SpoofState(
    val resetpropAvailable: Boolean = false,
    val currentModel: String = "",
    val currentBrand: String = "",
    val currentManufacturer: String = "",
    val currentDevice: String = "",
    val hasBaseline: Boolean = false,
    val statusMessage: String = ""
)

@Immutable
data class ProfileConfig(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean = true,
    val cpuGovernor: String = "schedutil",
    val cpuMaxScalePercent: Int = 100,
    val cpuMinScalePercent: Int = 20,
    val gpuGovernor: String = "",
    val gpuMaxPercent: Int = 100,
    val targetRefreshHz: Float? = null,
    val touchBoost: Boolean = false,
    val isBeastMode: Boolean = false
)

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val versionName: String = "",
    val isDisabled: Boolean = false
)

@Immutable
data class PerAppConfig(
    val packageName: String,
    val enabled: Boolean = false,
    val profileId: String = "balanced",
    val gpuProfile: String = "",
    val refreshRateHz: Float? = null,
    val spoofPresetId: String? = null,
    val dnsMode: PerAppDnsMode = PerAppDnsMode.SYSTEM,
    val customDnsServer: String = "1.1.1.1",
    val batteryRestricted: Boolean = false,
    val whitelisted: Boolean = false,
    val touchBoost: Boolean = false,
    val disableHwOverlays: Boolean = false
)

@Immutable
data class BatterySavingState(
    val gmsStandbyApplied: Boolean = false,
    val backgroundLimitPreset: String = "Stock",
    val trimSupported: Boolean = true,
    val restrictedCount: Int = 0,
    val whitelistCount: Int = 0,
    val lastActionSummary: String = ""
)

@Immutable
data class BackupPreview(
    val archiveVersion: Int,
    val createdAt: String,
    val includesEzdConfig: Boolean,
    val includesApps: Boolean = false,
    val appCount: Int = 0,
    val categories: List<String>,
    val checksumValid: Boolean
)

@Immutable
data class TerminalEntry(
    val id: Long,
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestampMs: Long
)

@Immutable
data class DaemonStats(
    val serviceRunning: Boolean = false,
    val rootGranted: Boolean = false,
    val commandsExecuted: Int = 0,
    val perAppMonitorActive: Boolean = false,
    val currentForegroundPkg: String = "",
    val lastAppliedPerAppPkg: String = "",
    val lastOutcome: String = "",
    val activeGamePkg: String = "",
    val logs: List<String> = emptyList()
)

@Immutable
data class SearchEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val targetTab: MainTab,
    val targetTool: ToolId? = null,
    val packageName: String? = null
)

enum class AccentColorPreset(val label: String, val argb: Long?) {
    THEME_DEFAULT("Theme Auto", null),
    CRIMSON("Crimson", 0xFFE13242),
    ELECTRIC_BLUE("Cobalt", 0xFF3B82F6),
    CYBER_CYAN("Cyan", 0xFF22D3EE),
    EMERALD("Emerald", 0xFF22C55E),
    AMETHYST("Amethyst", 0xFFA78BFA),
    AMBER("Gold", 0xFFFBBF24),
    ROSE("Rose", 0xFFFB7185)
}

@Immutable
data class ZramVmState(
    val zramSupported: Boolean = false,
    val diskSizeMb: Long = 0L,
    val usedMemMb: Long = 0L,
    val origDataMb: Long = 0L,
    val currentAlgorithm: String = "Unavailable",
    val availableAlgorithms: List<String> = emptyList(),
    val swappiness: Int = 60,
    val vfsCachePressure: Int = 100,
    val dirtyRatio: Int = 20,
    val dirtyBackgroundRatio: Int = 10,
    val statusNote: String = ""
)

@Immutable
data class IoBlockDevice(
    val name: String,
    val sysfsPath: String,
    val currentScheduler: String,
    val availableSchedulers: List<String>,
    val readAheadKb: Int
)

enum class ThermalProfileMode(val label: String, val subtitle: String) {
    STOCK("Stock Default", "Vendor thermal thresholds preserved"),
    COOL("Cool & Quiet", "Lower throttle ceiling, prioritizes low heat"),
    BALANCED("Balanced", "Adaptive thermal curve for daily performance"),
    PERFORMANCE("Performance", "Higher thermal headroom for sustained clocks"),
    MAX_HEADROOM("High Threshold", "Aggressive throttle ceiling (safety bounds preserved)")
}

enum class GpuRendererMode(val label: String, val propValue: String, val description: String) {
    SYSTEM_DEFAULT("System Default", "", "Uses ROM default HWUI pipeline"),
    SKIA_GL("Skia OpenGL (skiagl)", "skiagl", "Mature OpenGL ES 2D/UI acceleration"),
    SKIA_VK("Skia Vulkan (skiavk)", "skiavk", "Low-overhead Vulkan UI rendering pipeline")
}

@Immutable
data class IoThermalUiState(
    val blockDevices: List<IoBlockDevice> = emptyList(),
    val thermalProfile: ThermalProfileMode = ThermalProfileMode.STOCK,
    val thermalZoneCount: Int = 0,
    val thermalBackendNote: String = "",
    val windowAnimScale: Float = 1.0f,
    val transitionAnimScale: Float = 1.0f,
    val animatorDurationScale: Float = 1.0f,
    val gpuRenderer: GpuRendererMode = GpuRendererMode.SYSTEM_DEFAULT
)

@Immutable
data class RootModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val pendingRemove: Boolean,
    val managerType: String,
    val hasWebUi: Boolean = false
)

@Immutable
data class BatteryMonitorStats(
    val levelPercent: Int = 0,
    val isCharging: Boolean = false,
    val chargeSource: String = "Battery",
    val currentMa: Int = 0,
    val voltageMv: Int = 0,
    val powerWatts: Float = 0f,
    val temperatureC: Float = 0f,
    val chargeRatePctPerHr: Float = 0f,
    val activeDrainPctPerHr: Float = 0f,
    val idleDrainPctPerHr: Float = 0f,
    val avgActiveMa: Int = 0,
    val avgIdleMa: Int = 0,
    val screenOnMs: Long = 0L,
    val screenOffMs: Long = 0L,
    val awakeMs: Long = 0L,
    val deepSleepMs: Long = 0L,
    val deepSleepPercent: Float = 0f,
    val isScreenOn: Boolean = true
)

@Immutable
data class BuildPropEntry(
    val key: String,
    val value: String,
    val sourceFile: String = "build.prop"
)

@Immutable
data class BuildPropBackupInfo(
    val id: String,
    val fileName: String,
    val fullPath: String,
    val createdAt: String,
    val entryCount: Int,
    val sizeBytes: Long
)

@Immutable
data class BootGuardState(
    val safeModeActive: Boolean = false,
    val pendingBootAttempts: Int = 0,
    val bootConfirmedHealthy: Boolean = true,
    val lastReason: String = "",
    val lastTriggeredAt: Long = 0L
)

@Immutable
data class GitHubReleaseInfo(
    val currentVersion: String = "1.0.0",
    val latestTag: String = "",
    val latestName: String = "",
    val publishedAt: String = "",
    val changelogMarkdown: String = "",
    val downloadUrl: String = "",
    val updateAvailable: Boolean = false,
    val statusMessage: String = ""
)
