package com.usagecompanion.claude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.usagecompanion.claude.data.TokenVault
import com.usagecompanion.claude.data.UsageRepository
import com.usagecompanion.claude.data.UsageSnapshot
import com.usagecompanion.claude.data.WatchProgressStyle
import com.usagecompanion.claude.data.WearSyncRepository
import com.usagecompanion.claude.data.ClaudeUsageClient
import com.usagecompanion.claude.widget.UsageWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenVault = TokenVault(this)
        val repository = UsageRepository(this)
        val wearSyncRepository = WearSyncRepository(this)
        val usageClient = ClaudeUsageClient()

        setContent {
            ClaudeUsageApp(
                tokenVault = tokenVault,
                repository = repository,
                usageClient = usageClient,
                onSnapshotChanged = {
                    UsageWidgetProvider.updateAll(this)
                    wearSyncRepository.publish(repository.currentSnapshot())
                },
            )
        }
    }
}

@Composable
private fun ClaudeUsageApp(
    tokenVault: TokenVault,
    repository: UsageRepository,
    usageClient: ClaudeUsageClient,
    onSnapshotChanged: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    var snapshot by remember { mutableStateOf(repository.currentSnapshot()) }
    var saveState by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshUsage(currentToken: String, style: WatchProgressStyle) {
        if (currentToken.isBlank()) return
        isRefreshing = true
        saveState = "Refreshing usage..."
        val result = withContext(Dispatchers.IO) {
            usageClient.fetch(currentToken, style)
        }
        result
            .onSuccess { refreshed ->
                repository.save(refreshed)
                snapshot = refreshed
                saveState = "Usage refreshed."
                onSnapshotChanged()
            }
            .onFailure { error ->
                saveState = error.message ?: "Usage refresh failed"
            }
        isRefreshing = false
    }

    LaunchedEffect(Unit) {
        val storedToken = tokenVault.readToken().orEmpty()
        token = storedToken
        val initialSnapshot = repository.tokenStateSnapshot(hasToken = storedToken.isNotBlank())
        snapshot = initialSnapshot
        onSnapshotChanged()
        if (storedToken.isNotBlank()) {
            refreshUsage(storedToken, initialSnapshot.watchStyle)
        }
    }

    MaterialTheme(colorScheme = AppColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(snapshot)
                if (snapshot.hasUsage) {
                    UsageBarsCard(snapshot)
                } else if (token.isBlank()) {
                    SetupRequiredCard()
                } else {
                    WaitingForUsageCard()
                }
                PhoneSettingsPanel(
                    token = token,
                    watchStyle = snapshot.watchStyle,
                    saveState = saveState,
                    onTokenChange = { token = it },
                    onStyleChange = { style ->
                        snapshot = repository.updateWatchStyle(style)
                        saveState = "Watch style synced."
                        onSnapshotChanged()
                    },
                    onSave = {
                        tokenVault.saveToken(token)
                        snapshot = repository.tokenStateSnapshot(hasToken = token.isNotBlank())
                        saveState = if (token.isBlank()) {
                            "Add a token on this phone to sync usage."
                        } else {
                            "Token saved. Refreshing usage..."
                        }
                        onSnapshotChanged()
                        if (token.isNotBlank()) {
                            scope.launch {
                                refreshUsage(token, snapshot.watchStyle)
                            }
                        }
                    },
                    onRefresh = {
                        scope.launch {
                            refreshUsage(token, snapshot.watchStyle)
                        }
                    },
                    isRefreshing = isRefreshing,
                )
            }
        }
    }
}

@Composable
private fun Header(snapshot: UsageSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Claude Usage",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = snapshot.planLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        StatusDot(snapshot.statusColor())
    }
}

@Composable
private fun SetupRequiredCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Set up OAuth on your phone",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "The watch and Tile never ask for your token. Save it on this phone, then the latest usage snapshot can be sent to your widget and Wear OS devices.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WaitingForUsageCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Token saved",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Usage values will appear after the phone refreshes real Claude usage. Until then, the watch shows a waiting state.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun UsageBarsCard(snapshot: UsageSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Usage",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            UsageProgressRow("5-hour window", snapshot.fiveHourPercent, snapshot.fiveHourResetLabel)
            UsageProgressRow("7-day window", snapshot.sevenDayPercent, snapshot.sevenDayResetLabel)
        }
    }
}

@Composable
private fun UsageProgressRow(
    label: String,
    percent: Int,
    resetLabel: String,
) {
    val color = usageColor(percent)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Resets in $resetLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "${percent}%",
                color = color,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun PhoneSettingsPanel(
    token: String,
    watchStyle: WatchProgressStyle,
    saveState: String,
    onTokenChange: (String) -> Unit,
    onStyleChange: (WatchProgressStyle) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Phone settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Stored only on this device") },
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(
                text = "Watch progress style",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchProgressStyle.entries.forEach { style ->
                    FilterChip(
                        selected = watchStyle == style,
                        onClick = { onStyleChange(style) },
                        label = { Text(style.label) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSave) {
                    Text("Save on phone")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onRefresh,
                    enabled = token.isNotBlank() && !isRefreshing,
                ) {
                    Text(if (isRefreshing) "Refreshing" else "Refresh usage")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = saveState,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun UsageSnapshot.statusColor(): Color =
    when {
        !hasUsage -> MaterialTheme.colorScheme.outline
        fiveHourPercent > 100 || sevenDayPercent > 100 -> ClaudeRed
        fiveHourPercent >= 90 || sevenDayPercent >= 90 -> ClaudeDeepOrange
        fiveHourPercent >= 70 || sevenDayPercent >= 70 -> ClaudeYellow
        else -> ClaudeOrange
    }

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFFD97250),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8D8CB),
    onPrimaryContainer = Color(0xFF3E170B),
    secondary = Color(0xFF8A4B35),
    onSecondary = Color.White,
    tertiary = Color(0xFFFACC15),
    error = Color(0xFFEF4444),
    background = Color(0xFFFAF7F4),
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF1E6E0),
    onSurfaceVariant = Color(0xFF6F5C54),
    outline = Color(0xFF9C8D86),
)

private fun usageColor(percent: Int): Color =
    when {
        percent > 100 -> ClaudeRed
        percent >= 90 -> ClaudeDeepOrange
        percent >= 70 -> ClaudeYellow
        else -> ClaudeOrange
    }

private val ClaudeOrange = Color(0xFFD97250)
private val ClaudeYellow = Color(0xFFFACC15)
private val ClaudeDeepOrange = Color(0xFFF97316)
private val ClaudeRed = Color(0xFFEF4444)
private val ClaudeBlack = Color(0xFF050505)
private val ClaudeTrack = Color(0xFF2B2B2B)
private val ClaudeMuted = Color(0xFFB8B1AD)
