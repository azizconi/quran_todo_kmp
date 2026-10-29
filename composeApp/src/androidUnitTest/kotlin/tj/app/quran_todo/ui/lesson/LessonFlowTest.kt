package tj.app.quran_todo.ui.lesson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.LessonItem
import tj.app.quran_todo.hifz.schedule.LessonNew
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.LessonTomorrow
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.HifzItem
import tj.app.quran_todo.ui.hifz.HifzSession
import tj.app.quran_todo.ui.i18n.lessonCopy

class LessonFlowTest {
    private fun newBlock(state: LessonNewState = LessonNewState.PENDING) = LessonNew(
        surah = 78, startAyah = 1, endAyah = 5, state = state,
        grade = if (state == LessonNewState.DONE) ReviewGrade.GOOD else null,
        passageId = null, estimateSeconds = 120,
    )

    private fun item(id: Long, done: Boolean = false, surah: Int = 79) = LessonItem(
        passageId = id, surah = surah, startAyah = 1, endAyah = 5, stage = PassageStage.R3, hardAyahs = emptySet(),
        done = done, grade = if (done) ReviewGrade.GOOD else null, estimateSeconds = 60, overdueDays = 0,
    )

    private fun plan(
        new: LessonNew? = null,
        recent: List<LessonItem> = emptyList(),
        old: List<LessonItem> = emptyList(),
        complete: Boolean = false,
    ) = LessonPlan(
        today = 20_000, hasPlan = true, planFinished = false, newBlock = new, recent = recent, old = old,
        totalMinutes = 10, remainingMinutes = 5, catchingUp = false, deferredCount = 0,
        isComplete = complete, isEmpty = false,
        tomorrow = LessonTomorrow(newPassage = null, dueCount = 0),
    )

    // -- The current card --------------------------------------------------------------------------------------

    @Test
    fun theNewPassageComesFirst() {
        val card = LessonCards.current(plan(new = newBlock(), recent = listOf(item(1)), old = listOf(item(2))))
        assertIs<LessonCard.New>(card)
        assertEquals("new", card.key)
        assertEquals(LessonBlock.New, card.block)
    }

    @Test
    fun aNewPassageThatIsDoneOrMovedToLaterIsSkipped() {
        for (state in listOf(LessonNewState.DONE, LessonNewState.DEFERRED)) {
            val card = LessonCards.current(plan(new = newBlock(state), recent = listOf(item(1))))
            assertIs<LessonCard.Review>(card)
            assertEquals(1L, card.item.passageId)
        }
    }

    @Test
    fun theRecentBlockComesBeforeTheOldBlock() {
        val card = LessonCards.current(plan(recent = listOf(item(1)), old = listOf(item(2))))
        assertIs<LessonCard.Review>(card)
        assertEquals(LessonBlock.Recent, card.block)
        assertEquals("p1", card.key)
    }

    @Test
    fun theFirstCardThatIsNotDoneCountsFromTheDoneOnes() {
        val recent = listOf(item(1, done = true), item(2, done = true), item(3), item(4), item(5))
        val card = LessonCards.current(plan(recent = recent))
        assertIs<LessonCard.Review>(card)
        assertEquals(3L, card.item.passageId)
        assertEquals(3, card.position, "the third of five")
        assertEquals(5, card.total)
    }

    @Test
    fun theOldBlockCountsThePositionByTheDoneOnesToo() {
        // The portion is in mushaf order: a card that is not done may stand before a done one.
        val old = listOf(item(1), item(2, done = true), item(3))
        val card = LessonCards.current(plan(old = old))
        assertIs<LessonCard.Review>(card)
        assertEquals(LessonBlock.Old, card.block)
        assertEquals(1L, card.item.passageId)
        assertEquals(2, card.position)
        assertEquals(3, card.total)
    }

    @Test
    fun whenEverythingIsDoneThereIsNoCard() {
        val done = plan(
            new = newBlock(LessonNewState.DONE), recent = listOf(item(1, done = true)),
            old = listOf(item(2, done = true)), complete = true,
        )
        assertNull(LessonCards.current(done))
    }

    // -- The steps of the header -------------------------------------------------------------------------------

    @Test
    fun stepsAreOnlyTheBlocksThatHaveSomethingInThem() {
        assertEquals(emptyList(), LessonCards.steps(plan()))
        val steps = LessonCards.steps(plan(new = newBlock(), recent = listOf(item(1, done = true), item(2))))
        assertEquals(
            listOf(LessonStep(LessonBlock.New, 0, 1), LessonStep(LessonBlock.Recent, 1, 2)),
            steps,
        )
    }

    @Test
    fun aNewPassageMovedToLaterIsNotAStep() {
        val steps = LessonCards.steps(plan(new = newBlock(LessonNewState.DEFERRED), old = listOf(item(1))))
        assertEquals(listOf(LessonBlock.Old), steps.map { it.block })
    }

    @Test
    fun aDoneNewPassageIsACompleteStep() {
        val step = LessonCards.steps(plan(new = newBlock(LessonNewState.DONE))).single()
        assertEquals(1, step.done)
        assertTrue(step.complete)
    }

    @Test
    fun theCompleteBlocksAreTheOnesWithEverythingDone() {
        val plan = plan(
            new = newBlock(LessonNewState.DONE),
            recent = listOf(item(1, done = true), item(2, done = true)),
            old = listOf(item(3, done = true), item(4)),
        )
        assertEquals(setOf(LessonBlock.New, LessonBlock.Recent), LessonCards.completeBlocks(plan))
    }

