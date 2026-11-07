package tj.app.quran_todo.ui.home

import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.TextMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Listen to the old" and "Listened" on the card of today's lesson (task F1b): shown, and changing nothing else of the card. */
class ListenCardTest {
    private val today = 20_000

    // Surahs 112 (4 ayahs), 113 (5), 114 (6); 570 characters per ayah.
    private fun metrics() = TextMetrics(
        listOf(112 to 4, 113 to 5, 114 to 6).flatMap { (surah, count) -> (1..count).map { AyahMetric(surah, it, page = 1, chars = 570) } },
    )

    private val plan = HifzPlan(HifzScope.CUSTOM, listOf(114), 3, targetEpochDay = null, startedEpochDay = today - 10, manzilDays = 3)

    private fun old(id: Long, surah: Int, start: Int, end: Int) = Passage(
        id = id, surah = surah, startAyah = start, endAyah = end, learnedEpochDay = today - 100,
        stage = PassageStage.OLD, nextDueEpochDay = null, lastReviewedEpochDay = null, lapses = 0,
    )

    // Six passages of one ayah (570 characters) in surah 114: a circle of 3420 over 3 days is 1140 a day, i.e. two passages.
    private val passages = (1..6).map { old(it.toLong(), 114, it, it) }

    private fun lesson(passages: List<Passage> = this.passages): LessonPlan =
        LessonBuilder.build(LessonInput(today, plan, passages, emptyList(), metrics()))

    private fun card(lesson: LessonPlan, listened: Set<Long> = emptySet()) = LessonCardBuilder.build(lesson, listened) { "S$it" }

    @Test
    fun theCardOffersTheWholePortionOfTheCircleAsAudio() {
        val lesson = lesson()
        val old = card(lesson).oldListen
        assertNotNull(old)
        assertEquals(2, lesson.old.size)
        assertEquals(lesson.old.size, old.passages)
        // About one calm recitation of the portion, in whole minutes: 2 x 570 characters at 5.7 characters a second = 200 s.
        val seconds = lesson.old.sumOf { it.listenSeconds }
        assertEquals(200, seconds)
        assertEquals(4, old.minutes)
        assertFalse(old.listened)
    }

    @Test
    fun withoutAnOldPortionThereIsNoButton() {
        assertNull(card(lesson(passages = emptyList())).oldListen)
        val ladderOnly = listOf(
            Passage(7, 112, 1, 4, today - 5, PassageStage.R3, nextDueEpochDay = today, lastReviewedEpochDay = today - 3, lapses = 0),
        )
        assertNull(card(lesson(ladderOnly)).oldListen)
    }

    @Test
    fun listenedIsTrueOnlyWhenEveryPassageOfThePortionWasListenedTo() {
        val lesson = lesson()
        val ids = lesson.old.map { it.passageId }
        // One of two is not "listened".
        assertFalse(card(lesson, setOf(ids.first())).oldListen!!.listened)
        val all = card(lesson, ids.toSet())
        assertTrue(all.oldListen!!.listened)
        assertTrue(all.lines.single { it.kind == LessonLineKind.Old }.listened)
        // Listening of passages that are not in the portion says nothing about it.
        assertFalse(card(lesson, setOf(999L)).oldListen!!.listened)
    }

    @Test
    fun listeningDoesNotChangeTheStateOrTheCountsOrTheMinutesOfTheCard() {
        val lesson = lesson()
        val without = card(lesson)
        val with = card(lesson, lesson.old.map { it.passageId }.toSet())
        assertEquals(without.state, with.state)
        assertEquals(without.minutes, with.minutes)
        assertEquals(without.catchingUp, with.catchingUp)
        assertEquals(without.lines.map { it.copy(listened = false) }, with.lines.map { it.copy(listened = false) })
        assertEquals(LessonCardState.Start, with.state)
        assertTrue(with.lines.none { it.done })
    }
}
