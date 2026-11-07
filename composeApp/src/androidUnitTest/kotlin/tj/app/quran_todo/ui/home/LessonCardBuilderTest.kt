package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.TextMetrics
import tj.app.quran_todo.ui.home.components.lineText
import tj.app.quran_todo.ui.home.components.tomorrowText
import tj.app.quran_todo.ui.i18n.homeCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The card model of "Today's lesson" for every state, built from lessons made by the real scheduler
 * (`LessonBuilder`) over a small synthetic text: surahs 112 (4 ayahs), 113 (5) and 114 (6).
 */
class LessonCardBuilderTest {
    private val today = 20_000

    private fun metrics(charsPerAyah: Int = 100) = TextMetrics(
        listOf(112 to 4, 113 to 5, 114 to 6).flatMap { (surah, count) ->
            (1..count).map { AyahMetric(surah, it, page = 1, chars = charsPerAyah) }
        },
    )

    private fun plan(order: List<Int> = listOf(114), daily: Int = 3) =
        HifzPlan(HifzScope.CUSTOM, order, daily, targetEpochDay = null, startedEpochDay = today - 10, manzilDays = 7)

    private fun passage(
        id: Long,
        surah: Int,
        start: Int,
        end: Int,
        stage: PassageStage = PassageStage.R1,
        due: Int? = today,
        lastReviewed: Int? = today - 1,
    ) = Passage(
        id = id,
        surah = surah,
        startAyah = start,
        endAyah = end,
        learnedEpochDay = today - 5,
        stage = stage,
        nextDueEpochDay = due,
        lastReviewedEpochDay = lastReviewed,
        lapses = 0,
    )

    private fun log(id: Long, passageId: Long?, surah: Int, start: Int, end: Int, kind: ReviewKind) =
        ReviewLogEntry(id, passageId, surah, start, end, kind, ReviewGrade.GOOD, today, timestamp = id)

    private fun lesson(
        plan: HifzPlan? = plan(),
        passages: List<Passage> = emptyList(),
        todayLog: List<ReviewLogEntry> = emptyList(),
        metrics: TextMetrics = metrics(),
    ): LessonPlan = LessonBuilder.build(LessonInput(today, plan, passages, todayLog, metrics))

    private fun card(lesson: LessonPlan): LessonCardUi = LessonCardBuilder.build(lesson) { "S$it" }

    @Test
    fun aPausedDayIsTheCardOfAPausedPlanWhateverElseTheDayHolds() {
        // A plan with a passage due today and something new: on an ordinary day this is a lesson to start.
        val passages = listOf(passage(1, 113, 1, 3))
        assertEquals(LessonCardState.Start, card(lesson(passages = passages)).state)

        val pause = PlanPause(today - 1, today + 3)
        val paused = LessonCardBuilder.build(
            LessonBuilder.build(LessonInput(today, plan(), passages, emptyList(), metrics(), pause)),
        ) { "S$it" }
        assertEquals(LessonCardState.Paused, paused.state)
        assertEquals(today + 3, paused.pausedUntilEpochDay)
        assertTrue(paused.lines.isEmpty())
        assertNull(paused.tomorrow)
        assertNull(paused.oldListen)
        assertEquals(0, paused.minutes)
        assertFalse(paused.catchingUp)

        // Without a plan the paused card is still the paused card, not "make a plan".
        val noPlan = LessonCardBuilder.build(
            LessonBuilder.build(LessonInput(today, null, emptyList(), emptyList(), metrics(), pause)),
        ) { "S$it" }
        assertEquals(LessonCardState.Paused, noPlan.state)

        // The day after the pause ends is an ordinary day again.
        val after = LessonCardBuilder.build(
            LessonBuilder.build(LessonInput(today + 4, plan(), passages, emptyList(), metrics(), pause)),
        ) { "S$it" }
        assertEquals(LessonCardState.Start, after.state)
    }

    @Test
    fun noPlanAndNothingToRepeatInvitesToMakeAPlan() {
        val card = card(lesson(plan = null))
        assertEquals(LessonCardState.NoPlan, card.state)
        assertTrue(card.lines.isEmpty())
        assertNull(card.tomorrow)
    }

