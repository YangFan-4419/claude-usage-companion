package com.usagecompanion.claude.data

import android.content.Context

class UsageRepository(context: Context) {
    private val prefs = context.getSharedPreferences("usage_snapshot", Context.MODE_PRIVATE)

    fun currentSnapshot(): UsageSnapshot {
        return UsageSnapshot(
            planLabel = prefs.getString(KEY_PLAN, null) ?: "Phone setup required",
            fiveHourPercent = prefs.getInt(KEY_FIVE_HOUR_PERCENT, 0),
            sevenDayPercent = prefs.getInt(KEY_SEVEN_DAY_PERCENT, 0),
            fiveHourResetLabel = prefs.getString(KEY_FIVE_HOUR_RESET, null) ?: "--",
            sevenDayResetLabel = prefs.getString(KEY_SEVEN_DAY_RESET, null) ?: "--",
            usedTokensLabel = prefs.getString(KEY_USED, null) ?: "--",
            limitTokensLabel = prefs.getString(KEY_LIMIT, null) ?: "--",
            burnRateLabel = prefs.getString(KEY_BURN, null) ?: "--",
            weeklyLabel = prefs.getString(KEY_WEEKLY, null) ?: "--",
            sourceLabel = prefs.getString(KEY_SOURCE, null) ?: "Waiting for phone token",
            hasUsage = prefs.getBoolean(KEY_HAS_USAGE, false),
            watchStyle = WatchProgressStyle.fromId(prefs.getString(KEY_WATCH_STYLE, null)),
        )
    }

    fun tokenStateSnapshot(hasToken: Boolean): UsageSnapshot {
        val current = currentSnapshot()
        val next = if (hasToken) {
            if (current.hasUsage) {
                current
            } else {
                current.copy(
                    planLabel = "OAuth token saved",
                    fiveHourPercent = 0,
                    sevenDayPercent = 0,
                    fiveHourResetLabel = "--",
                    sevenDayResetLabel = "--",
                    usedTokensLabel = "--",
                    limitTokensLabel = "--",
                    burnRateLabel = "--",
                    weeklyLabel = "--",
                    sourceLabel = "Waiting for usage refresh",
                    hasUsage = false,
                )
            }
        } else {
            setupRequiredSnapshot(current.watchStyle)
        }
        save(next)
        return next
    }

    fun updateWatchStyle(style: WatchProgressStyle): UsageSnapshot {
        val next = currentSnapshot().copy(watchStyle = style)
        save(next)
        return next
    }

    fun save(snapshot: UsageSnapshot) {
        prefs.edit()
            .putString(KEY_PLAN, snapshot.planLabel)
            .putInt(KEY_FIVE_HOUR_PERCENT, snapshot.fiveHourPercent)
            .putInt(KEY_SEVEN_DAY_PERCENT, snapshot.sevenDayPercent)
            .putString(KEY_FIVE_HOUR_RESET, snapshot.fiveHourResetLabel)
            .putString(KEY_SEVEN_DAY_RESET, snapshot.sevenDayResetLabel)
            .putString(KEY_USED, snapshot.usedTokensLabel)
            .putString(KEY_LIMIT, snapshot.limitTokensLabel)
            .putString(KEY_BURN, snapshot.burnRateLabel)
            .putString(KEY_WEEKLY, snapshot.weeklyLabel)
            .putString(KEY_SOURCE, snapshot.sourceLabel)
            .putBoolean(KEY_HAS_USAGE, snapshot.hasUsage)
            .putString(KEY_WATCH_STYLE, snapshot.watchStyle.id)
            .apply()
    }

    private fun setupRequiredSnapshot(style: WatchProgressStyle): UsageSnapshot {
        return UsageSnapshot(
            planLabel = "Phone setup required",
            fiveHourPercent = 0,
            sevenDayPercent = 0,
            fiveHourResetLabel = "--",
            sevenDayResetLabel = "--",
            usedTokensLabel = "--",
            limitTokensLabel = "--",
            burnRateLabel = "--",
            weeklyLabel = "--",
            sourceLabel = "Waiting for phone token",
            hasUsage = false,
            watchStyle = style,
        )
    }

    private companion object {
        const val KEY_PLAN = "plan"
        const val KEY_FIVE_HOUR_PERCENT = "five_hour_percent"
        const val KEY_SEVEN_DAY_PERCENT = "seven_day_percent"
        const val KEY_FIVE_HOUR_RESET = "five_hour_reset"
        const val KEY_SEVEN_DAY_RESET = "seven_day_reset"
        const val KEY_USED = "used"
        const val KEY_LIMIT = "limit"
        const val KEY_BURN = "burn"
        const val KEY_WEEKLY = "weekly"
        const val KEY_SOURCE = "source"
        const val KEY_HAS_USAGE = "has_usage"
        const val KEY_WATCH_STYLE = "watch_style"
    }
}
