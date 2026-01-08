package tj.app.quran_todo.common.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    LIGHT,
    DARK,
}

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.LIGHT }
val LocalThemeModeSetter = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }

private val LightPalette = lightColors(
    primary = Color(0xFF1E88E5),
    primaryVariant = Color(0xFF1565C0),
    secondary = Color(0xFF26A69A),
    secondaryVariant = Color(0xFF1E8E84),
    background = Color(0xFFF6F2EC),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkPalette = darkColors(
    primary = Color(0xFF64B5F6),
    primaryVariant = Color(0xFF1E88E5),
    secondary = Color(0xFF4DB6AC),
    background = Color(0xFF0F141A),
    surface = Color(0xFF161B22),
    onPrimary = Color(0xFF001E2F),
    onSecondary = Color(0xFF00201D),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
)

@Composable
fun AppTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val colors = if (mode == ThemeMode.DARK) DarkPalette else LightPalette
    MaterialTheme(
        colors = colors,
        content = content
    )
}
