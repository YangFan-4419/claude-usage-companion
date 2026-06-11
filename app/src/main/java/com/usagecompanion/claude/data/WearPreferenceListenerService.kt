package com.usagecompanion.claude.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.usagecompanion.claude.widget.UsageWidgetProvider

class WearPreferenceListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            val item = event.dataItem
            if (event.type == DataEvent.TYPE_CHANGED && item.uri.path == WearSyncRepository.PATH_USAGE_PREFERENCES) {
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                val (tileShowsSevenDay, highUsageAlertsEnabled) = WearSyncRepository.preferencesFrom(dataMap)
                val repository = UsageRepository(this)
                var snapshot = repository.updateTileShowsSevenDay(tileShowsSevenDay)
                snapshot = repository.updateHighUsageAlerts(highUsageAlertsEnabled)
                WearSyncRepository(this).publish(snapshot)
                UsageWidgetProvider.updateAll(this)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearSyncRepository.PATH_REQUEST_USAGE_SNAPSHOT) {
            WearSyncRepository(this).publish(UsageRepository(this).currentSnapshot())
        }
    }
}
