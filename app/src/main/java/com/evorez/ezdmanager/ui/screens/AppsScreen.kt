package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.NeonIconBadge
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.components.TopBrandBar
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState

@Composable
fun AppsScreen(
    state: EzdUiState,
    daemonStats: DaemonStats,
    initialSelectedPkg: String?,
    onClearInitialPkg: () -> Unit,
    onSearchClick: () -> Unit,
    onRefreshApps: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onSavePerAppConfig: (PerAppConfig) -> Unit,
    spoofPresets: List<SpoofPreset>
) {
    val visuals = LocalEzdVisuals.current
    var query by remember { mutableStateOf("") }
    var editingApp by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(initialSelectedPkg, state.installedApps) {
        if (!initialSelectedPkg.isNullOrBlank()) {
            editingApp = state.installedApps.find { it.packageName == initialSelectedPkg }
            onClearInitialPkg()
        }
    }

    val filteredApps = remember(state.installedApps, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.installedApps
        else state.installedApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopBrandBar(
            title = "App Tuning",
            subtitle = "Refresh rate, spoof, governor, priority & touch boost",
            showLogo = false,
            onSearchClick = onSearchClick,
            onRefreshClick = onRefreshApps
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Per-App Auto-Apply Daemon Card
            GlassCard(cornerRadius = 26.dp, highlightBorder = daemonStats.perAppMonitorActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Per-App Auto-Apply Daemon",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                state.usageAccessGranted -> "Usage Access active · Daemon watches app launches and restores on exit."
                                state.rootStatus == RootStatus.GRANTED -> "Using Root window focus monitor · Grant Usage Access for faster detection."
                                else -> "Grant Usage Access or Root so EZD Manager detects foreground app switches."
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
                        Text(if (state.usageAccessGranted) "Granted" else "Usage Access")
                    }
                }
                if (daemonStats.lastOutcome.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    StatusPill(label = daemonStats.lastOutcome, accent = visuals.neonSecondary)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search installed apps...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apps_search_field")
            )

            if (filteredApps.isEmpty()) {
                GlassCard(cornerRadius = 24.dp) {
                    Text(
                        text = "No matching apps found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                filteredApps.take(80).forEach { app ->
                    val cfg = state.perAppConfigs[app.packageName]
                    val active = cfg?.enabled == true
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
                            NeonIconBadge(
                                icon = if (app.isSystemApp) Icons.Rounded.Android else Icons.Rounded.Apps,
                                contentDescription = app.label,
                                accent = if (active) visuals.neonAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (active && cfg != null) {
                                    Spacer(Modifier.height(4.dp))
                                    val summary = buildList {
                                        add(cfg.profileId)
                                        cfg.refreshRateHz?.let { add("${it.toInt()}Hz") }
                                        if (cfg.touchBoost) add("TouchBoost")
                                        if (cfg.disableHwOverlays) add("NoHWO")
                                        if (cfg.bypassCharging) add("Bypass")
                                    }.joinToString(" · ")
                                    StatusPill(label = summary, accent = visuals.neonAccent)
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Configure ${app.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    editingApp?.let { app ->
        val existing = state.perAppConfigs[app.packageName] ?: PerAppConfig(packageName = app.packageName)
        PerAppConfigDialog(
            app = app,
            initial = existing,
            displayModes = state.displayState.supportedModes,
            spoofPresets = spoofPresets,
            profiles = ProfileRepository.builtInProfiles() + state.customProfiles,
            availableGovernors = state.cpuPolicies.firstOrNull()?.governors.orEmpty(),
            onDismiss = { editingApp = null },
            onSave = { updated ->
                onSavePerAppConfig(updated)
                editingApp = null
            }
        )
    }
}

@Composable
private fun PerAppConfigDialog(
    app: AppEntry,
    initial: PerAppConfig,
    displayModes: List<DisplayModeInfo>,
    spoofPresets: List<SpoofPreset>,
    profiles: List<ProfileConfig>,
    availableGovernors: List<String>,
    onDismiss: () -> Unit,
    onSave: (PerAppConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(initial.enabled) }
    var profileId by remember { mutableStateOf(initial.profileId) }
    var refreshHz by remember { mutableStateOf(initial.refreshRateHz) }
    var spoofId by remember { mutableStateOf(initial.spoofPresetId) }
    var governor by remember { mutableStateOf(initial.governorOverride) }
    var priorityNice by remember { mutableIntStateOf(initial.priorityNice) }
    var perfBoost by remember { mutableStateOf(initial.performanceBoost) }
    var killBg by remember { mutableStateOf(initial.killBackgroundApps) }
    var bypassCharge by remember { mutableStateOf(initial.bypassCharging) }
    var touchBoost by remember { mutableStateOf(initial.touchBoost) }
    var disableHwo by remember { mutableStateOf(initial.disableHwOverlays) }

    val uniqueHz = remember(displayModes) {
        displayModes.map { it.refreshRateHz }.distinct().sorted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToggleRow("Enable Per-App Auto Tuning", enabled) { enabled = it }

                // Profile selector
                Text("Performance Profile", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    profiles.take(4).forEach { p ->
                        FilterChip(
                            selected = profileId == p.id,
                            onClick = { profileId = p.id },
                            label = { Text(p.name) },
                            shape = CircleShape
                        )
                    }
                }

                // Refresh Rate selector
                if (uniqueHz.isNotEmpty()) {
                    Text("Target Refresh Rate", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = refreshHz == null,
                            onClick = { refreshHz = null },
                            label = { Text("Auto") },
                            shape = CircleShape
                        )
                        uniqueHz.forEach { hz ->
                            FilterChip(
                                selected = refreshHz == hz,
                                onClick = { refreshHz = hz },
                                label = { Text("${hz.toInt()}Hz") },
                                shape = CircleShape
                            )
                        }
                    }
                }

                // Governor override
                if (availableGovernors.isNotEmpty()) {
                    Text("Governor Override", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = governor == null,
                            onClick = { governor = null },
                            label = { Text("Profile") },
                            shape = CircleShape
                        )
                        availableGovernors.take(4).forEach { g ->
                            FilterChip(
                                selected = governor == g,
                                onClick = { governor = g },
                                label = { Text(g) },
                                shape = CircleShape
                            )
                        }
                    }
                }

                // Spoof preset
                Text("Per-App Spoof Tag", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = spoofId == null,
                        onClick = { spoofId = null },
                        label = { Text("Off") },
                        shape = CircleShape
                    )
                    spoofPresets.take(4).forEach { sp ->
                        FilterChip(
                            selected = spoofId == sp.id,
                            onClick = { spoofId = sp.id },
                            label = { Text(sp.model) },
                            shape = CircleShape
                        )
                    }
                }

                // Priority Nice
                Text("App Process Priority (nice: $priorityNice)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Normal", -5 to "High", -10 to "Max").forEach { (niceVal, label) ->
                        FilterChip(
                            selected = priorityNice == niceVal,
                            onClick = { priorityNice = niceVal },
                            label = { Text(label) },
                            shape = CircleShape
                        )
                    }
                }

                ToggleRow("App Performance Boost", perfBoost) { perfBoost = it }
                ToggleRow("Kill Background Apps on Launch", killBg) { killBg = it }
                ToggleRow("Bypass Charging While Active", bypassCharge) { bypassCharge = it }
                ToggleRow("Touch Boost (Game Touch)", touchBoost) { touchBoost = it }
                ToggleRow("Disable HW Overlays (GPU Compose)", disableHwo) { disableHwo = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        PerAppConfig(
                            packageName = app.packageName,
                            enabled = enabled,
                            profileId = profileId,
                            refreshRateHz = refreshHz,
                            spoofPresetId = spoofId,
                            governorOverride = governor,
                            priorityNice = priorityNice,
                            performanceBoost = perfBoost,
                            killBackgroundApps = killBg,
                            bypassCharging = bypassCharge,
                            touchBoost = touchBoost,
                            disableHwOverlays = disableHwo
                        )
                    )
                },
                shape = CircleShape
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
