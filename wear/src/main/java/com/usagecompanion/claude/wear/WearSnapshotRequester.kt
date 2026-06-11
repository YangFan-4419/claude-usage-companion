package com.usagecompanion.claude.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable

class WearSnapshotRequester(context: Context) {
    private val appContext = context.applicationContext

    fun request() {
        val nodeClient = Wearable.getNodeClient(appContext)
        val messageClient = Wearable.getMessageClient(appContext)
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, PATH_REQUEST_USAGE_SNAPSHOT, ByteArray(0))
                }
            }
    }

    companion object {
        const val PATH_REQUEST_USAGE_SNAPSHOT = "/request_usage_snapshot"
    }
}
