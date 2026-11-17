package com.evorez.ezdmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.model.ControlMode
import com.evorez.ezdmanager.model.ToolId
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.NeonIconBadge
import com.evorez.ezdmanager.ui.components.TopBrandBar
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState

private fun toolIcon(tool: ToolId): ImageVector = when (tool) {
    ToolId.DEVICE_SPOOF -> Icons.Rounded.Smartphone
    ToolId.NETWORK_DNS -> Icons.Rounded.Dns
    ToolId.BATTERY_CHARGE -> Icons.Rounded.BatteryChargingFull
    ToolId.AUDIO_ENHANCER -> Icons.Rounded.GraphicEq
    ToolId.PERFORMANCE_HUD -> Icons.Rounded.Speed
    ToolId.COLOR_DISPLAY -> Icons.Rounded.Palette
    ToolId.TOUCH_RESPONSE -> Icons.Rounded.Fingerprint
    ToolId.APP_MANAGER -> Icons.Rounded.Apps
    ToolId.PROFILES -> Icons.Rounded.Tune
    ToolId.TASK_MONITOR -> Icons.Rounded.Memory
    ToolId.ROOT_FILES -> Icons.Rounded.FolderOpen
    ToolId.ROOT_TERMINAL -> Icons.Rounded.Terminal
}

@Composable
fun ToolsScreen(
    state: EzdUiState,
    onSearchClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSelectTool: (ToolId) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val isPro = state.settings.controlMode == ControlMode.PRO
    val visibleTools = ToolId.entries.filter { isPro || !it.proOnly }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopBrandBar(
            title = "System Tools",
            subtitle = if (isPro) "Pro Toolbox · All hardware & root utilities" else "Lite Toolbox · Recommended essentials",
            showLogo = false,
            onSearchClick = onSearchClick,
            onRefreshClick = onRefreshClick
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            visibleTools.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { tool ->
                        ToolGridCard(
                            tool = tool,
                            onClick = { onSelectTool(tool) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tool_card_${tool.name}")
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    tool: ToolId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    GlassCard(
        modifier = modifier.heightIn(min = 128.dp),
        cornerRadius = 26.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            NeonIconBadge(
                icon = toolIcon(tool),
                contentDescription = tool.title,
                accent = visuals.neonAccent,
                size = 40.dp
            )
            if (tool.proOnly) {
                Surface(
                    shape = CircleShape,
                    color = visuals.neonAccent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, visuals.neonAccent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = visuals.neonAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = tool.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tool.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
