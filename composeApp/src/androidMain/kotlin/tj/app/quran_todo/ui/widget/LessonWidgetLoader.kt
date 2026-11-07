package tj.app.quran_todo.ui.widget

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.reminder.ReminderChannel
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.home.LessonCardBuilder
import tj.app.quran_todo.ui.reminder.ReminderSurahNames

/**
 * Reads today's lesson and the streak once and turns them into the [LessonWidgetModel] in the language of the app. The widget may be
 * drawn in a process that was just started for it: the singletons come from Koin, which the application class starts first.
 */
internal object LessonWidgetLoader {
    /** The model, or null when the lesson could not be read (the widget then only shows its name). */
    suspend fun load(@Suppress("UNUSED_PARAMETER") context: Context): LessonWidgetModel? =
        try {
            val koin = KoinPlatform.getKoin()
            val hifz = koin.get<HifzRepository>()
            val today = EpochDays.now()
            val language = ReminderChannel.currentLanguage()
            val lesson = hifz.lesson(today).first()
            val streak = hifz.streak(today).first()
            val names = ReminderSurahNames(koin.get(), koin.get()).forLanguage(language)
            LessonWidgetBuilder.build(LessonCardBuilder.build(lesson, nameOf = names), streak, language)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppTelemetry.logError(throwable = error, context = "widget_load_failed")
            null
        }
}
