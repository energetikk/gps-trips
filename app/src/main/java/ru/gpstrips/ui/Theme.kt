package ru.gpstrips.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра: тёмная, «приборная», с бирюзовым акцентом. */
object P {
    val bg = Color(0xFF07090C)
    val surface = Color(0xFF12161C)
    val surface2 = Color(0xFF1A2029)
    val stroke = Color(0xFF232B36)
    val accent = Color(0xFF38E1B0)
    val accent2 = Color(0xFF4C8DFF)
    val warn = Color(0xFFFFB020)
    val danger = Color(0xFFFF5C6C)
    val text = Color(0xFFECF1F7)
    val dim = Color(0xFF8A97A8)
}

private val scheme = darkColorScheme(
    primary = P.accent,
    onPrimary = Color(0xFF04231B),
    secondary = P.accent2,
    onSecondary = Color(0xFF031427),
    background = P.bg,
    onBackground = P.text,
    surface = P.surface,
    onSurface = P.text,
    surfaceVariant = P.surface2,
    onSurfaceVariant = P.dim,
    error = P.danger,
    outline = P.stroke
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, letterSpacing = 0.3.sp)
)

@Composable
fun GpsTripsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
