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
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.root.RootShellManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

object DaemonBridge {
    private val _stats = MutableStateFlow(DaemonStats())
    val stats: StateFlow<DaemonStats> = _stats.asStateFlow()

    private val _overlayTrigger = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val overlayTrigger: SharedFlow<String> = _overlayTrigger.asSharedFlow()

    fun emitOverlayTrigger(action: String) {
        _overlayTrigger.tryEmit(action)
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

/**
 * Persistent background daemon Service handling:
 * - Keeping the root shell warm and reporting live command counts/logs
 * - Foreground detection for Per-App Tuning AND Game Space selected games
 * - Safe automatic apply & crash-safe exit restoration of clocks, Hz, spoof, HBM, HW Overlays & DND
 * - Floating Quick-Access Pill & Gaming Side Panel system overlays
 */
class RootDaemonService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private lateinit var store: SettingsDataStore
    private lateinit var cpuGpuRepo: CpuGpuRepository
    private lateinit var displayTouchRepo: DisplayTouchRepository
    private lateinit var sysToolsRepo: SystemToolsRepository
    private lateinit var profileRepo: ProfileRepository
    private lateinit var overlayManager: OverlayPanelManager

    private var activeConfiguredPkg: String? = null
    private var activeSpoofApplied: Boolean = false
    private var activeHbmPath: String? = null
    private var savedPreviousRefreshHz: Float? = null
    private val fpsHistory = ArrayDeque<Int>(30)

    override fun onCreate() {
        super.onCreate()
        store = SettingsDataStore(applicationContext)
        cpuGpuRepo = CpuGpuRepository(applicationContext)
        displayTouchRepo = DisplayTouchRepository(applicationContext)
        sysToolsRepo = SystemToolsRepository(applicationContext)
        profileRepo = ProfileRepository(applicationContext, cpuGpuRepo, displayTouchRepo, sysToolsRepo)
        overlayManager = OverlayPanelManager(
            context = applicationContext,
            onSelectGameProfile = { profileId ->
                serviceScope.launch {
                    val cur = store.settingsFlow.first().gameSpace
                    val next = cur.copy(activeProfile = profileId)
                    store.saveGameSpace(next)
                    val prof = ProfileRepository.forGameProfile(profileId, store.customProfilesFlow.first())
                    profileRepo.applyProfile(prof)
                    DaemonBridge.appendLog("SidePanel switched Game Space profile -> ${profileId.label}")
                }
            },
            onToggleShortcut = { shortcut ->
                serviceScope.launch {
                    val cur = store.settingsFlow.first().gameSpace
                    val updated = when (shortcut) {
                        GamePanelShortcut.CPU_LOCK -> cur.copy(cpuLockEnabled = !cur.cpuLockEnabled)
                        GamePanelShortcut.HBM -> {
                            val d = displayTouchRepo.readDisplayState(true)
                            d.hbmPath?.let { displayTouchRepo.setHbm(it, !cur.hbmBoostEnabled) }
                            cur.copy(hbmBoostEnabled = !cur.hbmBoostEnabled)
                        }
                        GamePanelShortcut.TOUCH_BOOST -> cur.copy(touchBoostEnabled = !cur.touchBoostEnabled)
                        GamePanelShortcut.DND -> cur.copy(dndEnabled = !cur.dndEnabled)
                        else -> cur
                    }
                    store.saveGameSpace(updated)
                    DaemonBridge.emitOverlayTrigger("SIDE_PANEL_${shortcut.name}")
                }
            },
            onQuickPillAction = { label ->
                DaemonBridge.emitOverlayTrigger("PILL_$label")
            }
        )

        ensureChannel()
        val notification = buildNotification("Monitoring performance profiles & root daemon")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        DaemonBridge.update { it.copy(serviceRunning = true) }
        DaemonBridge.appendLog("RootDaemonService initialized")
        startDaemonLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitorJob?.isActive != true) startDaemonLoop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        runBlocking {
            withContext(Dispatchers.Main) { overlayManager.destroyAll() }
            if (activeConfiguredPkg != null) {
                val root = DaemonBridge.stats.value.rootGranted
                restoreAfterExit(root)
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
                val userSettings = runCatching { store.settingsFlow.first() }.getOrDefault(com.evorez.ezdmanager.data.UserSettings())
                val configs = runCatching { store.perAppConfigsFlow.first() }.getOrDefault(emptyMap())
                val enabledConfigs = configs.filterValues { it.enabled }
                val gameSpace = userSettings.gameSpace
                val watchApps = enabledConfigs.isNotEmpty() || (gameSpace.enabled && gameSpace.selectedGames.isNotEmpty())

                val fgPkg = if (watchApps || gameSpace.sidePanelEnabled) detectForegroundPackage(rootGranted) else ""
                val isSelectedGame = gameSpace.enabled && fgPkg in gameSpace.selectedGames

                // Sync Floating Pill overlay on main thread
                withContext(Dispatchers.Main) {
                    overlayManager.syncQuickPill(userSettings.floatingPill, userSettings.themeMode)
                }

                if (watchApps && fgPkg.isNotBlank() && fgPkg != packageName) {
                    val rule = enabledConfigs[fgPkg]
                    val shouldApply = rule != null || isSelectedGame
                    if (shouldApply && activeConfiguredPkg != fgPkg) {
                        applyActiveRule(fgPkg, rule, isSelectedGame, gameSpace, rootGranted)
                        activeConfiguredPkg = fgPkg
                    } else if (!shouldApply && activeConfiguredPkg != null) {
                        restoreAfterExit(rootGranted)
                        activeConfiguredPkg = null
                    }
                } else if (!watchApps && activeConfiguredPkg != null) {
                    restoreAfterExit(rootGranted)
                    activeConfiguredPkg = null
                }

                // Sync Gaming Side Panel when enabled (either during selected game or preview inside EZD)
                val showSidePanel = gameSpace.sidePanelEnabled && (isSelectedGame || fgPkg == packageName)
                if (showSidePanel) {
                    val overview = cpuGpuRepo.readDeviceOverview()
                    val policies = if (rootGranted) cpuGpuRepo.scanCpuPolicies() else emptyList()
                    val gpu = if (rootGranted) cpuGpuRepo.scanGpu() else GpuState()
                    val disp = displayTouchRepo.readDisplayState(rootGranted)
                    val fps = sysToolsRepo.readGenuineFps()
                    fps.fps?.let { f ->
                        if (fpsHistory.size >= 24) fpsHistory.removeFirst()
                        fpsHistory.addLast(f)
                    }
                    val snap = SidePanelSnapshot(
                        activeAppName = if (isSelectedGame) fgPkg.substringAfterLast('.') else "Game Space Preview",
                        batteryPercent = overview.batteryPercent,
                        cpuUsage = overview.cpuUsagePercent ?: 0,
                        gpuUsage = gpu.usagePercent ?: 0,
                        cpuMhz = (policies.lastOrNull()?.currentKhz ?: 0L) / 1000L,
                        gpuMhz = gpu.currentMhz ?: 0L,
                        fpsReading = fps,
                        fpsHistory = fpsHistory.toList(),
                        ramUsedMb = overview.ramUsedMb,
                        ramTotalMb = overview.ramTotalMb,
                        refreshHz = disp.currentRefreshHz,
                        netLabel = sysToolsRepo.scanNetwork().transportType,
                        activeProfile = gameSpace.activeProfile,
                        themeMode = userSettings.themeMode,
                        config = gameSpace
                    )
                    withContext(Dispatchers.Main) {
                        overlayManager.syncGameSidePanel(true, snap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        overlayManager.syncGameSidePanel(false, SidePanelSnapshot())
                    }
                }

                DaemonBridge.update {
                    it.copy(
                        commandsExecuted = RootShellManager.executedCount,
                        perAppMonitorActive = watchApps,
                        currentForegroundPkg = fgPkg,
                        activeGamePkg = if (isSelectedGame) fgPkg else ""
                    )
                }

                delay(if (watchApps || showSidePanel) 1_800L else 4_500L)
            }
        }
    }

    private suspend fun detectForegroundPackage(rootGranted: Boolean): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm != null) {
            val now = System.currentTimeMillis()
            val events = runCatching { usm.queryEvents(now - 10_000L, now) }.getOrNull()
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
        if (rootGranted) {
            val res = RootShellManager.exec(
                "dumpsys window displays 2>/dev/null | grep -m1 'mCurrentFocus' | sed -E 's/.* ([a-zA-Z0-9_.]+)\\/.*/\\1/'",
                timeoutMs = 2_000L
            )
            val candidate = res.stdout.firstOrNull()?.trim().orEmpty()
            if (candidate.contains('.')) return candidate
        }
        return ""
    }

