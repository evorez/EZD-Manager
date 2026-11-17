package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import kotlin.math.roundToInt

@Composable
fun ColorDisplayPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val disp = state.displayState
    var brightSlider by remember(disp.brightness) { mutableFloatStateOf(disp.brightness.toFloat()) }
    var satSlider by remember(disp.saturationValue) { mutableFloatStateOf(disp.saturationValue) }
    val uniqueHz = remember(disp.supportedModes) { disp.supportedModes.map { it.refreshRateHz }.distinct().sorted() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Refresh Rate & Display Modes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Active: ${disp.currentRefreshHz} Hz · Peak: ${disp.peakRefreshHz?.toInt() ?: "Auto"} Hz",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = visuals.neonAccent
                    )
                }
                OutlinedButton(
                    onClick = { vm.resetRefreshRate() },
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Reset") }
            }
            Spacer(Modifier.height(10.dp))
            if (uniqueHz.isEmpty()) {
                Text("No selectable display modes reported.", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uniqueHz.forEach { hz ->
                        FilterChip(
                            selected = kotlin.math.abs(disp.currentRefreshHz - hz) < 1f,
                            onClick = { vm.setRefreshRate(hz) },
                            label = { Text("${hz.toInt()} Hz") },
                            shape = CircleShape
                        )
                    }
                }
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Brightness (${(brightSlider.roundToInt() * 100) / 255}%)", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Auto", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = disp.autoBrightness,
                        onCheckedChange = { vm.setBrightness(brightSlider.roundToInt(), it) }
                    )
                }
            }
            Slider(
                value = brightSlider,
                onValueChange = { brightSlider = it },
                onValueChangeFinished = { vm.setBrightness(brightSlider.roundToInt(), false) },
                valueRange = 5f..255f
            )
        }

        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Screen Saturation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (disp.kcalSupported) "Kernel KCAL + SurfaceFlinger" else "SurfaceFlinger Display Saturation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${String.format("%.2f", satSlider)}x",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMono,
                    color = visuals.neonAccent
                )
            }
            Slider(
                value = satSlider,
                onValueChange = { satSlider = it },
                onValueChangeFinished = { vm.setDisplaySaturation(satSlider) },
                valueRange = 0.5f..1.8f
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f to "Natural", 1.0f to "Default", 1.25f to "Vivid", 1.50f to "Boost").forEach { (v, lbl) ->
                    FilterChip(
                        selected = kotlin.math.abs(satSlider - v) < 0.05f,
                        onClick = {
                            satSlider = v
                            vm.setDisplaySaturation(v)
                        },
                        label = { Text(lbl) },
                        shape = CircleShape
                    )
                }
            }
            if (disp.cabcSupported) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Content Adaptive Backlight (CABC)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = disp.cabcEnabled, onCheckedChange = { vm.setCabc(it) })
                }
            }
        }
    }
}

