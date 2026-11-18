package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.shortTag
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState

/**
 * Unified CPU & GPU Engine screen combining CPU cluster clocks/governors/hotplug
 * alongside Adreno/Mali GPU frequency & governor controls.
 */
@Composable
fun CpuGpuScreen(
    state: EzdUiState,
    onSetCpuRange: (CpuPolicy, Long?, Long?) -> Unit,
    onSetCpuGovernor: (CpuPolicy, String) -> Unit,
    onResetCpuPolicy: (CpuPolicy) -> Unit,
    onToggleCore: (CpuCore, Boolean) -> Unit,
    onSetGpuRange: (Long?, Long?) -> Unit,
    onSetGpuGovernor: (String) -> Unit,
    onResetGpu: () -> Unit,
    onOpenProfiles: () -> Unit
) {
    val visuals = LocalEzdVisuals.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Unified Hero Card mixing CPU & GPU Telemetry
        GlassCard(cornerRadius = 26.dp, highlightBorder = state.rootStatus == RootStatus.GRANTED) {
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
                    GlowIcon(Icons.Rounded.Memory, contentDescription = "CPU & GPU", tint = visuals.accent, size = 26.dp)
                    Column {
                        Text("Unified CPU & GPU Engine", style = MaterialTheme.typography.titleLarge)
                        val cpuStr = when {
                            state.overview.cpuUsagePercent != null -> "CPU ${state.overview.cpuUsagePercent}%"
                            state.cpuPolicies.isNotEmpty() -> "CPU ${state.cpuPolicies.size} clusters"
                            else -> "CPU ${state.cpuResult.shortTag()}"
                        }
                        val gpuStr = if (state.gpuState.supported) {
                            "GPU ${state.gpuState.currentMhz ?: "?"} MHz (${state.gpuState.usagePercent ?: 0}%)"
                        } else {
                            "GPU ${state.gpuState.probeResult?.shortTag() ?: "Probing"}"
                        }
                        Text(
                            text = "$cpuStr · $gpuStr",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = visuals.accent
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenProfiles,
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Profiles")
                }
            }
        }

        // Combined GPU Control Section (embedded seamlessly)
        GpuScreen(
            state = state,
            onSetGpuRange = onSetGpuRange,
            onSetGpuGovernor = onSetGpuGovernor,
            onResetGpu = onResetGpu
        )

        // Combined CPU Clusters & Core Hotplug Section
        CpuScreen(
            state = state,
            onSetCpuRange = onSetCpuRange,
            onSetCpuGovernor = onSetCpuGovernor,
            onResetCpuPolicy = onResetCpuPolicy,
            onToggleCore = onToggleCore,
            onOpenProfiles = onOpenProfiles
        )
    }
}
