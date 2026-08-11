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
    primary = Color(0xFF2F684C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5EAD3),
    onPrimaryContainer = Color(0xFF123824),
    secondary = Color(0xFF52655A),
    secondaryContainer = Color(0xFFD8E8DE),
    background = Color(0xFFF2F5F2),
    surface = Color(0xFFFAFCF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F1),
    surfaceContainer = Color(0xFFE9EDE9),
    surfaceContainerHigh = Color(0xFFE2E7E2),
    surfaceContainerHighest = Color(0xFFDAE1DB),
    onSurface = Color(0xFF1A1C1A),
    onSurfaceVariant = Color(0xFF424843),
    outline = Color(0xFF727A73),
    outlineVariant = Color(0xFFC2C9C3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D5B0),
    primaryContainer = Color(0xFF194633),
    secondary = Color(0xFFB9CCC0),
    secondaryContainer = Color(0xFF354B40),
    background = Color(0xFF111411),
    surface = Color(0xFF181C19),
    surfaceContainerLowest = Color(0xFF0C0F0D),
    surfaceContainerLow = Color(0xFF1D211E),
    surfaceContainer = Color(0xFF222623),
    surfaceContainerHigh = Color(0xFF2C312D),
    surfaceContainerHighest = Color(0xFF373C38),
    onSurface = Color(0xFFE1E4E0),
    onSurfaceVariant = Color(0xFFC1C8C1),
    outline = Color(0xFF8B938C),
    outlineVariant = Color(0xFF414842),
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
