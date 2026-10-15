package com.example.englishwords

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Success = Color(0xFF16A34A)
val Danger = Color(0xFFE5484D)
val Amber = Color(0xFFF59E0B)
val Teal = Color(0xFF14B8A6)
val Indigo = Color(0xFF5B5BD6)

/** Фирменный градиент (используется на главной, в карточках знакомства). */
val BrandBrush = Brush.linearGradient(listOf(Color(0xFF5B5BD6), Color(0xFF8B5CF6)))

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E5FF),
    onPrimaryContainer = Color(0xFF14146B),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF6F2),
    onSecondaryContainer = Color(0xFF0B3B35),
    tertiary = Amber,
    background = Color(0xFFF5F5FB),
    onBackground = Color(0xFF16161F),
    surface = Color.White,
    onSurface = Color(0xFF16161F),
    surfaceVariant = Color(0xFFECECF6),
    onSurfaceVariant = Color(0xFF5C5C70),
    outline = Color(0xFF8A8AA0),
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D9DFF),
    onPrimary = Color(0xFF14146B),
    primaryContainer = Color(0xFF2E2E7A),
    onPrimaryContainer = Color(0xFFE6E5FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF053B34),
    secondaryContainer = Color(0xFF12453E),
    onSecondaryContainer = Color(0xFFDDF6F2),
    tertiary = Amber,
    background = Color(0xFF0F0F17),
    onBackground = Color(0xFFEDEDF7),
    surface = Color(0xFF1A1A26),
    onSurface = Color(0xFFEDEDF7),
    surfaceVariant = Color(0xFF262636),
    onSurfaceVariant = Color(0xFFB4B4C8),
    outline = Color(0xFF7A7A90),
    error = Danger
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val base = Typography()
private val AppTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
