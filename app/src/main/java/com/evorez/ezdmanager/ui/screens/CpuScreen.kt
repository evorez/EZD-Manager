package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import kotlin.math.roundToInt

@Composable
fun CpuScreen(
    state: EzdUiState,
    onSetCpuRange: (CpuPolicy, Long?, Long?) -> Unit,
    onSetCpuGovernor: (CpuPolicy, String) -> Unit,
    onResetCpuPolicy: (CpuPolicy) -> Unit,
    onToggleCore: (CpuCore, Boolean) -> Unit,
    onOpenProfiles: () -> Unit
) {
    val visuals = LocalEzdVisuals.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassCard(cornerRadius = 26.dp, highlightBorder = state.rootStatus == RootStatus.GRANTED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlowIcon(Icons.Rounded.Memory, contentDescription = "CPU Engine", tint = visuals.subtleRedAccent, size = 26.dp)
                    Column {
                        Text("CPU Clusters & Clocks", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = state.overview.cpuUsagePercent?.let { "Live CPU Load: $it% · ${state.cpuPolicies.size} cluster(s)" }
                                ?: "${state.cpuPolicies.size} cluster(s) detected",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onOpenProfiles, modifier = Modifier.size(48.dp)) {
                    GlowIcon(Icons.Rounded.Tune, contentDescription = "Profiles", size = 20.dp)
                }
            }
        }

        if (state.rootStatus != RootStatus.GRANTED) {
            GlassCard(cornerRadius = 24.dp) {
                Text(
                    text = "CPU scaling and governor controls require root access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        if (state.cpuPolicies.isEmpty()) {
            GlassCard(cornerRadius = 24.dp) {
                Text(
                    text = "No readable cpufreq policy nodes found on this kernel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            state.cpuPolicies.forEach { policy ->
                CpuPolicyCard(
                    policy = policy,
                    busy = state.busy,
                    isLite = visuals.isLiteMode,
                    onSetRange = { minK, maxK -> onSetCpuRange(policy, minK, maxK) },
                    onSetGovernor = { gov -> onSetCpuGovernor(policy, gov) },
                    onReset = { onResetCpuPolicy(policy) }
                )
            }
        }

        if (state.cpuCores.isNotEmpty()) {
            GlassCard(cornerRadius = 26.dp) {
                SectionHeader("PER-CORE STATUS & HOTPLUG", "${state.cpuCores.count { it.online }} / ${state.cpuCores.size} cores online")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.cpuCores.chunked(4).forEach { rowCores ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCores.forEach { core ->
                                CoreTile(
                                    core = core,
                                    busy = state.busy,
                                    onToggle = { onToggleCore(core, !core.online) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - rowCores.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(92.dp))
    }
}

@Composable
private fun CpuPolicyCard(
    policy: CpuPolicy,
    busy: Boolean,
    isLite: Boolean,
    onSetRange: (Long?, Long?) -> Unit,
    onSetGovernor: (String) -> Unit,
    onReset: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    var govMenuExpanded by remember(policy.id) { mutableStateOf(false) }

    val freqs = policy.availableFreqsKhz
    var pendingMinIdx by remember(policy.id, policy.minKhz, freqs) {
        mutableStateOf(
            if (freqs.isNotEmpty() && policy.minKhz != null) freqs.indexOfFirst { it >= policy.minKhz }.coerceAtLeast(0)
            else 0
        )
    }
    var pendingMaxIdx by remember(policy.id, policy.maxKhz, freqs) {
        mutableStateOf(
            if (freqs.isNotEmpty() && policy.maxKhz != null) freqs.indexOfLast { it <= policy.maxKhz }.coerceAtLeast(0)
            else (freqs.lastIndex.coerceAtLeast(0))
        )
    }

    GlassCard(cornerRadius = 26.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${policy.id.uppercase()} · CPUs ${policy.cpus.ifBlank { "?" }}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Now ${freqMhz(policy.currentKhz)} · Range ${freqMhz(policy.minKhz)} – ${freqMhz(policy.maxKhz)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onReset,
                enabled = !busy && (policy.originalMaxKhz != null || policy.originalGovernor != null),
                shape = CircleShape,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Reset")
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Governor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                FilledTonalButton(
                    onClick = { govMenuExpanded = true },
                    enabled = !busy && policy.govWritable && policy.governors.isNotEmpty(),
                    shape = CircleShape,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("cpu_gov_button_${policy.id}")
                ) {
                    Text(policy.currentGovernor.ifBlank { "Unavailable" })
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Select governor")
                }
                DropdownMenu(expanded = govMenuExpanded, onDismissRequest = { govMenuExpanded = false }) {
                    policy.governors.forEach { gov ->
                        DropdownMenuItem(
                            text = { Text(gov) },
                            onClick = {
                                govMenuExpanded = false
                                onSetGovernor(gov)
                            }
                        )
                    }
                }
            }
        }

        if (isLite) {
            val recommended = listOf("schedutil", "walt", "interactive").firstOrNull { it in policy.governors }
            if (recommended != null) {
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = { onSetGovernor(recommended) },
                    label = { Text("Recommended: $recommended") },
                    shape = CircleShape
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (freqs.size >= 2 && (policy.minWritable || policy.maxWritable)) {
            if (policy.minWritable) {
                val previewMin = freqs[pendingMinIdx.coerceIn(0, freqs.lastIndex)]
                Text("Min Frequency: ${freqMhz(previewMin)}", style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono)
                GlassSlider(
                    value = pendingMinIdx.toFloat(),
                    onValueChange = { pendingMinIdx = it.roundToInt().coerceIn(0, pendingMaxIdx) },
                    onValueChangeFinished = { onSetRange(freqs[pendingMinIdx.coerceIn(0, pendingMaxIdx)], null) },
                    valueRange = 0f..freqs.lastIndex.toFloat(),
                    steps = (freqs.size - 2).coerceAtLeast(0)
                )
            }
            if (policy.maxWritable) {
                val previewMax = freqs[pendingMaxIdx.coerceIn(0, freqs.lastIndex)]
                Text("Max Frequency: ${freqMhz(previewMax)}", style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono)
                GlassSlider(
                    value = pendingMaxIdx.toFloat(),
                    onValueChange = { pendingMaxIdx = it.roundToInt().coerceIn(pendingMinIdx, freqs.lastIndex) },
                    onValueChangeFinished = { onSetRange(null, freqs[pendingMaxIdx.coerceIn(pendingMinIdx, freqs.lastIndex)]) },
                    valueRange = 0f..freqs.lastIndex.toFloat(),
                    steps = (freqs.size - 2).coerceAtLeast(0)
                )
            }
        } else {
            Text(
                text = if (freqs.isEmpty()) "Available frequency table not exposed by kernel." else "Frequencies are read-only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoreTile(
    core: CpuCore,
    busy: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val activeColor = if (core.online) visuals.successColor else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (core.hotplugSupported && !busy) Modifier.clickable(onClick = onToggle) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = activeColor.copy(alpha = if (core.online) 0.14f else 0.08f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CPU${core.index}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                text = if (!core.online) "OFF" else core.currentKhz?.let { "${it / 1000}M" } ?: "ON",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMono,
                color = activeColor
            )
        }
    }
}

private fun freqMhz(khz: Long?): String = if (khz == null) "Unavailable" else "${khz / 1000} MHz"
