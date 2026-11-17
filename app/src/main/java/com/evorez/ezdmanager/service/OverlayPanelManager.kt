package com.evorez.ezdmanager.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.evorez.ezdmanager.MainActivity
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.ui.components.GlowIcon
import com.evorez.ezdmanager.ui.theme.EzdTheme
import com.evorez.ezdmanager.ui.theme.JetBrainsMono
import com.evorez.ezdmanager.ui.theme.LocalEzdVisuals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

data class SidePanelSnapshot(
    val activeAppName: String = "Game Space",
    val batteryPercent: Int = -1,
    val cpuUsage: Int = 0,
    val gpuUsage: Int = 0,
    val cpuMhz: Long = 0L,
    val gpuMhz: Long = 0L,
    val fpsReading: FpsReading = FpsReading(),
    val fpsHistory: List<Int> = emptyList(),
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val refreshHz: Float = 60f,
    val netLabel: String = "Online",
    val activeProfile: GameProfileId = GameProfileId.BALANCED,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val config: GameSpaceConfig = GameSpaceConfig()
)

private class OverlayComposeOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun attach() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun detach() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

class OverlayPanelManager(
    private val context: Context,
    private val onSelectGameProfile: (GameProfileId) -> Unit,
    private val onToggleShortcut: (GamePanelShortcut) -> Unit,
    private val onQuickPillAction: (String) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var pillView: ComposeView? = null
    private var pillOwner: OverlayComposeOwner? = null
    private val pillConfigFlow = MutableStateFlow(FloatingPillConfig())
    private val pillThemeFlow = MutableStateFlow(ThemeMode.DARK)

    private var gamePanelView: ComposeView? = null
    private var gamePanelOwner: OverlayComposeOwner? = null
    private val sidePanelFlow = MutableStateFlow(SidePanelSnapshot())

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun syncQuickPill(config: FloatingPillConfig, themeMode: ThemeMode) {
        pillConfigFlow.value = config
        pillThemeFlow.value = themeMode
        if (!config.enabled || !canDrawOverlays()) {
            removeQuickPill()
            return
        }
        val gravity = when (config.position) {
            PillPosition.TOP_LEFT -> Gravity.TOP or Gravity.START
            PillPosition.TOP_CENTER -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            PillPosition.TOP_RIGHT -> Gravity.TOP or Gravity.END
        }
        if (pillView == null) {
            val owner = OverlayComposeOwner().also { it.attach() }
            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    val cfg by pillConfigFlow.collectAsState()
                    val theme by pillThemeFlow.collectAsState()
                    EzdTheme(themeMode = theme) {
                        FloatingQuickPillOverlay(
                            config = cfg,
                            onAction = { action ->
                                if (action == "Open EZD") openMainApp()
                                else onQuickPillAction(action)
                            }
                        )
                    }
                }
            }
            val lp = overlayLayoutParams(gravity = gravity, x = 24, y = 44)
            runCatching {
                windowManager.addView(view, lp)
                pillView = view
                pillOwner = owner
            }
        } else {
            val lp = (pillView?.layoutParams as? WindowManager.LayoutParams) ?: return
            if (lp.gravity != gravity) {
                lp.gravity = gravity
                runCatching { windowManager.updateViewLayout(pillView, lp) }
            }
        }
    }

    fun removeQuickPill() {
        pillView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        pillOwner?.detach()
        pillView = null
        pillOwner = null
    }

    fun syncGameSidePanel(show: Boolean, snapshot: SidePanelSnapshot) {
        sidePanelFlow.value = snapshot
        if (!show || !canDrawOverlays()) {
            removeGameSidePanel()
            return
        }
        val gravity = (if (snapshot.config.sidePanelRightSide) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        if (gamePanelView == null) {
            val owner = OverlayComposeOwner().also { it.attach() }
            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    val snap by sidePanelFlow.collectAsState()
                    EzdTheme(themeMode = snap.themeMode) {
                        FloatingGameSidePanelOverlay(
                            snapshot = snap,
                            onSelectProfile = onSelectGameProfile,
                            onToggleShortcut = onToggleShortcut,
                            onOpenApp = { openMainApp() }
                        )
                    }
                }
            }
            val lp = overlayLayoutParams(gravity = gravity, x = 12, y = 0)
            runCatching {
                windowManager.addView(view, lp)
                gamePanelView = view
                gamePanelOwner = owner
            }
        } else {
            val lp = (gamePanelView?.layoutParams as? WindowManager.LayoutParams) ?: return
            if (lp.gravity != gravity) {
                lp.gravity = gravity
                runCatching { windowManager.updateViewLayout(gamePanelView, lp) }
            }
        }
    }

    fun removeGameSidePanel() {
        gamePanelView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        gamePanelOwner?.detach()
        gamePanelView = null
        gamePanelOwner = null
    }

    fun destroyAll() {
        removeQuickPill()
        removeGameSidePanel()
    }

    private fun overlayLayoutParams(gravity: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    private fun openMainApp() {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }
}

