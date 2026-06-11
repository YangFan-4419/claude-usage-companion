package com.usagecompanion.claude.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.usagecompanion.claude.receiver.UsageRefreshReceiver

class UsageRefreshScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun scheduleNext() {
        alarmManager.setWindow(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + UsageRefreshManager.CACHE_TTL_MS,
            REFRESH_WINDOW_MS,
            pendingIntent(),
        )
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, UsageRefreshReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REFRESH_WINDOW_MS = 30_000L
    }
}
