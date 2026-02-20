package tj.app.quran_todo.common.theme

import androidx.compose.runtime.staticCompositionLocalOf
import tj.app.quran_todo.common.i18n.AppStrings

enum class AyahCardStyle(val storageValue: String) {
    CLASSIC("classic"),
    COMPACT("compact"),
    FOCUS("focus");

    fun label(strings: AppStrings): String = when (this) {
        CLASSIC -> strings.cardStyleClassicLabel
        COMPACT -> strings.cardStyleCompactLabel
        FOCUS -> strings.cardStyleFocusLabel
    }

    companion object {
        fun fromStorage(value: String?): AyahCardStyle {
            return values().firstOrNull { it.storageValue == value } ?: CLASSIC
        }
    }
}

val LocalAyahCardStyle = staticCompositionLocalOf { AyahCardStyle.CLASSIC }
val LocalAyahCardStyleSetter = staticCompositionLocalOf<(AyahCardStyle) -> Unit> { {} }
