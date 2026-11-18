package com.evorez.ezdmanager.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.evorez.ezdmanager.MainActivity
import com.evorez.ezdmanager.R
import com.evorez.ezdmanager.data.SettingsDataStore
import com.evorez.ezdmanager.profiles.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home Screen Widget enabling instant 1-tap performance profile switching
 * without opening EZD Manager.
 */
class EzdProfileWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeId = runCatching {
                    SettingsDataStore(context.applicationContext).settingsFlow.first().activeProfileId
                }.getOrDefault("balanced")
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildRemoteViews(context, activeId))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_PROFILE) {
            val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
            val pendingResult = goAsync()
            val appCtx = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val svcIntent = Intent(appCtx, RootDaemonService::class.java).apply {
                        action = RootDaemonService.ACTION_SWITCH_PROFILE
                        putExtra(RootDaemonService.EXTRA_PROFILE_ID, profileId)
                    }
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            appCtx.startForegroundService(svcIntent)
                        } else {
                            appCtx.startService(svcIntent)
                        }
                    }
                    SettingsDataStore(appCtx).setActiveProfileId(profileId)
                    updateAllWidgets(appCtx, profileId)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_PROFILE = "com.evorez.ezdmanager.WIDGET_SWITCH_PROFILE"
        const val EXTRA_PROFILE_ID = "profile_id"

        fun updateAllWidgets(context: Context, knownActiveId: String? = null) {
            CoroutineScope(Dispatchers.IO).launch {
                val appCtx = context.applicationContext
                val mgr = AppWidgetManager.getInstance(appCtx)
                val ids = mgr.getAppWidgetIds(ComponentName(appCtx, EzdProfileWidgetProvider::class.java))
                if (ids.isEmpty()) return@launch
                val activeId = knownActiveId ?: runCatching {
                    SettingsDataStore(appCtx).settingsFlow.first().activeProfileId
                }.getOrDefault("balanced")
                ids.forEach { id ->
                    mgr.updateAppWidget(id, buildRemoteViews(appCtx, activeId))
                }
            }
        }

        private fun buildRemoteViews(context: Context, activeId: String): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_profile_switcher)
            val activeName = ProfileRepository.builtInProfiles().find { it.id == activeId }?.name ?: "Balanced"
            rv.setTextViewText(R.id.widget_active_label, "Active: $activeName")

            val openIntent = Intent(context, MainActivity::class.java)
            val openPi = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_header, openPi)

            bindButton(context, rv, R.id.btn_prof_balanced, "balanced", activeId == "balanced", 101)
            bindButton(context, rv, R.id.btn_prof_perf, "performance", activeId == "performance", 102)
            bindButton(context, rv, R.id.btn_prof_powersave, "powersave", activeId == "powersave", 103)
            return rv
        }

        private fun bindButton(
            context: Context,
            rv: RemoteViews,
            viewId: Int,
            profileId: String,
            isActive: Boolean,
            requestCode: Int
        ) {
            val intent = Intent(context, EzdProfileWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_PROFILE
                putExtra(EXTRA_PROFILE_ID, profileId)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(viewId, pi)
            rv.setInt(
                viewId,
                "setBackgroundResource",
                if (isActive) R.drawable.widget_btn_active else R.drawable.widget_btn_idle
            )
        }
    }
}
