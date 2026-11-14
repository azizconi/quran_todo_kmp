package tj.app.quran_todo.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.quiz.QuizBuilder
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.schedule.SurahCompletion
import tj.app.quran_todo.ui.progress.report.ReportExporter
import tj.app.quran_todo.ui.reminder.ReminderController
import tj.app.quran_todo.ui.reminder.ReminderPlanner
import tj.app.quran_todo.ui.store.ReviewPrompt

/**
 * Small doors of the lesson and progress screens into the shared layer that [IosUi] does not give (IOS-S4 / S6): the Flows the
 * summary of the lesson reads, the reminder controller and the review prompt. Call on the main thread.
 */
object IosLessonBridge {
    private val koin get() = KoinPlatform.getKoin()
    private val hifz: HifzRepository get() = koin.get()

    /** "Check yourself" is offered when enough is memorized for the quiz (`QuizBuilder.isAvailable`). */
    fun quizReady(): Flow<Boolean> = hifz.coveredAyahs().map { QuizBuilder.isAvailable(it) }.distinctUntilChanged().catch { emit(false) }

    /** The surah of the plan that today's new passage completed (task U13), or null. */
    fun finishedSurah(plan: LessonPlan): Flow<Int?> = combine(hifz.plan(), hifz.coveredAyahs()) { hifzPlan, covered ->
        SurahCompletion.finishedToday(plan.newBlock, hifzPlan?.surahOrder.orEmpty(), covered)
    }.distinctUntilChanged().catch { emit(null) }

    /** The pause of the plan that holds today, or null (the lesson says "Plan paused until ..." instead of "All done today"). */
    fun pauseToday(): Flow<PlanPause?> = combine(hifz.pause(IosUi.ticker.day), IosUi.ticker.day) { pause, today ->
        pause?.takeIf { today in it }
    }.distinctUntilChanged().catch { emit(null) }

    /** The reminder controller of the settings and of the summary's "Remind me". */
    fun reminderController(): ReminderController = ReminderController(koin.get<ReminderPlanner>(), IosUi.uiPrefs)

    /** The iOS maker of the report PDF (the services' `ReportExporter`), or null while none is registered. */
    fun reportExporter(): ReportExporter? = koin.getOrNull<ReportExporter>()

    /** A lesson was finished: counts it and, when the gate holds, asks the store for its card (`ReviewPrompt`). */
    suspend fun onLessonFinished() = koin.get<ReviewPrompt>().onLessonFinished()
}
