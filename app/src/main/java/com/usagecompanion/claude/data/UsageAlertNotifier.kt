package com.usagecompanion.claude.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.usagecompanion.claude.R

class UsageAlertNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    fun notifyHighUsage(snapshot: UsageSnapshot) {
        if (!snapshot.highUsageAlertsEnabled || !snapshot.hasUsage || !snapshot.isHighUsage()) return
        if (!canPostNotifications()) return

        createChannel()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Claude usage is high")
            .setContentText("5h ${snapshot.fiveHourPercent}% · 7d ${snapshot.sevenDayPercent}%")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Usage alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "usage_alerts"
        private const val NOTIFICATION_ID = 9001
    }
}

fun UsageSnapshot.isHighUsage(): Boolean = fiveHourPercent >= 90 || sevenDayPercent >= 90
