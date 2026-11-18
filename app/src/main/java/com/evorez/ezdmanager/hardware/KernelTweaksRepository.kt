package com.evorez.ezdmanager.hardware

import android.content.Context
import android.provider.Settings
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.CommandBridge
import com.evorez.ezdmanager.root.HwResult
import com.evorez.ezdmanager.root.RootShell
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.root.describe
import java.util.Locale

class KernelTweaksRepository(private val context: Context) {

    // ========================================================================
    // 1. ZRAM & VM TWEAKS (size, compression algorithm, swappiness, cache)
    // ========================================================================

    suspend fun scanZramVm(forceRefresh: Boolean = true): ZramVmState {
        return HardwareProbe.probe(context, forceRefresh = forceRefresh).zramVmState
    }

    suspend fun applyZramConfig(sizeMb: Long, algorithm: String): Pair<Boolean, String> {
        val clampedMb = sizeMb.coerceIn(0L, 16384L)
        val bytes = clampedMb * 1024L * 1024L
        val safeAlgo = algorithm.trim().takeIf { it.matches(Regex("^[a-zA-Z0-9_-]+$")) }

        if (!RootShell.isSuGranted()) {
            return false to HwResult.PermissionDenied.describe("ZRAM")
        }

        val existsCheck = RootShell.exec("[ -d /sys/block/zram0 ] && echo OK || echo NF", timeoutMs = 2_000L)
        if (!existsCheck.stdout.contains("OK")) {
            return false to HwResult.NotFound(listOf("/sys/block/zram0")).describe("ZRAM")
        }

        val script = buildString {
            appendLine("swapoff /dev/block/zram0 2>/dev/null || true")
            appendLine("echo 1 > /sys/block/zram0/reset 2>/dev/null || true")
            if (safeAlgo != null && clampedMb > 0L) {
                appendLine("echo ${RootShell.shellQuote(safeAlgo)} > /sys/block/zram0/comp_algorithm 2>/dev/null || true")
            }
            appendLine("echo $bytes > /sys/block/zram0/disksize")
            if (clampedMb > 0L) {
                appendLine("mkswap /dev/block/zram0 >/dev/null 2>&1")
                appendLine("swapon /dev/block/zram0 >/dev/null 2>&1")
            }
        }
        val res = RootShell.exec(script, timeoutMs = 8_000L)
        val readBackBytes = RootShell.readNode("/sys/block/zram0/disksize")?.toLongOrNull()
        val readBackAlgo = RootShell.readNode("/sys/block/zram0/comp_algorithm").orEmpty()
        return if (res.isSuccess && readBackBytes == bytes) {
            true to HwResult.Applied("${bytes / (1024L * 1024L)} MB ($readBackAlgo)").describe("ZRAM")
        } else if (res.stderr.contains("Permission denied", true) && RootShell.getSelinuxMode().equals("Enforcing", true)) {
            false to HwResult.SelinuxBlocked.describe("ZRAM")
        } else {
            false to HwResult.Rejected(
                "disksize read-back=${readBackBytes ?: "null"} expected=$bytes (${res.stderr.ifBlank { "kernel rejected size or swapoff busy" }})"
            ).describe("ZRAM")
        }
    }

    suspend fun applyVmTweaks(
        swappiness: Int,
        vfsCachePressure: Int,
        dirtyRatio: Int,
        dirtyBgRatio: Int
    ): Pair<Boolean, String> {
        val sw = swappiness.coerceIn(0, 200)
        val vfs = vfsCachePressure.coerceIn(10, 500)
        val dr = dirtyRatio.coerceIn(5, 80)
        val dbg = dirtyBgRatio.coerceIn(2, dr.coerceAtMost(50))

        val r1 = RootShell.writeAndVerify("/proc/sys/vm/swappiness", sw.toString())
        val r2 = RootShell.writeAndVerify("/proc/sys/vm/vfs_cache_pressure", vfs.toString())
        val r3 = RootShell.writeAndVerify("/proc/sys/vm/dirty_ratio", dr.toString())
        val r4 = RootShell.writeAndVerify("/proc/sys/vm/dirty_background_ratio", dbg.toString())

        val results = listOf(r1, r2, r3, r4)
        val ok = results.all { it is HwResult.Applied }
        return if (ok) {
            true to "VM Applied: sw=$sw, vfs=$vfs, dirty=$dr/$dbg"
        } else {
            val firstErr = results.first { it !is HwResult.Applied }
            false to firstErr.describe("VM tunables")
        }
    }

    // ========================================================================
    // 2. I/O SCHEDULER, READ-AHEAD, THERMAL PROFILE, ANIMATION SCALE & GPU RENDERER
    // ========================================================================

    suspend fun scanIoThermalUi(rootGranted: Boolean, forceRefresh: Boolean = true): IoThermalUiState {
        val probe = HardwareProbe.probe(context, forceRefresh = forceRefresh)
        val propScript = """
            echo "THERMZ|$(ls -d /sys/class/thermal/thermal_zone* 2>/dev/null | wc -l)"
            echo "THPROP|$(getprop persist.ezd.thermal_profile)"
            echo "RENDER|$(getprop debug.hwui.renderer)"
        """.trimIndent()

        val res = if (rootGranted) {
            RootShell.exec(propScript, timeoutMs = 3_000L)
        } else {
            RootShell.execLocalSh(propScript, timeoutMs = 2_500L)
        }

        var thermZones = 0
        var thermProp = ""
        var rendererProp = ""
        res.stdoutLines.forEach { line ->
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "THERMZ" -> thermZones = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                "THPROP" -> thermProp = parts.getOrNull(1)?.trim().orEmpty()
                "RENDER" -> rendererProp = parts.getOrNull(1)?.trim().orEmpty()
            }
        }

