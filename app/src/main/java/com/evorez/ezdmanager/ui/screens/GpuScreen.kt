package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.GlassSlider
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import kotlin.math.roundToInt

@Composable
fun GpuScreen(
    state: EzdUiState,
    onSetGpuRange: (Long?, Long?) -> Unit,
    onSetGpuGovernor: (String) -> Unit,
    onResetGpu: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val gpu = state.gpuState
    var govMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GlassCard(cornerRadius = 26.dp, highlightBorder = gpu.supported) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlowIcon(Icons.Rounded.Speed, contentDescription = "GPU Engine", tint = visuals.subtleRedAccent, size = 26.dp)
                    Column {
                        Text(gpu.vendorHint, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (gpu.supported) {
                                "Now ${gpu.currentMhz ?: "?"} MHz · ${gpu.minMhz ?: "?"}–${gpu.maxMhz ?: "?"} MHz"
                            } else {
                                "Hardware inspection"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (gpu.usagePercent != null) {
                    StatusPill("${gpu.usagePercent}% Load", visuals.subtleRedAccent)
                }
            }
        }

        if (state.rootStatus != RootStatus.GRANTED || !gpu.supported) {
            GlassCard(cornerRadius = 24.dp) {
                Text("Not supported on this device", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = gpu.reason.ifBlank { "Grant root access or check Adreno KGSL / Mali devfreq sysfs interfaces." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        GlassCard(cornerRadius = 26.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GPU Controls", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = onResetGpu,
                    enabled = !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Reset Baseline")
                }
            }

            if (gpu.availableGovernors.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GPU Governor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        FilledTonalButton(
                            onClick = { govMenu = true },
                            enabled = !state.busy,
                            shape = CircleShape,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("gpu_gov_button")
                        ) {
                            Text(gpu.currentGovernor.ifBlank { "Select" })
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Select GPU governor")
                        }
                        DropdownMenu(expanded = govMenu, onDismissRequest = { govMenu = false }) {
                            gpu.availableGovernors.forEach { gov ->
                                DropdownMenuItem(
                                    text = { Text(gov) },
                                    onClick = {
                                        govMenu = false
                                        onSetGpuGovernor(gov)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val freqs = gpu.availableMhz
            if (freqs.size >= 2) {
                Spacer(Modifier.height(12.dp))
                var minIdx by remember(gpu.minMhz, freqs) {
                    mutableStateOf(freqs.indexOfFirst { it >= (gpu.minMhz ?: freqs.first()) }.coerceAtLeast(0))
                }
                var maxIdx by remember(gpu.maxMhz, freqs) {
                    mutableStateOf(freqs.indexOfLast { it <= (gpu.maxMhz ?: freqs.last()) }.coerceAtLeast(0))
                }

                Text(
                    text = "GPU Min Frequency: ${freqs[minIdx.coerceIn(0, freqs.lastIndex)]} MHz",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMono
                )
                GlassSlider(
                    value = minIdx.toFloat(),
                    onValueChange = { minIdx = it.roundToInt().coerceIn(0, maxIdx) },
                    onValueChangeFinished = { onSetGpuRange(freqs[minIdx.coerceIn(0, maxIdx)], null) },
                    valueRange = 0f..freqs.lastIndex.toFloat(),
                    steps = (freqs.size - 2).coerceAtLeast(0)
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "GPU Max Frequency: ${freqs[maxIdx.coerceIn(0, freqs.lastIndex)]} MHz",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMono
                )
                GlassSlider(
                    value = maxIdx.toFloat(),
                    onValueChange = { maxIdx = it.roundToInt().coerceIn(minIdx, freqs.lastIndex) },
                    onValueChangeFinished = { onSetGpuRange(null, freqs[maxIdx.coerceIn(minIdx, freqs.lastIndex)]) },
                    valueRange = 0f..freqs.lastIndex.toFloat(),
                    steps = (freqs.size - 2).coerceAtLeast(0)
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "GPU frequency table not exposed as selectable steps by this kernel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(92.dp))
    }
}
