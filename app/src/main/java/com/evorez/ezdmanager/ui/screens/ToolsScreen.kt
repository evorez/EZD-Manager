package com.evorez.ezdmanager.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.model.PrivilegeMode
import com.evorez.ezdmanager.model.ToolId
import com.evorez.ezdmanager.root.ToolAuthorityBadge
import com.evorez.ezdmanager.root.WorkModeManager
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.components.SectionHeader
import com.evorez.ezdmanager.ui.components.captureLaunchBounds
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals

private fun toolIcon(tool: ToolId): ImageVector = when (tool) {
    ToolId.CPU_GPU_CONTROL -> Icons.Rounded.Memory
    ToolId.ZRAM_VM -> Icons.Rounded.SdStorage
    ToolId.KERNEL_TWEAKS -> Icons.Rounded.Tune
    ToolId.DISPLAY -> Icons.Rounded.DisplaySettings
    ToolId.TOUCH -> Icons.Rounded.TouchApp
    ToolId.NETWORK -> Icons.Rounded.Dns
    ToolId.AUDIO_ENHANCER -> Icons.Rounded.GraphicEq
    ToolId.BATTERY_SAVING -> Icons.Rounded.BatterySaver
    ToolId.DEVICE_SPOOF -> Icons.Rounded.Smartphone
    ToolId.PROFILES -> Icons.Rounded.Speed
    ToolId.MODULES -> Icons.Rounded.Extension
    ToolId.BUILD_PROP -> Icons.Rounded.EditNote
    ToolId.BOOT_GUARD -> Icons.Rounded.Shield
    ToolId.EZD_BACKUP -> Icons.Rounded.SettingsBackupRestore
    ToolId.APP_BACKUP -> Icons.Rounded.InstallMobile
    ToolId.ROOT_TERMINAL -> Icons.Rounded.Terminal
}

@Composable
fun ToolsScreen(
    currentAuthority: PrivilegeMode = PrivilegeMode.BASIC,
    onSelectTool: (ToolId) -> Unit,
    onRequestRoot: () -> Unit = {},
    onRequestShizuku: () -> Unit = {}
) {
    val context = LocalContext.current
    val visuals = LocalEzdVisuals.current
    var lockedToolNotice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "SYSTEM TOOLBOX",
            subtitle = "Modules, Build.prop, Backups, Shell & Hardware Tweaks"
        )

        // WorkMode Authority Status Banner
        GlassCard(cornerRadius = 22.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlowIcon(
                    icon = when (currentAuthority) {
                        PrivilegeMode.ROOT -> Icons.Rounded.Security
                        PrivilegeMode.SHIZUKU -> Icons.Rounded.Terminal
                        PrivilegeMode.BASIC -> Icons.Rounded.Visibility
                    },
                    contentDescription = "Authority Level",
                    tint = when (currentAuthority) {
                        PrivilegeMode.ROOT -> Color(0xFF22C55E)
                        PrivilegeMode.SHIZUKU -> Color(0xFF3B82F6)
                        PrivilegeMode.BASIC -> Color(0xFFF59E0B)
                    },
                    size = 24.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Authority: ${currentAuthority.label} Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (currentAuthority) {
                            PrivilegeMode.ROOT -> "Full kernel, sysfs, and module management authorized."
                            PrivilegeMode.SHIZUKU -> "Elevated ADB shell authorized. Kernel writes require Root."
                            PrivilegeMode.BASIC -> "Safe monitor mode. Grant Root or Shizuku for system writes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (currentAuthority != PrivilegeMode.ROOT) {
                    TextButton(
                        onClick = {
                            if (currentAuthority == PrivilegeMode.BASIC) onRequestRoot() else onRequestShizuku()
                        }
                    ) {
                        Text(if (currentAuthority == PrivilegeMode.BASIC) "Elevate" else "Refresh")
                    }
                }
            }
        }

        // Tools Grid
        ToolId.entries.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { tool ->
                    val badge = remember(tool, currentAuthority) {
                        WorkModeManager.getToolBadge(tool, currentAuthority)
                    }
                    val isLocked = badge.isStrictlyLocked

                    ToolGridCard(
                        tool = tool,
                        badge = badge,
                        onClick = {
                            if (isLocked) {
                                val explanation = WorkModeManager.getAuthorityNotice(tool, currentAuthority)
                                    ?: "Requires Root access to function."
                                lockedToolNotice = "${tool.title}: $explanation"
                                Toast.makeText(context, "${tool.title} requires Root access", Toast.LENGTH_SHORT).show()
                            } else {
                                onSelectTool(tool)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .captureLaunchBounds()
                            .testTag("tool_card_${tool.name}")
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(124.dp))
    }

    lockedToolNotice?.let { msg ->
        AlertDialog(
            onDismissRequest = { lockedToolNotice = null },
            shape = RoundedCornerShape(26.dp),
            title = { Text("Permission Required") },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        lockedToolNotice = null
                        onRequestRoot()
                    }
                ) {
                    Text("Request Root")
                }
            },
            dismissButton = {
                TextButton(onClick = { lockedToolNotice = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ToolGridCard(
    tool: ToolId,
    badge: ToolAuthorityBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = badge.isStrictlyLocked

    GlassCard(
        modifier = modifier
            .heightIn(min = 126.dp)
            .then(if (isLocked) Modifier.alpha(0.65f) else Modifier),
        cornerRadius = 26.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            GlowIcon(
                icon = toolIcon(tool),
                contentDescription = tool.title,
                tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                size = 24.dp
            )

            // Dynamic Authority Badge
            Surface(
                shape = CircleShape,
                color = Color(badge.colorArgb).copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Color(badge.colorArgb).copy(alpha = 0.45f))
            ) {
                Text(
                    text = badge.label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(badge.colorArgb),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = tool.title,
            style = MaterialTheme.typography.titleMedium,
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
