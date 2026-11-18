package com.evorez.ezdmanager.hardware

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Display
import com.evorez.ezdmanager.model.*
import com.evorez.ezdmanager.root.HwResult
import com.evorez.ezdmanager.root.RootShell
import com.evorez.ezdmanager.root.describe
import com.evorez.ezdmanager.root.shortTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class HardwareProbeReport(
    val timestampMs: Long = System.currentTimeMillis(),
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
    val androidRelease: String = Build.VERSION.RELEASE,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val kernelVersion: String = "",
    val selinuxMode: String = "Unknown",
    val rootType: String = "None",
    val suGranted: Boolean = false,
    val cpuPolicies: List<CpuPolicy> = emptyList(),
    val cpuCores: List<CpuCore> = emptyList(),
    val cpuUsagePercent: Int? = null,
    val cpuResult: HwResult = HwResult.PermissionDenied,
    val cpuReason: String = "Waiting for hardware probe…",
    val cpuPathsTried: List<String> = emptyList(),
    val gpuState: GpuState = GpuState(),
    val gpuExistingNodes: List<String> = emptyList(),
    val gpuWritableNodes: List<String> = emptyList(),
    val zramVmState: ZramVmState = ZramVmState(),
    val zramWritableNodes: List<String> = emptyList(),
    val ioDevices: List<IoBlockDevice> = emptyList(),
    val ioResult: HwResult = HwResult.PermissionDenied,
    val ioReason: String = "Waiting for hardware probe…",
    val ioPathsTried: List<String> = emptyList(),
    val displaySupportedRatesHz: List<Float> = emptyList(),
    val displayPeakRateHz: Float = 60f,
    val displayCurrentRateHz: Float = 60f,
    val gpuType: String = "none",
    val gpuOppTable: List<Pair<Int, Long>> = emptyList(),
    val gpuFixOppPath: String? = null,
    val mtkLimiterNodes: List<String> = emptyList(),
    val touchSamplingPath: String? = null,
    val touchGameModePath: String? = null,
    val touchBoostPath: String? = null,
    val xiaomiTouchDeviceExists: Boolean = false,
    val ioctlBinaryAvailable: Boolean = false,
    val socThermalZonePath: String? = null,
    val socZoneType: String = "",
    val thermalTelemetry: ThermalTelemetry = ThermalTelemetry(),
    val fastChargeState: FastChargeState = FastChargeState()
)

/**
 * Single centralized hardware probe backed exclusively by [RootShell] for /sys and /proc
 * and Android [DisplayManager] APIs for display modes. Caches results for instant reuse
 * and refreshes on startup or pull-to-refresh.
 */
object HardwareProbe {
    private val probeMutex = Mutex()

    @Volatile
    private var cachedReport: HardwareProbeReport? = null

    private val _reportFlow = MutableStateFlow<HardwareProbeReport?>(null)
    val reportFlow: StateFlow<HardwareProbeReport?> = _reportFlow.asStateFlow()

    @Volatile
    private var prevCpuTotal: Long = 0L

    @Volatile
    private var prevCpuIdle: Long = 0L

    fun getCached(): HardwareProbeReport? = cachedReport

