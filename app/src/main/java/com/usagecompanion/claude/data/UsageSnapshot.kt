package com.usagecompanion.claude.data

data class UsageSnapshot(
    val planLabel: String,
    val fiveHourPercent: Int,
    val sevenDayPercent: Int,
    val fiveHourResetLabel: String,
    val sevenDayResetLabel: String,
    val usedTokensLabel: String,
    val limitTokensLabel: String,
    val burnRateLabel: String,
    val weeklyLabel: String,
    val sourceLabel: String,
    val hasUsage: Boolean,
    val watchStyle: WatchProgressStyle,
    val tileShowsSevenDay: Boolean,
    val highUsageAlertsEnabled: Boolean,
)

enum class WatchProgressStyle(val id: String, val label: String) {
    Ring("ring", "Ring"),
    Bar("bar", "Bar"),
    Compact("compact", "Compact");

    companion object {
        fun fromId(id: String?): WatchProgressStyle {
            return entries.firstOrNull { it.id == id } ?: Ring
        }
    }
}
