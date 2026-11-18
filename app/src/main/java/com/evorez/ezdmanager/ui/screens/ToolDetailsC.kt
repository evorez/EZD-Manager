package com.evorez.ezdmanager.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.root.shortTag
import com.evorez.ezdmanager.ui.components.*
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import com.evorez.ezdmanager.viewmodel.EzdUiState
import com.evorez.ezdmanager.viewmodel.EzdViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

// ============================================================================
// 1. ZRAM & VM TWEAKS PANEL
// ============================================================================

@Composable
fun ZramVmPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val zv = state.zramVmState
    val rootGranted = state.rootStatus == RootStatus.GRANTED

    var targetSizeMb by remember(zv.diskSizeMb) {
        mutableFloatStateOf(zv.diskSizeMb.toFloat().coerceIn(0f, 8192f))
    }
    var selectedAlgo by remember(zv.currentAlgorithm) {
        mutableStateOf(zv.currentAlgorithm.takeIf { it != "Unavailable" } ?: "lz4")
    }

    var swappiness by remember(zv.swappiness) { mutableFloatStateOf(zv.swappiness.toFloat()) }
    var vfsPressure by remember(zv.vfsCachePressure) { mutableFloatStateOf(zv.vfsCachePressure.toFloat()) }
    var dirtyRatio by remember(zv.dirtyRatio) { mutableFloatStateOf(zv.dirtyRatio.toFloat()) }
    var dirtyBg by remember(zv.dirtyBackgroundRatio) { mutableFloatStateOf(zv.dirtyBackgroundRatio.toFloat()) }

    val algoChoices = zv.availableAlgorithms.ifEmpty { listOf("lz4", "zstd", "lzo-rle", "lzo") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = zv.zramSupported) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ZRAM Compression Engine", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (zv.zramSupported) {
                            "${zv.diskSizeMb} MB · ${zv.currentAlgorithm} · Used ${zv.usedMemMb} MB (Orig ${zv.origDataMb} MB)"
                        } else {
                            "${zv.probeResult?.shortTag() ?: "Pending"}: ${zv.statusNote}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = visuals.accent
                    )
                    if (!zv.zramSupported && zv.pathsTried.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Paths tried: ${zv.pathsTried.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { vm.refreshZramVm() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh ZRAM")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Target ZRAM Size: ${targetSizeMb.roundToInt()} MB",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = JetBrainsMono
            )
            GlassSlider(
                value = targetSizeMb,
                onValueChange = { targetSizeMb = (it / 256f).roundToInt() * 256f },
                valueRange = 0f..8192f,
                enabled = rootGranted && !state.busy
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 1024, 2048, 4096, 6144, 8192).forEach { mb ->
                    FilterChip(
                        selected = targetSizeMb.roundToInt() == mb,
                        onClick = { targetSizeMb = mb.toFloat() },
                        label = { Text(if (mb == 0) "Off" else "${mb / 1024} GB") },
                        shape = CircleShape
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Compression Algorithm", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                algoChoices.forEach { alg ->
                    FilterChip(
                        selected = selectedAlgo == alg,
                        onClick = { selectedAlgo = alg },
                        label = { Text(alg) },
                        shape = CircleShape
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            GlassButton(
                text = "Apply ZRAM Config",
                icon = Icons.Rounded.Memory,
                onClick = { vm.applyZramConfig(targetSizeMb.toLong(), selectedAlgo) },
                enabled = rootGranted && !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
        }

        GlassCard(cornerRadius = 24.dp) {
            SectionHeader("KERNEL VM TUNABLES", "/proc/sys/vm/*")
            Spacer(Modifier.height(8.dp))

            Text("vm.swappiness: ${swappiness.roundToInt()}", style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono)
            GlassSlider(
                value = swappiness,
                onValueChange = { swappiness = it },
                valueRange = 0f..200f,
                enabled = rootGranted && !state.busy
            )

            Text("vm.vfs_cache_pressure: ${vfsPressure.roundToInt()}", style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono)
            GlassSlider(
                value = vfsPressure,
                onValueChange = { vfsPressure = it },
                valueRange = 10f..500f,
                enabled = rootGranted && !state.busy
            )

            Text("vm.dirty_ratio: ${dirtyRatio.roundToInt()}% · bg: ${dirtyBg.roundToInt()}%", style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono)
            GlassSlider(
                value = dirtyRatio,
                onValueChange = {
                    dirtyRatio = it
                    if (dirtyBg > it) dirtyBg = it
                },
                valueRange = 5f..80f,
                enabled = rootGranted && !state.busy
            )
            GlassSlider(
                value = dirtyBg,
                onValueChange = { dirtyBg = it.coerceAtMost(dirtyRatio) },
                valueRange = 2f..50f,
                enabled = rootGranted && !state.busy
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = "Apply VM",
                    icon = Icons.Rounded.Done,
                    onClick = {
                        vm.applyVmTweaks(
                            swappiness.roundToInt(),
                            vfsPressure.roundToInt(),
                            dirtyRatio.roundToInt(),
                            dirtyBg.roundToInt()
                        )
                    },
                    enabled = rootGranted && !state.busy,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        swappiness = 60f
                        vfsPressure = 100f
                        dirtyRatio = 20f
                        dirtyBg = 10f
                        vm.applyVmTweaks(60, 100, 20, 10)
                    },
                    enabled = rootGranted && !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) { Text("Stock") }
            }
        }
    }
}

// ============================================================================
// 2. I/O SCHEDULER, READ-AHEAD, THERMAL PROFILE, ANIMATION SCALE & GPU RENDERER
// ============================================================================

@Composable
fun KernelTweaksPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val k = state.ioThermalUiState
    val rootGranted = state.rootStatus == RootStatus.GRANTED

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Block I/O Schedulers & Read-Ahead
        GlassCard(cornerRadius = 24.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("I/O SCHEDULER & READ-AHEAD", "${k.blockDevices.size} storage queue(s)")
                IconButton(onClick = { vm.refreshIoThermalUi() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh I/O")
                }
            }
            Spacer(Modifier.height(6.dp))
            if (k.blockDevices.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = k.ioStatusNote.ifBlank { "Waiting for /sys/block/*/queue probe…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            label = k.ioProbeResult?.shortTag() ?: "Probing",
                            accent = visuals.warningColor
                        )
                    }
                    if (k.ioPathsTried.isNotEmpty()) {
                        Text(
                            text = "Paths tried: ${k.ioPathsTried.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                k.blockDevices.forEach { dev ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = visuals.solidSurfaceElevated,
                        border = BorderStroke(1.dp, visuals.glassBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${dev.name} · ${dev.currentScheduler} · ${dev.readAheadKb} KB",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMono,
                                color = visuals.accent
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                dev.availableSchedulers.forEach { sch ->
                                    FilterChip(
                                        selected = dev.currentScheduler == sch,
                                        onClick = { vm.setIoScheduler(dev, sch) },
                                        enabled = rootGranted && !state.busy,
                                        label = { Text(sch) },
                                        shape = CircleShape
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(128, 256, 512, 1024, 2048).forEach { kb ->
                                    AssistChip(
                                        onClick = { vm.setIoReadAhead(dev, kb) },
                                        enabled = rootGranted && !state.busy,
                                        label = { Text("${kb}KB") },
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Thermal Throttle Profile
        GlassCard(cornerRadius = 24.dp, highlightBorder = k.thermalProfile != ThermalProfileMode.STOCK) {
            SectionHeader("THERMAL THROTTLE PROFILE", k.thermalBackendNote)
            Spacer(Modifier.height(8.dp))
            ThermalProfileMode.entries.forEach { mode ->
                val selected = k.thermalProfile == mode
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = rootGranted && !state.busy) {
                            vm.applyThermalProfile(mode)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = visuals.solidSurfaceElevated,
                    border = BorderStroke(1.dp, if (selected) visuals.accent else visuals.glassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) visuals.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.titleSmall)
                            Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = visuals.accent)
                        }
                    }
                }
            }
        }

        // System Animation Scale + GPU HWUI Renderer (skiavk / skiagl)
        GlassCard(cornerRadius = 24.dp) {
            SectionHeader("ANIMATION SCALE & GPU RENDERER", "System UI scale + HWUI pipeline")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Window / Transition / Animator Scale: ${k.animatorDurationScale}x",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = JetBrainsMono
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val canSetAnim = state.effectivePrivilegeMode != PrivilegeMode.BASIC
                listOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f, 1.5f).forEach { scale ->
                    FilterChip(
                        selected = kotlin.math.abs(k.animatorDurationScale - scale) < 0.05f,
                        onClick = { vm.setSystemAnimationScale(scale) },
                        enabled = canSetAnim && !state.busy,
                        label = { Text(if (scale == 0f) "Off" else "${scale}x") },
                        shape = CircleShape
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            Text("GPU Renderer Pipeline (debug.hwui.renderer)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            GpuRendererMode.entries.forEach { mode ->
                val selected = k.gpuRenderer == mode
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = rootGranted && !state.busy) {
                            vm.setGpuRenderer(mode)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = visuals.solidSurfaceElevated,
                    border = BorderStroke(1.dp, if (selected) visuals.accent else visuals.glassBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) visuals.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.label, style = MaterialTheme.typography.titleSmall)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = visuals.accent)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. MAGISK / KERNELSU / APATCH MODULE MANAGER
// ============================================================================

@Composable
fun RootModulesPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val rootGranted = state.rootStatus == RootStatus.GRANTED
    var webUiModule by remember { mutableStateOf<RootModuleInfo?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = rootGranted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${state.rootManagerType} Module Manager", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${state.rootModules.size} installed module(s) in /data/adb/modules",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMono,
                        color = visuals.accent
                    )
                }
                IconButton(onClick = { vm.refreshRootModules() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh modules")
                }
            }
        }

        if (!rootGranted) {
            GlassCard(cornerRadius = 24.dp) {
                Text("Grant root access to inspect, launch module WebUIs, or enable/disable Magisk / KernelSU modules.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        if (state.rootModules.isEmpty()) {
            GlassCard(cornerRadius = 24.dp) {
                Text("No modules found in /data/adb/modules.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            state.rootModules.forEach { mod ->
                GlassCard(cornerRadius = 24.dp, highlightBorder = mod.enabled && !mod.pendingRemove) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(mod.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (mod.version.isNotBlank()) StatusPill(mod.version, visuals.accent)
                            }
                            if (mod.author.isNotBlank()) {
                                Text("by ${mod.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = mod.enabled,
                            onCheckedChange = { vm.toggleRootModule(mod, it) },
                            enabled = !state.busy
                        )
                    }
                    if (mod.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(mod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = when {
                                mod.pendingRemove -> "Marked for Uninstall"
                                mod.enabled -> "Enabled"
                                else -> "Disabled"
                            },
                            accent = when {
                                mod.pendingRemove -> MaterialTheme.colorScheme.error
                                mod.enabled -> visuals.successColor
                                else -> visuals.warningColor
                            }
                        )
                        if (mod.hasWebUi) {
                            OutlinedButton(
                                onClick = { webUiModule = mod },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("module_webui_${mod.id}")
                            ) {
                                Icon(Icons.Rounded.Web, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("WebUI")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.toggleRootModuleRemove(mod, !mod.pendingRemove) }) {
                            Text(if (mod.pendingRemove) "Undo Remove" else "Remove on Reboot")
                        }
                    }
                }
            }
        }
    }

    webUiModule?.let { mod ->
        ModuleWebUiDialog(
            module = mod,
            onDismiss = { webUiModule = null }
        )
    }
}

private class KsuWebUiBridge(
    private val context: android.content.Context,
    private val module: RootModuleInfo,
    private val onRunJs: (String) -> Unit
) {
    @JavascriptInterface
    fun exec(cmd: String): String {
        val res = runBlocking { RootShellManager.exec(cmd, timeoutMs = 8_000L) }
        return res.stdoutText
    }

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String) {
        val res = runBlocking { RootShellManager.exec(cmd, timeoutMs = 8_000L) }
        val escapedOut = JSONObject.quote(res.stdoutText)
        val escapedErr = JSONObject.quote(res.stderr.joinToString("\n"))
        onRunJs("if (typeof $callbackFunc === 'function') { $callbackFunc(${res.exitCode}, $escapedOut, $escapedErr); }")
    }

    @JavascriptInterface
    fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun moduleInfo(): String {
        return JSONObject().apply {
            put("id", module.id)
            put("name", module.name)
            put("version", module.version)
            put("author", module.author)
            put("moduleDir", "/data/adb/modules/${module.id}")
        }.toString()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ModuleWebUiDialog(
    module: RootModuleInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val visuals = LocalEzdVisuals.current
    val scope = rememberCoroutineScope()
    var htmlContent by remember(module.id) { mutableStateOf<String?>(null) }
    var loadError by remember(module.id) { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(module.id) {
        withContext(Dispatchers.IO) {
            val qPath = RootShellManager.shellQuote("/data/adb/modules/${module.id}/webroot/index.html")
            val res = RootShellManager.exec("cat $qPath 2>/dev/null", timeoutMs = 4_000L)
            if (res.success && res.stdoutText.isNotBlank()) {
                htmlContent = res.stdoutText
            } else {
                loadError = "Unable to read /data/adb/modules/${module.id}/webroot/index.html"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, visuals.glassBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${module.name} · WebUI", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "/data/adb/modules/${module.id}/webroot",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = visuals.accent
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close WebUI")
                    }
                }
                HorizontalDivider()

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loadError != null -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(loadError!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        htmlContent == null -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        else -> {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.allowFileAccess = true
                                        webChromeClient = WebChromeClient()
                                        webViewClient = WebViewClient()
                                        val bridge = KsuWebUiBridge(ctx, module) { js ->
                                            scope.launch(Dispatchers.Main) {
                                                webViewRef?.evaluateJavascript(js, null)
                                            }
                                        }
                                        addJavascriptInterface(bridge, "ksu")
                                        webViewRef = this
                                        loadDataWithBaseURL(
                                            "file:///data/adb/modules/${module.id}/webroot/",
                                            htmlContent!!,
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. BUILD.PROP EDITOR WITH AUTOMATIC BACKUP
// ============================================================================

@Composable
fun BuildPropEditorPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val rootGranted = state.rootStatus == RootStatus.GRANTED
    var query by remember { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<BuildPropEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(state.buildProps, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.buildProps.take(80)
        else state.buildProps.filter {
            it.key.lowercase().contains(q) || it.value.lowercase().contains(q)
        }.take(80)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(cornerRadius = 24.dp, highlightBorder = true) {
            Text("Safe Build.prop Editor", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Automatically creates a restore backup before every edit and uses systemless resetprop to protect against bootloops.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = "Backup Now",
                    icon = Icons.Rounded.Save,
                    onClick = { vm.createBuildPropBackup() },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Add Prop",
                    icon = Icons.Rounded.Add,
                    onClick = { showAddDialog = true },
                    enabled = rootGranted && !state.busy,
                    accent = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.buildPropBackups.isNotEmpty()) {
            GlassCard(cornerRadius = 24.dp) {
                SectionHeader("SAVED PROP BACKUPS (${state.buildPropBackups.size})", "One-tap rollback if anything misbehaves")
                Spacer(Modifier.height(6.dp))
                state.buildPropBackups.take(5).forEach { bk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bk.createdAt, style = MaterialTheme.typography.titleSmall)
                            Text("${bk.entryCount} entries · ${bk.fileName}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { vm.restoreBuildPropBackup(bk) },
                            enabled = rootGranted && !state.busy,
                            shape = CircleShape
                        ) { Text("Restore") }
                    }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search property key or value…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth()
        )

        filtered.forEach { prop ->
            GlassCard(
                cornerRadius = 20.dp,
                onClick = if (rootGranted) {
                    { editingEntry = prop }
                } else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(prop.key, style = MaterialTheme.typography.titleSmall, fontFamily = JetBrainsMono, color = visuals.accent)
                        Text(prop.value.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMono)
                    }
                    StatusPill(prop.sourceFile, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    editingEntry?.let { entry ->
        var newVal by remember(entry.key) { mutableStateOf(entry.value) }
        GlassDialog(
            title = "Edit Property",
            onDismiss = { editingEntry = null },
            confirmText = "Backup & Apply",
            onConfirm = {
                vm.updateBuildProp(entry.key, newVal)
                editingEntry = null
            }
        ) {
            Text(entry.key, style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono, color = visuals.accent)
            OutlinedTextField(
                value = newVal,
                onValueChange = { newVal = it },
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAddDialog) {
        var newKey by remember { mutableStateOf("") }
        var newVal by remember { mutableStateOf("") }
        GlassDialog(
            title = "Add / Override Property",
            onDismiss = { showAddDialog = false },
            confirmText = "Backup & Set",
            onConfirm = {
                if (newKey.isNotBlank()) vm.updateBuildProp(newKey, newVal)
                showAddDialog = false
            }
        ) {
            OutlinedTextField(value = newKey, onValueChange = { newKey = it }, label = { Text("Property Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = newVal, onValueChange = { newVal = it }, label = { Text("Property Value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ============================================================================
// 5. BOOT-LOOP SAFE MODE GUARD & BOOT MEMORY PANEL
// ============================================================================

@Composable
fun BootGuardPanel(state: EzdUiState, vm: EzdViewModel) {
    val visuals = LocalEzdVisuals.current
    val bg = state.bootGuardState
    val bootMem = state.settings.bootMemory

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(
            cornerRadius = 24.dp,
            highlightBorder = bg.safeModeActive
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlowIcon(
                    icon = if (bg.safeModeActive) Icons.Rounded.Warning else Icons.Rounded.Shield,
                    contentDescription = "Boot Guard",
                    tint = if (bg.safeModeActive) MaterialTheme.colorScheme.error else visuals.successColor,
                    size = 26.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (bg.safeModeActive) "Safe Mode Active (Tweaks & Modules Disabled)" else "Boot-Loop Protection Armed",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (bg.safeModeActive) {
                            bg.lastReason.ifBlank { "Auto-disabled startup tweaks & Magisk/KSU modules after 3 failed boots." }
                        } else {
                            "Automatically disables all startup tweaks and Magisk/KernelSU/APatch modules if 3 consecutive boots fail before stabilizing."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Pending unconfirmed boots: ${bg.pendingBootAttempts}/3 · System Healthy: ${bg.bootConfirmedHealthy}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = JetBrainsMono,
                color = visuals.accent
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bg.safeModeActive) {
                    GlassButton(
                        text = "Exit Safe Mode",
                        icon = Icons.Rounded.VerifiedUser,
                        onClick = { vm.clearBootSafeMode() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedButton(
                        onClick = { vm.testSimulateBootLoopGuard() },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) { Text("Test 3-Fail Guard") }
                }
            }
        }

        GlassCard(cornerRadius = 24.dp, highlightBorder = bootMem.applyOnBoot && !bg.safeModeActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Boot Memory on Start", style = MaterialTheme.typography.titleMedium)
                    Text("Restores profile, refresh rate, overlays & DNS after reboot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = bootMem.applyOnBoot,
                    onCheckedChange = { vm.setApplyOnBoot(it) },
                    enabled = !bg.safeModeActive
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(bootMem.summary, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = visuals.accent)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = "Save Snapshot",
                    icon = Icons.Rounded.BookmarkAdd,
                    onClick = { vm.captureBootMemorySnapshot() },
                    accent = false,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Apply Now",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { vm.applyBootMemoryNow() },
                    enabled = !bg.safeModeActive,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
