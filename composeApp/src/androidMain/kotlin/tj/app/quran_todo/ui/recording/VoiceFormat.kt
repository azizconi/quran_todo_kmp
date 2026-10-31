package tj.app.quran_todo.ui.recording

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import tj.app.quran_todo.common.i18n.AppLanguage

/** Text forms of the recordings: the length of a recording and the moment it was made. */
object VoiceFormat {
    /** 42 s -> `0:42`, 12 min 5 s -> `12:05`, 10 min -> `10:00`; a negative length is 0. */
    fun clock(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) + 500L) / 1000L
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    /** `0:12 / 0:42`: the position in a recording that is loaded. */
    fun progress(positionMs: Long, durationMs: Long): String = "${clock(positionMs)} / ${clock(durationMs)}"

    /** "30 Oct, 21:14" in the language of the app and the time zone [zone] of the phone. */
    fun moment(epochMillis: Long, language: AppLanguage, zone: TimeZone = TimeZone.getDefault()): String {
        val locale = Locale.forLanguageTag(
            when (language) {
                AppLanguage.RU -> "ru"
                AppLanguage.EN -> "en"
                AppLanguage.TG -> "tg"
                AppLanguage.UZ -> "uz"
                AppLanguage.TR -> "tr"
            },
        )
        val format = SimpleDateFormat("d MMM, HH:mm", locale).apply { timeZone = zone }
        return format.format(Date(epochMillis))
    }

    /** The loudness bar: the share of the width (0..1) a level stands for, never quite empty so the bar can be seen. */
    fun barShare(level: Float): Float = level.coerceIn(0f, 1f) * 0.96f + 0.04f
}
