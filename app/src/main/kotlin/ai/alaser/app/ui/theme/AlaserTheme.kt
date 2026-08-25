package ai.alaser.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

val AlaserAccent = Color(0xFF65E5D5)
val AlaserViolet = Color(0xFFAD8CFF)
val AlaserBackground = Color(0xFF10141D)
val AlaserSurface = Color(0xFF171D29)
val AlaserSurfaceRaised = Color(0xFF202839)

private val DarkColors = darkColorScheme(
    primary = AlaserAccent,
    onPrimary = Color(0xFF00382F),
    secondary = AlaserViolet,
    background = AlaserBackground,
    surface = AlaserSurface,
    surfaceVariant = AlaserSurfaceRaised,
    onBackground = Color(0xFFE8ECF3),
    onSurface = Color(0xFFE8ECF3),
    outline = Color(0xFF526070),
    error = Color(0xFFFF8686),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF007A70),
    secondary = Color(0xFF6548A6),
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EDF3),
)

val AlaserCodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

private val AlaserTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
)

@Composable
fun AlaserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AlaserTypography,
        content = content,
    )
}