    @Test
    fun aFreshPlanStartsWithTheNewPassageAndAnEstimate() {
        val card = card(lesson())
        assertEquals(LessonCardState.Start, card.state)
        val line = card.lines.single()
        assertEquals(LessonLineKind.New, line.kind)
        assertEquals(114, line.surah)
        assertEquals("S114", line.surahName)
        assertEquals(1, line.startAyah)
        assertEquals(3, line.endAyah)
        assertFalse(line.done)
        assertTrue(card.minutes >= 1, "the time estimate is shown")
        assertFalse(card.catchingUp)
        assertNull(card.tomorrow, "tomorrow is only said once the lesson is done")
    }

    @Test
    fun theThreeLinesAreNewRecentOldAndSkipEmptyBlocks() {
        val recent = passage(1, 113, 1, 2, due = today) // a repetition due today
        val old = passage(2, 112, 1, 4, stage = PassageStage.OLD, due = null, lastReviewed = null)
        val card = card(lesson(passages = listOf(recent, old)))
        assertEquals(listOf(LessonLineKind.New, LessonLineKind.Recent, LessonLineKind.Old), card.lines.map { it.kind })
        assertEquals(LessonCardState.Start, card.state)
        assertEquals(1, card.lines[1].count)
        assertEquals(113, card.lines[1].surah)
        assertEquals(112, card.lines[2].surah)

        // Nothing recent and nothing old: only the new line.
        assertEquals(listOf(LessonLineKind.New), card(lesson()).lines.map { it.kind })
        // No plan but an old passage to go over: the card is still a lesson, with one line.
        val onlyOld = card(lesson(plan = null, passages = listOf(old)))
        assertEquals(LessonCardState.Start, onlyOld.state)
        assertEquals(listOf(LessonLineKind.Old), onlyOld.lines.map { it.kind })
    }

    @Test
    fun somethingDoneMakesItContinueAndKeepsTheRestToDo() {
        val learnedToday = passage(1, 114, 1, 3, due = today + 1, lastReviewed = today)
        val dueRecent = passage(2, 113, 1, 2, due = today)
        val card = card(
            lesson(
                passages = listOf(learnedToday, dueRecent),
                todayLog = listOf(log(1, 1, 114, 1, 3, ReviewKind.NEW)),
            ),
        )
        assertEquals(LessonCardState.Continue, card.state)
        val byKind = card.lines.associateBy { it.kind }
        assertTrue(byKind.getValue(LessonLineKind.New).done)
        assertFalse(byKind.getValue(LessonLineKind.Recent).done)
        assertTrue(card.minutes >= 1, "the minutes left are for what is not done")
    }

    @Test
    fun whenEverythingIsDoneTheCardIsDoneAndSaysWhatTomorrowBrings() {
        val learnedToday = passage(1, 114, 1, 3, due = today + 1, lastReviewed = today)
        val card = card(
            lesson(
                passages = listOf(learnedToday),
                todayLog = listOf(log(1, 1, 114, 1, 3, ReviewKind.NEW)),
            ),
        )
        assertEquals(LessonCardState.Done, card.state)
        assertTrue(card.lines.all { it.done })
        assertEquals(0, card.minutes)
        val tomorrow = card.tomorrow!!
        assertTrue(tomorrow.hasNew)
        assertEquals(114, tomorrow.surah)
        assertEquals(4, tomorrow.startAyah, "tomorrow's new passage starts after today's")
        assertEquals(1, tomorrow.dueCount, "today's passage is due tomorrow")
    }

    @Test
    fun aFinishedPlanWithNothingToRepeatOffersANewPlan() {
        val whole = passage(1, 114, 1, 6, stage = PassageStage.OLD, due = null, lastReviewed = today - 1)
        val card = card(lesson(passages = listOf(whole)))
        assertEquals(LessonCardState.PlanFinished, card.state)
        assertTrue(card.lines.isEmpty())
    }

    @Test
    fun aFinishedPlanWithRepetitionsLeftStillShowsTheLesson() {
        val whole = passage(1, 114, 1, 6, stage = PassageStage.R3, due = today)
        val card = card(lesson(passages = listOf(whole)))
        assertEquals(LessonCardState.Start, card.state)
        assertEquals(listOf(LessonLineKind.Recent), card.lines.map { it.kind })
    }

    @Test
    fun aBacklogCatchesUpAndTheNewPassageWaits() {
        // Six repetitions of about six minutes each do not fit into 25 minutes.
        val overdue = (1..6).map { passage(it.toLong(), 114, it, it, due = today - 2) }
        val card = card(lesson(plan = plan(order = listOf(113)), passages = overdue, metrics = metrics(charsPerAyah = 1_000)))
        assertTrue(card.catchingUp)
        val recent = card.lines.single { it.kind == LessonLineKind.Recent }
        assertTrue(recent.count < 6, "the lesson holds only what fits: ${recent.count}")
        assertTrue(card.lines.none { it.kind == LessonLineKind.New }, "a deferred new passage has no line")
        assertEquals(LessonCardState.Start, card.state)
    }

