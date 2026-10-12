package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Charcoal950,
    primaryContainer = Charcoal800,
    onPrimaryContainer = ElectricCyan,
    secondary = NeonBlue,
    onSecondary = TextPrimary,
    secondaryContainer = Charcoal700,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldSuccess,
    onTertiary = Charcoal950,
    background = Charcoal950,
    onBackground = TextPrimary,
    surface = Charcoal900,
    onSurface = TextPrimary,
    surfaceVariant = Charcoal800,
    onSurfaceVariant = TextSecondary,
    outline = Charcoal600,
    outlineVariant = CardBorder,
    error = RoseDanger,
    onError = TextPrimary
)

@Composable
fun LinkoraTheme(
    darkTheme: Boolean = true, // Default to premium dark as requested
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Charcoal950.toArgb()
            window.navigationBarColor = Charcoal950.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
