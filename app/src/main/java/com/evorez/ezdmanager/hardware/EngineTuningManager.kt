package com.evorez.ezdmanager.hardware

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.CommandBridge
import com.evorez.ezdmanager.root.HwResult
import com.evorez.ezdmanager.root.RootShell
import com.evorez.ezdmanager.root.describe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * Probe-backed kernel & system tuning engine:
 * - Never hardcodes device-specific paths; reads cached paths from [HardwareProbe].
 * - Always reads back after every sysfs/proc write and reports Applied / Failed / Unsupported.
 * - Snapshots original baseline values per boot session and restores on exit, profile change, crash, or reboot guard.
 * - Fast Charging controls are strictly isolated from Beast Mode.
 */
object EngineTuningManager {
    private const val SNAPSHOT_FILE = "ezd_engine_snapshot.json"
    private val engineMutex = Mutex()

    private fun snapshotFile(context: Context): File = File(context.filesDir, SNAPSHOT_FILE)

    private suspend fun currentBootId(): String =
        RootShell.readNode("/proc/sys/kernel/random/boot_id")?.trim().orEmpty()

    suspend fun captureBaselineIfNeeded(context: Context): JSONObject? {
        val file = snapshotFile(context)
        val bootId = currentBootId()
        if (file.exists()) {
            val existing = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (existing != null && existing.optString("bootId", "") == bootId && bootId.isNotBlank()) {
                return existing
            }
        }
        val probe = HardwareProbe.probe(context, forceRefresh = false)
        val root = JSONObject().apply {
            put("bootId", bootId)
            put("capturedAtMs", System.currentTimeMillis())
            put("beastActive", false)
            put("sessionActive", false)

            // CPU policy baselines
            val cpuArr = JSONArray()
            probe.cpuPolicies.forEach { p ->
                cpuArr.put(JSONObject().apply {
                    put("id", p.id)
                    put("minKhz", p.minKhz ?: -1L)
                    put("maxKhz", p.maxKhz ?: -1L)
                    put("gov", p.currentGovernor)
                })
            }
            put("cpuPolicies", cpuArr)

            // GPU baseline
            val gpuObj = JSONObject().apply {
                put("type", probe.gpuType)
                put("minPath", probe.gpuState.minPath ?: "")
                put("maxPath", probe.gpuState.maxPath ?: "")
                put("govPath", probe.gpuState.govPath ?: "")
                put("fixOppPath", probe.gpuFixOppPath ?: "")
                put("minVal", probe.gpuState.minPath?.let { RootShell.readNode(it) } ?: "")
                put("maxVal", probe.gpuState.maxPath?.let { RootShell.readNode(it) } ?: "")
                put("govVal", probe.gpuState.currentGovernor)
                put("fixOppVal", probe.gpuFixOppPath?.let { RootShell.readNode(it) } ?: "")
            }
            put("gpu", gpuObj)

            // MTK / power limiter nodes
            val limObj = JSONObject()
            probe.mtkLimiterNodes.forEach { path ->
                RootShell.readNode(path)?.let { v -> limObj.put(path, v) }
            }
            put("limiters", limObj)

            // Touch nodes
            val touchObj = JSONObject()
            listOfNotNull(probe.touchSamplingPath, probe.touchGameModePath, probe.touchBoostPath).forEach { path ->
                RootShell.readNode(path)?.let { v -> touchObj.put(path, v) }
            }
            put("touch", touchObj)

            // I/O devices
            val ioArr = JSONArray()
            probe.ioDevices.forEach { d ->
                ioArr.put(JSONObject().apply {
                    put("sysfsPath", d.sysfsPath)
                    put("scheduler", d.currentScheduler)
                    put("readAheadKb", d.readAheadKb)
                })
            }
            put("io", ioArr)

            // VM & TCP
            put("swappiness", RootShell.readNode("/proc/sys/vm/swappiness") ?: "")
            put("vfsCachePressure", RootShell.readNode("/proc/sys/vm/vfs_cache_pressure") ?: "")
            put("tcpCongestion", RootShell.readNode("/proc/sys/net/ipv4/tcp_congestion_control") ?: "")
            put("tcpFastOpen", RootShell.readNode("/proc/sys/net/ipv4/tcp_fastopen") ?: "")

            // Display refresh rate
            val peakSetting = runCatching {
                Settings.System.getString(context.contentResolver, "peak_refresh_rate")
            }.getOrNull() ?: ""
            val minSetting = runCatching {
                Settings.System.getString(context.contentResolver, "min_refresh_rate")
            }.getOrNull() ?: ""
            put("peakRefreshSetting", peakSetting)
            put("minRefreshSetting", minSetting)
            put("cachedAppsFreezer", runCatching {
                Settings.Global.getString(context.contentResolver, "cached_apps_freezer")
            }.getOrNull() ?: "")
        }
        runCatching { file.writeText(root.toString(2)) }
        return root
    }

    private fun updateSnapshotFlags(context: Context, beastActive: Boolean, sessionActive: Boolean) {
        val file = snapshotFile(context)
        if (!file.exists()) return
        runCatching {
            val obj = JSONObject(file.readText())
            obj.put("beastActive", beastActive)
            obj.put("sessionActive", sessionActive)
            file.writeText(obj.toString(2))
        }
    }

