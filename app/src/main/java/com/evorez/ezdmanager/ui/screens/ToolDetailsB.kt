package com.evorez.ezdmanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.backup.BackupExportOptions
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// 1. DEVICE SPOOF PANEL (Add, Edit, Rename, Delete, Apply, Reset)
// ============================================================================

@Composable
fun DeviceSpoofPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val spoof = state.spoofState
    val allPresets = remember(state.customSpoofPresets) { vm.allSpoofPresets() }
    var editingPreset by remember { mutableStateOf<SpoofPreset?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current Runtime Identity", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${spoof.currentBrand} · ${spoof.currentModel} (${spoof.currentDevice})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = visuals.subtleRedAccent
                    )
                    Text(
                        text = "Manufacturer: ${spoof.currentManufacturer}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { vm.resetSpoof() },
                    enabled = state.rootStatus == RootStatus.GRANTED && !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Reset") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (spoof.resetpropAvailable) {
                    "Uses resetprop to update runtime device identity properties safely without altering /system."
                } else {
                    "Requires root + Magisk/KernelSU/APatch resetprop binary. Apps already running may cache Build fields until restarted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Add Spoof Preset",
                icon = Icons.Rounded.Add,
                onClick = {
                    editingPreset = SpoofPreset(
                        id = "custom_spoof_${System.currentTimeMillis()}",
                        name = "Custom Device",
                        manufacturer = spoof.currentManufacturer.ifBlank { "Google" },
                        brand = spoof.currentBrand.ifBlank { "google" },
                        model = spoof.currentModel.ifBlank { "Pixel 9 Pro" },
                        device = spoof.currentDevice.ifBlank { "caiman" },
                        isCustom = true
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        allPresets.forEach { preset ->
            val isCurrent = spoof.currentModel == preset.model && spoof.currentDevice == preset.device
            GlassCard(cornerRadius = 24.dp, highlightBorder = isCurrent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(preset.name, style = MaterialTheme.typography.titleMedium)
                            if (preset.isCustom) {
                                StatusPill("Custom", visuals.subtleRedAccent)
                            }
                        }
                        Text(
                            text = "${preset.manufacturer} / ${preset.brand} · ${preset.model} (${preset.device})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = if (isCurrent) "Active" else "Apply",
                        onClick = { vm.applySpoofPreset(preset) },
                        enabled = state.rootStatus == RootStatus.GRANTED && !state.busy,
                        accent = !isCurrent
                    )
                    OutlinedButton(
                        onClick = {
                            editingPreset = if (preset.isCustom) preset else preset.copy(
                                id = "custom_spoof_${System.currentTimeMillis()}",
                                name = "${preset.name} Copy",
                                isCustom = true
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) { Text(if (preset.isCustom) "Edit / Rename" else "Duplicate") }

                    if (preset.isCustom) {
                        OutlinedButton(
                            onClick = { vm.deleteCustomSpoofPreset(preset.id) },
                            shape = CircleShape,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }

    editingPreset?.let { initial ->
        var name by remember { mutableStateOf(initial.name) }
        var manufacturer by remember { mutableStateOf(initial.manufacturer) }
        var brand by remember { mutableStateOf(initial.brand) }
        var model by remember { mutableStateOf(initial.model) }
        var device by remember { mutableStateOf(initial.device) }

        GlassDialog(
            title = if (initial.isCustom) "Save / Edit Spoof Preset" else "New Spoof Preset",
            onDismiss = { editingPreset = null },
            confirmText = "Save Preset",
            onConfirm = {
                vm.upsertCustomSpoofPreset(
                    initial.copy(
                        name = name.ifBlank { "Custom Device" }.trim(),
                        manufacturer = manufacturer.ifBlank { "Google" }.trim(),
                        brand = brand.ifBlank { "google" }.trim(),
                        model = model.ifBlank { "Pixel 9" }.trim(),
                        device = device.ifBlank { "tokay" }.trim(),
                        isCustom = true
                    )
                )
                editingPreset = null
            }
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, singleLine = true)
            OutlinedTextField(value = manufacturer, onValueChange = { manufacturer = it }, label = { Text("Manufacturer") }, singleLine = true)
            OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, singleLine = true)
            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true)
            OutlinedTextField(value = device, onValueChange = { device = it }, label = { Text("Device Codename") }, singleLine = true)
        }
    }
}

// ============================================================================
// 2. PROFILES PANEL (Balanced, Performance, Powersave, Custom)
// ============================================================================

@Composable
fun ProfilesPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val all = remember(state.customProfiles) { ProfileRepository.builtInProfiles() + state.customProfiles }
    var editingProfile by remember { mutableStateOf<ProfileConfig?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "New Custom",
                icon = Icons.Rounded.Add,
                onClick = {
                    editingProfile = ProfileConfig(
                        id = "custom_${System.currentTimeMillis()}",
                        name = "Custom Profile",
                        description = "User-defined CPU/GPU/Display profile",
                        isBuiltIn = false
                    )
                },
                modifier = Modifier.weight(1f)
            )
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
                            color = visuals.subtleRedAccent
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = if (active) "Active" else "Apply",
                        onClick = { vm.applyProfile(profile) },
                        enabled = !state.busy,
                        accent = !active
                    )
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

        GlassDialog(
            title = "Save / Edit Profile",
            onDismiss = { editingProfile = null },
            confirmText = "Save",
            onConfirm = {
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
            }
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Profile Name") }, singleLine = true)
            OutlinedTextField(value = gov, onValueChange = { gov = it }, label = { Text("CPU Governor") }, singleLine = true)
            Text("CPU Min %: ${minPct.roundToInt()}%")
            GlassSlider(value = minPct, onValueChange = { minPct = it.coerceAtMost(maxPct) }, valueRange = 5f..100f)
            Text("CPU Max %: ${maxPct.roundToInt()}%")
            GlassSlider(value = maxPct, onValueChange = { maxPct = it.coerceAtLeast(minPct) }, valueRange = 10f..100f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Touch Boost")
                Switch(checked = touchBoost, onCheckedChange = { touchBoost = it })
            }
        }
    }
}

