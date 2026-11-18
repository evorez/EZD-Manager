package com.evorez.ezdmanager.root

import com.evorez.ezdmanager.model.PrivilegeMode
import com.evorez.ezdmanager.model.RootStatus
import com.evorez.ezdmanager.model.ShizukuStatus
import com.evorez.ezdmanager.model.ToolId
import com.evorez.ezdmanager.model.WorkMode

/**
 * WorkModeManager:
 * - Central authority evaluator that detects whether the app holds Root or Shizuku permission.
 * - Dynamically determines authority level (Root vs Shizuku vs Basic Monitor).
 * - Dynamically enables/disables UI features, tools, and actions based on current authority.
 * - Provides user-facing badges, explanations, and unlock pathways.
 */
object WorkModeManager {

    /**
     * Resolves current effective authority mode based on user's desired WorkMode
     * and the actual granted permissions (Root and Shizuku).
     */
    fun resolveEffectiveMode(
        preferredMode: WorkMode,
        rootStatus: RootStatus,
        shizukuStatus: ShizukuStatus
    ): PrivilegeMode {
        val rootGranted = rootStatus == RootStatus.GRANTED
        val shizukuGranted = shizukuStatus == ShizukuStatus.GRANTED
        return when (preferredMode) {
            WorkMode.AUTO -> when {
                rootGranted -> PrivilegeMode.ROOT
                shizukuGranted -> PrivilegeMode.SHIZUKU
                else -> PrivilegeMode.BASIC
            }
            WorkMode.ROOT -> if (rootGranted) PrivilegeMode.ROOT else PrivilegeMode.BASIC
            WorkMode.SHIZUKU -> if (shizukuGranted) PrivilegeMode.SHIZUKU else PrivilegeMode.BASIC
            WorkMode.BASIC -> PrivilegeMode.BASIC
        }
    }

    /**
     * Minimum authority level required for full functionality of each tool.
     */
    fun getRequiredAuthority(tool: ToolId): PrivilegeMode = when (tool) {
        ToolId.CPU_GPU_CONTROL -> PrivilegeMode.ROOT
        ToolId.ZRAM_VM -> PrivilegeMode.ROOT
        ToolId.KERNEL_TWEAKS -> PrivilegeMode.ROOT
        ToolId.DISPLAY -> PrivilegeMode.SHIZUKU
        ToolId.TOUCH -> PrivilegeMode.ROOT
        ToolId.NETWORK -> PrivilegeMode.SHIZUKU
        ToolId.AUDIO_ENHANCER -> PrivilegeMode.BASIC
        ToolId.BATTERY_SAVING -> PrivilegeMode.SHIZUKU
        ToolId.FAST_CHARGE -> PrivilegeMode.ROOT
        ToolId.DEVICE_SPOOF -> PrivilegeMode.ROOT
        ToolId.PROFILES -> PrivilegeMode.BASIC
        ToolId.MODULES -> PrivilegeMode.ROOT
        ToolId.BUILD_PROP -> PrivilegeMode.ROOT
        ToolId.BOOT_GUARD -> PrivilegeMode.BASIC
        ToolId.EZD_BACKUP -> PrivilegeMode.BASIC
        ToolId.APP_BACKUP -> PrivilegeMode.SHIZUKU
        ToolId.ROOT_TERMINAL -> PrivilegeMode.SHIZUKU
    }

    /**
     * Returns true if the tool can be safely opened and interactively configured.
     * Features that offer telemetry or fallback capabilities remain operable,
     * whereas strictly root-locked features are blocked when root is missing.
     */
    fun isToolEnabled(tool: ToolId, currentMode: PrivilegeMode): Boolean {
        if (currentMode == PrivilegeMode.ROOT) return true
        if (isStrictlyRootLocked(tool) && currentMode != PrivilegeMode.ROOT) return false
        val required = getRequiredAuthority(tool)
        return when (required) {
            PrivilegeMode.BASIC -> true
            PrivilegeMode.SHIZUKU -> currentMode == PrivilegeMode.SHIZUKU
            PrivilegeMode.ROOT -> true // Opens in telemetry/read-only mode unless strictly locked
        }
    }

    /**
     * Tools that cannot function at all without genuine su root (e.g. Magisk/KSU modules, build.prop writing, resetprop).
     */
    fun isStrictlyRootLocked(tool: ToolId): Boolean = when (tool) {
        ToolId.MODULES,
        ToolId.BUILD_PROP,
        ToolId.DEVICE_SPOOF -> true
        else -> false
    }

    /**
     * Returns dynamic authority badge info for UI cards.
     */
    fun getToolBadge(tool: ToolId, currentMode: PrivilegeMode): ToolAuthorityBadge {
        val required = getRequiredAuthority(tool)
        return when {
            required == PrivilegeMode.ROOT && currentMode == PrivilegeMode.ROOT ->
                ToolAuthorityBadge("ROOT", 0xFF22C55E, isGranted = true, isStrictlyLocked = false)
            required == PrivilegeMode.ROOT && currentMode != PrivilegeMode.ROOT -> {
                val isLocked = isStrictlyRootLocked(tool)
                ToolAuthorityBadge(
                    if (isLocked) "LOCKED (ROOT)" else "TELEMETRY ONLY",
                    if (isLocked) 0xFFEF4444 else 0xFFF59E0B,
                    isGranted = false,
                    isStrictlyLocked = isLocked
                )
            }
            required == PrivilegeMode.SHIZUKU && (currentMode == PrivilegeMode.ROOT || currentMode == PrivilegeMode.SHIZUKU) ->
                ToolAuthorityBadge(if (currentMode == PrivilegeMode.ROOT) "ROOT" else "SHIZUKU", 0xFF3B82F6, isGranted = true, isStrictlyLocked = false)
            required == PrivilegeMode.SHIZUKU && currentMode == PrivilegeMode.BASIC ->
                ToolAuthorityBadge("SHIZUKU REQ", 0xFFF59E0B, isGranted = false, isStrictlyLocked = false)
            else ->
                ToolAuthorityBadge("SAFE", 0xFF10B981, isGranted = true, isStrictlyLocked = false)
        }
    }

    /**
     * Clear explanation for UI banners or dialogs when higher authority is needed.
     */
    fun getAuthorityNotice(tool: ToolId, currentMode: PrivilegeMode): String? {
        if (currentMode == PrivilegeMode.ROOT) return null
        val required = getRequiredAuthority(tool)
        return when {
            isStrictlyRootLocked(tool) && currentMode != PrivilegeMode.ROOT ->
                "Requires Root (su) authority. This tool modifies protected system partitions or Magisk/KernelSU modules."
            required == PrivilegeMode.ROOT && currentMode == PrivilegeMode.SHIZUKU ->
                "Operating with Shizuku: Hardware parameters can be monitored, but kernel sysfs writes require Root (su)."
            required == PrivilegeMode.ROOT && currentMode == PrivilegeMode.BASIC ->
                "Operating in Basic mode: Read-only telemetry enabled. Grant Root to tune clocks and governors."
            required == PrivilegeMode.SHIZUKU && currentMode == PrivilegeMode.BASIC ->
                "Grant Shizuku permission or Root to execute elevated system commands for this feature."
            else -> null
        }
    }
}

data class ToolAuthorityBadge(
    val label: String,
    val colorArgb: Long,
    val isGranted: Boolean,
    val isStrictlyLocked: Boolean
)
