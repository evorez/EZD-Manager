package com.evorez.ezdmanager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.backup.BackupExportOptions
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import java.io.File
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
    LaunchedEffect(Unit) { vm.refreshSavedEzdBackups() }
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
                text = "Save Backup",
                icon = Icons.Rounded.UploadFile,
                onClick = {
                    vm.exportEncryptedBackupToDownloads(
                        BackupExportOptions(
                            includeEzdConfig = true,
                            selectedApps = emptySet(),
                            includeMessagesAndCallLogs = false,
                            includeNetworkConfig = includeNetworkConfig,
                            includeBluetoothConfig = false,
                            includeRootBaselines = includeRootBaselines,
                            passphrase = passphrase
                        )
                    )
                },
                enabled = !state.busy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ezd_backup_downloads_btn")
            )
            GlassButton(
                text = "Pick File",
                icon = Icons.Rounded.DownloadForOffline,
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                enabled = !state.busy,
                accent = false,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedButton(
            onClick = {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                exportLauncher.launch("ezd_backup_$stamp.ezdcfg.json")
            },
            enabled = !state.busy,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Text("Save As (Custom Location)…")
        }

        if (state.savedEzdBackups.isNotEmpty()) {
            GlassCard(cornerRadius = 24.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved in Download/EZD/ (${state.savedEzdBackups.size})", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { vm.refreshSavedEzdBackups() }) { Text("Refresh") }
                }
                Spacer(Modifier.height(6.dp))
                state.savedEzdBackups.take(8).forEach { path ->
                    val name = File(path).name
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = visuals.solidSurfaceElevated,
                        border = BorderStroke(1.dp, visuals.glassBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(path, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(
                                onClick = { vm.inspectSavedEzdBackup(path, passphrase) },
                                enabled = !state.busy
                            ) { Text("Verify") }
                        }
                    }
                }
            }
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
                if (preview.filePath.isNotBlank()) {
                    Text("File: ${preview.filePath}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = visuals.accent)
                }
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
                        enabled = preview.checksumValid && !state.busy,
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
    LaunchedEffect(Unit) { vm.refreshLocalAppBackups() }
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
                text = "Archives Base APK (+ Split APKs), App Data, External Data, OBB & Media into public Download/EZD/ with timestamps (accessible on non-rooted devices too).",
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
                        color = visuals.solidSurfaceElevated,
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
        GlassCard(cornerRadius = 24.dp, highlightBorder = !rep.success) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup / Restore Result", style = MaterialTheme.typography.titleMedium)
                    StatusPill(
                        label = if (rep.success) "SUCCESS" else "FAILED",
                        accent = if (rep.success) visuals.successColor else MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = { vm.clearBackupDialogs() }) { Text("Clear") }
            }
            Text(
                text = rep.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (rep.success) visuals.accent else MaterialTheme.colorScheme.error
            )
            if (rep.filePath.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "File path: ${rep.filePath}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!rep.failureReason.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Reason: ${rep.failureReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (rep.backedUpItems.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Included:", style = MaterialTheme.typography.labelLarge)
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
// 5. ROOT TERMINAL SCREEN (Real root shell console + device specs header)
// ============================================================================

@Composable
fun RootTerminalPanel(state: EzdUiState, vm: EzdViewModel, modifier: Modifier = Modifier) {
    val visuals = LocalEzdVisuals.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var command by remember { mutableStateOf("") }
    var historyCursor by remember { mutableIntStateOf(-1) }

    val commandHistory = remember(state.terminalHistory) {
        state.terminalHistory.map { it.command }
    }

    LaunchedEffect(state.terminalHistory.size) {
        if (state.terminalHistory.isNotEmpty()) {
            listState.animateScrollToItem(state.terminalHistory.lastIndex)
        }
    }

    val ov = state.overview
    val kernelShort = state.probeReport?.kernelVersion?.ifBlank { ov.kernelVersion } ?: ov.kernelVersion
    val promptUser = when (state.effectivePrivilegeMode) {
        PrivilegeMode.ROOT -> "root"
        PrivilegeMode.SHIZUKU -> "shell"
        PrivilegeMode.BASIC -> "user"
    }
    val consoleBg = if (visuals.isDark) Color(0xFF070A10) else Color(0xFF0E1420)
    val consoleText = Color(0xFFE6EDF7)
    val consolePromptColor = when (state.effectivePrivilegeMode) {
        PrivilegeMode.ROOT -> Color(0xFF4ADE80)
        PrivilegeMode.SHIZUKU -> Color(0xFF38BDF8)
        PrivilegeMode.BASIC -> Color(0xFFFBBF24)
    }

    fun submitCommand(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        vm.runTerminalCommand(trimmed)
        command = ""
        historyCursor = -1
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Fancy Device Specs + Author Header
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = visuals.accent.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, visuals.accent.copy(alpha = 0.5f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = "Root Terminal",
                                tint = visuals.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "EZD Root Terminal · 𝙴𝚟𝚘𝚛𝚎𝚣🪽",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (state.effectivePrivilegeMode) {
                                PrivilegeMode.ROOT -> "uid=0 (su) · ${state.rootManagerType}"
                                PrivilegeMode.SHIZUKU -> "uid=2000 (Shizuku ADB)"
                                PrivilegeMode.BASIC -> "Unprivileged sh · Grant Root for sysfs"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = consolePromptColor
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            val transcript = state.terminalHistory.joinToString("\n\n") { entry ->
                                "$promptUser@${ov.device.ifBlank { "android" }}:/ # ${entry.command}\n${entry.output}\n[exit ${entry.exitCode}]"
                            }
                            if (transcript.isNotBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("EZD Terminal Output", transcript))
                                Toast.makeText(context, "Terminal output copied", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No terminal output to copy", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("terminal_copy_output_btn")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Output", tint = visuals.accent)
                    }
                    IconButton(
                        onClick = {
                            vm.runTerminalCommand("clear")
                            historyCursor = -1
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("terminal_clear_btn")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Terminal", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Device : ${ov.manufacturer} ${ov.model} (${ov.device})",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "SoC    : ${ov.board} / ${ov.hardware} · Android ${ov.androidVersion} (API ${ov.sdkInt})",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Kernel : ${kernelShort.ifBlank { "Linux" }}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quick command & history helper bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "id",
                "uname -a",
                "getprop ro.product.model",
                "ls /sys/devices/system/cpu/cpufreq",
                "cat /proc/version",
                "free -m"
            ).forEach { quick ->
                AssistChip(
                    onClick = { submitCommand(quick) },
                    label = { Text(quick, fontFamily = JetBrainsMono, fontSize = 11.sp) },
                    shape = CircleShape
                )
            }
        }

        // Scrollable Terminal Output Buffer
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(22.dp),
            color = consoleBg,
            border = BorderStroke(1.dp, visuals.accent.copy(alpha = 0.35f))
        ) {
            SelectionContainer {
                if (state.terminalHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "$promptUser@${ov.device.ifBlank { "android" }}:/ # _",
                                fontFamily = JetBrainsMono,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = consolePromptColor
                            )
                            Text(
                                text = "Ready. Enter any shell command below or tap a preset chip.\nOutput scrolls automatically and can be copied with the Copy button above.",
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp,
                                color = consoleText.copy(alpha = 0.72f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.terminalHistory, key = { it.id }) { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$promptUser@${ov.device.ifBlank { "android" }}:/ # ${entry.command}",
                                        fontFamily = JetBrainsMono,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = consolePromptColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "exit ${entry.exitCode}",
                                        fontFamily = JetBrainsMono,
                                        fontSize = 10.sp,
                                        color = if (entry.exitCode == 0) Color(0xFF4ADE80) else Color(0xFFF87171)
                                    )
                                }
                                Text(
                                    text = entry.output,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = consoleText
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sticky Command Input + History Up/Down Stepper
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = visuals.solidSurfaceElevated,
            border = BorderStroke(1.dp, visuals.glassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        if (commandHistory.isNotEmpty()) {
                            val nextCursor = if (historyCursor == -1) commandHistory.lastIndex else (historyCursor - 1).coerceAtLeast(0)
                            historyCursor = nextCursor
                            command = commandHistory[nextCursor]
                        }
                    },
                    enabled = commandHistory.isNotEmpty(),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("terminal_history_prev")
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous Command")
                }

                IconButton(
                    onClick = {
                        if (commandHistory.isNotEmpty() && historyCursor >= 0) {
                            val nextCursor = historyCursor + 1
                            if (nextCursor > commandHistory.lastIndex) {
                                historyCursor = -1
                                command = ""
                            } else {
                                historyCursor = nextCursor
                                command = commandHistory[nextCursor]
                            }
                        }
                    },
                    enabled = historyCursor >= 0,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("terminal_history_next")
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next Command")
                }

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    placeholder = { Text("Enter root command…", fontFamily = JetBrainsMono, fontSize = 13.sp) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = JetBrainsMono, fontSize = 13.sp),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitCommand(command) }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_input")
                )

                FilledIconButton(
                    onClick = { submitCommand(command) },
                    enabled = command.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = visuals.accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("terminal_run_btn")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Execute Command")
                }
            }
        }
    }
}