// ============================================================================
// 3. EZD MANAGER BACKUP & RESTORE PANEL (AES-256-GCM + SHA-256 + SAF)
// ============================================================================

@Composable
fun EzdBackupPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }

    var includeNetworkConfig by remember { mutableStateOf(true) }
    var includeRootBaselines by remember { mutableStateOf(true) }

    var restoreEzdConfig by remember { mutableStateOf(true) }
    var restoreNetworkConfig by remember { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportEncryptedBackup(
                uri = uri,
                options = BackupExportOptions(
                    includeEzdConfig = true,
                    selectedApps = emptySet(),
                    includeMessagesAndCallLogs = false,
                    includeNetworkConfig = includeNetworkConfig,
                    includeBluetoothConfig = false,
                    includeRootBaselines = includeRootBaselines,
                    passphrase = passphrase
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.inspectBackupArchive(uri, passphrase)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Text("EZD Manager Encrypted Backup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Backs up EZD themes, dock layout, profiles, per-app rules, spoof presets, and whitelist using AES-256-GCM + SHA-256.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            PassphraseField(
                passphrase = passphrase,
                showPassphrase = showPassphrase,
                onValueChange = { passphrase = it },
                onToggleShow = { showPassphrase = !showPassphrase }
            )
        }

        GlassCard(cornerRadius = 24.dp) {
            SectionHeader("EZD MANAGER DATA", "Themes, profiles, per-app rules, spoof presets & baselines")
            Spacer(Modifier.height(6.dp))
            BackupToggleRow("EZD Configuration", "Always included in EZD Manager backups", true) {}
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            BackupToggleRow("Network DNS & TCP Config", "Private DNS & congestion algorithm", includeNetworkConfig) {
                includeNetworkConfig = it
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            BackupToggleRow("EZD Root Baselines", "CPU/GPU stock restore snapshots", includeRootBaselines) {
                includeRootBaselines = it
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = "Backup EZD",
                icon = Icons.Rounded.UploadFile,
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    exportLauncher.launch("ezd_manager_$stamp.ezdcfg.json")
                },
                enabled = !state.busy,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = "Restore EZD",
                icon = Icons.Rounded.DownloadForOffline,
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                enabled = !state.busy,
                accent = false,
                modifier = Modifier.weight(1f)
            )
        }

        state.backupPreview?.let { preview ->
            GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EZD Archive Preview", style = MaterialTheme.typography.titleMedium)
                    StatusPill(
                        label = if (preview.checksumValid) "SHA-256 OK" else "CHECKSUM ERR",
                        accent = if (preview.checksumValid) visuals.successColor else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Version ${preview.archiveVersion} · Created ${preview.createdAt}", style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono)
                Text("Categories: ${preview.categories.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (preview.includesEzdConfig) {
                    BackupToggleRow("Restore EZD Configuration", "Themes, profiles, spoof presets & whitelist", restoreEzdConfig) {
                        restoreEzdConfig = it
                    }
                }
                BackupToggleRow("Restore Network Preferences", "Private DNS & TCP algorithm (requires root)", restoreNetworkConfig) {
                    restoreNetworkConfig = it
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Confirm Restore",
                        onClick = { vm.executeRestore(restoreEzdConfig, restoreNetworkConfig) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { vm.clearBackupDialogs() }, shape = CircleShape) { Text("Dismiss") }
                }
            }
        }

        BackupReportCard(state = state, vm = vm)
    }
}

