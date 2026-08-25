package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScreenHostColorScheme =
    darkColorScheme(
        primary = CyberCyan,
        onPrimary = Color(0xFF041E2D),
        primaryContainer = Color(0xFF03476E),
        onPrimaryContainer = Color(0xFFC7F3FF),
        secondary = SkyGlow,
        onSecondary = Color(0xFF061A2B),
        secondaryContainer = Color(0xFF0A2B45),
        onSecondaryContainer = Color(0xFFBAE6FD),
        tertiary = NeonGreen,
        onTertiary = Color(0xFF012411),
        tertiaryContainer = Color(0xFF064E3B),
        onTertiaryContainer = Color(0xFFA7F3D0),
        background = DarkBackground,
        onBackground = TextPrimary,
        surface = DarkSurface,
        onSurface = TextPrimary,
        surfaceVariant = DarkSurfaceElevated,
        onSurfaceVariant = TextSecondary,
        outline = DarkBorder,
        error = CrimsonGlow,
        onError = Color.White
    )

@Composable
fun ScreenHostTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ScreenHostColorScheme,
        typography = Typography,
        content = content
    )
}

