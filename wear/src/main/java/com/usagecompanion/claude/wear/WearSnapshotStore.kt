package com.usagecompanion.claude.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap

data class WearSnapshot(
    val planLabel: String,
    val fiveHourPercent: Int,
    val sevenDayPercent: Int,
    val fiveHourResetLabel: String,
    val sevenDayResetLabel: String,
    val usedTokensLabel: String,
    val limitTokensLabel: String,
    val burnRateLabel: String,
    val hasUsage: Boolean,
    val watchStyle: String,
    val updatedAt: Long,
)

class WearSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("wear_snapshot", Context.MODE_PRIVATE)

    fun read(): WearSnapshot {
        return WearSnapshot(
            planLabel = prefs.getString(KEY_PLAN, null) ?: "Set up on phone",
            fiveHourPercent = prefs.getInt(KEY_FIVE_HOUR_PERCENT, 0),
            sevenDayPercent = prefs.getInt(KEY_SEVEN_DAY_PERCENT, 0),
            fiveHourResetLabel = prefs.getString(KEY_FIVE_HOUR_RESET, null) ?: "--",
            sevenDayResetLabel = prefs.getString(KEY_SEVEN_DAY_RESET, null) ?: "--",
            usedTokensLabel = prefs.getString(KEY_USED, null) ?: "--",
            limitTokensLabel = prefs.getString(KEY_LIMIT, null) ?: "--",
            burnRateLabel = prefs.getString(KEY_BURN, null) ?: "--",
            hasUsage = prefs.getBoolean(KEY_HAS_USAGE, false),
            watchStyle = prefs.getString(KEY_WATCH_STYLE, null) ?: STYLE_RING,
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    fun save(dataMap: DataMap) {
        prefs.edit()
            .putString(KEY_PLAN, dataMap.getString(KEY_PLAN) ?: "Claude")
            .putInt(KEY_FIVE_HOUR_PERCENT, dataMap.getInt(KEY_FIVE_HOUR_PERCENT, 0))
            .putInt(KEY_SEVEN_DAY_PERCENT, dataMap.getInt(KEY_SEVEN_DAY_PERCENT, 0))
            .putString(KEY_FIVE_HOUR_RESET, dataMap.getString(KEY_FIVE_HOUR_RESET) ?: "--")
            .putString(KEY_SEVEN_DAY_RESET, dataMap.getString(KEY_SEVEN_DAY_RESET) ?: "--")
            .putString(KEY_USED, dataMap.getString(KEY_USED) ?: "--")
            .putString(KEY_LIMIT, dataMap.getString(KEY_LIMIT) ?: "--")
            .putString(KEY_BURN, dataMap.getString(KEY_BURN) ?: "--")
            .putBoolean(KEY_HAS_USAGE, dataMap.getBoolean(KEY_HAS_USAGE, false))
            .putString(KEY_WATCH_STYLE, dataMap.getString(KEY_WATCH_STYLE) ?: STYLE_RING)
            .putLong(KEY_UPDATED_AT, dataMap.getLong(KEY_UPDATED_AT, System.currentTimeMillis()))
            .apply()
    }

    companion object {
        const val PATH_USAGE_SNAPSHOT = "/usage_snapshot"
        const val KEY_PLAN = "plan"
        const val KEY_FIVE_HOUR_PERCENT = "five_hour_percent"
        const val KEY_SEVEN_DAY_PERCENT = "seven_day_percent"
        const val KEY_FIVE_HOUR_RESET = "five_hour_reset"
        const val KEY_SEVEN_DAY_RESET = "seven_day_reset"
        const val KEY_USED = "used"
        const val KEY_LIMIT = "limit"
        const val KEY_BURN = "burn"
        const val KEY_HAS_USAGE = "has_usage"
        const val KEY_WATCH_STYLE = "watch_style"
        const val KEY_UPDATED_AT = "updated_at"
        const val STYLE_RING = "ring"
        const val STYLE_BAR = "bar"
        const val STYLE_COMPACT = "compact"
    }
}
