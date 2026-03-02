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
        ReadingFontStyle.UTHMANI -> FontFamily(
            Font(Res.font.quran_font_2, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.AMIRI_QURAN -> FontFamily(
            Font(Res.font.quran_font, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.SCHEHERAZADE_NEW -> FontFamily.Serif
        ReadingFontStyle.NOTO_NASKH_ARABIC -> FontFamily.Serif
        ReadingFontStyle.NOTO_NASTALIQ -> FontFamily.Cursive
    }
}
