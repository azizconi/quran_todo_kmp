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
)

private data class AccentTokens(
    val primary: Color,
    val primaryWeak: Color,
    val primaryPressed: Color,
    val shadow: Color,
)

data class ExtendedColors(
    val surfaceAlt: Color,
    val libraryControlSurface: Color,
    val libraryControlBorder: Color,
    val surahStatusNotStartedSurface: Color,
    val surahStatusLearningSurface: Color,
    val surahStatusLearnedSurface: Color,
    val utilitySurface: Color,
    val utilityBorder: Color,
    val heroSurface: Color,
    val heroAccentGlow: Color,
    val selectionFill: Color,
    val selectionBorder: Color,
    val readingBackground: Color,
    val readingSurface: Color,
    val readingMutedSurface: Color,
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
    bg = Color(0xFFF7F8F5),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEEF2ED),
    border = Color(0xFFDDE3DF),
    textPrimary = Color(0xFF101513),
    textSecondary = Color(0xFF5F6965),
    textTertiary = Color(0xFF7A8581),
    disabled = Color(0xFFB8BCC6),
)

private val DarkNeutral = NeutralTokens(
    // A warm charcoal, rather than a near-black green. It keeps Arabic text
    // comfortable to read for long sessions and gives surfaces enough depth.
    bg = Color(0xFF121815),
    surface = Color(0xFF1A211D),
    surfaceAlt = Color(0xFF232C26),
    border = Color(0xFF35413A),
    textPrimary = Color(0xFFF0F5F1),
    textSecondary = Color(0xFFB7C1BA),
    textTertiary = Color(0xFF909B94),
    disabled = Color(0xFF5D6962),
)

private val SemanticSuccess = Color(0xFF257E55)
private val SemanticWarning = Color(0xFF9A6B22)
private val SemanticDanger = Color(0xFFB25757)
private val SemanticInfo = Color(0xFF2F7D9E)
private val DarkSemanticSuccess = Color(0xFF68C99F)
private val DarkSemanticWarning = Color(0xFFE0B466)
private val DarkSemanticDanger = Color(0xFFF08A8A)
private val DarkSemanticInfo = Color(0xFF6FC1E3)

private fun accentFor(
    palette: ThemePalette,
    mode: ThemeMode,
): AccentTokens = when (palette) {
    ThemePalette.SAND -> AccentTokens(
        primary = if (mode == ThemeMode.DARK) Color(0xFFD8BE6C) else Color(0xFF8F7424),
        primaryWeak = if (mode == ThemeMode.DARK) Color(0xFF3D351E) else Color(0xFFEFE5C7),
        primaryPressed = if (mode == ThemeMode.DARK) Color(0xFFE8D184) else Color(0xFF6F5A1C),
        shadow = if (mode == ThemeMode.DARK) Color(0x448F7424) else Color(0x228F7424),
    )
    ThemePalette.OCEAN -> AccentTokens(
        primary = if (mode == ThemeMode.DARK) Color(0xFF84C5D2) else Color(0xFF286F7D),
        primaryWeak = if (mode == ThemeMode.DARK) Color(0xFF17323A) else Color(0xFFD6ECEF),
        primaryPressed = if (mode == ThemeMode.DARK) Color(0xFFA1D8E1) else Color(0xFF1F5661),
        shadow = if (mode == ThemeMode.DARK) Color(0x44286F7D) else Color(0x22286F7D),
    )
    ThemePalette.FOREST -> AccentTokens(
        primary = if (mode == ThemeMode.DARK) Color(0xFF8DD4B4) else Color(0xFF1F6F5B),
        primaryWeak = if (mode == ThemeMode.DARK) Color(0xFF1C3A2E) else Color(0xFFDDECE6),
        primaryPressed = if (mode == ThemeMode.DARK) Color(0xFFABE3C8) else Color(0xFF165546),
        shadow = if (mode == ThemeMode.DARK) Color(0x441F6F5B) else Color(0x221F6F5B),
    )
}

