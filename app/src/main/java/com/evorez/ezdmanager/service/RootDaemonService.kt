package com.evorez.ezdmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.evorez.ezdmanager.MainActivity
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.hardware.CpuGpuRepository
import com.evorez.ezdmanager.hardware.DisplayTouchRepository
import com.evorez.ezdmanager.hardware.EngineTuningManager
import com.evorez.ezdmanager.hardware.HardwareProbe
import com.evorez.ezdmanager.hardware.RootModulesAndPropRepository
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.root.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object DaemonBridge {
    private val _stats = MutableStateFlow(DaemonStats())
    val stats: StateFlow<DaemonStats> = _stats.asStateFlow()

    private val _thermal = MutableStateFlow(ThermalTelemetry())
    val thermal: StateFlow<ThermalTelemetry> = _thermal.asStateFlow()

    private val _syncRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val syncRequest: SharedFlow<Unit> = _syncRequest.asSharedFlow()

    fun requestSync() {
        _syncRequest.tryEmit(Unit)
    }

    fun update(transform: (DaemonStats) -> DaemonStats) {
        _stats.update(transform)
    }

    fun updateThermal(telemetry: ThermalTelemetry) {
        _thermal.value = telemetry
    }

    fun appendLog(msg: String) {
        _stats.update { current ->
            current.copy(logs = (current.logs + msg).takeLast(35))
        }
    }

    fun startService(context: Context) {
        val intent = Intent(context.applicationContext, RootDaemonService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        } catch (_: Exception) {
            runCatching { context.applicationContext.startService(intent) }
        }
    }

    fun stopService(context: Context) {
        runCatching {
            context.applicationContext.stopService(Intent(context.applicationContext, RootDaemonService::class.java))
        }
    }
}

