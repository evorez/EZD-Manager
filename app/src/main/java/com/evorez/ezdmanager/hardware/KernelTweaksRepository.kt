package com.evorez.ezdmanager.hardware

import android.content.Context
import android.provider.Settings
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.CommandBridge
import com.evorez.ezdmanager.root.RootShellManager
import java.util.Locale

class KernelTweaksRepository(private val context: Context) {

    // ========================================================================
    // 1. ZRAM & VM TWEAKS (size, compression algorithm, swappiness, cache)
    // ========================================================================

    suspend fun scanZramVm(rootGranted: Boolean): ZramVmState {
        val swappiness = RootShellManager.readSysfs("/proc/sys/vm/swappiness")?.toIntOrNull() ?: 60
        val vfsPressure = RootShellManager.readSysfs("/proc/sys/vm/vfs_cache_pressure")?.toIntOrNull() ?: 100
        val dirtyRatio = RootShellManager.readSysfs("/proc/sys/vm/dirty_ratio")?.toIntOrNull() ?: 20
        val dirtyBg = RootShellManager.readSysfs("/proc/sys/vm/dirty_background_ratio")?.toIntOrNull() ?: 10

        val diskBytes = RootShellManager.readSysfs("/sys/block/zram0/disksize")?.toLongOrNull()
        val compRaw = RootShellManager.readSysfs("/sys/block/zram0/comp_algorithm")
        val mmStatRaw = RootShellManager.readSysfs("/sys/block/zram0/mm_stat")
        val memUsed = RootShellManager.readSysfs("/sys/block/zram0/mem_used_total")?.toLongOrNull()
        val origSize = RootShellManager.readSysfs("/sys/block/zram0/orig_data_size")?.toLongOrNull()

        var origMb = (origSize ?: 0L) / (1024L * 1024L)
        var usedMb = (memUsed ?: 0L) / (1024L * 1024L)
        if (!mmStatRaw.isNullOrBlank()) {
            val cols = mmStatRaw.trim().split(Regex("\\s+"))
            cols.getOrNull(0)?.toLongOrNull()?.let { origMb = it / (1024L * 1024L) }
            cols.getOrNull(2)?.toLongOrNull()?.let { usedMb = it / (1024L * 1024L) }
        }

        val algos = mutableListOf<String>()
        var activeAlgo = "Unavailable"
        compRaw?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.forEach { token ->
            if (token.startsWith("[") && token.endsWith("]")) {
                val clean = token.removePrefix("[").removeSuffix("]")
                activeAlgo = clean
                algos.add(clean)
            } else {
                algos.add(token)
            }
        }

        val supported = diskBytes != null || compRaw != null
        val note = when {
            !rootGranted -> "Grant root access to resize ZRAM, switch compression, or tune /proc/sys/vm."
            !supported -> "Kernel /sys/block/zram0 interface not exposed; VM tuning remains available."
            else -> "ZRAM live telemetry active."
        }

        return ZramVmState(
            zramSupported = supported,
            diskSizeMb = (diskBytes ?: 0L) / (1024L * 1024L),
            usedMemMb = usedMb,
            origDataMb = origMb,
            currentAlgorithm = activeAlgo,
            availableAlgorithms = algos.distinct(),
            swappiness = swappiness,
            vfsCachePressure = vfsPressure,
            dirtyRatio = dirtyRatio,
            dirtyBackgroundRatio = dirtyBg,
            statusNote = note
        )
    }

