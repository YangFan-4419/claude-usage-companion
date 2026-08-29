package com.usagecompanion.claude.data

import android.content.Context
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
        val expiresAt = tokenVault.expiresAt()
        if (expiresAt > 0L && now >= expiresAt - EXPIRY_MARGIN_MS) {
            renewAccessToken()?.let { token = it }
        }

        val targetStyle = style ?: current.watchStyle
        var result = usageClient.fetch(token, targetStyle)

        // 服务端说令牌不行（可能过期时间不准），续一次再试
        if (result.exceptionOrNull() is UnauthorizedException) {
            val renewed = renewAccessToken()
            if (renewed != null) {
                result = usageClient.fetch(renewed, targetStyle)
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
        val refreshToken = tokenVault.readRefreshToken() ?: return null
        val bundle = authClient.refresh(refreshToken).getOrNull() ?: return null
        tokenVault.updateTokens(bundle.accessToken, bundle.refreshToken, bundle.expiresAt)
        return bundle.accessToken
    }

    private fun publish(snapshot: UsageSnapshot) {
        UsageWidgetProvider.updateAll(appContext)
        wearSyncRepository.publish(snapshot)
    }

    companion object {
        const val CACHE_TTL_MS = 300_000L
        /** 过期前这么久就提前续，避免卡在边界上。 */
        const val EXPIRY_MARGIN_MS = 300_000L
    }
}
