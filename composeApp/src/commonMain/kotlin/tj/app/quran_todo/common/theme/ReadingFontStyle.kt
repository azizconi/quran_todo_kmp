package tj.app.quran_todo.common.theme

import androidx.compose.runtime.staticCompositionLocalOf
import tj.app.quran_todo.common.i18n.AppStrings

enum class ReadingFontStyle(val storageValue: String) {
    MUSHAF_MODERN("mushaf_modern"),
    MUSHAF_CLASSIC("mushaf_classic"),
    SANS("sans"),
    SERIF("serif"),
    MONO("mono");

    fun label(strings: AppStrings): String = when (this) {
        MUSHAF_MODERN -> strings.fontMushafModernLabel
        MUSHAF_CLASSIC -> strings.fontMushafClassicLabel
        SANS -> strings.fontSansLabel
        SERIF -> strings.fontSerifLabel
        MONO -> strings.fontMonoLabel
    }

    companion object {
        fun fromStorage(value: String?): ReadingFontStyle {
            return values().firstOrNull { it.storageValue == value } ?: MUSHAF_MODERN
        }
    }
}

val LocalReadingFontStyle = staticCompositionLocalOf { ReadingFontStyle.MUSHAF_MODERN }
val LocalReadingFontStyleSetter = staticCompositionLocalOf<(ReadingFontStyle) -> Unit> { {} }
