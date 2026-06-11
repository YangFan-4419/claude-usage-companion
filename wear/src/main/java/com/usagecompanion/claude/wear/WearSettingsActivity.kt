package com.usagecompanion.claude.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText

class WearSettingsActivity : ComponentActivity() {
    private lateinit var store: WearSnapshotStore
    private lateinit var sync: WearPreferenceSync
    private var syncStatus = "Synced"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WearSnapshotStore(this)
        sync = WearPreferenceSync(this)
        WearSnapshotRequester(this).request()
        render()
    }

    private fun render() {
        setContent {
            WearSettingsScreen(
                snapshot = store.read(),
                syncStatus = syncStatus,
                onTileShowsSevenDayChange = { enabled ->
                    updatePreferences(tileShowsSevenDay = enabled)
                },
                onHighUsageAlertsChange = { enabled ->
                    updatePreferences(highUsageAlertsEnabled = enabled)
                },
            )
        }
    }

    private fun updatePreferences(
        tileShowsSevenDay: Boolean? = null,
        highUsageAlertsEnabled: Boolean? = null,
    ) {
        val current = store.read()
        val next = store.updatePreferences(
            tileShowsSevenDay = tileShowsSevenDay ?: current.tileShowsSevenDay,
            highUsageAlertsEnabled = highUsageAlertsEnabled ?: current.highUsageAlertsEnabled,
        )
        syncStatus = "Sync pending"
        render()
        sync.publish(
            next,
            onSuccess = {
                syncStatus = "Synced to phone"
                runOnUiThread { render() }
            },
            onFailure = {
                syncStatus = "Sync failed"
                runOnUiThread { render() }
            },
        )
    }
}

@Composable
private fun WearSettingsScreen(
    snapshot: WearSnapshot,
    syncStatus: String,
    onTileShowsSevenDayChange: (Boolean) -> Unit,
    onHighUsageAlertsChange: (Boolean) -> Unit,
) {
    ClaudeWearTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            TimeText()
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentPadding = PaddingValues(top = 52.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                autoCentering = null,
            ) {
                item {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item { Spacer(modifier = Modifier.height(2.dp)) }
                item {
                    SwitchButton(
                        modifier = Modifier.width(156.dp),
                        checked = snapshot.tileShowsSevenDay,
                        onCheckedChange = onTileShowsSevenDayChange,
                        label = { Text("Tile 7d") },
                    )
                }
                item {
                    SwitchButton(
                        modifier = Modifier.width(156.dp),
                        checked = snapshot.highUsageAlertsEnabled,
                        onCheckedChange = onHighUsageAlertsChange,
                        label = { Text("Alerts") },
                    )
                }
                item {
                    Text(
                        text = syncStatus,
                        color = Color(0xFFC9C3BF),
                    )
                }
            }
        }
    }
}
