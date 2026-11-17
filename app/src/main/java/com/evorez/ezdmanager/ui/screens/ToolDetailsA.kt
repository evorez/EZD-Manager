package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import kotlin.math.roundToInt

@Composable
fun DeviceSpoofPanel(
    state: EzdUiState,
    vm: EzdViewModel
) {
    val visuals = LocalEzdVisuals.current
    var showAddDialog by remember { mutableStateOf(false) }
    val presets = remember(state.customSpoofPresets) { vm.allSpoofPresets() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Text("Current Device Properties", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Model: ${state.spoofState.currentModel}\nBrand: ${state.spoofState.currentBrand} · Manufacturer: ${state.spoofState.currentManufacturer}\nDevice: ${state.spoofState.currentDevice}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.spoofState.statusMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.spoofState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = visuals.warningColor
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.resetSpoof() },
                    enabled = state.spoofState.resetpropAvailable && !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Reset to Default")
                }
                Button(
                    onClick = { showAddDialog = true },
                    shape = CircleShape,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add_custom_spoof_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Own Device")
                }
            }
        }

        GlassCard(cornerRadius = 22.dp) {
            Text(
                text = "Note: Device spoofing updates allowlisted ro.product.* properties via resetprop. It does not guarantee unlocked FPS or bypass integrity checks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        presets.forEach { preset ->
            GlassCard(cornerRadius = 24.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${preset.manufacturer} · ${preset.model} (${preset.device})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (preset.isCustom) {
                            IconButton(onClick = { vm.deleteCustomSpoofPreset(preset.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete preset")
                            }
                        }
                        Button(
                            onClick = { vm.applySpoofPreset(preset) },
                            enabled = state.spoofState.resetpropAvailable && !state.busy,
                            shape = CircleShape,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var manufacturer by remember { mutableStateOf("") }
        var brand by remember { mutableStateOf("") }
        var model by remember { mutableStateOf("") }
        var device by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text("Add Custom Device Spoof") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Preset Name") }, singleLine = true)
                    OutlinedTextField(value = manufacturer, onValueChange = { manufacturer = it }, label = { Text("Manufacturer") }, singleLine = true)
                    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, singleLine = true)
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true)
                    OutlinedTextField(value = device, onValueChange = { device = it }, label = { Text("Device Codename") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && model.isNotBlank()) {
                            vm.addCustomSpoofPreset(
                                SpoofPreset(
                                    id = "custom_${System.currentTimeMillis()}",
                                    name = name.trim(),
                                    manufacturer = manufacturer.ifBlank { brand }.ifBlank { "Custom" }.trim(),
                                    brand = brand.ifBlank { manufacturer }.ifBlank { "custom" }.trim(),
                                    model = model.trim(),
                                    device = device.ifBlank { model }.trim(),
                                    isCustom = true
                                )
                            )
                            showAddDialog = false
                        }
                    },
                    shape = CircleShape
                ) { Text("Save Preset") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun NetworkDnsPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    var customHost by remember { mutableStateOf(state.networkState.privateDnsSpecifier) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp) {
            Text("TCP Congestion Control", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Active: ${state.networkState.tcpCongestion}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMono,
                color = visuals.neonAccent
            )
            Spacer(Modifier.height(10.dp))
            if (state.networkState.availableTcp.isEmpty()) {
                Text(
                    text = "Requires root to inspect /proc/sys/net/ipv4/tcp_available_congestion_control",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.networkState.availableTcp.forEach { algo ->
                        FilterChip(
                            selected = algo == state.networkState.tcpCongestion,
                            onClick = { vm.setTcpCongestion(algo) },
                            label = { Text(algo.uppercase()) },
                            shape = CircleShape
                        )
                    }
                }
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Text("Private DNS Providers", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mode: ${state.networkState.privateDnsMode} ${state.networkState.privateDnsSpecifier}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            SystemToolsRepository.DNS_PRESETS.forEach { preset ->
                val selected = state.networkState.privateDnsSpecifier == preset.hostname &&
                    (preset.hostname.isNotBlank() || state.networkState.privateDnsMode != "hostname")
                val badgeColor = when (preset.id) {
                    "cloudflare" -> Color(0xFFF38020)
                    "google" -> Color(0xFF4285F4)
                    "adguard" -> Color(0xFF68BC71)
                    "quad9" -> Color(0xFF3B82F6)
                    else -> visuals.neonAccent
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) badgeColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, if (selected) badgeColor else visuals.glassBorder),
                    onClick = { vm.applyDnsPreset(preset) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DnsProviderLogo(tag = preset.badgeTag, accent = badgeColor)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = preset.hostname.ifBlank { "System default DNS" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(label = preset.primaryIp, accent = badgeColor)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customHost,
                    onValueChange = { customHost = it },
                    label = { Text("Custom DoT Hostname") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (customHost.isNotBlank()) {
                            vm.applyDnsPreset(DnsPreset("custom", "Custom DNS", customHost.trim(), "DoT", "DNS"))
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Set") }
            }
        }
    }
}

@Composable
private fun DnsProviderLogo(tag: String, accent: Color) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
fun BatteryChargePanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val bat = state.batteryState
    var sliderMa by remember(bat.targetCurrentMa) { mutableFloatStateOf(bat.targetCurrentMa.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp) {
            Text("Battery Status", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Level: ${if (bat.percent >= 0) "${bat.percent}%" else "Unknown"} · ${bat.chargingStatus}" +
                    (bat.currentMa?.let { " · Live ${it} mA" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = JetBrainsMono,
                color = visuals.neonAccent
            )
            if (bat.statusNote.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(bat.statusNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bypass Charging", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (bat.bypassSupported) "Power device directly without charging battery" else "Not supported on this kernel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = bat.bypassEnabled,
                    onCheckedChange = { vm.toggleBypassCharging(it) },
                    enabled = bat.bypassSupported && !state.busy
                )
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Text("Max Charging Current & Wattage", style = MaterialTheme.typography.titleMedium)
            if (!bat.currentControlSupported) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Not supported on this device — current_max / constant_charge_current_max sysfs node not writable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val estWatts = ((sliderMa / 1000f) * 10f).roundToInt() // approx 10V PPS/QC rail estimate
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${sliderMa.roundToInt()} mA (~${estWatts}W limit)",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMono,
                    color = visuals.neonAccent
                )
                Slider(
                    value = sliderMa,
                    onValueChange = { sliderMa = it },
                    onValueChangeFinished = { vm.setChargingCurrentMa(sliderMa.roundToInt()) },
                    valueRange = 500f..10000f,
                    steps = 18
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1500 to "15W", 3000 to "30W", 6000 to "60W", 10000 to "100W").forEach { (ma, label) ->
                        FilterChip(
                            selected = sliderMa.roundToInt() == ma,
                            onClick = {
                                sliderMa = ma.toFloat()
                                vm.setChargingCurrentMa(ma)
                            },
                            label = { Text("$label ($ma mA)") },
                            shape = CircleShape
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioEnhancerPanel(state: EzdUiState, vm: EzdViewModel) {
    val audio = state.audioState
    val presets = listOf("Bass", "Vocal", "Music", "Dynamic", "Gaming")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp) {
            Text("Audio Style Presets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (audio.engineReady) "Active DSP Engine · Style: ${audio.activePreset}" else "AudioEffect session unavailable on emulator/device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { name ->
                    FilterChip(
                        selected = audio.activePreset == name,
                        onClick = { vm.selectAudioPreset(name) },
                        label = { Text(name) },
                        shape = CircleShape
                    )
                }
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Text("Bass Boost (${audio.bassStrength / 10}%)", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = audio.bassStrength.toFloat(),
                onValueChange = { vm.updateAudioManual(audio.bandLevels, it.roundToInt(), audio.loudnessGainMb) },
                valueRange = 0f..1000f
            )
            Text("Loudness Enhancer (+${audio.loudnessGainMb} mB)", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = audio.loudnessGainMb.toFloat(),
                onValueChange = { vm.updateAudioManual(audio.bandLevels, audio.bassStrength, it.roundToInt()) },
                valueRange = 0f..1200f
            )
            audio.bandsHz.forEachIndexed { idx, hz ->
                val lvl = audio.bandLevels.getOrElse(idx) { 0 }
                Text("${hz} Hz (${lvl / 100f} dB)", style = MaterialTheme.typography.labelMedium, fontFamily = JetBrainsMono)
                Slider(
                    value = lvl.toFloat(),
                    onValueChange = { v ->
                        val next = audio.bandLevels.toMutableList()
                        while (next.size <= idx) next.add(0)
                        next[idx] = v.roundToInt()
                        vm.updateAudioManual(next, audio.bassStrength, audio.loudnessGainMb)
                    },
                    valueRange = -1500f..1500f
                )
            }
        }
    }
}

@Composable
fun PerformanceHudPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val hud = state.hudMetrics
    val fpsHistory = remember { mutableStateListOf<Int>() }

    LaunchedEffect(hud.fps) {
        fpsHistory.add(hud.fps)
        if (fpsHistory.size > 30) fpsHistory.removeAt(0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 26.dp, highlightBorder = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Live FPS Meter", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${hud.fps} FPS",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = JetBrainsMono,
                        color = visuals.neonAccent
                    )
                }
                StatusPill(label = "${hud.currentRefreshHz.toInt()} Hz Panel", accent = visuals.neonSecondary)
            }

            Spacer(Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                if (fpsHistory.size >= 2) {
                    val path = Path()
                    val maxFps = (hud.currentRefreshHz.coerceAtLeast(60f) * 1.1f)
                    fpsHistory.forEachIndexed { i, f ->
                        val x = (i.toFloat() / (fpsHistory.size - 1)) * size.width
                        val y = size.height - ((f / maxFps).coerceIn(0f, 1f) * size.height)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = visuals.neonAccent, style = Stroke(width = 4f))
                } else {
                    drawLine(
                        color = visuals.glassBorder,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2f
                    )
                }
            }
        }

        // Big & Small Clusters CPU MHz + GPU + RAM (NO temperature!)
        GlassCard(cornerRadius = 24.dp) {
            Text("Cluster & Memory Telemetry", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            HudMetricRow("Small Cluster", "${hud.smallClusterMhz} MHz")
            HudMetricRow("Big Cluster", "${hud.bigClusterMhz} MHz")
            HudMetricRow("GPU Clock", if (hud.gpuMhz > 0) "${hud.gpuMhz} MHz" else "Unavailable")
            HudMetricRow("RAM Usage", "${hud.ramUsedMb} / ${hud.ramTotalMb} MB")
        }

        // Refresh Rate Quick Switcher inside HUD
        val uniqueHz = state.displayState.supportedModes.map { it.refreshRateHz }.distinct().sorted()
        GlassCard(cornerRadius = 24.dp) {
            Text("Refresh Rate Quick Set", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (uniqueHz.isEmpty()) {
                Text("Current: ${hud.currentRefreshHz.toInt()} Hz", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uniqueHz.forEach { hz ->
                        FilterChip(
                            selected = kotlin.math.abs(hud.currentRefreshHz - hz) < 1f,
                            onClick = { vm.setRefreshRate(hz) },
                            label = { Text("${hz.toInt()} Hz") },
                            shape = CircleShape
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold)
    }
}
