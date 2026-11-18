package com.evorez.ezdmanager.profiles

import android.content.Context
import com.evorez.ezdmanager.hardware.CpuGpuRepository
import com.evorez.ezdmanager.hardware.DisplayTouchRepository
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.GameProfileId
import com.evorez.ezdmanager.model.ProfileConfig
import com.evorez.ezdmanager.root.RootShellManager
import com.evorez.ezdmanager.root.ShizukuManager

class ProfileRepository(
    private val context: Context,
    private val cpuGpuRepo: CpuGpuRepository,
    private val displayTouchRepo: DisplayTouchRepository,
    private val sysToolsRepo: SystemToolsRepository
) {
    companion object {
        fun builtInProfiles(): List<ProfileConfig> = listOf(
            ProfileConfig(
                id = GameProfileId.BEAST.id,
                name = "Beast Mode",
                description = "Locks 100% CPU & GPU, forces all cores online, unthrottles governor & pushes kernel limits",
                isBuiltIn = true,
                cpuGovernor = "performance",
                cpuMaxScalePercent = 100,
                cpuMinScalePercent = 100,
                gpuGovernor = "performance",
                gpuMaxPercent = 100,
                targetRefreshHz = 120f,
                touchBoost = true,
                isBeastMode = true
            ),
            ProfileConfig(
                id = GameProfileId.POWERSAVE.id,
                name = "Power Save",
                description = "GPU 10% · CPU 30% battery-first low clock ceiling & 60Hz limit",
                isBuiltIn = true,
                cpuGovernor = "powersave",
                cpuMaxScalePercent = 30,
                cpuMinScalePercent = 5,
                gpuGovernor = "powersave",
                gpuMaxPercent = 10,
                targetRefreshHz = 60f,
                touchBoost = false
            ),
            ProfileConfig(
                id = GameProfileId.BALANCED.id,
                name = "Balanced Mode",
                description = "GPU 30% · CPU 50% optimal battery and smooth operation",
                isBuiltIn = true,
                cpuGovernor = "schedutil",
                cpuMaxScalePercent = 50,
                cpuMinScalePercent = 20,
                gpuGovernor = "msm-adreno-tz",
                gpuMaxPercent = 30,
                targetRefreshHz = null,
                touchBoost = false
            ),
            ProfileConfig(
                id = GameProfileId.PERFORMANCE.id,
                name = "Performance Mode",
                description = "GPU 70% · CPU 70% boost with 120Hz refresh & responsiveness",
                isBuiltIn = true,
                cpuGovernor = "performance",
                cpuMaxScalePercent = 70,
                cpuMinScalePercent = 40,
                gpuGovernor = "performance",
                gpuMaxPercent = 70,
                targetRefreshHz = 120f,
                touchBoost = true
            )
        )

        fun forGameProfile(id: GameProfileId, customProfiles: List<ProfileConfig>): ProfileConfig {
            return when (id) {
                GameProfileId.CUSTOM -> customProfiles.firstOrNull()
                    ?: builtInProfiles().first { it.id == GameProfileId.PERFORMANCE.id }
                else -> builtInProfiles().find { it.id == id.id } ?: builtInProfiles().first()
            }
        }
    }

    suspend fun applyProfile(
        profile: ProfileConfig,
        rootAvailable: Boolean = true,
        shizukuAvailable: Boolean = false
    ): Pair<Boolean, String> {
        val notes = mutableListOf<String>()
        var anyApplied = false

        if (rootAvailable) {
            if (profile.isBeastMode || profile.id == GameProfileId.BEAST.id) {
                // BEAST MODE: Push kernel limits, overclock/uncap, force all CPU cores online
                RootShellManager.exec("""
                    for cpu in /sys/devices/system/cpu/cpu[0-9]*; do
                        [ -w "${'$'}cpu/online" ] && echo 1 > "${'$'}cpu/online" 2>/dev/null
                    done
                    setprop debug.sf.disable_backpressure 1 2>/dev/null
                    if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
                        echo 1 > /sys/class/kgsl/kgsl-3d0/force_bus_on 2>/dev/null
                        echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on 2>/dev/null
                        echo 1 > /sys/class/kgsl/kgsl-3d0/force_rail_on 2>/dev/null
                    fi
                """.trimIndent())
                notes.add("All Cores Online")
                notes.add("Kernel Limits Uncapped")
            }

            val policies = cpuGpuRepo.scanCpuPolicies()
            policies.forEach { policy ->
                val targetGov = when {
                    profile.isBeastMode || profile.id == GameProfileId.BEAST.id -> {
                        if ("performance" in policy.governors) "performance" else policy.governors.firstOrNull()
                    }
                    profile.cpuGovernor in policy.governors -> profile.cpuGovernor
                    profile.id == GameProfileId.POWERSAVE.id && "conservative" in policy.governors -> "conservative"
                    "schedutil" in policy.governors -> "schedutil"
                    else -> policy.governors.firstOrNull()
                }
                if (targetGov != null && policy.govWritable) {
                    val (gOk, _) = cpuGpuRepo.setGovernor(policy, targetGov)
                    if (gOk) anyApplied = true
                }
                if (policy.availableFreqsKhz.isNotEmpty() && (policy.minWritable || policy.maxWritable)) {
                    val freqs = policy.availableFreqsKhz
                    val maxIdx = ((freqs.lastIndex * profile.cpuMaxScalePercent) / 100).coerceIn(0, freqs.lastIndex)
                    val minIdx = ((freqs.lastIndex * profile.cpuMinScalePercent) / 100).coerceIn(0, maxIdx)
                    val (fOk, _) = cpuGpuRepo.setCpuFrequencyRange(
                        policy = policy,
                        newMinKhz = if (policy.minWritable) freqs[minIdx] else null,
                        newMaxKhz = if (policy.maxWritable) freqs[maxIdx] else null
                    )
                    if (fOk) anyApplied = true
                }
            }
            if (policies.isNotEmpty()) {
                if (profile.isBeastMode) notes.add("CPU Locked 100% (${policies.size} clusters)")
                else notes.add("CPU (${profile.cpuMaxScalePercent}%)")
            }

            val gpu = cpuGpuRepo.scanGpu()
            if (gpu.supported) {
                val gpuGov = when {
                    profile.isBeastMode || profile.id == GameProfileId.BEAST.id -> {
                        if ("performance" in gpu.availableGovernors) "performance"
                        else gpu.availableGovernors.firstOrNull() ?: profile.gpuGovernor
                    }
                    profile.gpuGovernor in gpu.availableGovernors -> profile.gpuGovernor
                    profile.id == GameProfileId.POWERSAVE.id && "powersave" in gpu.availableGovernors -> "powersave"
                    "msm-adreno-tz" in gpu.availableGovernors -> "msm-adreno-tz"
                    else -> null
                }
                if (gpuGov != null && gpuGov.isNotBlank()) {
                    val (gOk, _) = cpuGpuRepo.setGpuGovernor(gpu, gpuGov)
                    if (gOk) {
                        anyApplied = true
                        notes.add("GPU Gov ($gpuGov)")
                    }
                }

                if (gpu.availableMhz.isNotEmpty() && (gpu.minPath != null || gpu.maxPath != null)) {
                    val freqs = gpu.availableMhz
                    val maxIdx = ((freqs.lastIndex * profile.gpuMaxPercent) / 100).coerceIn(0, freqs.lastIndex)
                    val minIdx = if (profile.isBeastMode) maxIdx else 0
                    val (fOk, _) = cpuGpuRepo.setGpuFrequency(
                        gpu = gpu,
                        minMhz = if (profile.isBeastMode && gpu.minPath != null) freqs[minIdx] else null,
                        maxMhz = if (gpu.maxPath != null) freqs[maxIdx] else null
                    )
                    if (fOk) {
                        anyApplied = true
                        if (profile.isBeastMode) notes.add("GPU Locked 100% (${freqs[maxIdx]} MHz)")
                        else notes.add("GPU Max (${freqs[maxIdx]} MHz)")
                    }
                }
            }
        }

        val canPrivilegedDisplay = rootAvailable || shizukuAvailable
        val display = displayTouchRepo.readDisplayState(rootGranted = canPrivilegedDisplay)
        val requestedHz = when {
            profile.targetRefreshHz != null -> profile.targetRefreshHz
            profile.id == GameProfileId.BALANCED.id -> null
            else -> null
        }
        if (requestedHz != null) {
            val supportedMatch = display.supportedModes
                .map { it.refreshRateHz }
                .minByOrNull { kotlin.math.abs(it - requestedHz) }
            if (supportedMatch != null) {
                val (dOk, _) = displayTouchRepo.setRefreshRate(
                    hz = supportedMatch,
                    rootGranted = rootAvailable,
                    shizukuGranted = shizukuAvailable
                )
                if (dOk) {
                    anyApplied = true
                    notes.add("${supportedMatch.toInt()} Hz")
                }
            }
        }

        if (shizukuAvailable && !rootAvailable) {
            val lowPower = if (profile.id == GameProfileId.POWERSAVE.id) "1" else "0"
            val res = ShizukuManager.exec("settings put global low_power $lowPower")
            if (res.success) {
                anyApplied = true
                notes.add(if (lowPower == "1") "Low Power On" else "ADB Boost")
            }
        }

        if (rootAvailable && profile.touchBoost) {
            val touch = displayTouchRepo.scanTouch()
            touch.gamingModePath?.let {
                val (tOk, _) = RootShellManager.writeAndVerify(it, "1")
                if (tOk) {
                    anyApplied = true
                    notes.add("Touch Boost")
                }
            }
            touch.touchBoostPath?.let {
                RootShellManager.writeAndVerify(it, "1")
            }
        }

        return if (anyApplied) {
            true to "${profile.name} active: ${notes.joinToString(" · ")}"
        } else {
            false to "${profile.name}: grant Root or Shizuku for full control"
        }
    }
}