    private suspend fun applyActiveRule(
        pkg: String,
        rule: PerAppConfig?,
        isGame: Boolean,
        gameSpace: GameSpaceConfig,
        rootGranted: Boolean
    ) {
        if (savedPreviousRefreshHz == null) {
            savedPreviousRefreshHz = displayTouchRepo.readDisplayState().currentRefreshHz
        }
        val outcomes = mutableListOf<String>()
        val allProfiles = store.customProfilesFlow.first() + ProfileRepository.builtInProfiles()

        if (isGame) {
            val gameProfile = ProfileRepository.forGameProfile(gameSpace.activeProfile, store.customProfilesFlow.first())
            if (rootGranted) {
                profileRepo.applyProfile(gameProfile)
                outcomes.add("Game:${gameProfile.name}")
                if (gameSpace.cpuLockEnabled) {
                    cpuGpuRepo.scanCpuPolicies().forEach { pol ->
                        val maxK = pol.availableFreqsKhz.lastOrNull()
                        val minLock = pol.availableFreqsKhz.getOrNull((pol.availableFreqsKhz.lastIndex * 0.7f).toInt())
                        if (maxK != null && minLock != null) cpuGpuRepo.setCpuFrequencyRange(pol, minLock, maxK)
                    }
                    outcomes.add("CPU Lock")
                }
                if (gameSpace.touchBoostEnabled) {
                    val t = displayTouchRepo.scanTouch()
                    t.gamingModePath?.let { RootShellManager.writeAndVerify(it, "1") }
                    t.touchBoostPath?.let { RootShellManager.writeAndVerify(it, "1") }
                }
                if (gameSpace.hbmBoostEnabled) {
                    val d = displayTouchRepo.readDisplayState(true)
                    d.hbmPath?.let { path ->
                        displayTouchRepo.setHbm(path, true)
                        activeHbmPath = path
                    }
                }
                if (gameSpace.disableHwOverlays) {
                    displayTouchRepo.setDisableHwOverlays(true)
                }
                if (gameSpace.foregroundPriorityEnabled) {
                    val qPkg = RootShellManager.shellQuote(pkg)
                    RootShellManager.exec("pidof $qPkg | xargs -r renice -n -10", timeoutMs = 2_000L)
                    outcomes.add("FG Priority")
                }
            }
            if (gameSpace.dndEnabled) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm?.isNotificationPolicyAccessGranted == true) {
                    runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) }
                }
            }
        }

        if (rule != null) {
            val chosenProfile = allProfiles.find { it.id == rule.profileId }
            if (chosenProfile != null && rootGranted && !isGame) {
                profileRepo.applyProfile(chosenProfile)
                outcomes.add(chosenProfile.name)
            }
            rule.refreshRateHz?.let { hz ->
                displayTouchRepo.setRefreshRate(hz, rootGranted)
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
            if (rule.disableHwOverlays && rootGranted) {
                displayTouchRepo.setDisableHwOverlays(true)
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
        }

        val summary = "Applied $pkg: ${outcomes.joinToString(" · ").ifBlank { "Active" }}"
        DaemonBridge.update { it.copy(lastAppliedPerAppPkg = pkg, lastOutcome = summary) }
        DaemonBridge.appendLog(summary)
    }

    private suspend fun restoreAfterExit(rootGranted: Boolean) {
        val balanced = ProfileRepository.builtInProfiles().first { it.id == GameProfileId.BALANCED.id }
        if (rootGranted) {
            profileRepo.applyProfile(balanced)
            displayTouchRepo.setDisableHwOverlays(false)
            activeHbmPath?.let { p ->
                displayTouchRepo.setHbm(p, false)
                activeHbmPath = null
            }
            if (activeSpoofApplied) {
                sysToolsRepo.resetSpoofToOriginal()
                activeSpoofApplied = false
            }
        }
        savedPreviousRefreshHz?.let { prev ->
            displayTouchRepo.setRefreshRate(prev, rootGranted)
            savedPreviousRefreshHz = null
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm?.isNotificationPolicyAccessGranted == true) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
        DaemonBridge.update { it.copy(lastOutcome = "Restored balanced baseline on app exit", activeGamePkg = "") }
        DaemonBridge.appendLog("Restored balanced baseline after exiting foreground target")
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.daemon_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.daemon_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.daemon_notif_title))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ezd_root_daemon"
        private const val NOTIF_ID = 7001
    }
}
