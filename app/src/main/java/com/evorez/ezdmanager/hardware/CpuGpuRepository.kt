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
import com.evorez.ezdmanager.root.RootShellManager

class CpuGpuRepository(private val context: Context) {

    private val baselinePrefs = context.getSharedPreferences("cpu_baselines", Context.MODE_PRIVATE)
    private val gpuBaselinePrefs = context.getSharedPreferences("gpu_baselines", Context.MODE_PRIVATE)

    @Volatile
    private var prevCpuTotal: Long = 0L

    @Volatile
    private var prevCpuIdle: Long = 0L

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
            kernelVersion = System.getProperty("os.version") ?: "Unknown",
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            batteryPercent = pct,
            chargingStatus = chargingLabel,
            cpuUsagePercent = readCpuUsagePercentLocal()
        )
    }

    private fun readCpuUsagePercentLocal(): Int? {
        return runCatching {
            val line = java.io.File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
            if (!line.startsWith("cpu ")) return null
            val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return null
            val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
            val total = parts.sum()
            val prevT = prevCpuTotal
            val prevI = prevCpuIdle
            prevCpuTotal = total
            prevCpuIdle = idle
            if (prevT == 0L || total <= prevT) return null
            val diffTotal = total - prevT
            val diffIdle = (idle - prevI).coerceAtLeast(0L)
            (((diffTotal - diffIdle) * 100.0) / diffTotal).toInt().coerceIn(0, 100)
        }.getOrNull()
    }

    suspend fun scanCpuPolicies(): List<CpuPolicy> {
        val safeScript = """
            read_node() { if [ -r "${'$'}1" ]; then cat "${'$'}1" 2>/dev/null | tr '\n|' '  '; fi; }
            for d in /sys/devices/system/cpu/cpufreq/policy[0-9]*; do
              [ -d "${'$'}d" ] || continue
              wmax=0; [ -w "${'$'}d/scaling_max_freq" ] && wmax=1
              wmin=0; [ -w "${'$'}d/scaling_min_freq" ] && wmin=1
              wgov=0; [ -w "${'$'}d/scaling_governor" ] && wgov=1
              printf 'P|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\n' \
                "${'$'}{d##*/}" \
                "${'$'}(read_node "${'$'}d/related_cpus")" \
                "${'$'}(read_node "${'$'}d/scaling_cur_freq")" \
                "${'$'}(read_node "${'$'}d/scaling_min_freq")" \
                "${'$'}(read_node "${'$'}d/scaling_max_freq")" \
                "${'$'}(read_node "${'$'}d/scaling_available_frequencies")" \
                "${'$'}(read_node "${'$'}d/scaling_available_governors")" \
                "${'$'}(read_node "${'$'}d/scaling_governor")" \
                "${'$'}wmax" "${'$'}wmin" "${'$'}wgov"
            done
        """.trimIndent()

        val res = RootShellManager.exec(safeScript, timeoutMs = 4_000L)
        if (!res.success) return emptyList()

        val policies = mutableListOf<CpuPolicy>()
        res.stdout.forEach { line ->
            val p = line.split('|').map { it.trim() }
            if (p.size == 12 && p[0] == "P" && p[1].matches(Regex("policy[0-9]+"))) {
                val policyId = p[1]
                val cpus = p[2]
                val cur = p[3].toLongOrNull()?.takeIf { it > 0 }
                val min = p[4].toLongOrNull()?.takeIf { it > 0 }
                val max = p[5].toLongOrNull()?.takeIf { it > 0 }
                val availFreqs = p[6].split(Regex("\\s+"))
                    .mapNotNull { it.toLongOrNull()?.takeIf { n -> n > 0 } }
                    .distinct()
                    .sorted()
                val governors = p[7].split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
                val curGov = p[8]

                val scopePrefix = "${Build.FINGERPRINT}:${System.getProperty("os.version")}:$policyId:$cpus"
                val maxKey = "$scopePrefix:max"
                val minKey = "$scopePrefix:min"
                val govKey = "$scopePrefix:gov"

                if (!baselinePrefs.contains(maxKey) && max != null) {
                    baselinePrefs.edit().putLong(maxKey, max).commit()
                }
                if (!baselinePrefs.contains(minKey) && min != null) {
                    baselinePrefs.edit().putLong(minKey, min).commit()
                }
                if (!baselinePrefs.contains(govKey) && curGov.isNotBlank()) {
                    baselinePrefs.edit().putString(govKey, curGov).commit()
                }

                policies.add(
                    CpuPolicy(
                        id = policyId,
                        baselineKey = scopePrefix,
                        cpus = cpus,
                        currentKhz = cur,
                        minKhz = min,
                        maxKhz = max,
                        availableFreqsKhz = availFreqs,
                        governors = governors,
                        currentGovernor = curGov,
                        maxWritable = p[9] == "1",
                        minWritable = p[10] == "1",
                        govWritable = p[11] == "1",
                        originalMinKhz = baselinePrefs.getLong(minKey, 0L).takeIf { it > 0 },
                        originalMaxKhz = baselinePrefs.getLong(maxKey, 0L).takeIf { it > 0 },
                        originalGovernor = baselinePrefs.getString(govKey, null)?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        return policies.sortedBy { it.id.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }
    }

    suspend fun scanCpuCores(): List<CpuCore> {
        val script = """
            for c in /sys/devices/system/cpu/cpu[0-9]*; do
              [ -d "${'$'}c" ] || continue
              idx="${'$'}{c##*/cpu}"
              case "${'$'}idx" in *[!0-9]*) continue ;; esac
              on="1"; [ -r "${'$'}c/online" ] && on="${'$'}(cat "${'$'}c/online" 2>/dev/null | tr -d '\n')"
              hp=0; [ "${'$'}idx" != "0" ] && [ -w "${'$'}c/online" ] && hp=1
              cur=""
              if [ -r "${'$'}c/cpufreq/scaling_cur_freq" ]; then
                cur="${'$'}(cat "${'$'}c/cpufreq/scaling_cur_freq" 2>/dev/null | tr -d '\n')"
              fi
              printf 'C|%s|%s|%s|%s\n' "${'$'}idx" "${'$'}on" "${'$'}hp" "${'$'}cur"
            done
        """.trimIndent()
        val res = RootShellManager.exec(script, timeoutMs = 3_500L)
        if (!res.success) return emptyList()
        return res.stdout.mapNotNull { line ->
            val p = line.split('|').map { it.trim() }
            if (p.size == 5 && p[0] == "C") {
                val idx = p[1].toIntOrNull() ?: return@mapNotNull null
                CpuCore(
                    index = idx,
                    online = p[2] != "0",
                    hotplugSupported = p[3] == "1",
                    currentKhz = p[4].toLongOrNull()?.takeIf { it > 0 }
                )
            } else null
        }.sortedBy { it.index }
    }

    suspend fun setCpuFrequencyRange(
        policy: CpuPolicy,
        newMinKhz: Long?,
        newMaxKhz: Long?
    ): Pair<Boolean, String> {
        if (!policy.id.matches(Regex("policy[0-9]+"))) return false to "Invalid policy ID"
        val targetMin = newMinKhz ?: policy.minKhz
        val targetMax = newMaxKhz ?: policy.maxKhz
        if (targetMin == null || targetMax == null || targetMin <= 0 || targetMax <= 0) {
            return false to "Frequency bounds unavailable"
        }
        if (targetMin > targetMax) {
            return false to "Minimum frequency cannot exceed maximum frequency"
        }
        if (policy.availableFreqsKhz.isNotEmpty() &&
            (targetMin !in policy.availableFreqsKhz || targetMax !in policy.availableFreqsKhz)
        ) {
            return false to "Requested frequency is not in kernel's supported list"
        }

        val minPath = "/sys/devices/system/cpu/cpufreq/${policy.id}/scaling_min_freq"
        val maxPath = "/sys/devices/system/cpu/cpufreq/${policy.id}/scaling_max_freq"

        val messages = mutableListOf<String>()
        var allOk = true

        val currentMax = policy.maxKhz ?: targetMax
        val applyMaxFirst = targetMax >= currentMax

        suspend fun applyMax(): Boolean {
            if (newMaxKhz == null || !policy.maxWritable) return true
            val (ok, info) = RootShellManager.writeAndVerify(maxPath, targetMax.toString())
            if (ok) messages.add("max=${targetMax / 1000} MHz")
            else messages.add("max error: ${info ?: "failed"}")
            return ok
        }

        suspend fun applyMin(): Boolean {
            if (newMinKhz == null || !policy.minWritable) return true
            val (ok, info) = RootShellManager.writeAndVerify(minPath, targetMin.toString())
            if (ok) messages.add("min=${targetMin / 1000} MHz")
            else messages.add("min error: ${info ?: "failed"}")
            return ok
        }

        if (applyMaxFirst) {
            if (!applyMax()) allOk = false
            if (!applyMin()) allOk = false
        } else {
            if (!applyMin()) allOk = false
            if (!applyMax()) allOk = false
        }

        return allOk to "${policy.id}: ${messages.joinToString(", ").ifBlank { "No writable changes" }}"
    }

    suspend fun setGovernor(policy: CpuPolicy, governor: String): Pair<Boolean, String> {
        if (!policy.id.matches(Regex("policy[0-9]+"))) return false to "Invalid policy ID"
        if (!policy.govWritable) return false to "${policy.id} governor is read-only"
        if (governor !in policy.governors) return false to "Unsupported governor: $governor"
        val govPath = "/sys/devices/system/cpu/cpufreq/${policy.id}/scaling_governor"
        val (ok, info) = RootShellManager.writeAndVerify(govPath, governor)
        return if (ok) true to "${policy.id} governor verified: $info"
        else false to "${policy.id} governor failed: ${info ?: "rejected"}"
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
        if (core.index <= 0) return false to "CPU0 cannot be offlined safely"
        if (!core.hotplugSupported) return false to "CPU${core.index} hotplugging not supported"
        val path = "/sys/devices/system/cpu/cpu${core.index}/online"
        val current = RootShellManager.readSysfs(path) ?: return false to "Cannot read CPU${core.index} state"
        val target = if (online) "1" else "0"
        if (current == target) return true to "CPU${core.index} already ${if (online) "online" else "offline"}"
        val (ok, info) = RootShellManager.writeAndVerify(path, target)
        return if (ok) true to "CPU${core.index} is now ${if (online) "online" else "offline"}"
        else false to "CPU${core.index} toggle failed: ${info ?: "rejected"}"
    }

    suspend fun scanGpu(): GpuState {
        val kgslBase = "/sys/class/kgsl/kgsl-3d0"
        val probeScript = """
            if [ -d "$kgslBase" ]; then
              echo "TYPE|adreno"
              echo "CUR|$(cat $kgslBase/devfreq/cur_freq 2>/dev/null || cat $kgslBase/gpuclk 2>/dev/null)"
              echo "MIN|$(cat $kgslBase/devfreq/min_freq 2>/dev/null)"
              echo "MAX|$(cat $kgslBase/devfreq/max_freq 2>/dev/null)"
              echo "BUSY|$(cat $kgslBase/gpubusy 2>/dev/null)"
              echo "AVAIL|$(cat $kgslBase/devfreq/available_frequencies 2>/dev/null || cat $kgslBase/gpu_available_frequencies 2>/dev/null)"
              echo "GOV|$(cat $kgslBase/devfreq/governor 2>/dev/null)"
              echo "AGOV|$(cat $kgslBase/devfreq/available_governors 2>/dev/null)"
              echo "MINP|$kgslBase/devfreq/min_freq"
              echo "MAXP|$kgslBase/devfreq/max_freq"
              echo "GOVP|$kgslBase/devfreq/governor"
            else
              gdir=""
              for d in /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali* /sys/class/devfreq/*kgsl* /sys/devices/platform/*.mali/devfreq/*; do
                if [ -d "${'$'}d" ] && [ -r "${'$'}d/cur_freq" ]; then gdir="${'$'}d"; break; fi
              done
              if [ -n "${'$'}gdir" ]; then
                echo "TYPE|devfreq"
                echo "CUR|$(cat "${'$'}gdir/cur_freq" 2>/dev/null)"
                echo "MIN|$(cat "${'$'}gdir/min_freq" 2>/dev/null)"
                echo "MAX|$(cat "${'$'}gdir/max_freq" 2>/dev/null)"
                echo "BUSY|$(cat "${'$'}gdir/load" 2>/dev/null)"
                echo "AVAIL|$(cat "${'$'}gdir/available_frequencies" 2>/dev/null)"
                echo "GOV|$(cat "${'$'}gdir/governor" 2>/dev/null)"
                echo "AGOV|$(cat "${'$'}gdir/available_governors" 2>/dev/null)"
                echo "MINP|${'$'}gdir/min_freq"
                echo "MAXP|${'$'}gdir/max_freq"
                echo "GOVP|${'$'}gdir/governor"
              else
                echo "TYPE|none"
              fi
            fi
        """.trimIndent()

        val res = RootShellManager.exec(probeScript, timeoutMs = 3_500L)
        if (!res.success) {
            return GpuState(
                supported = false,
                reason = "Root access is required to inspect GPU sysfs nodes."
            )
        }
        val map = res.stdout.mapNotNull { line ->
            val idx = line.indexOf('|')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
        }.toMap()

        val type = map["TYPE"] ?: "none"
        if (type == "none") {
            return GpuState(
                supported = false,
                reason = "Not supported on this device — no Adreno KGSL or Mali devfreq GPU sysfs interface was exposed by the kernel."
            )
        }

        val curRaw = map["CUR"]?.toLongOrNull()
        val minRaw = map["MIN"]?.toLongOrNull()
        val maxRaw = map["MAX"]?.toLongOrNull()
        val busyPct = parseGpuBusyPercent(map["BUSY"])
        val availRaw = map["AVAIL"]?.split(Regex("\\s+"))
            ?.mapNotNull { it.toLongOrNull()?.takeIf { v -> v > 0 } }
            ?.distinct()
            ?.sorted()
            .orEmpty()

        val sample = listOfNotNull(curRaw, maxRaw, availRaw.lastOrNull()).firstOrNull() ?: 0L
        val divisor = when {
            sample >= 10_000_000L -> 1_000_000L
            sample >= 10_000L -> 1_000L
            else -> 1L
        }

        val scope = "${Build.FINGERPRINT}:${System.getProperty("os.version")}:$type"
        if (!gpuBaselinePrefs.contains("$scope:min") && minRaw != null) {
            gpuBaselinePrefs.edit().putLong("$scope:min", minRaw).commit()
        }
        if (!gpuBaselinePrefs.contains("$scope:max") && maxRaw != null) {
            gpuBaselinePrefs.edit().putLong("$scope:max", maxRaw).commit()
        }
        val curGov = map["GOV"].orEmpty()
        if (!gpuBaselinePrefs.contains("$scope:gov") && curGov.isNotBlank()) {
            gpuBaselinePrefs.edit().putString("$scope:gov", curGov).commit()
        }

        return GpuState(
            supported = true,
            vendorHint = if (type == "adreno") "Adreno KGSL" else "Mali / Devfreq GPU",
            currentMhz = curRaw?.let { it / divisor },
            minMhz = minRaw?.let { it / divisor },
            maxMhz = maxRaw?.let { it / divisor },
            usagePercent = busyPct,
            availableMhz = availRaw.map { it / divisor }.distinct().sorted(),
            currentGovernor = curGov,
            availableGovernors = map["AGOV"]?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty(),
            minPath = map["MINP"]?.takeIf { it.startsWith("/") },
            maxPath = map["MAXP"]?.takeIf { it.startsWith("/") },
            govPath = map["GOVP"]?.takeIf { it.startsWith("/") },
            divisorToMhz = divisor,
            originalMinRaw = gpuBaselinePrefs.getLong("$scope:min", 0L).takeIf { it > 0 },
            originalMaxRaw = gpuBaselinePrefs.getLong("$scope:max", 0L).takeIf { it > 0 },
            originalGovernor = gpuBaselinePrefs.getString("$scope:gov", null),
            reason = ""
        )
    }

    private fun parseGpuBusyPercent(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val nums = raw.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (nums.size == 2 && nums[1] > 0L) {
            return ((nums[0] * 100.0) / nums[1]).toInt().coerceIn(0, 100)
        }
        if (nums.size == 1) return nums[0].toInt().coerceIn(0, 100)
        return null
    }

    suspend fun setGpuFrequency(gpu: GpuState, minMhz: Long?, maxMhz: Long?): Pair<Boolean, String> {
        if (!gpu.supported) return false to gpu.reason
        val targetMinMhz = minMhz ?: gpu.minMhz ?: return false to "GPU min unavailable"
        val targetMaxMhz = maxMhz ?: gpu.maxMhz ?: return false to "GPU max unavailable"
        if (targetMinMhz > targetMaxMhz) return false to "GPU minimum cannot exceed maximum"

        val results = mutableListOf<String>()
        var allOk = true
        if (minMhz != null && gpu.minPath != null) {
            val raw = minMhz * gpu.divisorToMhz
            val (ok, msg) = RootShellManager.writeAndVerify(gpu.minPath, raw.toString())
            if (!ok) allOk = false
            results.add(if (ok) "GPU min=${minMhz} MHz" else "GPU min failed: $msg")
        }
        if (maxMhz != null && gpu.maxPath != null) {
            val raw = maxMhz * gpu.divisorToMhz
            val (ok, msg) = RootShellManager.writeAndVerify(gpu.maxPath, raw.toString())
            if (!ok) allOk = false
            results.add(if (ok) "GPU max=${maxMhz} MHz" else "GPU max failed: $msg")
        }
        return allOk to results.joinToString(", ").ifBlank { "No writable GPU frequency nodes" }
    }

    suspend fun setGpuGovernor(gpu: GpuState, governor: String): Pair<Boolean, String> {
        val path = gpu.govPath ?: return false to "GPU governor node unavailable"
        if (governor !in gpu.availableGovernors) return false to "Unsupported GPU governor"
        val (ok, msg) = RootShellManager.writeAndVerify(path, governor)
        return if (ok) true to "GPU governor set to $governor"
        else false to "GPU governor failed: ${msg ?: "rejected"}"
    }

    suspend fun resetGpu(gpu: GpuState): Pair<Boolean, String> {
        if (!gpu.supported) return false to "GPU controls unsupported"
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
