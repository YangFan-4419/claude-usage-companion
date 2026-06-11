package com.usagecompanion.claude.data

import android.content.Context
import com.usagecompanion.claude.widget.UsageWidgetProvider

class UsageRefreshManager(
    context: Context,
    private val tokenVault: TokenVault = TokenVault(context),
    private val repository: UsageRepository = UsageRepository(context),
    private val usageClient: ClaudeUsageClient = ClaudeUsageClient(),
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
        val token = tokenVault.readToken().orEmpty()
        if (token.isBlank()) {
            return Result.success(tokenStateSnapshot(hasToken = false))
        }

        val current = repository.currentSnapshot()
        val now = System.currentTimeMillis()
        if (!force && current.hasUsage && now - repository.lastRefreshAt() < CACHE_TTL_MS) {
            publish(current)
            return Result.success(current)
        }

        val targetStyle = style ?: current.watchStyle
        return usageClient.fetch(token, targetStyle)
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

    private fun publish(snapshot: UsageSnapshot) {
        UsageWidgetProvider.updateAll(appContext)
        wearSyncRepository.publish(snapshot)
    }

    companion object {
        const val CACHE_TTL_MS = 60_000L
    }
}
