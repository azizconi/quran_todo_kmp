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
    /** The custom actions of a number wheel: one step up and one step down. */
    val wheelMore: String,
    val wheelLess: String,
    /** TalkBack actions of the year calendar: step the chosen day (or week) back and forward. */
    val heatPreviousDay: String,
    val heatNextDay: String,
    val heatPreviousWeek: String,
    val heatNextWeek: String,
)

@Composable
fun rememberA11yCopy(language: AppLanguage = LocalAppLanguage.current): A11yCopy =
    remember(language) { a11yCopy(language) }

fun a11yCopy(language: AppLanguage): A11yCopy = when (language) {
    AppLanguage.RU -> A11yCopy(sheetHandle = "Ручка панели", sheetClose = "Закрыть панель", wheelMore = "Больше", wheelLess = "Меньше",
        heatPreviousDay = "Предыдущий день", heatNextDay = "Следующий день",
        heatPreviousWeek = "Предыдущая неделя", heatNextWeek = "Следующая неделя",
    )
    AppLanguage.EN -> A11yCopy(sheetHandle = "Sheet handle", sheetClose = "Close sheet", wheelMore = "More", wheelLess = "Less",
        heatPreviousDay = "Previous day", heatNextDay = "Next day",
        heatPreviousWeek = "Previous week", heatNextWeek = "Next week",
    )
    AppLanguage.TG -> A11yCopy(sheetHandle = "Дастаки лавҳа", sheetClose = "Пӯшидани лавҳа", wheelMore = "Бештар", wheelLess = "Камтар",
        heatPreviousDay = "Рӯзи гузашта", heatNextDay = "Рӯзи оянда",
        heatPreviousWeek = "Ҳафтаи гузашта", heatNextWeek = "Ҳафтаи оянда",
    )
    AppLanguage.UZ -> A11yCopy(sheetHandle = "Panel tutqichi", sheetClose = "Panelni yopish", wheelMore = "Ko'proq", wheelLess = "Kamroq",
        heatPreviousDay = "Oldingi kun", heatNextDay = "Keyingi kun",
        heatPreviousWeek = "Oldingi hafta", heatNextWeek = "Keyingi hafta",
    )
    AppLanguage.TR -> A11yCopy(sheetHandle = "Panel tutamacı", sheetClose = "Paneli kapat", wheelMore = "Daha fazla", wheelLess = "Daha az",
        heatPreviousDay = "Önceki gün", heatNextDay = "Sonraki gün",
        heatPreviousWeek = "Önceki hafta", heatNextWeek = "Sonraki hafta",
    )
}
