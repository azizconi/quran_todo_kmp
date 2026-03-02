package tj.app.quran_todo.common.theme

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private data class NeutralTokens(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val disabled: Color,
    val shadow: Color,
)

private data class AccentTokens(
    val primary: Color,
    val primaryWeak: Color,
    val primaryPressed: Color,
)

data class ExtendedColors(
    val surfaceAlt: Color,
    val border: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val disabled: Color,
    val shadow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val primaryWeak: Color,
    val primaryPressed: Color,
)

private val LightNeutral = NeutralTokens(
    bg = Color(0xFFF7F7F5),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF2F3F1),
    border = Color(0xFFE6E7E3),
    textPrimary = Color(0xFF101113),
    textSecondary = Color(0xFF5B5E66),
    textTertiary = Color(0xFF7B7F89),
    disabled = Color(0xFFB8BCC6),
    shadow = Color(0x14000000),
)

private val DarkNeutral = NeutralTokens(
    bg = Color(0xFF0F1012),
    surface = Color(0xFF15171A),
    surfaceAlt = Color(0xFF1C1F24),
    border = Color(0xFF2A2E35),
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFFB7BCC6),
    textTertiary = Color(0xFF8F96A3),
    disabled = Color(0xFF5B616D),
    shadow = Color(0x66000000),
)

private val SemanticSuccess = Color(0xFF22C55E)
private val SemanticWarning = Color(0xFFF59E0B)
private val SemanticDanger = Color(0xFFEF4444)
private val SemanticInfo = Color(0xFF3B82F6)

private fun accentFor(palette: ThemePalette): AccentTokens = when (palette) {
    ThemePalette.SAND -> AccentTokens(
        primary = Color(0xFFC9A227),
        primaryWeak = Color(0xFFF1E4B8),
        primaryPressed = Color(0xFFA8841D),
    )
    ThemePalette.OCEAN -> AccentTokens(
        primary = Color(0xFF1E88E5),
        primaryWeak = Color(0xFFCFE6FB),
        primaryPressed = Color(0xFF1569B5),
    )
    ThemePalette.FOREST -> AccentTokens(
        primary = Color(0xFF2E7D32),
        primaryWeak = Color(0xFFCFE8D0),
        primaryPressed = Color(0xFF215A24),
    )
}

private fun materialPalette(
    mode: ThemeMode,
    palette: ThemePalette,
): Pair<Colors, ExtendedColors> {
    val neutral = if (mode == ThemeMode.DARK) DarkNeutral else LightNeutral
    val accent = accentFor(palette)
    val colors = if (mode == ThemeMode.DARK) {
        darkColors(
            primary = accent.primary,
            primaryVariant = accent.primaryPressed,
            secondary = accent.primaryWeak,
            secondaryVariant = accent.primaryPressed,
            background = neutral.bg,
            surface = neutral.surface,
            error = SemanticDanger,
            onPrimary = Color.White,
            onSecondary = neutral.textPrimary,
            onBackground = neutral.textPrimary,
            onSurface = neutral.textPrimary,
            onError = Color.White,
        )
    } else {
        lightColors(
            primary = accent.primary,
            primaryVariant = accent.primaryPressed,
            secondary = accent.primaryWeak,
            secondaryVariant = accent.primaryPressed,
            background = neutral.bg,
            surface = neutral.surface,
            error = SemanticDanger,
            onPrimary = Color.White,
            onSecondary = neutral.textPrimary,
            onBackground = neutral.textPrimary,
            onSurface = neutral.textPrimary,
            onError = Color.White,
        )
    }

    val extended = ExtendedColors(
        surfaceAlt = neutral.surfaceAlt,
        border = neutral.border,
        textSecondary = neutral.textSecondary,
        textTertiary = neutral.textTertiary,
        disabled = neutral.disabled,
        shadow = neutral.shadow,
        success = SemanticSuccess,
        warning = SemanticWarning,
        danger = SemanticDanger,
        info = SemanticInfo,
        primaryWeak = accent.primaryWeak,
        primaryPressed = accent.primaryPressed,
    )

    return colors to extended
}

private val appTypography = Typography(
    h4 = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
    ),
    h5 = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    h6 = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    subtitle1 = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    body1 = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    body2 = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    caption = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    button = TextStyle(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        surfaceAlt = Color(0xFFF2F3F1),
        border = Color(0xFFE6E7E3),
        textSecondary = Color(0xFF5B5E66),
        textTertiary = Color(0xFF7B7F89),
        disabled = Color(0xFFB8BCC6),
        shadow = Color(0x14000000),
        success = SemanticSuccess,
        warning = SemanticWarning,
        danger = SemanticDanger,
        info = SemanticInfo,
        primaryWeak = Color(0xFFF1E4B8),
        primaryPressed = Color(0xFFA8841D),
    )
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current

val Colors.mutedText: Color
    @Composable
    get() = MaterialTheme.extendedColors.textSecondary

val Colors.faintText: Color
    @Composable
    get() = MaterialTheme.extendedColors.textTertiary

val Colors.softSurface: Color
    @Composable
    get() = MaterialTheme.extendedColors.surfaceAlt

val Colors.softSurfaceStrong: Color
    @Composable
    get() = lerp(surface, onSurface, if (isLight) 0.12f else 0.22f)

val Colors.subtleBorder: Color
    @Composable
    get() = MaterialTheme.extendedColors.border

val Colors.progressTrack: Color
    @Composable
    get() = lerp(surface, onSurface, if (isLight) 0.1f else 0.2f)

fun Colors.tintedSurface(tint: Color, emphasis: Float = 0.16f): Color {
    val adjusted = if (isLight) emphasis else emphasis + 0.08f
    return lerp(surface, tint, adjusted.coerceIn(0f, 1f))
}

@Composable
fun AppTheme(
    mode: ThemeMode,
    palette: ThemePalette,
    content: @Composable () -> Unit,
) {
    val (colors, extended) = materialPalette(mode, palette)
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colors = colors,
            typography = appTypography,
            content = content
        )
    }
}
