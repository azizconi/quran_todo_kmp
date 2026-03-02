package tj.app.quran_todo.common.theme

import androidx.compose.runtime.staticCompositionLocalOf
import tj.app.quran_todo.common.i18n.AppStrings

enum class ReadingFontStyle(val storageValue: String) {
    UTHMANI("uthmani"),
    AMIRI_QURAN("amiri_quran"),
    SCHEHERAZADE_NEW("scheherazade_new"),
    NOTO_NASKH_ARABIC("noto_naskh_arabic"),
    NOTO_NASTALIQ("noto_nastaliq");

    fun label(strings: AppStrings): String = when (this) {
        UTHMANI -> strings.fontUthmaniLabel
        AMIRI_QURAN -> strings.fontAmiriQuranLabel
        SCHEHERAZADE_NEW -> strings.fontScheherazadeLabel
        NOTO_NASKH_ARABIC -> strings.fontNotoNaskhLabel
        NOTO_NASTALIQ -> strings.fontNotoNastaliqLabel
    }

    companion object {
        fun fromStorage(value: String?): ReadingFontStyle {
            return values().firstOrNull { it.storageValue == value } ?: UTHMANI
        }
    }
}

val LocalReadingFontStyle = staticCompositionLocalOf { ReadingFontStyle.UTHMANI }
val LocalReadingFontStyleSetter = staticCompositionLocalOf<(ReadingFontStyle) -> Unit> { {} }
