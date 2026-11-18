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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.BatteryMonitorStats
import com.evorez.ezdmanager.model.DnsPreset
import com.evorez.ezdmanager.model.PrivilegeMode
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DisplayPanel(state: EzdUiState, vm: EzdViewModel) {
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
                        color = visuals.subtleRedAccent
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
                            selected = abs(disp.currentRefreshHz - hz) < 1f,
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
            GlassSlider(
                value = brightSlider,
                onValueChange = { brightSlider = it },
                onValueChangeFinished = { vm.setBrightness(brightSlider.roundToInt(), false) },
                valueRange = 5f..255f
            )
            if (disp.hbmSupported) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("High Brightness Mode (HBM)", style = MaterialTheme.typography.titleSmall)
                        Text("Hardware outdoor/gaming luminance boost", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = disp.hbmEnabled, onCheckedChange = { vm.setHbm(it) })
                }
            }
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
                        text = if (disp.kcalSupported) "Kernel KCAL + SurfaceFlinger" else "SurfaceFlinger Display Matrix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${String.format(Locale.US, "%.2f", satSlider)}x",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMono,
                    color = visuals.subtleRedAccent
                )
            }
            GlassSlider(
                value = satSlider,
                onValueChange = { satSlider = it },
                onValueChangeFinished = { vm.setDisplaySaturation(satSlider) },
                valueRange = 0.5f..1.8f
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.85f to "Natural", 1.0f to "Default", 1.25f to "Vivid", 1.50f to "Boost").forEach { (v, lbl) ->
                    FilterChip(
                        selected = abs(satSlider - v) < 0.05f,
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
fun TouchPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val touch = state.touchState
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = touch.hwOverlaysDisabled || touch.touchBoostEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    GlowIcon(Icons.Rounded.TouchApp, contentDescription = "Touch & HW Overlay", tint = visuals.accent, size = 24.dp)
                    Column {
                        Text("Touch & HW Overlay", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (touch.hwOverlaysDisabled) "GPU Compositing Active · Low-latency pipeline" else "Standard Display Hardware Composer",
                            style = MaterialTheme.typography.bodySmall,
                            color = visuals.accent
                        )
                    }
                }
                StatusPill(
                    label = if (touch.hwOverlaysDisabled) "GPU ON" else "HWC",
                    accent = if (touch.hwOverlaysDisabled) visuals.successColor else visuals.accent
                )
            }
            if (touch.statusNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(touch.statusNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GlassCard(cornerRadius = 24.dp) {
            Text("Compositor & Input Pipeline", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disable HW Overlays", style = MaterialTheme.typography.titleSmall)
                    Text("Forces GPU compositing via SurfaceFlinger for smoother touch/UI frames", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = touch.hwOverlaysDisabled,
                    onCheckedChange = { vm.setDisableHwOverlays(it) },
                    enabled = state.effectivePrivilegeMode != PrivilegeMode.BASIC && !state.busy
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            TouchRow(
                title = "Touch Sampling Rate",
                subtitle = touch.samplingNodePath?.let { "Current: ${touch.samplingValue} ($it)" } ?: "Not exposed by this kernel",
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
                subtitle = touch.gamingModePath ?: "Not exposed by this kernel",
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
                subtitle = touch.touchBoostPath ?: "Not exposed by this kernel",
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
    }
}

@Composable
private fun TouchRow(title: String, subtitle: String, supported: Boolean, control: @Composable () -> Unit) {
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
fun NetworkPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val net = state.networkState
    var customHost by remember { mutableStateOf(net.privateDnsSpecifier) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = net.vpnActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Active Network: ${net.transportType}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Interface: ${net.interfaceName} · TCP: ${net.tcpCongestion}" +
                            if (net.tcpFastOpenSupported) " · TFO=${net.tcpFastOpenValue}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = visuals.subtleRedAccent
                    )
                }
                OutlinedButton(
                    onClick = { vm.resetNetworkSettings() },
                    enabled = state.effectivePrivilegeMode != PrivilegeMode.BASIC && !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Reset") }
            }
        }

        // TCP Fast / Rescue Presets & Algorithm selector
        GlassCard(cornerRadius = 24.dp) {
            Text("TCP Optimization Presets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "TCP Fast applies low-latency BBR/Westwood + Fast Open when supported. TCP Rescue restores safe baseline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = "TCP Fast",
                    icon = Icons.Rounded.Bolt,
                    onClick = { vm.applyTcpFastPreset() },
                    enabled = state.rootStatus == RootStatus.GRANTED && !state.busy,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "TCP Rescue",
                    icon = Icons.Rounded.Restore,
                    onClick = { vm.applyTcpRescuePreset() },
                    enabled = state.rootStatus == RootStatus.GRANTED && !state.busy,
                    accent = false,
                    modifier = Modifier.weight(1f)
                )
            }
            if (net.availableTcp.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    net.availableTcp.forEach { algo ->
                        FilterChip(
                            selected = algo == net.tcpCongestion,
                            onClick = { vm.setTcpCongestion(algo) },
                            label = { Text(algo.uppercase()) },
                            shape = CircleShape
                        )
                    }
                }
            }
        }

        // Private DNS Providers with custom brand logos
        GlassCard(cornerRadius = 24.dp) {
            Text("Global Private DNS (DoT)", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Mode: ${net.privateDnsMode} ${net.privateDnsSpecifier}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SystemToolsRepository.DNS_PRESETS.forEach { preset ->
                val selected = net.privateDnsSpecifier == preset.hostname &&
                    (preset.hostname.isNotBlank() || net.privateDnsMode != "hostname")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) visuals.subtleRedAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, if (selected) visuals.subtleRedAccent else visuals.glassBorder),
                    onClick = { vm.applyDnsPreset(preset) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DnsProviderLogo(presetId = preset.id, size = 36.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.titleSmall)
                            Text(preset.hostname.ifBlank { "System default DNS" }, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(preset.primaryIp, style = MaterialTheme.typography.labelMedium, fontFamily = JetBrainsMono)
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
                GlassButton(
                    text = "Set",
                    onClick = {
                        if (customHost.isNotBlank()) {
                            vm.applyDnsPreset(DnsPreset("custom", "Custom DNS", customHost.trim(), "DoT", "", "DNS"))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AudioEnhancerPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val audio = state.audioState
    val presets = listOf("Bass", "Vocal", "Music", "Dynamic", "Gaming")
    val focusModes = listOf("Sub 40Hz", "Punch 60Hz", "Warm 90Hz")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Advanced Multi-Style Visualizer & Visual Style Switcher
        AudioEnhancerVisualizer(audioState = audio)

        GlassCard(cornerRadius = 24.dp) {
            Text("Visualizer Style & Spectrum Engine", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = audio.statusNote.ifBlank { "Interactive Equalizer & Bass Boost Engine" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.evorez.ezdmanager.model.AudioVisualStyle.entries.forEach { style ->
                    val styleColor = when (style) {
                        com.evorez.ezdmanager.model.AudioVisualStyle.BASS_PULSE -> Color(0xFFFF3B5C)
                        com.evorez.ezdmanager.model.AudioVisualStyle.PRISM_BARS -> Color(0xFF22D3EE)
                        com.evorez.ezdmanager.model.AudioVisualStyle.SILK_WAVE -> Color(0xFFA78BFA)
                        com.evorez.ezdmanager.model.AudioVisualStyle.CYBER_MATRIX -> Color(0xFF34D399)
                    }
                    FilterChip(
                        selected = audio.visualStyle == style,
                        onClick = { vm.setAudioVisualStyle(style) },
                        label = { Text(style.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = styleColor.copy(alpha = 0.20f),
                            selectedLabelColor = styleColor
                        ),
                        shape = CircleShape
                    )
                }
            }
        }

        // 2. Separate Dedicated Bass Enhancer Section
        GlassCard(cornerRadius = 24.dp, highlightBorder = audio.bassEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowIcon(Icons.Rounded.Speaker, contentDescription = "Bass Enhancer", tint = Color(0xFFFF3B5C), size = 22.dp)
                    Column {
                        Text("Bass Enhancer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (audio.bassEnabled) "Intensity: ${audio.bassStrength / 10}% · ${audio.bassFrequencyFocus}" else "Bypassed",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = audio.bassEnabled,
                    onCheckedChange = { vm.toggleBassEnhancer(it) }
                )
            }
            if (audio.bassEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("Sub-Bass Gain (${audio.bassStrength / 10}%)", style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono)
                GlassSlider(
                    value = audio.bassStrength.toFloat(),
                    onValueChange = { vm.updateAudioManual(audio.bandLevels, it.roundToInt(), audio.loudnessGainMb) },
                    valueRange = 0f..1000f
                )
                Spacer(Modifier.height(4.dp))
                Text("Frequency Focus", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    focusModes.forEach { focus ->
                        FilterChip(
                            selected = audio.bassFrequencyFocus == focus,
                            onClick = { vm.setBassFrequencyFocus(focus) },
                            label = { Text(focus) },
                            shape = CircleShape
                        )
                    }
                }
            }
        }

        // 3. Sound Profile Presets & Equalizer Bands
        GlassCard(cornerRadius = 24.dp, highlightBorder = audio.equalizerEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Equalizer & Loudness", style = MaterialTheme.typography.titleMedium)
                    Text("Active Curve: ${audio.activePreset}", style = MaterialTheme.typography.bodySmall, color = visuals.accent)
                }
                Switch(
                    checked = audio.equalizerEnabled,
                    onCheckedChange = { vm.toggleEqualizer(it) }
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { name ->
                    FilterChip(
                        selected = audio.activePreset == name,
                        onClick = { vm.selectAudioPreset(name) },
                        label = { Text(name) },
                        shape = CircleShape
                    )
                }
            }

            if (audio.equalizerEnabled) {
                Spacer(Modifier.height(10.dp))
                Text("Loudness Enhancer (+${audio.loudnessGainMb} mB)", style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMono)
                GlassSlider(
                    value = audio.loudnessGainMb.toFloat(),
                    onValueChange = { vm.updateAudioManual(audio.bandLevels, audio.bassStrength, it.roundToInt()) },
                    valueRange = 0f..1200f
                )
                audio.bandsHz.forEachIndexed { idx, hz ->
                    val lvl = audio.bandLevels.getOrElse(idx) { 0 }
                    val labelHz = if (hz >= 1000) "${String.format(Locale.US, "%.1f", hz / 1000f)} kHz" else "$hz Hz"
                    Text("$labelHz (${String.format(Locale.US, "%.1f", lvl / 100f)} dB)", style = MaterialTheme.typography.labelMedium, fontFamily = JetBrainsMono)
                    GlassSlider(
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
}

@Composable
fun BatterySavingPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val bat = state.batterySavingState
    val monitor = state.batteryMonitorStats
    val canTune = state.effectivePrivilegeMode != PrivilegeMode.BASIC
    var search by remember { mutableStateOf("") }

    val userApps = remember(state.installedApps, search) {
        val q = search.trim().lowercase()
        state.installedApps.filter { !it.isSystemApp && (q.isBlank() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)) }.take(40)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Real-Time Battery & Deep Sleep Telemetry Card (Works without root!)
        BatteryMonitorLiveCard(
            monitor = monitor,
            onResetStats = { vm.resetBatteryMonitorStats() }
        )

        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Text("Battery Saving Overview", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Battery: ${if (state.overview.batteryPercent >= 0) "${state.overview.batteryPercent}%" else "Unknown"} · Whitelisted: ${bat.whitelistCount} · Restricted: ${bat.restrictedCount}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = JetBrainsMono,
                color = visuals.subtleRedAccent
            )
            if (bat.lastActionSummary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(bat.lastActionSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // GMS Control (safe non-essential standby only - Root or Shizuku)
        GlassCard(cornerRadius = 24.dp) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("GMS Telemetry Standby", style = MaterialTheme.typography.titleMedium)
                    Text("Reduces non-essential Play telemetry/feedback without breaking Login, Push or Play Store (Root / Shizuku).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = bat.gmsStandbyApplied,
                    onCheckedChange = { vm.applyGmsBackgroundManagement(it) },
                    enabled = canTune && !state.busy
                )
            }
        }

        // Background App Limit + Safe Memory Trim
        GlassCard(cornerRadius = 24.dp) {
            Text("Background App Limit", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Stock", "Balanced", "Strict").forEach { preset ->
                    FilterChip(
                        selected = bat.backgroundLimitPreset == preset,
                        onClick = { vm.applyBackgroundAppLimit(preset) },
                        enabled = canTune && !state.busy,
                        label = { Text(preset) },
                        shape = CircleShape
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassButton(
                text = "Run Safe Memory Trim (Skips Whitelist)",
                icon = Icons.Rounded.CleaningServices,
                onClick = { vm.runSafeMemoryTrim() },
                enabled = canTune && !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Whitelist & Per-App Background Restriction Manager
        GlassCard(cornerRadius = 24.dp) {
            Text("Whitelist & Background Restriction", style = MaterialTheme.typography.titleMedium)
            Text("Whitelisted apps are protected from memory trim and background restrictions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Filter user apps…") },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            userApps.forEach { app ->
                val isWl = app.packageName in state.batteryWhitelist
                val isRestr = state.perAppConfigs[app.packageName]?.batteryRestricted == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIconItem(app.packageName, app.label, size = 34.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    FilterChip(
                        selected = isWl,
                        onClick = { vm.toggleWhitelistApp(app.packageName) },
                        label = { Text(if (isWl) "Protected" else "Whitelist") },
                        shape = CircleShape
                    )
                    if (!isWl) {
                        FilterChip(
                            selected = isRestr,
                            onClick = { vm.setAppBackgroundRestriction(app.packageName, !isRestr) },
                            label = { Text(if (isRestr) "Restricted" else "Restrict") },
                            shape = CircleShape
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryMonitorLiveCard(
    monitor: BatteryMonitorStats,
    onResetStats: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val currentText = if (monitor.currentMa >= 0 && monitor.isCharging) "+${monitor.currentMa} mA" else "${monitor.currentMa} mA"
    val accentTint = if (monitor.isCharging) visuals.successColor else visuals.accent

    GlassCard(cornerRadius = 26.dp, highlightBorder = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowIcon(
                    icon = if (monitor.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    contentDescription = "Battery Monitor",
                    tint = accentTint,
                    size = 24.dp
                )
                Column {
                    Text("Live Battery & Drain Monitor", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${monitor.levelPercent}% · ${monitor.chargeSource} · ${String.format(Locale.US, "%.1f", monitor.temperatureC)}°C · No root needed",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = accentTint
                    )
                }
            }
            TextButton(onClick = onResetStats) { Text("Reset") }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatteryMetricTile(
                label = if (monitor.isCharging) "CHARGE FLOW" else "DRAW FLOW",
                value = currentText,
                sub = "${monitor.voltageMv} mV · ${String.format(Locale.US, "%.2f", monitor.powerWatts)} W",
                accent = accentTint,
                modifier = Modifier.weight(1f)
            )
            BatteryMetricTile(
                label = if (monitor.isCharging) "CHARGE SPEED" else "ACTIVE DRAIN",
                value = if (monitor.isCharging) {
                    "${String.format(Locale.US, "%.1f", monitor.chargeRatePctPerHr)} %/hr"
                } else {
                    "${String.format(Locale.US, "%.1f", monitor.activeDrainPctPerHr)} %/hr"
                },
                sub = if (monitor.isCharging) "Charging session" else "Screen On avg ${monitor.avgActiveMa} mA",
                accent = visuals.accent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BatteryMetricTile(
                label = "IDLE DRAIN (OFF)",
                value = "${String.format(Locale.US, "%.2f", monitor.idleDrainPctPerHr)} %/hr",
                sub = "Idle avg ${monitor.avgIdleMa} mA",
                accent = visuals.accent,
                modifier = Modifier.weight(1f)
            )
            BatteryMetricTile(
                label = "DEEP SLEEP",
                value = "${String.format(Locale.US, "%.1f", monitor.deepSleepPercent)}%",
                sub = "${formatDurationMs(monitor.deepSleepMs)} / Awake ${formatDurationMs(monitor.awakeMs)}",
                accent = visuals.successColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Screen On: ${formatDurationMs(monitor.screenOnMs)}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Screen Off: ${formatDurationMs(monitor.screenOffMs)}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryMetricTile(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = visuals.glassSurfaceElevated,
        border = BorderStroke(1.dp, visuals.glassBorder)
    ) {
        Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontFamily = JetBrainsMono)
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
