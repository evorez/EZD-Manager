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
    CPU("cpu", "CPU"),
    GPU("gpu", "GPU"),
    APPS("apps", "Apps"),
    TOOLS("tools", "Tools")
}

enum class ThemeMode(val label: String, val description: String) {
    DARK("Dark", "AMOLED black frosted glass with subtle red accent"),
    LIGHT("Light", "Crisp translucent white glass with red accent"),
    SYSTEM("System", "Follows Android system dark or light setting"),
    LITE("Lite Mode", "No blur or gradients · Clean red-black with warm beige contrast")
}

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
    GAMING("Game Space"),
    WHITELISTED("Whitelisted")
}

enum class ToolId(
    val title: String,
    val subtitle: String
) {
    DISPLAY("Display", "Refresh rate, brightness, HBM & saturation"),
    TOUCH("Touch", "Sampling rate, touch boost & HW overlays"),
    NETWORK("Network", "TCP Fast/Rescue, congestion & DNS"),
    AUDIO_ENHANCER("Audio Enhancer", "Hz equalizer bands, bass & presets"),
    BATTERY_SAVING("Battery Saving", "GMS control, background limit, trim & whitelist"),
    DEVICE_SPOOF("Device Spoof", "Editable presets, preview & resetprop restore"),
    PROFILES("Profiles", "Balanced, Dynamic, Performance, Max & Custom"),
    GAME_SPACE("Game Space", "Energy Cube HUD, FPS meter & gaming overlay"),
    BACKUP_RESTORE("Backup & Restore", "Encrypted EZD & granular device backup"),
    POWER_REBOOT("Power & Reboot", "Safe reboot, soft reboot, recovery & bootloader"),
    ROOT_TERMINAL("Root Shell", "Interactive root daemon command runner")
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
    BALANCED("balanced", "Balanced", "Stock dynamic CPU/GPU scaling"),
    DYNAMIC("dynamic", "Dynamic", "Responsive ramp-up with thermal awareness"),
    PERFORMANCE("performance", "Performance", "Elevated minimum clocks and boost bias"),
    MAX_PERFORMANCE("max_performance", "Maximum Performance", "Highest supported clocks while preserving thermal safety"),
    CUSTOM("custom", "Custom", "User-configured gaming tuning")
}

enum class GamePanelShortcut(val label: String) {
    CPU_LOCK("CPU Lock"),
    PROFILE("Profile"),
    FPS("FPS Meter"),
    HBM("HBM"),
    BRIGHTNESS("Brightness"),
    TOUCH_BOOST("Touch Boost"),
    REFRESH_RATE("Refresh Rate"),
    WIFI("WiFi"),
    ROTATION("Rotation"),
    DND("DND"),
    SCREENSHOT("Screenshot"),
    RECORD("Record"),
    DEVICE_INFO("Info")
}

@Immutable
data class FloatingPillConfig(
    val enabled: Boolean = false,
    val position: PillPosition = PillPosition.TOP_CENTER,
    val compactOpacity: Float = 0.88f,
    val shortcuts: List<String> = listOf("Game Space", "Power", "Network")
)

@Immutable
data class NotificationPrefs(
    val daemonAlerts: Boolean = true,
    val gameModeAlerts: Boolean = true,
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

@Immutable
data class AudioState(
    val activePreset: String = "Dynamic",
    val bassStrength: Int = 300,
    val loudnessGainMb: Int = 0,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val bandsHz: List<Int> = emptyList(),
    val engineReady: Boolean = false,
    val statusNote: String = ""
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
    val targetRefreshHz: Float? = null,
    val touchBoost: Boolean = false
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
    val gameModeAssigned: Boolean = false,
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
data class GameSpaceConfig(
    val enabled: Boolean = true,
    val activeProfile: GameProfileId = GameProfileId.BALANCED,
    val cpuLockEnabled: Boolean = false,
    val touchBoostEnabled: Boolean = false,
    val foregroundPriorityEnabled: Boolean = false,
    val hbmBoostEnabled: Boolean = false,
    val disableHwOverlays: Boolean = false,
    val dndEnabled: Boolean = false,
    val sidePanelEnabled: Boolean = false,
    val sidePanelRightSide: Boolean = true,
    val sidePanelShortcuts: Set<GamePanelShortcut> = setOf(
        GamePanelShortcut.CPU_LOCK,
        GamePanelShortcut.PROFILE,
        GamePanelShortcut.FPS,
        GamePanelShortcut.HBM,
        GamePanelShortcut.TOUCH_BOOST,
        GamePanelShortcut.REFRESH_RATE,
        GamePanelShortcut.WIFI,
        GamePanelShortcut.DND,
        GamePanelShortcut.BRIGHTNESS
    ),
    val selectedGames: Set<String> = emptySet()
)

@Immutable
data class FpsReading(
    val fps: Int? = null,
    val minFps: Int? = null,
    val avgFps: Int? = null,
    val maxFps: Int? = null,
    val source: String = "Unavailable"
)

@Immutable
data class BackupPreview(
    val archiveVersion: Int,
    val createdAt: String,
    val includesEzdConfig: Boolean,
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
