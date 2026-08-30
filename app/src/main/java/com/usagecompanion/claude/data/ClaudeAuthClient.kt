package com.usagecompanion.claude.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 用刷新令牌换新的访问令牌。
 *
 * 注意：这套接口没有公开文档，是社区逆向出来的，Anthropic 随时可能改。
 * 两个已知细节，都踩过坑：
 *  - 端点搬过家，platform.claude.com 是当前的，console.anthropic.com 留作回落
 *  - User-Agent 不能带 claude-code/，否则会被限流返回 429
 */
class ClaudeAuthClient {

    fun refresh(refreshToken: String): Result<TokenBundle> {
        if (refreshToken.isBlank()) {
            return Result.failure(IllegalArgumentException("没有刷新令牌，无法自动续期"))
        }
        var lastError: Throwable = IllegalStateException("续期失败")
        for (endpoint in ENDPOINTS) {
            Log.w(TAG, "refresh: trying $endpoint")
            val attempt = attempt(endpoint, refreshToken)
            attempt.onSuccess {
                Log.w(TAG, "refresh: OK via $endpoint, expiresAt=${it.expiresAt}, newRefreshToken=${it.refreshToken != null}")
                return attempt
            }
            lastError = attempt.exceptionOrNull() ?: lastError
            Log.w(TAG, "refresh: FAILED via $endpoint -> ${lastError.message}")
        }
        return Result.failure(lastError)
    }

    private fun attempt(endpoint: String, refreshToken: String): Result<TokenBundle> = runCatching {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("accept", "application/json")
            setRequestProperty("User-Agent", TOKEN_USER_AGENT)
        }

        val body = JSONObject().apply {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
            put("client_id", CLIENT_ID)
        }.toString()

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()

        Log.w(TAG, "refresh: HTTP $code from ${shortHost(endpoint)}, body=${text.take(300)}")
        if (code !in 200..299) {
            throw IllegalStateException("续期失败 HTTP $code (${shortHost(endpoint)}): ${text.take(160)}")
        }

        val json = JSONObject(text)
        val access = json.optString("access_token")
        if (access.isBlank()) throw IllegalStateException("响应里没有 access_token")

        val expiresIn = json.optLong("expires_in", 0L)
        val expiresAt = when {
            expiresIn > 0L -> System.currentTimeMillis() + expiresIn * 1000L
            json.has("expires_at") -> normalize(json.optLong("expires_at", 0L))
            else -> 0L
        }

        TokenBundle(
            accessToken = access,
            refreshToken = json.optString("refresh_token").ifBlank { null },
            expiresAt = expiresAt,
        )
    }

    private fun normalize(value: Long): Long = when {
        value <= 0L -> 0L
        value < 100_000_000_000L -> value * 1000L
        else -> value
    }

    private fun shortHost(url: String): String =
        runCatching { URL(url).host }.getOrDefault(url)

    private companion object {
        const val TAG = "ClaudeUsageAuth"
        const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val TOKEN_USER_AGENT = "anthropic"
        val ENDPOINTS = listOf(
            "https://platform.claude.com/v1/oauth/token",
            "https://console.anthropic.com/v1/oauth/token",
        )
    }
}
