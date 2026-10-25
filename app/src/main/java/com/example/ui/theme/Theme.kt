package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OpenDroidColorScheme = darkColorScheme(
  primary = BuildGreenGlow,
  onPrimary = DeepBlack,
  primaryContainer = SurfaceCard,
  onPrimaryContainer = BuildGreenGlow,
  secondary = PlanPurpleGlow,
  onSecondary = DeepBlack,
  secondaryContainer = SurfaceCard,
  onSecondaryContainer = PlanPurpleGlow,
  tertiary = TerminalCyan,
  onTertiary = DeepBlack,
  background = DeepBlack,
  onBackground = TextPrimary,
  surface = SurfaceDark,
  onSurface = TextPrimary,
  surfaceVariant = SurfaceCard,
  onSurfaceVariant = TextSecondary,
  outline = BorderSubtle,
  outlineVariant = BorderHighlight,
  error = ErrorRed,
  onError = DeepBlack,
)

@Composable
fun OpenDroidTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = OpenDroidColorScheme,
    typography = Typography,
    content = content,
  )
}

@Composable
fun OpenCodeTheme(
  content: @Composable () -> Unit,
) {
  OpenDroidTheme(content)
}
