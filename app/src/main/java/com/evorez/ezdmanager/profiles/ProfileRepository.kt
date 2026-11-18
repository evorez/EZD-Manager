package com.evorez.ezdmanager.profiles

import android.content.Context
import com.evorez.ezdmanager.hardware.CpuGpuRepository
import com.evorez.ezdmanager.hardware.DisplayTouchRepository
import com.evorez.ezdmanager.hardware.EngineTuningManager
import com.evorez.ezdmanager.hardware.HardwareProbe
import com.evorez.ezdmanager.hardware.SystemToolsRepository
import com.evorez.ezdmanager.model.BeastExecutionReport
import com.evorez.ezdmanager.model.GameProfileId
import com.evorez.ezdmanager.model.ProfileConfig

class ProfileRepository(
    private val context: Context,
    private val cpuGpuRepo: CpuGpuRepository,
    private val displayTouchRepo: DisplayTouchRepository,
    private val sysToolsRepo: SystemToolsRepository
) {
    companion object {
        fun builtInProfiles(): List<ProfileConfig> {
            val detectedPeak = HardwareProbe.getCached()?.displayPeakRateHz
            return listOf(
                ProfileConfig(
                    id = GameProfileId.BEAST.id,
                    name = "Beast Mode",
                    description = "Locks 100% CPU & GPU, disables limiters, tunes I/O/VM/TCP & locks peak refresh",
                    isBuiltIn = true,
                    cpuGovernor = "performance",
                    cpuMaxScalePercent = 100,
                    cpuMinScalePercent = 100,
                    gpuGovernor = "performance",
                    gpuMaxPercent = 100,
                    targetRefreshHz = detectedPeak,
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
                    description = "GPU 70% · CPU 70% boost with detected peak refresh & responsiveness",
                    isBuiltIn = true,
                    cpuGovernor = "performance",
                    cpuMaxScalePercent = 70,
                    cpuMinScalePercent = 40,
                    gpuGovernor = "performance",
                    gpuMaxPercent = 70,
                    targetRefreshHz = detectedPeak,
                    touchBoost = true
                )
            )
        }

        fun forGameProfile(id: GameProfileId, customProfiles: List<ProfileConfig>): ProfileConfig {
            return when (id) {
                GameProfileId.CUSTOM -> customProfiles.firstOrNull()
                    ?: builtInProfiles().first { it.id == GameProfileId.PERFORMANCE.id }
                else -> builtInProfiles().find { it.id == id.id } ?: builtInProfiles().first()
            }
        }
    }

    suspend fun activateBeastMode(
        rootAvailable: Boolean,
        shizukuAvailable: Boolean
    ): BeastExecutionReport {
        return EngineTuningManager.activateBeastMode(
            context = context,
            rootGranted = rootAvailable,
            shizukuGranted = shizukuAvailable
        )
    }

    suspend fun deactivateBeastMode(
        rootAvailable: Boolean,
        shizukuAvailable: Boolean
    ): Pair<Boolean, String> {
        return EngineTuningManager.deactivateBeastAndRestore(
            context = context,
            rootGranted = rootAvailable,
            shizukuGranted = shizukuAvailable
        )
    }

    suspend fun applyProfile(
        profile: ProfileConfig,
        rootAvailable: Boolean = true,
        shizukuAvailable: Boolean = false
    ): Pair<Boolean, String> {
        if (profile.isBeastMode || profile.id == GameProfileId.BEAST.id) {
            val report = activateBeastMode(rootAvailable, shizukuAvailable)
            val anyOk = report.items.any { it.status == com.evorez.ezdmanager.model.EngineItemStatus.OK }
            return anyOk to report.summary
        }
        return EngineTuningManager.applyStandardProfile(
            context = context,
            profile = profile,
            rootGranted = rootAvailable,
            shizukuGranted = shizukuAvailable
        )
    }
}

