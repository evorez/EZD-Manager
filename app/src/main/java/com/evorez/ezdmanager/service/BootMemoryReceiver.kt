package com.evorez.ezdmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.hardware.RootModulesAndPropRepository
import com.evorez.ezdmanager.model.GameProfileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootMemoryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") {
            return
        }
        val pendingResult = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = SettingsDataStore(appCtx)
                var settings = store.settingsFlow.first()

                // Beast Mode Boot Guard: never leave Beast Mode active across a device reboot
                var beastDisabledAtBoot = false
                if (settings.activeProfileId == GameProfileId.BEAST.id) {
                    store.setActiveProfileId(GameProfileId.BALANCED.id)
                    beastDisabledAtBoot = true
                }
                if (settings.bootMemory.savedProfileId == GameProfileId.BEAST.id) {
                    store.saveBootMemory(settings.bootMemory.copy(savedProfileId = GameProfileId.BALANCED.id))
                    beastDisabledAtBoot = true
                }
                if (beastDisabledAtBoot) {
                    settings = store.settingsFlow.first()
                }

                val shouldApplyTweaks = settings.bootMemory.applyOnBoot

                if (shouldApplyTweaks) {
                    val safeModeTriggered = BootGuardManager.onBootStarting(appCtx)
                    if (safeModeTriggered) {
                        // Auto-disable boot tweaks and deactivate all Magisk/KSU/APatch modules
                        store.saveBootMemory(settings.bootMemory.copy(applyOnBoot = false))
                        runCatching { RootModulesAndPropRepository(appCtx).disableAllModules() }
                        val safeIntent = Intent(appCtx, RootDaemonService::class.java).apply {
                            this.action = RootDaemonService.ACTION_BOOT_SAFE_MODE_ALERT
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            appCtx.startForegroundService(safeIntent)
                        } else {
                            appCtx.startService(safeIntent)
                        }
                        return@launch
                    }
                }

                if (shouldApplyTweaks || settings.daemonEnabled || beastDisabledAtBoot) {
                    val svcIntent = Intent(appCtx, RootDaemonService::class.java).apply {
                        when {
                            shouldApplyTweaks -> this.action = RootDaemonService.ACTION_APPLY_BOOT_MEMORY
                            beastDisabledAtBoot -> this.action = RootDaemonService.ACTION_BEAST_BOOT_DISABLED_ALERT
                        }
                        if (beastDisabledAtBoot) {
                            putExtra(RootDaemonService.EXTRA_BEAST_BOOT_GUARD, true)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appCtx.startForegroundService(svcIntent)
                    } else {
                        appCtx.startService(svcIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
