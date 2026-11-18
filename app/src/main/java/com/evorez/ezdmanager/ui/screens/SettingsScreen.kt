package com.evorez.ezdmanager.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.BuildConfig
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    state: EzdUiState,
    daemonStats: DaemonStats,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPreset: (AccentColorPreset) -> Unit,
    onSetBlurIntensity: (Float) -> Unit,
    onSetReduceAnimations: (Boolean) -> Unit,
    onSetFloatingPillOpaque: (Boolean) -> Unit,
    onSetBackgroundTexture: (BackgroundTexturePreset) -> Unit,
    onSetBackgroundTextureOpacity: (Float) -> Unit,
    onSetAppFontStyle: (AppFontStyle) -> Unit,
    onRemovePinnedTool: (ToolId) -> Unit,
    onMovePinnedTool: (ToolId, Int) -> Unit,
    onSetNotificationPrefs: (NotificationPrefs) -> Unit,
    onToggleDaemon: (Boolean) -> Unit,
    onOpenTool: (ToolId) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenAppNotifications: () -> Unit,
    onToggleBootMemory: (Boolean) -> Unit,
    onCaptureBootMemory: () -> Unit,
    onApplyBootMemoryNow: () -> Unit,
    onClearSafeMode: () -> Unit,
    onCheckUpdates: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    changelogHistory: List<Pair<String, List<String>>>,
    onBrandLogoTap: () -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalEzdVisuals.current
    val notifs = state.settings.notifications

    // Appearance card collapsed by default per Phase 3 spec
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var permissionsExpanded by rememberSaveable { mutableStateOf(false) }
    var changelogExpanded by rememberSaveable { mutableStateOf(false) }
    var showDaemonLogs by remember { mutableStateOf(false) }

    val grantedCount = listOf(
        state.rootStatus == RootStatus.GRANTED,
        state.usageAccessGranted,
        state.writeSettingsGranted,
        state.notificationsGranted
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header with Dark/Light toggle right next to Settings header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Appearance, textures, fonts, daemon & updates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = visuals.solidSurfaceElevated,
                border = BorderStroke(1.dp, visuals.glassBorder),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val nextMode = if (state.settings.themeMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
                        onSetThemeMode(nextMode)
                    }
                    .testTag("header_dark_light_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (state.settings.themeMode == ThemeMode.LIGHT) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Toggle Dark/Light Mode",
                        tint = visuals.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (state.settings.themeMode == ThemeMode.LIGHT) "Light" else "Dark",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Author Profile Card
        GlassCard(cornerRadius = 28.dp, highlightBorder = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.evorez_avatar),
                    contentDescription = "Evorez Author Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(2.dp, visuals.accent, CircleShape)
                        .clickable(onClick = onBrandLogoTap)
                        .testTag("author_brand_logo")
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "𝙴𝚟𝚘𝚛𝚎𝚣🪽",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Evolve & Rise",
                        style = MaterialTheme.typography.bodyMedium,
                        color = visuals.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "EZD Manager · v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AuthorLinkPill(
                    label = "Evorez",
                    badgeText = "GH",
                    icon = Icons.Rounded.Code,
                    onClick = { openUrl(context, "https://github.com/Evorez") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("author_github_pill")
                )
                AuthorLinkPill(
                    label = "Evorez",
                    badgeText = "TG",
                    icon = Icons.Rounded.Send,
                    onClick = { openUrl(context, "https://t.me/Evorez") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("author_telegram_pill")
                )
            }
        }

        // Collapsible Appearance, Textures & Fonts Card (Collapsed by default)
        GlassCard(cornerRadius = 26.dp) {
            CollapsibleSection(
                title = "Appearance, Textures & Fonts",
                subtitle = "Background textures, font family, accent & glass blur",
                expanded = appearanceExpanded,
                onToggle = { appearanceExpanded = !appearanceExpanded },
                testTag = "appearance_collapse_toggle",
                trailingBadge = "${state.settings.backgroundTexture.label} · ${state.settings.appFontStyle.label.substringBefore(" ")}"
            ) {
                Spacer(Modifier.height(6.dp))

                // 1. Background Texture Selector + Opacity Slider
                Text("Background Texture", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BackgroundTexturePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = state.settings.backgroundTexture == preset,
                            onClick = { onSetBackgroundTexture(preset) },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                            shape = CircleShape,
                            modifier = Modifier.testTag("texture_preset_${preset.name}")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                val texOpacityPct = (state.settings.backgroundTextureOpacity * 100f).roundToInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Texture Opacity", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (state.settings.backgroundTexture == BackgroundTexturePreset.PLAIN) "Plain" else "$texOpacityPct%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMono,
                        color = visuals.accent
                    )
                }
                var sliderTexOpacity by remember(state.settings.backgroundTextureOpacity) {
                    mutableFloatStateOf(state.settings.backgroundTextureOpacity)
                }
                GlassSlider(
                    value = sliderTexOpacity,
                    onValueChange = { sliderTexOpacity = it },
                    onValueChangeFinished = { onSetBackgroundTextureOpacity(sliderTexOpacity) },
                    valueRange = 0f..1f,
                    enabled = state.settings.backgroundTexture != BackgroundTexturePreset.PLAIN
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // 2. App Font Selector
                Text("Typography Font", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppFontStyle.entries.forEach { font ->
                        FilterChip(
                            selected = state.settings.appFontStyle == font,
                            onClick = { onSetAppFontStyle(font) },
                            label = { Text(font.label, style = MaterialTheme.typography.labelMedium) },
                            shape = CircleShape,
                            modifier = Modifier.testTag("font_style_${font.name}")
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // 3. Accent Color
                Text("Accent Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccentColorPreset.entries.forEach { preset ->
                        val swatchColor = preset.argb?.let { Color(it) } ?: visuals.accent
                        FilterChip(
                            selected = state.settings.accentPreset == preset,
                            onClick = { onSetAccentPreset(preset) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                )
                            },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                            shape = CircleShape,
                            modifier = Modifier.testTag("accent_preset_${preset.name}")
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                val blurPercent = (state.settings.blurIntensity * 100f).roundToInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Frosted Glass Blur Intensity", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = if (state.settings.themeMode == ThemeMode.LITE) "Off (Lite)" else "$blurPercent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMono,
                        color = visuals.accent
                    )
                }
                var sliderBlur by remember(state.settings.blurIntensity) { mutableFloatStateOf(state.settings.blurIntensity) }
                GlassSlider(
                    value = sliderBlur,
                    onValueChange = { sliderBlur = it },
                    onValueChangeFinished = { onSetBlurIntensity(sliderBlur) },
                    valueRange = 0f..1f,
                    enabled = state.settings.themeMode != ThemeMode.LITE
                )

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                SettingToggleRow("Reduce Animations", state.settings.reduceAnimations, onSetReduceAnimations)
                SettingToggleRow("Bottom Floating Pill Opaque", state.settings.floatingPillOpaque, onSetFloatingPillOpaque)
                Text(
                    text = "Automatically obeys Android system prefers-reduced-motion scale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.settings.pinnedPillTools.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Pinned Pill Shortcuts (Drag from Tools)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    state.settings.pinnedPillTools.forEachIndexed { idx, tool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(tool.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Row {
                                IconButton(
                                    onClick = { onMovePinnedTool(tool, -1) },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Move Left")
                                }
                                IconButton(
                                    onClick = { onMovePinnedTool(tool, +1) },
                                    enabled = idx < state.settings.pinnedPillTools.lastIndex,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Move Right")
                                }
                                IconButton(
                                    onClick = { onRemovePinnedTool(tool) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Unpin ${tool.title}")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hardware Diagnostics Export
        GlassCard(cornerRadius = 26.dp) {
            Text("Hardware Diagnostics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Exports full CPU, GPU, ZRAM, I/O, kernel, SELinux & display probe results to clipboard and Download/ezd-diag.txt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.probeReport?.let { rep ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Kernel: ${rep.kernelVersion} · SELinux: ${rep.selinuxMode} · Root: ${rep.rootType}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = visuals.accent
                )
            }
            if (state.diagnosticsExportPath.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Saved: ${state.diagnosticsExportPath}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = visuals.successColor
                )
            } else if (state.diagnosticsExportError.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Export error: ${state.diagnosticsExportError}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(10.dp))
            GlassButton(
                text = "Copy diagnostics",
                icon = Icons.Rounded.ContentCopy,
                onClick = onCopyDiagnostics,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_diagnostics_btn")
            )
        }

        // Boot Memory (Auto-Apply Tuning on Device Startup) & Boot-Loop Safe Mode
        GlassCard(cornerRadius = 26.dp, highlightBorder = state.settings.bootMemory.applyOnBoot || state.bootGuardState.safeModeActive) {
            val bootMem = state.settings.bootMemory
            val safeActive = state.bootGuardState.safeModeActive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Boot Memory on Start", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (safeActive) {
                            "Paused: Boot-Loop Safe Mode triggered after 3 failed boots."
                        } else {
                            "Restores saved profile, refresh rate, overlays & DNS after reboot."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (safeActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = bootMem.applyOnBoot && !safeActive,
                    onCheckedChange = onToggleBootMemory,
                    enabled = !safeActive,
                    modifier = Modifier.testTag("boot_memory_switch")
                )
            }
            if (safeActive) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Clear Safe Mode",
                        icon = Icons.Rounded.VerifiedUser,
                        onClick = onClearSafeMode,
                        accent = true,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Boot Guard",
                        icon = Icons.Rounded.Security,
                        onClick = { onOpenTool(ToolId.BOOT_GUARD) },
                        accent = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = bootMem.summary,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMono,
                color = if (bootMem.applyOnBoot) visuals.accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = "Save Snapshot",
                    icon = Icons.Rounded.BookmarkAdd,
                    onClick = onCaptureBootMemory,
                    accent = false,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("boot_memory_capture")
                )
                GlassButton(
                    text = "Apply Now",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onApplyBootMemoryNow,
                    accent = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("boot_memory_apply")
                )
            }
        }

        // Notifications
        GlassCard(cornerRadius = 26.dp) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SettingToggleRow("Root Daemon Alerts", notifs.daemonAlerts) {
                onSetNotificationPrefs(notifs.copy(daemonAlerts = it))
            }
            SettingToggleRow("Battery Saving Alerts", notifs.batteryAlerts) {
                onSetNotificationPrefs(notifs.copy(batteryAlerts = it))
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onOpenAppNotifications,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text("System Alert Channels")
            }
        }

        // Root Daemon & Logs
        GlassCard(cornerRadius = 26.dp, highlightBorder = daemonStats.serviceRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { showDaemonLogs = !showDaemonLogs },
                        onLongClick = { onOpenTool(ToolId.ROOT_TERMINAL) }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Root Background Daemon", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (daemonStats.serviceRunning) {
                            "Active · ${daemonStats.commandsExecuted} cmds · Tap for logs, hold for shell"
                        } else {
                            "Stopped · Foreground automation paused"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = state.settings.daemonEnabled, onCheckedChange = onToggleDaemon)
            }
            if (showDaemonLogs && daemonStats.logs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                daemonStats.logs.takeLast(6).forEach { log ->
                    Text(log, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Collapsible Permissions Status (Collapsed by Default)
        GlassCard(cornerRadius = 26.dp) {
            CollapsibleSection(
                title = "Permissions Status",
                subtitle = "Root, Usage Access, System Settings & Alerts",
                expanded = permissionsExpanded,
                onToggle = { permissionsExpanded = !permissionsExpanded },
                testTag = "permissions_status_collapse_toggle",
                trailingBadge = "$grantedCount/4"
            ) {
                Spacer(Modifier.height(4.dp))
                PermissionStatusRow("Root Access (su)", state.rootStatus == RootStatus.GRANTED, null)
                PermissionStatusRow("Usage Access (Per-App)", state.usageAccessGranted, onOpenUsageAccess)
                PermissionStatusRow("Modify System Settings", state.writeSettingsGranted, onOpenWriteSettings)
                PermissionStatusRow("Notifications", state.notificationsGranted, onOpenAppNotifications)
            }
        }

        // Check for Updates (at very bottom of Settings)
        GlassCard(cornerRadius = 26.dp, highlightBorder = state.releaseInfo.updateAvailable) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check for Updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Installed: v${BuildConfig.VERSION_NAME} · Repo: evorez/EZD-Manager",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = visuals.accent
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.releaseInfo.statusMessage.ifBlank {
                            "Queries GitHub Releases for evorez/EZD-Manager (works offline with clear status)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.releaseInfo.updateAvailable) visuals.successColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.releaseInfo.latestTag.isNotBlank()) {
                    StatusPill(
                        label = state.releaseInfo.latestTag,
                        accent = if (state.releaseInfo.updateAvailable) visuals.successColor else visuals.accent
                    )
                }
            }

            if (state.releaseInfo.updateAvailable && state.releaseInfo.changelogMarkdown.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.releaseInfo.changelogMarkdown.take(360),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = JetBrainsMono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = if (state.checkingUpdate) "Checking…" else "Check for Updates",
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = onCheckUpdates,
                    enabled = !state.checkingUpdate,
                    accent = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("github_update_check_btn")
                )
                if (state.releaseInfo.updateAvailable && state.releaseInfo.downloadUrl.isNotBlank()) {
                    GlassButton(
                        text = "Open Release",
                        icon = Icons.Rounded.OpenInBrowser,
                        onClick = { openUrl(context, state.releaseInfo.downloadUrl) },
                        accent = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            CollapsibleSection(
                title = "Release Changelog",
                subtitle = "Recent EZD Manager builds",
                expanded = changelogExpanded,
                onToggle = { changelogExpanded = !changelogExpanded },
                testTag = "changelog_collapse_toggle",
                trailingBadge = "${changelogHistory.size} builds"
            ) {
                changelogHistory.forEach { (version, notes) ->
                    Spacer(Modifier.height(6.dp))
                    Text(version, style = MaterialTheme.typography.titleSmall, color = visuals.accent, fontFamily = JetBrainsMono)
                    notes.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(if (state.settings.pinnedPillTools.isEmpty()) 128.dp else 164.dp))
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean, onAction: (() -> Unit)?) {
    val visuals = LocalEzdVisuals.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                label = if (granted) "Granted" else "Needed",
                accent = if (granted) visuals.successColor else visuals.warningColor
            )
            if (!granted && onAction != null) {
                TextButton(onClick = onAction) { Text("Open") }
            }
        }
    }
}

@Composable
private fun AuthorLinkPill(
    label: String,
    badgeText: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = visuals.accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, visuals.accent.copy(alpha = 0.44f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = badgeText, tint = visuals.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
