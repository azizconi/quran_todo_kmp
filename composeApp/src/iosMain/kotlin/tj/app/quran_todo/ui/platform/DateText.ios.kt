package tj.app.quran_todo.ui.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName
import tj.app.quran_todo.common.i18n.AppLanguage

actual fun formatDayOfMonth(epochDay: Int, language: AppLanguage): String {
    val tag = when (language) {
        AppLanguage.RU -> "ru"
        AppLanguage.EN -> "en"
        AppLanguage.TG -> "tg"
        AppLanguage.UZ -> "uz"
        AppLanguage.TR -> "tr"
    }
    val formatter = NSDateFormatter().apply {
        dateFormat = "d MMMM"
        locale = NSLocale(localeIdentifier = tag)
        timeZone = NSTimeZone.timeZoneWithName("UTC")!!
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochDay * 86400.0))
}