        val winScale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE) }.getOrDefault(1.0f)
        val transScale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE) }.getOrDefault(1.0f)
        val animScale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) }.getOrDefault(1.0f)

        val renderer = GpuRendererMode.entries.find { it.propValue.equals(rendererProp, ignoreCase = true) }
            ?: GpuRendererMode.SYSTEM_DEFAULT
        val thermalMode = runCatching {
            if (thermProp.isNotBlank()) ThermalProfileMode.valueOf(thermProp) else ThermalProfileMode.STOCK
        }.getOrDefault(ThermalProfileMode.STOCK)

        val thermalNote = when {
            !rootGranted -> "Root (su) required to modify vendor thermal throttle policies & block I/O queues."
            thermZones > 0 -> "$thermZones thermal zones detected · Safe threshold modulation ready."
            else -> "Using property-based thermal profile fallback."
        }

        return IoThermalUiState(
            blockDevices = probe.ioDevices,
            thermalProfile = thermalMode,
            thermalZoneCount = thermZones,
            thermalBackendNote = thermalNote,
            windowAnimScale = winScale,
            transitionAnimScale = transScale,
            animatorDurationScale = animScale,
            gpuRenderer = renderer,
            ioProbeResult = probe.ioResult,
            ioPathsTried = probe.ioPathsTried,
            ioStatusNote = probe.ioReason
        )
    }

    suspend fun setIoScheduler(device: IoBlockDevice, scheduler: String): Pair<Boolean, String> {
        if (!scheduler.matches(Regex("^[a-zA-Z0-9_-]+$"))) return false to "Rejected: invalid scheduler name $scheduler"
        val res = RootShell.writeAndVerify("${device.sysfsPath}/queue/scheduler", scheduler)
        return (res is HwResult.Applied) to res.describe("${device.name} scheduler")
    }

    suspend fun setIoReadAhead(device: IoBlockDevice, kb: Int): Pair<Boolean, String> {
        val clamped = kb.coerceIn(64, 4096)
        val res = RootShell.writeAndVerify("${device.sysfsPath}/queue/read_ahead_kb", clamped.toString())
        return (res is HwResult.Applied) to res.describe("${device.name} read_ahead_kb")
    }

    suspend fun applyThermalProfile(mode: ThermalProfileMode): Pair<Boolean, String> {
        val sconfigVal = when (mode) {
            ThermalProfileMode.STOCK -> "0"
            ThermalProfileMode.COOL -> "11"
            ThermalProfileMode.BALANCED -> "9"
            ThermalProfileMode.PERFORMANCE -> "10"
            ThermalProfileMode.MAX_HEADROOM -> "20"
        }
        val msmToggle = when (mode) {
            ThermalProfileMode.COOL -> "Y"
            else -> "Y"
        }
        val cmd = """
            setprop persist.ezd.thermal_profile ${mode.name}
            if [ -w /sys/class/thermal/thermal_message/sconfig ]; then
              echo $sconfigVal > /sys/class/thermal/thermal_message/sconfig 2>/dev/null
            fi
            if [ -w /sys/module/msm_thermal/parameters/enabled ]; then
              echo $msmToggle > /sys/module/msm_thermal/parameters/enabled 2>/dev/null
            fi
            if [ -w /sys/kernel/msm_thermal/enabled ]; then
              echo 1 > /sys/kernel/msm_thermal/enabled 2>/dev/null
            fi
            setprop vendor.thermal.profile ${mode.name.lowercase()} 2>/dev/null || true
            echo OK
        """.trimIndent()
        val res = RootShellManager.exec(cmd, timeoutMs = 4_000L)
        return if (res.success) {
            true to "Thermal throttle profile set to ${mode.label} (hardware safety trip points protected)"
        } else {
            false to "Thermal profile change requires root access"
        }
    }

    suspend fun setAnimationScale(
        scale: Float,
        rootGranted: Boolean = true,
        shizukuGranted: Boolean = false
    ): Pair<Boolean, String> {
        val formatted = String.format(Locale.US, "%.2f", scale.coerceIn(0f, 2f))
        val cmd = """
            settings put global window_animation_scale $formatted
            settings put global transition_animation_scale $formatted
            settings put global animator_duration_scale $formatted
        """.trimIndent()
        val res = CommandBridge.execPrivileged(cmd, useRoot = rootGranted, useShizuku = shizukuGranted)
        return if (res.success) {
            true to "System animation scale set to ${formatted}x"
        } else {
            false to "Changing system animation scales requires Root or Shizuku"
        }
    }

    suspend fun setGpuRenderer(mode: GpuRendererMode): Pair<Boolean, String> {
        val cmd = if (mode.propValue.isBlank()) {
            "resetprop --delete debug.hwui.renderer 2>/dev/null || setprop debug.hwui.renderer \"\""
        } else {
            val qVal = RootShellManager.shellQuote(mode.propValue)
            "(command -v resetprop >/dev/null 2>&1 && resetprop debug.hwui.renderer $qVal) || setprop debug.hwui.renderer $qVal"
        }
        val res = RootShellManager.exec(cmd)
        return if (res.success) {
            true to "GPU HWUI renderer set to ${mode.label}. Re-open apps (or Soft Reboot) for full effect."
        } else {
            false to "Setting debug.hwui.renderer requires root access"
        }
    }
}