class RootDaemonService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var notificationTickerJob: Job? = null
    private var fgEventWatcherJob: Job? = null
    @Volatile private var fgLogcatProcess: Process? = null
    @Volatile private var currentProfileId: String = GameProfileId.BALANCED.id

    private lateinit var store: SettingsDataStore
    private lateinit var cpuGpuRepo: CpuGpuRepository
    private lateinit var displayTouchRepo: DisplayTouchRepository
    private lateinit var sysToolsRepo: SystemToolsRepository
    private lateinit var modulesRepo: RootModulesAndPropRepository
    private lateinit var profileRepo: ProfileRepository

    private var activeConfiguredPkg: String? = null
    private var activeSpoofApplied: Boolean = false
    private var savedPreviousRefreshHz: Float? = null

    @Volatile private var eventForegroundPkg: String = ""
    @Volatile private var lastEventFgMs: Long = 0L

    private var systemEventReceiver: BroadcastReceiver? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onCreate() {
        super.onCreate()
        store = SettingsDataStore(applicationContext)
        cpuGpuRepo = CpuGpuRepository(applicationContext)
        displayTouchRepo = DisplayTouchRepository(applicationContext)
        sysToolsRepo = SystemToolsRepository(applicationContext)
        modulesRepo = RootModulesAndPropRepository(applicationContext)
        profileRepo = ProfileRepository(applicationContext, cpuGpuRepo, displayTouchRepo, sysToolsRepo)

        ShizukuManager.init(applicationContext)
        BatteryMonitorTracker.ensureRegistered(applicationContext)
        registerEventDrivenTriggers()
        ensureChannel()

        val initialStats = BatteryMonitorTracker.sampleNow(applicationContext)
        val notification = buildNotification(
            title = formatNotificationTitle(currentProfileId),
            text = BatteryMonitorTracker.notificationLine(initialStats)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        DaemonBridge.update { it.copy(serviceRunning = true) }
        DaemonBridge.appendLog("EZD Event-Driven Monitor & Watchdog initialized")
        startDaemonLoop()
        startNotificationTicker()
    }

    private fun registerEventDrivenTriggers() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                DaemonBridge.requestSync()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        systemEventReceiver = receiver

        val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        if (dm != null) {
            val dl = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = DaemonBridge.requestSync()
                override fun onDisplayRemoved(displayId: Int) = DaemonBridge.requestSync()
                override fun onDisplayChanged(displayId: Int) = DaemonBridge.requestSync()
            }
            dm.registerDisplayListener(dl, Handler(Looper.getMainLooper()))
            displayListener = dl
        }
    }

    private fun ensureForegroundEventWatcher(enable: Boolean) {
        if (!enable) {
            fgEventWatcherJob?.cancel()
            fgEventWatcherJob = null
            runCatching { fgLogcatProcess?.destroyForcibly() }
            fgLogcatProcess = null
            return
        }
        if (fgEventWatcherJob?.isActive == true) return
        fgEventWatcherJob = serviceScope.launch(Dispatchers.IO) {
            val regex = Regex("""(?:am_focused_activity|am_resumed_activity)\s*:\s*\[\d+,([^,/]+)""")
            while (isActive) {
                try {
                    val proc = ProcessBuilder(
                        "su", "-c",
                        "logcat -T 1 -b events -v brief -s am_focused_activity am_resumed_activity"
                    ).redirectErrorStream(true).start()
                    fgLogcatProcess = proc
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            val pkg = regex.find(line)?.groupValues?.getOrNull(1)?.trim()
                            if (!pkg.isNullOrBlank() && pkg.contains('.')) {
                                if (eventForegroundPkg != pkg) {
                                    eventForegroundPkg = pkg
                                    lastEventFgMs = System.currentTimeMillis()
                                    DaemonBridge.requestSync()
                                } else {
                                    lastEventFgMs = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                    runCatching { proc.destroyForcibly() }
                } catch (_: Exception) {
                    // Fallback to UsageEvents / dumpsys if event log stream is unavailable
                }
                if (isActive) delay(5_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_BEAST_BOOT_GUARD, false) == true ||
            intent?.action == ACTION_BEAST_BOOT_DISABLED_ALERT
        ) {
            EzdNotificationHelper.postAlertNotification(
                context = applicationContext,
                title = "Boot Guard · Beast Mode Disabled",
                message = "Device rebooted while Beast Mode was active. Stepped down to Balanced Mode for safety.",
                notificationId = NOTIF_ID + 2
            )
            DaemonBridge.appendLog("Boot Guard stepped down Beast Mode to Balanced after reboot")
        }

        when (intent?.action) {
            ACTION_APPLY_BOOT_MEMORY -> {
                serviceScope.launch {
                    delay(2_000L)
                    applyBootMemoryInternal()
                    delay(45_000L)
                    BootGuardManager.confirmBootHealthy(applicationContext)
                }
            }
            ACTION_BOOT_SAFE_MODE_ALERT -> {
                serviceScope.launch {
                    val rootOk = RootShellManager.probeRoot(forceFreshRequest = false) == RootStatus.GRANTED
                    if (rootOk) {
                        modulesRepo.disableAllModules()
                    }
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(
                    NOTIF_ID + 1,
                    buildNotification(
                        title = "EZD Boot-Loop Safe Mode Active",
                        text = "Tweaks & Magisk/KernelSU modules disabled after 3 failed boots."
                    )
                )
                DaemonBridge.appendLog("Boot Guard activated Safe Mode (tweaks & modules disabled)")
            }
            ACTION_SWITCH_PROFILE -> {
                val targetId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (!targetId.isNullOrBlank()) {
                    serviceScope.launch {
                        applyProfileFromBackground(targetId)
                    }
                }
            }
        }
        if (monitorJob?.isActive != true) startDaemonLoop()
        else DaemonBridge.requestSync()
        return START_STICKY
    }

    private suspend fun applyProfileFromBackground(profileId: String) {
        val all = store.customProfilesFlow.first() + ProfileRepository.builtInProfiles()
        val chosen = all.find { it.id == profileId } ?: return
        val prevId = store.settingsFlow.first().activeProfileId
        store.setActiveProfileId(chosen.id)
        val rootStatus = RootShellManager.probeRoot(forceFreshRequest = false)
        val rootGranted = rootStatus == RootStatus.GRANTED
        val shizukuGranted = ShizukuManager.refreshStatus(applicationContext) == ShizukuStatus.GRANTED
        DaemonBridge.update { it.copy(rootGranted = rootGranted) }
        if (rootGranted || shizukuGranted) {
            if (prevId == GameProfileId.BEAST.id && chosen.id == GameProfileId.BALANCED.id) {
                profileRepo.deactivateBeastMode(rootGranted, shizukuGranted)
            } else {
                profileRepo.applyProfile(chosen, rootAvailable = rootGranted, shizukuAvailable = shizukuGranted)
            }
        }
        val msg = "Switched profile to ${chosen.name}"
        DaemonBridge.update { it.copy(lastOutcome = msg) }
        DaemonBridge.appendLog(msg)
        currentProfileId = chosen.id
        updatePermanentNotification(profileId = chosen.id)
        EzdProfileWidgetProvider.updateAllWidgets(applicationContext, chosen.id)
    }

    private suspend fun applyBootMemoryInternal() {
        val guard = BootGuardManager.refresh(applicationContext)
        if (guard.safeModeActive) {
            DaemonBridge.appendLog("Skipped Boot Memory: Safe Mode is active")
            return
        }
        val bootCfg = runCatching { store.settingsFlow.first().bootMemory }.getOrNull() ?: return
        if (!bootCfg.applyOnBoot) return
        val rootStatus = RootShellManager.probeRoot(forceFreshRequest = false)
        val rootGranted = rootStatus == RootStatus.GRANTED
        val shizukuGranted = ShizukuManager.refreshStatus(applicationContext) == ShizukuStatus.GRANTED
        DaemonBridge.update { it.copy(rootGranted = rootGranted) }

        val applied = mutableListOf<String>()
        bootCfg.savedProfileId?.let { pid ->
            if (pid == GameProfileId.BEAST.id) return@let // Boot Guard blocks auto-activating Beast Mode
            val all = store.customProfilesFlow.first() + ProfileRepository.builtInProfiles()
            all.find { it.id == pid }?.let { prof ->
                if (rootGranted || shizukuGranted) {
                    profileRepo.applyProfile(prof, rootAvailable = rootGranted, shizukuAvailable = shizukuGranted)
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
            applied.add("GPU Compositing")
        }
        val dnsHost = bootCfg.savedDnsHost
        if (dnsHost != null && (rootGranted || shizukuGranted)) {
            val preset = SystemToolsRepository.DNS_PRESETS.find { it.hostname == dnsHost }
                ?: DnsPreset("boot_dns", bootCfg.savedDnsName ?: dnsHost, dnsHost, "DoT", "", "DNS")
            sysToolsRepo.applyDnsPreset(preset, rootGranted, shizukuGranted)
            applied.add(preset.name)
        }
        val note = if (applied.isEmpty()) "Boot Memory ready" else "Boot Memory applied: ${applied.joinToString(" · ")}"
        DaemonBridge.update { it.copy(lastOutcome = note) }
        DaemonBridge.appendLog(note)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ensureForegroundEventWatcher(false)
        notificationTickerJob?.cancel()
        monitorJob?.cancel()
        systemEventReceiver?.let { runCatching { unregisterReceiver(it) } }
        systemEventReceiver = null
        displayListener?.let { dl ->
            val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            runCatching { dm?.unregisterDisplayListener(dl) }
        }
        displayListener = null

        runBlocking {
            val root = DaemonBridge.stats.value.rootGranted
            val shizuku = ShizukuManager.refreshStatus(applicationContext) == ShizukuStatus.GRANTED
            if (activeConfiguredPkg != null) {
                restoreAfterExit(root, shizuku)
                activeConfiguredPkg = null
            } else {
                val activeId = runCatching { store.settingsFlow.first().activeProfileId }.getOrDefault("balanced")
                if (activeId != GameProfileId.BALANCED.id) {
                    EngineTuningManager.restoreBaselineOnExit(applicationContext, root, shizuku)
                    if (activeId == GameProfileId.BEAST.id) {
                        store.setActiveProfileId(GameProfileId.BALANCED.id)
                    }
                }
            }
        }
        serviceScope.cancel()
        DaemonBridge.update {
            it.copy(serviceRunning = false, perAppMonitorActive = false, activeGamePkg = "")
        }
        super.onDestroy()
    }

    private fun startDaemonLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val rootStatus = RootShellManager.probeRoot(forceFreshRequest = false)
            val rootGranted = rootStatus == RootStatus.GRANTED
            val initShizuku = ShizukuManager.refreshStatus(applicationContext) == ShizukuStatus.GRANTED
            DaemonBridge.update {
                it.copy(
                    rootGranted = rootGranted,
                    commandsExecuted = RootShellManager.executedCount
                )
            }

            // Crash / Reboot guard check on service startup
            val recoveryAlert = EngineTuningManager.checkAndRecoverFromCrashOrReboot(
                context = applicationContext,
                rootGranted = rootGranted,
                shizukuGranted = initShizuku
            )
            if (!recoveryAlert.isNullOrBlank()) {
                DaemonBridge.appendLog(recoveryAlert)
                EzdNotificationHelper.postAlertNotification(
                    context = applicationContext,
                    title = "EZD Safety Guard",
                    message = recoveryAlert,
                    notificationId = NOTIF_ID + 3
                )
            }

            while (isActive) {
                val guard = BootGuardManager.refresh(applicationContext)
                val settings = runCatching { store.settingsFlow.first() }.getOrNull()
                val shizukuStatus = ShizukuManager.refreshStatus(applicationContext)
                val privMode = ShizukuManager.resolveEffectiveMode(
                    settings?.workMode ?: WorkMode.AUTO,
                    if (rootGranted) RootStatus.GRANTED else RootStatus.DENIED,
                    shizukuStatus
                )
                val useRoot = privMode == PrivilegeMode.ROOT
                val useShizuku = privMode == PrivilegeMode.SHIZUKU

                val configs = runCatching { store.perAppConfigsFlow.first() }.getOrDefault(emptyMap())
                val enabledConfigs = if (guard.safeModeActive) emptyMap() else configs.filterValues { it.enabled }
                val watchApps = enabledConfigs.isNotEmpty()

                ensureForegroundEventWatcher(watchApps && useRoot)

                val fgPkg = if (watchApps) detectForegroundPackage(useRoot, useShizuku) else ""

                if (watchApps) {
                    val rule = enabledConfigs[fgPkg]
                    if (rule != null) {
                        if (activeConfiguredPkg != fgPkg) {
                            if (activeConfiguredPkg != null) {
                                restoreAfterExit(useRoot, useShizuku)
                            }
                            applyActiveRule(fgPkg, rule, useRoot, useShizuku)
                            activeConfiguredPkg = fgPkg
                        }
                    } else if (activeConfiguredPkg != null && fgPkg.isNotBlank()) {
                        // Exited target app (including returning to EZD Manager or launcher) -> restore baseline
                        restoreAfterExit(useRoot, useShizuku)
                        activeConfiguredPkg = null
                    }
                } else if (activeConfiguredPkg != null) {
                    restoreAfterExit(useRoot, useShizuku)
                    activeConfiguredPkg = null
                }

                // Event-driven kernel/PPM drift enforcement + Beast Thermal Safety Watchdog
                var activeProfileId = settings?.activeProfileId ?: GameProfileId.BALANCED.id
                if (activeConfiguredPkg == null && !guard.safeModeActive) {
                    if (activeProfileId == GameProfileId.BEAST.id || activeProfileId == GameProfileId.PERFORMANCE.id) {
                        val reapplied = EngineTuningManager.reapplyActiveEnforcement(
                            context = applicationContext,
                            activeProfileId = activeProfileId,
                            rootGranted = useRoot,
                            shizukuGranted = useShizuku
                        )
                        if (reapplied > 0) {
                            DaemonBridge.appendLog("Re-enforced $reapplied kernel lock(s) reset by PPM/PowerHAL")
                        }
                    }

                    if (activeProfileId == GameProfileId.BEAST.id && settings != null) {
                        val thermal = HardwareProbe.probeThermalNow(applicationContext)
                        DaemonBridge.updateThermal(thermal)

                        val batExceeded = thermal.batteryTempC >= settings.beastBatteryTempLimitC
                        val socExceeded = thermal.socTempC != null && thermal.socTempC >= settings.beastSocTempLimitC
                        if (!settings.beastIgnoreThermalLimits && (batExceeded || socExceeded)) {
                            val balanced = ProfileRepository.builtInProfiles().first { it.id == GameProfileId.BALANCED.id }
                            EngineTuningManager.applyStandardProfile(applicationContext, balanced, useRoot, useShizuku)
                            store.setActiveProfileId(GameProfileId.BALANCED.id)
                            activeProfileId = GameProfileId.BALANCED.id

                            val batStr = String.format(Locale.US, "%.1f°C", thermal.batteryTempC)
                            val socStr = thermal.socTempC?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A"
                            val alertMsg = "Thermal Watchdog stepped down Beast Mode to Balanced (Battery $batStr / SoC $socStr)."
                            DaemonBridge.update { it.copy(lastOutcome = alertMsg) }
                            DaemonBridge.appendLog(alertMsg)
                            EzdNotificationHelper.postAlertNotification(
                                context = applicationContext,
                                title = "Thermal Safety Watchdog",
                                message = alertMsg,
                                notificationId = NOTIF_ID + 4
                            )
                            EzdProfileWidgetProvider.updateAllWidgets(applicationContext, GameProfileId.BALANCED.id)
                        }
                    }
                }

                currentProfileId = activeProfileId
                BatteryMonitorTracker.sampleNow(applicationContext)
                updatePermanentNotification(profileId = activeProfileId)

                DaemonBridge.update {
                    it.copy(
                        commandsExecuted = RootShellManager.executedCount,
                        perAppMonitorActive = watchApps,
                        currentForegroundPkg = fgPkg,
                        activeGamePkg = activeConfiguredPkg.orEmpty()
                    )
                }

                val waitMs = when {
                    activeProfileId == GameProfileId.BEAST.id -> 3_000L // Mandatory 3s Beast thermal watchdog cadence
                    watchApps && useRoot -> 12_000L // Event-driven via logcat stream + BroadcastReceiver
                    watchApps -> 3_500L // UsageEvents fallback without root
                    else -> 15_000L
                }
                withTimeoutOrNull(waitMs) {
                    DaemonBridge.syncRequest.first()
                }
            }
        }
    }

    private fun startNotificationTicker() {
        notificationTickerJob?.cancel()
        notificationTickerJob = serviceScope.launch {
            while (isActive) {
                delay(2_500L)
                updatePermanentNotification(profileId = currentProfileId)
            }
        }
    }

    private fun formatNotificationTitle(profileId: String): String {
        val modeLabel = when (profileId.lowercase()) {
            GameProfileId.BEAST.id -> "BEAST"
            GameProfileId.PERFORMANCE.id -> "Performance"
            GameProfileId.POWERSAVE.id -> "Power save"
            else -> "Balanced"
        }
        return "EZD Manager • $modeLabel"
    }

    private fun updatePermanentNotification(
        profileId: String = currentProfileId
    ) {
        val stats = BatteryMonitorTracker.sampleNow(applicationContext)
        val title = formatNotificationTitle(profileId)
        val body = BatteryMonitorTracker.notificationLine(stats)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIF_ID, buildNotification(title, body))
    }

    private suspend fun detectForegroundPackage(rootGranted: Boolean, shizukuGranted: Boolean): String {
        val cachedFg = eventForegroundPkg
        if (cachedFg.isNotBlank() && (System.currentTimeMillis() - lastEventFgMs) < 30_000L) {
            return cachedFg
        }

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm != null) {
            val now = System.currentTimeMillis()
            val events = runCatching { usm.queryEvents(now - 6_000L, now) }.getOrNull()
            if (events != null) {
                var lastPkg = ""
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastPkg = event.packageName ?: lastPkg
                    }
                }
                if (lastPkg.isNotBlank()) return lastPkg
            }
        }

        val cmd = "dumpsys window displays 2>/dev/null | grep -m1 'mCurrentFocus' | sed -E 's/.* ([a-zA-Z0-9_.]+)\\/.*/\\1/'"
        val res = when {
            rootGranted -> RootShellManager.exec(cmd, timeoutMs = 2_000L)
            shizukuGranted -> ShizukuManager.exec(cmd, timeoutMs = 2_000L)
            else -> null
        }
        val candidate = res?.stdout?.firstOrNull()?.trim().orEmpty()
        if (candidate.contains('.')) return candidate
        return ""
    }

    private suspend fun applyActiveRule(
        pkg: String,
        rule: PerAppConfig,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ) {
        if (savedPreviousRefreshHz == null) {
            savedPreviousRefreshHz = displayTouchRepo.readDisplayState().currentRefreshHz
        }
        EngineTuningManager.captureBaselineIfNeeded(applicationContext)
        val outcomes = mutableListOf<String>()
        val allProfiles = store.customProfilesFlow.first() + ProfileRepository.builtInProfiles()

        val chosenProfile = allProfiles.find { it.id == rule.profileId }
        if (chosenProfile != null && (rootGranted || shizukuGranted)) {
            profileRepo.applyProfile(chosenProfile, rootAvailable = rootGranted, shizukuAvailable = shizukuGranted)
            outcomes.add(chosenProfile.name)
        }
        rule.refreshRateHz?.let { hz ->
            displayTouchRepo.setRefreshRate(hz, rootGranted, shizukuGranted)
            outcomes.add("${hz.toInt()}Hz")
        }
        if (rule.gpuProfile.isNotBlank() && rootGranted) {
            val gpu = cpuGpuRepo.scanGpu()
            if (gpu.supported && rule.gpuProfile in gpu.availableGovernors) {
                cpuGpuRepo.setGpuGovernor(gpu, rule.gpuProfile)
            }
        }
        if (rule.touchBoost && rootGranted) {
            val touch = displayTouchRepo.scanTouch()
            touch.gamingModePath?.let { RootShellManager.writeAndVerify(it, "1") }
            touch.touchBoostPath?.let { RootShellManager.writeAndVerify(it, "1") }
        }
        if (rule.disableHwOverlays && (rootGranted || shizukuGranted)) {
            displayTouchRepo.setDisableHwOverlays(true, rootGranted, shizukuGranted)
        }
        rule.spoofPresetId?.let { spId ->
            if (rootGranted) {
                val allSpoofs = SystemToolsRepository.BUILT_IN_SPOOF_PRESETS + store.customSpoofPresetsFlow.first()
                allSpoofs.find { it.id == spId }?.let { preset ->
                    sysToolsRepo.applySpoofPreset(preset)
                    activeSpoofApplied = true
                    outcomes.add("Spoof:${preset.model}")
                }
            }
        }

        val summary = "Applied $pkg: ${outcomes.joinToString(" · ").ifBlank { "Active" }}"
        DaemonBridge.update { it.copy(lastAppliedPerAppPkg = pkg, lastOutcome = summary) }
        DaemonBridge.appendLog(summary)
    }

    private suspend fun restoreAfterExit(rootGranted: Boolean, shizukuGranted: Boolean) {
        EngineTuningManager.restoreBaselineOnExit(applicationContext, rootGranted, shizukuGranted)
        if (rootGranted || shizukuGranted) {
            displayTouchRepo.setDisableHwOverlays(false, rootGranted, shizukuGranted)
        }
        if (rootGranted && activeSpoofApplied) {
            sysToolsRepo.resetSpoofToOriginal()
            activeSpoofApplied = false
        }
        savedPreviousRefreshHz?.let { prev ->
            displayTouchRepo.setRefreshRate(prev, rootGranted, shizukuGranted)
            savedPreviousRefreshHz = null
        }
        DaemonBridge.update { it.copy(lastOutcome = "Restored hardware baseline on app exit", activeGamePkg = "") }
        DaemonBridge.appendLog("Restored hardware baseline after exiting foreground target")
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.daemon_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.daemon_channel_desc)
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(title: String, text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getActivity(this, 0, launchIntent, piFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
                .setPriority(Notification.PRIORITY_LOW)
                .setSound(null)
        }
        builder
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_ezd_stat)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        EzdNotificationHelper.getAppLogoBitmap(this)?.let { logo ->
            builder.setLargeIcon(logo)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_APPLY_BOOT_MEMORY = "com.evorez.ezdmanager.ACTION_APPLY_BOOT_MEMORY"
        const val ACTION_BOOT_SAFE_MODE_ALERT = "com.evorez.ezdmanager.ACTION_BOOT_SAFE_MODE_ALERT"
        const val ACTION_BEAST_BOOT_DISABLED_ALERT = "com.evorez.ezdmanager.ACTION_BEAST_BOOT_DISABLED_ALERT"
        const val ACTION_SWITCH_PROFILE = "com.evorez.ezdmanager.ACTION_SWITCH_PROFILE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_BEAST_BOOT_GUARD = "extra_beast_boot_guard"
        private const val CHANNEL_ID = "ezd_live_monitor"
        private const val NOTIF_ID = 7001
    }
}
