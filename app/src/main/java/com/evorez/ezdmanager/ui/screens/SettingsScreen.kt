package com.evorez.ezdmanager.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.ui.components.CollapsibleSection
import com.evorez.ezdmanager.ui.components.GlassCard
import com.evorez.ezdmanager.ui.components.StatusPill
import com.evorez.ezdmanager.ui.components.TopBrandBar
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.ui.theme.buildVisualTokens
import com.evorez.ezdmanager.viewmodel.EzdUiState

@Composable
fun SettingsScreen(
    state: EzdUiState,
    daemonStats: DaemonStats,
    onSearchClick: () -> Unit,
    onSetControlMode: (ControlMode) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetThemeStyle: (EzdThemeStyle) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetPillPosition: (PillPosition) -> Unit,
    onToggleDaemon: (Boolean) -> Unit,
    onBuildDeviceReport: () -> String
) {
    val context = LocalContext.current
    val visuals = LocalEzdVisuals.current
    var themesExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TopBrandBar(
            title = "Settings",
            subtitle = "Themes, mode, daemon & author",
            showLogo = true,
            onSearchClick = onSearchClick
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Author Profile Card (Flower-crown portrait on left, Evorez 🪽, Evolve & Rise, GitHub @Evorez & Telegram -> @evorezlabs)
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
                            .border(2.dp, visuals.neonAccent, CircleShape)
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
                            color = visuals.neonAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "EZD Manager · v1.0",
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
                        label = "@Evorez",
                        badgeText = "GH",
                        icon = Icons.Rounded.Code,
                        onClick = { openUrl(context, "https://github.com/Evorez") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("author_github_pill")
                    )
                    AuthorLinkPill(
                        label = "@Evorez",
                        badgeText = "TG",
                        icon = Icons.Rounded.Send,
                        onClick = { openUrl(context, "https://t.me/evorezlabs") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("author_telegram_pill")
                    )
                }
            }

            // Control Mode: Pro Mode (default) vs Lite Mode
            GlassCard(cornerRadius = 26.dp) {
                Text("Control Mode", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pro Mode enables maximum features (default). Lite Mode simplifies Home & Tools with Recommended presets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeCardChip(
                        title = "Pro Mode",
                        subtitle = "Max features unlocked",
                        selected = state.settings.controlMode == ControlMode.PRO,
                        onClick = { onSetControlMode(ControlMode.PRO) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeCardChip(
                        title = "Lite Mode",
                        subtitle = "Recommended controls",
                        selected = state.settings.controlMode == ControlMode.LITE,
                        onClick = { onSetControlMode(ControlMode.LITE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Collapsible Theme Styles (3 White incl. Warm Beige + 4 Black + 1 Special Edition = 8 Styles + System/Light/Dark)
            GlassCard(cornerRadius = 26.dp) {
                CollapsibleSection(
                    title = "Theme Styles",
                    subtitle = "3 White · 4 Black · 1 Special Edition",
                    expanded = themesExpanded,
                    onToggle = { themesExpanded = !themesExpanded },
                    testTag = "theme_styles_collapse_toggle",
                    trailingBadge = state.settings.themeStyle.displayName
                ) {
                    Spacer(Modifier.height(6.dp))
                    Text("Brightness Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label) },
                                shape = CircleShape,
                                modifier = Modifier.testTag("theme_mode_${mode.name}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    ThemeCategory.entries.forEach { category ->
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = visuals.neonAccent,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        EzdThemeStyle.entries.filter { it.category == category }.forEach { style ->
                            val tokens = remember(style) { buildVisualTokens(style) }
                            val selected = state.settings.themeStyle == style
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onSetThemeStyle(style) }
                                    .testTag("theme_style_${style.name}"),
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) visuals.neonAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                                border = BorderStroke(1.dp, if (selected) visuals.neonAccent else visuals.glassBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                        Box(
                                            Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(tokens.bgBase)
                                                .border(1.dp, tokens.neonAccent, CircleShape)
                                        )
                                        Box(
                                            Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(tokens.neonAccent)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(style.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                        Text(style.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (selected) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = visuals.neonAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Pill Navigation Location & Language
            GlassCard(cornerRadius = 26.dp) {
                Text("Floating Pill Navigation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Tip: Long-tap Settings on the floating pill anytime to move it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillPosition.entries.forEach { pos ->
                        FilterChip(
                            selected = state.settings.pillPosition == pos,
                            onClick = { onSetPillPosition(pos) },
                            label = { Text(pos.label) },
                            shape = CircleShape
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                Text("Language", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = state.settings.language == lang,
                            onClick = { onSetLanguage(lang) },
                            label = { Text(lang.label) },
                            shape = CircleShape
                        )
                    }
                }
            }

            // Persistent Root Daemon Status Card
            GlassCard(cornerRadius = 26.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Root Background Daemon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = if (daemonStats.serviceRunning) {
                                "Active · ${daemonStats.commandsExecuted} commands executed asynchronously"
                            } else {
                                "Stopped"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.settings.daemonEnabled,
                        onCheckedChange = onToggleDaemon
                    )
                }
            }

            // Device Information & Copy/Share (moved out of bottom nav as requested!)
            GlassCard(cornerRadius = 26.dp) {
                Text("Device Information", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Copy or share full live hardware, CPU cluster, GPU & display diagnostics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("EZD Device Info", onBuildDeviceReport()))
                            Toast.makeText(context, "Device info copied", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Info")
                    }
                    OutlinedButton(
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, onBuildDeviceReport())
                            }
                            context.startActivity(Intent.createChooser(share, "Share Device Info"))
                        },
                        shape = CircleShape,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorLinkPill(
    label: String,
    badgeText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        color = visuals.neonAccent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, visuals.neonAccent.copy(alpha = 0.50f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = badgeText, tint = visuals.neonAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ModeCardChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalEzdVisuals.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) visuals.neonAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.2.dp, if (selected) visuals.neonAccent else visuals.glassBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) visuals.neonAccent else MaterialTheme.colorScheme.onSurface
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