private fun materialPalette(
    mode: ThemeMode,
    palette: ThemePalette,
): Pair<Colors, ExtendedColors> {
    val neutral = if (mode == ThemeMode.DARK) DarkNeutral else LightNeutral
    val accent = accentFor(palette, mode)
    val colors = if (mode == ThemeMode.DARK) {
        darkColors(
            primary = accent.primary,
            primaryVariant = accent.primaryPressed,
            secondary = accent.primaryWeak,
            secondaryVariant = accent.primaryPressed,
            background = neutral.bg,
            surface = neutral.surface,
            error = DarkSemanticDanger,
            onPrimary = LightNeutral.textPrimary,
            onSecondary = neutral.textPrimary,
            onBackground = neutral.textPrimary,
            onSurface = neutral.textPrimary,
            onError = LightNeutral.textPrimary,
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
            onError = LightNeutral.textPrimary,
        )
    }

    val extended = ExtendedColors(
        surfaceAlt = neutral.surfaceAlt,
        // These controls are rendered by native UIKit/Material views beside
        // Compose content. Keep their surfaces fully opaque so the host view
        // never leaks a black/transparent backing layer around rounded edges.
        libraryControlSurface = neutral.surface,
        libraryControlBorder = if (mode == ThemeMode.DARK) neutral.border else Color(0xFFE1E7E3),
        surahStatusNotStartedSurface = neutral.surfaceAlt,
        surahStatusLearningSurface = if (mode == ThemeMode.DARK) Color(0xFF19343E) else Color(0xFFE0F0F5),
        surahStatusLearnedSurface = if (mode == ThemeMode.DARK) Color(0xFF183528) else Color(0xFFDDEFE5),
        utilitySurface = neutral.surfaceAlt,
        utilityBorder = neutral.border,
        heroSurface = accent.primaryWeak,
        heroAccentGlow = accent.primary,
        selectionFill = accent.primaryWeak,
        selectionBorder = accent.primary,
        // The reader is a quiet, paper-like surface of its own. In dark mode
        // it is neutral and warm—not a large expanse of green.
        readingBackground = if (mode == ThemeMode.DARK) Color(0xFF171510) else neutral.bg,
        readingSurface = if (mode == ThemeMode.DARK) Color(0xFF201E1A) else neutral.surface,
        readingMutedSurface = if (mode == ThemeMode.DARK) Color(0xFF2A2722) else neutral.surfaceAlt,
        border = neutral.border,
        textSecondary = neutral.textSecondary,
        textTertiary = neutral.textTertiary,
        disabled = neutral.disabled,
        shadow = accent.shadow,
        success = if (mode == ThemeMode.DARK) DarkSemanticSuccess else SemanticSuccess,
        warning = if (mode == ThemeMode.DARK) DarkSemanticWarning else SemanticWarning,
        danger = if (mode == ThemeMode.DARK) DarkSemanticDanger else SemanticDanger,
        info = if (mode == ThemeMode.DARK) DarkSemanticInfo else SemanticInfo,
        primaryWeak = accent.primaryWeak,
        primaryPressed = accent.primaryPressed,
    )

    return colors to extended
}

/**
 * Semantic tokens used by native platform surfaces that sit beside Compose UI.
 * Keeping this derived from [materialPalette] prevents the iOS SwiftUI reader
 * from drifting away from the currently selected Compose palette.
 */
data class NativeReaderThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val readingBackground: Color,
    val readingSurface: Color,
    val readingMutedSurface: Color,
    val border: Color,
    val primary: Color,
    val primaryWeak: Color,
    val onSurface: Color,
    val textSecondary: Color,
    val success: Color,
    val info: Color,
)

/**
 * The small, platform-neutral subset of theme tokens used behind system UI.
 * Platform hosts use it to keep status/navigation areas visually continuous
 * with the currently visible application surface.
 */
data class AppChromeAppearance(
    val backgroundArgb: Long,
    val surfaceArgb: Long,
    val readerBackgroundArgb: Long,
    val readerPlayerSurfaceArgb: Long,
    val primaryArgb: Long,
    val isDark: Boolean,
)

fun nativeReaderThemeColors(
    mode: ThemeMode,
    palette: ThemePalette,
): NativeReaderThemeColors {
    val (colors, extended) = materialPalette(mode, palette)
    return NativeReaderThemeColors(
        background = colors.background,
        surface = colors.surface,
        surfaceAlt = extended.surfaceAlt,
        readingBackground = extended.readingBackground,
        readingSurface = extended.readingSurface,
        readingMutedSurface = extended.readingMutedSurface,
        border = extended.border,
        primary = colors.primary,
        primaryWeak = extended.primaryWeak,
        onSurface = colors.onSurface,
        textSecondary = extended.textSecondary,
        success = extended.success,
        info = extended.info,
    )
}

fun appChromeAppearance(
    mode: ThemeMode,
    palette: ThemePalette,
): AppChromeAppearance {
    val colors = nativeReaderThemeColors(mode, palette)
    return AppChromeAppearance(
        backgroundArgb = colors.background.toArgbLong(),
        surfaceArgb = colors.surface.toArgbLong(),
        readerBackgroundArgb = colors.readingBackground.toArgbLong(),
        readerPlayerSurfaceArgb = colors.readingSurface.toArgbLong(),
        primaryArgb = colors.primary.toArgbLong(),
        isDark = mode == ThemeMode.DARK,
    )
}

private fun Color.toArgbLong(): Long {
    fun channel(value: Float): Long = (value.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    return (channel(alpha) shl 24) or
        (channel(red) shl 16) or
        (channel(green) shl 8) or
        channel(blue)
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
        surfaceAlt = Color(0xFFEEF2ED),
        libraryControlSurface = Color(0xFFFFFFFF),
        libraryControlBorder = Color(0xFFE1E7E3),
        surahStatusNotStartedSurface = Color(0xFFEEF2ED),
        surahStatusLearningSurface = Color(0xFFE0F0F5),
        surahStatusLearnedSurface = Color(0xFFDDEFE5),
        utilitySurface = Color(0xFFEEF2ED),
        utilityBorder = Color(0xFFDDE3DF),
        heroSurface = Color(0xFFDDECE6),
        heroAccentGlow = Color(0xFF7AC7AD),
        selectionFill = Color(0xFFDDECE6),
        selectionBorder = Color(0xFF1F6F5B),
        readingBackground = Color(0xFFF7F8F5),
        readingSurface = Color(0xFFFFFFFF),
        readingMutedSurface = Color(0xFFEEF2ED),
        border = Color(0xFFDDE3DF),
        textSecondary = Color(0xFF5F6965),
        textTertiary = Color(0xFF7A8581),
        disabled = Color(0xFFB8BCC6),
        shadow = Color(0x228F7424),
        success = SemanticSuccess,
        warning = SemanticWarning,
        danger = SemanticDanger,
        info = SemanticInfo,
        primaryWeak = Color(0xFFEFE5C7),
        primaryPressed = Color(0xFF6F5A1C),
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
