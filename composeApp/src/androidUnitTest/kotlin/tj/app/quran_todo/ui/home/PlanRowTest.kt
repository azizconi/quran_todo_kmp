package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.ui.home.components.planRowText
import tj.app.quran_todo.ui.i18n.tabsCopy
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanRowTest {
    private val today = LocalDate(2026, 11, 12).toEpochDays()
    private val finish = LocalDate(2027, 3, 2).toEpochDays()

    private val plan = HifzPlan(
        scope = HifzScope.JUZ_30,
        surahOrder = listOf(114, 113),
        dailyNewAyahs = 5,
        targetEpochDay = null,
        startedEpochDay = today - 10,
        manzilDays = 7,
    )

    private fun forecast(remaining: Int = 554, total: Int = 564, finished: Boolean = false, finishDay: Int? = finish) = PlanForecast(
        totalAyahs = total,
        remainingAyahs = remaining,
        dailyNewAyahs = 5,
        finished = finished,
        finishEpochDay = finishDay,
        targetEpochDay = null,
        requiredPerDay = null,
        behindAyahs = 0,
    )

    private fun row(card: LessonCardState? = LessonCardState.Start, forecast: PlanForecast? = forecast(), plan: HifzPlan? = this.plan) =
        HomeStateBuilder.planRow(plan, forecast, card, today)

    @Test
    fun aRunningPlanGivesTheCoverageNumbersOfTheForecast() {
        val model = assertNotNull(row())
        assertEquals(HifzScope.JUZ_30, model.scope)
        assertEquals(10, model.covered)
        assertEquals(564, model.total)
        assertEquals(finish, model.finishEpochDay)
    }

    @Test
    fun theRowShowsInEveryStateOfARunningLesson() {
        for (state in listOf(LessonCardState.Start, LessonCardState.Continue, LessonCardState.Done)) {
            assertNotNull(row(card = state), "no row in $state")
        }
    }

    @Test
    fun noRowWhenTheCardAlreadySaysSo() {
        assertNull(row(card = LessonCardState.NoPlan))
        assertNull(row(card = LessonCardState.Paused))
        assertNull(row(card = LessonCardState.PlanFinished))
        assertNull(row(card = null))
    }

    @Test
    fun noRowWithoutAPlanOrAForecastOrForAFinishedPlan() {
        assertNull(row(plan = null))
        assertNull(row(forecast = null))
        assertNull(row(forecast = forecast(remaining = 0, finished = true)))
    }

    @Test
    fun coveredNeverLeavesTheRange() {
        assertEquals(0, assertNotNull(row(forecast = forecast(remaining = 700))).covered)
        assertEquals(564, assertNotNull(row(forecast = forecast(remaining = -3))).covered)
    }

    @Test
    fun theTextSaysCoveredAndTheFinishDayInRussian() {
        val text = planRowText(assertNotNull(row()), tabsCopy(AppLanguage.RU))
        assertTrue(text.endsWith(" · пройдено 10 из 564 · закончите ~2 марта 2027"), text)
        assertTrue("выучено" !in text)
    }

    @Test
    fun withoutAFinishDayTheDatePartIsLeftOut() {
        val model = assertNotNull(row(forecast = forecast(finishDay = null)))
        for (language in AppLanguage.entries) {
            val text = planRowText(model, tabsCopy(language))
            assertTrue("~" !in text, "$language: $text")
            assertTrue("10" in text && "564" in text, "$language: $text")
        }
    }

    @Test
    fun everyLanguageHasTheWordsOfTheRow() {
        val model = assertNotNull(row())
        for (language in AppLanguage.entries) {
            val copy = tabsCopy(language)
            assertTrue(copy.planChange.isNotBlank() && copy.tabLesson.isNotBlank() && copy.tabQuran.isNotBlank(), "$language")
            assertTrue("~" in planRowText(model, copy), "$language")
        }
    }
}
