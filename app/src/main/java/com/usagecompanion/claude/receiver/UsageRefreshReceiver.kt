package com.usagecompanion.claude.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.usagecompanion.claude.data.TokenVault
import com.usagecompanion.claude.data.UsageRefreshManager
import com.usagecompanion.claude.data.UsageRefreshScheduler

class UsageRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val tokenVault = TokenVault(context)
                val scheduler = UsageRefreshScheduler(context)
                if (tokenVault.readToken().isNullOrBlank()) {
                    scheduler.cancel()
                } else {
                    UsageRefreshManager(context, tokenVault = tokenVault).refreshIfStale(force = false)
                    scheduler.scheduleNext()
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
