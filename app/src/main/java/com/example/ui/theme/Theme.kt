package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AuroraViolet,
    onPrimary = Color(0xFF160B2E),
    primaryContainer = Color(0xFF3B1D72),
    onPrimaryContainer = Color(0xFFF2E9FF),
    secondary = AuroraCyan,
    onSecondary = Color(0xFF04232B),
    secondaryContainer = Color(0xFF0B4354),
    onSecondaryContainer = Color(0xFFD7FAFF),
    tertiary = AuroraPink,
    onTertiary = Color(0xFF3A0B24),
    tertiaryContainer = Color(0xFF651A40),
    onTertiaryContainer = Color(0xFFFFE2F0),
    background = SpaceBackground,
    onBackground = TextPrimaryDark,
    surface = SpaceSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SpaceSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = SpaceOutline,
    outlineVariant = Color(0xFF252A4C),
    error = AccentRed,
    onError = Color(0xFF2B0710)
)

private val LightColorScheme = lightColorScheme(
    primary = AuroraVioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DFFF),
    onPrimaryContainer = Color(0xFF2A1258),
    secondary = AuroraCyanDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4F7FF),
    onSecondaryContainer = Color(0xFF00323D),
    tertiary = AuroraPinkDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E9),
    onTertiaryContainer = Color(0xFF4A0A2A),
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFF8A7EB3),
    outlineVariant = Color(0xFFD8CDF5),
    error = AccentRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
