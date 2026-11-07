package tj.app.quran_todo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import tj.app.quran_todo.common.theme.AppTheme
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemePalette
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.ui.haptics.LocalHaptics
import tj.app.quran_todo.ui.haptics.rememberHaptics
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.rememberSystemReducedMotion

/**
 * Root of the new Android UI. Provides the Qh token sets, haptics and reduced motion, maps the tokens
 * onto a Material 3 theme (so stock sheets, sliders and switches look like ours) and, for screens that
 * are not rewritten yet, nests the legacy `AppTheme` inside so old screens render as before.
 *
 * @param reducedMotion the in-app "fewer animations" toggle. The system animator scale is observed
 * here too, so callers do not need to combine the two.
 * @param hapticsEnabled the in-app haptics toggle.
 */
@Composable
fun QhTheme(
    mode: ThemeMode,
    palette: ThemePalette,
    reducedMotion: Boolean = false,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = remember(mode, palette) { qhColors(mode, palette) }
    val typography = remember { QhTypography() }
    val shapes = remember { QhShapes() }
    val systemReducedMotion = rememberSystemReducedMotion()
    val haptics = rememberHaptics(hapticsEnabled)

    CompositionLocalProvider(
        LocalQhColors provides colors,
        LocalQhTypography provides typography,
        LocalQhShapes provides shapes,
        LocalHaptics provides haptics,
        LocalReducedMotion provides (reducedMotion || systemReducedMotion),
        LocalThemeMode provides mode,
        LocalThemePalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = remember(colors) { colors.toMaterialColorScheme() },
            shapes = remember(shapes) { shapes.toMaterialShapes() },
            typography = remember(typography) { typography.toMaterialTypography() },
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.ink) {
                ProvideTextStyle(typography.body) {
                    // Legacy screens still read the old theme; keep it underneath until they are rewritten.
                    AppTheme(mode = mode, palette = palette, content = content)
                }
            }
        }
    }
}

/** Access to the active token sets: `QhTheme.colors`, `QhTheme.type`, `QhTheme.shapes`. */
object QhTheme {
    val colors: QhColors
        @Composable @ReadOnlyComposable get() = LocalQhColors.current

    val type: QhTypography
        @Composable @ReadOnlyComposable get() = LocalQhTypography.current

    val shapes: QhShapes
        @Composable @ReadOnlyComposable get() = LocalQhShapes.current
}

private fun QhColors.toMaterialColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primaryContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = gold,
        onTertiary = bg,
        tertiaryContainer = goldContainer,
        onTertiaryContainer = ink,
        background = bg,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = inkSecondary,
        surfaceTint = primary,
        inverseSurface = ink,
        inverseOnSurface = bg,
        error = danger,
        onError = onDanger,
        errorContainer = dueContainer,
        onErrorContainer = due,
        outline = hairline,
        outlineVariant = hairline,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = bg,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceMuted,
        surfaceContainerHighest = surfaceStrong,
    )
}