@Composable
fun TouchResponsePanel(state: EzdUiState, vm: EzdViewModel) {
    val touch = state.touchState
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (touch.statusNote.isNotBlank()) {
            GlassCard(cornerRadius = 22.dp) {
                Text(touch.statusNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Text("Kernel Touch Controls", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            TouchRow(
                title = "Touch Sampling Rate",
                subtitle = if (touch.samplingNodePath != null) "Current: ${touch.samplingValue} (${touch.samplingNodePath})" else "Not supported on this device.",
                supported = touch.samplingNodePath != null
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("120", "240", "360").forEach { rate ->
                        OutlinedButton(
                            onClick = { touch.samplingNodePath?.let { vm.setTouchControl(it, rate, "Sampling rate") } },
                            shape = CircleShape
                        ) { Text("${rate}Hz") }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TouchRow(
                title = "Gaming Touch Mode",
                subtitle = touch.gamingModePath ?: "Not supported on this device.",
                supported = touch.gamingModePath != null
            ) {
                Switch(
                    checked = touch.gamingModeEnabled,
                    onCheckedChange = { enabled ->
                        touch.gamingModePath?.let { vm.setTouchControl(it, if (enabled) "1" else "0", "Gaming touch mode") }
                    }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            TouchRow(
                title = "Kernel Touch Boost",
                subtitle = touch.touchBoostPath ?: "Not supported on this device.",
                supported = touch.touchBoostPath != null
            ) {
                Switch(
                    checked = touch.touchBoostEnabled,
                    onCheckedChange = { enabled ->
                        touch.touchBoostPath?.let { vm.setTouchControl(it, if (enabled) "1" else "0", "Touch boost") }
                    }
                )
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disable HW Overlays", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Forces GPU compositing via SurfaceFlinger to reduce frame latency spikes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = touch.hwOverlaysDisabled,
                    onCheckedChange = { vm.setDisableHwOverlays(it) },
                    enabled = state.rootStatus == RootStatus.GRANTED && !state.busy
                )
            }
        }
    }
}

@Composable
private fun TouchRow(
    title: String,
    subtitle: String,
    supported: Boolean,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (supported) control()
    }
}

@Composable
fun AppManagerPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var uninstallTarget by remember { mutableStateOf<AppEntry?>(null) }

    val list = remember(state.installedApps, search, showSystem) {
        val q = search.trim().lowercase()
        state.installedApps.filter { app ->
            (showSystem || !app.isSystemApp) &&
                (q.isBlank() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search apps to backup, freeze, uninstall...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showSystem, onClick = { showSystem = false }, label = { Text("User Apps") }, shape = CircleShape)
            FilterChip(selected = showSystem, onClick = { showSystem = true }, label = { Text("Include System Apps") }, shape = CircleShape)
        }

        list.take(50).forEach { app ->
            GlassCard(cornerRadius = 24.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (app.isSystemApp) {
                        StatusPill("SYSTEM", visuals.warningColor)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { vm.clearAppCache(app.packageName) }, shape = CircleShape) { Text("Clear Cache") }
                    OutlinedButton(onClick = { vm.forceStopApp(app.packageName) }, shape = CircleShape) { Text("Force Stop") }
                    OutlinedButton(onClick = { vm.toggleFreezeApp(app) }, shape = CircleShape) {
                        Text(if (app.isDisabled) "Unfreeze" else "Freeze")
                    }
                    OutlinedButton(onClick = { vm.backupAppAndData(app.packageName) }, shape = CircleShape) {
                        Text("Backup Data")
                    }
                    Button(
                        onClick = { uninstallTarget = app },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = CircleShape
                    ) { Text("Uninstall") }
                }
            }
        }
    }

    uninstallTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            shape = RoundedCornerShape(26.dp),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (target.isSystemApp) MaterialTheme.colorScheme.error else visuals.warningColor
                )
            },
            title = {
                Text(if (target.isSystemApp) "System App Uninstall Warning!" else "Uninstall ${target.label}?")
            },
            text = {
                Text(
                    if (target.isSystemApp) {
                        "${target.label} (${target.packageName}) is a SYSTEM app. Removing system packages can cause bootloops, crashes, or break core device features. Proceed only if you know it is safe."
                    } else {
                        "Uninstall ${target.label} (${target.packageName})?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.uninstallApp(target)
                        uninstallTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = CircleShape
                ) {
                    Text(if (target.isSystemApp) "Uninstall Anyway" else "Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProfilesPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val all = remember(state.customProfiles) { ProfileRepository.builtInProfiles() + state.customProfiles }
    var editingProfile by remember { mutableStateOf<ProfileConfig?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    editingProfile = ProfileConfig(
                        id = "custom_${System.currentTimeMillis()}",
                        name = "Custom Profile",
                        description = "User-defined CPU/GPU/Display profile",
                        isBuiltIn = false
                    )
                },
                shape = CircleShape,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("New Custom")
            }
            OutlinedButton(
                onClick = { vm.resetCustomProfiles() },
                shape = CircleShape,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) { Text("Reset Defaults") }
        }

        all.forEach { profile ->
            val active = state.settings.activeProfileId == profile.id
            GlassCard(cornerRadius = 24.dp, highlightBorder = active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Gov: ${profile.cpuGovernor} · Range ${profile.cpuMinScalePercent}%–${profile.cpuMaxScalePercent}% · Hz: ${profile.targetRefreshHz?.toInt() ?: "Auto"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMono,
                            color = visuals.neonAccent
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.applyProfile(profile) },
                        enabled = !state.busy,
                        shape = CircleShape
                    ) { Text(if (active) "Active" else "Apply") }

                    if (!profile.isBuiltIn) {
                        OutlinedButton(onClick = { editingProfile = profile }, shape = CircleShape) { Text("Edit") }
                        OutlinedButton(onClick = { vm.deleteCustomProfile(profile.id) }, shape = CircleShape) { Text("Delete") }
                    }
                }
            }
        }
    }

    editingProfile?.let { initial ->
        var name by remember { mutableStateOf(initial.name) }
        var gov by remember { mutableStateOf(initial.cpuGovernor) }
        var minPct by remember { mutableFloatStateOf(initial.cpuMinScalePercent.toFloat()) }
        var maxPct by remember { mutableFloatStateOf(initial.cpuMaxScalePercent.toFloat()) }
        var touchBoost by remember { mutableStateOf(initial.touchBoost) }

        AlertDialog(
            onDismissRequest = { editingProfile = null },
            shape = RoundedCornerShape(26.dp),
            title = { Text("Save / Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile Name") }, singleLine = true)
                    OutlinedTextField(value = gov, onValueChange = { gov = it }, label = { Text("CPU Governor") }, singleLine = true)
                    Text("CPU Min %: ${minPct.roundToInt()}%")
                    Slider(value = minPct, onValueChange = { minPct = it.coerceAtMost(maxPct) }, valueRange = 5f..100f)
                    Text("CPU Max %: ${maxPct.roundToInt()}%")
                    Slider(value = maxPct, onValueChange = { maxPct = it.coerceAtLeast(minPct) }, valueRange = 10f..100f)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Touch Boost")
                        Switch(checked = touchBoost, onCheckedChange = { touchBoost = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.saveCustomProfile(
                            initial.copy(
                                name = name.ifBlank { "Custom Profile" }.trim(),
                                cpuGovernor = gov.ifBlank { "schedutil" }.trim(),
                                cpuMinScalePercent = minPct.roundToInt(),
                                cpuMaxScalePercent = maxPct.roundToInt(),
                                touchBoost = touchBoost
                            )
                        )
                        editingProfile = null
                    },
                    shape = CircleShape
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingProfile = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun TaskMonitorPanel(state: EzdUiState, vm: EzdViewModel) {
    LaunchedEffect(Unit) { vm.refreshProcesses() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { vm.refreshProcesses() }, shape = CircleShape) { Text("Refresh Running PIDs") }
        if (state.processes.isEmpty()) {
            GlassCard(cornerRadius = 22.dp) {
                Text("No process snapshot yet (requires root).", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            state.processes.forEach { proc ->
                GlassCard(cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                text = "PID ${proc.pid} · ${proc.user} · RSS ${proc.rssKb / 1024} MB · CPU ${proc.cpuPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.killProcess(proc.pid) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Kill PID ${proc.pid}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RootFileManagerPanel(state: EzdUiState, vm: EzdViewModel) {
    LaunchedEffect(Unit) { vm.browseDirectory(state.currentFileDir) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(cornerRadius = 22.dp) {
            Text(state.currentFileDir, style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMono)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val parent = state.currentFileDir.substringBeforeLast('/').ifBlank { "/" }
                        vm.browseDirectory(parent)
                    },
                    shape = CircleShape
                ) { Text("Up") }
                OutlinedButton(onClick = { vm.browseDirectory("/sys/devices/system/cpu") }, shape = CircleShape) { Text("/sys/cpu") }
                OutlinedButton(onClick = { vm.browseDirectory("/data/adb") }, shape = CircleShape) { Text("/data/adb") }
            }
        }
        state.fileItems.forEach { item ->
            GlassCard(cornerRadius = 20.dp, onClick = { vm.openFilePreview(item) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                            contentDescription = null
                        )
                        Column {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text("${item.permissions} · ${item.sizeText}", style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono)
                        }
                    }
                }
            }
        }
    }

    if (state.filePreviewTitle != null && state.filePreviewContent != null) {
        AlertDialog(
            onDismissRequest = { vm.closeFilePreview() },
            shape = RoundedCornerShape(24.dp),
            title = { Text(state.filePreviewTitle) },
            text = {
                Text(state.filePreviewContent, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono)
            },
            confirmButton = {
                TextButton(onClick = { vm.closeFilePreview() }) { Text("Close") }
            }
        )
    }
}

@Composable
fun RootTerminalPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    var command by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Text(
                text = if (state.rootStatus == RootStatus.GRANTED) {
                    "Root Daemon Shell (uid=0, persistent worker)"
                } else {
                    "Unprivileged sh Shell (root not granted)"
                },
                style = MaterialTheme.typography.titleSmall,
                color = visuals.neonAccent
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("id", "uname -a", "getprop ro.product.model", "ls /sys/devices/system/cpu/cpufreq", "clear").forEach { quick ->
                    AssistChip(onClick = { vm.runTerminalCommand(quick) }, label = { Text(quick) }, shape = CircleShape)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    placeholder = { Text("Enter shell command...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_input")
                )
                Button(
                    onClick = {
                        vm.runTerminalCommand(command)
                        command = ""
                    },
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Run") }
            }
        }

        state.terminalHistory.reversed().forEach { entry ->
            GlassCard(cornerRadius = 20.dp) {
                Text(
                    text = "$ ${entry.command}  [exit ${entry.exitCode}]",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = visuals.neonAccent
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