    @Test
    fun theMinutesBucketHidesTheExactNumber() {
        assertEquals("le5", LessonCards.minutesBucket(3))
        assertEquals("le5", LessonCards.minutesBucket(5))
        assertEquals("le10", LessonCards.minutesBucket(6))
        assertEquals("le15", LessonCards.minutesBucket(15))
        assertEquals("le25", LessonCards.minutesBucket(25))
        assertEquals("gt25", LessonCards.minutesBucket(40))
    }

    // -- Phases ------------------------------------------------------------------------------------------------

    @Test
    fun aNewPassageGoesListenReadRecall() {
        val card = LessonCard.New(newBlock())
        var phase = CardPhase.first(card)
        val walked = mutableListOf(phase)
        while (true) phase = phase.next()?.also { walked += it } ?: break
        assertEquals(listOf(CardPhase.Listen, CardPhase.Read, CardPhase.Recall), walked)
    }

    @Test
    fun aRepetitionGoesIntroRecall() {
        val card = LessonCard.Review(item(7), LessonBlock.Recent, 1, 1)
        assertEquals(CardPhase.Intro, CardPhase.first(card))
        assertEquals(CardPhase.Recall, CardPhase.Intro.next())
        assertNull(CardPhase.Recall.next(), "the card ends by grading it, not by another phase")
    }

    // -- The position inside a card as text --------------------------------------------------------------------

    private fun recall() = HifzSession(
        surah = 78,
        items = listOf(HifzItem(1, 1, AyahMark.HARD), HifzItem(2, 2), HifzItem(3, 3)),
        index = 1,
        revealed = true,
    )

    @Test
    fun aPositionInRecallSurvivesBeingSavedAsText() {
        val progress = CardProgress("p42", CardPhase.Recall, recall())
        assertEquals(progress, CardProgress.decode(progress.encode()))
    }

    @Test
    fun aPositionBeforeRecallHasNoSession() {
        val progress = CardProgress("new", CardPhase.Read)
        assertEquals(progress, CardProgress.decode(progress.encode()))
    }

    @Test
    fun garbageIsNotAPosition() {
        assertNull(CardProgress.decode(null))
        assertNull(CardProgress.decode(""))
        assertNull(CardProgress.decode("c1~new~9~"))
        assertNull(CardProgress.decode("c2~new~0~"))
        assertNull(CardProgress.decode("c1~~0~"))
        assertNull(CardProgress.decode("c1~new~0"))
        // Recall without its session cannot be put back.
        assertNull(CardProgress.decode("c1~new~2~"))
        assertNull(CardProgress.decode("c1~new~2~garbage"))
    }

    @Test
    fun theFirstProgressOfACardStartsAtItsFirstPhase() {
        val card = LessonCard.New(newBlock())
        assertEquals(CardProgress("new", CardPhase.Listen), CardProgress.first(card))
    }

    // -- The sound of "Listen" ---------------------------------------------------------------------------------

    @Test
    fun everyAyahPlaysNTimesThenThePassageMTimes() {
        assertEquals(
            listOf(1, 1, 2, 2, 3, 3, 1, 2, 3, 1, 2, 3),
            ListenPlan.sequence(startAyah = 1, endAyah = 3, ayahRepeats = 2, passageRepeats = 2),
        )
    }

    @Test
    fun theDefaultsAreThreeAndTwo() {
        val sequence = ListenPlan.sequence(6, 10, ayahRepeats = 3, passageRepeats = 2)
        assertEquals(5 * 3 + 5 * 2, sequence.size)
        assertEquals(listOf(6, 6, 6, 7, 7, 7), sequence.take(6))
        assertEquals((6..10).toList() + (6..10).toList(), sequence.takeLast(10))
    }

    @Test
    fun withoutPassagePassesOnlyTheAyahsRepeat() {
        assertEquals(listOf(4, 4, 5, 5), ListenPlan.sequence(4, 5, ayahRepeats = 2, passageRepeats = 0))
    }

    @Test
    fun aPassageOfOneAyahIsOnlyItsOwnRepeats() {
        assertEquals(listOf(9, 9, 9), ListenPlan.sequence(9, 9, ayahRepeats = 3, passageRepeats = 2))
    }

    @Test
    fun anAyahPlaysAtLeastOnce() {
        assertEquals(listOf(1, 2), ListenPlan.sequence(1, 2, ayahRepeats = 0, passageRepeats = 0))
    }

    @Test
    fun aBackwardRangeIsEmpty() {
        assertEquals(emptyList(), ListenPlan.sequence(5, 3, ayahRepeats = 3, passageRepeats = 2))
    }

    @Test
    fun listenFirstIsOnePassOverThePassage() {
        assertEquals(listOf(3, 4, 5), ListenPlan.once(3, 5))
    }

    // -- Copy --------------------------------------------------------------------------------------------------

    @Test
    fun tomorrowIsBuiltFromWhatWaits() {
        val ru = lessonCopy(AppLanguage.RU)
        assertEquals("Завтра: Ан-Наба 6–10 и 3 повторения", ru.tomorrow("Ан-Наба 6–10", 3))
        assertEquals("Завтра: Ан-Наба 6–10", ru.tomorrow("Ан-Наба 6–10", 0))
        assertEquals("Завтра: 1 повторение", ru.tomorrow(null, 1))
        assertEquals("Завтра: 5 повторений", ru.tomorrow(null, 5))
        assertEquals("До завтра", ru.tomorrow(null, 0))
        assertEquals("Tomorrow: 1 repetition", lessonCopy(AppLanguage.EN).tomorrow(null, 1))
        assertEquals("Tomorrow: An-Naba 6–10 and 2 repetitions", lessonCopy(AppLanguage.EN).tomorrow("An-Naba 6–10", 2))
    }
}
