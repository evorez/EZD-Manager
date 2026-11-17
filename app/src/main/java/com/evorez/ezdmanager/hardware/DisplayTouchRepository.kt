package com.evorez.ezdmanager.hardware

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import com.evorez.ezdmanager.model.DisplayModeInfo
import com.evorez.ezdmanager.model.DisplayState
import com.evorez.ezdmanager.model.TouchState
import com.evorez.ezdmanager.root.RootShellManager
import kotlin.math.roundToInt

class DisplayTouchRepository(private val context: Context) {

    suspend fun readDisplayState(rootGranted: Boolean = false): DisplayState {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val currentHz = display?.mode?.refreshRate ?: 60f
        val modes = display?.supportedModes
            ?.map {
                DisplayModeInfo(
                    modeId = it.modeId,
                    width = it.physicalWidth,
                    height = it.physicalHeight,
                    refreshRateHz = (it.refreshRate * 10f).roundToInt() / 10f
                )
            }
            ?.distinctBy { "${it.width}x${it.height}@${it.refreshRateHz}" }
            ?.sortedBy { it.refreshRateHz }
            ?: emptyList()

        val peakHz = runCatching {
            Settings.System.getFloat(context.contentResolver, "peak_refresh_rate")
        }.getOrNull()
        val minHz = runCatching {
            Settings.System.getFloat(context.contentResolver, "min_refresh_rate")
        }.getOrNull()

        val brightness = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        val autoBright = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(false)

        var cabcPath: String? = null
        var cabcEnabled = false
        var hbmPath: String? = null
        var hbmEnabled = false
        var kcalSupported = false
        var kcalSat = 255

        if (rootGranted) {
            val cabcCandidates = listOf(
                "/sys/class/graphics/fb0/cabc",
                "/sys/devices/platform/kcal_ctrl.0/kcal_enable",
                "/sys/class/drm/card0-DSI-1/cabc"
            )
            for (p in cabcCandidates) {
                val valStr = RootShellManager.readSysfs(p)
                if (valStr != null) {
                    cabcPath = p
                    cabcEnabled = valStr != "0"
                    break
                }
            }
            val hbmCandidates = listOf(
                "/sys/class/drm/card0-DSI-1/hbm",
                "/sys/class/graphics/fb0/hbm",
                "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/backlight/panel0-backlight/hbm_mode",
                "/sys/class/backlight/panel0-backlight/hbm_mode"
            )
            for (p in hbmCandidates) {
                val valStr = RootShellManager.readSysfs(p)
                if (valStr != null) {
                    hbmPath = p
                    hbmEnabled = valStr != "0"
                    break
                }
            }
            val kcalVal = RootShellManager.readSysfs("/sys/devices/platform/kcal_ctrl.0/kcal_sat")
            if (kcalVal != null) {
                kcalSupported = true
                kcalSat = kcalVal.toIntOrNull() ?: 255
            }
        }

        return DisplayState(
            currentRefreshHz = (currentHz * 10f).roundToInt() / 10f,
            supportedModes = modes,
            peakRefreshHz = peakHz,
            minRefreshHz = minHz,
            brightness = brightness.coerceIn(1, 255),
            autoBrightness = autoBright,
            hbmSupported = hbmPath != null,
            hbmPath = hbmPath,
            hbmEnabled = hbmEnabled,
            cabcSupported = cabcPath != null,
            cabcEnabled = cabcEnabled,
            cabcPath = cabcPath,
            saturationSupported = true,
            saturationValue = 1.0f,
            kcalSupported = kcalSupported,
            kcalSatValue = kcalSat
        )
    }

