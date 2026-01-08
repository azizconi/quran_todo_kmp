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

enum class ThemePalette {
    SAND,
    OCEAN,
    FOREST,
}

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.LIGHT }
val LocalThemeModeSetter = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }
val LocalThemePalette = staticCompositionLocalOf { ThemePalette.SAND }
val LocalThemePaletteSetter = staticCompositionLocalOf<(ThemePalette) -> Unit> { {} }

private fun lightPalette(palette: ThemePalette) = when (palette) {
    ThemePalette.SAND -> lightColors(
        primary = Color(0xFFB06D3B),
        primaryVariant = Color(0xFF8C5327),
        secondary = Color(0xFF2E7D6D),
        secondaryVariant = Color(0xFF1F5E52),
        background = Color(0xFFF6F2EC),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
    )
    ThemePalette.OCEAN -> lightColors(
        primary = Color(0xFF1E88E5),
        primaryVariant = Color(0xFF1565C0),
        secondary = Color(0xFF26A69A),
        secondaryVariant = Color(0xFF1E8E84),
        background = Color(0xFFF3F7FB),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF162029),
        onSurface = Color(0xFF162029),
    )
    ThemePalette.FOREST -> lightColors(
        primary = Color(0xFF2E7D32),
        primaryVariant = Color(0xFF1B5E20),
        secondary = Color(0xFF7B5E57),
        secondaryVariant = Color(0xFF5D4037),
        background = Color(0xFFF2F4EF),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
    )
}

private fun darkPalette(palette: ThemePalette) = when (palette) {
    ThemePalette.SAND -> darkColors(
        primary = Color(0xFFE0A36C),
        primaryVariant = Color(0xFFB06D3B),
        secondary = Color(0xFF6EC5B6),
        background = Color(0xFF12100D),
        surface = Color(0xFF1B1713),
        onPrimary = Color(0xFF2E1A09),
        onSecondary = Color(0xFF00201D),
        onBackground = Color(0xFFEFE7DD),
        onSurface = Color(0xFFEFE7DD),
    )
    ThemePalette.OCEAN -> darkColors(
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
    ThemePalette.FOREST -> darkColors(
        primary = Color(0xFF81C784),
        primaryVariant = Color(0xFF2E7D32),
        secondary = Color(0xFFBCAAA4),
        background = Color(0xFF0F1510),
        surface = Color(0xFF161B17),
        onPrimary = Color(0xFF0B1F0C),
        onSecondary = Color(0xFF2B1C18),
        onBackground = Color(0xFFE3E8E1),
        onSurface = Color(0xFFE3E8E1),
    )
}

@Composable
fun AppTheme(
    mode: ThemeMode,
    palette: ThemePalette,
    content: @Composable () -> Unit,
) {
    val colors = if (mode == ThemeMode.DARK) darkPalette(palette) else lightPalette(palette)
    MaterialTheme(
        colors = colors,
        content = content
    )
}
