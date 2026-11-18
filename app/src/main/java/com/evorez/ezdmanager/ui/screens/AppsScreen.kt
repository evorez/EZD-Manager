package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(
    state: EzdUiState,
    daemonStats: DaemonStats,
    vpnActive: Boolean,
    listState: LazyListState,
    initialSelectedPkg: String?,
    onClearInitialPkg: () -> Unit,
    onToggleIncludeSystem: (Boolean) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onSavePerAppConfig: (PerAppConfig) -> Unit,
    onRequestVpnConsent: () -> Unit,
    onClearCache: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onToggleFreeze: (AppEntry) -> Unit,
    onUninstallApp: (AppEntry) -> Unit,
    spoofPresets: List<SpoofPreset>
) {
    val visuals = LocalEzdVisuals.current
    var query by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(AppSortOption.CONFIGURED) }
    var categoryFilter by remember { mutableStateOf(AppCategoryFilter.ALL) }
    var editingApp by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(initialSelectedPkg, state.installedApps) {
        if (!initialSelectedPkg.isNullOrBlank()) {
            editingApp = state.installedApps.find { it.packageName == initialSelectedPkg }
            onClearInitialPkg()
        }
    }

    val filteredApps = remember(
        state.installedApps,
        state.perAppConfigs,
        state.batteryWhitelist,
        query,
        sortOption,
        categoryFilter
    ) {
        val q = query.trim().lowercase()
        val filtered = state.installedApps.filter { app ->
            val matchesQuery = q.isBlank() ||
                app.label.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q)
            val cfg = state.perAppConfigs[app.packageName]
            val matchesCat = when (categoryFilter) {
                AppCategoryFilter.ALL -> true
                AppCategoryFilter.CONFIGURED -> cfg?.enabled == true || (cfg != null && cfg.dnsMode != PerAppDnsMode.SYSTEM)
                AppCategoryFilter.WHITELISTED -> app.packageName in state.batteryWhitelist
            }
            matchesQuery && matchesCat
        }
        when (sortOption) {
            AppSortOption.NAME -> filtered.sortedBy { it.label.lowercase() }
            AppSortOption.PACKAGE -> filtered.sortedBy { it.packageName.lowercase() }
            AppSortOption.CONFIGURED -> filtered.sortedWith(
                compareByDescending<AppEntry> {
                    val c = state.perAppConfigs[it.packageName]
                    (c?.enabled == true) || (c?.dnsMode != null && c.dnsMode != PerAppDnsMode.SYSTEM)
                }.thenBy { it.label.lowercase() }
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "apps_header_engine") {
            GlassCard(cornerRadius = 26.dp, highlightBorder = daemonStats.perAppMonitorActive || vpnActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Per-App Engine & DNS", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = when {
                                vpnActive -> "Per-App DNS VPN tunnel active · Foreground monitor ready"
                                state.usageAccessGranted -> "Usage Access active · Auto-applies rules & restores on exit"
                                state.effectivePrivilegeMode != PrivilegeMode.BASIC -> "${state.effectivePrivilegeMode.label} focus monitor ready · Grant Usage Access for instant triggers"
                                else -> "Grant Usage Access, Root or Shizuku for foreground app tuning"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onOpenUsageAccess,
                        shape = CircleShape,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(if (state.usageAccessGranted) "Access OK" else "Usage Access")
                    }
                }
                if (state.pendingVpnConsentNeeded) {
                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        text = "Allow Per-App DNS VPN",
                        icon = Icons.Rounded.VpnLock,
                        onClick = onRequestVpnConsent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "apps_search_filter") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search installed apps or packages…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apps_search_field")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppCategoryFilter.entries.forEach { cat ->
                        FilterChip(
                            selected = categoryFilter == cat,
                            onClick = { categoryFilter = cat },
                            label = { Text(cat.label) },
                            shape = CircleShape
                        )
                    }
                    FilterChip(
                        selected = state.includeSystemApps,
                        onClick = { onToggleIncludeSystem(!state.includeSystemApps) },
                        label = { Text("System Apps") },
                        shape = CircleShape,
                        modifier = Modifier.testTag("apps_system_toggle")
                    )
                    AppSortOption.entries.forEach { sort ->
                        AssistChip(
                            onClick = { sortOption = sort },
                            label = { Text(if (sortOption == sort) "Sort: ${sort.label}" else sort.label) },
                            shape = CircleShape
                        )
                    }
                }
            }
        }

        if (state.appsLoading && filteredApps.isEmpty()) {
            item(key = "apps_loading") {
                GlassCard(cornerRadius = 24.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Loading installed apps…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else if (filteredApps.isEmpty()) {
            item(key = "apps_empty") {
                GlassCard(cornerRadius = 24.dp) {
                    Text("No apps match current filter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(
                items = filteredApps,
                key = { it.packageName },
                contentType = { "app_row" }
            ) { app ->
                val cfg = state.perAppConfigs[app.packageName]
                val whitelisted = app.packageName in state.batteryWhitelist
                val active = cfg?.enabled == true || (cfg != null && cfg.dnsMode != PerAppDnsMode.SYSTEM) || whitelisted
                val spoofModel = remember(cfg?.spoofPresetId, spoofPresets) {
                    cfg?.spoofPresetId?.let { spId -> spoofPresets.find { it.id == spId }?.model }
                }

                GlassCard(
                    cornerRadius = 24.dp,
                    highlightBorder = active,
                    onClick = { editingApp = app }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIconItem(packageName = app.packageName, contentDescription = app.label, size = 44.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (active) {
                                Spacer(Modifier.height(4.dp))
                                val tags = buildList {
                                    if (cfg?.enabled == true) add(cfg.profileId)
                                    cfg?.refreshRateHz?.let { add("${it.toInt()}Hz") }
                                    if (spoofModel != null) add(spoofModel)
                                    if (cfg != null && cfg.dnsMode != PerAppDnsMode.SYSTEM) add(cfg.dnsMode.label)
                                    if (whitelisted) add("Whitelisted")
                                    if (cfg?.batteryRestricted == true) add("Restricted")
                                }.joinToString(" · ")
                                StatusPill(label = tags, accent = visuals.accent)
                            }
                        }
                        GlowIcon(Icons.Rounded.Tune, contentDescription = "Configure ${app.label}", size = 20.dp)
                    }
                }
            }
        }
    }

    editingApp?.let { app ->
        val existing = state.perAppConfigs[app.packageName] ?: PerAppConfig(
            packageName = app.packageName,
            whitelisted = app.packageName in state.batteryWhitelist
        )
        PerAppDetailSheet(
            app = app,
            initial = existing,
            displayModes = state.displayState.supportedModes,
            spoofPresets = spoofPresets,
            profiles = remember(state.customProfiles) { ProfileRepository.builtInProfiles() + state.customProfiles },
            gpuGovernors = state.gpuState.availableGovernors,
            canManageApps = state.effectivePrivilegeMode != PrivilegeMode.BASIC,
            hasRoot = state.effectivePrivilegeMode == PrivilegeMode.ROOT,
            onDismiss = { editingApp = null },
            onSave = { updated ->
                onSavePerAppConfig(updated)
                editingApp = null
            },
            onClearCache = { onClearCache(app.packageName) },
            onForceStop = { onForceStop(app.packageName) },
            onToggleFreeze = { onToggleFreeze(app) },
            onUninstall = {
                onUninstallApp(app)
                editingApp = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PerAppDetailSheet(
    app: AppEntry,
    initial: PerAppConfig,
    displayModes: List<DisplayModeInfo>,
    spoofPresets: List<SpoofPreset>,
    profiles: List<ProfileConfig>,
    gpuGovernors: List<String>,
    canManageApps: Boolean,
    hasRoot: Boolean,
    onDismiss: () -> Unit,
    onSave: (PerAppConfig) -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
    onToggleFreeze: () -> Unit,
    onUninstall: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    var enabled by remember { mutableStateOf(initial.enabled) }
    var profileId by remember { mutableStateOf(initial.profileId) }
    var gpuProf by remember { mutableStateOf(initial.gpuProfile) }
    var refreshHz by remember { mutableStateOf(initial.refreshRateHz) }
    var spoofId by remember { mutableStateOf(initial.spoofPresetId) }
    var dnsMode by remember { mutableStateOf(initial.dnsMode) }
    var dnsServer by remember { mutableStateOf(initial.customDnsServer) }
    var batteryRestricted by remember { mutableStateOf(initial.batteryRestricted) }
    var whitelisted by remember { mutableStateOf(initial.whitelisted) }
    var touchBoost by remember { mutableStateOf(initial.touchBoost) }
    var disableHwo by remember { mutableStateOf(initial.disableHwOverlays) }

    val uniqueHz = remember(displayModes) { displayModes.map { it.refreshRateHz }.distinct().sorted() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = visuals.solidSurfaceElevated,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Hero App Header Card
            GlassCard(cornerRadius = 24.dp, highlightBorder = enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppIconItem(app.packageName, app.label, size = 50.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.versionName.isNotBlank()) {
                            Text(
                                text = "v${app.versionName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMono,
                                color = visuals.accent
                            )
                        }
                    }
                    StatusPill(
                        label = if (enabled) "Auto Active" else "Inactive",
                        accent = if (enabled) visuals.successColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (canManageApps) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onClearCache, shape = CircleShape) { Text("Clear Cache") }
                        OutlinedButton(onClick = onForceStop, shape = CircleShape) { Text("Force Stop") }
                        OutlinedButton(onClick = onToggleFreeze, shape = CircleShape) { Text(if (app.isDisabled) "Unfreeze" else "Freeze") }
                        OutlinedButton(onClick = onUninstall, shape = CircleShape) { Text("Uninstall") }
                    }
                }
            }

            // Scrollable Tuning Sections
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 2. Master Activation & Battery Guard Card
                GlassCard(cornerRadius = 24.dp) {
                    SheetToggle("Enable Per-App Auto Tuning", "Auto-applies settings when foregrounded", enabled) { enabled = it }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SheetToggle("Battery Whitelist", "Protect from memory trim & background kill", whitelisted) {
                        whitelisted = it
                        if (it) batteryRestricted = false
                    }
                    if (!whitelisted) {
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SheetToggle("Restrict Background Battery", "Limit background wakeups", batteryRestricted) { batteryRestricted = it }
                    }
                }

                // 3. Performance & Display Section (ALL built-in + custom profiles shown)
                GlassCard(cornerRadius = 24.dp) {
                    SectionHeader("PERFORMANCE & DISPLAY")
                    Spacer(Modifier.height(8.dp))
                    Text("Tuning Profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        profiles.forEach { p ->
                            FilterChip(selected = profileId == p.id, onClick = { profileId = p.id }, label = { Text(p.name) }, shape = CircleShape)
                        }
                    }

                    if (gpuGovernors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("GPU Governor", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = gpuProf.isBlank(), onClick = { gpuProf = "" }, label = { Text("Profile") }, shape = CircleShape)
                            gpuGovernors.forEach { g ->
                                FilterChip(selected = gpuProf == g, onClick = { gpuProf = g }, label = { Text(g) }, shape = CircleShape)
                            }
                        }
                    }

                    if (uniqueHz.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Target Refresh Rate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = refreshHz == null, onClick = { refreshHz = null }, label = { Text("Auto") }, shape = CircleShape)
                            uniqueHz.forEach { hz ->
                                FilterChip(selected = refreshHz == hz, onClick = { refreshHz = hz }, label = { Text("${hz.toInt()}Hz") }, shape = CircleShape)
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SheetToggle("Touch & HW Overlay Boost", "Enables touch boost + GPU compositing", touchBoost && disableHwo) { on ->
                        touchBoost = on
                        disableHwo = on
                    }
                }

                // 4. Per-App DNS Section with DNS Brand Logos
                GlassCard(cornerRadius = 24.dp) {
                    SectionHeader("PER-APP DNS ROUTING (LOCAL VPN)")
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerAppDnsMode.entries.forEach { m ->
                            FilterChip(selected = dnsMode == m, onClick = { dnsMode = m }, label = { Text(m.label) }, shape = CircleShape)
                        }
                    }
                    if (dnsMode != PerAppDnsMode.SYSTEM) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.evorez.ezdmanager.hardware.SystemToolsRepository.DNS_PRESETS
                                .filter { it.id != "off" }
                                .forEach { preset ->
                                    val targetHost = when (dnsMode) {
                                        PerAppDnsMode.DOH -> preset.dohUrl
                                        PerAppDnsMode.DOT -> preset.hostname
                                        else -> preset.primaryIp
                                    }
                                    FilterChip(
                                        selected = dnsServer.equals(targetHost, ignoreCase = true),
                                        onClick = { dnsServer = targetHost },
                                        leadingIcon = { DnsProviderLogo(preset.id, size = 18.dp) },
                                        label = { Text(preset.name) },
                                        shape = CircleShape
                                    )
                                }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dnsServer,
                            onValueChange = { dnsServer = it },
                            label = {
                                Text(
                                    when (dnsMode) {
                                        PerAppDnsMode.DOH -> "DoH URL (e.g. cloudflare / dns.google)"
                                        PerAppDnsMode.DOT -> "DoT Hostname (1dot1dot1dot1.cloudflare-dns.com)"
                                        else -> "Custom DNS IP (e.g. 1.1.1.1)"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 5. Device Spoof Section (shows ALL built-in + user-created custom presets without truncation!)
                GlassCard(cornerRadius = 24.dp) {
                    SectionHeader(
                        "PER-APP DEVICE SPOOF",
                        if (hasRoot) "All built-in & custom spoof presets (${spoofPresets.size})"
                        else "Requires Root / resetprop when app launches (${spoofPresets.size} presets)"
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(selected = spoofId == null, onClick = { spoofId = null }, label = { Text("Off") }, shape = CircleShape)
                        spoofPresets.forEach { sp ->
                            FilterChip(
                                selected = spoofId == sp.id,
                                onClick = { spoofId = sp.id },
                                label = {
                                    Text(if (sp.isCustom) "${sp.name} (${sp.model})" else sp.model)
                                },
                                shape = CircleShape
                            )
                        }
                    }
                }
            }

            // Sticky Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(0.4f)
                        .defaultMinSize(minHeight = 48.dp)
                ) { Text("Cancel") }
                GlassButton(
                    text = "Save Tuning",
                    icon = Icons.Rounded.Check,
                    onClick = {
                        onSave(
                            PerAppConfig(
                                packageName = app.packageName,
                                enabled = enabled,
                                profileId = profileId,
                                gpuProfile = gpuProf,
                                refreshRateHz = refreshHz,
                                spoofPresetId = spoofId,
                                dnsMode = dnsMode,
                                customDnsServer = dnsServer.ifBlank { "1.1.1.1" }.trim(),
                                batteryRestricted = batteryRestricted,
                                whitelisted = whitelisted,
                                touchBoost = touchBoost,
                                disableHwOverlays = disableHwo
                            )
                        )
                    },
                    modifier = Modifier.weight(0.6f)
                )
            }
        }
    }
}

@Composable
private fun SheetToggle(label: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
