package tj.app.quran_todo.common.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import qurantodo.composeapp.generated.resources.Res
import qurantodo.composeapp.generated.resources.amiri_quran
import qurantodo.composeapp.generated.resources.aref_ruqaa_ink
import qurantodo.composeapp.generated.resources.lateef
import qurantodo.composeapp.generated.resources.madinah_mushaf
import qurantodo.composeapp.generated.resources.noto_naskh_arabic
import qurantodo.composeapp.generated.resources.noto_nastaliq_urdu
import qurantodo.composeapp.generated.resources.scheherazade_new
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.ReadingFontStyle

fun <T> MutableState<T>.asState(): State<T> = this

@Composable
fun getQuranFontFamily(style: ReadingFontStyle = LocalReadingFontStyle.current): FontFamily {
    return when (style) {
        ReadingFontStyle.MADINAH_MUSHAF -> FontFamily(
            Font(Res.font.madinah_mushaf, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.AMIRI_QURAN -> FontFamily(
            Font(Res.font.amiri_quran, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.SCHEHERAZADE_NEW -> FontFamily(
            Font(Res.font.scheherazade_new, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.NOTO_NASKH_ARABIC -> FontFamily(
            Font(Res.font.noto_naskh_arabic, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.LATEEF -> FontFamily(
            Font(Res.font.lateef, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.NOTO_NASTALIQ_URDU -> FontFamily(
            Font(Res.font.noto_nastaliq_urdu, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
        ReadingFontStyle.AREF_RUQAA_INK -> FontFamily(
            Font(Res.font.aref_ruqaa_ink, weight = FontWeight.Normal, style = FontStyle.Normal)
        )
    }
}
