package com.evorez.ezdmanager.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evorez.ezdmanager.model.CpuCore
import com.evorez.ezdmanager.model.CpuPolicy
import com.evorez.ezdmanager.model.PrivilegeMode
import com.evorez.ezdmanager.model.ToolId
import com.evorez.ezdmanager.root.ToolAuthorityBadge
import com.evorez.ezdmanager.root.WorkModeManager
import com.evorez.ezdmanager.ui.components.CollapsibleSection
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.components.SectionHeader
import com.evorez.ezdmanager.ui.components.captureLaunchBounds
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState

fun toolGridIcon(tool: ToolId): ImageVector = when (tool) {
    ToolId.CPU_GPU_CONTROL -> Icons.Rounded.Memory
    ToolId.ZRAM_VM -> Icons.Rounded.SdStorage
    ToolId.KERNEL_TWEAKS -> Icons.Rounded.Tune
    ToolId.DISPLAY -> Icons.Rounded.DisplaySettings
    ToolId.TOUCH -> Icons.Rounded.TouchApp
    ToolId.NETWORK -> Icons.Rounded.Dns
    ToolId.AUDIO_ENHANCER -> Icons.Rounded.GraphicEq
    ToolId.BATTERY_SAVING -> Icons.Rounded.BatterySaver
    ToolId.FAST_CHARGE -> Icons.Rounded.Bolt
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
    state: EzdUiState,
    currentAuthority: PrivilegeMode = state.effectivePrivilegeMode,
    pinnedTools: List<ToolId> = state.settings.pinnedPillTools,
    onSelectTool: (ToolId) -> Unit,
    onRequestRoot: () -> Unit = {},
    onRequestShizuku: () -> Unit = {},
    onApplyProfile: (String) -> Unit = {},
    onSetCpuRange: (CpuPolicy, Long?, Long?) -> Unit = { _, _, _ -> },
    onSetCpuGovernor: (CpuPolicy, String) -> Unit = { _, _ -> },
    onResetCpuPolicy: (CpuPolicy) -> Unit = {},
    onToggleCore: (CpuCore, Boolean) -> Unit = { _, _ -> },
    onSetGpuRange: (Long?, Long?) -> Unit = { _, _ -> },
    onSetGpuGovernor: (String) -> Unit = {},
    onResetGpu: () -> Unit = {},
    onToolDragStart: (ToolId, Offset) -> Unit = { _, _ -> },
    onToolDragMove: (Offset) -> Unit = {},
    onToolDragEnd: () -> Unit = {},
    onToolDragCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val visuals = LocalEzdVisuals.current
    var lockedToolNotice by remember { mutableStateOf<String?>(null) }
    var cpuGpuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = "PERFORMANCE & ENGINE HUB",
            subtitle = "CPU Clusters, GPU Clocks, Hotplugging & Profiles"
        )

        // Authority Notice Banner (merged from Performance Hub)
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

        // 3 Performance Profiles Quick Switcher (moved from Performance Hub)
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
                            .testTag("tools_profile_$id"),
                        shape = RoundedCornerShape(18.dp),
                        color = visuals.solidSurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) visuals.accent else visuals.glassBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) visuals.accent.copy(alpha = 0.18f) else Color.Transparent)
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

        // Full Unified CPU & GPU Engine Controls (moved from Performance Hub without losing any controls)
        GlassCard(cornerRadius = 26.dp) {
            CollapsibleSection(
                title = "CPU & GPU Tuning Engine",
                subtitle = "Cluster clocks, governors, core hotplug & GPU",
                expanded = cpuGpuExpanded,
                onToggle = { cpuGpuExpanded = !cpuGpuExpanded },
                testTag = "tools_cpu_gpu_collapse_toggle",
                trailingBadge = if (state.cpuPolicies.isNotEmpty()) "${state.cpuPolicies.size} clusters" else "Open"
            ) {
                Spacer(Modifier.height(8.dp))
                CpuGpuScreen(
                    state = state,
                    onSetCpuRange = onSetCpuRange,
                    onSetCpuGovernor = onSetCpuGovernor,
                    onResetCpuPolicy = onResetCpuPolicy,
                    onToggleCore = onToggleCore,
                    onSetGpuRange = onSetGpuRange,
                    onSetGpuGovernor = onSetGpuGovernor,
                    onResetGpu = onResetGpu,
                    onOpenProfiles = { onSelectTool(ToolId.PROFILES) }
                )
            }
        }

        // Quick Jump Shortcuts from Performance Hub
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTool(ToolId.ZRAM_VM) },
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
                    .clickable { onSelectTool(ToolId.KERNEL_TWEAKS) },
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

        Spacer(Modifier.height(4.dp))

        SectionHeader(
            title = "SYSTEM TOOLBOX",
            subtitle = "Tap to open · Hold & drag any card onto the bottom pill to pin"
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

        // Tools Grid (long-press drag onto Floating Pill to pin shortcut)
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
                    val isPinned = tool in pinnedTools

                    ToolGridCard(
                        tool = tool,
                        badge = badge,
                        isPinned = isPinned,
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
                        onDragStartRoot = { pos -> onToolDragStart(tool, pos) },
                        onDragMoveRoot = onToolDragMove,
                        onDragEndRoot = onToolDragEnd,
                        onDragCancelRoot = onToolDragCancel,
                        modifier = Modifier
                            .weight(1f)
                            .captureLaunchBounds()
                            .testTag("tool_card_${tool.name}")
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(if (pinnedTools.isEmpty()) 128.dp else 164.dp))
    }

    lockedToolNotice?.let { msg ->
        AlertDialog(
            onDismissRequest = { lockedToolNotice = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = visuals.solidSurfaceElevated,
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
    isPinned: Boolean,
    onClick: () -> Unit,
    onDragStartRoot: (Offset) -> Unit,
    onDragMoveRoot: (Offset) -> Unit,
    onDragEndRoot: () -> Unit,
    onDragCancelRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    val isLocked = badge.isStrictlyLocked
    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    var activePointerRoot by remember { mutableStateOf(Offset.Zero) }

    GlassCard(
        modifier = modifier
            .heightIn(min = 128.dp)
            .onGloballyPositioned { coords ->
                originInRoot = coords.positionInRoot()
            }
            .pointerInput(tool) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { localOffset ->
                        activePointerRoot = originInRoot + localOffset
                        onDragStartRoot(activePointerRoot)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        activePointerRoot += dragAmount
                        onDragMoveRoot(activePointerRoot)
                    },
                    onDragEnd = { onDragEndRoot() },
                    onDragCancel = { onDragCancelRoot() }
                )
            }
            .then(if (isLocked) Modifier.alpha(0.65f) else Modifier),
        cornerRadius = 26.dp,
        highlightBorder = isPinned,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            GlowIcon(
                icon = toolGridIcon(tool),
                contentDescription = tool.title,
                tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                size = 24.dp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned to pill",
                        tint = visuals.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
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
