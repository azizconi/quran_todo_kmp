package tj.app.quran_todo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette

/**
 * Color tokens of the "Muskhaf" visual language (docs/redesign/ANDROID_REDESIGN.md, section 2.1).
 *
 * Status colors ([learned], [learning], [due]) are identical for every palette: their meaning must
 * never change. A palette only recolors the brand ([primary] and friends), the screen background and
 * the reader paper. Read them through [QhTheme.colors]; never hard-code a color in a component.
 */
@Immutable
data class QhColors(
    /** Screen background (paper by day, ink by night). */
    val bg: Color,
    /** Cards, sheets, the player. */
    val surface: Color,
    /** Search field, inactive chips, progress track. */
    val surfaceMuted: Color,
    /** Pressed state, skeletons. */
    val surfaceStrong: Color,
    /** 1dp dividers and outlines. */
    val hairline: Color,
    /** Primary text and Arabic text. */
    val ink: Color,
    /** Captions. */
    val inkSecondary: Color,
    /** Metadata, placeholders. Decorative contrast only; do not use for essential text. */
    val inkTertiary: Color,
    /** Brand: buttons and active elements. */
    val primary: Color,
    val onPrimary: Color,
    /** Tonal buttons, selection. */
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    /** Ornament and the "memorizing" accent. Never for 14sp body text. */
    val gold: Color,
    val goldContainer: Color,
    /** Status "memorized". */
    val learned: Color,
    /** Content on [learned] (a filled star). Palette-independent, like [learned] itself. */
    val onLearned: Color,
    val learnedContainer: Color,
    /** Status "memorizing". */
    val learning: Color,
    val learningContainer: Color,
    /** "Time to review" and weak ayahs. */
    val due: Color,
    val dueContainer: Color,
    /** Errors and destructive actions. */
    val danger: Color,
    /** Content on [danger]. Not part of the spec table; added so filled danger buttons keep 4.5:1. */
    val onDanger: Color,
    /** Reader paper: warm, never green at night. */
    val readerBg: Color,
    /** Player surface inside the reader. */
    val readerSurface: Color,
    /** Background of the current ayah (a translucent [primary]). */
    val readerHighlight: Color,
    /** Scrim under sheets (already translucent). */
    val scrim: Color,
    val isDark: Boolean,
)

/** Resolves the tokens for a theme [mode] and [palette]. */
fun qhColors(mode: ThemeMode, palette: ThemePalette): QhColors {
    val dark = mode == ThemeMode.DARK
    val brand = brandTokens(palette)
    val primary = if (dark) brand.primaryDark else brand.primaryLight
    return QhColors(
        bg = if (dark) brand.bgDark else brand.bgLight,
        surface = if (dark) Color(0xFF141C19) else Color(0xFFFFFDF9),
        surfaceMuted = if (dark) Color(0xFF1A2420) else Color(0xFFEFEADF),
        surfaceStrong = if (dark) Color(0xFF212C27) else Color(0xFFE6E0D2),
        hairline = if (dark) Color(0xFF27322D) else Color(0xFFE2DBCB),
        ink = if (dark) Color(0xFFECF2EE) else Color(0xFF16201C),
        inkSecondary = if (dark) Color(0xFFA7B3AC) else Color(0xFF5D6862),
        inkTertiary = if (dark) Color(0xFF76837C) else Color(0xFF8B938E),
        primary = primary,
        onPrimary = if (dark) brand.onPrimaryDark else Color.White,
        primaryContainer = if (dark) brand.primaryContainerDark else brand.primaryContainerLight,
        onPrimaryContainer = if (dark) Color(0xFFBDEFD9) else Color(0xFF08392C),
        gold = if (dark) Color(0xFFDDB76A) else Color(0xFFA97B2C),
        goldContainer = if (dark) Color(0xFF33291A) else Color(0xFFF3E7CA),
        learned = if (dark) Color(0xFF58CDA2) else Color(0xFF0E6B52),
        onLearned = if (dark) Color(0xFF003324) else Color.White,
        learnedContainer = if (dark) Color(0xFF10392C) else Color(0xFFD5EBDF),
        learning = if (dark) Color(0xFFDDB76A) else Color(0xFFA97B2C),
        learningContainer = if (dark) Color(0xFF33291A) else Color(0xFFF3E7CA),
        due = if (dark) Color(0xFFE89676) else Color(0xFFB4573A),
        dueContainer = if (dark) Color(0xFF3A2219) else Color(0xFFF6DDD3),
        danger = if (dark) Color(0xFFF08A7A) else Color(0xFFB3412F),
        onDanger = if (dark) Color(0xFF3A140D) else Color.White,
        readerBg = if (dark) brand.readerBgDark else brand.readerBgLight,
        readerSurface = if (dark) Color(0xFF191815) else Color(0xFFFFFDF9),
        readerHighlight = primary.copy(alpha = if (dark) 0.12f else 0.07f),
        scrim = Color.Black.copy(alpha = if (dark) 0.56f else 0.36f),
        isDark = dark,
    )
}

/** The only values a palette changes (section 2.1, "Отличия палитр"). */
private class BrandTokens(
    val primaryLight: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryContainerLight: Color,
    val primaryContainerDark: Color,
    val bgLight: Color,
    val bgDark: Color,
    val readerBgLight: Color,
    val readerBgDark: Color,
)

private fun brandTokens(palette: ThemePalette): BrandTokens = when (palette) {
    ThemePalette.FOREST -> BrandTokens(
        primaryLight = Color(0xFF0E6B52),
        primaryDark = Color(0xFF58CDA2),
        onPrimaryDark = Color(0xFF003324),
        primaryContainerLight = Color(0xFFD5EBDF),
        primaryContainerDark = Color(0xFF10392C),
        bgLight = Color(0xFFF6F3EC),
        bgDark = Color(0xFF0D1311),
        readerBgLight = Color(0xFFFBF8F2),
        readerBgDark = Color(0xFF11100E),
    )
    ThemePalette.SAND -> BrandTokens(
        primaryLight = Color(0xFF8C6A1E),
        primaryDark = Color(0xFFE3C278),
        onPrimaryDark = Color(0xFF3A2A06),
        primaryContainerLight = Color(0xFFF2E5C2),
        primaryContainerDark = Color(0xFF3B3019),
        bgLight = Color(0xFFF7F2E6),
        bgDark = Color(0xFF13110C),
        readerBgLight = Color(0xFFFBF6EA),
        readerBgDark = Color(0xFF12100B),
    )
    ThemePalette.OCEAN -> BrandTokens(
        primaryLight = Color(0xFF1A6579),
        primaryDark = Color(0xFF7DCBDF),
        onPrimaryDark = Color(0xFF00303B),
        primaryContainerLight = Color(0xFFD3E9EF),
        primaryContainerDark = Color(0xFF12333D),
        bgLight = Color(0xFFF2F5F5),
        bgDark = Color(0xFF0C1214),
        readerBgLight = Color(0xFFF8FAFA),
        readerBgDark = Color(0xFF0F1213),
    )
}

/** Default (light FOREST) so previews and tests work without a [QhTheme]. */
internal val LocalQhColors = staticCompositionLocalOf { qhColors(ThemeMode.LIGHT, ThemePalette.FOREST) }