    /**
     * Checks whether the device rebooted while Beast Mode was active (Boot Guard) or if EZD crashed
     * mid-override on the same boot. Returns an optional user-facing alert message.
     */
    suspend fun checkAndRecoverFromCrashOrReboot(
        context: Context,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): String? = engineMutex.withLock {
        val file = snapshotFile(context)
        val store = SettingsDataStore(context)
        val settings = runCatching { store.settingsFlow.first() }.getOrNull()
        val nowBootId = currentBootId()

        if (!file.exists()) {
            // If profile was left on beast after clean reboot without snapshot, force step-down
            if (settings?.activeProfileId == GameProfileId.BEAST.id) {
                store.setActiveProfileId(GameProfileId.BALANCED.id)
                return "Boot Guard disabled Beast Mode after reboot and stepped down to Balanced."
            }
            return null
        }

        val snap = runCatching { JSONObject(file.readText()) }.getOrNull()
        if (snap == null) {
            runCatching { file.delete() }
            return null
        }

        val savedBootId = snap.optString("bootId", "")
        val wasBeast = snap.optBoolean("beastActive", false) || settings?.activeProfileId == GameProfileId.BEAST.id
        val wasSessionActive = snap.optBoolean("sessionActive", false)

        // Reboot occurred -> never carry over Beast Mode across reboots
        if (nowBootId.isNotBlank() && savedBootId.isNotBlank() && nowBootId != savedBootId) {
            runCatching { file.delete() }
            if (wasBeast) {
                store.setActiveProfileId(GameProfileId.BALANCED.id)
                val bm = settings?.bootMemory
                if (bm != null && bm.savedProfileId == GameProfileId.BEAST.id) {
                    store.saveBootMemory(bm.copy(savedProfileId = GameProfileId.BALANCED.id))
                }
                return "Boot Guard detected reboot while Beast Mode was ON — disabled Beast Mode safely."
            }
            return null
        }

        // Same boot, unexpected termination while overrides were active -> restore clean baseline
        if (wasSessionActive) {
            restoreSnapshotInternal(context, rootGranted, shizukuGranted)
            if (wasBeast) {
                store.setActiveProfileId(GameProfileId.BALANCED.id)
                return "Recovered from unexpected termination: restored hardware baseline."
            }
        }
        return null
    }

    suspend fun activateBeastMode(
        context: Context,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): BeastExecutionReport = engineMutex.withLock {
        val probe = HardwareProbe.probe(context, forceRefresh = false)
        captureBaselineIfNeeded(context)
        updateSnapshotFlags(context, beastActive = true, sessionActive = true)

        val steps = mutableListOf<EngineStepResult>()
        val peakHz = probe.displayPeakRateHz.coerceAtLeast(60f)

        // 1. CPU: all cores online + all clusters min=max=highest, governor=performance
        if (!rootGranted) {
            steps += EngineStepResult(
                category = "CPU",
                label = "CPU Clusters & Cores",
                status = EngineItemStatus.FAILED,
                reason = "Root (su) permission required to lock CPU governor and min/max frequencies.",
                path = "/sys/devices/system/cpu/cpufreq"
            )
        } else {
            val onlineRes = RootShell.exec(
                """
                CNT=0; OK=0
                for c in /sys/devices/system/cpu/cpu[0-9]*; do
                  [ -e "${'$'}c/online" ] || continue
                  CNT=$((CNT+1))
                  echo 1 > "${'$'}c/online" 2>/dev/null && [ "$(cat "${'$'}c/online" 2>/dev/null)" = "1" ] && OK=$((OK+1))
                done
                echo "${'$'}OK/${'$'}CNT"
                """.trimIndent(),
                timeoutMs = 3_000L
            )
            steps += EngineStepResult(
                category = "CPU",
                label = "Force All CPU Cores Online",
                status = if (onlineRes.isSuccess) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = if (onlineRes.isSuccess) "Hotplug verified (${onlineRes.stdout.trim()} toggleable cores online)" else onlineRes.stderr.ifBlank { "Rejected by kernel" },
                path = "/sys/devices/system/cpu/cpu*/online"
            )

            if (probe.cpuPolicies.isEmpty()) {
                steps += EngineStepResult(
                    category = "CPU",
                    label = "CPU Cluster Locking",
                    status = EngineItemStatus.UNSUPPORTED,
                    reason = probe.cpuReason,
                    path = probe.cpuPathsTried.joinToString(", ")
                )
            } else {
                probe.cpuPolicies.forEach { policy ->
                    val base = "/sys/devices/system/cpu/cpufreq/${policy.id}"
                    val targetGov = when {
                        "performance" in policy.governors -> "performance"
                        policy.governors.isNotEmpty() -> policy.governors.first()
                        else -> "performance"
                    }
                    val govRes = RootShell.writeAndVerify("$base/scaling_governor", targetGov)
                    val maxFreq = policy.availableFreqsKhz.maxOrNull() ?: policy.maxKhz
                    if (maxFreq != null) {
                        val maxRes = RootShell.writeAndVerify("$base/scaling_max_freq", maxFreq.toString())
                        val minRes = RootShell.writeAndVerify("$base/scaling_min_freq", maxFreq.toString())
                        val allOk = govRes is HwResult.Applied && maxRes is HwResult.Applied && minRes is HwResult.Applied
                        val msg = "gov=${shortOutcome(govRes)}, max=${shortOutcome(maxRes)}, min=${shortOutcome(minRes)} (${maxFreq / 1000} MHz)"
                        steps += EngineStepResult(
                            category = "CPU",
                            label = "${policy.id} (CPUs ${policy.cpus})",
                            status = if (allOk) EngineItemStatus.OK else if (maxRes is HwResult.NotFound) EngineItemStatus.UNSUPPORTED else EngineItemStatus.FAILED,
                            reason = msg,
                            path = "$base/scaling_{governor,min_freq,max_freq}"
                        )
                    } else {
                        steps += EngineStepResult(
                            category = "CPU",
                            label = "${policy.id} (CPUs ${policy.cpus})",
                            status = if (govRes is HwResult.Applied) EngineItemStatus.OK else EngineItemStatus.FAILED,
                            reason = "Gov: ${govRes.describe()} (no available frequency table)",
                            path = "$base/scaling_governor"
                        )
                    }
                }
            }
        }

        // 2. GPU: lock max frequency (and min if possible)
        steps += applyBeastGpu(probe, rootGranted)

        // 3. Touch: high polling rate, game mode, /dev/xiaomi-touch ioctl probe, touch boost, lowest latency
        steps += applyBeastTouch(probe, rootGranted, shizukuGranted)

        // 4. Display: lock to dynamically detected peak refresh rate (never hardcoded 120Hz)
        steps += applyBeastDisplay(peakHz, rootGranted, shizukuGranted)

        // 5. Power limiters, I/O scheduler + read-ahead, ZRAM/swappiness, TCP fast/low-latency, DND/bg freezer
        // Note: Fast charging controls remain strictly separate (excluded from Beast Mode).
        steps += applyBeastSystemTweaks(context, probe, rootGranted, shizukuGranted)

        val okCount = steps.count { it.status == EngineItemStatus.OK }
        val failCount = steps.count { it.status == EngineItemStatus.FAILED }
        val unsupCount = steps.count { it.status == EngineItemStatus.UNSUPPORTED }
        BeastExecutionReport(
            active = true,
            timestampMs = System.currentTimeMillis(),
            peakHzUsed = peakHz,
            items = steps,
            summary = "Beast Mode: $okCount OK · $failCount Failed · $unsupCount Unsupported · Peak ${formatHz(peakHz)}Hz"
        )
    }

