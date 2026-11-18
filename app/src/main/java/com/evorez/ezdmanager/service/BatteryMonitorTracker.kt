package com.evorez.ezdmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import com.evorez.ezdmanager.model.BatteryMonitorStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs

/**
 * Lightweight, zero-root real-time Battery & Drain tracker.
 * Tracks live current (mA), voltage (mV), power (W), charge rate (%/hr),
 * active screen-on drain, idle screen-off drain, Screen On/Off durations,
 * and Awake vs Deep Sleep time using Android BatteryManager & SystemClock.
 */
object BatteryMonitorTracker {

    private val _stats = MutableStateFlow(BatteryMonitorStats())
    val stats: StateFlow<BatteryMonitorStats> = _stats.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var receiverRegistered = false

    private var lastSampleElapsedMs: Long = 0L
    private var screenOnAccumMs: Long = 0L
    private var screenOffAccumMs: Long = 0L

    private var chargeStartTimeMs: Long = 0L
    private var chargeStartLevel: Int = -1

    private var activeTimeForRateMs: Long = 0L
    private var activeLevelDrop: Float = 0f
    private var activeMaSum: Long = 0L
    private var activeMaCount: Int = 0

    private var idleTimeForRateMs: Long = 0L
    private var idleLevelDrop: Float = 0f
    private var idleMaSum: Long = 0L
    private var idleMaCount: Int = 0

