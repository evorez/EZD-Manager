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
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.service.DaemonBridge
import com.evorez.ezdmanager.service.PerAppDnsVpnBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class EzdUiState(
    val settings: UserSettings = UserSettings(),
    val rootStatus: RootStatus = RootStatus.CHECKING,
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
    val fpsReading: FpsReading = FpsReading(),
    val fpsHistory: List<Int> = emptyList(),
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
    val backupPreview: BackupPreview? = null,
    val pendingRestorePayload: JSONObject? = null,
    val backupReport: BackupReport? = null,
    val pendingVpnConsentNeeded: Boolean = false
)

class EzdViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    private val store = SettingsDataStore(ctx)
    private val cpuGpuRepo = CpuGpuRepository(ctx)
    private val displayTouchRepo = DisplayTouchRepository(ctx)
    private val sysToolsRepo = SystemToolsRepository(ctx)
    private val profileRepo = ProfileRepository(ctx, cpuGpuRepo, displayTouchRepo, sysToolsRepo)
    private val backupManager = BackupRestoreManager(ctx, store)

    private val _ui = MutableStateFlow(
        EzdUiState(overview = cpuGpuRepo.readDeviceOverview())
    )
    val ui: StateFlow<EzdUiState> = _ui.asStateFlow()

    val daemonStats: StateFlow<DaemonStats> = DaemonBridge.stats
    val vpnActive: StateFlow<Boolean> = PerAppDnsVpnBridge.vpnActive
    val vpnPackages: StateFlow<List<String>> = PerAppDnsVpnBridge.routedPackages

    private var pollingJob: Job? = null

    init {
        observeDataStore()
        observeOverlayEvents()
        initializeDaemonAndRoot()
    }

    private fun observeDataStore() {
        viewModelScope.launch {
            store.settingsFlow.collect { s ->
                _ui.update { it.copy(settings = s) }
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
    }

    private fun observeOverlayEvents() {
        viewModelScope.launch {
            DaemonBridge.overlayTrigger.collect { action ->
                when (action) {
                    "PILL_Game Space" -> openTool(ToolId.GAME_SPACE)
                    "PILL_Power" -> setShowPowerSheet(true)
                    "PILL_Network" -> openTool(ToolId.NETWORK)
                    "PILL_CPU" -> openTool(ToolId.PROFILES)
                }
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

    fun startTelemetry() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val overview = cpuGpuRepo.readDeviceOverview()
                val rootGranted = _ui.value.rootStatus == RootStatus.GRANTED
                val policies = if (rootGranted) cpuGpuRepo.scanCpuPolicies() else emptyList()
                val cores = if (rootGranted) cpuGpuRepo.scanCpuCores() else emptyList()
                val gpu = if (rootGranted) cpuGpuRepo.scanGpu() else _ui.value.gpuState
                val display = displayTouchRepo.readDisplayState(rootGranted)
                val fps = if (_ui.value.activeTool == ToolId.GAME_SPACE || _ui.value.settings.gameSpace.enabled) {
                    sysToolsRepo.readGenuineFps()
                } else _ui.value.fpsReading

                _ui.update { current ->
                    val nextHistory = fps.fps?.let { f -> (current.fpsHistory + f).takeLast(28) } ?: current.fpsHistory
                    current.copy(
                        overview = overview,
                        cpuPolicies = policies,
                        cpuCores = cores,
                        gpuState = if (rootGranted) gpu else GpuState(
                            supported = false,
                            reason = "Grant root access to inspect and control GPU frequencies."
                        ),
                        displayState = display,
                        fpsReading = fps,
                        fpsHistory = nextHistory,
                        overlayGranted = Settings.canDrawOverlays(ctx),
                        usageAccessGranted = hasUsageStatsPermission(),
                        dndPolicyGranted = hasDndPermission(),
                        writeSettingsGranted = Settings.System.canWrite(ctx),
                        notificationsGranted = hasPostNotificationsPermission()
                    )
                }
                delay(2_200L)
            }
        }
    }

    fun stopTelemetry() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun autoDetectRoot(forceFresh: Boolean = true) {
        viewModelScope.launch {
            _ui.update { it.copy(rootStatus = RootStatus.CHECKING) }
            val status = RootShellManager.probeRoot(forceFreshRequest = forceFresh)
            DaemonBridge.update { it.copy(rootGranted = status == RootStatus.GRANTED) }
            val edlOk = if (status == RootStatus.GRANTED) sysToolsRepo.isEdlVerifiedSupported() else false
            _ui.update {
                it.copy(
                    rootStatus = status,
                    edlSupported = edlOk,
                    statusBanner = when (status) {
                        RootStatus.DENIED -> "Root denied. Approve su in Magisk / KernelSU / APatch."
                        RootStatus.UNAVAILABLE -> "No su binary detected. Running in safe read-only inspection mode."
                        else -> ""
                    }
                )
            }
            refreshAllHardware()
        }
    }

    fun refreshAllHardware() {
        viewModelScope.launch {
            val rootGranted = _ui.value.rootStatus == RootStatus.GRANTED
            val overview = cpuGpuRepo.readDeviceOverview()
            val policies = if (rootGranted) cpuGpuRepo.scanCpuPolicies() else emptyList()
            val cores = if (rootGranted) cpuGpuRepo.scanCpuCores() else emptyList()
            val gpu = cpuGpuRepo.scanGpu()
            val display = displayTouchRepo.readDisplayState(rootGranted)
            val touch = displayTouchRepo.scanTouch()
            val net = sysToolsRepo.scanNetwork(vpnActive = vpnActive.value)
            val spoof = sysToolsRepo.scanSpoofState()
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
            val (_, msg) = profileRepo.applyProfile(profile)
            pushNotification(msg)
            refreshAllHardware()
            _ui.update { it.copy(statusBanner = msg) }
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

    fun updateGameSpace(config: GameSpaceConfig) {
        viewModelScope.launch {
            store.saveGameSpace(config)
            if (config.enabled) {
                val prof = ProfileRepository.forGameProfile(config.activeProfile, _ui.value.customProfiles)
                store.setActiveProfileId(prof.id)
            }
        }
    }

    fun toggleGameSelection(packageName: String) {
        val current = _ui.value.settings.gameSpace
        val nextSet = if (packageName in current.selectedGames) {
            current.selectedGames - packageName
        } else {
            current.selectedGames + packageName
        }
        updateGameSpace(current.copy(selectedGames = nextSet))
    }

    fun applyGameSpaceNow() {
        val gs = _ui.value.settings.gameSpace
        runAction {
            val prof = ProfileRepository.forGameProfile(gs.activeProfile, _ui.value.customProfiles)
            val (_, profMsg) = profileRepo.applyProfile(prof)
            if (gs.hbmBoostEnabled) {
                _ui.value.displayState.hbmPath?.let { displayTouchRepo.setHbm(it, true) }
            }
            if (gs.disableHwOverlays) {
                displayTouchRepo.setDisableHwOverlays(true)
            }
            if (gs.dndEnabled && hasDndPermission()) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                runCatching { nm?.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
            }
            pushNotification("Game Space applied: ${gs.activeProfile.label}")
            refreshAllHardware()
            _ui.update { it.copy(statusBanner = profMsg) }
        }
    }

    // --- Display & Touch ---
    fun setRefreshRate(hz: Float) = runAction {
        val rootGranted = _ui.value.rootStatus == RootStatus.GRANTED
        val (_, msg) = displayTouchRepo.setRefreshRate(hz, rootGranted)
        _ui.update { it.copy(displayState = displayTouchRepo.readDisplayState(rootGranted), statusBanner = msg) }
    }

    fun resetRefreshRate() = runAction {
        val rootGranted = _ui.value.rootStatus == RootStatus.GRANTED
        val (_, msg) = displayTouchRepo.resetRefreshRate(rootGranted)
        _ui.update { it.copy(displayState = displayTouchRepo.readDisplayState(rootGranted), statusBanner = msg) }
    }

    fun setBrightness(value: Int, auto: Boolean) = runAction {
        val rootGranted = _ui.value.rootStatus == RootStatus.GRANTED
        val (_, msg) = displayTouchRepo.setBrightness(value, auto, rootGranted)
        _ui.update { it.copy(displayState = displayTouchRepo.readDisplayState(rootGranted), statusBanner = msg) }
    }

    fun setHbm(enabled: Boolean) {
        val path = _ui.value.displayState.hbmPath ?: return
        runAction {
            val (_, msg) = displayTouchRepo.setHbm(path, enabled)
            _ui.update {
                it.copy(
                    displayState = displayTouchRepo.readDisplayState(_ui.value.rootStatus == RootStatus.GRANTED),
                    statusBanner = msg
                )
            }
        }
    }

    fun setDisplaySaturation(saturation: Float) = runAction {
        val (_, msg) = displayTouchRepo.setSaturation(saturation, _ui.value.displayState.kcalSupported)
        _ui.update { it.copy(displayState = it.displayState.copy(saturationValue = saturation), statusBanner = msg) }
    }

    fun setCabc(enabled: Boolean) {
        val path = _ui.value.displayState.cabcPath ?: return
        runAction {
            val (_, msg) = displayTouchRepo.setCabc(path, enabled)
            _ui.update {
                it.copy(
                    displayState = displayTouchRepo.readDisplayState(_ui.value.rootStatus == RootStatus.GRANTED),
                    statusBanner = msg
                )
            }
        }
    }

    fun setTouchControl(path: String, value: String, label: String) = runAction {
        val (_, msg) = displayTouchRepo.setTouchNode(path, value, label)
        _ui.update { it.copy(touchState = displayTouchRepo.scanTouch(), statusBanner = msg) }
    }

    fun setDisableHwOverlays(disable: Boolean) = runAction {
        val (_, msg) = displayTouchRepo.setDisableHwOverlays(disable)
        _ui.update { it.copy(touchState = displayTouchRepo.scanTouch(), statusBanner = msg) }
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
        val (_, msg) = sysToolsRepo.applyDnsPreset(preset)
        _ui.update { it.copy(networkState = sysToolsRepo.scanNetwork(vpnActive.value), statusBanner = msg) }
    }

    fun resetNetworkSettings() = runAction {
        val (_, msg) = sysToolsRepo.resetNetworkSettings(_ui.value.networkState.availableTcp)
        _ui.update { it.copy(networkState = sysToolsRepo.scanNetwork(vpnActive.value), statusBanner = msg) }
    }

    // --- Audio Enhancer (Phase 8: Lazy init only on AUDIO_ENHANCER open, real Hz labels) ---
    fun selectAudioPreset(name: String) {
        viewModelScope.launch {
            store.setAudioPreset(name)
            val updated = sysToolsRepo.applyAudioPreset(name, _ui.value.audioState)
            _ui.update { it.copy(audioState = updated, statusBanner = "Audio preset: $name") }
        }
    }

    fun updateAudioManual(levels: List<Int>, bass: Int, loud: Int) {
        sysToolsRepo.applyAudioParams(levels, bass, loud)
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

    // --- Battery Saving + Whitelist (Phase 10) ---
    fun applyGmsBackgroundManagement(restrictNonEssential: Boolean) = runAction {
        val (ok, msg) = sysToolsRepo.applyGmsBackgroundManagement(restrictNonEssential)
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
        val (ok, msg) = sysToolsRepo.applyBackgroundAppLimit(preset)
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
        val (_, msg) = sysToolsRepo.runSafeMemoryTrim(_ui.value.installedApps, _ui.value.batteryWhitelist)
        _ui.update {
            it.copy(
                batterySavingState = it.batterySavingState.copy(lastActionSummary = msg),
                statusBanner = msg
            )
        }
    }

    fun setAppBackgroundRestriction(packageName: String, restrict: Boolean) = runAction {
        val (ok, msg) = sysToolsRepo.setAppBackgroundRestriction(packageName, restrict, _ui.value.batteryWhitelist)
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

            // Keep Game Space selectedGames synced if user toggled gameModeAssigned
            val gs = _ui.value.settings.gameSpace
            val nextGames = if (config.gameModeAssigned) gs.selectedGames + config.packageName else gs.selectedGames - config.packageName
            if (nextGames != gs.selectedGames) store.saveGameSpace(gs.copy(selectedGames = nextGames))

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
        val (_, msg) = sysToolsRepo.clearAppCache(pkg)
        _ui.update { it.copy(statusBanner = msg) }
    }

    fun forceStopApp(pkg: String) = runAction {
        val (_, msg) = sysToolsRepo.forceStopApp(pkg)
        _ui.update { it.copy(statusBanner = msg) }
    }

    fun toggleFreezeApp(app: AppEntry) = runAction {
        val (_, msg) = sysToolsRepo.toggleFreezeApp(app.packageName, !app.isDisabled)
        loadInstalledApps()
        _ui.update { it.copy(statusBanner = msg) }
    }

    fun uninstallApp(app: AppEntry) = runAction {
        val (_, msg) = sysToolsRepo.uninstallApp(app)
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

    // --- Root Terminal ---
    fun runTerminalCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isBlank()) return
        if (cmd == "clear") {
            _ui.update { it.copy(terminalHistory = emptyList()) }
            return
        }
        viewModelScope.launch {
            val res = if (_ui.value.rootStatus == RootStatus.GRANTED) {
                RootShellManager.exec(cmd, timeoutMs = 10_000L)
            } else {
                RootShellManager.execUnprivileged(cmd, timeoutMs = 5_000L)
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

    // --- Settings, Notifications, Pill, Navigation ---
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }
    fun setFloatingPill(config: FloatingPillConfig) = viewModelScope.launch { store.setFloatingPill(config) }
    fun setNotificationPrefs(prefs: NotificationPrefs) = viewModelScope.launch { store.setNotificationPrefs(prefs) }

    fun markNotificationsRead() {
        viewModelScope.launch {
            val cur = _ui.value.settings.notifications
            if (cur.unreadCount > 0) {
                store.setNotificationPrefs(cur.copy(unreadCount = 0))
            }
        }
    }

    private suspend fun pushNotification(text: String) {
        val cur = _ui.value.settings.notifications
        store.setNotificationPrefs(cur.copy(unreadCount = cur.unreadCount + 1, lastNotificationText = text))
    }

    fun toggleDaemonService(enabled: Boolean) {
        viewModelScope.launch {
            store.setDaemonEnabled(enabled)
            if (enabled) DaemonBridge.startService(ctx) else DaemonBridge.stopService(ctx)
        }
    }

    fun openTool(tool: ToolId?) {
        _ui.update { it.copy(activeTool = tool, showSettingsScreen = false) }
        if (tool == ToolId.AUDIO_ENHANCER && !_ui.value.audioState.engineReady) {
            viewModelScope.launch(Dispatchers.Default) {
                val audio = sysToolsRepo.initAudioEngine(_ui.value.settings.savedAudioPreset)
                _ui.update { it.copy(audioState = audio) }
            }
        }
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
        items.add(SearchEntry("cpu_tab", "CPU Clusters & Hotplug", "Frequencies, governors & cores", "CPU", MainTab.CPU))
        items.add(SearchEntry("gpu_tab", "GPU Frequency & Governor", "Adreno / Mali devfreq clocks", "GPU", MainTab.GPU))

        ToolId.entries.forEach { tool ->
            items.add(SearchEntry("tool_${tool.name}", tool.title, tool.subtitle, "Tools", MainTab.TOOLS, targetTool = tool))
        }

        _ui.value.installedApps.take(60).forEach { app ->
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