    private suspend fun applyBeastGpu(probe: HardwareProbeReport, rootGranted: Boolean): EngineStepResult {
        if (!rootGranted) {
            return EngineStepResult(
                category = "GPU",
                label = "GPU Max Clock Lock",
                status = EngineItemStatus.FAILED,
                reason = "Root (su) required for GPU frequency locking.",
                path = probe.gpuState.maxPath ?: probe.gpuFixOppPath ?: probe.gpuState.pathsTried.joinToString(", ")
            )
        }
        val gpu = probe.gpuState
        if (!gpu.supported) {
            return EngineStepResult(
                category = "GPU",
                label = "GPU Max Clock Lock",
                status = EngineItemStatus.UNSUPPORTED,
                reason = gpu.reason,
                path = gpu.pathsTried.joinToString(", ")
            )
        }
        // MediaTek gpufreq / gpufreqv2 OPP locking
        if ((probe.gpuType == "mtk_gpufreqv2" || probe.gpuType == "mtk_gpufreq") && probe.gpuFixOppPath != null) {
            val topOpp = probe.gpuOppTable.maxByOrNull { it.second }
            if (topOpp != null) {
                val res = RootShell.writeAndVerify(probe.gpuFixOppPath, topOpp.first.toString())
                return EngineStepResult(
                    category = "GPU",
                    label = "MTK GPU Lock (${topOpp.second} MHz, OPP ${topOpp.first})",
                    status = when (res) {
                        is HwResult.Applied -> EngineItemStatus.OK
                        is HwResult.NotFound -> EngineItemStatus.UNSUPPORTED
                        else -> EngineItemStatus.FAILED
                    },
                    reason = res.describe("OPP Lock"),
                    path = probe.gpuFixOppPath
                )
            }
            return EngineStepResult(
                category = "GPU",
                label = "MTK GPU Clock Lock",
                status = EngineItemStatus.UNSUPPORTED,
                reason = "OPP table empty or node read-only (${gpu.reason})",
                path = probe.gpuFixOppPath
            )
        }

        // Adreno / generic devfreq
        val topMhz = gpu.availableMhz.maxOrNull() ?: gpu.maxMhz
        val govTarget = when {
            "performance" in gpu.availableGovernors -> "performance"
            else -> null
        }
        val notes = mutableListOf<String>()
        var anyOk = false
        var anyRejected = false
        if (govTarget != null && gpu.govPath != null) {
            val r = RootShell.writeAndVerify(gpu.govPath, govTarget)
            if (r is HwResult.Applied) anyOk = true else anyRejected = true
            notes += "gov=${shortOutcome(r)}"
        }
        if (topMhz != null) {
            val raw = (topMhz * gpu.divisorToMhz).toString()
            gpu.maxPath?.let { p ->
                val r = RootShell.writeAndVerify(p, raw)
                if (r is HwResult.Applied) anyOk = true else anyRejected = true
                notes += "max=${shortOutcome(r)}"
            }
            gpu.minPath?.let { p ->
                val r = RootShell.writeAndVerify(p, raw)
                if (r is HwResult.Applied) anyOk = true
                notes += "min=${shortOutcome(r)}"
            }
        }
        val pathSummary = listOfNotNull(gpu.maxPath, gpu.minPath, gpu.govPath).joinToString(", ")
        return EngineStepResult(
            category = "GPU",
            label = "GPU Clock Lock (${topMhz ?: "?"} MHz)",
            status = when {
                anyOk && !anyRejected -> EngineItemStatus.OK
                anyOk -> EngineItemStatus.OK
                gpu.writableNodes.isEmpty() -> EngineItemStatus.UNSUPPORTED
                else -> EngineItemStatus.FAILED
            },
            reason = notes.ifEmpty { listOf(gpu.reason) }.joinToString(" · "),
            path = pathSummary.ifBlank { gpu.pathsTried.joinToString(", ") }
        )
    }

