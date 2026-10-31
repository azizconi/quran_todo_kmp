package tj.app.quran_todo.ui.drill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.ui.hifz.AyahMark

/** The queue and the flow of the drill of the difficult ayahs (task F2a). */
class DrillStateTest {
    private val ok = AyahMark.REMEMBERED
    private val hard = AyahMark.HARD

    private fun queue(vararg items: Pair<Int, Int>, more: Int = 0) = DrillQueue(items.map { DrillItem(it.first, it.second) }, more)

    // -- The queue ---------------------------------------------------------------------------------------

    @Test
    fun theQueueFollowsTheMushafAndAscendsInsideASurah() {
        val built = DrillQueues.build(mapOf(114 to setOf(5, 2), 2 to setOf(255, 3, 10), 78 to setOf(1)))
        assertEquals(
            listOf(2 to 3, 2 to 10, 2 to 255, 78 to 1, 114 to 2, 114 to 5),
            built.items.map { it.surah to it.ayah },
        )
        assertEquals(0, built.more)
    }

    @Test
    fun aDrillTakesAtMostTwentyAndSaysHowManyAreLeft() {
        val hardAyahs = mapOf(2 to (1..15).toSet(), 3 to (1..10).toSet())
        val built = DrillQueues.build(hardAyahs)
        assertEquals(DrillQueues.Limit, built.items.size)
        assertEquals(5, built.more, "25 ayahs: 20 now, 5 next time")
        // The first twenty are the first in the mushaf: all of surah 2, then the first five of surah 3.
        assertEquals((1..15).map { 2 to it } + (1..5).map { 3 to it }, built.items.map { it.surah to it.ayah })
    }

    @Test
    fun exactlyTwentyHasNothingLeft() {
        val built = DrillQueues.build(mapOf(5 to (1..20).toSet()))
        assertEquals(20, built.items.size)
        assertEquals(0, built.more)
    }

    @Test
    fun nothingHardIsAnEmptyQueue() {
        assertTrue(DrillQueues.build(emptyMap()).items.isEmpty())
        assertEquals(0, DrillQueues.build(emptyMap()).more)
    }

    @Test
    fun theCountBucketNeverTellsTheExactNumber() {
        assertEquals(listOf("le3", "le3", "le7", "le7", "le12", "le12", "gt12", "gt12"), listOf(1, 3, 4, 7, 8, 12, 13, 20).map(DrillQueues::countBucket))
    }

    // -- The flow ----------------------------------------------------------------------------------------

    @Test
    fun theGroupOfASurahIsWrittenWhenItsLastAyahIsAnswered() {
        var state = DrillState(queue(2 to 3, 2 to 7, 5 to 1))
        val first = state.answer(ok)
        assertNull(first.write, "the surah has another ayah to go")
        state = first.state
        val second = state.answer(hard)
        assertEquals(DrillWrite(2, remembered = setOf(3), stillHard = setOf(7)), second.write)
        state = second.state
        assertEquals(2, state.index)
        assertEquals(5 to 1, state.current?.let { it.surah to it.ayah })
        val third = state.answer(ok)
        assertEquals(DrillWrite(5, remembered = setOf(1), stillHard = emptySet()), third.write)
        assertTrue(third.state.finished)
        assertNull(third.state.current)
    }

    @Test
    fun theCountsOfTheEndAreThePlainAnswers() {
        var state = DrillState(queue(2 to 3, 2 to 7, 5 to 1, 5 to 9, 7 to 2))
        listOf(ok, hard, ok, ok, hard).forEach { state = state.answer(it).state }
        assertTrue(state.finished)
        assertEquals(3, state.remembered)
        assertEquals(2, state.stillHard)
        assertEquals(5, state.total)
    }

    @Test
    fun whatIsAnsweredInAGroupThatIsStillGoingIsWrittenWhenThePersonLeaves() {
        var state = DrillState(queue(2 to 3, 2 to 7, 2 to 9, 5 to 1))
        state = state.answer(ok).state
        state = state.answer(hard).state
        val pending = state.pending()
        assertEquals(DrillWrite(2, remembered = setOf(3), stillHard = setOf(7)), pending)
        // Written once: after the flush nothing is pending, and the next group starts clean.
        state = state.flushed()
        assertNull(state.pending())
        val rest = state.answer(ok)
        assertEquals(DrillWrite(2, remembered = setOf(9), stillHard = emptySet()), rest.write, "only the ayah not written yet")
    }

    @Test
    fun nothingIsPendingBeforeTheFirstAnswerOrAfterAClosedGroup() {
        val fresh = DrillState(queue(2 to 3, 5 to 1))
        assertNull(fresh.pending())
        val closed = fresh.answer(ok).state
        assertNull(closed.pending(), "the group of surah 2 was written when it closed")
        assertEquals(closed, closed.flushed())
    }

    @Test
    fun theAyahBeforeIsTheHintExceptForTheFirstAyahOfASurah() {
        assertEquals(2, DrillState(queue(2 to 3)).previousAyah)
        assertNull(DrillState(queue(2 to 1)).previousAyah)
        assertNull(DrillState(queue()).previousAyah)
        assertEquals(254, DrillState(queue(2 to 255)).previousAyah)
    }

    @Test
    fun anAnswerAfterTheEndIsIgnored() {
        val done = DrillState(queue(2 to 3)).answer(ok).state
        val again = done.answer(hard)
        assertEquals(done, again.state)
        assertNull(again.write)
    }

    @Test
    fun theLeftOutAyahsStayInTheQueueNumbers() {
        // "N more next time" is what the queue says; answering does not change it.
        val state = DrillState(queue(2 to 3, more = 4))
        assertEquals(4, state.answer(ok).state.queue.more)
    }
}
