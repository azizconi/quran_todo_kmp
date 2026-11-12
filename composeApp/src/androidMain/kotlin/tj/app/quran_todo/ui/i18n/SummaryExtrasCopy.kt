package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Two small additions to the lesson summary in the five app languages: the question "Remind you about tomorrow's lesson?" (task U2)
 * and the line with the real time of the lesson (task U3).
 */
@Immutable
data class SummaryExtrasCopy(
    val language: AppLanguage,
    val askTitle: String,
    val askOtherTime: String,
    val askYes: String,
    val askNo: String,
    private val takesAboutFmt: String,
) {
    /** "Your lesson takes about 12 minutes". */
    fun takesAbout(minutes: Int): String = takesAboutFmt.replace("{min}", "$minutes ${minuteWord(minutes)}")

    private fun minuteWord(n: Int): String = when (language) {
        AppLanguage.RU -> if (n % 10 == 1 && n % 100 != 11) "минуты" else "минут"
        AppLanguage.EN -> if (n == 1) "minute" else "minutes"
        AppLanguage.TG -> "дақиқа"
        AppLanguage.UZ -> "daqiqa"
        AppLanguage.TR -> "dakika"
    }
}

@Composable
fun rememberSummaryExtrasCopy(language: AppLanguage = LocalAppLanguage.current): SummaryExtrasCopy =
    remember(language) { summaryExtrasCopy(language) }

fun summaryExtrasCopy(language: AppLanguage): SummaryExtrasCopy = when (language) {
    AppLanguage.RU -> SummaryExtrasCopy(
        language = language,
        askTitle = "Напомнить завтра об уроке?",
        askOtherTime = "Другое…",
        askYes = "Напоминать",
        askNo = "Не сейчас",
        takesAboutFmt = "Ваш урок занимает около {min}",
    )
    AppLanguage.EN -> SummaryExtrasCopy(
        language = language,
        askTitle = "Remind you about tomorrow's lesson?",
        askOtherTime = "Other…",
        askYes = "Remind me",
        askNo = "Not now",
        takesAboutFmt = "Your lesson takes about {min}",
    )
    AppLanguage.TG -> SummaryExtrasCopy(
        language = language,
        askTitle = "Оид ба дарси фардо ёдрас кунем?",
        askOtherTime = "Вақти дигар…",
        askYes = "Ёдрас кун",
        askNo = "Ҳозир не",
        takesAboutFmt = "Дарси шумо тақрибан {min} давом мекунад",
    )
    AppLanguage.UZ -> SummaryExtrasCopy(
        language = language,
        askTitle = "Ertangi dars haqida eslatib qo‘yaylikmi?",
        askOtherTime = "Boshqa vaqt…",
        askYes = "Eslatib tur",
        askNo = "Hozir emas",
        takesAboutFmt = "Darsingiz taxminan {min} davom etadi",
    )
    AppLanguage.TR -> SummaryExtrasCopy(
        language = language,
        askTitle = "Yarınki ders için hatırlatalım mı?",
        askOtherTime = "Başka saat…",
        askYes = "Hatırlat",
        askNo = "Şimdi değil",
        takesAboutFmt = "Dersiniz yaklaşık {min} sürüyor",
    )
}
