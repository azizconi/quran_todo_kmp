package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/** Words for TalkBack that the system libraries would otherwise say in the phone's language instead of the app's. */
data class A11yCopy(
    /** The description of the sheet's drag handle. */
    val sheetHandle: String,
    /** The description of the dimmed area behind a sheet (tapping it closes the sheet). */
    val sheetClose: String,
)

@Composable
fun rememberA11yCopy(language: AppLanguage = LocalAppLanguage.current): A11yCopy =
    remember(language) { a11yCopy(language) }

fun a11yCopy(language: AppLanguage): A11yCopy = when (language) {
    AppLanguage.RU -> A11yCopy(sheetHandle = "Ручка панели", sheetClose = "Закрыть панель")
    AppLanguage.EN -> A11yCopy(sheetHandle = "Sheet handle", sheetClose = "Close sheet")
    AppLanguage.TG -> A11yCopy(sheetHandle = "Дастаки лавҳа", sheetClose = "Пӯшидани лавҳа")
    AppLanguage.UZ -> A11yCopy(sheetHandle = "Panel tutqichi", sheetClose = "Panelni yopish")
    AppLanguage.TR -> A11yCopy(sheetHandle = "Panel tutamacı", sheetClose = "Paneli kapat")
}