// ============================================================================
// 4. APP BACKUP PANEL (Base APK, App Data, Ext Data, OBB, Media + Restore)
// ============================================================================

@Composable
fun AppBackupPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var appFilter by remember { mutableStateOf("") }

    var includeBaseApk by remember { mutableStateOf(true) }
    var includeAppData by remember { mutableStateOf(true) }
    var includeExternalData by remember { mutableStateOf(true) }
    var includeObb by remember { mutableStateOf(false) }
    var includeMedia by remember { mutableStateOf(false) }

    val filteredApps = remember(state.installedApps, appFilter) {
        val q = appFilter.trim().lowercase()
        state.installedApps.filter {
            !it.isSystemApp && (q.isBlank() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q))
        }
    }

    val anyOptionSelected = includeBaseApk || includeAppData || includeExternalData || includeObb || includeMedia

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Text("App Package & Data Backup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Archives Base APK (+ Split APKs), App Data, External Data, OBB & Media into public Download/EZD Manager/Backups (accessible on non-rooted devices too).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GlassCard(cornerRadius = 24.dp) {
            SectionHeader("BACKUP COMPONENTS", "Enable toggles for each app payload")
            Spacer(Modifier.height(8.dp))
            BackupToggleRow("Base APK (+ Split APKs)", "Copies base.apk & split config APKs", includeBaseApk) {
                includeBaseApk = it
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            BackupToggleRow("App Data", "/data/user/0/<package> prefs & databases (root)", includeAppData) {
                includeAppData = it
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            BackupToggleRow("External Data", "/sdcard/Android/data/<package>", includeExternalData) {
                includeExternalData = it
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            BackupToggleRow("OBB Files", "/sdcard/Android/obb/<package> game assets", includeObb) {
                includeObb = it
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            BackupToggleRow("Media Files", "/sdcard/Android/media/<package>", includeMedia) {
                includeMedia = it
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Apps (${selectedApps.size})", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { selectedApps = filteredApps.take(25).map { it.packageName }.toSet() }) {
                        Text("Select Visible")
                    }
                    if (selectedApps.isNotEmpty()) {
                        TextButton(onClick = { selectedApps = emptySet() }) { Text("Clear") }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = appFilter,
                onValueChange = { appFilter = it },
                placeholder = { Text("Search installed apps…") },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            filteredApps.take(35).forEach { app ->
                val checked = app.packageName in selectedApps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            selectedApps = if (checked) selectedApps - app.packageName else selectedApps + app.packageName
                        }
                        .padding(vertical = 5.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIconItem(app.packageName, app.label, size = 34.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (app.versionName.isNotBlank()) "v${app.versionName} · ${app.packageName}" else app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            selectedApps = if (checked) selectedApps - app.packageName else selectedApps + app.packageName
                        }
                    )
                }
            }
        }

        GlassButton(
            text = if (state.busy) "Backing Up…" else "Start App Backup (${selectedApps.size})",
            icon = Icons.Rounded.Archive,
            onClick = {
                vm.runFullAppBackup(
                    selectedApps = selectedApps,
                    options = AppBackupOptions(
                        includeBaseApk = includeBaseApk,
                        includeAppData = includeAppData,
                        includeExternalData = includeExternalData,
                        includeObb = includeObb,
                        includeMedia = includeMedia
                    )
                )
            },
            enabled = !state.busy && selectedApps.isNotEmpty() && anyOptionSelected,
            modifier = Modifier.fillMaxWidth()
        )

        BackupReportCard(state = state, vm = vm)

        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved App Backups (${state.localAppBackups.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { vm.refreshLocalAppBackups() }) { Text("Refresh") }
            }
            if (state.localAppBackups.isEmpty()) {
                Text(
                    text = "No local app backups saved yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(6.dp))
                state.localAppBackups.forEach { rec ->
                    val sizeText = if (rec.totalSizeBytes >= 1_048_576L) {
                        String.format(Locale.US, "%.1f MB", rec.totalSizeBytes / 1_048_576.0)
                    } else {
                        "${(rec.totalSizeBytes / 1024L).coerceAtLeast(1L)} KB"
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                        border = BorderStroke(1.dp, visuals.glassBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${rec.appLabel} · $sizeText", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${rec.packageName} · ${rec.createdAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "Includes: ${rec.parts.joinToString(" · ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = visuals.accent
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton(
                                    text = "Restore",
                                    icon = Icons.Rounded.Restore,
                                    onClick = { vm.restoreLocalAppBackup(rec) },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { vm.deleteLocalAppBackup(rec) },
                                    enabled = !state.busy,
                                    shape = CircleShape,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PassphraseField(
    passphrase: String,
    showPassphrase: Boolean,
    onValueChange: (String) -> Unit,
    onToggleShow: () -> Unit
) {
    OutlinedTextField(
        value = passphrase,
        onValueChange = onValueChange,
        label = { Text("Encryption Passphrase (Optional)") },
        placeholder = { Text("Leave blank for device-bound key") },
        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleShow) {
                Icon(
                    imageVector = if (showPassphrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Toggle passphrase"
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BackupReportCard(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    state.backupReport?.let { rep ->
        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Archive Report", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { vm.clearBackupDialogs() }) { Text("Clear") }
            }
            Text(rep.summary, style = MaterialTheme.typography.bodyMedium, color = visuals.accent)
            if (rep.backedUpItems.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Completed:", style = MaterialTheme.typography.labelLarge)
                rep.backedUpItems.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (rep.skippedItems.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Notes:", style = MaterialTheme.typography.labelLarge)
                rep.skippedItems.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun BackupToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

// ============================================================================
// 5. ROOT SHELL PANEL
// ============================================================================

@Composable
fun RootTerminalPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    var command by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Text(
                text = when (state.effectivePrivilegeMode) {
                    PrivilegeMode.ROOT -> "Root Daemon Shell (uid=0, persistent worker)"
                    PrivilegeMode.SHIZUKU -> "Shizuku ADB Shell (uid=2000, rootless privileged)"
                    PrivilegeMode.BASIC -> "Unprivileged sh Shell (Root / Shizuku not granted)"
                },
                style = MaterialTheme.typography.titleSmall,
                color = visuals.subtleRedAccent
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
                    placeholder = { Text("Enter shell command…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_input")
                )
                GlassButton(
                    text = "Run",
                    onClick = {
                        vm.runTerminalCommand(command)
                        command = ""
                    }
                )
            }
        }

        state.terminalHistory.reversed().forEach { entry ->
            GlassCard(cornerRadius = 20.dp) {
                Text(
                    text = "$ ${entry.command}  [exit ${entry.exitCode}]",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = visuals.subtleRedAccent
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