    private var prevLevel: Int = -1
    private var prevWasCharging: Boolean = false
    private var prevScreenOn: Boolean = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null) sampleNow(context)
        }
    }

    @Synchronized
    fun ensureRegistered(context: Context) {
        val appCtx = context.applicationContext
        if (!initialized) {
            initialized = true
            lastSampleElapsedMs = SystemClock.elapsedRealtime()
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            prevScreenOn = pm?.isInteractive ?: true
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            runCatching {
                appCtx.registerReceiver(screenReceiver, filter)
                receiverRegistered = true
            }
        }
        sampleNow(appCtx)
    }

    @Synchronized
    fun resetCounters(context: Context): BatteryMonitorStats {
        lastSampleElapsedMs = SystemClock.elapsedRealtime()
        screenOnAccumMs = 0L
        screenOffAccumMs = 0L
        activeTimeForRateMs = 0L
        activeLevelDrop = 0f
        activeMaSum = 0L
        activeMaCount = 0
        idleTimeForRateMs = 0L
        idleLevelDrop = 0f
        idleMaSum = 0L
        idleMaCount = 0
        chargeStartTimeMs = 0L
        chargeStartLevel = -1
        prevLevel = -1
        return sampleNow(context)
    }

    @Synchronized
    fun sampleNow(context: Context): BatteryMonitorStats {
        val appCtx = context.applicationContext
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()

        val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenOn = pm?.isInteractive ?: true

        val intent = runCatching {
            appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val levelRaw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val level = if (levelRaw >= 0) (levelRaw * 100) / scale else (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC Fast"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            isCharging -> "Charging"
            else -> "Discharging"
        }

        val voltageMv = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0).let { v ->
            if (v in 1..20) v * 1000 else v
        }
        val tempC = ((intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f)

        val currentNowUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentMa = normalizeCurrentMa(currentNowUa, isCharging)
        val watts = if (voltageMv > 0) (abs(currentMa) * voltageMv) / 1_000_000f else 0f

        if (lastSampleElapsedMs > 0L) {
            val deltaMs = (nowElapsed - lastSampleElapsedMs).coerceIn(0L, 600_000L)
            if (prevScreenOn) {
                screenOnAccumMs += deltaMs
            } else {
                screenOffAccumMs += deltaMs
            }

            if (isCharging) {
                if (!prevWasCharging || chargeStartLevel < 0 || level < chargeStartLevel) {
                    chargeStartTimeMs = nowElapsed
                    chargeStartLevel = level
                }
            } else {
                chargeStartTimeMs = 0L
                chargeStartLevel = -1
                if (!prevWasCharging && prevLevel >= 0) {
                    val dropped = (prevLevel - level).coerceAtLeast(0).toFloat()
                    if (prevScreenOn) {
                        activeTimeForRateMs += deltaMs
                        activeLevelDrop += dropped
                        if (abs(currentMa) in 10..8000) {
                            activeMaSum += abs(currentMa)
                            activeMaCount++
                        }
                    } else {
                        idleTimeForRateMs += deltaMs
                        idleLevelDrop += dropped
                        if (abs(currentMa) in 5..5000) {
                            idleMaSum += abs(currentMa)
                            idleMaCount++
                        }
                    }
                }
            }
        }

        lastSampleElapsedMs = nowElapsed
        prevLevel = level
        prevWasCharging = isCharging
        prevScreenOn = isScreenOn

        val chargeRate = if (isCharging && chargeStartTimeMs > 0L && level > chargeStartLevel) {
            val elapsedHrs = (nowElapsed - chargeStartTimeMs).coerceAtLeast(60_000L) / 3_600_000f
            ((level - chargeStartLevel) / elapsedHrs).coerceIn(0f, 300f)
        } else 0f

        val avgActiveMa = if (activeMaCount > 0) (activeMaSum / activeMaCount).toInt()
        else if (!isCharging && isScreenOn) abs(currentMa) else 0

        val avgIdleMa = if (idleMaCount > 0) (idleMaSum / idleMaCount).toInt()
        else if (!isCharging && !isScreenOn) abs(currentMa) else 0

        val activeHistoryReady = activeLevelDrop > 0f && activeTimeForRateMs >= 60_000L
        val idleHistoryReady = idleLevelDrop > 0f && idleTimeForRateMs >= 60_000L

        val activeRate = if (activeHistoryReady) {
            (activeLevelDrop / (activeTimeForRateMs / 3_600_000f)).coerceIn(0f, 100f)
        } else 0f

        val idleRate = if (idleHistoryReady) {
            (idleLevelDrop / (idleTimeForRateMs / 3_600_000f)).coerceIn(0f, 60f)
        } else 0f

        val deepSleepMs = (nowElapsed - nowUptime).coerceAtLeast(0L)
        val deepPct = if (nowElapsed > 0L) ((deepSleepMs.toFloat() / nowElapsed.toFloat()) * 100f).coerceIn(0f, 100f) else 0f

        val snap = BatteryMonitorStats(
            levelPercent = level.coerceIn(0, 100),
            isCharging = isCharging,
            chargeSource = source,
            currentMa = currentMa,
            voltageMv = voltageMv,
            powerWatts = watts,
            temperatureC = tempC,
            chargeRatePctPerHr = chargeRate,
            activeDrainPctPerHr = activeRate,
            idleDrainPctPerHr = idleRate,
            activeHistoryReady = activeHistoryReady,
            idleHistoryReady = idleHistoryReady,
            avgActiveMa = avgActiveMa,
            avgIdleMa = avgIdleMa,
            screenOnMs = screenOnAccumMs,
            screenOffMs = screenOffAccumMs,
            awakeMs = nowUptime,
            deepSleepMs = deepSleepMs,
            deepSleepPercent = deepPct,
            isScreenOn = isScreenOn
        )
        _stats.value = snap
        return snap
    }

    private fun normalizeCurrentMa(rawUa: Int, isCharging: Boolean): Int {
        if (rawUa == Int.MIN_VALUE || rawUa == 0) return 0
        // Some OEM kernels report mA directly instead of uA, or invert sign
        val magMa = if (abs(rawUa) > 20_000) abs(rawUa) / 1000 else abs(rawUa)
        return if (isCharging) magMa else -magMa
    }

    fun formatDurationShort(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun notificationLine(stats: BatteryMonitorStats): String {
        val signMa = if (stats.currentMa >= 0) "+${stats.currentMa}mA" else "${stats.currentMa}mA"
        val watts = String.format(Locale.US, "%.1fW", stats.powerWatts)
        val phase = if (stats.isCharging) "while charging" else "while discharging"
        val idStr = if (stats.idleHistoryReady) {
            String.format(Locale.US, "%.1f%%/h", stats.idleDrainPctPerHr)
        } else "--"
        val adStr = if (stats.activeHistoryReady) {
            String.format(Locale.US, "%.1f%%", stats.activeDrainPctPerHr)
        } else "--"
        return "$signMa ($watts) $phase • ID $idStr • AD $adStr           ~𝙴𝚟𝚘𝚛𝚎𝚣🪽"
    }
}
