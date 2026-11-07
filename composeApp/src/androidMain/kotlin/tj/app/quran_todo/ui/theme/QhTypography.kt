package tj.app.quran_todo.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import tj.app.quran_todo.R

/**
 * UI font: Manrope (OFL), a single variable TTF in `res/font/manrope_variable.ttf` instanced at the
 * weights 400-800. Variable fonts need API 26; below that the system font keeps the weight scale
 * intact (the variable font's default axis value is ExtraLight, which would be unreadable).
 *
 * Manrope has no Tajik-specific Cyrillic letters (Ғ Ӣ Қ Ӯ Ҳ Ҷ) nor U+02BB/U+02BC; Android falls back
 * to the system font for those glyphs.
 */
@OptIn(ExperimentalTextApi::class)
val QhFontFamily: FontFamily = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        listOf(400, 500, 600, 700, 800).map { weight ->
            Font(
                resId = R.font.manrope_variable,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )
} else {
    FontFamily.Default
}

/** Text scale of section 2.2. Arabic styles are factories because they depend on the chosen Quran font. */
@Immutable
class QhTypography(
    /** 34/40, 800: large screen title. */
    val display: TextStyle = uiStyle(34, 40, 800, -0.6f),
    /** 26/32, 700: hero heading, session summary. */
    val headline: TextStyle = uiStyle(26, 32, 700, -0.3f),
    /** 21/28, 700: surah name on a card, sheet titles. */
    val title1: TextStyle = uiStyle(21, 28, 700, -0.2f),
    /** 17/24, 600: surah name in a row, settings items. */
    val title2: TextStyle = uiStyle(17, 24, 600, 0f),
    /** 16/24, 400: translation, descriptions. */
    val body: TextStyle = uiStyle(16, 24, 400, 0f),
    /** 14/20, 500: captions, metadata. */
    val callout: TextStyle = uiStyle(14, 20, 500, 0f),
    /** 13/16, 600: chips, segments, small buttons. */
    val label: TextStyle = uiStyle(13, 16, 600, 0.1f),
    /** 11/14, 700, tracking 1.0: section overlines. Pass the text through `uppercase()`. */
    val overline: TextStyle = uiStyle(11, 14, 700, 1f),
) {
    /** Arabic surah name in the reader header: 30-34sp, line height 1.6x. */
    fun quranHeader(family: FontFamily, size: TextUnit = 32.sp): TextStyle = quranStyle(family, size, 1.6f)

    /** Ayah text: [size] is `readingFontSize` (22-40sp), line height 1.85x. */
    fun quranBody(family: FontFamily, size: TextUnit = 28.sp): TextStyle = quranStyle(family, size, 1.85f)

    /** Arabic surah name in lists: 19sp, line height 1.4x. */
    fun quranInline(family: FontFamily, size: TextUnit = 19.sp): TextStyle = quranStyle(family, size, 1.4f)
}

/** Tabular figures: use for counters, timers and ayah numbers so digits do not jitter. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private fun uiStyle(size: Int, lineHeight: Int, weight: Int, tracking: Float) = TextStyle(
    fontFamily = QhFontFamily,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
)

private fun quranStyle(family: FontFamily, size: TextUnit, lineHeightFactor: Float) = TextStyle(
    fontFamily = family,
    fontSize = size,
    lineHeight = size * lineHeightFactor,
    fontWeight = FontWeight.Normal,
    textDirection = TextDirection.ContentOrRtl,
)

/** Maps our scale onto Material 3 so stock components (sheet, slider, switch) read the same fonts. */
internal fun QhTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = headline,
    headlineLarge = headline,
    headlineMedium = title1,
    headlineSmall = title1,
    titleLarge = title1,
    titleMedium = title2,
    titleSmall = callout,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = callout,
    labelLarge = label,
    labelMedium = label,
    labelSmall = overline,
)

internal val LocalQhTypography = staticCompositionLocalOf { QhTypography() }
