package dev.opengdrive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315C49),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F1CB),
    secondary = Color(0xFF52635A),
    background = Color(0xFFF8FAF5),
    surface = Color(0xFFF8FAF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D5B0),
    primaryContainer = Color(0xFF194633),
    secondary = Color(0xFFB9CCC0),
)

@Composable
fun OpenGDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
