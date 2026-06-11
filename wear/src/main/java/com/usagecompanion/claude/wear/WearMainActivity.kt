package com.usagecompanion.claude.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        WearSnapshotRequester(this).request()
        render()
    }

    private fun render() {
        val snapshot = WearSnapshotStore(this).read()
        setContent {
            ClaudeWearTheme {
                WearMainScreen(
                    snapshot = snapshot,
                    onSettingsClick = {
                        startActivity(Intent(this, WearSettingsActivity::class.java))
                    },
                )
            }
        }
    }
}

@Composable
private fun WearMainScreen(
    snapshot: WearSnapshot,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TimeText()
        if (snapshot.hasUsage) {
            UsageContent(snapshot)
        } else {
            SetupContent(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 46.dp),
            )
        }
        SettingsButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
            onSettingsClick = onSettingsClick,
        )
    }
}

@Composable
private fun BoxScope.UsageContent(snapshot: WearSnapshot) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = 46.dp),
        text = "Claude Usage",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    PositionedUsageLine(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = 82.dp),
        label = "5-hour",
        percent = snapshot.fiveHourPercent,
    )
    PositionedUsageLine(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = 122.dp),
        label = "7-day",
        percent = snapshot.sevenDayPercent,
    )
}

@Composable
private fun SetupContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Set up on phone",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "OAuth token required",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PositionedUsageLine(
    modifier: Modifier = Modifier,
    label: String,
    percent: Int,
) {
    val accent = usageAccent(percent)
    Column(
        modifier = modifier.width(150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "${percent}% used",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        ProgressTrack(percent = percent, color = accent)
    }
}

@Composable
private fun ProgressTrack(percent: Int, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(color),
        )
    }
}

@Composable
private fun SettingsButton(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .width(112.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onSettingsClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Settings",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
