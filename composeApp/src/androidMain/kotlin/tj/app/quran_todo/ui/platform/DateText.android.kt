package tj.app.quran_todo.ui.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import tj.app.quran_todo.common.i18n.AppLanguage

private const val MillisPerDay = 24L * 60 * 60 * 1000

actual fun formatDayOfMonth(epochDay: Int, language: AppLanguage): String {
    val locale = Locale.forLanguageTag(
        when (language) {
            AppLanguage.RU -> "ru"
            AppLanguage.EN -> "en"
            AppLanguage.TG -> "tg"
            AppLanguage.UZ -> "uz"
            AppLanguage.TR -> "tr"
        },
    )
    val format = SimpleDateFormat("d MMMM", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return format.format(Date(epochDay * MillisPerDay))
}
