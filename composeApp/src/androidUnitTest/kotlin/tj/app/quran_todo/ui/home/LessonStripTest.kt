package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.homeCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonStripTest {
    private val copy = homeCopy(AppLanguage.EN)

    private fun newLine(done: Boolean = false) = LessonLineUi(
        kind = LessonLineKind.New, surah = 2, surahName = "Al-Baqarah", startAyah = 1, endAyah = 5,
        count = 1, doneCount = if (done) 1 else 0,
    )

    @Test
    fun startShowsTheNewPassageTheMinutesAndStart() {
        val strip = assertNotNull(LessonStrip.of(LessonCardUi(LessonCardState.Start, lines = listOf(newLine()), minutes = 14), copy))
        assertEquals(copy.lessonNew("Al-Baqarah 1–5") + " · " + copy.lessonMinutes(14), strip.text)
        assertEquals(copy.lessonStart, strip.action)
        assertEquals(false, strip.done)
    }

    @Test
    fun continueSaysHowMuchIsLeft() {
        val strip = assertNotNull(LessonStrip.of(LessonCardUi(LessonCardState.Continue, lines = listOf(newLine()), minutes = 6), copy))
        assertTrue(strip.text.endsWith(copy.lessonMinutesLeft(6)))
        assertEquals(copy.lessonContinue, strip.action)
    }

    @Test
    fun doneHasACheckAndNoButton() {
        val strip = assertNotNull(LessonStrip.of(LessonCardUi(LessonCardState.Done), copy))
        assertEquals(copy.lessonDone, strip.text)
        assertTrue(strip.done)
        assertNull(strip.action)
    }

    @Test
    fun noLessonNoStrip() {
        assertNull(LessonStrip.of(null, copy))
        assertNull(LessonStrip.of(LessonCardUi(LessonCardState.NoPlan), copy))
        assertNull(LessonStrip.of(LessonCardUi(LessonCardState.Paused), copy))
        assertNull(LessonStrip.of(LessonCardUi(LessonCardState.PlanFinished), copy))
    }
}
