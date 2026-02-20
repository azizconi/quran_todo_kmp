package tj.app.quran_todo.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.quran_font
import qurantodo.composeapp.generated.resources.quran_font_2
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.ReadingFontStyle

fun <T> MutableState<T>.asState(): State<T> = this

@Composable
fun getQuranFontFamily(): FontFamily {
    return when (LocalReadingFontStyle.current) {
        ReadingFontStyle.MUSHAF_MODERN -> FontFamily(
            Font(Res.font.quran_font_2, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.MUSHAF_CLASSIC -> FontFamily(
            Font(Res.font.quran_font, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.SANS -> FontFamily.SansSerif
        ReadingFontStyle.SERIF -> FontFamily.Serif
        ReadingFontStyle.MONO -> FontFamily.Monospace
    }
}