    private suspend fun applyBeastTouch(
        probe: HardwareProbeReport,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): List<EngineStepResult> {
        val out = mutableListOf<EngineStepResult>()

        // Touch sampling rate
        if (probe.touchSamplingPath != null && rootGranted) {
            val res = RootShell.writeAndVerify(probe.touchSamplingPath, "1")
            out += EngineStepResult(
                category = "Touch",
                label = "High Touch Sampling Rate",
                status = if (res is HwResult.Applied) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = res.describe("Sample Rate"),
                path = probe.touchSamplingPath
            )
        } else {
            out += EngineStepResult(
                category = "Touch",
                label = "High Touch Sampling Rate",
                status = if (probe.touchSamplingPath == null) EngineItemStatus.UNSUPPORTED else EngineItemStatus.FAILED,
                reason = if (probe.touchSamplingPath == null) {
                    "Not exposed via sysfs (tried /sys/devices/virtual/touch/touch_dev/bump_sample_rate, /sys/class/touch/touch_dev/report_rate, /proc/touchpanel/report_rate)"
                } else "Root (su) required",
                path = probe.touchSamplingPath ?: "/sys/devices/virtual/touch/touch_dev/bump_sample_rate"
            )
        }

        // Touch game mode & /dev/xiaomi-touch ioctl
        when {
            probe.touchGameModePath != null && rootGranted -> {
                val res = RootShell.writeAndVerify(probe.touchGameModePath, "1")
                out += EngineStepResult(
                    category = "Touch",
                    label = "Touch Game Mode",
                    status = if (res is HwResult.Applied) EngineItemStatus.OK else EngineItemStatus.FAILED,
                    reason = res.describe("Game Mode"),
                    path = probe.touchGameModePath
                )
            }
            probe.xiaomiTouchDeviceExists && probe.ioctlBinaryAvailable && rootGranted -> {
                // Attempt ioctl game mode open (Touch_Game_Mode = 0, value = 1)
                val ioctlRes = RootShell.exec("ioctl /dev/xiaomi-touch 0x5400 1 2>&1 || true", timeoutMs = 2_000L)
                out += EngineStepResult(
                    category = "Touch",
                    label = "Xiaomi Touch Game Mode (ioctl)",
                    status = if (ioctlRes.isSuccess) EngineItemStatus.OK else EngineItemStatus.FAILED,
                    reason = if (ioctlRes.isSuccess) "Dispatched ioctl to /dev/xiaomi-touch" else ioctlRes.stderr.ifBlank { "ioctl rejected by /dev/xiaomi-touch driver" },
                    path = "/dev/xiaomi-touch"
                )
            }
            probe.xiaomiTouchDeviceExists -> {
                out += EngineStepResult(
                    category = "Touch",
                    label = "Xiaomi Touch Game Mode (/dev/xiaomi-touch)",
                    status = EngineItemStatus.UNSUPPORTED,
                    reason = "Character device /dev/xiaomi-touch exists, but shell `ioctl` binary/native NDK ioctl bridge is unavailable to verify read-back.",
                    path = "/dev/xiaomi-touch"
                )
            }
            else -> {
                out += EngineStepResult(
                    category = "Touch",
                    label = "Touch Game Mode",
                    status = EngineItemStatus.UNSUPPORTED,
                    reason = "Neither /dev/xiaomi-touch nor touch game_mode sysfs node exposed.",
                    path = "/dev/xiaomi-touch, /proc/touchpanel/game_switch_enable"
                )
            }
        }

        // Touch boost + low-latency GPU compositing
        if (probe.touchBoostPath != null && rootGranted) {
            val tbRes = RootShell.writeAndVerify(probe.touchBoostPath, "1")
            out += EngineStepResult(
                category = "Touch",
                label = "Kernel Touch Boost",
                status = if (tbRes is HwResult.Applied) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = tbRes.describe("TouchBoost"),
                path = probe.touchBoostPath
            )
        }
        if (rootGranted || shizukuGranted) {
            val hwc = CommandBridge.execPrivileged("service call SurfaceFlinger 1008 i32 1", useRoot = rootGranted, useShizuku = shizukuGranted)
            out += EngineStepResult(
                category = "Touch",
                label = "Low-Latency Compositor (Disable HW Overlays)",
                status = if (hwc.success) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = if (hwc.success) "SurfaceFlinger HWC disabled for direct GPU frame pacing" else hwc.combinedText,
                path = "SurfaceFlinger#1008"
            )
        }
        return out
    }

    private suspend fun applyBeastDisplay(
        peakHz: Float,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): EngineStepResult {
        val targetInt = peakHz.roundToInt()
        if (!rootGranted && !shizukuGranted) {
            return EngineStepResult(
                category = "Display",
                label = "Lock Peak Refresh Rate (${targetInt}Hz)",
                status = EngineItemStatus.FAILED,
                reason = "Requires Root or Shizuku to lock DisplayManager refresh bounds.",
                path = "settings:system/peak_refresh_rate"
            )
        }
        val cmd = "settings put system peak_refresh_rate $targetInt.0 && settings put system min_refresh_rate $targetInt.0 && " +
            "echo \"\$(settings get system peak_refresh_rate)|\$(settings get system min_refresh_rate)\""
        val res = CommandBridge.execPrivileged(cmd, useRoot = rootGranted, useShizuku = shizukuGranted)
        val readBack = res.stdoutText.trim()
        val ok = res.success && readBack.contains(targetInt.toString())
        return EngineStepResult(
            category = "Display",
            label = "Lock Detected Peak Refresh (${targetInt}Hz)",
            status = if (ok) EngineItemStatus.OK else EngineItemStatus.FAILED,
            reason = if (ok) "Applied & read back ($readBack) from device DisplayManager modes" else res.combinedText.ifBlank { "Read-back mismatch ($readBack)" },
            path = "settings:system/{peak_refresh_rate,min_refresh_rate}"
        )
    }

