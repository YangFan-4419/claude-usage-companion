package com.usagecompanion.claude.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.WearableListenerService

class UsageDataListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            val item = event.dataItem
            if (event.type == DataEvent.TYPE_CHANGED && item.uri.path == WearSnapshotStore.PATH_USAGE_SNAPSHOT) {
                WearSnapshotStore(this).save(DataMapItem.fromDataItem(item).dataMap)
                TileService.getUpdater(this).requestUpdate(UsageTileService::class.java)
            }
        }
    }
}
