package ru.daybyday.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NightColors = darkColorScheme(
    primary = AccentPurple,
    onPrimary = Color(0xFF21143F),
    primaryContainer = Color(0xFF332A4C),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = AccentTeal,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF173D38),
    onSecondaryContainer = Color(0xFFA5F4E3),
    tertiary = AccentBlue,
    background = Color(0xFF101116),
    onBackground = Color(0xFFF3F1F8),
    surface = Color(0xFF1B1D26),
    onSurface = Color(0xFFF3F1F8),
    surfaceVariant = Color(0xFF272A36),
    onSurfaceVariant = Color(0xFFB5B5C6),
    outline = Color(0xFF4B4A5D),
    outlineVariant = Color(0xFF303240),
    error = Color(0xFFFFB4AB),
)
private val DayColors = lightColorScheme(
    primary = Color(0xFF6750B8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE2FF),
    onPrimaryContainer = Color(0xFF2D185F),
    secondary = Color(0xFF087866),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F6EA),
    onSecondaryContainer = Color(0xFF004739),
    tertiary = Color(0xFF3766B5),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF20202C),
    surface = Color.White,
    onSurface = Color(0xFF20202C),
    surfaceVariant = Color(0xFFECEAF3),
    onSurfaceVariant = Color(0xFF666477),
    outline = Color(0xFF7C768B),
    outlineVariant = Color(0xFFE0DDE9),
)
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 76.sp, lineHeight = 80.sp, letterSpacing = (-3).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 39.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun DayByDayTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) NightColors else DayColors, typography = AppTypography, content = content)
}
