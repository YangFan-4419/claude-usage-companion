package com.usagecompanion.claude.data

import android.content.Context
import android.util.Log
import com.usagecompanion.claude.widget.UsageWidgetProvider

class UsageRefreshManager(
    context: Context,
    private val tokenVault: TokenVault = TokenVault(context),
    private val repository: UsageRepository = UsageRepository(context),
    private val usageClient: ClaudeUsageClient = ClaudeUsageClient(),
    private val authClient: ClaudeAuthClient = ClaudeAuthClient(),
    private val wearSyncRepository: WearSyncRepository = WearSyncRepository(context),
    private val usageAlertNotifier: UsageAlertNotifier = UsageAlertNotifier(context),
) {
    private val appContext = context.applicationContext

    /** 最近一次续期失败的原因，用来显示到界面上，不再默默吞掉。 */
    @Volatile
    private var lastRenewError: String? = null

    /** 续期失败的冷却状态。服务端说 try again later，就真的等，而不是每 5 分钟捶一次。 */
    private val backoffPrefs =
        appContext.getSharedPreferences("renew_backoff", Context.MODE_PRIVATE)

    private fun backoffUntil(): Long = backoffPrefs.getLong(KEY_BACKOFF_UNTIL, 0L)

    private fun failureCount(): Int = backoffPrefs.getInt(KEY_FAILURES, 0)

    private fun noteRenewFailure() {
        val failures = (failureCount() + 1).coerceAtMost(MAX_BACKOFF_STEPS)
        val delay = (BASE_BACKOFF_MS shl (failures - 1)).coerceAtMost(MAX_BACKOFF_MS)
        backoffPrefs.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_BACKOFF_UNTIL, System.currentTimeMillis() + delay)
            .apply()
        Log.w(TAG, "renew: backing off ${delay / 60_000} 分钟（连续失败 $failures 次）")
    }

    private fun clearBackoff() {
        backoffPrefs.edit().clear().apply()
    }

    fun currentSnapshot(): UsageSnapshot = repository.currentSnapshot()

    fun tokenStateSnapshot(hasToken: Boolean): UsageSnapshot {
        val snapshot = repository.tokenStateSnapshot(hasToken)
        publish(snapshot)
        return snapshot
    }

    fun updateWatchStyle(style: WatchProgressStyle): UsageSnapshot {
        val snapshot = repository.updateWatchStyle(style)
        publish(snapshot)
        return snapshot
    }

    fun updateTileShowsSevenDay(enabled: Boolean): UsageSnapshot {
        val snapshot = repository.updateTileShowsSevenDay(enabled)
        publish(snapshot)
        return snapshot
    }

    fun updateHighUsageAlerts(enabled: Boolean): UsageSnapshot {
        val snapshot = repository.updateHighUsageAlerts(enabled)
        publish(snapshot)
        return snapshot
    }

    fun refreshIfStale(force: Boolean = false, style: WatchProgressStyle? = null): Result<UsageSnapshot> {
        var token = tokenVault.readToken().orEmpty()
        if (token.isBlank()) {
            return Result.success(tokenStateSnapshot(hasToken = false))
        }

        val current = repository.currentSnapshot()
        val now = System.currentTimeMillis()
        if (!force && current.hasUsage && now - repository.lastRefreshAt() < CACHE_TTL_MS) {
            publish(current)
            return Result.success(current)
        }

        // 快过期就先续，省得白跑一次请求
        lastRenewError = null
        val expiresAt = tokenVault.expiresAt()
        val hasRefresh = tokenVault.hasRefreshToken()
        Log.w(TAG, "refreshIfStale: expiresAt=$expiresAt now=$now hasRefreshToken=$hasRefresh " +
            "expired=${expiresAt in 1 until (now + EXPIRY_MARGIN_MS)}")
        if (expiresAt > 0L && now >= expiresAt - EXPIRY_MARGIN_MS) {
            Log.w(TAG, "refreshIfStale: token near/past expiry, renewing")
            renewAccessToken()?.let { token = it }
        }

        val targetStyle = style ?: current.watchStyle
        var result = usageClient.fetch(token, targetStyle)

        // 服务端说令牌不行（可能过期时间不准），续一次再试
        if (result.exceptionOrNull() is UnauthorizedException) {
            Log.w(TAG, "fetch got 401/403, renewing then retrying")
            val renewed = if (lastRenewError != null) {
                Log.w(TAG, "同一轮里已经失败过，不再重复请求")
                null
            } else {
                renewAccessToken()
            }
            if (renewed != null) {
                result = usageClient.fetch(renewed, targetStyle)
            }
        }

        // 令牌相关的失败，把续期失败的真实原因带出去显示，别只说一句“令牌失效”
        result.exceptionOrNull()?.let { error ->
            if (error is UnauthorizedException) {
                val reason = lastRenewError
                if (reason != null) {
                    return Result.failure(IllegalStateException("自动续期失败: $reason"))
                }
            }
        }

        return result
            .map { refreshed ->
                refreshed.copy(
                    tileShowsSevenDay = current.tileShowsSevenDay,
                    highUsageAlertsEnabled = current.highUsageAlertsEnabled,
                )
            }
            .onSuccess { refreshed ->
                val shouldNotify = !current.isHighUsage() && refreshed.isHighUsage()
                repository.save(refreshed)
                repository.markRefreshedAt(now)
                publish(refreshed)
                if (shouldNotify) {
                    usageAlertNotifier.notifyHighUsage(refreshed)
                }
            }
    }

    /** 续期成功返回新的访问令牌，失败返回 null（调用方继续用旧的，让上层报出真实错误）。 */
    private fun renewAccessToken(): String? {
        val until = backoffUntil()
        val now = System.currentTimeMillis()
        if (now < until) {
            val mins = (until - now) / 60_000 + 1
            Log.w(TAG, "renew: 冷却中，还有 $mins 分钟，跳过不发请求")
            if (lastRenewError == null) {
                lastRenewError = "上次续期被服务端拒绝，${mins} 分钟后再试"
            }
            return null
        }
        val refreshToken = tokenVault.readRefreshToken()
        if (refreshToken == null) {
            lastRenewError = "保存的凭证里没有刷新令牌，请重新粘贴完整 JSON"
            Log.w(TAG, "renew: no refresh token stored")
            return null
        }
        val result = authClient.refresh(refreshToken)
        val bundle = result.getOrNull()
        if (bundle == null) {
            lastRenewError = result.exceptionOrNull()?.message ?: "未知错误"
            Log.w(TAG, "renew: failed -> $lastRenewError")
            noteRenewFailure()
            return null
        }
        lastRenewError = null
        clearBackoff()
        tokenVault.updateTokens(bundle.accessToken, bundle.refreshToken, bundle.expiresAt)
        Log.w(TAG, "renew: success, new expiresAt=${bundle.expiresAt}")
        return bundle.accessToken
    }

    private fun publish(snapshot: UsageSnapshot) {
        UsageWidgetProvider.updateAll(appContext)
        wearSyncRepository.publish(snapshot)
    }

    companion object {
        const val TAG = "ClaudeUsageAuth"
        private const val KEY_BACKOFF_UNTIL = "backoff_until"
        private const val KEY_FAILURES = "failures"
        private const val BASE_BACKOFF_MS = 1_800_000L   // 30 分钟
        private const val MAX_BACKOFF_MS = 21_600_000L   // 6 小时
        private const val MAX_BACKOFF_STEPS = 4
        const val CACHE_TTL_MS = 300_000L
        /** 过期前这么久就提前续，避免卡在边界上。 */
        const val EXPIRY_MARGIN_MS = 300_000L
    }
}