    suspend fun probe(context: Context, forceRefresh: Boolean = false): HardwareProbeReport {
        val existing = cachedReport
        if (!forceRefresh && existing != null) return existing
        return probeMutex.withLock {
            val recheck = cachedReport
            if (!forceRefresh && recheck != null) return@withLock recheck

            val suGranted = RootShell.isSuGranted(forceFresh = forceRefresh)
            val kernelVersion = RootShell.getKernelVersion()
            val selinuxMode = RootShell.getSelinuxMode()
            val rootType = if (suGranted) RootShell.detectRootType() else "None"

            val (cpuPolicies, cpuResult, cpuReason, cpuPathsTried) = probeCpuPolicies(context, suGranted, selinuxMode)
            val cpuCores = probeCpuCores(suGranted)
            val cpuUsage = sampleCpuUsageViaShell()
            val gpuDetail = probeGpuDetailed(context, suGranted, selinuxMode)
            val zramState = probeZramVm(suGranted, selinuxMode)
            val (ioDevices, ioResult, ioReason, ioPathsTried) = probeIoDevices(suGranted, selinuxMode)
            val (dispSupported, dispPeak, dispCurrent) = probeDisplayRates(context)
            val mtkLimiters = probeMtkLimiters(suGranted)
            val touchProbe = probeTouchNodes(suGranted)
            val (socZonePath, socZoneType, initialSocTemp) = probeSocThermalZone(suGranted, existing?.socThermalZonePath, existing?.socZoneType)
            val batTemp = com.evorez.ezdmanager.service.BatteryMonitorTracker.sampleNow(context).temperatureC
            val thermalTele = ThermalTelemetry(
                batteryTempC = batTemp,
                socTempC = initialSocTemp,
                socZonePath = socZonePath,
                socZoneType = socZoneType,
                lastUpdatedMs = System.currentTimeMillis()
            )
            val fastCharge = probeFastCharge(context, suGranted, selinuxMode)

            val fresh = HardwareProbeReport(
                timestampMs = System.currentTimeMillis(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                kernelVersion = kernelVersion,
                selinuxMode = selinuxMode,
                rootType = rootType,
                suGranted = suGranted,
                cpuPolicies = cpuPolicies,
                cpuCores = cpuCores,
                cpuUsagePercent = cpuUsage,
                cpuResult = cpuResult,
                cpuReason = cpuReason,
                cpuPathsTried = cpuPathsTried,
                gpuState = gpuDetail.gpuState,
                gpuExistingNodes = gpuDetail.gpuState.existingNodes,
                gpuWritableNodes = gpuDetail.gpuState.writableNodes,
                zramVmState = zramState,
                zramWritableNodes = zramState.writableNodes,
                ioDevices = ioDevices,
                ioResult = ioResult,
                ioReason = ioReason,
                ioPathsTried = ioPathsTried,
                displaySupportedRatesHz = dispSupported,
                displayPeakRateHz = dispPeak,
                displayCurrentRateHz = dispCurrent,
                gpuType = gpuDetail.gpuType,
                gpuOppTable = gpuDetail.oppTable,
                gpuFixOppPath = gpuDetail.fixOppPath,
                mtkLimiterNodes = mtkLimiters,
                touchSamplingPath = touchProbe.samplingPath,
                touchGameModePath = touchProbe.gameModePath,
                touchBoostPath = touchProbe.touchBoostPath,
                xiaomiTouchDeviceExists = touchProbe.xiaomiTouchExists,
                ioctlBinaryAvailable = touchProbe.ioctlAvailable,
                socThermalZonePath = socZonePath,
                socZoneType = socZoneType,
                thermalTelemetry = thermalTele,
                fastChargeState = fastCharge
            )
            cachedReport = fresh
            _reportFlow.value = fresh
            fresh
        }
    }

    suspend fun sampleCpuUsageViaShell(): Int? {
        val firstLine = RootShell.readNode("/proc/stat")?.lineSequence()?.firstOrNull() ?: return null
        if (!firstLine.startsWith("cpu ")) return null
        val parts = firstLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
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
        return (((diffTotal - diffIdle) * 100.0) / diffTotal).toInt().coerceIn(0, 100)
    }

    private suspend fun runProbeScript(suGranted: Boolean, script: String, timeoutMs: Long = 4_500L) =
        if (suGranted) RootShell.exec(script, timeoutMs) else RootShell.execLocalSh(script, timeoutMs)

    private data class CpuProbeOutcome(
        val policies: List<CpuPolicy>,
        val result: HwResult,
        val reason: String,
        val pathsTried: List<String>
    )

    private suspend fun probeCpuPolicies(
        context: Context,
        suGranted: Boolean,
        selinuxMode: String
    ): CpuProbeOutcome {
        val baseDir = "/sys/devices/system/cpu/cpufreq"
        val tried = mutableListOf(
            "$baseDir/policy*",
            "/sys/devices/system/cpu/cpu0/cpufreq"
        )
        val script = """
            read_val() {
              if [ -r "${'$'}1" ]; then cat "${'$'}1" 2>/dev/null | tr '\n|' '  '; fi
            }
            if [ ! -e "$baseDir" ]; then
              echo "STATUS|NODIR|$baseDir"
              exit 0
            fi
            found=0
            for d in $baseDir/policy[0-9]*; do
              [ -d "${'$'}d" ] || continue
              found=1
              wmax=0; [ -w "${'$'}d/scaling_max_freq" ] && wmax=1
              wmin=0; [ -w "${'$'}d/scaling_min_freq" ] && wmin=1
              wgov=0; [ -w "${'$'}d/scaling_governor" ] && wgov=1
              rmax=0; [ -r "${'$'}d/scaling_max_freq" ] && rmax=1
              cmin="$(read_val "${'$'}d/cpuinfo_min_freq")"
              cmax="$(read_val "${'$'}d/cpuinfo_max_freq")"
              printf 'P|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\n' \
                "${'$'}{d##*/}" \
                "$(read_val "${'$'}d/related_cpus")" \
                "$(read_val "${'$'}d/scaling_cur_freq")" \
                "$(read_val "${'$'}d/scaling_min_freq")" \
                "$(read_val "${'$'}d/scaling_max_freq")" \
                "$(read_val "${'$'}d/scaling_available_frequencies")" \
                "$(read_val "${'$'}d/scaling_available_governors")" \
                "$(read_val "${'$'}d/scaling_governor")" \
                "${'$'}wmax" "${'$'}wmin" "${'$'}wgov" \
                "${'$'}cmin" "${'$'}cmax" "${'$'}rmax"
            done
            if [ "${'$'}found" -eq 0 ]; then
              echo "STATUS|EMPTY|$baseDir"
            fi
        """.trimIndent()

        val res = runProbeScript(suGranted, script, timeoutMs = 4_500L)
        if (res.timedOut) {
            return CpuProbeOutcome(
                emptyList(),
                HwResult.Rejected("CPU cpufreq probe timed out"),
                "RootShell timed out reading $baseDir.",
                tried
            )
        }
        val lines = res.stdoutLines
        val baselinePrefs = context.getSharedPreferences("cpu_baselines", Context.MODE_PRIVATE)
        val policies = mutableListOf<CpuPolicy>()
        var statusTag: String? = null
        var unreadableCount = 0

        lines.forEach { line ->
            if (line.startsWith("STATUS|")) {
                statusTag = line.removePrefix("STATUS|")
                return@forEach
            }
            val p = line.split('|').map { it.trim() }
            if (p.size >= 15 && p[0] == "P" && p[1].matches(Regex("policy[0-9]+"))) {
                val policyId = p[1]
                tried.add("$baseDir/$policyId")
                val cpus = p[2]
                val cur = p[3].toLongOrNull()?.takeIf { it > 0 }
                val cmin = p[12].toLongOrNull()?.takeIf { it > 0 }
                val cmax = p[13].toLongOrNull()?.takeIf { it > 0 }
                val min = p[4].toLongOrNull()?.takeIf { it > 0 } ?: cmin
                val max = p[5].toLongOrNull()?.takeIf { it > 0 } ?: cmax
                val rmax = p[14] == "1"

                val availParsed = p[6].split(Regex("\\s+"))
                    .mapNotNull { it.toLongOrNull()?.takeIf { n -> n > 0 } }
                    .distinct()
                    .sorted()
                val availFreqs = if (availParsed.isNotEmpty()) {
                    availParsed
                } else {
                    listOfNotNull(cmin, cur, cmax, min, max).distinct().sorted()
                }
                val governors = p[7].split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
                val curGov = p[8]
                if (!rmax && min == null && max == null && curGov.isBlank()) {
                    unreadableCount++
                }

                val scopePrefix = "${Build.FINGERPRINT}:${System.getProperty("os.version")}:$policyId:$cpus"
                val maxKey = "$scopePrefix:max"
                val minKey = "$scopePrefix:min"
                val govKey = "$scopePrefix:gov"
                if (!baselinePrefs.contains(maxKey) && max != null) baselinePrefs.edit().putLong(maxKey, max).apply()
                if (!baselinePrefs.contains(minKey) && min != null) baselinePrefs.edit().putLong(minKey, min).apply()
                if (!baselinePrefs.contains(govKey) && curGov.isNotBlank()) baselinePrefs.edit().putString(govKey, curGov).apply()

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

        val sorted = policies.sortedBy { it.id.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE }
        if (sorted.isNotEmpty()) {
            val writableCount = sorted.count { it.maxWritable || it.minWritable || it.govWritable }
            val result = when {
                writableCount > 0 -> HwResult.Applied("${sorted.size} policy cluster(s), $writableCount writable")
                !suGranted -> HwResult.PermissionDenied
                selinuxMode.equals("Enforcing", true) -> HwResult.SelinuxBlocked
                else -> HwResult.Rejected("cpufreq nodes exist (${sorted.size} clusters) but are read-only")
            }
            val reason = when {
                writableCount > 0 -> "Detected ${sorted.size} cpufreq cluster(s) via $baseDir."
                !suGranted -> "Detected ${sorted.size} cpufreq cluster(s) read-only; grant Root (su) to write clocks/governors."
                else -> "Detected ${sorted.size} cpufreq cluster(s), but kernel/SELinux ($selinuxMode) marked scaling nodes read-only."
            }
            return CpuProbeOutcome(sorted, result, reason, tried.distinct())
        }

        val isPermErr = res.stderr.contains("Permission denied", true) || !suGranted
        val hwRes = when {
            statusTag?.startsWith("NODIR") == true || statusTag?.startsWith("EMPTY") == true -> {
                if (!suGranted) HwResult.PermissionDenied else HwResult.NotFound(tried.distinct())
            }
            isPermErr && selinuxMode.equals("Enforcing", true) -> HwResult.SelinuxBlocked
            !suGranted -> HwResult.PermissionDenied
            unreadableCount > 0 -> if (selinuxMode.equals("Enforcing", true)) HwResult.SelinuxBlocked else HwResult.PermissionDenied
            else -> HwResult.NotFound(tried.distinct())
        }
        val reason = when (hwRes) {
            HwResult.PermissionDenied -> "Root (su) permission not granted; unprivileged shell could not enumerate cpufreq nodes (${tried.joinToString(", ")})."
            HwResult.SelinuxBlocked -> "SELinux ($selinuxMode) blocked cpufreq node reads in $baseDir."
            is HwResult.NotFound -> "Kernel did not expose cpufreq policy* directories under ${hwRes.pathsTried.joinToString(", ")}."
            is HwResult.Rejected -> hwRes.reason
            is HwResult.Applied -> hwRes.readBackValue
        }
        return CpuProbeOutcome(emptyList(), hwRes, reason, tried.distinct())
    }

    private suspend fun probeCpuCores(suGranted: Boolean): List<CpuCore> {
        val script = """
            for c in /sys/devices/system/cpu/cpu[0-9]*; do
              [ -d "${'$'}c" ] || continue
              idx="${'$'}{c##*/cpu}"
              case "${'$'}idx" in *[!0-9]*) continue ;; esac
              on="1"; [ -r "${'$'}c/online" ] && on="$(cat "${'$'}c/online" 2>/dev/null | tr -d '\n')"
              hp=0; [ "${'$'}idx" != "0" ] && [ -w "${'$'}c/online" ] && hp=1
              cur=""
              if [ -r "${'$'}c/cpufreq/scaling_cur_freq" ]; then
                cur="$(cat "${'$'}c/cpufreq/scaling_cur_freq" 2>/dev/null | tr -d '\n')"
              fi
              printf 'C|%s|%s|%s|%s\n' "${'$'}idx" "${'$'}on" "${'$'}hp" "${'$'}cur"
            done
        """.trimIndent()
        val res = runProbeScript(suGranted, script, timeoutMs = 3_500L)
        if (!res.isSuccess && res.stdoutLines.isEmpty()) return emptyList()
        return res.stdoutLines.mapNotNull { line ->
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

    private data class GpuProbeDetail(
        val gpuState: GpuState,
        val gpuType: String,
        val oppTable: List<Pair<Int, Long>> = emptyList(),
        val fixOppPath: String? = null
    )

    private suspend fun probeGpuDetailed(
        context: Context,
        suGranted: Boolean,
        selinuxMode: String
    ): GpuProbeDetail {
        val pathsTried = listOf(
            "/sys/class/devfreq/*",
            "/proc/gpufreq/*",
            "/proc/gpufreqv2/*",
            "/sys/class/kgsl/kgsl-3d0",
            "/sys/devices/platform/*.mali/devfreq/*"
        )
        val script = """
            emit_kv() { echo "${'$'}1|${'$'}2"; }
            EXISTING=""
            WRITABLE=""
            record_dir() {
              for f in "${'$'}1"/*; do
                [ -e "${'$'}f" ] || continue
                EXISTING="${'$'}EXISTING ${'$'}f"
                [ -w "${'$'}f" ] && WRITABLE="${'$'}WRITABLE ${'$'}f"
              done
            }

            # 1. MediaTek /proc/gpufreqv2/* & /proc/gpufreq/*
            if [ -d /proc/gpufreqv2 ]; then
              record_dir /proc/gpufreqv2
              emit_kv "TYPE" "mtk_gpufreqv2"
              emit_kv "OPP" "$(cat /proc/gpufreqv2/gpu_working_opp_table 2>/dev/null | tr '\n' ';')"
              emit_kv "CUR_OPP" "$(cat /proc/gpufreqv2/gpufreq_status 2>/dev/null | tr '\n' ';')"
              emit_kv "FIX" "$(cat /proc/gpufreqv2/fix_target_opp_index 2>/dev/null)"
              emit_kv "MINP" "/proc/gpufreqv2/fix_target_opp_index"
              emit_kv "MAXP" "/proc/gpufreqv2/fix_target_opp_index"
              emit_kv "EXIST" "${'$'}EXISTING"
              emit_kv "WRITE" "${'$'}WRITABLE"
              exit 0
            fi
            if [ -d /proc/gpufreq ]; then
              record_dir /proc/gpufreq
              emit_kv "TYPE" "mtk_gpufreq"
              emit_kv "OPP" "$(cat /proc/gpufreq/gpufreq_opp_dump 2>/dev/null | tr '\n' ';')"
              emit_kv "CUR_OPP" "$(cat /proc/gpufreq/gpufreq_var_dump 2>/dev/null | tr '\n' ';')"
              emit_kv "FIX" "$(cat /proc/gpufreq/gpufreq_opp_freq 2>/dev/null)"
              emit_kv "MAXP" "/proc/gpufreq/gpufreq_opp_freq"
              emit_kv "EXIST" "${'$'}EXISTING"
              emit_kv "WRITE" "${'$'}WRITABLE"
              exit 0
            fi

            # 2. Adreno KGSL
            KGSL="/sys/class/kgsl/kgsl-3d0"
            if [ -d "${'$'}KGSL" ]; then
              record_dir "${'$'}KGSL"
              [ -d "${'$'}KGSL/devfreq" ] && record_dir "${'$'}KGSL/devfreq"
              emit_kv "TYPE" "adreno"
              emit_kv "CUR" "$(cat "${'$'}KGSL/devfreq/cur_freq" 2>/dev/null || cat "${'$'}KGSL/gpuclk" 2>/dev/null)"
              emit_kv "MIN" "$(cat "${'$'}KGSL/devfreq/min_freq" 2>/dev/null)"
              emit_kv "MAX" "$(cat "${'$'}KGSL/devfreq/max_freq" 2>/dev/null)"
              emit_kv "BUSY" "$(cat "${'$'}KGSL/gpubusy" 2>/dev/null)"
              emit_kv "AVAIL" "$(cat "${'$'}KGSL/devfreq/available_frequencies" 2>/dev/null || cat "${'$'}KGSL/gpu_available_frequencies" 2>/dev/null)"
              emit_kv "GOV" "$(cat "${'$'}KGSL/devfreq/governor" 2>/dev/null)"
              emit_kv "AGOV" "$(cat "${'$'}KGSL/devfreq/available_governors" 2>/dev/null)"
              emit_kv "MINP" "${'$'}KGSL/devfreq/min_freq"
              emit_kv "MAXP" "${'$'}KGSL/devfreq/max_freq"
              emit_kv "GOVP" "${'$'}KGSL/devfreq/governor"
              emit_kv "EXIST" "${'$'}EXISTING"
              emit_kv "WRITE" "${'$'}WRITABLE"
              exit 0
            fi

            # 3. Generic devfreq (/sys/class/devfreq/*)
            gdir=""
            for d in /sys/class/devfreq/*mali* /sys/class/devfreq/*gpu* /sys/class/devfreq/*gpufreq* /sys/class/devfreq/*.mali* /sys/devices/platform/*.mali/devfreq/* /sys/class/devfreq/*; do
              [ -d "${'$'}d" ] || continue
              EXISTING="${'$'}EXISTING ${'$'}d"
              if [ -e "${'$'}d/cur_freq" ] || [ -e "${'$'}d/available_frequencies" ]; then
                case "${'$'}d" in
                  *mali*|*gpu*|*kgsl*|*pvrsrv*) gdir="${'$'}d"; break ;;
                esac
                if [ -z "${'$'}gdir" ]; then gdir="${'$'}d"; fi
              fi
            done
            if [ -n "${'$'}gdir" ]; then
              record_dir "${'$'}gdir"
              emit_kv "TYPE" "devfreq"
              emit_kv "DIR" "${'$'}gdir"
              emit_kv "CUR" "$(cat "${'$'}gdir/cur_freq" 2>/dev/null)"
              emit_kv "MIN" "$(cat "${'$'}gdir/min_freq" 2>/dev/null)"
              emit_kv "MAX" "$(cat "${'$'}gdir/max_freq" 2>/dev/null)"
              emit_kv "BUSY" "$(cat "${'$'}gdir/load" 2>/dev/null)"
              emit_kv "AVAIL" "$(cat "${'$'}gdir/available_frequencies" 2>/dev/null)"
              emit_kv "GOV" "$(cat "${'$'}gdir/governor" 2>/dev/null)"
              emit_kv "AGOV" "$(cat "${'$'}gdir/available_governors" 2>/dev/null)"
              emit_kv "MINP" "${'$'}gdir/min_freq"
              emit_kv "MAXP" "${'$'}gdir/max_freq"
              emit_kv "GOVP" "${'$'}gdir/governor"
              emit_kv "EXIST" "${'$'}EXISTING"
              emit_kv "WRITE" "${'$'}WRITABLE"
              exit 0
            fi

            emit_kv "TYPE" "none"
            emit_kv "EXIST" "${'$'}EXISTING"
            emit_kv "WRITE" "${'$'}WRITABLE"
        """.trimIndent()

        val res = runProbeScript(suGranted, script, timeoutMs = 4_000L)
        if (res.timedOut) {
            return GpuProbeDetail(
                GpuState(
                    supported = false,
                    vendorHint = "Probe Timed Out",
                    reason = "RootShell timed out inspecting GPU nodes.",
                    probeResult = HwResult.Rejected("GPU probe timed out"),
                    pathsTried = pathsTried
                ),
                gpuType = "none"
            )
        }

        val map = res.stdoutLines.mapNotNull { line ->
            val idx = line.indexOf('|')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
        }.toMap()

        val existingNodes = map["EXIST"].orEmpty().split(Regex("\\s+")).filter { it.startsWith("/") }.distinct()
        val writableNodes = map["WRITE"].orEmpty().split(Regex("\\s+")).filter { it.startsWith("/") }.distinct()
        val type = map["TYPE"] ?: "none"

        if (type == "none") {
            val hwRes = when {
                !suGranted -> HwResult.PermissionDenied
                selinuxMode.equals("Enforcing", true) && existingNodes.isEmpty() -> HwResult.NotFound(pathsTried)
                else -> HwResult.NotFound(pathsTried)
            }
            val reason = when {
                !suGranted -> "Root (su) not granted; GPU sysfs/proc nodes require superuser access. Paths checked: ${pathsTried.joinToString(", ")}"
                else -> "No Adreno KGSL, devfreq GPU, or MediaTek gpufreq/gpufreqv2 nodes exposed. Checked: ${pathsTried.joinToString(", ")}"
            }
            return GpuProbeDetail(
                GpuState(
                    supported = false,
                    vendorHint = "GPU Nodes Absent",
                    reason = reason,
                    probeResult = hwRes,
                    pathsTried = pathsTried,
                    existingNodes = existingNodes,
                    writableNodes = writableNodes
                ),
                gpuType = "none"
            )
        }

        if (type == "mtk_gpufreq" || type == "mtk_gpufreqv2") {
            val oppText = map["OPP"].orEmpty()
            val curOppText = map["CUR_OPP"].orEmpty()
            val oppRows = mutableListOf<Pair<Int, Long>>()
            oppText.split(';').forEach { row ->
                val idxMatch = Regex("""\[\s*(\d+)\s*]""").find(row)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val fMatch = Regex("""freq\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(row)?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (idxMatch != null && fMatch != null && fMatch > 0L) {
                    val mhz = if (fMatch > 10_000L) fMatch / 1000L else fMatch
                    oppRows.add(idxMatch to mhz)
                }
            }
            val freqMatchesKhz = Regex("""freq\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
                .findAll(oppText)
                .mapNotNull { it.groupValues[1].toLongOrNull() }
                .filter { it > 0L }
                .distinct()
                .sorted()
                .toList()
            val availMhz = if (freqMatchesKhz.isNotEmpty()) {
                freqMatchesKhz.map { if (it > 10_000L) it / 1000L else it }.distinct().sorted()
            } else emptyList()
            val curFreqMatch = Regex("""freq\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(curOppText)?.groupValues?.getOrNull(1)?.toLongOrNull()
            val curMhz = curFreqMatch?.let { if (it > 10_000L) it / 1000L else it } ?: availMhz.firstOrNull()
            val isWritable = writableNodes.isNotEmpty()
            val fixPath = map["MAXP"]?.takeIf { it in existingNodes }
            val reason = when {
                isWritable -> "Detected MediaTek $type with ${writableNodes.size} writable node(s)."
                !suGranted -> "MediaTek $type detected read-only; grant Root (su) for write checks."
                else -> "MediaTek $type detected (${existingNodes.size} nodes), kernel exposes read-only OPP telemetry."
            }
            val probeRes = when {
                isWritable -> HwResult.Applied("MediaTek $type (${curMhz ?: "?"} MHz)")
                !suGranted -> HwResult.PermissionDenied
                else -> HwResult.Rejected(reason)
            }
            return GpuProbeDetail(
                gpuState = GpuState(
                    supported = existingNodes.isNotEmpty(),
                    vendorHint = if (type == "mtk_gpufreqv2") "MediaTek Mali (gpufreqv2)" else "MediaTek GPU (gpufreq)",
                    currentMhz = curMhz,
                    minMhz = availMhz.firstOrNull(),
                    maxMhz = availMhz.lastOrNull(),
                    usagePercent = null,
                    availableMhz = availMhz,
                    currentGovernor = if (isWritable) "gpufreq-opp" else "read-only",
                    availableGovernors = emptyList(),
                    minPath = map["MINP"]?.takeIf { it in writableNodes },
                    maxPath = map["MAXP"]?.takeIf { it in writableNodes },
                    govPath = null,
                    divisorToMhz = 1_000L,
                    reason = reason,
                    probeResult = probeRes,
                    pathsTried = pathsTried,
                    existingNodes = existingNodes,
                    writableNodes = writableNodes
                ),
                gpuType = type,
                oppTable = oppRows,
                fixOppPath = fixPath
            )
        }

        val curRaw = map["CUR"]?.toLongOrNull()
        val minRaw = map["MIN"]?.toLongOrNull()
        val maxRaw = map["MAX"]?.toLongOrNull()
        val busyPct = parseGpuBusy(map["BUSY"])
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

        val gpuBaselinePrefs = context.getSharedPreferences("gpu_baselines", Context.MODE_PRIVATE)
        val scope = "${Build.FINGERPRINT}:${System.getProperty("os.version")}:$type"
        if (!gpuBaselinePrefs.contains("$scope:min") && minRaw != null) gpuBaselinePrefs.edit().putLong("$scope:min", minRaw).apply()
        if (!gpuBaselinePrefs.contains("$scope:max") && maxRaw != null) gpuBaselinePrefs.edit().putLong("$scope:max", maxRaw).apply()
        val curGov = map["GOV"].orEmpty()
        if (!gpuBaselinePrefs.contains("$scope:gov") && curGov.isNotBlank()) gpuBaselinePrefs.edit().putString("$scope:gov", curGov).apply()

        val minPath = map["MINP"]?.takeIf { it.startsWith("/") && it in existingNodes }
        val maxPath = map["MAXP"]?.takeIf { it.startsWith("/") && it in existingNodes }
        val govPath = map["GOVP"]?.takeIf { it.startsWith("/") && it in existingNodes }
        val anyWritable = listOfNotNull(minPath, maxPath, govPath).any { it in writableNodes } || writableNodes.isNotEmpty()
        val dirNote = map["DIR"]?.let { " via $it" }.orEmpty()
        val reason = when {
            anyWritable -> "GPU interface active$dirNote (${writableNodes.size} writable node(s))."
            !suGranted -> "GPU interface found$dirNote, grant Root (su) to unlock writes."
            else -> "GPU interface found$dirNote, nodes are read-only (SELinux=$selinuxMode)."
        }
        val probeRes = when {
            anyWritable -> HwResult.Applied("${curRaw?.div(divisor) ?: "?"} MHz")
            !suGranted -> HwResult.PermissionDenied
            selinuxMode.equals("Enforcing", true) -> HwResult.SelinuxBlocked
            else -> HwResult.Rejected(reason)
        }

        return GpuProbeDetail(
            gpuState = GpuState(
                supported = true,
                vendorHint = if (type == "adreno") "Adreno KGSL" else "Devfreq GPU (${map["DIR"]?.substringAfterLast('/') ?: "mali"})",
                currentMhz = curRaw?.let { it / divisor },
                minMhz = minRaw?.let { it / divisor },
                maxMhz = maxRaw?.let { it / divisor },
                usagePercent = busyPct,
                availableMhz = availRaw.map { it / divisor }.distinct().sorted(),
                currentGovernor = curGov,
                availableGovernors = map["AGOV"]?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty(),
                minPath = minPath,
                maxPath = maxPath,
                govPath = govPath,
                divisorToMhz = divisor,
                originalMinRaw = gpuBaselinePrefs.getLong("$scope:min", 0L).takeIf { it > 0 },
                originalMaxRaw = gpuBaselinePrefs.getLong("$scope:max", 0L).takeIf { it > 0 },
                originalGovernor = gpuBaselinePrefs.getString("$scope:gov", null),
                reason = reason,
                probeResult = probeRes,
                pathsTried = pathsTried,
                existingNodes = existingNodes,
                writableNodes = writableNodes
            ),
            gpuType = type
        )
    }

    private fun parseGpuBusy(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val nums = raw.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (nums.size == 2 && nums[1] > 0L) return ((nums[0] * 100.0) / nums[1]).toInt().coerceIn(0, 100)
        if (nums.size == 1) return nums[0].toInt().coerceIn(0, 100)
        return null
    }

    private suspend fun probeZramVm(suGranted: Boolean, selinuxMode: String): ZramVmState {
        val pathsTried = listOf(
            "/sys/block/zram0/disksize",
            "/sys/block/zram0/comp_algorithm",
            "/sys/block/zram0/mm_stat",
            "/proc/sys/vm/swappiness",
            "/proc/sys/vm/vfs_cache_pressure",
            "/proc/sys/vm/dirty_ratio",
            "/proc/sys/vm/dirty_background_ratio"
        )
        val script = """
            EXISTING=""; WRITABLE=""
            for p in ${pathsTried.joinToString(" ")}; do
              if [ -e "${'$'}p" ]; then
                EXISTING="${'$'}EXISTING ${'$'}p"
                [ -w "${'$'}p" ] && WRITABLE="${'$'}WRITABLE ${'$'}p"
              fi
            done
            echo "SW|$(cat /proc/sys/vm/swappiness 2>/dev/null)"
            echo "VFS|$(cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null)"
            echo "DR|$(cat /proc/sys/vm/dirty_ratio 2>/dev/null)"
            echo "DBG|$(cat /proc/sys/vm/dirty_background_ratio 2>/dev/null)"
            echo "DISK|$(cat /sys/block/zram0/disksize 2>/dev/null)"
            echo "COMP|$(cat /sys/block/zram0/comp_algorithm 2>/dev/null)"
            echo "MM|$(cat /sys/block/zram0/mm_stat 2>/dev/null)"
            echo "MEM|$(cat /sys/block/zram0/mem_used_total 2>/dev/null)"
            echo "ORIG|$(cat /sys/block/zram0/orig_data_size 2>/dev/null)"
            echo "EXIST|${'$'}EXISTING"
            echo "WRITE|${'$'}WRITABLE"
        """.trimIndent()

        val res = runProbeScript(suGranted, script, timeoutMs = 3_500L)
        val map = res.stdoutLines.mapNotNull { line ->
            val idx = line.indexOf('|')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
        }.toMap()

        val existing = map["EXIST"].orEmpty().split(Regex("\\s+")).filter { it.startsWith("/") }.distinct()
        val writable = map["WRITE"].orEmpty().split(Regex("\\s+")).filter { it.startsWith("/") }.distinct()

        val swappiness = map["SW"]?.toIntOrNull() ?: 60
        val vfs = map["VFS"]?.toIntOrNull() ?: 100
        val dr = map["DR"]?.toIntOrNull() ?: 20
        val dbg = map["DBG"]?.toIntOrNull() ?: 10
        val diskBytes = map["DISK"]?.toLongOrNull()
        val compRaw = map["COMP"]?.takeIf { it.isNotBlank() }
        val mmRaw = map["MM"]?.takeIf { it.isNotBlank() }

        var origMb = (map["ORIG"]?.toLongOrNull() ?: 0L) / (1024L * 1024L)
        var usedMb = (map["MEM"]?.toLongOrNull() ?: 0L) / (1024L * 1024L)
        if (mmRaw != null) {
            val cols = mmRaw.split(Regex("\\s+"))
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
        val hwRes = when {
            !suGranted -> HwResult.PermissionDenied
            supported && writable.isNotEmpty() -> HwResult.Applied("${(diskBytes ?: 0L) / (1024L * 1024L)} MB ($activeAlgo)")
            !supported -> HwResult.NotFound(pathsTried)
            selinuxMode.equals("Enforcing", true) -> HwResult.SelinuxBlocked
            else -> HwResult.Rejected("ZRAM nodes read-only")
        }
        val note = when (hwRes) {
            is HwResult.Applied -> "ZRAM live (${writable.size} writable nodes)."
            HwResult.PermissionDenied -> "Grant Root (su) to resize /sys/block/zram0 or tune /proc/sys/vm/*."
            is HwResult.NotFound -> "No /sys/block/zram0 exposed (tried ${pathsTried.take(3).joinToString(", ")})."
            HwResult.SelinuxBlocked -> "SELinux ($selinuxMode) restricted ZRAM/VM writes."
            is HwResult.Rejected -> hwRes.reason
        }

        return ZramVmState(
            zramSupported = supported,
            diskSizeMb = (diskBytes ?: 0L) / (1024L * 1024L),
            usedMemMb = usedMb,
            origDataMb = origMb,
            currentAlgorithm = activeAlgo,
            availableAlgorithms = algos.distinct(),
            swappiness = swappiness,
            vfsCachePressure = vfs,
            dirtyRatio = dr,
            dirtyBackgroundRatio = dbg,
            statusNote = note,
            probeResult = hwRes,
            pathsTried = pathsTried,
            existingNodes = existing,
            writableNodes = writable
        )
    }

    private data class IoProbeOutcome(
        val devices: List<IoBlockDevice>,
        val result: HwResult,
        val reason: String,
        val pathsTried: List<String>
    )

    private suspend fun probeIoDevices(suGranted: Boolean, selinuxMode: String): IoProbeOutcome {
        val tried = mutableListOf("/sys/block/*/queue/scheduler", "/sys/block/*/queue/read_ahead_kb")
        val script = """
            for q in /sys/block/*/queue; do
              [ -d "${'$'}q" ] || continue
              d="${'$'}{q%/queue}"
              NAME="${'$'}{d##*/}"
              SCH=$(cat "${'$'}q/scheduler" 2>/dev/null)
              RA=$(cat "${'$'}q/read_ahead_kb" 2>/dev/null)
              WS=0; [ -w "${'$'}q/scheduler" ] && WS=1
              WR=0; [ -w "${'$'}q/read_ahead_kb" ] && WR=1
              echo "BLK|${'$'}NAME|${'$'}d|${'$'}SCH|${'$'}RA|${'$'}WS|${'$'}WR"
            done
        """.trimIndent()
        val res = runProbeScript(suGranted, script, timeoutMs = 4_000L)
        val devices = mutableListOf<IoBlockDevice>()
        var anyWritable = false

        res.stdoutLines.forEach { line ->
            val parts = line.split('|')
            if (parts.firstOrNull() == "BLK") {
                val name = parts.getOrNull(1).orEmpty()
                val path = parts.getOrNull(2).orEmpty()
                if (path.isNotBlank()) tried.add("$path/queue")
                val schRaw = parts.getOrNull(3).orEmpty()
                val ra = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: 128
                if (parts.getOrNull(5) == "1" || parts.getOrNull(6) == "1") anyWritable = true

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
                if (name.isNotBlank() && (avail.isNotEmpty() || parts.getOrNull(4)?.isNotBlank() == true)) {
                    devices.add(
                        IoBlockDevice(
                            name = name,
                            sysfsPath = path,
                            currentScheduler = activeSch,
                            availableSchedulers = avail.distinct(),
                            readAheadKb = ra
                        )
                    )
                }
            }
        }

        val distinctTried = tried.distinct()
        val hwRes = when {
            devices.isNotEmpty() && anyWritable -> HwResult.Applied("${devices.size} queue(s) detected")
            devices.isNotEmpty() && !suGranted -> HwResult.PermissionDenied
            devices.isNotEmpty() -> HwResult.Rejected("Block queues detected read-only (SELinux=$selinuxMode)")
            !suGranted -> HwResult.PermissionDenied
            else -> HwResult.NotFound(distinctTried)
        }
        val reason = when (hwRes) {
            is HwResult.Applied -> "Detected ${devices.size} block queue(s) with scheduler/read_ahead control."
            HwResult.PermissionDenied -> "Grant Root (su) to read/write /sys/block/*/queue nodes."
            is HwResult.NotFound -> "No /sys/block/*/queue scheduler nodes exposed by kernel (tried ${distinctTried.take(3).joinToString(", ")})."
            HwResult.SelinuxBlocked -> "SELinux ($selinuxMode) blocked /sys/block/*/queue access."
            is HwResult.Rejected -> hwRes.reason
        }
        return IoProbeOutcome(devices, hwRes, reason, distinctTried)
    }

    private fun probeDisplayRates(context: Context): Triple<List<Float>, Float, Float> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val current = ((display?.mode?.refreshRate ?: 60f) * 10f).roundToInt() / 10f
        val rates = display?.supportedModes
            ?.map { (it.refreshRate * 10f).roundToInt() / 10f }
            ?.distinct()
            ?.sorted()
            ?: listOf(current)
        val peak = rates.maxOrNull() ?: current
        return Triple(rates, peak, current)
    }

    private data class TouchProbeDetail(
        val samplingPath: String? = null,
        val gameModePath: String? = null,
        val touchBoostPath: String? = null,
        val xiaomiTouchExists: Boolean = false,
        val ioctlAvailable: Boolean = false
    )

    private suspend fun probeMtkLimiters(suGranted: Boolean): List<String> {
        val candidates = listOf(
            "/proc/ppm/enabled",
            "/proc/ppm/policy/ut_fix_freq_idx",
            "/proc/ppm/policy/sysboost_cluster_freqidx_limit",
            "/proc/ppm/policy/userlimit_min_cpu_freq",
            "/proc/ppm/policy/userlimit_max_cpu_freq",
            "/proc/ppm/policy_status",
            "/proc/pbm/stop",
            "/sys/module/mtk_fpsgo/parameters/fpsgo_enable",
            "/sys/kernel/fpsgo/common/fpsgo_enable",
            "/sys/kernel/ged/hal/dcs_mode",
            "/sys/devices/system/cpu/eas/enable",
            "/proc/perfmgr/boost_ctrl/eas_ctrl/boot_boost",
            "/proc/perfmgr/boost_ctrl/cpu_ctrl/boot_freq"
        )
        val script = """
            for p in ${candidates.joinToString(" ")}; do
              [ -e "${'$'}p" ] && echo "${'$'}p"
            done
        """.trimIndent()
        val res = runProbeScript(suGranted, script, timeoutMs = 2_500L)
        return res.stdoutLines.map { it.trim() }.filter { it.startsWith("/") }.distinct()
    }

    private suspend fun probeTouchNodes(suGranted: Boolean): TouchProbeDetail {
        val samplingCandidates = listOf(
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
            "/sys/class/touch/touch_dev/report_rate",
            "/proc/touchpanel/report_rate"
        )
        val gameCandidates = listOf(
            "/proc/touchpanel/game_switch_enable",
            "/sys/class/touch/touch_dev/game_mode",
            "/sys/devices/virtual/sec/tsp/cmd"
        )
        val boostCandidates = listOf(
            "/sys/module/msm_performance/parameters/touchboost",
            "/sys/power/pnpmgr/touch_boost",
            "/sys/class/input_booster/level"
        )
        val script = """
            pick() { for p in "${'$'}@"; do if [ -e "${'$'}p" ]; then echo "${'$'}p"; return; fi; done; }
            echo "SP|$(pick ${samplingCandidates.joinToString(" ")})"
            echo "GM|$(pick ${gameCandidates.joinToString(" ")})"
            echo "TB|$(pick ${boostCandidates.joinToString(" ")})"
            [ -c /dev/xiaomi-touch ] && echo "XT|1" || echo "XT|0"
            if command -v ioctl >/dev/null 2>&1; then echo "IO|1"; else echo "IO|0"; fi
        """.trimIndent()
        val res = runProbeScript(suGranted, script, timeoutMs = 2_500L)
        val map = res.stdoutLines.mapNotNull { line ->
            val idx = line.indexOf('|')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
        }.toMap()
        return TouchProbeDetail(
            samplingPath = map["SP"]?.takeIf { it.startsWith("/") },
            gameModePath = map["GM"]?.takeIf { it.startsWith("/") },
            touchBoostPath = map["TB"]?.takeIf { it.startsWith("/") },
            xiaomiTouchExists = map["XT"] == "1",
            ioctlAvailable = map["IO"] == "1"
        )
    }

    private suspend fun probeSocThermalZone(
        suGranted: Boolean,
        cachedPath: String?,
        cachedType: String?
    ): Triple<String?, String, Float?> {
        if (!cachedPath.isNullOrBlank()) {
            val raw = RootShell.readNode("$cachedPath/temp") ?: RootShell.readNode(cachedPath)
            val parsed = parseMillidegrees(raw)
            if (parsed != null) return Triple(cachedPath, cachedType.orEmpty(), parsed)
        }
        val script = """
            for z in /sys/class/thermal/thermal_zone*; do
              [ -d "${'$'}z" ] || continue
              T=$(cat "${'$'}z/type" 2>/dev/null | tr -d '\n')
              V=$(cat "${'$'}z/temp" 2>/dev/null | tr -d '\n')
              echo "Z|${'$'}z|${'$'}T|${'$'}V"
            done
        """.trimIndent()
        val res = runProbeScript(suGranted, script, timeoutMs = 3_000L)
        var bestPath: String? = null
        var bestType = ""
        var bestTemp: Float? = null
        var bestScore = -1
        val priorityKeywords = listOf(
            "mtkts_ap" to 100,
            "soc_max" to 95,
            "cpu_max" to 92,
            "soc" to 85,
            "cpu" to 80,
            "ap_ntc" to 75,
            "tsens" to 70,
            "pmic" to 40,
            "gpu" to 65
        )
        res.stdoutLines.forEach { line ->
            val parts = line.split('|')
            if (parts.size >= 4 && parts[0] == "Z") {
                val path = parts[1].trim()
                val type = parts[2].trim()
                val temp = parseMillidegrees(parts[3].trim()) ?: return@forEach
                if (temp !in 10f..125f) return@forEach
                val low = type.lowercase()
                val score = priorityKeywords.firstOrNull { low.contains(it.first) }?.second ?: 10
                if (score > bestScore) {
                    bestScore = score
                    bestPath = path
                    bestType = type
                    bestTemp = temp
                }
            }
        }
        return Triple(bestPath, bestType, bestTemp)
    }

    suspend fun probeThermalNow(context: Context): ThermalTelemetry {
        val batTemp = com.evorez.ezdmanager.service.BatteryMonitorTracker.sampleNow(context).temperatureC
        val existing = cachedReport
        val suGranted = existing?.suGranted ?: RootShell.isSuGranted(forceFresh = false)
        val (zonePath, zoneType, socTemp) = probeSocThermalZone(
            suGranted = suGranted,
            cachedPath = existing?.socThermalZonePath,
            cachedType = existing?.socZoneType
        )
        return ThermalTelemetry(
            batteryTempC = batTemp,
            socTempC = socTemp,
            socZonePath = zonePath,
            socZoneType = zoneType,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }

    suspend fun probeFastCharge(
        context: Context,
        suGranted: Boolean? = null,
        selinuxMode: String? = null
    ): FastChargeState {
        val resolvedSu = suGranted ?: cachedReport?.suGranted ?: RootShell.isSuGranted(forceFresh = false)
        val resolvedSelinux = selinuxMode ?: cachedReport?.selinuxMode ?: RootShell.getSelinuxMode()
        val batStats = com.evorez.ezdmanager.service.BatteryMonitorTracker.sampleNow(context)
        val limitCandidates = listOf(
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/battery/input_current_limit",
            "/sys/class/power_supply/battery/charge_current_limit",
            "/sys/class/power_supply/battery/siop_level",
            "/sys/class/qcom-battery/restricted_current",
            "/sys/class/power_supply/usb/input_current_limit",
            "/sys/devices/platform/charger/current_cmd",
            "/proc/mtk_battery_cmd/current_cmd"
        )
        val forceCandidates = listOf(
            "/sys/kernel/fast_charge/force_fast_charge",
            "/sys/class/power_supply/battery/fastchg_force_switch",
            "/sys/module/qpnp_charger/parameters/fast_charge_current"
        )
        val allTried = limitCandidates + forceCandidates
        val script = """
            EXIST=""; WRITE=""
            for p in ${allTried.joinToString(" ")}; do
              if [ -e "${'$'}p" ]; then
                EXIST="${'$'}EXIST ${'$'}p"
                [ -w "${'$'}p" ] && WRITE="${'$'}WRITE ${'$'}p"
              fi
            done
            CUR=$(cat /sys/class/power_supply/battery/current_now 2>/dev/null)
            VOLT=$(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)
            CTYPE=$(cat /sys/class/power_supply/battery/charge_type 2>/dev/null)
            STATUS=$(cat /sys/class/power_supply/battery/status 2>/dev/null)
            LIM_PATH=""; LIM_VAL=""
            for p in ${limitCandidates.joinToString(" ")}; do
              if [ -e "${'$'}p" ] && [ -r "${'$'}p" ]; then
                LIM_PATH="${'$'}p"
                LIM_VAL="$(cat "${'$'}p" 2>/dev/null | tr -d '\n')"
                break
              fi
            done
            FFC_PATH=""; FFC_VAL=""
            for p in ${forceCandidates.joinToString(" ")}; do
              if [ -e "${'$'}p" ] && [ -r "${'$'}p" ]; then
                FFC_PATH="${'$'}p"
                FFC_VAL="$(cat "${'$'}p" 2>/dev/null | tr -d '\n')"
                break
              fi
            done
            echo "EXIST|${'$'}EXIST"
            echo "WRITE|${'$'}WRITE"
            echo "CUR|${'$'}CUR"
            echo "VOLT|${'$'}VOLT"
            echo "CTYPE|${'$'}CTYPE"
            echo "STATUS|${'$'}STATUS"
            echo "LIMP|${'$'}LIM_PATH|${'$'}LIM_VAL"
            echo "FFCP|${'$'}FFC_PATH|${'$'}FFC_VAL"
        """.trimIndent()

        val res = runProbeScript(resolvedSu, script, timeoutMs = 3_500L)
        val map = res.stdoutLines.mapNotNull { line ->
            val idx = line.indexOf('|')
            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1).trim() else null
        }.toMap()

        val existing = map["EXIST"].orEmpty().split(Regex("\\s+")).filter { it.startsWith("/") }.distinct()
        val writable = map["WRITE"].orEmpty().split(Regex("\\s+")).filter { it.startsWith("/") }.distinct()

        val curRaw = map["CUR"]?.toIntOrNull()
        val curMa = if (curRaw != null && curRaw != 0) {
            val mag = if (kotlin.math.abs(curRaw) > 20_000) kotlin.math.abs(curRaw) / 1000 else kotlin.math.abs(curRaw)
            if (batStats.isCharging) mag else -mag
        } else batStats.currentMa

        val voltRaw = map["VOLT"]?.toIntOrNull()
        val voltMv = if (voltRaw != null && voltRaw > 0) {
            if (voltRaw > 100_000) voltRaw / 1000 else if (voltRaw < 20) voltRaw * 1000 else voltRaw
        } else batStats.voltageMv

        val watts = if (voltMv > 0) (kotlin.math.abs(curMa) * voltMv) / 1_000_000f else batStats.powerWatts

        val limpParts = map["LIMP"]?.split('|', limit = 2).orEmpty()
        val limitPath = limpParts.getOrNull(0)?.takeIf { it.startsWith("/") }
        val rawLimitVal = limpParts.getOrNull(1)?.trim()?.toIntOrNull()
        val limitMa = rawLimitVal?.let { if (it > 50_000) it / 1000 else it }

        val ffcpParts = map["FFCP"]?.split('|', limit = 2).orEmpty()
        val forcePath = ffcpParts.getOrNull(0)?.takeIf { it.startsWith("/") }
        val forceEnabled = ffcpParts.getOrNull(1)?.trim() == "1"

        val hasWritable = writable.isNotEmpty()
        val hwRes = when {
            hasWritable -> HwResult.Applied("${limitMa ?: curMa} mA")
            !resolvedSu -> HwResult.PermissionDenied
            existing.isEmpty() -> HwResult.NotFound(allTried)
            resolvedSelinux.equals("Enforcing", true) -> HwResult.SelinuxBlocked
            else -> HwResult.Rejected("Charging control nodes exist read-only (vendor PMIC locks current limit)")
        }
        val reason = when (hwRes) {
            is HwResult.Applied -> "Writable charge limit node active (${writable.first()})."
            HwResult.PermissionDenied -> "Grant Root (su) to inspect or modify kernel charging current-limit nodes."
            is HwResult.NotFound -> "No writable charging current-limit sysfs nodes exposed by this kernel/PMIC (GKI/MediaTek often moves charge policy to charger_manager)."
            HwResult.SelinuxBlocked -> "SELinux ($resolvedSelinux) blocked access to charging control nodes."
            is HwResult.Rejected -> hwRes.reason
        }

        return FastChargeState(
            currentMa = curMa,
            voltageMv = voltMv,
            powerWatts = watts,
            isCharging = batStats.isCharging,
            chargeSource = batStats.chargeSource,
            chargeType = map["CTYPE"]?.ifBlank { batStats.chargeSource } ?: batStats.chargeSource,
            activeLimitMa = limitMa,
            currentLimitPath = limitPath,
            forceFastChargePath = forcePath,
            forceFastChargeEnabled = forceEnabled,
            existingNodes = existing,
            writableNodes = writable,
            pathsTried = allTried,
            probeResult = hwRes,
            reason = reason
        )
    }

    private fun parseMillidegrees(raw: String?): Float? {
        val v = raw?.trim()?.toFloatOrNull() ?: return null
        return when {
            v > 1000f -> v / 1000f
            v > 150f -> v / 10f
            else -> v
        }
    }

    fun buildDiagnosticsText(report: HardwareProbeReport): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestampMs))
        return buildString {
            appendLine("=== EZD Manager Hardware Diagnostics ===")
            appendLine("Timestamp: $stamp")
            appendLine("Device: ${report.deviceModel}")
            appendLine("Board / Hardware: ${Build.BOARD} / ${Build.HARDWARE}")
            appendLine("Android: ${report.androidRelease} (SDK ${report.sdkInt})")
            appendLine("Kernel (uname -r): ${report.kernelVersion}")
            appendLine("SELinux (getenforce): ${report.selinuxMode}")
            appendLine("Root Granted: ${report.suGranted} | Root Type: ${report.rootType}")
            appendLine()
            appendLine("[CPU Probe] Result=${report.cpuResult.shortTag()}")
            appendLine("Reason: ${report.cpuReason}")
            appendLine("Paths Tried: ${report.cpuPathsTried.joinToString(", ")}")
            appendLine("Clusters (${report.cpuPolicies.size}):")
            report.cpuPolicies.forEach { p ->
                appendLine(
                    "  ${p.id}: cpus=[${p.cpus}] cur=${p.currentKhz ?: "?"}kHz min=${p.minKhz ?: "?"}kHz max=${p.maxKhz ?: "?"}kHz " +
                        "gov=${p.currentGovernor.ifBlank { "?" }} availGov=${p.governors.joinToString(",")} " +
                        "availFreqCount=${p.availableFreqsKhz.size} writable(min=${p.minWritable},max=${p.maxWritable},gov=${p.govWritable})"
                )
            }
            appendLine("CPU Cores (${report.cpuCores.size}): online=${report.cpuCores.count { it.online }}/${report.cpuCores.size}")
            appendLine()
            appendLine("[GPU Probe] Result=${report.gpuState.probeResult?.shortTag() ?: "Unknown"} | Supported=${report.gpuState.supported}")
            appendLine("Vendor/Interface: ${report.gpuState.vendorHint}")
            appendLine("Reason: ${report.gpuState.reason}")
            appendLine("Cur=${report.gpuState.currentMhz ?: "?"}MHz Range=${report.gpuState.minMhz ?: "?"}-${report.gpuState.maxMhz ?: "?"}MHz Gov=${report.gpuState.currentGovernor}")
            appendLine("Paths Tried: ${report.gpuState.pathsTried.joinToString(", ")}")
            appendLine("Existing Nodes (${report.gpuExistingNodes.size}): ${report.gpuExistingNodes.joinToString(", ")}")
            appendLine("Writable Nodes (${report.gpuWritableNodes.size}): ${report.gpuWritableNodes.joinToString(", ")}")
            appendLine()
            appendLine("[ZRAM & VM Probe] Result=${report.zramVmState.probeResult?.shortTag() ?: "Unknown"}")
            appendLine("Reason: ${report.zramVmState.statusNote}")
            appendLine("ZRAM Disk=${report.zramVmState.diskSizeMb}MB Used=${report.zramVmState.usedMemMb}MB Algo=${report.zramVmState.currentAlgorithm} Avail=${report.zramVmState.availableAlgorithms.joinToString(",")}")
            appendLine("VM: swappiness=${report.zramVmState.swappiness} vfs_cache_pressure=${report.zramVmState.vfsCachePressure} dirty_ratio=${report.zramVmState.dirtyRatio} dirty_bg=${report.zramVmState.dirtyBackgroundRatio}")
            appendLine("Writable Nodes (${report.zramWritableNodes.size}): ${report.zramWritableNodes.joinToString(", ")}")
            appendLine()
            appendLine("[I/O Probe] Result=${report.ioResult.shortTag()}")
            appendLine("Reason: ${report.ioReason}")
            appendLine("Paths Tried: ${report.ioPathsTried.joinToString(", ")}")
            report.ioDevices.forEach { d ->
                appendLine("  ${d.name} (${d.sysfsPath}): scheduler=${d.currentScheduler} avail=${d.availableSchedulers.joinToString(",")} read_ahead_kb=${d.readAheadKb}")
            }
            appendLine()
            appendLine("[Display]")
            appendLine("Current Hz: ${report.displayCurrentRateHz} | Peak Hz: ${report.displayPeakRateHz}")
            appendLine("Supported Rates: ${report.displaySupportedRatesHz.joinToString(", ") { "${it}Hz" }}")
        }
    }

    /**
     * Exports diagnostics text to `Download/ezd-diag.txt` via MediaStore (with File + RootShell fallback).
     * Returns Pair(success, savedPathOrError).
     */
    suspend fun exportDiagnosticsToDownloads(
        context: Context,
        report: HardwareProbeReport
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val text = buildDiagnosticsText(report)
        val bytes = text.toByteArray(Charsets.UTF_8)

        // 1. Scoped Storage MediaStore.Downloads (works on Android 10-14+ without storage permissions)
        val mediaResult = runCatching {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val args = arrayOf("ezd-diag.txt", "${Environment.DIRECTORY_DOWNLOADS}%")
            var existingUri: Uri? = null
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    existingUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
            val targetUri = existingUri ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "ezd-diag.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            ) ?: error("MediaStore insert returned null")

            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: error("Cannot open MediaStore output stream")
            "/storage/emulated/0/Download/ezd-diag.txt"
        }
        if (mediaResult.isSuccess) {
            return@withContext true to mediaResult.getOrThrow()
        }

        // 2. Direct File fallback
        val directFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ezd-diag.txt")
        val directOk = runCatching {
            directFile.parentFile?.mkdirs()
            directFile.writeBytes(bytes)
            directFile.absolutePath
        }
        if (directOk.isSuccess) {
            return@withContext true to directOk.getOrThrow()
        }

        // 3. RootShell fallback via temp file
        val tmpFile = File(context.cacheDir, "ezd-diag.txt")
        val tmpOk = runCatching { tmpFile.writeBytes(bytes) }.isSuccess
        if (tmpOk && RootShell.isSuGranted()) {
            val qSrc = RootShell.shellQuote(tmpFile.absolutePath)
            val qDst = RootShell.shellQuote("/storage/emulated/0/Download/ezd-diag.txt")
            val cpRes = RootShell.exec("mkdir -p /storage/emulated/0/Download && cp $qSrc $qDst && chmod 644 $qDst")
            runCatching { tmpFile.delete() }
            if (cpRes.isSuccess) {
                return@withContext true to "/storage/emulated/0/Download/ezd-diag.txt"
            }
        }

        false to (mediaResult.exceptionOrNull()?.message ?: "Failed writing Download/ezd-diag.txt")
    }
}
