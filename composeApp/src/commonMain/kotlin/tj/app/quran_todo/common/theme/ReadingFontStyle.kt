package tj.app.quran_todo.common.theme

import androidx.compose.runtime.staticCompositionLocalOf
import tj.app.quran_todo.common.i18n.AppStrings

enum class ReadingFontStyle(val storageValue: String) {
    MADINAH_MUSHAF("madinah_mushaf"),
    AMIRI_QURAN("amiri_quran"),
    SCHEHERAZADE_NEW("scheherazade_new"),
    NOTO_NASKH_ARABIC("noto_naskh_arabic"),
    LATEEF("lateef"),
    NOTO_NASTALIQ_URDU("noto_nastaliq_urdu"),
    AREF_RUQAA_INK("aref_ruqaa_ink");

    fun label(strings: AppStrings): String = when (this) {
        MADINAH_MUSHAF -> strings.fontMadinahMushafLabel
        AMIRI_QURAN -> strings.fontAmiriQuranLabel
        SCHEHERAZADE_NEW -> strings.fontScheherazadeLabel
        NOTO_NASKH_ARABIC -> strings.fontNotoNaskhLabel
        LATEEF -> strings.fontLateefLabel
        NOTO_NASTALIQ_URDU -> strings.fontNotoNastaliqUrduLabel
        AREF_RUQAA_INK -> strings.fontArefRuqaaInkLabel
    }

    companion object {
        fun fromStorage(value: String?): ReadingFontStyle {
            return when (value) {
                // Keep existing users on the closest equivalent after the font refresh.
                "uthmani" -> MADINAH_MUSHAF
                "noto_nastaliq" -> NOTO_NASTALIQ_URDU
                else -> entries.firstOrNull { it.storageValue == value } ?: MADINAH_MUSHAF
            }
        }
    }
}

val LocalReadingFontStyle = staticCompositionLocalOf { ReadingFontStyle.MADINAH_MUSHAF }
val LocalReadingFontStyleSetter = staticCompositionLocalOf<(ReadingFontStyle) -> Unit> { {} }