    suspend fun applyZramConfig(sizeMb: Long, algorithm: String): Pair<Boolean, String> {
        val clampedMb = sizeMb.coerceIn(0L, 16384L)
        val bytes = clampedMb * 1024L * 1024L
        val safeAlgo = algorithm.trim().takeIf { it.matches(Regex("^[a-zA-Z0-9_-]+$")) }

        val script = buildString {
            appendLine("swapoff /dev/block/zram0 2>/dev/null || true")
            appendLine("echo 1 > /sys/block/zram0/reset 2>/dev/null || true")
            if (safeAlgo != null && clampedMb > 0L) {
                appendLine("echo ${RootShellManager.shellQuote(safeAlgo)} > /sys/block/zram0/comp_algorithm 2>/dev/null || true")
            }
            appendLine("echo $bytes > /sys/block/zram0/disksize")
            if (clampedMb > 0L) {
                appendLine("mkswap /dev/block/zram0 >/dev/null 2>&1")
                appendLine("swapon /dev/block/zram0 >/dev/null 2>&1")
            }
        }
        val res = RootShellManager.exec(script, timeoutMs = 8_000L)
        return if (res.success) {
            val algoText = safeAlgo ?: "default"
            true to if (clampedMb == 0L) "ZRAM swap disabled (0 MB)" else "ZRAM set to ${clampedMb} MB ($algoText)"
        } else {
            false to "ZRAM update failed: ${res.combinedText.ifBlank { "kernel rejected size or swapoff busy" }}"
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

        val cmd = """
            sysctl -w vm.swappiness=$sw >/dev/null 2>&1 || echo $sw > /proc/sys/vm/swappiness
            sysctl -w vm.vfs_cache_pressure=$vfs >/dev/null 2>&1 || echo $vfs > /proc/sys/vm/vfs_cache_pressure
            sysctl -w vm.dirty_ratio=$dr >/dev/null 2>&1 || echo $dr > /proc/sys/vm/dirty_ratio
            sysctl -w vm.dirty_background_ratio=$dbg >/dev/null 2>&1 || echo $dbg > /proc/sys/vm/dirty_background_ratio
        """.trimIndent()
        val res = RootShellManager.exec(cmd)
        return if (res.success) {
            true to "VM applied: swappiness=$sw, vfs_cache=$vfs, dirty=$dr/$dbg"
        } else {
            false to "VM sysctl write requires root access"
        }
    }

    // ========================================================================
    // 2. I/O SCHEDULER, READ-AHEAD, THERMAL PROFILE, ANIMATION SCALE & GPU RENDERER
    // ========================================================================

    suspend fun scanIoThermalUi(rootGranted: Boolean): IoThermalUiState {
        val blockList = mutableListOf<IoBlockDevice>()
        val probeCmd = """
            for d in /sys/block/sda /sys/block/sdb /sys/block/sdc /sys/block/mmcblk0 /sys/block/mmcblk1 /sys/block/dm-0; do
              if [ -d "${'$'}d/queue" ]; then
                NAME=$(basename "${'$'}d")
                SCH=$(cat "${'$'}d/queue/scheduler" 2>/dev/null)
                RA=$(cat "${'$'}d/queue/read_ahead_kb" 2>/dev/null)
                echo "BLK|${'$'}NAME|${'$'}d|${'$'}SCH|${'$'}RA"
              fi
            done
            echo "THERMZ|$(ls -d /sys/class/thermal/thermal_zone* 2>/dev/null | wc -l)"
            echo "THPROP|$(getprop persist.ezd.thermal_profile)"
            echo "RENDER|$(getprop debug.hwui.renderer)"
        """.trimIndent()

        val res = if (rootGranted) {
            RootShellManager.exec(probeCmd, timeoutMs = 4_000L)
        } else {
            RootShellManager.execUnprivileged(probeCmd, timeoutMs = 3_000L)
        }

        var thermZones = 0
        var thermProp = ""
        var rendererProp = ""

        res.stdout.forEach { line ->
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "BLK" -> {
                    val name = parts.getOrNull(1).orEmpty()
                    val path = parts.getOrNull(2).orEmpty()
                    val schRaw = parts.getOrNull(3).orEmpty()
                    val ra = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 128

                    var activeSch = "default"
                    val avail = mutableListOf<String>()
                    schRaw.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { tok ->
                        if (tok.startsWith("[") && tok.endsWith("]")) {
                            val clean = tok.removePrefix("[").removeSuffix("]")
                            activeSch = clean
                            avail.add(clean)
                        } else {
                            avail.add(tok)
                        }
                    }
                    if (name.isNotBlank() && avail.isNotEmpty()) {
                        blockList.add(IoBlockDevice(name, path, activeSch, avail.distinct(), ra))
                    }
                }
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
            !rootGranted -> "Root access needed to modify vendor thermal throttle policies & block I/O queues."
            thermZones > 0 -> "$thermZones thermal zones detected · Safe threshold modulation ready."
            else -> "Using property-based thermal profile fallback."
        }

        return IoThermalUiState(
            blockDevices = blockList,
            thermalProfile = thermalMode,
            thermalZoneCount = thermZones,
            thermalBackendNote = thermalNote,
            windowAnimScale = winScale,
            transitionAnimScale = transScale,
            animatorDurationScale = animScale,
            gpuRenderer = renderer
        )
    }

    suspend fun setIoScheduler(device: IoBlockDevice, scheduler: String): Pair<Boolean, String> {
        if (!scheduler.matches(Regex("^[a-zA-Z0-9_-]+$"))) return false to "Invalid I/O scheduler name"
        val (ok, msg) = RootShellManager.writeAndVerify("${device.sysfsPath}/queue/scheduler", scheduler)
        return if (ok) true to "${device.name} scheduler set to $scheduler"
        else false to "Failed writing ${device.name}/scheduler: ${msg ?: "requires root"}"
    }

    suspend fun setIoReadAhead(device: IoBlockDevice, kb: Int): Pair<Boolean, String> {
        val clamped = kb.coerceIn(64, 4096)
        val (ok, msg) = RootShellManager.writeAndVerify("${device.sysfsPath}/queue/read_ahead_kb", clamped.toString())
        return if (ok) true to "${device.name} read-ahead set to ${clamped} KB"
        else false to "Failed writing read_ahead_kb: ${msg ?: "requires root"}"
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
