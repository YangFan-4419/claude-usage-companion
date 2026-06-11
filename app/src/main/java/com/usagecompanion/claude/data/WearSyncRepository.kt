package com.usagecompanion.claude.data

import android.content.Context
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearSyncRepository(context: Context) {
    private val appContext = context.applicationContext

    fun publish(snapshot: UsageSnapshot) {
        val request = PutDataMapRequest.create(PATH_USAGE_SNAPSHOT).apply {
            dataMap.putString(KEY_PLAN, snapshot.planLabel)
            dataMap.putInt(KEY_FIVE_HOUR_PERCENT, snapshot.fiveHourPercent)
            dataMap.putInt(KEY_SEVEN_DAY_PERCENT, snapshot.sevenDayPercent)
            dataMap.putString(KEY_FIVE_HOUR_RESET, snapshot.fiveHourResetLabel)
            dataMap.putString(KEY_SEVEN_DAY_RESET, snapshot.sevenDayResetLabel)
            dataMap.putString(KEY_USED, snapshot.usedTokensLabel)
            dataMap.putString(KEY_LIMIT, snapshot.limitTokensLabel)
            dataMap.putString(KEY_BURN, snapshot.burnRateLabel)
            dataMap.putString(KEY_WEEKLY, snapshot.weeklyLabel)
            dataMap.putString(KEY_SOURCE, snapshot.sourceLabel)
            dataMap.putBoolean(KEY_HAS_USAGE, snapshot.hasUsage)
            dataMap.putString(KEY_WATCH_STYLE, snapshot.watchStyle.id)
            dataMap.putBoolean(KEY_TILE_SHOWS_SEVEN_DAY, snapshot.tileShowsSevenDay)
            dataMap.putBoolean(KEY_HIGH_USAGE_ALERTS, snapshot.highUsageAlertsEnabled)
            dataMap.putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        runCatching {
            Wearable.getDataClient(appContext).putDataItem(request)
        }
    }

    companion object {
        const val PATH_USAGE_SNAPSHOT = "/usage_snapshot"
        const val PATH_USAGE_PREFERENCES = "/usage_preferences"
        const val PATH_REQUEST_USAGE_SNAPSHOT = "/request_usage_snapshot"
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
        const val KEY_TILE_SHOWS_SEVEN_DAY = "tile_shows_seven_day"
        const val KEY_HIGH_USAGE_ALERTS = "high_usage_alerts"
        const val KEY_UPDATED_AT = "updated_at"

        fun preferencesFrom(dataMap: DataMap): Pair<Boolean, Boolean> {
            return dataMap.getBoolean(KEY_TILE_SHOWS_SEVEN_DAY, true) to
                dataMap.getBoolean(KEY_HIGH_USAGE_ALERTS, false)
        }

        fun snapshotFrom(dataMap: DataMap): UsageSnapshot {
            return UsageSnapshot(
                planLabel = dataMap.getString(KEY_PLAN) ?: "Claude Code",
                fiveHourPercent = dataMap.getInt(KEY_FIVE_HOUR_PERCENT, 0),
                sevenDayPercent = dataMap.getInt(KEY_SEVEN_DAY_PERCENT, 0),
                fiveHourResetLabel = dataMap.getString(KEY_FIVE_HOUR_RESET) ?: "--",
                sevenDayResetLabel = dataMap.getString(KEY_SEVEN_DAY_RESET) ?: "--",
                usedTokensLabel = dataMap.getString(KEY_USED) ?: "--",
                limitTokensLabel = dataMap.getString(KEY_LIMIT) ?: "--",
                burnRateLabel = dataMap.getString(KEY_BURN) ?: "--",
                weeklyLabel = dataMap.getString(KEY_WEEKLY) ?: "--",
                sourceLabel = dataMap.getString(KEY_SOURCE) ?: "Phone",
                hasUsage = dataMap.getBoolean(KEY_HAS_USAGE, false),
                watchStyle = WatchProgressStyle.fromId(dataMap.getString(KEY_WATCH_STYLE)),
                tileShowsSevenDay = dataMap.getBoolean(KEY_TILE_SHOWS_SEVEN_DAY, true),
                highUsageAlertsEnabled = dataMap.getBoolean(KEY_HIGH_USAGE_ALERTS, false),
            )
        }
    }
}
