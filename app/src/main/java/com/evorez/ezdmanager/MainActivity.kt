package com.evorez.ezdmanager

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.ui.components.EzdAmbientBackground
import com.evorez.ezdmanager.ui.components.FloatingPillNavBar
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.screens.*
import com.evorez.ezdmanager.ui.theme.EzdTheme
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: EzdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}

@Composable
private fun EzdRootApp(vm: EzdViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val daemonStats by vm.daemonStats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val tabs = MainTab.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    var pendingAppPkg by remember { mutableStateOf<String?>(null) }

    val activeTool = state.activeTool

    BackHandler(
        enabled = state.showSearchSheet || state.showDockPicker || state.showDeviceDetails || activeTool != null || pagerState.currentPage != 0
    ) {
        when {
            state.showSearchSheet -> vm.setShowSearchSheet(false)
            state.showDockPicker -> vm.setShowDockPicker(false)
            state.showDeviceDetails -> vm.setShowDeviceDetails(false)
            activeTool != null -> vm.openTool(null)
            pagerState.currentPage != 0 -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    EzdTheme(
        themeMode = state.settings.themeMode,
        themeStyle = state.settings.themeStyle
    ) {
        val pillPos = state.settings.pillPosition

        EzdAmbientBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                val contentPadding = when (pillPos) {
                    PillPosition.BOTTOM -> PaddingValues(bottom = 94.dp)
                    PillPosition.LEFT -> PaddingValues(start = 88.dp, bottom = 16.dp)
                    PillPosition.RIGHT -> PaddingValues(end = 88.dp, bottom = 16.dp)
                }

                AnimatedContent(
                    targetState = activeTool,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(contentPadding),
                    label = "toolVsMain"
                ) { tool ->
                    if (tool != null) {
                        ToolDetailContainer(
                            tool = tool,
                            state = state,
                            vm = vm,
                            onBack = { vm.openTool(null) }
                        )
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val maxContentWidth = if (maxWidth > 720.dp) 680.dp else maxWidth
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = maxContentWidth)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        when (tabs[page]) {
                                            MainTab.HOME -> HomeScreen(
                                                state = state,
                                                onSearchClick = { vm.setShowSearchSheet(true) },
                                                onRefreshClick = { vm.refreshAllHardware() },
                                                onRetryRoot = { vm.autoDetectRoot(forceFresh = true) },
                                                onShowDetails = { vm.setShowDeviceDetails(true) },
                                                onToggleGameMode = { vm.toggleGameMode(it) },
                                                onApplyProfile = { vm.applyProfile(it) },
                                                onSetCpuRange = { p, minK, maxK -> vm.setCpuFrequencyRange(p, minK, maxK) },
                                                onSetCpuGovernor = { p, gov -> vm.setCpuGovernor(p, gov) },
                                                onResetCpuPolicy = { p -> vm.resetCpuPolicy(p) },
                                                onToggleCore = { c, on -> vm.toggleCpuCore(c, on) },
                                                onSetGpuRange = { minM, maxM -> vm.setGpuFrequency(minM, maxM) },
                                                onSetGpuGovernor = { g -> vm.setGpuGovernor(g) },
                                                onResetGpu = { vm.resetGpu() },
                                                onOpenTool = { t -> vm.openTool(t) },
                                                onOpenDndSettings = { vm.openDndPolicySettings() }
                                            )
                                            MainTab.TOOLS -> ToolsScreen(
                                                state = state,
                                                onSearchClick = { vm.setShowSearchSheet(true) },
                                                onRefreshClick = { vm.refreshAllHardware() },
                                                onSelectTool = { t -> vm.openTool(t) }
                                            )
                                            MainTab.APPS -> AppsScreen(
                                                state = state,
                                                daemonStats = daemonStats,
                                                initialSelectedPkg = pendingAppPkg,
                                                onClearInitialPkg = { pendingAppPkg = null },
                                                onSearchClick = { vm.setShowSearchSheet(true) },
                                                onRefreshApps = { vm.loadInstalledApps() },
                                                onOpenUsageAccess = { vm.openUsageStatsSettings() },
                                                onSavePerAppConfig = { cfg -> vm.savePerAppConfig(cfg) },
                                                spoofPresets = vm.allSpoofPresets()
                                            )
                                            MainTab.SETTINGS -> SettingsScreen(
                                                state = state,
                                                daemonStats = daemonStats,
                                                onSearchClick = { vm.setShowSearchSheet(true) },
                                                onSetControlMode = { vm.setControlMode(it) },
                                                onSetThemeMode = { vm.setThemeMode(it) },
                                                onSetThemeStyle = { vm.setThemeStyle(it) },
                                                onSetLanguage = { vm.setLanguage(it) },
                                                onSetPillPosition = { vm.setPillPosition(it) },
                                                onToggleDaemon = { vm.toggleDaemonService(it) },
                                                onBuildDeviceReport = { vm.buildDeviceReportText() }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Frosted Glass Pill Navigation Bar (Bottom / Left / Right)
                if (activeTool == null) {
                    val pillAlignment = when (pillPos) {
                        PillPosition.BOTTOM -> Alignment.BottomCenter
                        PillPosition.LEFT -> Alignment.CenterStart
                        PillPosition.RIGHT -> Alignment.CenterEnd
                    }
                    FloatingPillNavBar(
                        selectedTab = tabs[pagerState.currentPage],
                        pillPosition = pillPos,
                        onSelectTab = { target ->
                            val idx = tabs.indexOf(target)
                            if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                        },
                        onRequestDockMenu = { vm.setShowDockPicker(true) },
                        modifier = Modifier.align(pillAlignment)
                    )
                }
            }

            if (state.showSearchSheet) {
                GlobalSearchDialog(
                    entries = remember(state.installedApps, state.settings.controlMode) { vm.buildSearchIndex() },
                    onDismiss = { vm.setShowSearchSheet(false) },
                    onSelectEntry = { entry ->
                        vm.setShowSearchSheet(false)
                        vm.openTool(entry.targetTool)
                        pendingAppPkg = entry.packageName
                        val idx = tabs.indexOf(entry.targetTab)
                        if (idx >= 0) scope.launch { pagerState.animateScrollToPage(idx) }
                    }
                )
            }

            if (state.showDockPicker) {
                PillDockDialog(
                    current = state.settings.pillPosition,
                    onSelect = { pos ->
                        vm.setPillPosition(pos)
                        vm.setShowDockPicker(false)
                    },
                    onDismiss = { vm.setShowDockPicker(false) }
                )
            }

            if (state.showDeviceDetails) {
                DeviceDetailsDialog(
                    report = vm.buildDeviceReportText(),
                    onDismiss = { vm.setShowDeviceDetails(false) }
                )
            }
        }
    }
}

@Composable
private fun ToolDetailContainer(
    tool: ToolId,
    state: com.evorez.ezdmanager.viewmodel.EzdUiState,
    vm: EzdViewModel,
    onBack: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = visuals.glassSurfaceElevated,
                border = BorderStroke(1.dp, visuals.glassBorder)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.statusBanner.isNotBlank()) {
            GlassCard(cornerRadius = 20.dp, highlightBorder = true) {
                Text(state.statusBanner, style = MaterialTheme.typography.bodySmall)
            }
        }

        when (tool) {
            ToolId.DEVICE_SPOOF -> DeviceSpoofPanel(state, vm)
            ToolId.NETWORK_DNS -> NetworkDnsPanel(state, vm)
            ToolId.BATTERY_CHARGE -> BatteryChargePanel(state, vm)
            ToolId.AUDIO_ENHANCER -> AudioEnhancerPanel(state, vm)
            ToolId.PERFORMANCE_HUD -> PerformanceHudPanel(state, vm)
            ToolId.COLOR_DISPLAY -> ColorDisplayPanel(state, vm)
            ToolId.TOUCH_RESPONSE -> TouchResponsePanel(state, vm)
            ToolId.APP_MANAGER -> AppManagerPanel(state, vm)
            ToolId.PROFILES -> ProfilesPanel(state, vm)
            ToolId.TASK_MONITOR -> TaskMonitorPanel(state, vm)
            ToolId.ROOT_FILES -> RootFileManagerPanel(state, vm)
            ToolId.ROOT_TERMINAL -> RootTerminalPanel(state, vm)
        }
        Spacer(Modifier.height(24.dp))
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
                    placeholder = { Text("Search tools, CPU, themes, apps...") },
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
                                StatusPill(label = item.category, accent = visuals.neonAccent)
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
private fun PillDockDialog(
    current: PillPosition,
    onSelect: (PillPosition) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Floating Pill Location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose where to dock the frosted navigation pill:", style = MaterialTheme.typography.bodySmall)
                PillPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = pos == current,
                        onClick = { onSelect(pos) },
                        label = { Text("${pos.label} Edge") },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
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
            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("EZD Device Info", report))
                    Toast.makeText(context, "Device info copied", Toast.LENGTH_SHORT).show()
                },
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
