package com.usagecompanion.claude.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearPreferenceSync(context: Context) {
    private val appContext = context.applicationContext

    fun publish(
        snapshot: WearSnapshot,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        val request = PutDataMapRequest.create(PATH_USAGE_PREFERENCES).apply {
            dataMap.putBoolean(WearSnapshotStore.KEY_TILE_SHOWS_SEVEN_DAY, snapshot.tileShowsSevenDay)
            dataMap.putBoolean(WearSnapshotStore.KEY_HIGH_USAGE_ALERTS, snapshot.highUsageAlertsEnabled)
            dataMap.putLong(WearSnapshotStore.KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(appContext)
            .putDataItem(request)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure() }
    }

    companion object {
        const val PATH_USAGE_PREFERENCES = "/usage_preferences"
    }
}