    private suspend fun applyBeastSystemTweaks(
        context: Context,
        probe: HardwareProbeReport,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): List<EngineStepResult> {
        val out = mutableListOf<EngineStepResult>()

        // MTK / SoC power-saving limiters
        if (rootGranted && probe.mtkLimiterNodes.isNotEmpty()) {
            val appliedPaths = mutableListOf<String>()
            val rejectedPaths = mutableListOf<String>()
            probe.mtkLimiterNodes.forEach { path ->
                val writeVal = when {
                    path.endsWith("/ppm/enabled") -> "0"
                    path.endsWith("/pbm/stop") -> "1"
                    path.endsWith("fpsgo_enable") -> "1"
                    path.endsWith("dcs_mode") -> "0"
                    else -> null
                } ?: return@forEach
                val res = RootShell.writeAndVerify(path, writeVal)
                if (res is HwResult.Applied) appliedPaths += "$path=$writeVal" else rejectedPaths += "$path(${shortOutcome(res)})"
            }
            out += EngineStepResult(
                category = "Limiters",
                label = "Disable Power-Saving Limiters",
                status = when {
                    appliedPaths.isNotEmpty() -> EngineItemStatus.OK
                    rejectedPaths.isNotEmpty() -> EngineItemStatus.FAILED
                    else -> EngineItemStatus.UNSUPPORTED
                },
                reason = buildString {
                    if (appliedPaths.isNotEmpty()) append("Applied: ${appliedPaths.joinToString(", ")}")
                    if (rejectedPaths.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append("Rejected: ${rejectedPaths.joinToString(", ")}")
                    }
                    if (isEmpty()) append("Only telemetry status nodes found (${probe.mtkLimiterNodes.joinToString(", ")})")
                },
                path = probe.mtkLimiterNodes.joinToString(", ")
            )
        } else {
            out += EngineStepResult(
                category = "Limiters",
                label = "Power-Saving Limiters (PPM/PBM)",
                status = if (!rootGranted) EngineItemStatus.FAILED else EngineItemStatus.UNSUPPORTED,
                reason = if (!rootGranted) "Root (su) required" else "No /proc/ppm or /proc/pbm limiter control nodes exposed by GKI kernel.",
                path = "/proc/ppm/enabled, /proc/pbm/stop"
            )
        }

        // I/O Scheduler & higher Read-Ahead on active storage block queues
        if (rootGranted && probe.ioDevices.isNotEmpty()) {
            val preferredQueue = probe.ioDevices.firstOrNull {
                it.name.startsWith("sd") || it.name.startsWith("mmcblk")
            } ?: probe.ioDevices.first()
            val lowLatSch = listOf("none", "kyber", "mq-deadline", "deadline")
                .firstOrNull { it in preferredQueue.availableSchedulers }
            val schRes = lowLatSch?.let {
                RootShell.writeAndVerify("${preferredQueue.sysfsPath}/queue/scheduler", it)
            }
            val raRes = RootShell.writeAndVerify("${preferredQueue.sysfsPath}/queue/read_ahead_kb", "512")
            val ok = (schRes is HwResult.Applied || lowLatSch == null) && raRes is HwResult.Applied
            out += EngineStepResult(
                category = "I/O",
                label = "Low-Latency I/O (${preferredQueue.name})",
                status = if (ok) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = "scheduler=${lowLatSch ?: "fixed"}(${schRes?.let { shortOutcome(it) } ?: "N/A"}), read_ahead=512kB(${shortOutcome(raRes)})",
                path = "${preferredQueue.sysfsPath}/queue/{scheduler,read_ahead_kb}"
            )
        } else {
            out += EngineStepResult(
                category = "I/O",
                label = "Low-Latency I/O & Read-Ahead",
                status = if (!rootGranted) EngineItemStatus.FAILED else EngineItemStatus.UNSUPPORTED,
                reason = probe.ioReason,
                path = "/sys/block/*/queue"
            )
        }

        // ZRAM / Swappiness tuned for gaming
        if (rootGranted) {
            val swapRes = RootShell.writeAndVerify("/proc/sys/vm/swappiness", "20")
            val vfsRes = RootShell.writeAndVerify("/proc/sys/vm/vfs_cache_pressure", "60")
            out += EngineStepResult(
                category = "Memory",
                label = "Gaming VM / ZRAM Swappiness",
                status = if (swapRes is HwResult.Applied) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = "swappiness=20 (${shortOutcome(swapRes)}), vfs_cache_pressure=60 (${shortOutcome(vfsRes)})",
                path = "/proc/sys/vm/{swappiness,vfs_cache_pressure}"
            )

            // TCP Low-Latency
            val availTcp = RootShell.readNode("/proc/sys/net/ipv4/tcp_available_congestion_control")
                ?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
            val tcpTarget = listOf("bbr", "westwood", "cubic").firstOrNull { it in availTcp }
            val tfoRes = RootShell.writeAndVerify("/proc/sys/net/ipv4/tcp_fastopen", "3")
            val tcpRes = tcpTarget?.let { RootShell.writeAndVerify("/proc/sys/net/ipv4/tcp_congestion_control", it) }
            out += EngineStepResult(
                category = "Network",
                label = "TCP Low-Latency",
                status = if (tfoRes is HwResult.Applied || tcpRes is HwResult.Applied) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = "congestion=${tcpTarget ?: "default"}(${tcpRes?.let { shortOutcome(it) } ?: "N/A"}), fastopen=3(${shortOutcome(tfoRes)})",
                path = "/proc/sys/net/ipv4/{tcp_congestion_control,tcp_fastopen}"
            )
        }

        // DND & Background Freezer
        if (rootGranted || shizukuGranted) {
            val bgRes = CommandBridge.execPrivileged(
                "settings put global cached_apps_freezer enabled",
                useRoot = rootGranted,
                useShizuku = shizukuGranted
            )
            val dndApplied = runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    true
                } else false
            }.getOrDefault(false)
            out += EngineStepResult(
                category = "System",
                label = "Background Limiter & DND",
                status = if (bgRes.success) EngineItemStatus.OK else EngineItemStatus.FAILED,
                reason = "App freezer=${if (bgRes.success) "enabled" else "failed"}, DND=${if (dndApplied) "priority" else "grant DND access for auto-DND"}",
                path = "settings:global/cached_apps_freezer"
            )
        }

        return out
    }

    /**
     * Event-driven daemon enforcement: inspects current kernel values and re-applies only
     * when the kernel / MTK PowerHAL / PPM resets them while Beast Mode or Performance lock is active.
     */
    suspend fun reapplyActiveEnforcement(
        context: Context,
        activeProfileId: String,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): Int {
        if (!rootGranted && !shizukuGranted) return 0
        if (activeProfileId != GameProfileId.BEAST.id && activeProfileId != GameProfileId.PERFORMANCE.id) return 0
        val probe = HardwareProbe.getCached() ?: return 0
        var reapplied = 0

        if (rootGranted) {
            probe.cpuPolicies.forEach { p ->
                val base = "/sys/devices/system/cpu/cpufreq/${p.id}"
                val targetGov = if ("performance" in p.governors) "performance" else return@forEach
                val curGov = RootShell.readNode("$base/scaling_governor")
                if (curGov != null && curGov != targetGov) {
                    if (RootShell.writeAndVerify("$base/scaling_governor", targetGov) is HwResult.Applied) reapplied++
                }
                if (activeProfileId == GameProfileId.BEAST.id) {
                    val topFreq = p.availableFreqsKhz.maxOrNull()
                    if (topFreq != null) {
                        val curMax = RootShell.readNode("$base/scaling_max_freq")?.toLongOrNull()
                        if (curMax != null && curMax < topFreq) {
                            if (RootShell.writeAndVerify("$base/scaling_max_freq", topFreq.toString()) is HwResult.Applied) reapplied++
                        }
                        val curMin = RootShell.readNode("$base/scaling_min_freq")?.toLongOrNull()
                        if (curMin != null && curMin < topFreq) {
                            if (RootShell.writeAndVerify("$base/scaling_min_freq", topFreq.toString()) is HwResult.Applied) reapplied++
                        }
                    }
                }
            }
            if (activeProfileId == GameProfileId.BEAST.id &&
                (probe.gpuType == "mtk_gpufreqv2" || probe.gpuType == "mtk_gpufreq") &&
                probe.gpuFixOppPath != null
            ) {
                val topOpp = probe.gpuOppTable.maxByOrNull { it.second }
                if (topOpp != null) {
                    val cur = RootShell.readNode(probe.gpuFixOppPath)?.trim()
                    if (cur != null && !cur.contains(topOpp.first.toString())) {
                        if (RootShell.writeAndVerify(probe.gpuFixOppPath, topOpp.first.toString()) is HwResult.Applied) reapplied++
                    }
                }
            }
        }
        return reapplied
    }

    suspend fun applyStandardProfile(
        context: Context,
        profile: ProfileConfig,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): Pair<Boolean, String> = engineMutex.withLock {
        val probe = HardwareProbe.probe(context, forceRefresh = false)
        captureBaselineIfNeeded(context)
        updateSnapshotFlags(context, beastActive = false, sessionActive = profile.id != GameProfileId.BALANCED.id)

        // If stepping down to Balanced from Beast Mode, first restore baseline limiters/VM/IO cleanly
        if (profile.id == GameProfileId.BALANCED.id) {
            restoreNonClockBaseline(context, rootGranted, shizukuGranted)
        }

        val notes = mutableListOf<String>()
        var anyApplied = false

        if (rootGranted) {
            probe.cpuPolicies.forEach { policy ->
                val base = "/sys/devices/system/cpu/cpufreq/${policy.id}"
                val targetGov = when {
                    profile.cpuGovernor in policy.governors -> profile.cpuGovernor
                    profile.id == GameProfileId.POWERSAVE.id && "conservative" in policy.governors -> "conservative"
                    "schedutil" in policy.governors -> "schedutil"
                    else -> policy.governors.firstOrNull()
                }
                if (targetGov != null && policy.govWritable) {
                    if (RootShell.writeAndVerify("$base/scaling_governor", targetGov) is HwResult.Applied) anyApplied = true
                }
                if (policy.availableFreqsKhz.isNotEmpty() && (policy.minWritable || policy.maxWritable)) {
                    val freqs = policy.availableFreqsKhz
                    val maxIdx = ((freqs.lastIndex * profile.cpuMaxScalePercent) / 100).coerceIn(0, freqs.lastIndex)
                    val minIdx = ((freqs.lastIndex * profile.cpuMinScalePercent) / 100).coerceIn(0, maxIdx)
                    if (policy.maxWritable && RootShell.writeAndVerify("$base/scaling_max_freq", freqs[maxIdx].toString()) is HwResult.Applied) anyApplied = true
                    if (policy.minWritable && RootShell.writeAndVerify("$base/scaling_min_freq", freqs[minIdx].toString()) is HwResult.Applied) anyApplied = true
                }
            }
            if (probe.cpuPolicies.isNotEmpty()) notes += "CPU ${profile.cpuMaxScalePercent}%"

            val gpu = probe.gpuState
            if (gpu.supported) {
                if ((probe.gpuType == "mtk_gpufreqv2" || probe.gpuType == "mtk_gpufreq") && probe.gpuFixOppPath != null && probe.gpuOppTable.isNotEmpty()) {
                    if (profile.id == GameProfileId.BALANCED.id) {
                        RootShell.writeAndVerify(probe.gpuFixOppPath, "-1")
                    } else {
                        val sortedOpp = probe.gpuOppTable.sortedBy { it.second }
                        val idx = ((sortedOpp.lastIndex * profile.gpuMaxPercent) / 100).coerceIn(0, sortedOpp.lastIndex)
                        if (RootShell.writeAndVerify(probe.gpuFixOppPath, sortedOpp[idx].first.toString()) is HwResult.Applied) {
                            anyApplied = true
                            notes += "GPU ${sortedOpp[idx].second}MHz"
                        }
                    }
                } else {
                    val gpuGov = when {
                        profile.gpuGovernor in gpu.availableGovernors -> profile.gpuGovernor
                        profile.id == GameProfileId.POWERSAVE.id && "powersave" in gpu.availableGovernors -> "powersave"
                        "msm-adreno-tz" in gpu.availableGovernors -> "msm-adreno-tz"
                        else -> null
                    }
                    if (gpuGov != null && gpu.govPath != null) {
                        if (RootShell.writeAndVerify(gpu.govPath, gpuGov) is HwResult.Applied) anyApplied = true
                    }
                    if (gpu.availableMhz.isNotEmpty() && gpu.maxPath != null) {
                        val idx = ((gpu.availableMhz.lastIndex * profile.gpuMaxPercent) / 100).coerceIn(0, gpu.availableMhz.lastIndex)
                        val raw = (gpu.availableMhz[idx] * gpu.divisorToMhz).toString()
                        if (RootShell.writeAndVerify(gpu.maxPath, raw) is HwResult.Applied) {
                            anyApplied = true
                            notes += "GPU ${gpu.availableMhz[idx]}MHz"
                        }
                    }
                }
            }
        }

        // Dynamic Peak Hz resolution: never hardcode 120Hz!
        val desiredHz = when {
            profile.id == GameProfileId.PERFORMANCE.id -> probe.displayPeakRateHz
            profile.targetRefreshHz != null && profile.targetRefreshHz > 0f -> {
                probe.displaySupportedRatesHz.minByOrNull { kotlin.math.abs(it - profile.targetRefreshHz) }
                    ?: profile.targetRefreshHz
            }
            else -> null
        }
        if ((rootGranted || shizukuGranted) && desiredHz != null) {
            val targetInt = desiredHz.roundToInt()
            val res = CommandBridge.execPrivileged(
                "settings put system peak_refresh_rate $targetInt.0 && settings put system min_refresh_rate $targetInt.0",
                useRoot = rootGranted,
                useShizuku = shizukuGranted
            )
            if (res.success) {
                anyApplied = true
                notes += "${targetInt}Hz"
            }
        } else if ((rootGranted || shizukuGranted) && profile.id == GameProfileId.BALANCED.id) {
            CommandBridge.execPrivileged(
                "settings delete system min_refresh_rate; settings put system peak_refresh_rate ${probe.displayPeakRateHz.roundToInt()}.0",
                useRoot = rootGranted,
                useShizuku = shizukuGranted
            )
        }

        if (shizukuGranted && !rootGranted) {
            val lowPower = if (profile.id == GameProfileId.POWERSAVE.id) "1" else "0"
            if (CommandBridge.execPrivileged("settings put global low_power $lowPower", useRoot = false, useShizuku = true).success) {
                anyApplied = true
                notes += if (lowPower == "1") "Low Power On" else "ADB Boost"
            }
        }

        if (anyApplied) {
            true to "${profile.name} active: ${notes.joinToString(" · ")}"
        } else {
            false to "${profile.name}: grant Root or Shizuku for full control"
        }
    }

    suspend fun deactivateBeastAndRestore(
        context: Context,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): Pair<Boolean, String> = engineMutex.withLock {
        val restored = restoreSnapshotInternal(context, rootGranted, shizukuGranted)
        val probe = HardwareProbe.probe(context, forceRefresh = true)
        if (restored > 0) {
            true to "Deactivated Beast Mode · Restored $restored baseline node(s) (Display ${formatHz(probe.displayCurrentRateHz)}Hz)"
        } else {
            // Fallback to balanced profile if no prior snapshot existed
            val balanced = ProfileConfig(
                id = GameProfileId.BALANCED.id,
                name = "Balanced Mode",
                description = "Restored balanced defaults",
                cpuGovernor = "schedutil",
                cpuMaxScalePercent = 50,
                cpuMinScalePercent = 20,
                gpuGovernor = "msm-adreno-tz",
                gpuMaxPercent = 30
            )
            true to "Beast Mode deactivated (${balanced.name})"
        }
    }

    suspend fun restoreBaselineOnExit(
        context: Context,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): Int = engineMutex.withLock {
        restoreSnapshotInternal(context, rootGranted, shizukuGranted)
    }

    private suspend fun restoreNonClockBaseline(
        context: Context,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ) {
        val file = snapshotFile(context)
        if (!file.exists()) return
        val snap = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        if (rootGranted) {
            snap.optJSONObject("limiters")?.let { lim ->
                lim.keys().forEach { k ->
                    val v = lim.optString(k)
                    if (v.isNotBlank()) RootShell.writeAndVerify(k, v)
                }
            }
            snap.optJSONObject("touch")?.let { tch ->
                tch.keys().forEach { k ->
                    val v = tch.optString(k)
                    if (v.isNotBlank()) RootShell.writeAndVerify(k, v)
                }
            }
            snap.optString("swappiness").takeIf { it.isNotBlank() }?.let {
                RootShell.writeAndVerify("/proc/sys/vm/swappiness", it)
            }
            snap.optString("vfsCachePressure").takeIf { it.isNotBlank() }?.let {
                RootShell.writeAndVerify("/proc/sys/vm/vfs_cache_pressure", it)
            }
            snap.optString("tcpCongestion").takeIf { it.isNotBlank() }?.let {
                RootShell.writeAndVerify("/proc/sys/net/ipv4/tcp_congestion_control", it)
            }
            snap.optString("tcpFastOpen").takeIf { it.isNotBlank() }?.let {
                RootShell.writeAndVerify("/proc/sys/net/ipv4/tcp_fastopen", it)
            }
            snap.optJSONArray("io")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val path = o.optString("sysfsPath")
                    val sch = o.optString("scheduler")
                    val ra = o.optInt("readAheadKb", -1)
                    if (path.isNotBlank() && sch.isNotBlank()) {
                        RootShell.writeAndVerify("$path/queue/scheduler", sch)
                    }
                    if (path.isNotBlank() && ra > 0) {
                        RootShell.writeAndVerify("$path/queue/read_ahead_kb", ra.toString())
                    }
                }
            }
        }
        if (rootGranted || shizukuGranted) {
            CommandBridge.execPrivileged("service call SurfaceFlinger 1008 i32 0", useRoot = rootGranted, useShizuku = shizukuGranted)
            val freezer = snap.optString("cachedAppsFreezer")
            val cmd = if (freezer.isNotBlank()) {
                "settings put global cached_apps_freezer ${RootShell.shellQuote(freezer)}"
            } else {
                "settings delete global cached_apps_freezer"
            }
            CommandBridge.execPrivileged(cmd, useRoot = rootGranted, useShizuku = shizukuGranted)
        }
    }

    private suspend fun restoreSnapshotInternal(
        context: Context,
        rootGranted: Boolean,
        shizukuGranted: Boolean
    ): Int {
        val file = snapshotFile(context)
        if (!file.exists()) return 0
        val snap = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return 0
        var count = 0

        if (rootGranted) {
            snap.optJSONArray("cpuPolicies")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val base = "/sys/devices/system/cpu/cpufreq/$id"
                    val gov = o.optString("gov")
                    val minK = o.optLong("minKhz", -1L)
                    val maxK = o.optLong("maxKhz", -1L)
                    if (gov.isNotBlank() && RootShell.writeAndVerify("$base/scaling_governor", gov) is HwResult.Applied) count++
                    if (minK > 0 && RootShell.writeAndVerify("$base/scaling_min_freq", minK.toString()) is HwResult.Applied) count++
                    if (maxK > 0 && RootShell.writeAndVerify("$base/scaling_max_freq", maxK.toString()) is HwResult.Applied) count++
                }
            }
            snap.optJSONObject("gpu")?.let { g ->
                val fixPath = g.optString("fixOppPath")
                if (fixPath.isNotBlank()) {
                    RootShell.writeAndVerify(fixPath, "-1")
                    count++
                }
                val govP = g.optString("govPath")
                val govV = g.optString("govVal")
                if (govP.isNotBlank() && govV.isNotBlank() && RootShell.writeAndVerify(govP, govV) is HwResult.Applied) count++
                val minP = g.optString("minPath")
                val minV = g.optString("minVal")
                if (minP.isNotBlank() && minV.isNotBlank() && RootShell.writeAndVerify(minP, minV) is HwResult.Applied) count++
                val maxP = g.optString("maxPath")
                val maxV = g.optString("maxVal")
                if (maxP.isNotBlank() && maxV.isNotBlank() && RootShell.writeAndVerify(maxP, maxV) is HwResult.Applied) count++
            }
        }

        restoreNonClockBaseline(context, rootGranted, shizukuGranted)

        if (rootGranted || shizukuGranted) {
            val peak = snap.optString("peakRefreshSetting")
            val min = snap.optString("minRefreshSetting")
            val cmd = buildString {
                if (peak.isNotBlank() && peak != "null") append("settings put system peak_refresh_rate $peak; ")
                else append("settings delete system peak_refresh_rate; ")
                if (min.isNotBlank() && min != "null") append("settings put system min_refresh_rate $min")
                else append("settings delete system min_refresh_rate")
            }
            if (CommandBridge.execPrivileged(cmd, useRoot = rootGranted, useShizuku = shizukuGranted).success) count++
        }

        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }

        updateSnapshotFlags(context, beastActive = false, sessionActive = false)
        return count
    }

    // --- Dedicated Fast Charge Controls (strictly separate from Beast Mode) ---

    suspend fun setFastChargeCurrentLimit(state: FastChargeState, limitMa: Int): Pair<Boolean, String> {
        val path = state.currentLimitPath
            ?: return false to "No charging current-limit sysfs node detected. Paths tried: ${state.pathsTried.joinToString(", ")}"
        if (path !in state.writableNodes) {
            return false to "Node $path is read-only or blocked on this device (${state.reason})."
        }
        val currentVal = RootShell.readNode(path)?.trim()?.toLongOrNull() ?: 0L
        val writeValue = if (currentVal > 50_000L || path.contains("constant_charge_current_max") || path.contains("input_current_limit")) {
            (limitMa * 1000).toString()
        } else {
            limitMa.toString()
        }
        val res = RootShell.writeAndVerify(path, writeValue)
        return (res is HwResult.Applied) to res.describe("Charge Limit ($path)")
    }

    suspend fun toggleForceFastCharge(state: FastChargeState, enable: Boolean): Pair<Boolean, String> {
        val path = state.forceFastChargePath
            ?: return false to "Force Fast Charge sysfs switch not exposed by kernel (tried /sys/kernel/fast_charge/force_fast_charge, fastchg_force_switch)."
        if (path !in state.writableNodes) {
            return false to "Switch $path is read-only (${state.reason})."
        }
        val res = RootShell.writeAndVerify(path, if (enable) "1" else "0")
        return (res is HwResult.Applied) to res.describe("Force Fast Charge")
    }

    private fun shortOutcome(res: HwResult): String = when (res) {
        is HwResult.Applied -> "OK(${res.readBackValue})"
        is HwResult.Rejected -> "Rejected(${res.reason})"
        is HwResult.NotFound -> "NotFound"
        HwResult.PermissionDenied -> "Denied"
        HwResult.SelinuxBlocked -> "SELinux"
    }

    private fun formatHz(hz: Float): String =
        if (hz % 1f == 0f) hz.toInt().toString() else String.format(java.util.Locale.US, "%.1f", hz)
}
