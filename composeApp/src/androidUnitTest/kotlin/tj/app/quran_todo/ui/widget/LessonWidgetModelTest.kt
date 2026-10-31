package tj.app.quran_todo.ui.widget

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.home.LessonCardState
import tj.app.quran_todo.ui.home.LessonCardUi
import tj.app.quran_todo.ui.home.LessonLineKind
import tj.app.quran_todo.ui.home.LessonLineUi
import tj.app.quran_todo.ui.home.LessonTomorrowUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The widget "Lesson of the day" (task F5b): every state of the card in the five languages, no placeholder left, no star. */
class LessonWidgetModelTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷')

    private fun line(kind: LessonLineKind, count: Int = 1, done: Int = 0, surah: String = "An-Naba", start: Int = 1, end: Int = 5) =
        LessonLineUi(kind, surah = 78, surahName = surah, startAyah = start, endAyah = end, count = count, doneCount = done)

    private val start = LessonCardUi(
        state = LessonCardState.Start,
        lines = listOf(line(LessonLineKind.New), line(LessonLineKind.Recent, count = 1), line(LessonLineKind.Old, count = 1)),
        minutes = 12,
    )
    private val continuing = LessonCardUi(
        state = LessonCardState.Continue,
        lines = listOf(line(LessonLineKind.New, done = 1), line(LessonLineKind.Recent, count = 2, done = 1), line(LessonLineKind.Old, count = 3, done = 1)),
        minutes = 7,
    )
    private val done = LessonCardUi(
        state = LessonCardState.Done,
        lines = listOf(line(LessonLineKind.New, done = 1)),
        tomorrow = LessonTomorrowUi(surah = 78, surahName = "An-Naba", startAyah = 6, endAyah = 10, dueCount = 2),
    )
    private val doneNothingTomorrow = LessonCardUi(state = LessonCardState.Done, tomorrow = LessonTomorrowUi(null, "", 0, 0, 0))
    private val noPlan = LessonCardUi(state = LessonCardState.NoPlan)
    private val paused = LessonCardUi(state = LessonCardState.Paused, pausedUntilEpochDay = 20_000)
    private val finished = LessonCardUi(state = LessonCardState.PlanFinished)

    private fun all() = listOf(start, continuing, done, doneNothingTomorrow, noPlan, paused, finished)

    @Test
    fun everyStateInEveryLanguageIsWrittenWithoutPlaceholdersOrStars() {
        AppLanguage.entries.forEach { language ->
            all().forEach { card ->
                val model = LessonWidgetBuilder.build(card, streakDays = 7, language = language)
                listOfNotNull(model.title, model.detail, model.streak, model.action).forEach { text ->
                    assertTrue(text.isNotBlank(), "${language.name} ${card.state}: blank")
                    assertFalse('{' in text || '}' in text, "${language.name} ${card.state}: $text")
                    assertTrue(stars.none { it in text }, "${language.name} ${card.state}: star in $text")
                }
                assertTrue(model.title.isNotBlank() && model.detail.isNotBlank())
            }
        }
    }

    @Test
    fun startSaysTheNewPassageAndTheRepetitionsLikeTheCard() {
        val model = LessonWidgetBuilder.build(start, streakDays = 7, language = AppLanguage.RU)
        assertEquals("Урок дня · ~12 мин", model.title)
        assertEquals("Новое: An-Naba 1–5 · 2 повторения", model.detail)
        assertEquals("7 дней подряд", model.streak)
        assertEquals("Начать урок", model.action)
        assertTrue(model.opensLesson)
        assertEquals(
            "New: An-Naba 1–5 · 2 reviews",
            LessonWidgetBuilder.build(start, 0, AppLanguage.EN).detail,
        )
        assertEquals("Today’s lesson · ~12 min", LessonWidgetBuilder.build(start, 0, AppLanguage.EN).title)
    }

    @Test
    fun continueCountsOnlyWhatIsLeft() {
        val model = LessonWidgetBuilder.build(continuing, streakDays = 1, language = AppLanguage.RU)
        assertEquals("Урок дня · ещё ~7 мин", model.title)
        // The new passage is done: only the repetitions that are left (1 recent + 2 old = 3).
        assertEquals("3 повторения", model.detail)
        assertEquals("Продолжить урок", model.action)
        assertEquals("1 день подряд", model.streak)
    }

    @Test
    fun aDoneDaySaysAllDoneAndWhatWaitsTomorrow() {
        val model = LessonWidgetBuilder.build(done, streakDays = 3, language = AppLanguage.RU)
        assertEquals("Сегодня всё · завтра: An-Naba 6–10 · повторить: 2", model.detail)
        assertEquals("Урок выполнен", model.action)
        assertTrue(model.opensLesson)
        assertEquals("All done today · tomorrow: An-Naba 6–10 · 2 to review", LessonWidgetBuilder.build(done, 0, AppLanguage.EN).detail)
        assertEquals("Сегодня всё · завтра: повторений нет", LessonWidgetBuilder.build(doneNothingTomorrow, 0, AppLanguage.RU).detail)
    }

    @Test
    fun noPlanInvitesToChooseOneAndOpensTheMainScreen() {
        val model = LessonWidgetBuilder.build(noPlan, streakDays = 5, language = AppLanguage.RU)
        assertEquals("Выбрать план", model.detail)
        assertFalse(model.opensLesson)
        assertNull(model.streak)
    }

    @Test
    fun aPausedPlanSaysUntilWhenAndOpensTheMainScreen() {
        val model = LessonWidgetBuilder.build(paused, streakDays = 4, language = AppLanguage.EN)
        assertTrue(model.detail.startsWith("Plan paused until "), model.detail)
        assertFalse(model.opensLesson)
        assertNull(model.action)
    }

    @Test
    fun aFinishedPlanOffersANewOne() {
        val model = LessonWidgetBuilder.build(finished, streakDays = 0, language = AppLanguage.RU)
        assertEquals("План выполнен", model.title)
        assertEquals("Новый план", model.action)
        assertFalse(model.opensLesson)
    }

    @Test
    fun noStreakMeansNoStreakLine() {
        assertNull(LessonWidgetBuilder.build(start, 0, AppLanguage.RU).streak)
    }
}
