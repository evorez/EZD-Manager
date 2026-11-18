package com.evorez.ezdmanager.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.evorez.ezdmanager.model.BootGuardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Synchronous persistent Boot-Loop Protection & Safe Mode guard.
 * Tracks pending boot attempts in device-protected storage (when available) so
 * if 3 consecutive boots fail or crash before reaching stability confirmation,
 * all boot tweaks, per-app overrides, and Magisk/KernelSU/APatch modules are
 * automatically disabled into Safe Mode.
 */
object BootGuardManager {
    private const val PREF_NAME = "ezd_boot_guard_prefs"
    private const val KEY_PENDING_ATTEMPTS = "pending_boot_attempts"
    private const val KEY_SAFE_MODE = "safe_mode_active"
    private const val KEY_SAFE_REASON = "safe_mode_reason"
    private const val KEY_TRIGGERED_AT = "safe_mode_triggered_at"
    private const val KEY_HEALTHY = "boot_confirmed_healthy"

    const val MAX_FAILED_BOOTS = 3

    private val _state = MutableStateFlow(BootGuardState())
    val state: StateFlow<BootGuardState> = _state.asStateFlow()

    private fun prefs(context: Context): SharedPreferences {
        val ctx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { context.createDeviceProtectedStorageContext() }.getOrElse { context }
        } else {
            context
        }
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun refresh(context: Context): BootGuardState {
        val p = prefs(context)
        val snap = BootGuardState(
            safeModeActive = p.getBoolean(KEY_SAFE_MODE, false),
            pendingBootAttempts = p.getInt(KEY_PENDING_ATTEMPTS, 0),
            bootConfirmedHealthy = p.getBoolean(KEY_HEALTHY, true),
            lastReason = p.getString(KEY_SAFE_REASON, "") ?: "",
            lastTriggeredAt = p.getLong(KEY_TRIGGERED_AT, 0L)
        )
        _state.value = snap
        return snap
    }

    /**
     * Invoked at BOOT_COMPLETED before applying any root/kernel tweaks.
     * Returns true if Safe Mode was triggered (>= 2 unconfirmed boots) and tweaks MUST be aborted.
     */
    fun onBootStarting(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_SAFE_MODE, false)) {
            refresh(context)
            return true
        }
        val nextAttempts = p.getInt(KEY_PENDING_ATTEMPTS, 0) + 1
        if (nextAttempts >= MAX_FAILED_BOOTS) {
            p.edit()
                .putBoolean(KEY_SAFE_MODE, true)
                .putInt(KEY_PENDING_ATTEMPTS, 0)
                .putBoolean(KEY_HEALTHY, false)
                .putString(
                    KEY_SAFE_REASON,
                    "Auto-protected after $nextAttempts unconfirmed boot attempts — Boot Memory, tweaks & root modules disabled."
                )
                .putLong(KEY_TRIGGERED_AT, System.currentTimeMillis())
                .commit()
            refresh(context)
            return true
        }
        p.edit()
            .putInt(KEY_PENDING_ATTEMPTS, nextAttempts)
            .putBoolean(KEY_HEALTHY, false)
            .commit()
        refresh(context)
        return false
    }

    fun confirmBootHealthy(context: Context) {
        val p = prefs(context)
        p.edit()
            .putInt(KEY_PENDING_ATTEMPTS, 0)
            .putBoolean(KEY_HEALTHY, true)
            .commit()
        refresh(context)
    }

    fun triggerManualSafeMode(context: Context, reason: String) {
        val p = prefs(context)
        p.edit()
            .putBoolean(KEY_SAFE_MODE, true)
            .putInt(KEY_PENDING_ATTEMPTS, 0)
            .putBoolean(KEY_HEALTHY, false)
            .putString(KEY_SAFE_REASON, reason)
            .putLong(KEY_TRIGGERED_AT, System.currentTimeMillis())
            .commit()
        refresh(context)
    }

    fun clearSafeMode(context: Context) {
        val p = prefs(context)
        p.edit()
            .putBoolean(KEY_SAFE_MODE, false)
            .putInt(KEY_PENDING_ATTEMPTS, 0)
            .putBoolean(KEY_HEALTHY, true)
            .putString(KEY_SAFE_REASON, "")
            .commit()
        refresh(context)
    }
}