    suspend fun setRefreshRate(hz: Float, rootGranted: Boolean): Pair<Boolean, String> {
        val target = hz.roundToInt().toFloat()
        if (rootGranted) {
            val r1 = RootShellManager.exec("settings put system peak_refresh_rate $target")
            val r2 = RootShellManager.exec("settings put system min_refresh_rate $target")
            if (r1.success && r2.success) {
                return true to "Refresh rate set to ${target.toInt()} Hz"
            }
        }
        return if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", target)
                Settings.System.putFloat(context.contentResolver, "min_refresh_rate", target)
                true to "Refresh rate set to ${target.toInt()} Hz"
            }.getOrElse { false to (it.message ?: "Failed to write refresh rate") }
        } else {
            false to "Requires Root or Modify System Settings permission"
        }
    }

    suspend fun resetRefreshRate(rootGranted: Boolean): Pair<Boolean, String> {
        if (rootGranted) {
            RootShellManager.exec("settings delete system peak_refresh_rate; settings delete system min_refresh_rate")
            return true to "Refresh rate restored to system default"
        }
        return if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putFloat(context.contentResolver, "min_refresh_rate", 0f)
                true to "Refresh rate reset to adaptive default"
            }.getOrElse { false to "Could not reset refresh rate" }
        } else {
            false to "Requires Root or Modify System Settings permission"
        }
    }

    suspend fun setBrightness(value: Int, auto: Boolean, rootGranted: Boolean): Pair<Boolean, String> {
        val clamped = value.coerceIn(5, 255)
        val mode = if (auto) 1 else 0
        if (rootGranted) {
            val res = RootShellManager.exec(
                "settings put system screen_brightness_mode $mode; settings put system screen_brightness $clamped"
            )
            if (res.success) return true to if (auto) "Auto-brightness enabled" else "Brightness set to ${(clamped * 100) / 255}%"
        }
        return if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
                true to "Brightness updated"
            }.getOrElse { false to (it.message ?: "Failed") }
        } else {
            false to "Requires Root or Modify System Settings permission"
        }
    }

    suspend fun setHbm(path: String, enabled: Boolean): Pair<Boolean, String> {
        val (ok, msg) = RootShellManager.writeAndVerify(path, if (enabled) "1" else "0")
        return if (ok) true to "High Brightness Mode ${if (enabled) "enabled" else "disabled"}"
        else false to (msg ?: "HBM write rejected")
    }

    suspend fun setSaturation(saturation: Float, kcalPathSupported: Boolean): Pair<Boolean, String> {
        val clamped = saturation.coerceIn(0.4f, 2.0f)
        if (kcalPathSupported) {
            val kcalValue = (255 * clamped).roundToInt().coerceIn(128, 383)
            val (ok, msg) = RootShellManager.writeAndVerify(
                "/sys/devices/platform/kcal_ctrl.0/kcal_sat",
                kcalValue.toString()
            )
            if (ok) return true to "KCAL saturation set to ${String.format(java.util.Locale.US, "%.2f", clamped)}x"
            return false to (msg ?: "KCAL write failed")
        }
        val res = RootShellManager.exec(
            "service call SurfaceFlinger 1022 f ${String.format(java.util.Locale.US, "%.2f", clamped)}"
        )
        return if (res.success) {
            true to "Display saturation set to ${String.format(java.util.Locale.US, "%.2f", clamped)}x"
        } else {
            false to "Saturation requires root access on this device"
        }
    }

    suspend fun setCabc(path: String, enabled: Boolean): Pair<Boolean, String> {
        val (ok, msg) = RootShellManager.writeAndVerify(path, if (enabled) "1" else "0")
        return if (ok) true to "CABC ${if (enabled) "enabled" else "disabled"}" else false to (msg ?: "CABC write rejected")
    }

    suspend fun scanTouch(): TouchState {
        val script = """
            find_first() {
              for p in "$@"; do
                if [ -e "${'$'}p" ] && [ -r "${'$'}p" ]; then echo "${'$'}p"; return; fi
              done
            }
            SP=$(find_first /sys/class/touch/touch_dev/report_rate /proc/touchpanel/report_rate /sys/devices/virtual/touch/touch_dev/bump_sample_rate)
            GM=$(find_first /proc/touchpanel/game_switch_enable /sys/class/touch/touch_dev/game_mode /sys/devices/virtual/sec/tsp/cmd)
            SN=$(find_first /sys/class/touch/touch_dev/sensitivity /proc/touchpanel/smooth_level)
            TB=$(find_first /sys/module/msm_performance/parameters/touchboost /sys/power/pnpmgr/touch_boost /sys/class/input_booster/level)
            echo "SP|${'$'}SP|$(if [ -n "${'$'}SP" ]; then cat "${'$'}SP" 2>/dev/null; fi)"
            echo "GM|${'$'}GM|$(if [ -n "${'$'}GM" ]; then cat "${'$'}GM" 2>/dev/null; fi)"
            echo "SN|${'$'}SN|$(if [ -n "${'$'}SN" ]; then cat "${'$'}SN" 2>/dev/null; fi)"
            echo "TB|${'$'}TB|$(if [ -n "${'$'}TB" ]; then cat "${'$'}TB" 2>/dev/null; fi)"
            echo "HWO|$(dumpsys SurfaceFlinger 2>/dev/null | grep -i -m1 'disable HWC' || true)"
        """.trimIndent()
        val res = RootShellManager.exec(script, timeoutMs = 3_500L)
        if (!res.success) {
            return TouchState(
                statusNote = "Root access required to probe kernel touch sysfs nodes."
            )
        }
        var spPath: String? = null
        var spVal = ""
        var gmPath: String? = null
        var gmVal = false
        var snPath: String? = null
        var snVal = 0
        var tbPath: String? = null
        var tbVal = false
        var hwo = false

        res.stdout.forEach { line ->
            val parts = line.split('|', limit = 3)
            when (parts.firstOrNull()) {
                "SP" -> {
                    spPath = parts.getOrNull(1)?.takeIf { it.startsWith("/") }
                    spVal = parts.getOrNull(2)?.trim().orEmpty()
                }
                "GM" -> {
                    gmPath = parts.getOrNull(1)?.takeIf { it.startsWith("/") }
                    gmVal = parts.getOrNull(2)?.trim() == "1"
                }
                "SN" -> {
                    snPath = parts.getOrNull(1)?.takeIf { it.startsWith("/") }
                    snVal = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                }
                "TB" -> {
                    tbPath = parts.getOrNull(1)?.takeIf { it.startsWith("/") }
                    tbVal = (parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0) > 0
                }
                "HWO" -> {
                    hwo = parts.getOrNull(1)?.contains("true", ignoreCase = true) == true
                }
            }
        }

        val note = if (spPath == null && gmPath == null && snPath == null && tbPath == null) {
            "Kernel touch sysfs nodes are not exposed on this device. SurfaceFlinger HW overlay toggle remains available."
        } else ""

        return TouchState(
            samplingNodePath = spPath,
            samplingValue = spVal,
            gamingModePath = gmPath,
            gamingModeEnabled = gmVal,
            sensitivityPath = snPath,
            sensitivityValue = snVal,
            touchBoostPath = tbPath,
            touchBoostEnabled = tbVal,
            hwOverlaysDisabled = hwo,
            statusNote = note
        )
    }

    suspend fun setTouchNode(path: String, value: String, label: String): Pair<Boolean, String> {
        val (ok, msg) = RootShellManager.writeAndVerify(path, value)
        return if (ok) true to "$label applied ($msg)" else false to "$label failed: ${msg ?: "rejected"}"
    }

    suspend fun setDisableHwOverlays(disable: Boolean): Pair<Boolean, String> {
        val arg = if (disable) "1" else "0"
        val res = RootShellManager.exec("service call SurfaceFlinger 1008 i32 $arg")
        return if (res.success) {
            true to "HW Overlays ${if (disable) "disabled (GPU compositing)" else "restored"}"
        } else {
            false to "SurfaceFlinger HW overlay toggle requires root"
        }
    }
}
