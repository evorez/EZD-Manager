package com.evorez.ezdmanager.profiles

import android.content.Context
import com.evorez.ezdmanager.hardware.CpuGpuRepository
import com.evorez.ezdmanager.hardware.DisplayTouchRepository
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.GameProfileId
import com.evorez.ezdmanager.model.ProfileConfig
import com.evorez.ezdmanager.root.RootShellManager

class ProfileRepository(
    private val context: Context,
    private val cpuGpuRepo: CpuGpuRepository,
    private val displayTouchRepo: DisplayTouchRepository,
    private val sysToolsRepo: SystemToolsRepository
) {
    companion object {
        fun builtInProfiles(): List<ProfileConfig> = listOf(
            ProfileConfig(
                id = GameProfileId.BALANCED.id,
                name = "Balanced",
                description = "Stock dynamic scaling with responsive schedutil balance",
                isBuiltIn = true,
                cpuGovernor = "schedutil",
                cpuMaxScalePercent = 100,
                cpuMinScalePercent = 15,
                gpuGovernor = "msm-adreno-tz",
                targetRefreshHz = null,
                touchBoost = false
            ),
            ProfileConfig(
                id = GameProfileId.DYNAMIC.id,
                name = "Dynamic",
                description = "Adaptive mid-tier floor boost with thermal safety intact",
                isBuiltIn = true,
                cpuGovernor = "schedutil",
                cpuMaxScalePercent = 100,
                cpuMinScalePercent = 35,
                gpuGovernor = "msm-adreno-tz",
                targetRefreshHz = null,
                touchBoost = true
            ),
            ProfileConfig(
                id = GameProfileId.PERFORMANCE.id,
                name = "Performance",
                description = "Sustained higher CPU/GPU floor & fast touch response",
                isBuiltIn = true,
                cpuGovernor = "schedutil",
                cpuMaxScalePercent = 100,
                cpuMinScalePercent = 55,
                gpuGovernor = "performance",
                targetRefreshHz = null,
                touchBoost = true
            ),
            ProfileConfig(
                id = GameProfileId.MAX_PERFORMANCE.id,
                name = "Maximum Performance",
                description = "Peak CPU/GPU clock targets within kernel thermal protection",
                isBuiltIn = true,
                cpuGovernor = "performance",
                cpuMaxScalePercent = 100,
                cpuMinScalePercent = 75,
                gpuGovernor = "performance",
                targetRefreshHz = 120f,
                touchBoost = true
            )
        )

        fun forGameProfile(id: GameProfileId, customProfiles: List<ProfileConfig>): ProfileConfig {
            return when (id) {
                GameProfileId.CUSTOM -> customProfiles.firstOrNull() ?: builtInProfiles().first { it.id == GameProfileId.PERFORMANCE.id }
                else -> builtInProfiles().find { it.id == id.id } ?: builtInProfiles().first()
            }
        }
    }

    suspend fun applyProfile(profile: ProfileConfig): Pair<Boolean, String> {
        val notes = mutableListOf<String>()
        var anyApplied = false

        // Thermal protection nodes are NEVER modified or bypassed.
        val policies = cpuGpuRepo.scanCpuPolicies()
        policies.forEach { policy ->
            val targetGov = when {
                profile.cpuGovernor in policy.governors -> profile.cpuGovernor
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
        if (policies.isNotEmpty()) notes.add("CPU (${policies.size} clusters)")

        val gpu = cpuGpuRepo.scanGpu()
        if (gpu.supported && profile.gpuGovernor.isNotBlank() && profile.gpuGovernor in gpu.availableGovernors) {
            val (gOk, _) = cpuGpuRepo.setGpuGovernor(gpu, profile.gpuGovernor)
            if (gOk) {
                anyApplied = true
                notes.add("GPU (${profile.gpuGovernor})")
            }
        }

        val display = displayTouchRepo.readDisplayState(rootGranted = true)
        profile.targetRefreshHz?.let { requestedHz ->
            val supportedMatch = display.supportedModes
                .map { it.refreshRateHz }
                .minByOrNull { kotlin.math.abs(it - requestedHz) }
            if (supportedMatch != null) {
                val (dOk, _) = displayTouchRepo.setRefreshRate(supportedMatch, rootGranted = true)
                if (dOk) {
                    anyApplied = true
                    notes.add("${supportedMatch.toInt()} Hz")
                }
            }
        }

        if (profile.touchBoost) {
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
            false to "${profile.name}: no writable hardware nodes available (check root status)"
        }
    }
}
