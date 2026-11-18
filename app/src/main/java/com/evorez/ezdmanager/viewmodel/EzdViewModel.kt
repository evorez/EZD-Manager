package com.evorez.ezdmanager.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evorez.ezdmanager.backup.BackupExportOptions
import com.evorez.ezdmanager.backup.BackupReport
import com.evorez.ezdmanager.backup.BackupRestoreManager
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.data.UserSettings
import com.evorez.ezdmanager.hardware.CpuGpuRepository
import com.evorez.ezdmanager.hardware.DisplayTouchRepository
import com.evorez.ezdmanager.hardware.KernelTweaksRepository
import com.evorez.ezdmanager.hardware.RootModulesAndPropRepository
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.root.CommandBridge
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.root.ShizukuManager
import com.evorez.ezdmanager.service.BatteryMonitorTracker
import com.evorez.ezdmanager.service.BootGuardManager
import com.evorez.ezdmanager.service.DaemonBridge
import com.evorez.ezdmanager.service.EzdProfileWidgetProvider
import com.evorez.ezdmanager.service.PerAppDnsVpnBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class EzdUiState(
    val settings: UserSettings = UserSettings(),
    val rootStatus: RootStatus = RootStatus.CHECKING,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val effectivePrivilegeMode: PrivilegeMode = PrivilegeMode.BASIC,
    val batteryMonitorStats: BatteryMonitorStats = BatteryMonitorStats(),
    val busy: Boolean = false,
    val statusBanner: String = "",
    val overview: DeviceOverview = DeviceOverview(),
    val cpuPolicies: List<CpuPolicy> = emptyList(),
    val cpuCores: List<CpuCore> = emptyList(),
    val gpuState: GpuState = GpuState(),
    val displayState: DisplayState = DisplayState(),
    val touchState: TouchState = TouchState(),
    val networkState: NetworkState = NetworkState(),
    val audioState: AudioState = AudioState(),
    val spoofState: SpoofState = SpoofState(),
    val customSpoofPresets: List<SpoofPreset> = emptyList(),
    val customProfiles: List<ProfileConfig> = emptyList(),
    val perAppConfigs: Map<String, PerAppConfig> = emptyMap(),
    val batterySavingState: BatterySavingState = BatterySavingState(),
    val batteryWhitelist: Set<String> = emptySet(),
    val installedApps: List<AppEntry> = emptyList(),
    val appsLoading: Boolean = false,
    val includeSystemApps: Boolean = false,
    val edlSupported: Boolean = false,
    val terminalHistory: List<TerminalEntry> = emptyList(),
    val overlayGranted: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val dndPolicyGranted: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val notificationsGranted: Boolean = true,
    val activeTool: ToolId? = null,
    val showSettingsScreen: Boolean = false,
    val showSearchSheet: Boolean = false,
    val showPowerSheet: Boolean = false,
    val showNotificationsSheet: Boolean = false,
    val showDeviceDetails: Boolean = false,
    val showDeveloperEggDialog: Boolean = false,
    val localAppBackups: List<LocalAppBackupRecord> = emptyList(),
    val backupPreview: BackupPreview? = null,
    val pendingRestorePayload: JSONObject? = null,
    val backupReport: BackupReport? = null,
    val pendingVpnConsentNeeded: Boolean = false,
    val zramVmState: ZramVmState = ZramVmState(),
    val ioThermalUiState: IoThermalUiState = IoThermalUiState(),
    val rootModules: List<RootModuleInfo> = emptyList(),
    val rootManagerType: String = "Detecting…",
    val buildProps: List<BuildPropEntry> = emptyList(),
    val buildPropBackups: List<BuildPropBackupInfo> = emptyList(),
    val bootGuardState: BootGuardState = BootGuardState(),
    val releaseInfo: GitHubReleaseInfo = GitHubReleaseInfo(),
    val checkingUpdate: Boolean = false
)

class EzdViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val store = SettingsDataStore(ctx)
    private val cpuGpuRepo = CpuGpuRepository(ctx)
    private val displayTouchRepo = DisplayTouchRepository(ctx)
    private val sysToolsRepo = SystemToolsRepository(ctx)
    private val kernelTweaksRepo = KernelTweaksRepository(ctx)
    private val modulesPropRepo = RootModulesAndPropRepository(ctx)
    private val profileRepo = ProfileRepository(ctx, cpuGpuRepo, displayTouchRepo, sysToolsRepo)
    private val backupManager = BackupRestoreManager(ctx, store)

    private val _ui = MutableStateFlow(
        EzdUiState(
            overview = cpuGpuRepo.readDeviceOverview(),
            bootGuardState = BootGuardManager.refresh(ctx),
            batteryMonitorStats = BatteryMonitorTracker.sampleNow(ctx)
        )
    )
    val ui: StateFlow<EzdUiState> = _ui.asStateFlow()

    val daemonStats: StateFlow<DaemonStats> = DaemonBridge.stats
    val vpnActive: StateFlow<Boolean> = PerAppDnsVpnBridge.vpnActive
    val vpnPackages: StateFlow<List<String>> = PerAppDnsVpnBridge.routedPackages

    private var pollingJob: Job? = null
    private var logoTapCount: Int = 0
    private var lastLogoTapMs: Long = 0L

    init {
        BootGuardManager.confirmBootHealthy(ctx)
        ShizukuManager.init(ctx)
        BatteryMonitorTracker.ensureRegistered(ctx)
        observeDataStore()
        initializeDaemonAndRoot()
        refreshLocalAppBackups()
    }

    private fun observeDataStore() {
        viewModelScope.launch {
            store.settingsFlow.collect { s ->
                _ui.update { cur ->
                    val eff = ShizukuManager.resolveEffectiveMode(s.workMode, cur.rootStatus, cur.shizukuStatus)
                    cur.copy(settings = s, effectivePrivilegeMode = eff)
                }
            }
        }
        viewModelScope.launch {
            ShizukuManager.status.collect { sh ->
                _ui.update { cur ->
                    val eff = ShizukuManager.resolveEffectiveMode(cur.settings.workMode, cur.rootStatus, sh)
                    cur.copy(shizukuStatus = sh, effectivePrivilegeMode = eff)
                }
            }
        }
        viewModelScope.launch {
            BatteryMonitorTracker.stats.collect { b ->
                _ui.update { it.copy(batteryMonitorStats = b) }
            }
        }
        viewModelScope.launch {
            store.customProfilesFlow.collect { list ->
                _ui.update { it.copy(customProfiles = list) }
            }
        }
        viewModelScope.launch {
            store.perAppConfigsFlow.collect { map ->
                val restricted = map.values.count { it.batteryRestricted }
                _ui.update {
                    it.copy(
                        perAppConfigs = map,
                        batterySavingState = it.batterySavingState.copy(restrictedCount = restricted)
                    )
                }
            }
        }
        viewModelScope.launch {
            store.customSpoofPresetsFlow.collect { list ->
                _ui.update { it.copy(customSpoofPresets = list) }
            }
        }
        viewModelScope.launch {
            store.batteryWhitelistFlow.collect { set ->
                _ui.update {
                    it.copy(
                        batteryWhitelist = set,
                        batterySavingState = it.batterySavingState.copy(whitelistCount = set.size)
                    )
                }
            }
        }
        viewModelScope.launch {
            BootGuardManager.state.collect { bg ->
                _ui.update { it.copy(bootGuardState = bg) }
            }
        }
    }

    private fun initializeDaemonAndRoot() {
        DaemonBridge.startService(ctx)
        viewModelScope.launch {
            autoDetectRoot(forceFresh = false)
            loadInstalledApps(_ui.value.includeSystemApps)
        }
    }

    private fun isRootActive(): Boolean = _ui.value.effectivePrivilegeMode == PrivilegeMode.ROOT
    private fun isShizukuActive(): Boolean = _ui.value.effectivePrivilegeMode == PrivilegeMode.SHIZUKU

    fun startTelemetry() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch(Dispatchers.Default) {
            var tick = 0
            while (isActive) {
                val overview = cpuGpuRepo.readDeviceOverview()
                val batterySnap = BatteryMonitorTracker.sampleNow(ctx)
                val rootGranted = isRootActive()
                val tool = _ui.value.activeTool
                val heavyDue = tick % 3 == 0 || tool == ToolId.CPU_GPU_CONTROL || tool == ToolId.DISPLAY

                val policies = if (rootGranted && heavyDue) cpuGpuRepo.scanCpuPolicies() else _ui.value.cpuPolicies
                val cores = if (rootGranted && tool == ToolId.CPU_GPU_CONTROL) cpuGpuRepo.scanCpuCores() else _ui.value.cpuCores
                val gpu = if (rootGranted && heavyDue) cpuGpuRepo.scanGpu() else _ui.value.gpuState
                val display = if (heavyDue) displayTouchRepo.readDisplayState(rootGranted) else _ui.value.displayState

                _ui.update { current ->
                    current.copy(
                        overview = overview,
                        batteryMonitorStats = batterySnap,
                        cpuPolicies = if (rootGranted) policies else emptyList(),
                        cpuCores = if (rootGranted) cores else emptyList(),
                        gpuState = if (rootGranted) gpu else GpuState(
                            supported = false,
                            reason = "GPU frequency nodes require Root privilege."
                        ),
                        displayState = display,
                        overlayGranted = if (tick % 3 == 0) Settings.canDrawOverlays(ctx) else current.overlayGranted,
                        usageAccessGranted = if (tick % 3 == 0) hasUsageStatsPermission() else current.usageAccessGranted,
                        dndPolicyGranted = if (tick % 3 == 0) hasDndPermission() else current.dndPolicyGranted,
                        writeSettingsGranted = if (tick % 3 == 0) Settings.System.canWrite(ctx) else current.writeSettingsGranted,
                        notificationsGranted = if (tick % 3 == 0) hasPostNotificationsPermission() else current.notificationsGranted
                    )
                }
                tick++
                delay(2_400L)
            }
        }
    }

    fun stopTelemetry() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun requestShizukuPermission() {
        val next = ShizukuManager.requestPermission()
        _ui.update { cur ->
            val eff = ShizukuManager.resolveEffectiveMode(cur.settings.workMode, cur.rootStatus, next)
            cur.copy(
                shizukuStatus = next,
                effectivePrivilegeMode = eff,
                statusBanner = when (next) {
                    ShizukuStatus.NOT_INSTALLED -> "Shizuku app is not installed on this device."
                    ShizukuStatus.NOT_RUNNING -> "Open the Shizuku app and start the Shizuku service first."
                    ShizukuStatus.PERMISSION_REQUIRED -> "Shizuku permission dialog requested…"
                    ShizukuStatus.GRANTED -> "Shizuku privilege active!"
                }
            )
        }
    }

    fun refreshPrivilegeModes() {
        autoDetectRoot(forceFresh = false)
        val sh = ShizukuManager.refreshStatus(ctx)
        _ui.update { cur ->
            cur.copy(
                shizukuStatus = sh,
                effectivePrivilegeMode = ShizukuManager.resolveEffectiveMode(cur.settings.workMode, cur.rootStatus, sh)
            )
        }
    }

    fun autoDetectRoot(forceFresh: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(rootStatus = RootStatus.CHECKING) }
            val status = RootShellManager.probeRoot(forceFreshRequest = forceFresh)
            val shStatus = ShizukuManager.refreshStatus(ctx)
            DaemonBridge.update { it.copy(rootGranted = status == RootStatus.GRANTED) }
            val edlOk = if (status == RootStatus.GRANTED) sysToolsRepo.isEdlVerifiedSupported() else false
            _ui.update { cur ->
                val eff = ShizukuManager.resolveEffectiveMode(cur.settings.workMode, status, shStatus)
                cur.copy(
                    rootStatus = status,
                    shizukuStatus = shStatus,
                    effectivePrivilegeMode = eff,
                    edlSupported = edlOk,
                    statusBanner = when {
                        status == RootStatus.GRANTED -> ""
                        shStatus == ShizukuStatus.GRANTED -> "Running in Shizuku ADB mode (Rootless tuning enabled)."
                        status == RootStatus.DENIED && forceFresh -> "Root denied. Grant su or enable Shizuku in Work Mode."
                        else -> ""
                    }
                )
            }
            refreshAllHardware()
        }
    }

    fun refreshAllHardware() {
        viewModelScope.launch(Dispatchers.Default) {
            val rootGranted = isRootActive()
            val shizukuGranted = isShizukuActive()
            val overview = cpuGpuRepo.readDeviceOverview()
            val policies = if (rootGranted) cpuGpuRepo.scanCpuPolicies() else emptyList()
            val cores = if (rootGranted) cpuGpuRepo.scanCpuCores() else emptyList()
            val gpu = if (rootGranted) cpuGpuRepo.scanGpu() else GpuState(false, "Requires Root access")
            val display = displayTouchRepo.readDisplayState(rootGranted)
            val touch = displayTouchRepo.scanTouch(shizukuGranted)
            val net = sysToolsRepo.scanNetwork(vpnActive = vpnActive.value)
            val spoof = if (rootGranted) sysToolsRepo.scanSpoofState() else SpoofState(statusMessage = "Requires Root / resetprop")
            val edlOk = if (rootGranted) sysToolsRepo.isEdlVerifiedSupported() else false
            _ui.update {
                it.copy(
                    overview = overview,
                    cpuPolicies = policies,
                    cpuCores = cores,
                    gpuState = gpu,
                    displayState = display,
                    touchState = touch,
                    networkState = net,
                    spoofState = spoof,
                    edlSupported = edlOk,
                    overlayGranted = Settings.canDrawOverlays(ctx),
                    usageAccessGranted = hasUsageStatsPermission(),
                    dndPolicyGranted = hasDndPermission(),
                    writeSettingsGranted = Settings.System.canWrite(ctx),
                    notificationsGranted = hasPostNotificationsPermission()
                )
            }
        }
    }

    // --- Power & Reboot (Phase 4) ---
    fun performPowerAction(action: PowerAction) {
        runAction {
            val (_, msg) = sysToolsRepo.executePowerAction(action)
            pushNotification(msg)
            _ui.update { it.copy(statusBanner = msg, showPowerSheet = false) }
        }
    }

    // --- CPU & GPU Controls ---
    fun setCpuFrequencyRange(policy: CpuPolicy, newMinKhz: Long?, newMaxKhz: Long?) {
        runAction {
            val (_, msg) = cpuGpuRepo.setCpuFrequencyRange(policy, newMinKhz, newMaxKhz)
            val updated = cpuGpuRepo.scanCpuPolicies()
            _ui.update { it.copy(cpuPolicies = updated, statusBanner = msg) }
        }
    }

    fun setCpuGovernor(policy: CpuPolicy, governor: String) {
        runAction {
            val (_, msg) = cpuGpuRepo.setGovernor(policy, governor)
            val updated = cpuGpuRepo.scanCpuPolicies()
            _ui.update { it.copy(cpuPolicies = updated, statusBanner = msg) }
        }
    }

    fun resetCpuPolicy(policy: CpuPolicy) {
        runAction {
            val (_, msg) = cpuGpuRepo.resetPolicy(policy)
            val updated = cpuGpuRepo.scanCpuPolicies()
            _ui.update { it.copy(cpuPolicies = updated, statusBanner = msg) }
        }
    }

    fun toggleCpuCore(core: CpuCore, online: Boolean) {
        runAction {
            val (_, msg) = cpuGpuRepo.setCoreOnline(core, online)
            val cores = cpuGpuRepo.scanCpuCores()
            _ui.update { it.copy(cpuCores = cores, statusBanner = msg) }
        }
    }

    fun setGpuFrequency(minMhz: Long?, maxMhz: Long?) {
        runAction {
            val (_, msg) = cpuGpuRepo.setGpuFrequency(_ui.value.gpuState, minMhz, maxMhz)
            val gpu = cpuGpuRepo.scanGpu()
            _ui.update { it.copy(gpuState = gpu, statusBanner = msg) }
        }
    }

    fun setGpuGovernor(governor: String) {
        runAction {
            val (_, msg) = cpuGpuRepo.setGpuGovernor(_ui.value.gpuState, governor)
            val gpu = cpuGpuRepo.scanGpu()
            _ui.update { it.copy(gpuState = gpu, statusBanner = msg) }
        }
    }

    fun resetGpu() {
        runAction {
            val (_, msg) = cpuGpuRepo.resetGpu(_ui.value.gpuState)
            val gpu = cpuGpuRepo.scanGpu()
            _ui.update { it.copy(gpuState = gpu, statusBanner = msg) }
        }
    }

    // --- Profiles & Game Space (Phase 12) ---
    fun allProfiles(): List<ProfileConfig> = ProfileRepository.builtInProfiles() + _ui.value.customProfiles

    fun applyProfile(profile: ProfileConfig) {
        runAction {
            store.setActiveProfileId(profile.id)
            val (_, msg) = profileRepo.applyProfile(
                profile = profile,
                rootAvailable = isRootActive(),
                shizukuAvailable = isShizukuActive()
            )
            EzdProfileWidgetProvider.updateAllWidgets(ctx, profile.id)
            pushNotification(msg)
            refreshAllHardware()
            _ui.update { it.copy(statusBanner = msg) }
        }
    }

    fun applyProfile(profileId: String) {
        val found = allProfiles().firstOrNull { it.id.equals(profileId, ignoreCase = true) }
            ?: ProfileRepository.builtInProfiles().firstOrNull { it.id.equals(profileId, ignoreCase = true) }
        if (found != null) {
            applyProfile(found)
        } else {
            runAction {
                store.setActiveProfileId(profileId)
                EzdProfileWidgetProvider.updateAllWidgets(ctx, profileId)
                _ui.update { it.copy(statusBanner = "Active profile: $profileId") }
            }
        }
    }

    fun saveCustomProfile(profile: ProfileConfig) {
        viewModelScope.launch {
            val existing = _ui.value.customProfiles.toMutableList()
            val idx = existing.indexOfFirst { it.id == profile.id }
            if (idx >= 0) existing[idx] = profile else existing.add(profile)
            store.saveCustomProfiles(existing)
            _ui.update { it.copy(statusBanner = "Saved profile: ${profile.name}") }
        }
    }

    fun deleteCustomProfile(profileId: String) {
        viewModelScope.launch {
            store.saveCustomProfiles(_ui.value.customProfiles.filterNot { it.id == profileId })
            _ui.update { it.copy(statusBanner = "Deleted custom profile") }
        }
    }

    fun resetCustomProfiles() {
        viewModelScope.launch {
            store.saveCustomProfiles(emptyList())
            applyProfile(ProfileRepository.builtInProfiles().first())
        }
    }

    fun launchApp(packageName: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) {
            runCatching { ctx.startActivity(intent) }
        } else {
            _ui.update { it.copy(statusBanner = "No launchable activity found for $packageName") }
        }
    }

    // --- Display & Touch ---
    fun setRefreshRate(hz: Float) = runAction {
        val rootGranted = isRootActive()
        val shizukuGranted = isShizukuActive()
        val (_, msg) = displayTouchRepo.setRefreshRate(hz, rootGranted, shizukuGranted)
        _ui.update { it.copy(displayState = displayTouchRepo.readDisplayState(rootGranted), statusBanner = msg) }
    }

    fun resetRefreshRate() = runAction {
        val rootGranted = isRootActive()
        val shizukuGranted = isShizukuActive()
        val (_, msg) = displayTouchRepo.resetRefreshRate(rootGranted, shizukuGranted)
        _ui.update { it.copy(displayState = displayTouchRepo.readDisplayState(rootGranted), statusBanner = msg) }
    }

    fun setBrightness(value: Int, auto: Boolean) = runAction {
        val rootGranted = isRootActive()
        val shizukuGranted = isShizukuActive()
        val (_, msg) = displayTouchRepo.setBrightness(value, auto, rootGranted, shizukuGranted)
        _ui.update { it.copy(displayState = displayTouchRepo.readDisplayState(rootGranted), statusBanner = msg) }
    }

    fun setHbm(enabled: Boolean) {
        val path = _ui.value.displayState.hbmPath ?: return
        runAction {
            val (_, msg) = displayTouchRepo.setHbm(path, enabled)
            _ui.update {
                it.copy(
                    displayState = displayTouchRepo.readDisplayState(isRootActive()),
                    statusBanner = msg
                )
            }
        }
    }

    fun setDisplaySaturation(saturation: Float) = runAction {
        val (_, msg) = displayTouchRepo.setSaturation(
            saturation,
            _ui.value.displayState.kcalSupported,
            rootGranted = isRootActive(),
            shizukuGranted = isShizukuActive()
        )
        _ui.update { it.copy(displayState = it.displayState.copy(saturationValue = saturation), statusBanner = msg) }
    }

    fun setCabc(enabled: Boolean) {
        val path = _ui.value.displayState.cabcPath ?: return
        runAction {
            val (_, msg) = displayTouchRepo.setCabc(path, enabled)
            _ui.update {
                it.copy(
                    displayState = displayTouchRepo.readDisplayState(isRootActive()),
                    statusBanner = msg
                )
            }
        }
    }

    fun setTouchControl(path: String, value: String, label: String) = runAction {
        val (_, msg) = displayTouchRepo.setTouchNode(path, value, label)
        _ui.update { it.copy(touchState = displayTouchRepo.scanTouch(isShizukuActive()), statusBanner = msg) }
    }

    fun setDisableHwOverlays(disable: Boolean) = runAction {
        val (_, msg) = displayTouchRepo.setDisableHwOverlays(
            disable,
            rootGranted = isRootActive(),
            shizukuGranted = isShizukuActive()
        )
        _ui.update { it.copy(touchState = displayTouchRepo.scanTouch(isShizukuActive()), statusBanner = msg) }
    }

    // --- Network (Phase 9) ---
    fun setTcpCongestion(algo: String) = runAction {
        val (_, msg) = sysToolsRepo.setTcpCongestion(algo)
        _ui.update { it.copy(networkState = sysToolsRepo.scanNetwork(vpnActive.value), statusBanner = msg) }
    }

    fun applyTcpFastPreset() = runAction {
        val (_, msg) = sysToolsRepo.applyTcpFastPreset(_ui.value.networkState.availableTcp)
        _ui.update { it.copy(networkState = sysToolsRepo.scanNetwork(vpnActive.value), statusBanner = msg) }
    }

    fun applyTcpRescuePreset() = runAction {
        val (_, msg) = sysToolsRepo.applyTcpRescuePreset(_ui.value.networkState.availableTcp)
        _ui.update { it.copy(networkState = sysToolsRepo.scanNetwork(vpnActive.value), statusBanner = msg) }
    }

    fun applyDnsPreset(preset: DnsPreset) = runAction {
        val (_, msg) = sysToolsRepo.applyDnsPreset(preset, isRootActive(), isShizukuActive())
        _ui.update { it.copy(networkState = sysToolsRepo.scanNetwork(vpnActive.value), statusBanner = msg) }
    }

    fun resetNetworkSettings() = runAction {
        val (_, msg) = sysToolsRepo.resetNetworkSettings(_ui.value.networkState.availableTcp, isRootActive(), isShizukuActive())
        _ui.update { it.copy(networkState = sysToolsRepo.scanNetwork(vpnActive.value), statusBanner = msg) }
    }

    // --- Audio Enhancer (Phase 8: Independent Bass Enhancer + Multi-Style EQ) ---
    fun selectAudioPreset(name: String) {
        viewModelScope.launch {
            store.setAudioPreset(name)
            val updated = sysToolsRepo.applyAudioPreset(name, _ui.value.audioState)
            store.setAudioFlags(visualStyle = updated.visualStyle)
            _ui.update { it.copy(audioState = updated, statusBanner = "Audio preset: $name") }
        }
    }

    fun toggleBassEnhancer(enabled: Boolean) {
        viewModelScope.launch {
            store.setAudioFlags(bassEnabled = enabled)
            val s = _ui.value.audioState.copy(bassEnabled = enabled)
            sysToolsRepo.applyAudioParams(s.bandLevels, s.bassStrength, s.loudnessGainMb, enabled, s.equalizerEnabled, s.bassFrequencyFocus)
            _ui.update { it.copy(audioState = s, statusBanner = "Bass Enhancer ${if (enabled) "enabled" else "muted"}") }
        }
    }

    fun toggleEqualizer(enabled: Boolean) {
        viewModelScope.launch {
            store.setAudioFlags(eqEnabled = enabled)
            val s = _ui.value.audioState.copy(equalizerEnabled = enabled)
            sysToolsRepo.applyAudioParams(s.bandLevels, s.bassStrength, s.loudnessGainMb, s.bassEnabled, enabled, s.bassFrequencyFocus)
            _ui.update { it.copy(audioState = s, statusBanner = "Equalizer ${if (enabled) "enabled" else "bypassed"}") }
        }
    }

    fun setBassFrequencyFocus(focus: String) {
        viewModelScope.launch {
            store.setAudioFlags(bassFocus = focus)
            val s = _ui.value.audioState.copy(bassFrequencyFocus = focus)
            sysToolsRepo.applyAudioParams(s.bandLevels, s.bassStrength, s.loudnessGainMb, s.bassEnabled, s.equalizerEnabled, focus)
            _ui.update { it.copy(audioState = s, statusBanner = "Bass frequency focus: $focus") }
        }
    }

    fun setAudioVisualStyle(style: AudioVisualStyle) {
        viewModelScope.launch {
            store.setAudioFlags(visualStyle = style)
            _ui.update { it.copy(audioState = it.audioState.copy(visualStyle = style)) }
        }
    }

    fun updateAudioManual(levels: List<Int>, bass: Int, loud: Int) {
        val cur = _ui.value.audioState
        sysToolsRepo.applyAudioParams(levels, bass, loud, cur.bassEnabled, cur.equalizerEnabled, cur.bassFrequencyFocus)
        _ui.update {
            it.copy(
                audioState = it.audioState.copy(
                    activePreset = "Custom",
                    bandLevels = levels,
                    bassStrength = bass,
                    loudnessGainMb = loud
                )
            )
        }
    }

    // --- Battery Saving + Monitor + Whitelist ---
    fun resetBatteryMonitorStats() {
        val snap = BatteryMonitorTracker.resetCounters(ctx)
        _ui.update { it.copy(batteryMonitorStats = snap, statusBanner = "Battery drain & Screen On counters reset") }
    }

    fun applyGmsBackgroundManagement(restrictNonEssential: Boolean) = runAction {
        val (ok, msg) = sysToolsRepo.applyGmsBackgroundManagement(restrictNonEssential, isRootActive(), isShizukuActive())
        _ui.update {
            it.copy(
                batterySavingState = it.batterySavingState.copy(
                    gmsStandbyApplied = if (ok) restrictNonEssential else it.batterySavingState.gmsStandbyApplied,
                    lastActionSummary = msg
                ),
                statusBanner = msg
            )
        }
    }

    fun applyBackgroundAppLimit(preset: String) = runAction {
        val (ok, msg) = sysToolsRepo.applyBackgroundAppLimit(preset, isRootActive(), isShizukuActive())
        _ui.update {
            it.copy(
                batterySavingState = it.batterySavingState.copy(
                    backgroundLimitPreset = if (ok) preset else it.batterySavingState.backgroundLimitPreset,
                    lastActionSummary = msg
                ),
                statusBanner = msg
            )
        }
    }

    fun runSafeMemoryTrim() = runAction {
        val (_, msg) = sysToolsRepo.runSafeMemoryTrim(
            _ui.value.installedApps,
            _ui.value.batteryWhitelist,
            isRootActive(),
            isShizukuActive()
        )
        _ui.update {
            it.copy(
                batterySavingState = it.batterySavingState.copy(lastActionSummary = msg),
                statusBanner = msg
            )
        }
    }

    fun setAppBackgroundRestriction(packageName: String, restrict: Boolean) = runAction {
        val (ok, msg) = sysToolsRepo.setAppBackgroundRestriction(
            packageName,
            restrict,
            _ui.value.batteryWhitelist,
            isRootActive(),
            isShizukuActive()
        )
        if (ok) {
            val current = _ui.value.perAppConfigs[packageName] ?: PerAppConfig(packageName = packageName)
            savePerAppConfig(current.copy(batteryRestricted = restrict))
        }
        _ui.update { it.copy(statusBanner = msg) }
    }

    fun toggleWhitelistApp(packageName: String) {
        viewModelScope.launch {
            val current = _ui.value.batteryWhitelist
            val next = if (packageName in current) current - packageName else current + packageName
            store.saveBatteryWhitelist(next)
            // Automatically clear battery restriction if app just became whitelisted
            if (packageName in next) {
                val appCfg = _ui.value.perAppConfigs[packageName]
                if (appCfg?.batteryRestricted == true) {
                    savePerAppConfig(appCfg.copy(batteryRestricted = false, whitelisted = true))
                }
            }
            _ui.update {
                it.copy(
                    statusBanner = if (packageName in next) "Whitelisted $packageName (protected from kill/restrict)" else "Removed $packageName from Whitelist"
                )
            }
        }
    }

    // --- Device Spoof (Phase 7: Add, Edit, Rename, Delete, Reset) ---
    fun allSpoofPresets(): List<SpoofPreset> = SystemToolsRepository.BUILT_IN_SPOOF_PRESETS + _ui.value.customSpoofPresets

    fun applySpoofPreset(preset: SpoofPreset) = runAction {
        val (_, msg) = sysToolsRepo.applySpoofPreset(preset)
        _ui.update { it.copy(spoofState = sysToolsRepo.scanSpoofState(), statusBanner = msg) }
    }

    fun upsertCustomSpoofPreset(preset: SpoofPreset) {
        viewModelScope.launch {
            val list = _ui.value.customSpoofPresets.toMutableList()
            val idx = list.indexOfFirst { it.id == preset.id }
            if (idx >= 0) list[idx] = preset.copy(isCustom = true) else list.add(preset.copy(isCustom = true))
            store.saveCustomSpoofPresets(list)
            _ui.update { it.copy(statusBanner = "Saved spoof preset: ${preset.name}") }
        }
    }

    fun deleteCustomSpoofPreset(id: String) {
        viewModelScope.launch {
            store.saveCustomSpoofPresets(_ui.value.customSpoofPresets.filterNot { it.id == id })
            _ui.update { it.copy(statusBanner = "Deleted custom spoof preset") }
        }
    }

    fun resetSpoof() = runAction {
        val (_, msg) = sysToolsRepo.resetSpoofToOriginal()
        _ui.update { it.copy(spoofState = sysToolsRepo.scanSpoofState(), statusBanner = msg) }
    }

    // --- Apps & Per-App DNS (Phase 5 & Phase 6) ---
    fun setIncludeSystemApps(include: Boolean) {
        _ui.update { it.copy(includeSystemApps = include) }
        loadInstalledApps(include)
    }

    fun loadInstalledApps(includeSystem: Boolean = _ui.value.includeSystemApps) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(appsLoading = true) }
            val apps = sysToolsRepo.loadInstalledApps(includeSystem = includeSystem)
            _ui.update { it.copy(installedApps = apps, appsLoading = false) }
        }
    }

    fun savePerAppConfig(config: PerAppConfig) {
        viewModelScope.launch {
            val updated = _ui.value.perAppConfigs.toMutableMap()
            updated[config.packageName] = config
            store.savePerAppConfigs(updated)
            DaemonBridge.requestSync()

            // Sync battery whitelist
            val wl = _ui.value.batteryWhitelist
            val nextWl = if (config.whitelisted) wl + config.packageName else wl - config.packageName
            if (nextWl != wl) store.saveBatteryWhitelist(nextWl)

            syncPerAppDnsVpn(updated)
            _ui.update { it.copy(statusBanner = "Saved tuning for ${config.packageName}") }
        }
    }

    fun syncPerAppDnsVpn(configs: Map<String, PerAppConfig> = _ui.value.perAppConfigs) {
        val needsVpn = configs.values.any { it.dnsMode != PerAppDnsMode.SYSTEM }
        if (!needsVpn) {
            PerAppDnsVpnBridge.stop(ctx)
            _ui.update { it.copy(pendingVpnConsentNeeded = false) }
            return
        }
        val prepareIntent = VpnService.prepare(ctx)
        if (prepareIntent == null) {
            PerAppDnsVpnBridge.startOrUpdate(ctx)
            _ui.update { it.copy(pendingVpnConsentNeeded = false) }
        } else {
            _ui.update { it.copy(pendingVpnConsentNeeded = true) }
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        _ui.update { it.copy(pendingVpnConsentNeeded = false) }
        if (granted) {
            PerAppDnsVpnBridge.startOrUpdate(ctx)
            _ui.update { it.copy(statusBanner = "Per-App DNS VPN started.") }
        } else {
            _ui.update { it.copy(statusBanner = "VPN consent denied; per-app DNS tunnel inactive.") }
        }
    }

    fun clearAppCache(pkg: String) = runAction {
        val (_, msg) = sysToolsRepo.clearAppCache(pkg, isRootActive(), isShizukuActive())
        _ui.update { it.copy(statusBanner = msg) }
    }

    fun forceStopApp(pkg: String) = runAction {
        val (_, msg) = sysToolsRepo.forceStopApp(pkg, isRootActive(), isShizukuActive())
        _ui.update { it.copy(statusBanner = msg) }
    }

    fun toggleFreezeApp(app: AppEntry) = runAction {
        val (_, msg) = sysToolsRepo.toggleFreezeApp(app.packageName, !app.isDisabled, isRootActive(), isShizukuActive())
        loadInstalledApps()
        _ui.update { it.copy(statusBanner = msg) }
    }

    fun uninstallApp(app: AppEntry) = runAction {
        val (_, msg) = sysToolsRepo.uninstallApp(app, isRootActive(), isShizukuActive())
        loadInstalledApps()
        _ui.update { it.copy(statusBanner = msg) }
    }

    // --- Backup & Restore (Phase 11) ---
    fun exportEncryptedBackup(uri: Uri, options: BackupExportOptions) {
        runAction {
            val report = backupManager.createEncryptedBackup(uri, options, _ui.value.rootStatus)
            _ui.update { it.copy(backupReport = report, statusBanner = report.summary) }
        }
    }

    fun inspectBackupArchive(uri: Uri, passphrase: String) {
        runAction {
            val res = backupManager.inspectBackupArchive(uri, passphrase)
            res.onSuccess { (preview, payload) ->
                _ui.update {
                    it.copy(
                        backupPreview = preview,
                        pendingRestorePayload = payload,
                        statusBanner = if (preview.checksumValid) "Archive verified (SHA-256 valid)" else "Warning: SHA-256 integrity mismatch"
                    )
                }
            }.onFailure { err ->
                _ui.update { it.copy(statusBanner = err.message ?: "Failed to inspect backup archive") }
            }
        }
    }

    fun executeRestore(restoreEzdConfig: Boolean, restoreNetworkConfig: Boolean) {
        val payload = _ui.value.pendingRestorePayload ?: return
        runAction {
            val report = backupManager.restoreFromPayload(payload, restoreEzdConfig, restoreNetworkConfig, _ui.value.rootStatus)
            _ui.update {
                it.copy(
                    backupReport = report,
                    backupPreview = null,
                    pendingRestorePayload = null,
                    statusBanner = report.summary
                )
            }
        }
    }

    fun clearBackupDialogs() {
        _ui.update { it.copy(backupPreview = null, pendingRestorePayload = null, backupReport = null) }
    }

    fun refreshLocalAppBackups() {
        viewModelScope.launch {
            val list = backupManager.listLocalAppBackups()
            _ui.update { it.copy(localAppBackups = list) }
        }
    }

    fun runFullAppBackup(selectedApps: Set<String>, options: AppBackupOptions) {
        runAction {
            val report = backupManager.createLocalAppBackups(selectedApps, options, _ui.value.rootStatus)
            val list = backupManager.listLocalAppBackups()
            _ui.update { it.copy(backupReport = report, localAppBackups = list, statusBanner = report.summary) }
        }
    }

    fun restoreLocalAppBackup(record: LocalAppBackupRecord) {
        runAction {
            val report = backupManager.restoreLocalAppBackup(record, _ui.value.rootStatus)
            loadInstalledApps(_ui.value.includeSystemApps)
            _ui.update { it.copy(backupReport = report, statusBanner = report.summary) }
        }
    }

    fun deleteLocalAppBackup(record: LocalAppBackupRecord) {
        viewModelScope.launch {
            backupManager.deleteLocalAppBackup(record)
            val list = backupManager.listLocalAppBackups()
            _ui.update { it.copy(localAppBackups = list, statusBanner = "Deleted backup for ${record.appLabel}") }
        }
    }

    // --- Privileged Shell Terminal (Root / Shizuku / Local) ---
    fun runTerminalCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isBlank()) return
        if (cmd == "clear") {
            _ui.update { it.copy(terminalHistory = emptyList()) }
            return
        }
        viewModelScope.launch {
            val res = when {
                isRootActive() -> RootShellManager.exec(cmd, timeoutMs = 10_000L)
                isShizukuActive() -> CommandBridge.execPrivileged(cmd, useRoot = false, useShizuku = true, timeoutMs = 10_000L)
                else -> RootShellManager.execUnprivileged(cmd, timeoutMs = 5_000L)
            }
            DaemonBridge.update { it.copy(commandsExecuted = RootShellManager.executedCount) }
            val entry = TerminalEntry(
                id = System.nanoTime(),
                command = cmd,
                output = res.combinedText.ifBlank { "(no output)" },
                exitCode = res.exitCode,
                timestampMs = System.currentTimeMillis()
            )
            _ui.update { it.copy(terminalHistory = (it.terminalHistory + entry).takeLast(50)) }
        }
    }

    // --- Onboarding, User Profile & Preferences ---
    fun completeOnboarding(
        userName: String,
        tagline: String,
        photoUri: Uri?,
        workMode: WorkMode
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedPath = photoUri?.let { saveProfilePhotoUri(it) } ?: ""
            store.setWorkMode(workMode)
            store.saveUserProfile(
                name = userName,
                tagline = tagline,
                photoPath = savedPath,
                markOnboardingDone = true
            )
        }
    }

    fun updateUserProfile(userName: String, tagline: String, newPhotoUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val pathToSave = if (newPhotoUri != null) saveProfilePhotoUri(newPhotoUri) else null
            store.saveUserProfile(name = userName, tagline = tagline, photoPath = pathToSave)
            _ui.update { it.copy(statusBanner = "Profile updated for $userName") }
        }
    }

    private fun saveProfilePhotoUri(uri: Uri): String {
        return runCatching {
            val dest = File(ctx.filesDir, "user_profile.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val rawBytes = input.readBytes()
                val bmp = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                if (bmp != null) {
                    val maxDim = 320
                    val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
                    val target = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).toInt().coerceAtLeast(1),
                            (bmp.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    } else bmp
                    dest.outputStream().use { out ->
                        target.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    if (target !== bmp) target.recycle()
                    bmp.recycle()
                } else {
                    dest.writeBytes(rawBytes)
                }
            }
            dest.absolutePath
        }.getOrDefault("")
    }

    fun setWorkMode(mode: WorkMode) {
        viewModelScope.launch {
            store.setWorkMode(mode)
            refreshPrivilegeModes()
        }
    }

    fun setAnimStyle(style: AppAnimStyle) = viewModelScope.launch { store.setAnimStyle(style) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }
    fun setThemeStyle(style: EzdThemeStyle) = viewModelScope.launch { store.setThemeStyle(style) }
    fun setDockPosition(pos: DockPosition) = viewModelScope.launch { store.setDockPosition(pos) }
    fun setNotificationPrefs(prefs: NotificationPrefs) = viewModelScope.launch { store.setNotificationPrefs(prefs) }
    fun setAccentPreset(preset: AccentColorPreset) = viewModelScope.launch { store.setAccentPreset(preset) }
    fun setBlurIntensity(intensity: Float) = viewModelScope.launch { store.setBlurIntensity(intensity) }
    fun setHapticsEnabled(enabled: Boolean) = viewModelScope.launch { store.setHapticsEnabled(enabled) }
    fun setReduceAnimations(reduce: Boolean) = viewModelScope.launch { store.setReduceAnimations(reduce) }
    fun setFloatingPillOpaque(opaque: Boolean) = viewModelScope.launch { store.setFloatingPillOpaque(opaque) }

    fun markNotificationsRead() {
        viewModelScope.launch {
            val cur = _ui.value.settings.notifications
            if (cur.unreadCount > 0) {
                store.setNotificationPrefs(cur.copy(unreadCount = 0))
            }
        }
    }

    private suspend fun pushNotification(text: String, title: String = "EZD Performance") {
        val cur = _ui.value.settings.notifications
        store.setNotificationPrefs(cur.copy(unreadCount = cur.unreadCount + 1, lastNotificationText = text))
        if (hasPostNotificationsPermission() && cur.daemonAlerts) {
            runCatching {
                com.evorez.ezdmanager.service.EzdNotificationHelper.postAlertNotification(ctx, title, text)
            }
        }
    }

    fun toggleDaemonService(enabled: Boolean) {
        viewModelScope.launch {
            store.setDaemonEnabled(enabled)
            if (enabled) DaemonBridge.startService(ctx) else DaemonBridge.stopService(ctx)
        }
    }

    fun openTool(tool: ToolId?) {
        _ui.update { it.copy(activeTool = tool, showSettingsScreen = false) }
        when (tool) {
            ToolId.AUDIO_ENHANCER -> {
                val s = _ui.value.settings
                viewModelScope.launch(Dispatchers.Default) {
                    val audio = sysToolsRepo.initAudioEngine(
                        savedPreset = s.savedAudioPreset,
                        bassEnabled = s.audioBassEnabled,
                        eqEnabled = s.audioEqEnabled,
                        visualStyle = s.audioVisualStyle,
                        bassFocus = s.audioBassFocus
                    )
                    _ui.update { it.copy(audioState = audio) }
                }
            }
            ToolId.APP_BACKUP -> refreshLocalAppBackups()
            ToolId.ZRAM_VM -> refreshZramVm()
            ToolId.KERNEL_TWEAKS -> refreshIoThermalUi()
            ToolId.MODULES -> refreshRootModules()
            ToolId.BUILD_PROP -> refreshBuildProps()
            ToolId.BOOT_GUARD -> BootGuardManager.refresh(ctx)
            else -> Unit
        }
    }

    // --- ZRAM & VM Tweaks ---
    fun refreshZramVm() {
        viewModelScope.launch(Dispatchers.Default) {
            val st = kernelTweaksRepo.scanZramVm(_ui.value.rootStatus == RootStatus.GRANTED)
            _ui.update { it.copy(zramVmState = st) }
        }
    }

    fun applyZramConfig(sizeMb: Long, algorithm: String) = runAction {
        val (_, msg) = kernelTweaksRepo.applyZramConfig(sizeMb, algorithm)
        val st = kernelTweaksRepo.scanZramVm(_ui.value.rootStatus == RootStatus.GRANTED)
        _ui.update { it.copy(zramVmState = st, statusBanner = msg) }
    }

    fun applyVmTweaks(swappiness: Int, vfsPressure: Int, dirtyRatio: Int, dirtyBgRatio: Int) = runAction {
        val (_, msg) = kernelTweaksRepo.applyVmTweaks(swappiness, vfsPressure, dirtyRatio, dirtyBgRatio)
        val st = kernelTweaksRepo.scanZramVm(_ui.value.rootStatus == RootStatus.GRANTED)
        _ui.update { it.copy(zramVmState = st, statusBanner = msg) }
    }

    // --- I/O Scheduler, Read-Ahead, Thermal Profile, Animation Scale, GPU Renderer ---
    fun refreshIoThermalUi() {
        viewModelScope.launch(Dispatchers.Default) {
            val st = kernelTweaksRepo.scanIoThermalUi(_ui.value.rootStatus == RootStatus.GRANTED)
            _ui.update { it.copy(ioThermalUiState = st) }
        }
    }

    fun setIoScheduler(device: IoBlockDevice, scheduler: String) = runAction {
        val (_, msg) = kernelTweaksRepo.setIoScheduler(device, scheduler)
        val st = kernelTweaksRepo.scanIoThermalUi(_ui.value.rootStatus == RootStatus.GRANTED)
        _ui.update { it.copy(ioThermalUiState = st, statusBanner = msg) }
    }

    fun setIoReadAhead(device: IoBlockDevice, kb: Int) = runAction {
        val (_, msg) = kernelTweaksRepo.setIoReadAhead(device, kb)
        val st = kernelTweaksRepo.scanIoThermalUi(_ui.value.rootStatus == RootStatus.GRANTED)
        _ui.update { it.copy(ioThermalUiState = st, statusBanner = msg) }
    }

    fun applyThermalProfile(mode: ThermalProfileMode) = runAction {
        val (_, msg) = kernelTweaksRepo.applyThermalProfile(mode)
        val st = kernelTweaksRepo.scanIoThermalUi(_ui.value.rootStatus == RootStatus.GRANTED)
        _ui.update { it.copy(ioThermalUiState = st, statusBanner = msg) }
    }

    fun setSystemAnimationScale(scale: Float) = runAction {
        val (_, msg) = kernelTweaksRepo.setAnimationScale(scale, isRootActive(), isShizukuActive())
        val st = kernelTweaksRepo.scanIoThermalUi(isRootActive())
        _ui.update { it.copy(ioThermalUiState = st, statusBanner = msg) }
    }

    fun setGpuRenderer(mode: GpuRendererMode) = runAction {
        val (_, msg) = kernelTweaksRepo.setGpuRenderer(mode)
        val st = kernelTweaksRepo.scanIoThermalUi(isRootActive())
        _ui.update { it.copy(ioThermalUiState = st, statusBanner = msg) }
    }

    // --- Magisk / KernelSU Module Manager ---
    fun refreshRootModules() {
        viewModelScope.launch(Dispatchers.Default) {
            val (mgr, mods) = modulesPropRepo.scanModules()
            _ui.update { it.copy(rootManagerType = mgr, rootModules = mods) }
        }
    }

    fun toggleRootModule(module: RootModuleInfo, enable: Boolean) = runAction {
        val (_, msg) = modulesPropRepo.toggleModuleEnabled(module, enable)
        val (mgr, mods) = modulesPropRepo.scanModules()
        _ui.update { it.copy(rootManagerType = mgr, rootModules = mods, statusBanner = msg) }
    }

    fun toggleRootModuleRemove(module: RootModuleInfo, remove: Boolean) = runAction {
        val (_, msg) = modulesPropRepo.toggleModuleRemove(module, remove)
        val (mgr, mods) = modulesPropRepo.scanModules()
        _ui.update { it.copy(rootManagerType = mgr, rootModules = mods, statusBanner = msg) }
    }

    // --- Build.prop Editor & Auto-Backups ---
    fun refreshBuildProps() {
        viewModelScope.launch(Dispatchers.Default) {
            val entries = modulesPropRepo.loadBuildProps(isRootActive())
            val backups = modulesPropRepo.listPropBackups()
            _ui.update { it.copy(buildProps = entries, buildPropBackups = backups) }
        }
    }

    fun createBuildPropBackup() = runAction {
        val created = modulesPropRepo.createPropBackup(_ui.value.buildProps, "manual")
        val backups = modulesPropRepo.listPropBackups()
        _ui.update {
            it.copy(
                buildPropBackups = backups,
                statusBanner = if (created != null) "Saved build.prop backup (${created.entryCount} props)" else "Nothing to backup"
            )
        }
    }

    fun updateBuildProp(key: String, value: String) = runAction {
        val (_, msg) = modulesPropRepo.updateBuildProp(key, value, _ui.value.buildProps)
        val entries = modulesPropRepo.loadBuildProps(isRootActive())
        val backups = modulesPropRepo.listPropBackups()
        _ui.update { it.copy(buildProps = entries, buildPropBackups = backups, statusBanner = msg) }
    }

    fun restoreBuildPropBackup(backup: BuildPropBackupInfo) = runAction {
        val (_, msg) = modulesPropRepo.restorePropBackup(backup)
        val entries = modulesPropRepo.loadBuildProps(isRootActive())
        _ui.update { it.copy(buildProps = entries, statusBanner = msg) }
    }

    // --- Boot-Loop Safe Mode Controls (3 failed boots + module disable) ---
    fun clearBootSafeMode() {
        BootGuardManager.clearSafeMode(ctx)
        _ui.update { it.copy(statusBanner = "Safe Mode cleared. Re-enable modules or Boot Memory when ready.") }
    }

    fun testSimulateBootLoopGuard() {
        viewModelScope.launch(Dispatchers.Default) {
            var triggered = false
            repeat(BootGuardManager.MAX_FAILED_BOOTS) {
                triggered = BootGuardManager.onBootStarting(ctx)
            }
            var modMsg = ""
            if (triggered) {
                val curBoot = _ui.value.settings.bootMemory
                store.saveBootMemory(curBoot.copy(applyOnBoot = false))
                if (isRootActive()) {
                    modMsg = " · " + modulesPropRepo.disableAllModules().second
                    val (mgr, mods) = modulesPropRepo.scanModules()
                    _ui.update { it.copy(rootManagerType = mgr, rootModules = mods) }
                }
            }
            _ui.update {
                it.copy(
                    statusBanner = "Simulated ${BootGuardManager.MAX_FAILED_BOOTS} failed boots — Safe Mode ON & Boot Memory disabled$modMsg"
                )
            }
        }
    }

    // --- Changelog & GitHub Release Checker ---
    fun builtInChangelog(): List<Pair<String, List<String>>> = modulesPropRepo.builtInChangelog()

    fun checkForGitHubUpdates() {
        viewModelScope.launch {
            _ui.update { it.copy(checkingUpdate = true) }
            val currentVer = runCatching {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
            }.getOrDefault("1.0.0")
            val info = withContext(Dispatchers.IO) {
                modulesPropRepo.fetchLatestGitHubRelease(currentVer)
            }
            _ui.update {
                it.copy(
                    releaseInfo = info,
                    checkingUpdate = false,
                    statusBanner = info.statusMessage
                )
            }
        }
    }

    // --- Boot Memory on Start ---
    fun setApplyOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            val cur = _ui.value.settings.bootMemory
            val next = if (enabled && cur.lastCapturedAt == 0L) {
                buildCurrentBootSnapshot(applyOnBoot = true)
            } else {
                cur.copy(applyOnBoot = enabled)
            }
            store.saveBootMemory(next)
            _ui.update {
                it.copy(statusBanner = if (enabled) "Boot Memory enabled — saved tuning restores at startup" else "Boot Memory disabled")
            }
        }
    }

    fun captureBootMemorySnapshot() {
        viewModelScope.launch {
            val snap = buildCurrentBootSnapshot(applyOnBoot = _ui.value.settings.bootMemory.applyOnBoot)
            store.saveBootMemory(snap)
            _ui.update { it.copy(statusBanner = "Captured Boot Memory: ${snap.summary}") }
        }
    }

    fun applyBootMemoryNow() {
        runAction {
            val bootCfg = _ui.value.settings.bootMemory
            val rootGranted = isRootActive()
            val shizukuGranted = isShizukuActive()
            val applied = mutableListOf<String>()
            bootCfg.savedProfileId?.let { pid ->
                allProfiles().find { it.id == pid }?.let { prof ->
                    if (rootGranted || shizukuGranted) {
                        profileRepo.applyProfile(prof, rootGranted, shizukuGranted)
                        applied.add(prof.name)
                    }
                }
            }
            bootCfg.savedRefreshHz?.let { hz ->
                displayTouchRepo.setRefreshRate(hz, rootGranted, shizukuGranted)
                applied.add("${hz.toInt()}Hz")
            }
            if (bootCfg.savedDisableHwOverlays && (rootGranted || shizukuGranted)) {
                displayTouchRepo.setDisableHwOverlays(true, rootGranted, shizukuGranted)
                applied.add("GPU Overlays")
            }
            val dnsHost = bootCfg.savedDnsHost
            if (dnsHost != null && (rootGranted || shizukuGranted)) {
                val preset = SystemToolsRepository.DNS_PRESETS.find { it.hostname == dnsHost }
                    ?: DnsPreset("boot_dns", bootCfg.savedDnsName ?: dnsHost, dnsHost, "DoT", "", "DNS")
                sysToolsRepo.applyDnsPreset(preset, rootGranted, shizukuGranted)
                applied.add(preset.name)
            }
            refreshAllHardware()
            val msg = if (applied.isEmpty()) "Boot Memory ready (grant Root or Shizuku)" else "Boot Memory applied: ${applied.joinToString(" · ")}"
            _ui.update { it.copy(statusBanner = msg) }
        }
    }

    private fun buildCurrentBootSnapshot(applyOnBoot: Boolean): BootMemoryConfig {
        val uiState = _ui.value
        val activeProfId = uiState.settings.activeProfileId
        val profName = allProfiles().find { it.id == activeProfId }?.name ?: activeProfId
        val hz = uiState.displayState.currentRefreshHz
        val hwo = uiState.touchState.hwOverlaysDisabled
        val dnsHost = uiState.networkState.privateDnsSpecifier.ifBlank { null }
        val dnsName = SystemToolsRepository.DNS_PRESETS.find { it.hostname == dnsHost }?.name ?: dnsHost
        val parts = buildList {
            add("Profile: $profName")
            add("${hz.toInt()}Hz")
            if (hwo) add("GPU Compositing")
            if (dnsName != null) add("DNS: $dnsName")
        }
        return BootMemoryConfig(
            applyOnBoot = applyOnBoot,
            savedProfileId = activeProfId,
            savedRefreshHz = hz,
            savedDisableHwOverlays = hwo,
            savedDnsHost = dnsHost,
            savedDnsName = dnsName,
            savedAudioPreset = uiState.settings.savedAudioPreset,
            lastCapturedAt = System.currentTimeMillis(),
            summary = parts.joinToString(" · ")
        )
    }

    // --- Brand Logo 5-Tap Developer Easter Egg ---
    fun recordBrandLogoTap(): Int {
        val now = System.currentTimeMillis()
        if (now - lastLogoTapMs > 3_000L) {
            logoTapCount = 0
        }
        lastLogoTapMs = now
        logoTapCount++
        if (logoTapCount >= 5) {
            logoTapCount = 0
            _ui.update { it.copy(showDeveloperEggDialog = true) }
            return 5
        }
        return logoTapCount
    }

    fun dismissDeveloperEgg() {
        _ui.update { it.copy(showDeveloperEggDialog = false) }
    }

    fun setShowSettingsScreen(show: Boolean) {
        _ui.update { it.copy(showSettingsScreen = show, activeTool = null) }
    }

    fun setShowSearchSheet(show: Boolean) = _ui.update { it.copy(showSearchSheet = show) }
    fun setShowPowerSheet(show: Boolean) = _ui.update { it.copy(showPowerSheet = show) }
    fun setShowNotificationsSheet(show: Boolean) {
        _ui.update { it.copy(showNotificationsSheet = show) }
        if (show) markNotificationsRead()
    }
    fun setShowDeviceDetails(show: Boolean) = _ui.update { it.copy(showDeviceDetails = show) }
    fun clearBanner() = _ui.update { it.copy(statusBanner = "") }

    fun openUsageStatsSettings() {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openDndPolicySettings() {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun openWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun buildDeviceReportText(): String {
        val s = _ui.value
        val ov = s.overview
        return buildString {
            appendLine("EZD Manager — Device Information")
            appendLine("Device: ${ov.manufacturer} ${ov.model} (${ov.device})")
            appendLine("SoC / Board: ${ov.board} / ${ov.hardware}")
            appendLine("Android: ${ov.androidVersion} (API ${ov.sdkInt})")
            appendLine("Kernel: ${ov.kernelVersion}")
            appendLine("RAM: ${ov.ramUsedMb} / ${ov.ramTotalMb} MB")
            appendLine("Battery: ${if (ov.batteryPercent >= 0) "${ov.batteryPercent}%" else "Unavailable"} (${ov.chargingStatus})")
            appendLine("Root: ${s.rootStatus.name}")
            appendLine("CPU Clusters: ${s.cpuPolicies.size}")
            s.cpuPolicies.forEach { p ->
                appendLine("  ${p.id} (CPUs ${p.cpus}): ${p.minKhz?.div(1000) ?: "?"}–${p.maxKhz?.div(1000) ?: "?"} MHz | Gov: ${p.currentGovernor}")
            }
            appendLine("GPU: ${if (s.gpuState.supported) "${s.gpuState.vendorHint} (${s.gpuState.currentMhz ?: "?"} MHz, ${s.gpuState.currentGovernor})" else "Not supported"}")
            appendLine("Display: ${s.displayState.currentRefreshHz} Hz (${s.displayState.supportedModes.size} modes)")
        }
    }

    fun buildSearchIndex(): List<SearchEntry> {
        val items = mutableListOf<SearchEntry>()
        items.add(SearchEntry("home_tab", "Home Dashboard", "Device overview, root & quick shortcuts", "Home", MainTab.HOME))
        items.add(SearchEntry("perf_tab", "Performance Hub", "CPU & GPU clocks, governors, hotplug & profiles", "Performance", MainTab.PERF, targetTool = ToolId.CPU_GPU_CONTROL))
        items.add(SearchEntry("cpu_gpu_tool", "CPU & GPU Engine", "Clusters, hotplug, clocks & governors", "Performance", MainTab.PERF, targetTool = ToolId.CPU_GPU_CONTROL))

        ToolId.entries.forEach { tool ->
            items.add(SearchEntry("tool_${tool.name}", tool.title, tool.subtitle, "Tools", MainTab.TOOLS, targetTool = tool))
        }

        _ui.value.installedApps.forEach { app ->
            items.add(
                SearchEntry(
                    id = "app_${app.packageName}",
                    title = app.label,
                    subtitle = app.packageName,
                    category = "Apps",
                    targetTab = MainTab.APPS,
                    packageName = app.packageName
                )
            )
        }
        return items
    }

    private fun hasUsageStatsPermission(): Boolean {
        return runCatching {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
            val now = System.currentTimeMillis()
            val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
            !list.isNullOrEmpty()
        }.getOrDefault(false)
    }

    private fun hasDndPermission(): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                withContext(Dispatchers.Default) { block() }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        stopTelemetry()
        sysToolsRepo.releaseAudio()
        super.onCleared()
    }
}
