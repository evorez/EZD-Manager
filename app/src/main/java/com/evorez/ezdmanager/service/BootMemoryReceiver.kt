package com.evorez.ezdmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.hardware.RootModulesAndPropRepository
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
                val settings = store.settingsFlow.first()
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

                if (shouldApplyTweaks || settings.daemonEnabled) {
                    val svcIntent = Intent(appCtx, RootDaemonService::class.java).apply {
                        if (shouldApplyTweaks) {
                            this.action = RootDaemonService.ACTION_APPLY_BOOT_MEMORY
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
