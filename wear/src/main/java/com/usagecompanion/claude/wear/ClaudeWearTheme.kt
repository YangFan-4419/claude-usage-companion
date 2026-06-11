package com.usagecompanion.claude.wear

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val ClaudeOrange = Color(0xFFD97250)
val ClaudeYellow = Color(0xFFFACC15)
val ClaudeDeepOrange = Color(0xFFF97316)
val ClaudeRed = Color(0xFFEF4444)

private val ClaudeWearColorScheme = ColorScheme(
    primary = ClaudeOrange,
    primaryDim = Color(0xFFB85F43),
    primaryContainer = Color(0xFF4A2418),
    onPrimary = Color.Black,
    onPrimaryContainer = Color(0xFFFFD8C9),
    secondary = Color(0xFFE7B8A4),
    secondaryDim = Color(0xFFC89B88),
    secondaryContainer = Color(0xFF3B2821),
    onSecondary = Color.Black,
    onSecondaryContainer = Color(0xFFFFD8C9),
    tertiary = ClaudeYellow,
    tertiaryDim = Color(0xFFD8A500),
    tertiaryContainer = Color(0xFF3D3000),
    onTertiary = Color.Black,
    onTertiaryContainer = Color(0xFFFFE9A6),
    surfaceContainerLow = Color(0xFF171412),
    surfaceContainer = Color(0xFF211D1A),
    surfaceContainerHigh = Color(0xFF2B2521),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFC9C3BF),
    outline = Color(0xFF8F8782),
    outlineVariant = Color(0xFF3C3531),
    background = Color.Black,
    onBackground = Color.White,
    error = ClaudeRed,
    errorDim = Color(0xFFCC3636),
    errorContainer = Color(0xFF4B1618),
    onError = Color.Black,
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun ClaudeWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClaudeWearColorScheme,
        content = content,
    )
}

fun usageAccent(percent: Int): Color =
    when {
        percent > 100 -> ClaudeRed
        percent >= 90 -> ClaudeDeepOrange
        percent >= 70 -> ClaudeYellow
        else -> ClaudeOrange
    }
