package tj.app.quran_todo.common.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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

val Colors.mutedText: Color
    get() = lerp(onSurface, surface, if (isLight) 0.34f else 0.26f)

val Colors.faintText: Color
    get() = lerp(onSurface, surface, if (isLight) 0.48f else 0.38f)

val Colors.softSurface: Color
    get() = lerp(surface, onSurface, if (isLight) 0.05f else 0.11f)

val Colors.softSurfaceStrong: Color
    get() = lerp(surface, onSurface, if (isLight) 0.1f else 0.2f)

val Colors.subtleBorder: Color
    get() = lerp(surface, onSurface, if (isLight) 0.14f else 0.25f)

val Colors.progressTrack: Color
    get() = lerp(surface, onSurface, if (isLight) 0.12f else 0.22f)

fun Colors.tintedSurface(tint: Color, emphasis: Float = 0.16f): Color {
    val adjusted = if (isLight) emphasis else emphasis + 0.08f
    return lerp(surface, tint, adjusted.coerceIn(0f, 1f))
}

private fun lightPalette(palette: ThemePalette) = when (palette) {
    ThemePalette.SAND -> lightColors(
        primary = Color(0xFF9C6A38),
        primaryVariant = Color(0xFF7C522A),
        secondary = Color(0xFF2F7E73),
        secondaryVariant = Color(0xFF236458),
        background = Color(0xFFF7F3EE),
        surface = Color(0xFFFFFCFA),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1E242C),
        onSurface = Color(0xFF1E242C),
    )
    ThemePalette.OCEAN -> lightColors(
        primary = Color(0xFF1E6FA8),
        primaryVariant = Color(0xFF155987),
        secondary = Color(0xFF107F76),
        secondaryVariant = Color(0xFF0B655D),
        background = Color(0xFFEFF5FA),
        surface = Color(0xFFFAFCFF),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF15222E),
        onSurface = Color(0xFF15222E),
    )
    ThemePalette.FOREST -> lightColors(
        primary = Color(0xFF2E7B5D),
        primaryVariant = Color(0xFF225D46),
        secondary = Color(0xFF9A6A3E),
        secondaryVariant = Color(0xFF7A532E),
        background = Color(0xFFF1F5F1),
        surface = Color(0xFFFBFEFB),
        onPrimary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A241E),
        onSurface = Color(0xFF1A241E),
    )
}

private fun darkPalette(palette: ThemePalette) = when (palette) {
    ThemePalette.SAND -> darkColors(
        primary = Color(0xFFE1B17B),
        primaryVariant = Color(0xFFC68E57),
        secondary = Color(0xFF6AC3B1),
        background = Color(0xFF101418),
        surface = Color(0xFF1A2026),
        onPrimary = Color(0xFF2F1B09),
        onSecondary = Color(0xFF03211C),
        onBackground = Color(0xFFEAE5DD),
        onSurface = Color(0xFFEAE5DD),
    )
    ThemePalette.OCEAN -> darkColors(
        primary = Color(0xFF79BBE9),
        primaryVariant = Color(0xFF4D93CE),
        secondary = Color(0xFF5EC8BC),
        background = Color(0xFF0D141B),
        surface = Color(0xFF15202B),
        onPrimary = Color(0xFF062033),
        onSecondary = Color(0xFF01241F),
        onBackground = Color(0xFFE4EDF4),
        onSurface = Color(0xFFE4EDF4),
    )
    ThemePalette.FOREST -> darkColors(
        primary = Color(0xFF78C8A6),
        primaryVariant = Color(0xFF4CA980),
        secondary = Color(0xFFE0AA72),
        background = Color(0xFF0E1512),
        surface = Color(0xFF16211C),
        onPrimary = Color(0xFF0E2419),
        onSecondary = Color(0xFF2C1D10),
        onBackground = Color(0xFFE2ECE5),
        onSurface = Color(0xFFE2ECE5),
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
