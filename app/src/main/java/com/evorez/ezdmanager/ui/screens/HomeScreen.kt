package com.evorez.ezdmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.profiles.ProfileRepository
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState

@Composable
fun HomeScreen(
    state: EzdUiState,
    daemonStats: DaemonStats,
    onRetryRoot: () -> Unit,
    onShowDetails: () -> Unit,
    onApplyProfile: (ProfileConfig) -> Unit,
    onOpenTab: (MainTab) -> Unit,
    onOpenTool: (ToolId) -> Unit
) {
    val visuals = LocalEzdVisuals.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RootStatusCard(rootStatus = state.rootStatus, onRetryRoot = onRetryRoot)

        AnimatedVisibility(visible = state.statusBanner.isNotBlank()) {
            GlassCard(cornerRadius = 22.dp, highlightBorder = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlowIcon(Icons.Rounded.Info, contentDescription = null, tint = visuals.subtleRedAccent, size = 20.dp)
                    Text(
                        text = state.statusBanner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        DeviceOverviewCard(overview = state.overview, onDetailsClick = onShowDetails)

        // CPU & GPU Live Summary Card linking cleanly to CPU / GPU tabs
        GlassCard(cornerRadius = 26.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Performance Summary", style = MaterialTheme.typography.titleLarge)
                StatusPill(
                    label = if (daemonStats.activeGamePkg.isNotBlank()) "Game Active" else "${state.cpuPolicies.size} Clusters",
                    accent = visuals.subtleRedAccent
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryStatChip(
                    title = "CPU",
                    value = state.overview.cpuUsagePercent?.let { "$it%" } ?: "--",
                    sub = state.cpuPolicies.lastOrNull()?.currentKhz?.let { "${it / 1000} MHz" } ?: "Idle",
                    icon = Icons.Rounded.Memory,
                    onClick = { onOpenTab(MainTab.CPU) },
                    modifier = Modifier.weight(1f)
                )
                SummaryStatChip(
                    title = "GPU",
                    value = state.gpuState.usagePercent?.let { "$it%" } ?: "--",
                    sub = state.gpuState.currentMhz?.let { "$it MHz" } ?: "N/A",
                    icon = Icons.Rounded.Speed,
                    onClick = { onOpenTab(MainTab.GPU) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Game Space + Quick Profile Pills
        val quickProfiles = ProfileRepository.builtInProfiles()
        val activeName = quickProfiles.find { it.id == state.settings.activeProfileId }?.name ?: "Balanced"
        GlassCard(cornerRadius = 26.dp, highlightBorder = state.settings.gameSpace.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlowIcon(Icons.Rounded.SportsEsports, contentDescription = "Game Space", tint = visuals.subtleRedAccent, size = 24.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Game Space & Profiles", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Active: $activeName · ${state.settings.gameSpace.selectedGames.size} selected game(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onOpenTool(ToolId.GAME_SPACE) },
                    shape = CircleShape,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("home_open_game_space")
                ) {
                    Text("Open")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickProfiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == state.settings.activeProfileId,
                        onClick = { onApplyProfile(profile) },
                        label = { Text(profile.name) },
                        shape = CircleShape,
                        modifier = Modifier.testTag("home_profile_${profile.id}")
                    )
                }
            }
        }

        // Quick Actions Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionPill("Battery Saving", Icons.Rounded.BatterySaver) { onOpenTool(ToolId.BATTERY_SAVING) }
            QuickActionPill("Network", Icons.Rounded.Dns) { onOpenTool(ToolId.NETWORK) }
            QuickActionPill("Display", Icons.Rounded.Tune) { onOpenTool(ToolId.DISPLAY) }
            QuickActionPill("Spoof", Icons.Rounded.Smartphone) { onOpenTool(ToolId.DEVICE_SPOOF) }
            QuickActionPill("Backup", Icons.Rounded.Backup) { onOpenTool(ToolId.BACKUP_RESTORE) }
        }

        Spacer(Modifier.height(92.dp))
    }
}

@Composable
private fun RootStatusCard(
    rootStatus: RootStatus,
    onRetryRoot: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val (title, subtitle, tint, icon) = when (rootStatus) {
        RootStatus.GRANTED -> listOf("Root Granted", "Superuser shell & persistent daemon active", visuals.successColor, Icons.Rounded.VerifiedUser)
        RootStatus.CHECKING -> listOf("Detecting Root Access…", "Probing su binary & daemon", visuals.subtleRedAccent, Icons.Rounded.Sync)
        RootStatus.DENIED -> listOf("Root Denied", "su binary found, grant permission in Magisk/KSU", visuals.warningColor, Icons.Rounded.GppBad)
        else -> listOf("Root Unavailable", "No su binary detected · Read-only inspection", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Lock)
    }

    GlassCard(cornerRadius = 24.dp, highlightBorder = rootStatus == RootStatus.GRANTED) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlowIcon(
                icon = icon as ImageVector,
                contentDescription = title as String,
                tint = tint as androidx.compose.ui.graphics.Color,
                size = 24.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle as String, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rootStatus != RootStatus.GRANTED && rootStatus != RootStatus.CHECKING) {
                OutlinedButton(
                    onClick = onRetryRoot,
                    shape = CircleShape,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("retry_root_button")
                ) {
                    Text("Retry")
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
                Text("Specs", color = visuals.subtleRedAccent)
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
private fun SummaryStatChip(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = visuals.glassSurfaceElevated,
        border = BorderStroke(1.dp, visuals.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlowIcon(icon = icon, contentDescription = title, tint = visuals.subtleRedAccent, size = 18.dp)
            Column {
                Text("$title $value", style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono)
                Text(sub, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = visuals.glassSurfaceElevated,
        border = BorderStroke(1.dp, visuals.glassBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlowIcon(icon, contentDescription = label, tint = visuals.subtleRedAccent, size = 18.dp)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