@Composable
private fun FloatingQuickPillOverlay(
    config: FloatingPillConfig,
    onAction: (String) -> Unit
) {
    val visuals = LocalEzdVisuals.current
    var expanded by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = visuals.pillBackground.copy(alpha = config.compactOpacity.coerceIn(0.45f, 1f)),
            border = BorderStroke(1.dp, visuals.subtleRedAccent.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "EZD Quick Pill",
                    tint = visuals.subtleRedAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "EZD",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = visuals.textPrimary
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = visuals.glassSurfaceElevated.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, visuals.glassBorder),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (config.shortcuts + "Open EZD").distinct().take(4).forEach { label ->
                        Surface(
                            shape = CircleShape,
                            color = visuals.subtleRedAccent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, visuals.subtleRedAccent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    expanded = false
                                    onAction(label)
                                }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = visuals.textPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingGameSidePanelOverlay(
    snapshot: SidePanelSnapshot,
    onSelectProfile: (GameProfileId) -> Unit,
    onToggleShortcut: (GamePanelShortcut) -> Unit,
    onOpenApp: () -> Unit
) {
    val visuals = LocalEzdVisuals.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val audioMgr = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var volumeFraction by remember {
        val cur = audioMgr?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5
        val max = (audioMgr?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
        mutableFloatStateOf(cur.toFloat() / max.toFloat())
    }
    var brightFraction by remember {
        val b = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(140)
        mutableFloatStateOf(b / 255f)
    }

    if (!expanded) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = visuals.pillBackground.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, visuals.subtleRedAccent.copy(alpha = 0.7f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(width = 30.dp, height = 74.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = "Open Game Panel",
                    tint = visuals.subtleRedAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = snapshot.fpsReading.fps?.toString() ?: "--",
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = visuals.textPrimary
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xEC09090D),
            border = BorderStroke(1.dp, visuals.subtleRedAccent.copy(alpha = 0.45f)),
            shadowElevation = 12.dp,
            modifier = Modifier.width(248.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(
                            text = snapshot.activeAppName,
                            style = MaterialTheme.typography.titleSmall,
                            color = visuals.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (snapshot.batteryPercent >= 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${snapshot.batteryPercent}%",
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMono,
                                color = visuals.successColor
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = onOpenApp, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = "Open EZD", tint = visuals.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { expanded = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Collapse Panel", tint = visuals.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Dual CPU / GPU Gauges + Genuine FPS Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniArcGauge("CPU", snapshot.cpuUsage, "${snapshot.cpuMhz}M", visuals.subtleRedAccent)
                    MiniArcGauge("GPU", snapshot.gpuUsage, if (snapshot.gpuMhz > 0) "${snapshot.gpuMhz}M" else "--", visuals.subtleRedAccent)
                }

                // Genuine FPS Graph
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF121218),
                    border = BorderStroke(1.dp, visuals.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text("FPS", style = MaterialTheme.typography.labelSmall, color = visuals.textSecondary)
                            Text(
                                text = snapshot.fpsReading.fps?.toString() ?: "N/A",
                                fontSize = 19.sp,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = visuals.textPrimary
                            )
                            Text(
                                text = "Min ${snapshot.fpsReading.minFps ?: "-"} · Max ${snapshot.fpsReading.maxFps ?: "-"}",
                                fontSize = 9.sp,
                                fontFamily = JetBrainsMono,
                                color = visuals.textSecondary
                            )
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        ) {
                            val pts = snapshot.fpsHistory
                            if (pts.size >= 2) {
                                val path = Path()
                                val maxV = pts.max().coerceAtLeast(60).toFloat()
                                pts.forEachIndexed { idx, v ->
                                    val x = (idx.toFloat() / (pts.size - 1)) * size.width
                                    val y = size.height - ((v / maxV).coerceIn(0f, 1f) * size.height)
                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = visuals.subtleRedAccent, style = Stroke(width = 3f, cap = StrokeCap.Round))
                            } else {
                                drawLine(
                                    color = visuals.glassBorder,
                                    start = Offset(0f, size.height / 2f),
                                    end = Offset(size.width, size.height / 2f),
                                    strokeWidth = 2f
                                )
                            }
                        }
                    }
                }

                // Profile Selector Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        GameProfileId.BALANCED to "Bal",
                        GameProfileId.DYNAMIC to "Dyn",
                        GameProfileId.PERFORMANCE to "Perf",
                        GameProfileId.MAX_PERFORMANCE to "Max"
                    ).forEach { (id, lbl) ->
                        val active = snapshot.activeProfile == id
                        Surface(
                            shape = CircleShape,
                            color = if (active) visuals.subtleRedAccent else Color(0xFF17171E),
                            border = BorderStroke(1.dp, if (active) visuals.subtleRedAccent else visuals.glassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable { onSelectProfile(id) }
                        ) {
                            Text(
                                text = lbl,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else visuals.textSecondary,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Compact Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val ramPct = if (snapshot.ramTotalMb > 0) ((snapshot.ramUsedMb * 100) / snapshot.ramTotalMb).toInt() else 0
                    Text("RAM $ramPct%", fontSize = 10.sp, fontFamily = JetBrainsMono, color = visuals.textSecondary)
                    Text("${snapshot.refreshHz.toInt()}Hz", fontSize = 10.sp, fontFamily = JetBrainsMono, color = visuals.textSecondary)
                    Text(snapshot.netLabel, fontSize = 10.sp, fontFamily = JetBrainsMono, color = visuals.textSecondary)
                }

                // Neutral GlowIcon Shortcut Grid (max 8 in 4x2 grid)
                val shortcuts = snapshot.config.sidePanelShortcuts.toList().take(8)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shortcuts.chunked(4).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowItems.forEach { sc ->
                                val isOn = when (sc) {
                                    GamePanelShortcut.CPU_LOCK -> snapshot.config.cpuLockEnabled
                                    GamePanelShortcut.HBM -> snapshot.config.hbmBoostEnabled
                                    GamePanelShortcut.TOUCH_BOOST -> snapshot.config.touchBoostEnabled
                                    GamePanelShortcut.DND -> snapshot.config.dndEnabled
                                    else -> false
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onToggleShortcut(sc) }
                                        .padding(4.dp)
                                ) {
                                    GlowIcon(
                                        icon = shortcutVector(sc),
                                        contentDescription = sc.label,
                                        tint = if (isOn) visuals.subtleRedAccent else visuals.textPrimary,
                                        size = 18.dp
                                    )
                                    Text(
                                        text = sc.label.take(7),
                                        fontSize = 9.sp,
                                        color = visuals.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Brightness & Volume Sliders
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Brightness6, contentDescription = "Brightness", tint = visuals.textSecondary, modifier = Modifier.size(16.dp))
                    Slider(
                        value = brightFraction,
                        onValueChange = { f ->
                            brightFraction = f
                            if (Settings.System.canWrite(context)) {
                                runCatching {
                                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (f * 255).roundToInt().coerceIn(10, 255))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = visuals.subtleRedAccent, activeTrackColor = visuals.subtleRedAccent)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = "Media Volume", tint = visuals.textSecondary, modifier = Modifier.size(16.dp))
                    Slider(
                        value = volumeFraction,
                        onValueChange = { f ->
                            volumeFraction = f
                            audioMgr?.let { am ->
                                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt().coerceIn(0, max), 0)
                            }
                        },
                        modifier = Modifier.weight(1f).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = visuals.subtleRedAccent, activeTrackColor = visuals.subtleRedAccent)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniArcGauge(label: String, percent: Int, sub: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = Color(0x33FFFFFF),
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = 140f,
                    sweepAngle = 260f * (percent.coerceIn(0, 100) / 100f),
                    useCenter = false,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "${percent}%",
                fontSize = 11.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text("$label · $sub", fontSize = 10.sp, fontFamily = JetBrainsMono, color = Color(0xFFAAB2C2))
    }
}

private fun shortcutVector(sc: GamePanelShortcut): ImageVector = when (sc) {
    GamePanelShortcut.CPU_LOCK -> Icons.Rounded.Lock
    GamePanelShortcut.PROFILE -> Icons.Rounded.Tune
    GamePanelShortcut.FPS -> Icons.Rounded.Speed
    GamePanelShortcut.HBM -> Icons.Rounded.BrightnessHigh
    GamePanelShortcut.BRIGHTNESS -> Icons.Rounded.Brightness6
    GamePanelShortcut.TOUCH_BOOST -> Icons.Rounded.TouchApp
    GamePanelShortcut.REFRESH_RATE -> Icons.Rounded.Refresh
    GamePanelShortcut.WIFI -> Icons.Rounded.Wifi
    GamePanelShortcut.ROTATION -> Icons.Rounded.ScreenRotation
    GamePanelShortcut.DND -> Icons.Rounded.DoNotDisturbOn
    GamePanelShortcut.SCREENSHOT -> Icons.Rounded.Screenshot
    GamePanelShortcut.RECORD -> Icons.Rounded.Videocam
    GamePanelShortcut.DEVICE_INFO -> Icons.Rounded.Info
}
