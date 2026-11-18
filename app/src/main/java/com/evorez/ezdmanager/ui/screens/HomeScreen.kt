package com.evorez.ezdmanager.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.root.shortTag
import com.evorez.ezdmanager.ui.components.GlassButton
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.GlassDialog
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.components.UserAvatarBadge
import com.evorez.ezdmanager.ui.components.captureLaunchBounds
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    state: EzdUiState,
    daemonStats: DaemonStats,
    onRetryRoot: () -> Unit,
    onRequestShizuku: () -> Unit,
    onShowDetails: () -> Unit,
    onApplyProfile: (ProfileConfig) -> Unit,
    onToggleBeastMode: () -> Unit,
    onSetBeastThermalLimits: (Float?, Float?) -> Unit,
    onSetBeastIgnoreThermal: (Boolean) -> Unit,
    onOpenTool: (ToolId) -> Unit,
    onClearSafeMode: () -> Unit,
    onUpdateUserProfile: (String, String, Uri?) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    var showEditProfileDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // User Profile Card (Author card layout without external links)
        UserHomeProfileCard(
            userName = state.settings.userName,
            userTagline = state.settings.userTagline,
            photoPath = state.settings.userPhotoPath,
            privilegeMode = state.effectivePrivilegeMode,
            workMode = state.settings.workMode,
            onEditClick = { showEditProfileDialog = true }
        )

        if (state.bootGuardState.safeModeActive) {
            GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlowIcon(Icons.Rounded.Warning, contentDescription = "Safe Mode", tint = MaterialTheme.colorScheme.error, size = 24.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Boot-Loop Safe Mode Active", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(
                            text = state.bootGuardState.lastReason.ifBlank {
                                "Startup tweaks & Root modules auto-disabled after 3 failed boots."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onClearSafeMode) { Text("Clear") }
                }
            }
        }

        PrivilegeStatusCard(
            rootStatus = state.rootStatus,
            shizukuStatus = state.shizukuStatus,
            effectiveMode = state.effectivePrivilegeMode,
            onRetryRoot = onRetryRoot,
            onRequestShizuku = onRequestShizuku
        )

        AnimatedVisibility(visible = state.statusBanner.isNotBlank()) {
            GlassCard(cornerRadius = 22.dp, highlightBorder = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlowIcon(Icons.Rounded.Info, contentDescription = null, tint = visuals.accent, size = 20.dp)
                    Text(
                        text = state.statusBanner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        DeviceOverviewCard(overview = state.overview, onDetailsClick = onShowDetails)

        // Mixed CPU & GPU Unified Live Control Card (Balanced + Boost)
        MixedCpuGpuControlCard(
            state = state,
            daemonStats = daemonStats,
            onApplyProfile = onApplyProfile
        )

        // Header 2: 𝙱𝙴𝙰𝚂𝚃 𝙼𝙾𝙳𝙴 (Pushes kernel limits, uncaps limits & locks 100% CPU & GPU)
        BeastModeCard(
            state = state,
            onToggleBeastMode = onToggleBeastMode,
            onSetBeastThermalLimits = onSetBeastThermalLimits,
            onSetBeastIgnoreThermal = onSetBeastIgnoreThermal
        )

        // 3 Distinct Mode Toggles: Power Save (GPU 10% / CPU 30%), Balanced (GPU 30% / CPU 50%), Performance (GPU 70% / CPU 70%)
        ProfileTogglesStrip(
            activeProfileId = state.settings.activeProfileId,
            onApplyProfile = onApplyProfile
        )

        // Quick Actions Strip (launch-bounds tracked for iOS Card Morph)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionPill("ZRAM & VM", Icons.Rounded.SdStorage) { onOpenTool(ToolId.ZRAM_VM) }
            QuickActionPill("I/O & Thermal", Icons.Rounded.Tune) { onOpenTool(ToolId.KERNEL_TWEAKS) }
            QuickActionPill("Battery Stats", Icons.Rounded.BatteryChargingFull) { onOpenTool(ToolId.BATTERY_SAVING) }
            QuickActionPill("Modules", Icons.Rounded.Extension) { onOpenTool(ToolId.MODULES) }
            QuickActionPill("Network", Icons.Rounded.Dns) { onOpenTool(ToolId.NETWORK) }
            QuickActionPill("App Backup", Icons.Rounded.InstallMobile) { onOpenTool(ToolId.APP_BACKUP) }
        }

        Spacer(Modifier.height(124.dp))
    }

    if (showEditProfileDialog) {
        EditUserProfileDialog(
            initialName = state.settings.userName,
            initialTagline = state.settings.userTagline,
            initialPhotoPath = state.settings.userPhotoPath,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, tag, uri ->
                onUpdateUserProfile(name, tag, uri)
                showEditProfileDialog = false
            }
        )
    }
}

