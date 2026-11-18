package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.WorkModeManager
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.components.SectionHeader
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState

/**
 * PerformanceScreen: Dedicated 2nd Tab in the 4-tab Navigation Pill.
 * Organizes hardware tuning:
 * 1. Authority level banner (Full Root vs Read-only Telemetry).
 * 2. 3 Performance Profiles (Balanced, Performance, Powersave) with 1-tap toggle.
 * 3. Unified CPU & GPU Engine with live clocks, frequency sliders, governors, and hotplug.
 * 4. Quick navigation shortcuts to ZRAM/VM and Thermal tools.
 */
@Composable
fun PerformanceScreen(
    state: EzdUiState,
    onApplyProfile: (String) -> Unit,
    onSetCpuRange: (CpuPolicy, Long?, Long?) -> Unit,
    onSetCpuGovernor: (CpuPolicy, String) -> Unit,
    onResetCpuPolicy: (CpuPolicy) -> Unit,
    onToggleCore: (CpuCore, Boolean) -> Unit,
    onSetGpuRange: (Long?, Long?) -> Unit,
    onSetGpuGovernor: (String) -> Unit,
    onResetGpu: () -> Unit,
    onOpenTool: (ToolId) -> Unit,
    onRequestRoot: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val currentAuthority = state.effectivePrivilegeMode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "PERFORMANCE HUB",
            subtitle = "CPU Clusters, GPU Clocks, Hotplugging & Profiles"
        )

        // Authority Notice Banner
        if (currentAuthority != PrivilegeMode.ROOT) {
            GlassCard(cornerRadius = 22.dp, highlightBorder = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlowIcon(
                        icon = Icons.Rounded.Info,
                        contentDescription = "Authority info",
                        tint = Color(0xFFF59E0B),
                        size = 24.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Telemetry Mode (${currentAuthority.label})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF59E0B)
                        )
                        Text(
                            text = "Live telemetry is active. Root (su) is required to write frequency limits and change governors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onRequestRoot) {
                        Text("Grant Root")
                    }
                }
            }
        }

        // 3 Performance Profiles Quick Switcher
        GlassCard(cornerRadius = 26.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Performance Profile", style = MaterialTheme.typography.titleMedium)
                Surface(
                    shape = CircleShape,
                    color = visuals.accent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, visuals.accent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = state.settings.activeProfileId.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = visuals.accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val profiles = listOf(
                Triple("balanced", "Balanced", Icons.Rounded.Balance),
                Triple("performance", "Boost", Icons.Rounded.Bolt),
                Triple("powersave", "Powersave", Icons.Rounded.BatterySaver)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { (id, label, icon) ->
                    val selected = state.settings.activeProfileId.equals(id, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onApplyProfile(id) }
                            .testTag("perf_tab_profile_$id"),
                        shape = RoundedCornerShape(18.dp),
                        color = visuals.solidSurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) visuals.accent else visuals.glassBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) visuals.accent.copy(alpha = 0.20f) else Color.Transparent)
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) visuals.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) visuals.accent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Full Unified CPU & GPU screen controls
        CpuGpuScreen(
            state = state,
            onSetCpuRange = onSetCpuRange,
            onSetCpuGovernor = onSetCpuGovernor,
            onResetCpuPolicy = onResetCpuPolicy,
            onToggleCore = onToggleCore,
            onSetGpuRange = onSetGpuRange,
            onSetGpuGovernor = onSetGpuGovernor,
            onResetGpu = onResetGpu,
            onOpenProfiles = { onOpenTool(ToolId.PROFILES) }
        )

        // Quick Jump Shortcuts to Related Performance Tools
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenTool(ToolId.ZRAM_VM) },
                shape = RoundedCornerShape(20.dp),
                color = visuals.solidSurfaceElevated,
                border = BorderStroke(1.dp, visuals.glassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlowIcon(Icons.Rounded.SdStorage, contentDescription = null, tint = visuals.accent, size = 20.dp)
                    Column {
                        Text("ZRAM & VM", style = MaterialTheme.typography.labelLarge)
                        Text("Swap & cache", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenTool(ToolId.KERNEL_TWEAKS) },
                shape = RoundedCornerShape(20.dp),
                color = visuals.solidSurfaceElevated,
                border = BorderStroke(1.dp, visuals.glassBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlowIcon(Icons.Rounded.Tune, contentDescription = null, tint = visuals.accent, size = 20.dp)
                    Column {
                        Text("I/O & Thermal", style = MaterialTheme.typography.labelLarge)
                        Text("Scheduler & UI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(124.dp))
    }
}
