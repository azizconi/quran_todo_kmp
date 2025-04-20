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

fun <T> MutableState<T>.asState(): State<T> = this

@Composable
fun getQuranFontFamily() = FontFamily(
    Font(Res.font.quran_font, weight = FontWeight.Normal, FontStyle.Normal)     // <-- your internal val FontResource
)