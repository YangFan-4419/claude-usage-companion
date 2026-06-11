package com.usagecompanion.claude.data

import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class ClaudeUsageClient {
    fun fetch(token: String, style: WatchProgressStyle): Result<UsageSnapshot> {
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException("OAuth token is required"))
        }

        return runCatching {
            val connection = (URL(MESSAGES_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                setRequestProperty("anthropic-beta", OAUTH_BETA)
                setRequestProperty("content-type", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            connection.outputStream.use { stream ->
                stream.write(PROBE_BODY.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val fiveHourRaw = connection.getHeaderField(HEADER_5H_USAGE)
            val fiveHourResetRaw = connection.getHeaderField(HEADER_5H_RESET)
            val sevenDayRaw = connection.getHeaderField(HEADER_7D_USAGE)
            val sevenDayResetRaw = connection.getHeaderField(HEADER_7D_RESET)
            connection.disconnect()

            if (fiveHourRaw.isNullOrBlank() && sevenDayRaw.isNullOrBlank()) {
                val message = when (code) {
                    HttpURLConnection.HTTP_UNAUTHORIZED -> "OAuth token rejected by Anthropic"
                    HttpURLConnection.HTTP_FORBIDDEN -> "OAuth token is not allowed to access Claude usage"
                    else -> "Usage headers missing from Anthropic response (HTTP $code)"
                }
                throw IllegalStateException(message)
            }

            UsageSnapshot(
                planLabel = "Claude Code usage",
                fiveHourPercent = utilizationPercent(fiveHourRaw),
                sevenDayPercent = utilizationPercent(sevenDayRaw),
                fiveHourResetLabel = resetLabel(fiveHourResetRaw),
                sevenDayResetLabel = resetLabel(sevenDayResetRaw),
                usedTokensLabel = "--",
                limitTokensLabel = "--",
                burnRateLabel = "--",
                weeklyLabel = "--",
                sourceLabel = "Phone direct",
                hasUsage = true,
                watchStyle = style,
                tileShowsSevenDay = true,
                highUsageAlertsEnabled = false,
            )
        }
    }

    private fun utilizationPercent(raw: String?): Int {
        return ((raw?.toDoubleOrNull() ?: 0.0) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun resetLabel(raw: String?): String {
        val epochSeconds = raw?.toLongOrNull() ?: return "--"
        val now = Instant.now()
        val reset = Instant.ofEpochSecond(epochSeconds)
        val seconds = reset.epochSecond - now.epochSecond
        if (seconds <= 0) return "now"

        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3_600
        val minutes = (seconds % 3_600) / 60

        return when {
            days > 0 -> "${days}d${hours}h"
            hours > 0 -> "${hours}h${minutes}m"
            else -> "${minutes}m"
        }
    }

    companion object {
        private const val MESSAGES_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val OAUTH_BETA = "oauth-2025-04-20"
        private const val USER_AGENT = "claude-code/2.1.5"
        private const val PROBE_MODEL = "claude-haiku-4-5-20251001"
        private const val PROBE_BODY =
            "{\"model\":\"$PROBE_MODEL\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\".\"}]}"

        private const val HEADER_5H_USAGE = "anthropic-ratelimit-unified-5h-utilization"
        private const val HEADER_5H_RESET = "anthropic-ratelimit-unified-5h-reset"
        private const val HEADER_7D_USAGE = "anthropic-ratelimit-unified-7d-utilization"
        private const val HEADER_7D_RESET = "anthropic-ratelimit-unified-7d-reset"
    }
}
