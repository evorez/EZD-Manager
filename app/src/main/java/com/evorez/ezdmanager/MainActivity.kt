package com.evorez.ezdmanager

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.WorkModeManager
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.screens.*
import com.evorez.ezdmanager.ui.theme.EzdTheme
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: EzdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighRefreshRate()
        setContent {
            EzdRootApp(viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startTelemetry()
    }

    override fun onStop() {
        viewModel.stopTelemetry()
        super.onStop()
    }

    private fun enableHighRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val currentMode = display?.mode
        val supported = display?.supportedModes ?: emptyArray()
        val bestMode = supported
            .filter {
                currentMode == null ||
                    (it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight)
            }
            .maxByOrNull { it.refreshRate }

        window.attributes = window.attributes.apply {
            if (bestMode != null) {
                preferredDisplayModeId = bestMode.modeId
                preferredRefreshRate = bestMode.refreshRate
            } else {
                preferredRefreshRate = 120f
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EzdRootApp(vm: EzdViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val daemonStats by vm.daemonStats.collectAsStateWithLifecycle()
    val vpnActive by vm.vpnActive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vm.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.pendingVpnConsentNeeded) {
        if (state.pendingVpnConsentNeeded) {
            val intent = VpnService.prepare(context)
            if (intent != null) {
                vpnPrepareLauncher.launch(intent)
            } else {
                vm.onVpnPermissionResult(true)
            }
        }
    }

    val tabs = MainTab.entries
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedPage.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size }
    )
    LaunchedEffect(pagerState.currentPage) {
        savedPage = pagerState.currentPage
    }

    val homeScroll = rememberScrollState()
    val toolsScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val appsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var pendingAppPkg by remember { mutableStateOf<String?>(null) }
    var splashDone by rememberSaveable { mutableStateOf(false) }

    var draggedTool by remember { mutableStateOf<ToolId?>(null) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    var pillBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var containerOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val handleBrandLogoTap: () -> Unit = {
        val count = vm.recordBrandLogoTap()
        if (count in 4..6) {
            Toast.makeText(context, "${7 - count} step(s) away from Reporting & Requesting", Toast.LENGTH_SHORT).show()
        }
    }

    val activeTool = state.activeTool
    val currentScroll = when (tabs.getOrNull(pagerState.currentPage)) {
        MainTab.HOME -> homeScroll.value.toFloat()
        MainTab.TOOLS -> toolsScroll.value.toFloat()
        MainTab.APPS -> appsListState.firstVisibleItemScrollOffset.toFloat()
        MainTab.SETTINGS -> settingsScroll.value.toFloat()
        null -> 0f
    }
    val collapseFraction = (currentScroll / 140f).coerceIn(0f, 1f)

    BackHandler(
        enabled = state.showSearchSheet ||
            state.showPowerSheet ||
            state.showNotificationsSheet ||
            state.showDeviceDetails ||
            state.showDeveloperEggDialog ||
            activeTool != null ||
            pagerState.currentPage != 0
    ) {
        when {
            state.showDeveloperEggDialog -> vm.dismissDeveloperEgg()
            state.showSearchSheet -> vm.setShowSearchSheet(false)
            state.showPowerSheet -> vm.setShowPowerSheet(false)
            state.showNotificationsSheet -> vm.setShowNotificationsSheet(false)
            state.showDeviceDetails -> vm.setShowDeviceDetails(false)
            activeTool != null -> vm.openTool(null)
            pagerState.currentPage != 0 -> scope.launch {
                pagerState.animateScrollToPage(0, animationSpec = ColorOsMotion.fluidSpring())
            }
        }
    }

    EzdTheme(
        themeMode = state.settings.themeMode,
        themeStyle = state.settings.themeStyle,
        accentPreset = state.settings.accentPreset,
        blurIntensity = state.settings.blurIntensity,
        reduceAnimations = state.settings.reduceAnimations,
        texturePreset = state.settings.backgroundTexture,
        textureOpacity = state.settings.backgroundTextureOpacity,
        fontStyle = state.settings.appFontStyle
    ) {
        val visuals = LocalEzdVisuals.current
        EzdAmbientBackground(modifier = Modifier.fillMaxSize()) {
            if (!state.settings.onboardingCompleted) {
                OnboardingScreen(
                    state = state,
                    onRequestRoot = { vm.autoDetectRoot(true) },
                    onRequestShizuku = { vm.requestShizukuPermission() },
                    onRefreshPrivileges = { vm.refreshPrivilegeModes() },
                    onCompleteOnboarding = { name, tagline, uri, mode ->
                        vm.completeOnboarding(name, tagline, uri, mode)
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { containerOriginInRoot = it.positionInRoot() }
                ) {
                    val screenKey = if (activeTool != null) "tool_${activeTool.name}" else "main_tabs"

                    AnimatedContent(
                        targetState = screenKey,
                        transitionSpec = {
                            if (visuals.reduceMotion) {
                                (EnterTransition.None togetherWith ExitTransition.None)
                                    .using(SizeTransform(clip = false))
                            } else {
                                val slideSpec = tween<IntOffset>(
                                    durationMillis = 320,
                                    easing = ColorOsMotion.IosNavigationEasing
                                )
                                val pushing = targetState != "main_tabs"
                                if (pushing) {
                                    ContentTransform(
                                        targetContentEnter = slideInHorizontally(slideSpec) { fullW -> fullW },
                                        initialContentExit = slideOutHorizontally(slideSpec) { fullW -> -(fullW * 0.25f).toInt() },
                                        targetContentZIndex = 1f,
                                        sizeTransform = SizeTransform(clip = false)
                                    )
                                } else {
                                    ContentTransform(
                                        targetContentEnter = slideInHorizontally(slideSpec) { fullW -> -(fullW * 0.25f).toInt() },
                                        initialContentExit = slideOutHorizontally(slideSpec) { fullW -> fullW },
                                        targetContentZIndex = -1f,
                                        sizeTransform = SizeTransform(clip = false)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "iosRootScreen"
                    ) { key ->
                        when {
                            key.startsWith("tool_") && activeTool != null -> {
                                ToolDetailContainer(
                                    tool = activeTool,
                                    state = state,
                                    vm = vm,
                                    onBack = { vm.openTool(null) }
                                )
                            }
                            else -> {
                                Column(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val currentTab = tabs[pagerState.currentPage]
                                    TopBrandBar(
                                        title = when (currentTab) {
                                            MainTab.HOME -> "EZD Manager"
                                            MainTab.TOOLS -> "Tools & Engine"
                                            MainTab.APPS -> "Per-App Tuning"
                                            MainTab.SETTINGS -> "Settings"
                                        },
                                        collapseFraction = collapseFraction,
                                        unreadNotifications = state.settings.notifications.unreadCount,
                                        onLogoClick = handleBrandLogoTap,
                                        onSearchClick = { vm.setShowSearchSheet(true) },
                                        onPowerClick = { vm.setShowPowerSheet(true) },
                                        onNotificationsClick = { vm.setShowNotificationsSheet(true) },
                                        onSettingsClick = {
                                            val settingsIdx = tabs.indexOf(MainTab.SETTINGS)
                                            if (settingsIdx >= 0) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(settingsIdx, animationSpec = ColorOsMotion.fluidSpring())
                                                }
                                            }
                                        }
                                    )

                                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        val maxContentWidth = if (maxWidth > 720.dp) 680.dp else maxWidth
                                        HorizontalPager(
                                            state = pagerState,
                                            userScrollEnabled = draggedTool == null,
                                            flingBehavior = PagerDefaults.flingBehavior(
                                                state = pagerState,
                                                snapAnimationSpec = ColorOsMotion.fluidSpring()
                                            ),
                                            modifier = Modifier.fillMaxSize()
                                        ) { page ->
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .widthIn(max = maxContentWidth)
                                                        .fillMaxHeight()
                                                ) {
                                                    when (tabs[page]) {
                                                        MainTab.HOME -> {
                                                            PullToRefreshBox(
                                                                isRefreshing = state.isRefreshingProbe,
                                                                onRefresh = { vm.refreshAllHardware(forceProbe = true) },
                                                                modifier = Modifier.fillMaxSize()
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .verticalScroll(homeScroll)
                                                                ) {
                                                                    HomeScreen(
                                                                        state = state,
                                                                        daemonStats = daemonStats,
                                                                        onRetryRoot = { vm.autoDetectRoot(forceFresh = true) },
                                                                        onRequestShizuku = { vm.requestShizukuPermission() },
                                                                        onShowDetails = { vm.setShowDeviceDetails(true) },
                                                                        onApplyProfile = { vm.applyProfile(it) },
                                                                        onToggleBeastMode = { vm.toggleBeastMode() },
                                                                        onSetBeastThermalLimits = { bat, soc -> vm.setBeastThermalLimits(bat, soc) },
                                                                        onSetBeastIgnoreThermal = { vm.setBeastIgnoreThermalLimits(it) },
                                                                        onOpenTool = { t -> vm.openTool(t) },
                                                                        onClearSafeMode = { vm.clearBootSafeMode() },
                                                                        onUpdateUserProfile = { name, tag, uri -> vm.updateUserProfile(name, tag, uri) }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        MainTab.TOOLS -> {
                                                            PullToRefreshBox(
                                                                isRefreshing = state.isRefreshingProbe,
                                                                onRefresh = { vm.refreshAllHardware(forceProbe = true) },
                                                                modifier = Modifier.fillMaxSize()
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .verticalScroll(toolsScroll)
                                                                ) {
                                                                    ToolsScreen(
                                                                        state = state,
                                                                        currentAuthority = state.effectivePrivilegeMode,
                                                                        pinnedTools = state.settings.pinnedPillTools,
                                                                        onSelectTool = { t -> vm.openTool(t) },
                                                                        onRequestRoot = { vm.autoDetectRoot(forceFresh = true) },
                                                                        onRequestShizuku = { vm.requestShizukuPermission() },
                                                                        onApplyProfile = { vm.applyProfile(it) },
                                                                        onSetCpuRange = { p, minK, maxK -> vm.setCpuFrequencyRange(p, minK, maxK) },
                                                                        onSetCpuGovernor = { p, gov -> vm.setCpuGovernor(p, gov) },
                                                                        onResetCpuPolicy = { p -> vm.resetCpuPolicy(p) },
                                                                        onToggleCore = { c, on -> vm.toggleCpuCore(c, on) },
                                                                        onSetGpuRange = { minM, maxM -> vm.setGpuFrequency(minM, maxM) },
                                                                        onSetGpuGovernor = { g -> vm.setGpuGovernor(g) },
                                                                        onResetGpu = { vm.resetGpu() },
                                                                        onToolDragStart = { tool, rootPos ->
                                                                            draggedTool = tool
                                                                            dragPointerRoot = rootPos
                                                                        },
                                                                        onToolDragMove = { rootPos ->
                                                                            dragPointerRoot = rootPos
                                                                        },
                                                                        onToolDragEnd = {
                                                                            val tool = draggedTool
                                                                            val bounds = pillBoundsInRoot
                                                                            if (tool != null && bounds != null && bounds.contains(dragPointerRoot)) {
                                                                                vm.pinToolToPill(tool)
                                                                                Toast.makeText(
                                                                                    context,
                                                                                    "Pinned ${tool.title} to navigation pill",
                                                                                    Toast.LENGTH_SHORT
                                                                                ).show()
                                                                            }
                                                                            draggedTool = null
                                                                        },
                                                                        onToolDragCancel = {
                                                                            draggedTool = null
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        MainTab.APPS -> {
                                                            AppsScreen(
                                                                state = state,
                                                                daemonStats = daemonStats,
                                                                vpnActive = vpnActive,
                                                                listState = appsListState,
                                                                initialSelectedPkg = pendingAppPkg,
                                                                onClearInitialPkg = { pendingAppPkg = null },
                                                                onToggleIncludeSystem = { vm.setIncludeSystemApps(it) },
                                                                onOpenUsageAccess = { vm.openUsageStatsSettings() },
                                                                onSavePerAppConfig = { cfg -> vm.savePerAppConfig(cfg) },
                                                                onRequestVpnConsent = { vm.syncPerAppDnsVpn() },
                                                                onClearCache = { vm.clearAppCache(it) },
                                                                onForceStop = { vm.forceStopApp(it) },
                                                                onToggleFreeze = { vm.toggleFreezeApp(it) },
                                                                onUninstallApp = { vm.uninstallApp(it) },
                                                                spoofPresets = vm.allSpoofPresets()
                                                            )
                                                        }
                                                        MainTab.SETTINGS -> {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .verticalScroll(settingsScroll)
                                                            ) {
                                                                SettingsScreen(
                                                                    state = state,
                                                                    daemonStats = daemonStats,
                                                                    onSetThemeMode = { vm.setThemeMode(it) },
                                                                    onSetAccentPreset = { vm.setAccentPreset(it) },
                                                                    onSetBlurIntensity = { vm.setBlurIntensity(it) },
                                                                    onSetReduceAnimations = { vm.setReduceAnimations(it) },
                                                                    onSetFloatingPillOpaque = { vm.setFloatingPillOpaque(it) },
                                                                    onSetBackgroundTexture = { vm.setBackgroundTexture(it) },
                                                                    onSetBackgroundTextureOpacity = { vm.setBackgroundTextureOpacity(it) },
                                                                    onSetAppFontStyle = { vm.setAppFontStyle(it) },
                                                                    onRemovePinnedTool = { vm.unpinToolFromPill(it) },
                                                                    onMovePinnedTool = { tool, delta -> vm.movePinnedPillTool(tool, delta) },
                                                                    onSetNotificationPrefs = { vm.setNotificationPrefs(it) },
                                                                    onToggleDaemon = { vm.toggleDaemonService(it) },
                                                                    onOpenTool = { vm.openTool(it) },
                                                                    onOpenUsageAccess = { vm.openUsageStatsSettings() },
                                                                    onOpenWriteSettings = { vm.openWriteSettings() },
                                                                    onOpenAppNotifications = { vm.openAppNotificationSettings() },
                                                                    onToggleBootMemory = { vm.setApplyOnBoot(it) },
                                                                    onCaptureBootMemory = { vm.captureBootMemorySnapshot() },
                                                                    onApplyBootMemoryNow = { vm.applyBootMemoryNow() },
                                                                    onClearSafeMode = { vm.clearBootSafeMode() },
                                                                    onCheckUpdates = { vm.checkForGitHubUpdates() },
                                                                    onCopyDiagnostics = { vm.exportAndCopyDiagnostics() },
                                                                    changelogHistory = remember { vm.builtInChangelog() },
                                                                    onBrandLogoTap = handleBrandLogoTap
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Frosted Liquid-Glass Navigation Pill driven strictly by PagerState
                    if (activeTool == null) {
                        val dropHovered = draggedTool != null && pillBoundsInRoot?.contains(dragPointerRoot) == true
                        FloatingPillNavBar(
                            pagerState = pagerState,
                            backdropLayer = null,
                            opaque = state.settings.floatingPillOpaque,
                            pinnedTools = state.settings.pinnedPillTools,
                            isDropTargetHovered = dropHovered,
                            onPillBoundsChanged = { bounds -> pillBoundsInRoot = bounds },
                            onOpenPinnedTool = { tool ->
                                val badge = WorkModeManager.getToolBadge(tool, state.effectivePrivilegeMode)
                                if (badge.isStrictlyLocked) {
                                    Toast.makeText(
                                        context,
                                        "${tool.title} requires Root access",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    vm.openTool(tool)
                                }
                            },
                            onRemovePinnedTool = { vm.unpinToolFromPill(it) },
                            onMovePinnedTool = { tool, delta -> vm.movePinnedPillTool(tool, delta) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(50f)
                        )
                    }

                    // Drag-and-drop preview badge floating under the user's finger
                    draggedTool?.let { tool ->
                        val localPos = dragPointerRoot - containerOriginInRoot
                        val dropReady = pillBoundsInRoot?.contains(dragPointerRoot) == true
                        Surface(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (localPos.x - 110f).roundToInt(),
                                        y = (localPos.y - 90f).roundToInt()
                                    )
                                }
                                .zIndex(80f),
                            shape = CircleShape,
                            color = visuals.solidSurfaceElevated,
                            shadowElevation = 10.dp,
                            border = BorderStroke(
                                width = if (dropReady) 2.dp else 1.dp,
                                color = if (dropReady) visuals.accent else visuals.glassBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = pinnedToolIcon(tool),
                                    contentDescription = null,
                                    tint = if (dropReady) visuals.accent else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (dropReady) "Drop to Pin: ${tool.title}" else tool.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (dropReady) visuals.accent else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (!splashDone) {
                        BrandLaunchSplash(onFinished = { splashDone = true })
                    }
                }
            }

            if (state.showSearchSheet) {
                GlobalSearchDialog(
                    entries = remember(state.installedApps) { vm.buildSearchIndex() },
                    onDismiss = { vm.setShowSearchSheet(false) },
                    onSelectEntry = { entry ->
                        vm.setShowSearchSheet(false)
                        vm.openTool(entry.targetTool)
                        pendingAppPkg = entry.packageName
                        val idx = tabs.indexOf(entry.targetTab)
                        if (idx >= 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(idx, animationSpec = ColorOsMotion.fluidSpring())
                            }
                        }
                    }
                )
            }

            if (state.showPowerSheet) {
                PowerControlBottomSheet(
                    state = state,
                    onExecute = { vm.performPowerAction(it) },
                    onDismiss = { vm.setShowPowerSheet(false) }
                )
            }

            if (state.showNotificationsSheet) {
                NotificationsBottomSheet(
                    state = state,
                    daemonStats = daemonStats,
                    onOpenSettings = {
                        vm.setShowNotificationsSheet(false)
                        val settingsIdx = tabs.indexOf(MainTab.SETTINGS)
                        if (settingsIdx >= 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(settingsIdx, animationSpec = ColorOsMotion.fluidSpring())
                            }
                        }
                    },
                    onDismiss = { vm.setShowNotificationsSheet(false) }
                )
            }

            if (state.showDeviceDetails) {
                DeviceDetailsDialog(
                    report = vm.buildDeviceReportText(),
                    onDismiss = { vm.setShowDeviceDetails(false) }
                )
            }
            if (state.showDeveloperEggDialog) {
                DeveloperEggDialog(onDismiss = { vm.dismissDeveloperEgg() })
            }
        }
    }
}

@Composable
private fun ToolDetailContainer(
    tool: ToolId,
    state: EzdUiState,
    vm: EzdViewModel,
    onBack: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val isTerminal = tool == ToolId.ROOT_TERMINAL
    val baseModifier = Modifier
        .fillMaxSize()
        .background(visuals.bgBase)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    val containerModifier = if (isTerminal) {
        baseModifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    } else {
        baseModifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    }

    Column(
        modifier = containerModifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = visuals.pillBackground,
                border = BorderStroke(1.dp, visuals.glassBorder)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.statusBanner.isNotBlank() && !isTerminal) {
            GlassCard(cornerRadius = 20.dp, highlightBorder = true) {
                Text(state.statusBanner, style = MaterialTheme.typography.bodySmall)
            }
        }

        when (tool) {
            ToolId.CPU_GPU_CONTROL -> CpuGpuScreen(
                state = state,
                onSetCpuRange = { p, minK, maxK -> vm.setCpuFrequencyRange(p, minK, maxK) },
                onSetCpuGovernor = { p, gov -> vm.setCpuGovernor(p, gov) },
                onResetCpuPolicy = { p -> vm.resetCpuPolicy(p) },
                onToggleCore = { c, on -> vm.toggleCpuCore(c, on) },
                onSetGpuRange = { minM, maxM -> vm.setGpuFrequency(minM, maxM) },
                onSetGpuGovernor = { g -> vm.setGpuGovernor(g) },
                onResetGpu = { vm.resetGpu() },
                onOpenProfiles = { vm.openTool(ToolId.PROFILES) }
            )
            ToolId.ZRAM_VM -> ZramVmPanel(state, vm)
            ToolId.KERNEL_TWEAKS -> KernelTweaksPanel(state, vm)
            ToolId.MODULES -> RootModulesPanel(state, vm)
            ToolId.BUILD_PROP -> BuildPropEditorPanel(state, vm)
            ToolId.BOOT_GUARD -> BootGuardPanel(state, vm)
            ToolId.DISPLAY -> DisplayPanel(state, vm)
            ToolId.TOUCH -> TouchPanel(state, vm)
            ToolId.NETWORK -> NetworkPanel(state, vm)
            ToolId.AUDIO_ENHANCER -> AudioEnhancerPanel(state, vm)
            ToolId.BATTERY_SAVING -> BatterySavingPanel(state, vm)
            ToolId.FAST_CHARGE -> FastChargePanel(state, vm)
            ToolId.DEVICE_SPOOF -> DeviceSpoofPanel(state, vm)
            ToolId.PROFILES -> ProfilesPanel(state, vm)
            ToolId.EZD_BACKUP -> EzdBackupPanel(state, vm)
            ToolId.APP_BACKUP -> AppBackupPanel(state, vm)
            ToolId.ROOT_TERMINAL -> RootTerminalPanel(state, vm, modifier = Modifier.weight(1f))
        }
        if (!isTerminal) {
            Spacer(Modifier.height(48.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun PowerControlBottomSheet(
    state: EzdUiState,
    onExecute: (PowerAction) -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    var pendingConfirm by remember { mutableStateOf<PowerAction?>(null) }
    val actions = remember(state.edlSupported) {
        PowerAction.entries.filter { it != PowerAction.EDL || state.edlSupported }
    }

    GlassBottomSheet(onDismiss = onDismiss) {
        Text("Power & Reboot", style = MaterialTheme.typography.titleLarge)
        if (state.rootStatus != RootStatus.GRANTED) {
            Text(
                text = "Root access is required to trigger hardware power actions.",
                style = MaterialTheme.typography.bodySmall,
                color = visuals.warningColor
            )
        }
        actions.forEach { action ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = state.rootStatus == RootStatus.GRANTED && !state.busy) {
                        pendingConfirm = action
                    },
                shape = RoundedCornerShape(18.dp),
                color = visuals.solidSurfaceBase,
                border = BorderStroke(1.dp, visuals.glassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlowIcon(
                        icon = when (action) {
                            PowerAction.REBOOT -> Icons.Rounded.RestartAlt
                            PowerAction.SOFT_REBOOT -> Icons.Rounded.Refresh
                            PowerAction.RECOVERY -> Icons.Rounded.BuildCircle
                            PowerAction.BOOTLOADER -> Icons.Rounded.DeveloperMode
                            PowerAction.POWER_OFF -> Icons.Rounded.PowerSettingsNew
                            PowerAction.EDL -> Icons.Rounded.FlashOn
                        },
                        contentDescription = action.title,
                        tint = if (action == PowerAction.POWER_OFF || action == PowerAction.EDL) {
                            MaterialTheme.colorScheme.error
                        } else visuals.accent,
                        size = 20.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(action.title, style = MaterialTheme.typography.titleSmall)
                        Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    pendingConfirm?.let { action ->
        GlassDialog(
            title = "Confirm ${action.title}",
            onDismiss = { pendingConfirm = null },
            confirmText = action.title,
            onConfirm = {
                onExecute(action)
                pendingConfirm = null
            }
        ) {
            Text(action.destructiveNote, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NotificationsBottomSheet(
    state: EzdUiState,
    daemonStats: DaemonStats,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Activity & Notifications", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onOpenSettings) { Text("Preferences") }
        }
        Text(
            text = "Latest: ${state.settings.notifications.lastNotificationText}",
            style = MaterialTheme.typography.bodyMedium,
            color = visuals.accent
        )
        if (daemonStats.logs.isEmpty()) {
            Text("No recent daemon events recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            daemonStats.logs.takeLast(8).reversed().forEach { log ->
                Text(log, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono)
            }
        }
    }
}

@Composable
private fun GlobalSearchDialog(
    entries: List<SearchEntry>,
    onDismiss: () -> Unit,
    onSelectEntry: (SearchEntry) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(entries, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) entries.take(25)
        else entries.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Search Everywhere", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search tools, CPU, GPU, apps…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onSelectEntry(item) },
                            shape = RoundedCornerShape(18.dp),
                            color = visuals.solidSurfaceElevated,
                            border = BorderStroke(1.dp, visuals.glassBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(label = item.category, accent = visuals.accent)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DeviceDetailsDialog(
    report: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Device Specifications") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono)
            }
        },
        confirmButton = {
            GlassButton(
                text = "Copy",
                icon = Icons.Rounded.ContentCopy,
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("EZD Device Info", report))
                    Toast.makeText(context, "Device info copied", Toast.LENGTH_SHORT).show()
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