@Composable
private fun UserHomeProfileCard(
    userName: String,
    userTagline: String,
    photoPath: String,
    privilegeMode: PrivilegeMode,
    workMode: WorkMode,
    onEditClick: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    GlassCard(cornerRadius = 26.dp, highlightBorder = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            UserAvatarBadge(photoPath = photoPath, size = 56.dp, borderColor = visuals.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userName.ifBlank { "Evorez User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = userTagline.ifBlank { "Device Tuner" },
                    style = MaterialTheme.typography.bodySmall,
                    color = visuals.accent,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        label = "${privilegeMode.label} Mode",
                        accent = when (privilegeMode) {
                            PrivilegeMode.ROOT -> visuals.successColor
                            PrivilegeMode.SHIZUKU -> visuals.accent
                            PrivilegeMode.BASIC -> visuals.warningColor
                        }
                    )
                    StatusPill(
                        label = "Work: ${workMode.label}",
                        accent = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("edit_user_profile_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit Profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditUserProfileDialog(
    initialName: String,
    initialTagline: String,
    initialPhotoPath: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Uri?) -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalEzdVisuals.current
    var name by remember { mutableStateOf(initialName) }
    var tagline by remember { mutableStateOf(initialTagline) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val pickedBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = pickedUri) {
        value = pickedUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }.getOrNull()
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    GlassDialog(
        title = "Edit Profile",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = { onSave(name, tagline, pickedUri) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pickedBitmap != null) {
                Image(
                    bitmap = pickedBitmap!!,
                    contentDescription = "New Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .border(2.dp, visuals.accent, CircleShape)
                )
            } else {
                UserAvatarBadge(photoPath = initialPhotoPath, size = 58.dp)
            }
            OutlinedButton(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                shape = CircleShape,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Change Photo")
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = tagline,
            onValueChange = { tagline = it },
            label = { Text("Tagline") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MixedCpuGpuControlCard(
    state: EzdUiState,
    daemonStats: DaemonStats,
    onApplyProfile: (ProfileConfig) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val cpuLoad = state.overview.cpuUsagePercent?.let { "$it%" } ?: "--"
    val littleCluster = state.cpuPolicies.firstOrNull()
    val primeCluster = state.cpuPolicies.lastOrNull()
    val gpu = state.gpuState
    val builtIns = remember { ProfileRepository.builtInProfiles() }
    val perfProf = builtIns.find { it.id == "performance" }
    val balProf = builtIns.find { it.id == "balanced" }

    GlassCard(
        cornerRadius = 26.dp,
        highlightBorder = daemonStats.perAppMonitorActive,
        modifier = Modifier.testTag("home_cpu_gpu_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowIcon(Icons.Rounded.Memory, contentDescription = "CPU & GPU", tint = visuals.accent, size = 24.dp)
                Column {
                    Text("CPU & GPU Control", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = when {
                            daemonStats.perAppMonitorActive -> "Per-App Daemon Active"
                            state.cpuPolicies.isNotEmpty() -> "${state.cpuPolicies.size} CPU Clusters · ${gpu.vendorHint}"
                            else -> "CPU: ${state.cpuResult.shortTag()} · ${gpu.vendorHint}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = visuals.solidSurfaceElevated,
                border = BorderStroke(1.dp, visuals.glassBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("CPU LOAD · $cpuLoad", style = MaterialTheme.typography.labelMedium, color = visuals.accent, fontFamily = JetBrainsMono)
                    Text(
                        text = primeCluster?.currentKhz?.let { "${it / 1000} MHz Peak" }
                            ?: littleCluster?.currentKhz?.let { "${it / 1000} MHz" }
                            ?: "Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMono
                    )
                    Text(
                        text = "Gov: ${primeCluster?.currentGovernor?.ifBlank { "-" } ?: "-"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = visuals.solidSurfaceElevated,
                border = BorderStroke(1.dp, visuals.glassBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val gpuLoad = gpu.usagePercent?.let { "$it%" } ?: "--"
                    Text("GPU LOAD · $gpuLoad", style = MaterialTheme.typography.labelMedium, color = visuals.accent, fontFamily = JetBrainsMono)
                    Text(
                        text = gpu.currentMhz?.let { "$it MHz" } ?: "Standby",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMono
                    )
                    Text(
                        text = "Gov: ${gpu.currentGovernor.ifBlank { "-" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (balProf != null) {
                GlassButton(
                    text = "Balanced",
                    onClick = { onApplyProfile(balProf) },
                    accent = state.settings.activeProfileId == balProf.id,
                    modifier = Modifier.weight(1f)
                )
            }
            if (perfProf != null) {
                GlassButton(
                    text = "Boost",
                    icon = Icons.Rounded.Bolt,
                    onClick = { onApplyProfile(perfProf) },
                    accent = state.settings.activeProfileId == perfProf.id,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PrivilegeStatusCard(
    rootStatus: RootStatus,
    shizukuStatus: ShizukuStatus,
    effectiveMode: PrivilegeMode,
    onRetryRoot: () -> Unit,
    onRequestShizuku: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val (title, subtitle, tint, icon) = when (effectiveMode) {
        PrivilegeMode.ROOT -> listOf(
            "Root Granted",
            "Full superuser kernel, CPU/GPU, ZRAM & resetprop active",
            visuals.successColor,
            Icons.Rounded.VerifiedUser
        )
        PrivilegeMode.SHIZUKU -> listOf(
            "Shizuku Active",
            "Rootless ADB shell enabled · Display, Apps, DNS & Battery tweaks unlocked",
            visuals.accent,
            Icons.Rounded.Key
        )
        PrivilegeMode.BASIC -> when (rootStatus) {
            RootStatus.CHECKING -> listOf("Detecting Privileges…", "Checking Root (su) & Shizuku service", visuals.accent, Icons.Rounded.Sync)
            RootStatus.DENIED -> listOf("Root Denied", "Grant su in Magisk/KSU or activate Shizuku", visuals.warningColor, Icons.Rounded.GppBad)
            else -> listOf("Basic Monitor Mode", "Grant Root or Shizuku for active tuning", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Lock)
        }
    }

    GlassCard(
        cornerRadius = 24.dp,
        highlightBorder = effectiveMode != PrivilegeMode.BASIC
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlowIcon(
                icon = icon as ImageVector,
                contentDescription = title as String,
                tint = tint as Color,
                size = 24.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle as String, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                effectiveMode == PrivilegeMode.BASIC && shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED -> {
                    OutlinedButton(
                        onClick = onRequestShizuku,
                        shape = CircleShape,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) { Text("Shizuku") }
                }
                effectiveMode != PrivilegeMode.ROOT && rootStatus != RootStatus.CHECKING -> {
                    OutlinedButton(
                        onClick = onRetryRoot,
                        shape = CircleShape,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("retry_root_button")
                    ) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun DeviceOverviewCard(
    overview: DeviceOverview,
    onDetailsClick: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    GlassCard(cornerRadius = 26.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Device Overview", style = MaterialTheme.typography.titleLarge)
                Text("${overview.manufacturer} ${overview.model}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(
                onClick = onDetailsClick,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("device_details_button")
            ) {
                Text("Specs", color = visuals.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        OverviewRow("SoC / Board", "${overview.board} (${overview.hardware})")
        OverviewRow("Android", "${overview.androidVersion} (API ${overview.sdkInt})")
        OverviewRow("Memory", "${overview.ramUsedMb} / ${overview.ramTotalMb} MB used")
        val batText = if (overview.batteryPercent >= 0) "${overview.batteryPercent}% · ${overview.chargingStatus}" else overview.chargingStatus
        OverviewRow("Battery", batText)
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    Surface(
        modifier = Modifier
            .captureLaunchBounds()
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = visuals.solidSurfaceElevated,
        border = BorderStroke(1.dp, visuals.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlowIcon(icon, contentDescription = label, tint = visuals.accent, size = 18.dp)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BeastModeCard(
    state: EzdUiState,
    onToggleBeastMode: () -> Unit,
    onSetBeastThermalLimits: (Float?, Float?) -> Unit,
    onSetBeastIgnoreThermal: (Boolean) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val isBeastActive = state.settings.activeProfileId == GameProfileId.BEAST.id
    val peakHz = (state.displayState.peakHardwareHz.takeIf { it > 0f }
        ?: state.probeReport?.displayPeakRateHz
        ?: state.displayState.currentRefreshHz).coerceAtLeast(60f)
    val peakHzLabel = if (peakHz % 1f == 0f) "${peakHz.toInt()}Hz" else String.format(java.util.Locale.US, "%.1fHz", peakHz)

    val thermal = state.thermalTelemetry
    val batTemp = if (thermal.batteryTempC > 0f) thermal.batteryTempC else state.batteryMonitorStats.temperatureC
    val socTemp = thermal.socTempC
    val batLimit = state.settings.beastBatteryTempLimitC
    val socLimit = state.settings.beastSocTempLimitC
    val ignoreThermal = state.settings.beastIgnoreThermalLimits

    var showWatchdogSettings by remember { mutableStateOf(false) }

    val fieryColor = Color(0xFFFF2A2A)
    val amberGlow = Color(0xFFFF9500)

    val cardBorderBrush = if (isBeastActive) {
        Brush.linearGradient(listOf(fieryColor, amberGlow, fieryColor))
    } else {
        Brush.linearGradient(
            listOf(
                fieryColor.copy(alpha = 0.45f),
                visuals.glassBorder,
                amberGlow.copy(alpha = 0.35f)
            )
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .captureLaunchBounds()
            .testTag("beast_mode_card"),
        shape = RoundedCornerShape(26.dp),
        color = visuals.solidSurfaceElevated,
        border = BorderStroke(if (isBeastActive) 1.8.dp else 1.2.dp, cardBorderBrush),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isBeastActive) fieryColor.copy(alpha = if (visuals.isDark) 0.14f else 0.08f) else Color.Transparent
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = fieryColor.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, fieryColor.copy(alpha = 0.60f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "Beast Mode",
                                tint = fieryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        // Header 2: 𝙱𝙴𝙰𝚂𝚃 𝙼𝙾𝙳𝙴
                        Text(
                            text = "𝙱𝙴𝙰𝚂𝚃 𝙼𝙾𝙳𝙴",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isBeastActive) fieryColor else MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isBeastActive) "ACTIVE · Baseline Snapshotted · Watchdog Armed" else "Probe-Locked CPU/GPU · Limiters · I/O · Touch",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBeastActive) amberGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                StatusPill(
                    label = if (isBeastActive) "LOCKED 100%" else "MAX LOCK",
                    accent = if (isBeastActive) fieryColor else amberGlow
                )
            }

            Text(
                text = "Locks CPU clusters & GPU at peak OPP, enables touch boost/gaming mode, disables SoC limiters where exposed, tunes I/O/VM/TCP, and locks $peakHzLabel. Fast charging remains separate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live status & dynamic peak Hz + live thermal readout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusPill("CPU: 100%", fieryColor)
                StatusPill("GPU: 100%", amberGlow)
                StatusPill(peakHzLabel, visuals.successColor)
                StatusPill(
                    label = "Bat ${String.format(java.util.Locale.US, "%.1f°C", batTemp)}",
                    accent = when {
                        batTemp >= batLimit -> fieryColor
                        batTemp >= batLimit - 2.5f -> amberGlow
                        else -> visuals.successColor
                    }
                )
                StatusPill(
                    label = if (socTemp != null) "SoC ${String.format(java.util.Locale.US, "%.1f°C", socTemp)}" else "SoC N/A",
                    accent = when {
                        socTemp == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        socTemp >= socLimit -> fieryColor
                        socTemp >= socLimit - 5f -> amberGlow
                        else -> visuals.accent
                    }
                )
            }

            // Thermal Safety Watchdog toggle header & controls
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showWatchdogSettings = !showWatchdogSettings },
                shape = RoundedCornerShape(16.dp),
                color = visuals.solidSurfaceBase,
                border = BorderStroke(1.dp, if (ignoreThermal) fieryColor.copy(alpha = 0.65f) else visuals.glassBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Thermal Watchdog: Bat ${batLimit.toInt()}°C · SoC ${socLimit.toInt()}°C",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ignoreThermal) fieryColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (ignoreThermal) "BYPASSED — Overheat auto-stepdown OFF" else "Auto-steps down to Balanced if threshold exceeded · Boot Guard ON",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ignoreThermal) fieryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (showWatchdogSettings) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Configure Watchdog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showWatchdogSettings) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            Text("Battery Limit (°C)", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(40f, 43f, 45f, 48f).forEach { deg ->
                                    FilterChip(
                                        selected = kotlin.math.abs(batLimit - deg) < 0.5f,
                                        onClick = { onSetBeastThermalLimits(deg, null) },
                                        label = { Text("${deg.toInt()}°C") },
                                        shape = CircleShape
                                    )
                                }
                            }
                            Text("SoC Limit (°C)", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(70f, 75f, 80f, 85f).forEach { deg ->
                                    FilterChip(
                                        selected = kotlin.math.abs(socLimit - deg) < 0.5f,
                                        onClick = { onSetBeastThermalLimits(null, deg) },
                                        label = { Text("${deg.toInt()}°C") },
                                        shape = CircleShape
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ignore thermal limits", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Opt-in only (Default OFF). Disables automatic step-down.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = ignoreThermal,
                                    onCheckedChange = onSetBeastIgnoreThermal,
                                    modifier = Modifier.testTag("beast_ignore_thermal_switch")
                                )
                            }
                            if (ignoreThermal) {
                                Text(
                                    text = "WARNING: Thermal safety step-down is bypassed. Sustained peak clocks above 43°C battery / 75°C SoC can accelerate battery wear and heat.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = fieryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onToggleBeastMode,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("beast_mode_toggle_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBeastActive) fieryColor else fieryColor.copy(alpha = 0.88f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBeastActive) "DEACTIVATE" else "ACTIVATE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.8.sp
                )
            }

            // Per-item Beast Execution Report (read-back verified outcomes)
            state.beastReport?.let { report ->
                if (report.items.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = visuals.solidSurfaceBase,
                        border = BorderStroke(1.dp, visuals.glassBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = report.summary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = visuals.accent
                            )
                            HorizontalDivider()
                            report.items.forEach { item ->
                                val statusColor = when (item.status) {
                                    EngineItemStatus.OK -> visuals.successColor
                                    EngineItemStatus.FAILED -> fieryColor
                                    EngineItemStatus.UNSUPPORTED -> amberGlow
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.category} · ${item.label}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        StatusPill(label = item.status.name, accent = statusColor)
                                    }
                                    Text(
                                        text = item.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.path.isNotBlank()) {
                                        Text(
                                            text = item.path,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMono,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
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

@Composable
private fun ProfileTogglesStrip(
    activeProfileId: String,
    onApplyProfile: (ProfileConfig) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val builtIns = remember { ProfileRepository.builtInProfiles() }
    val powerSaveProf = builtIns.find { it.id == "powersave" }
    val balProf = builtIns.find { it.id == "balanced" }
    val perfProf = builtIns.find { it.id == "performance" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Performance Profiles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "3 Profiles + Beast Mode",
                style = MaterialTheme.typography.labelSmall,
                color = visuals.accent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Power Save: GPU 10%, CPU 30%
            if (powerSaveProf != null) {
                ProfileToggleCard(
                    title = "Power Save",
                    cpuSpec = "CPU 30%",
                    gpuSpec = "GPU 10%",
                    icon = Icons.Rounded.BatteryChargingFull,
                    accentColor = visuals.successColor,
                    isActive = activeProfileId == "powersave",
                    onClick = { onApplyProfile(powerSaveProf) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. Balanced Mode: GPU 30%, CPU 50%
            if (balProf != null) {
                ProfileToggleCard(
                    title = "Balanced",
                    cpuSpec = "CPU 50%",
                    gpuSpec = "GPU 30%",
                    icon = Icons.Rounded.Tune,
                    accentColor = visuals.accent,
                    isActive = activeProfileId == "balanced",
                    onClick = { onApplyProfile(balProf) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Performance Mode: GPU 70%, CPU 70%
            if (perfProf != null) {
                ProfileToggleCard(
                    title = "Performance",
                    cpuSpec = "CPU 70%",
                    gpuSpec = "GPU 70%",
                    icon = Icons.Rounded.Speed,
                    accentColor = Color(0xFFFF9500),
                    isActive = activeProfileId == "performance",
                    onClick = { onApplyProfile(perfProf) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProfileToggleCard(
    title: String,
    cpuSpec: String,
    gpuSpec: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 84.dp)
            .testTag("profile_toggle_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(18.dp),
        color = visuals.solidSurfaceElevated,
        border = BorderStroke(
            width = if (isActive) 1.6.dp else 1.dp,
            color = if (isActive) accentColor else visuals.glassBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isActive) accentColor.copy(alpha = if (visuals.isDark) 0.22f else 0.14f) else Color.Transparent
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                if (isActive) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "$cpuSpec\n$gpuSpec",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMono,
                color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 13.sp
            )
        }
    }
}
