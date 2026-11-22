package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TacticalDarkColorScheme = darkColorScheme(
    primary = SignalOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1F12),
    onPrimaryContainer = Color(0xFFFFC8B4),
    secondary = CyanTelemetry,
    onSecondary = ObsidianBg,
    secondaryContainer = Color(0xFF0C3B45),
    onSecondaryContainer = Color(0xFFC2FAFF),
    tertiary = EmeraldVector,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = IceText,
    surface = GraphiteSurface,
    onSurface = IceText,
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = SteelText,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = GraphiteSurface,
    surfaceContainer = GraphiteElevated,
    surfaceContainerHigh = GraphiteHighlight,
    outline = HudBorder,
    outlineVariant = Color(0xFF334155),
    error = DangerRed,
    onError = ObsidianBg
)

private val TacticalLightColorScheme = lightColorScheme(
    primary = SignalOrangeLightUi,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFED7AA),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = CyanTelemetryLightUi,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBAE6FD),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = EmeraldVectorLightUi,
    onTertiary = Color.White,
    background = PaperSlateBg,
    onBackground = InkText,
    surface = CrispWhiteSurface,
    onSurface = InkText,
    surfaceVariant = MistSurface,
    onSurfaceVariant = SlateSecondaryText,
    surfaceContainerLowest = CrispWhiteSurface,
    surfaceContainerLow = PaperSlateBg,
    surfaceContainer = MistSurface,
    surfaceContainerHigh = FrostHighlight,
    outline = LightHudBorder,
    outlineVariant = Color(0xFF94A3B8),
    error = DangerRedLightUi,
    onError = Color.White
)

@Composable
fun TrackForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TacticalDarkColorScheme else TacticalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
