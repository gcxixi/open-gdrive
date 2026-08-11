package dev.opengdrive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

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

private val BaseTypography = androidx.compose.material3.Typography()
private val AppFont = FontFamily.SansSerif

private fun TextStyle.appStyle() = copy(
    fontFamily = AppFont,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val AppTypography = androidx.compose.material3.Typography(
    displayLarge = BaseTypography.displayLarge.appStyle(),
    displayMedium = BaseTypography.displayMedium.appStyle(),
    displaySmall = BaseTypography.displaySmall.appStyle(),
    headlineLarge = BaseTypography.headlineLarge.appStyle(),
    headlineMedium = BaseTypography.headlineMedium.appStyle(),
    headlineSmall = BaseTypography.headlineSmall.appStyle(),
    titleLarge = BaseTypography.titleLarge.appStyle(),
    titleMedium = BaseTypography.titleMedium.appStyle(),
    titleSmall = BaseTypography.titleSmall.appStyle(),
    bodyLarge = BaseTypography.bodyLarge.appStyle(),
    bodyMedium = BaseTypography.bodyMedium.appStyle(),
    bodySmall = BaseTypography.bodySmall.appStyle(),
    labelLarge = BaseTypography.labelLarge.appStyle(),
    labelMedium = BaseTypography.labelMedium.appStyle(),
    labelSmall = BaseTypography.labelSmall.appStyle(),
)

@Composable
fun OpenGDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
