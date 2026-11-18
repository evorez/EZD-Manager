package com.evorez.ezdmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.evorez.ezdmanager.MainActivity
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.hardware.CpuGpuRepository
import com.evorez.ezdmanager.hardware.DisplayTouchRepository
import com.evorez.ezdmanager.hardware.RootModulesAndPropRepository
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.root.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object DaemonBridge {
    private val _stats = MutableStateFlow(DaemonStats())
    val stats: StateFlow<DaemonStats> = _stats.asStateFlow()

    private val _syncRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val syncRequest: SharedFlow<Unit> = _syncRequest.asSharedFlow()

    fun requestSync() {
        _syncRequest.tryEmit(Unit)
    }

    fun update(transform: (DaemonStats) -> DaemonStats) {
        _stats.update(transform)
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
    private lateinit var store: SettingsDataStore
    private lateinit var cpuGpuRepo: CpuGpuRepository
    private lateinit var displayTouchRepo: DisplayTouchRepository
    private lateinit var sysToolsRepo: SystemToolsRepository
    private lateinit var modulesRepo: RootModulesAndPropRepository
    private lateinit var profileRepo: ProfileRepository

    private var activeConfiguredPkg: String? = null
    private var activeSpoofApplied: Boolean = false
    private var savedPreviousRefreshHz: Float? = null
    private var lastNotifUpdateMs: Long = 0L

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
        ensureChannel()
        val initialStats = BatteryMonitorTracker.sampleNow(applicationContext)
        val notification = buildNotification(
            title = "EZD Manager · Balanced",
            text = BatteryMonitorTracker.notificationLine(initialStats)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        DaemonBridge.update { it.copy(serviceRunning = true) }
        DaemonBridge.appendLog("EZD Monitor & Daemon initialized")
        startDaemonLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        store.setActiveProfileId(chosen.id)
        val rootStatus = RootShellManager.probeRoot(forceFreshRequest = false)
        val rootGranted = rootStatus == RootStatus.GRANTED
        val shizukuGranted = ShizukuManager.refreshStatus(applicationContext) == ShizukuStatus.GRANTED
        DaemonBridge.update { it.copy(rootGranted = rootGranted) }
        if (rootGranted || shizukuGranted) {
            profileRepo.applyProfile(chosen, rootAvailable = rootGranted, shizukuAvailable = shizukuGranted)
        }
        val msg = "Switched profile to ${chosen.name}"
        DaemonBridge.update { it.copy(lastOutcome = msg) }
        DaemonBridge.appendLog(msg)
        updatePermanentNotification()
        EzdProfileWidgetProvider.updateAllWidgets(applicationContext)
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
        monitorJob?.cancel()
        runBlocking {
            if (activeConfiguredPkg != null) {
                val root = DaemonBridge.stats.value.rootGranted
                val shizuku = ShizukuManager.refreshStatus(applicationContext) == ShizukuStatus.GRANTED
                restoreAfterExit(root, shizuku)
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
            DaemonBridge.update {
                it.copy(
                    rootGranted = rootGranted,
                    commandsExecuted = RootShellManager.executedCount
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

                val fgPkg = if (watchApps) detectForegroundPackage(useRoot, useShizuku) else ""

                if (watchApps && fgPkg.isNotBlank() && fgPkg != packageName) {
                    val rule = enabledConfigs[fgPkg]
                    if (rule != null && activeConfiguredPkg != fgPkg) {
                        applyActiveRule(fgPkg, rule, useRoot, useShizuku)
                        activeConfiguredPkg = fgPkg
                    } else if (rule == null && activeConfiguredPkg != null) {
                        restoreAfterExit(useRoot, useShizuku)
                        activeConfiguredPkg = null
                    }
                } else if (!watchApps && activeConfiguredPkg != null) {
                    restoreAfterExit(useRoot, useShizuku)
                    activeConfiguredPkg = null
                }

                BatteryMonitorTracker.sampleNow(applicationContext)
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastNotifUpdateMs >= 4_500L) {
                    lastNotifUpdateMs = nowMs
                    updatePermanentNotification(
                        profileId = settings?.activeProfileId ?: "balanced",
                        privLabel = privMode.label,
                        watchApps = watchApps
                    )
                }

                DaemonBridge.update {
                    it.copy(
                        commandsExecuted = RootShellManager.executedCount,
                        perAppMonitorActive = watchApps,
                        currentForegroundPkg = fgPkg,
                        activeGamePkg = ""
                    )
                }

                val waitMs = if (watchApps) 2_200L else 5_000L
                withTimeoutOrNull(waitMs) {
                    DaemonBridge.syncRequest.first()
                }
            }
        }
    }

    private fun updatePermanentNotification(
        profileId: String = "balanced",
        privLabel: String = "Monitor",
        watchApps: Boolean = false
    ) {
        val stats = BatteryMonitorTracker.sampleNow(applicationContext)
        val profileLabel = ProfileRepository.builtInProfiles().find { it.id == profileId }?.name
            ?: profileId.replaceFirstChar { it.uppercase() }
        val watchSuffix = if (watchApps) " · Auto-App" else ""
        val title = "EZD Manager · $profileLabel · $privLabel$watchSuffix"
        val body = BatteryMonitorTracker.notificationLine(stats)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIF_ID, buildNotification(title, body))
    }

    private suspend fun detectForegroundPackage(rootGranted: Boolean, shizukuGranted: Boolean): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm != null) {
            val now = System.currentTimeMillis()
            val events = runCatching { usm.queryEvents(now - 15_000L, now) }.getOrNull()
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
            val recent = runCatching {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60_000L, now)
            }.getOrNull()?.maxByOrNull { it.lastTimeUsed }?.packageName
            if (!recent.isNullOrBlank()) return recent
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
        val balanced = ProfileRepository.builtInProfiles().first { it.id == GameProfileId.BALANCED.id }
        if (rootGranted || shizukuGranted) {
            profileRepo.applyProfile(balanced, rootAvailable = rootGranted, shizukuAvailable = shizukuGranted)
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
        DaemonBridge.update { it.copy(lastOutcome = "Restored balanced baseline on app exit", activeGamePkg = "") }
        DaemonBridge.appendLog("Restored balanced baseline after exiting foreground target")
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
        const val ACTION_SWITCH_PROFILE = "com.evorez.ezdmanager.ACTION_SWITCH_PROFILE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        private const val CHANNEL_ID = "ezd_live_monitor"
        private const val NOTIF_ID = 7001
    }
}
