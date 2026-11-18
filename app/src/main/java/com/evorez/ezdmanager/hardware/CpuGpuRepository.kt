package com.evorez.ezdmanager.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.evorez.ezdmanager.model.CpuCore
import com.evorez.ezdmanager.model.CpuPolicy
import com.evorez.ezdmanager.model.DeviceOverview
import com.evorez.ezdmanager.model.GpuState
import com.evorez.ezdmanager.root.HwResult
import com.evorez.ezdmanager.root.RootShell
import com.evorez.ezdmanager.root.describe

class CpuGpuRepository(private val context: Context) {

    @Volatile
    private var lastCpuUsagePercent: Int? = null

    fun readDeviceOverview(): DeviceOverview {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val statusInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val chargingLabel = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }

        return DeviceOverview(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            kernelVersion = HardwareProbe.getCached()?.kernelVersion ?: (System.getProperty("os.version") ?: "Unknown"),
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            batteryPercent = pct,
            chargingStatus = chargingLabel,
            cpuUsagePercent = lastCpuUsagePercent
        )
    }

    suspend fun sampleOverviewAsync(): DeviceOverview {
        lastCpuUsagePercent = HardwareProbe.sampleCpuUsageViaShell() ?: lastCpuUsagePercent
        return readDeviceOverview()
    }

    suspend fun scanCpuPolicies(forceRefresh: Boolean = true): List<CpuPolicy> {
        return HardwareProbe.probe(context, forceRefresh = forceRefresh).cpuPolicies
    }

    suspend fun scanCpuCores(forceRefresh: Boolean = true): List<CpuCore> {
        return HardwareProbe.probe(context, forceRefresh = forceRefresh).cpuCores
    }

    suspend fun setCpuFrequencyRange(
        policy: CpuPolicy,
        newMinKhz: Long?,
        newMaxKhz: Long?
    ): Pair<Boolean, String> {
        if (!policy.id.matches(Regex("policy[0-9]+"))) return false to "Rejected: invalid policy ID ${policy.id}"
        val targetMin = newMinKhz ?: policy.minKhz
        val targetMax = newMaxKhz ?: policy.maxKhz
        if (targetMin == null || targetMax == null || targetMin <= 0 || targetMax <= 0) {
            return false to "Rejected: frequency bounds unavailable for ${policy.id}"
        }
        if (targetMin > targetMax) {
            return false to "Rejected: minimum frequency ($targetMin) exceeds maximum ($targetMax)"
        }
        if (policy.availableFreqsKhz.size > 2 &&
            (targetMin !in policy.availableFreqsKhz || targetMax !in policy.availableFreqsKhz)
        ) {
            return false to "Rejected: requested frequency not in ${policy.id} kernel table"
        }

        val minPath = "/sys/devices/system/cpu/cpufreq/${policy.id}/scaling_min_freq"
        val maxPath = "/sys/devices/system/cpu/cpufreq/${policy.id}/scaling_max_freq"

        val messages = mutableListOf<String>()
        var allOk = true

        val currentMax = policy.maxKhz ?: targetMax
        val applyMaxFirst = targetMax >= currentMax

        suspend fun applyMax(): Boolean {
            if (newMaxKhz == null) return true
            val res = RootShell.writeAndVerify(maxPath, targetMax.toString())
            val ok = res is HwResult.Applied
            messages.add(if (ok) "max=${targetMax / 1000} MHz" else res.describe("max"))
            return ok
        }

        suspend fun applyMin(): Boolean {
            if (newMinKhz == null) return true
            val res = RootShell.writeAndVerify(minPath, targetMin.toString())
            val ok = res is HwResult.Applied
            messages.add(if (ok) "min=${targetMin / 1000} MHz" else res.describe("min"))
            return ok
        }

        if (applyMaxFirst) {
            if (!applyMax()) allOk = false
            if (!applyMin()) allOk = false
        } else {
            if (!applyMin()) allOk = false
            if (!applyMax()) allOk = false
        }

        return allOk to "${policy.id}: ${messages.joinToString(", ").ifBlank { "No frequency changes requested" }}"
    }

    suspend fun setGovernor(policy: CpuPolicy, governor: String): Pair<Boolean, String> {
        if (!policy.id.matches(Regex("policy[0-9]+"))) return false to "Rejected: invalid policy ID"
        if (governor !in policy.governors) return false to "Rejected: $governor not in ${policy.governors.joinToString(", ")}"
        val govPath = "/sys/devices/system/cpu/cpufreq/${policy.id}/scaling_governor"
        val res = RootShell.writeAndVerify(govPath, governor)
        return (res is HwResult.Applied) to res.describe("${policy.id} governor")
    }

    suspend fun resetPolicy(policy: CpuPolicy): Pair<Boolean, String> {
        val origMin = policy.originalMinKhz
        val origMax = policy.originalMaxKhz
        val origGov = policy.originalGovernor
        val notes = mutableListOf<String>()
        var ok = true
        if (origGov != null && policy.govWritable && origGov in policy.governors) {
            val (gOk, gMsg) = setGovernor(policy, origGov)
            if (!gOk) ok = false
            notes.add(gMsg)
        }
        if (origMin != null && origMax != null && (policy.minWritable || policy.maxWritable)) {
            val (fOk, fMsg) = setCpuFrequencyRange(
                policy = policy,
                newMinKhz = if (policy.minWritable) origMin else null,
                newMaxKhz = if (policy.maxWritable) origMax else null
            )
            if (!fOk) ok = false
            notes.add(fMsg)
        }
        return ok to notes.joinToString(" | ").ifBlank { "${policy.id}: no saved baseline to restore" }
    }

    suspend fun setCoreOnline(core: CpuCore, online: Boolean): Pair<Boolean, String> {
        if (core.index <= 0) return false to "Rejected: CPU0 cannot be offlined safely"
        val path = "/sys/devices/system/cpu/cpu${core.index}/online"
        val current = RootShell.readNode(path) ?: return false to "Cannot read $path"
        val target = if (online) "1" else "0"
        if (current == target) return true to "CPU${core.index} already ${if (online) "online" else "offline"}"
        val res = RootShell.writeAndVerify(path, target)
        return (res is HwResult.Applied) to res.describe("CPU${core.index} online")
    }

    suspend fun scanGpu(forceRefresh: Boolean = true): GpuState {
        return HardwareProbe.probe(context, forceRefresh = forceRefresh).gpuState
    }

    suspend fun setGpuFrequency(gpu: GpuState, minMhz: Long?, maxMhz: Long?): Pair<Boolean, String> {
        if (!gpu.supported) return false to gpu.reason
        val targetMinMhz = minMhz ?: gpu.minMhz ?: return false to "Rejected: GPU min frequency unavailable"
        val targetMaxMhz = maxMhz ?: gpu.maxMhz ?: return false to "Rejected: GPU max frequency unavailable"
        if (targetMinMhz > targetMaxMhz) return false to "Rejected: GPU min ($targetMinMhz) > max ($targetMaxMhz)"

        val results = mutableListOf<String>()
        var allOk = true
        if (minMhz != null) {
            val minPath = gpu.minPath
            if (minPath == null) {
                allOk = false
                results.add(HwResult.NotFound(gpu.pathsTried).describe("GPU min"))
            } else {
                val res = RootShell.writeAndVerify(minPath, (minMhz * gpu.divisorToMhz).toString())
                if (res !is HwResult.Applied) allOk = false
                results.add(res.describe("GPU min"))
            }
        }
        if (maxMhz != null) {
            val maxPath = gpu.maxPath
            if (maxPath == null) {
                allOk = false
                results.add(HwResult.NotFound(gpu.pathsTried).describe("GPU max"))
            } else {
                val res = RootShell.writeAndVerify(maxPath, (maxMhz * gpu.divisorToMhz).toString())
                if (res !is HwResult.Applied) allOk = false
                results.add(res.describe("GPU max"))
            }
        }
        return allOk to results.joinToString(", ").ifBlank { "No writable GPU frequency nodes (${gpu.pathsTried.joinToString(", ")})" }
    }

    suspend fun setGpuGovernor(gpu: GpuState, governor: String): Pair<Boolean, String> {
        val path = gpu.govPath ?: return false to HwResult.NotFound(gpu.pathsTried).describe("GPU governor")
        if (governor !in gpu.availableGovernors) return false to "Rejected: $governor not in ${gpu.availableGovernors.joinToString(", ")}"
        val res = RootShell.writeAndVerify(path, governor)
        return (res is HwResult.Applied) to res.describe("GPU governor")
    }

    suspend fun resetGpu(gpu: GpuState): Pair<Boolean, String> {
        if (!gpu.supported) return false to gpu.reason
        val notes = mutableListOf<String>()
        var ok = true
        gpu.originalGovernor?.let { gov ->
            if (gov in gpu.availableGovernors) {
                val (gOk, gMsg) = setGpuGovernor(gpu, gov)
                if (!gOk) ok = false
                notes.add(gMsg)
            }
        }
        val minOrigMhz = gpu.originalMinRaw?.let { it / gpu.divisorToMhz }
        val maxOrigMhz = gpu.originalMaxRaw?.let { it / gpu.divisorToMhz }
        if (minOrigMhz != null && maxOrigMhz != null) {
            val (fOk, fMsg) = setGpuFrequency(gpu, minOrigMhz, maxOrigMhz)
            if (!fOk) ok = false
            notes.add(fMsg)
        }
        return ok to notes.joinToString(" | ").ifBlank { "No saved GPU baseline" }
    }
}