    @Test
    fun whenAllRecentPassagesAreDoneTheLineIsDone() {
        val first = passage(1, 113, 1, 2, due = today - 1)
        val card = card(
            lesson(
                plan = null,
                passages = listOf(first.copy(nextDueEpochDay = today + 1, lastReviewedEpochDay = today)),
                todayLog = listOf(log(1, 1, 113, 1, 2, ReviewKind.RECENT)),
            ),
        )
        assertEquals(LessonCardState.Done, card.state)
        val line = card.lines.single()
        assertEquals(LessonLineKind.Recent, line.kind)
        assertEquals(1, line.doneCount)
        assertTrue(line.done)
    }

    // -- The words of the card -----------------------------------------------------------------------

    @Test
    fun theLinesReadLikeTheBrief() {
        val ru = homeCopy(AppLanguage.RU)
        val new = LessonLineUi(LessonLineKind.New, 78, "Ан-Наба", 1, 5, count = 1, doneCount = 0)
        val recent = LessonLineUi(LessonLineKind.Recent, 112, "Аль-Ихлас", 1, 4, count = 3, doneCount = 0)
        val recentPartly = recent.copy(doneCount = 1)
        val old = LessonLineUi(LessonLineKind.Old, 67, "Аль-Мульк", 1, 15, count = 1, doneCount = 0)
        assertEquals("Новое: Ан-Наба 1–5", lineText(new, ru))
        assertEquals("Недавнее: 3 отрывка", lineText(recent, ru))
        assertEquals("Недавнее: 1 из 3", lineText(recentPartly, ru))
        assertEquals("Старое: Аль-Мульк 1–15", lineText(old, ru))
        assertEquals("Старое: Аль-Мульк 1–15 +2", lineText(old.copy(count = 3), ru))
        assertEquals("Новое: Ан-Нас 4", lineText(new.copy(surahName = "Ан-Нас", startAyah = 4, endAyah = 4), ru))
        val en = homeCopy(AppLanguage.EN)
        assertEquals("Recent: 3 passages", lineText(recent, en))
        assertEquals("Recent: 1 passage", lineText(recent.copy(count = 1), en))
    }

    @Test
    fun tomorrowNamesTheNewPassageAndTheDueRepetitions() {
        val ru = homeCopy(AppLanguage.RU)
        assertEquals(
            "Завтра: Ан-Наба 6–10 · повторить: 2",
            tomorrowText(LessonTomorrowUi(78, "Ан-Наба", 6, 10, dueCount = 2), ru),
        )
        assertEquals("Завтра: Ан-Наба 6–10", tomorrowText(LessonTomorrowUi(78, "Ан-Наба", 6, 10, dueCount = 0), ru))
        assertEquals("Завтра: повторить: 3", tomorrowText(LessonTomorrowUi(null, "", 0, 0, dueCount = 3), ru))
        assertEquals("Завтра: повторений нет", tomorrowText(LessonTomorrowUi(null, "", 0, 0, dueCount = 0), ru))
    }

    @Test
    fun everyLanguageFillsEveryPlaceholderOfTheCard() {
        for (language in AppLanguage.entries) {
            val copy = homeCopy(language)
            val line = LessonLineUi(LessonLineKind.New, 78, "Name", 1, 5, count = 3, doneCount = 1)
            val texts = listOf(
                lineText(line, copy),
                lineText(line.copy(kind = LessonLineKind.Recent), copy),
                lineText(line.copy(kind = LessonLineKind.Recent, doneCount = 0), copy),
                lineText(line.copy(kind = LessonLineKind.Old), copy),
                copy.lessonMinutes(12),
                copy.lessonMinutesLeft(7),
                tomorrowText(LessonTomorrowUi(78, "Name", 6, 10, dueCount = 2), copy),
                tomorrowText(LessonTomorrowUi(null, "", 0, 0, dueCount = 0), copy),
                copy.learningToast("Name"),
            )
            texts.forEach { text ->
                assertFalse(text.contains('{') || text.contains('}'), "${language.name}: $text")
                assertTrue(text.isNotBlank())
            }
            assertTrue(copy.lessonMinutes(12).contains("12"))
            assertTrue(copy.lessonMinutesLeft(7).contains("7"))
        }
    }
}
