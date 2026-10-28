package pl.fleet.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandRed = Color(0xFFD32F2F)

/** Jasny, czerwono-biały motyw E-SERVER PRO – niezależny od trybu ciemnego i kolorów tapety. */
private val Colors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E0),
    onPrimaryContainer = Color(0xFF5F0B0B),
    secondary = Color(0xFF455A64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEFF1),
    onSecondaryContainer = Color(0xFF263238),
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF4F4F6),
    surfaceContainerHighest = Color(0xFFEDEDEF),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE3E3E6),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF7A1410),
)

@Composable
fun FleetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
