package com.evorez.ezdmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.evorez.ezdmanager.MainActivity
import com.evorez.ezdmanager.R

object EzdNotificationHelper {

    const val CHANNEL_ALERTS_ID = "ezd_alerts_channel"
    const val CHANNEL_DAEMON_ID = "ezd_live_monitor"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ALERTS_ID) == null) {
                val alertsChannel = NotificationChannel(
                    CHANNEL_ALERTS_ID,
                    "EZD Alerts & Profiles",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts for profile changes, Beast Mode, backups and boot safe mode"
                    setShowBadge(true)
                }
                nm.createNotificationChannel(alertsChannel)
            }
        }
    }

    /**
     * Resolves the appropriate EZD logo bitmap based on the Android platform version:
     * - Older Android versions (< Android 12 / SDK 31): uses R.drawable.evorez_logo (original format PNG).
     * - Newer Android versions (Android 12+ / SDK >= 31): uses R.drawable.evorez_logo_circular (circular app icon PNG).
     */
    fun getAppLogoBitmap(context: Context): Bitmap? {
        return runCatching {
            val resId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                R.drawable.evorez_logo_circular
            } else {
                R.drawable.evorez_logo
            }
            BitmapFactory.decodeResource(context.resources, resId)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    fun postAlertNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationId, launchIntent, flags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ALERTS_ID)
        } else {
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH)
        }

        builder
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_ezd_stat)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        getAppLogoBitmap(context)?.let { logo ->
            builder.setLargeIcon(logo)
        }

        nm.notify(notificationId, builder.build())
    }
}
