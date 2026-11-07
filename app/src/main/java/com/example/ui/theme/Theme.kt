package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004E57),
    onPrimaryContainer = Color(0xFF97F0FF),

    secondary = NeonPurple,
    onSecondary = Color(0xFF38006B),
    secondaryContainer = Color(0xFF53118A),
    onSecondaryContainer = Color(0xFFE9D5FF),

    tertiary = AnimeMagenta,
    onTertiary = Color(0xFF4A0025),
    tertiaryContainer = Color(0xFF70003A),
    onTertiaryContainer = Color(0xFFFFD9E4),

    background = Obsidian,
    onBackground = TextPrimary,

    surface = StudioBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,

    outline = BorderDark,
    outlineVariant = SurfaceHighlight,

    error = CrimsonAlert,
    onError = Color.White
)

@Composable
fun AnimeStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AnimeStudioTheme(content = content)
}
