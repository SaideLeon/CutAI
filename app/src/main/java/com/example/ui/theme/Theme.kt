package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ImmersiveColorScheme = darkColorScheme(
    primary = ImmersivePrimary,
    onPrimary = ImmersiveOnPrimary,
    primaryContainer = ImmersiveOnPrimary,
    onPrimaryContainer = ImmersivePrimary,
    secondary = ImmersiveSecondary,
    onSecondary = ImmersiveTextPrimary,
    background = ImmersiveBg,
    onBackground = ImmersiveTextPrimary,
    surface = ImmersiveSurface,
    onSurface = ImmersiveTextPrimary,
    surfaceVariant = ImmersiveSecondary,
    onSurfaceVariant = ImmersiveTextSecondary,
    outline = ImmersiveSecondary
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force immersive dark mode
  dynamicColor: Boolean = false, // Disable dynamic colors for custom brand consistency
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) ImmersiveColorScheme else ImmersiveColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
