package tj.app.quran_todo.ui.hifz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.presentation.review.ReviewQuality

class HifzSessionTest {
    /** A surah of 12 ayahs whose first ayah has the global number 100. */
    private val ayahs = (1..12).map { inSurah ->
        AyahEntity(
            number = 99 + inSurah,
            numberInSurah = inSurah,
            juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
            text = "text $inSurah",
            surahNumber = 7,
        )
    }

    private fun HifzSession.numbers() = items.map { it.inSurah }

    /** A session over exactly these ayahs of the surah (numbers in the surah), unrated. */
    private fun sessionOf(vararg inSurah: Int): HifzSession {
        val whole = assertNotNull(HifzSessions.range(7, ayahs, inSurah.min(), inSurah.max()))
        return whole.copy(items = whole.items.filter { it.inSurah in inSurah.toList() })
    }

    /** Reveals and rates every ayah in turn. */
    private fun HifzSession.rateAll(vararg qualities: ReviewQuality): HifzSession =
        qualities.fold(this) { current, quality -> current.reveal().rate(quality) }

    // -- Building --------------------------------------------------------------------------------------------

    @Test
    fun aRangeIsTheAyahsFromStartToEndInclusive() {
        val session = assertNotNull(HifzSessions.range(7, ayahs, 3, 6))
        assertEquals(listOf(3, 4, 5, 6), session.numbers())
        assertEquals(listOf(102, 103, 104, 105), session.items.map { it.number })
        assertEquals(7, session.surah)
        assertEquals(12, session.totalInSurah)
        assertEquals(0, session.index)
        assertFalse(session.revealed)
    }

    @Test
    fun aReversedRangeIsPutRight() {
        assertEquals(listOf(4, 5, 6), HifzSessions.range(7, ayahs, 6, 4)?.numbers())
    }

    @Test
    fun aRangeIsClampedToTheAyahsThatExist() {
        assertEquals(listOf(11, 12), HifzSessions.range(7, ayahs, 11, 40)?.numbers())
        assertEquals(listOf(1, 2), HifzSessions.range(7, ayahs, -3, 2)?.numbers())
    }

    @Test
    fun aRangeWithoutAyahsMakesNoSession() {
        assertNull(HifzSessions.range(7, ayahs, 20, 30))
        assertNull(HifzSessions.range(7, emptyList(), 1, 3))
    }

    @Test
    fun aSingleAyahRangeIsOneAyah() {
        assertEquals(listOf(9), HifzSessions.range(7, ayahs, 9, 9)?.numbers())
    }

    @Test
    fun fromAnAyahRunsToTheEndOfTheSurah() {
        val session = assertNotNull(HifzSessions.fromAyah(7, ayahs, startIndex = 8))
        assertEquals(listOf(9, 10, 11, 12), session.numbers())
    }

    @Test
    fun fromAnAyahStopsAtTheEndOfTheRepeatPassage() {
        assertEquals(listOf(3, 4, 5), HifzSessions.fromAyah(7, ayahs, startIndex = 2, endIndex = 4)?.numbers())
        // A passage that ends where the session starts is a session of that one ayah.
        assertEquals(listOf(5), HifzSessions.fromAyah(7, ayahs, startIndex = 4, endIndex = 4)?.numbers())
    }

    @Test
    fun aPassageThatEndsBeforeTheStartIsIgnored() {
        assertEquals(listOf(9, 10, 11, 12), HifzSessions.fromAyah(7, ayahs, startIndex = 8, endIndex = 2)?.numbers())
    }

    @Test
    fun fromAnAyahClampsTheStartAndTheEnd() {
        assertEquals(listOf(12), HifzSessions.fromAyah(7, ayahs, startIndex = 99)?.numbers())
        assertEquals(12, HifzSessions.fromAyah(7, ayahs, startIndex = -5)?.size)
        assertEquals(listOf(11, 12), HifzSessions.fromAyah(7, ayahs, startIndex = 10, endIndex = 50)?.numbers())
        assertNull(HifzSessions.fromAyah(7, emptyList(), startIndex = 0))
    }

    // -- Running ---------------------------------------------------------------------------------------------

    @Test
    fun anAyahCanBeRatedOnlyAfterItIsRevealed() {
        val fresh = sessionOf(1, 2, 3)
        assertEquals(fresh, fresh.rate(ReviewQuality.GOOD))
        val rated = fresh.reveal().rate(ReviewQuality.GOOD)
        assertEquals(1, rated.index)
        assertEquals(ReviewQuality.GOOD, rated.items[0].rating)
        assertFalse(rated.revealed, "the next ayah starts hidden")
    }

    @Test
    fun theWholeSessionWalksThroughItsAyahsInOrder() {
        val given = listOf(ReviewQuality.EASY, ReviewQuality.HARD, ReviewQuality.FORGOT)
        var current = sessionOf(1, 2, 3)
        given.forEachIndexed { position, quality ->
            assertEquals(position, current.index)
            assertFalse(current.finished)
            current = current.reveal().rate(quality)
        }
        assertTrue(current.finished)
        assertNull(current.current)
        assertEquals(given, current.items.map { it.rating })
        assertEquals(3, current.ratedCount)
        assertEquals(0, current.remaining)
    }

    @Test
    fun aFinishedSessionIgnoresFurtherCommands() {
        val finished = sessionOf(1).rateAll(ReviewQuality.GOOD)
        assertTrue(finished.finished)
        assertEquals(finished, finished.reveal())
        assertEquals(finished, finished.rate(ReviewQuality.EASY))
    }

    @Test
    fun phasesFollowThePosition() {
        val running = sessionOf(3, 4, 5).rateAll(ReviewQuality.GOOD)
        assertEquals(HifzPhase.Outside, running.phaseOf(2))
        assertEquals(HifzPhase.Done, running.phaseOf(3))
        assertEquals(HifzPhase.Current, running.phaseOf(4))
        assertEquals(HifzPhase.Upcoming, running.phaseOf(5))
        assertEquals(HifzPhase.Outside, running.phaseOf(6))
        assertEquals(ReviewQuality.GOOD, running.itemOf(3)?.rating)
        assertNull(running.itemOf(2))
    }

    @Test
    fun theBreakdownCountsEveryRating() {
        val done = sessionOf(1, 2, 3, 4, 5).rateAll(
            ReviewQuality.EASY, ReviewQuality.GOOD, ReviewQuality.GOOD, ReviewQuality.HARD, ReviewQuality.FORGOT,
        )
        assertEquals(1, done.count(ReviewQuality.EASY))
        assertEquals(2, done.count(ReviewQuality.GOOD))
        assertEquals(1, done.count(ReviewQuality.HARD))
        assertEquals(1, done.count(ReviewQuality.FORGOT))
        assertEquals(0.6f, done.recallShare, 1e-6f)
        assertEquals(60, done.recallPercent())
    }

    @Test
    fun theRecallShareBeforeAnyRatingIsZero() {
        assertEquals(0f, sessionOf(1, 2).recallShare)
    }

    // -- Repeating the hard ones -------------------------------------------------------------------------------

    @Test
    fun theHardOnesAreTheAyahsRatedHardOrForgotten() {
        val done = sessionOf(1, 2, 3, 4, 5).rateAll(
            ReviewQuality.HARD, ReviewQuality.GOOD, ReviewQuality.FORGOT, ReviewQuality.EASY, ReviewQuality.HARD,
        )
        val retry = assertNotNull(done.retryHard())
        assertEquals(listOf(1, 3, 5), retry.numbers())
        assertEquals(listOf(100, 102, 104), retry.items.map { it.number })
        assertTrue(retry.items.all { it.rating == null }, "the new session starts unrated")
        assertEquals(0, retry.index)
        assertFalse(retry.revealed)
        assertEquals(2, retry.round)
        assertEquals(7, retry.surah)
        assertEquals(12, retry.totalInSurah)
    }

    @Test
    fun withoutHardOnesThereIsNothingToRepeat() {
        val done = sessionOf(1, 2).rateAll(ReviewQuality.GOOD, ReviewQuality.EASY)
        assertNull(done.retryHard())
        assertTrue(done.hardItems.isEmpty())
    }

    @Test
    fun aRetryOfARetryKeepsCountingRounds() {
        val first = sessionOf(1, 2).rateAll(ReviewQuality.HARD, ReviewQuality.HARD)
        val second = assertNotNull(first.retryHard()).rateAll(ReviewQuality.FORGOT, ReviewQuality.GOOD)
        val third = assertNotNull(second.retryHard())
        assertEquals(listOf(1), third.numbers())
        assertEquals(3, third.round)
    }

    // -- Saving ------------------------------------------------------------------------------------------------

    @Test
    fun aSessionSurvivesBeingSavedAsText() {
        val current = sessionOf(4, 5, 6, 7).rateAll(ReviewQuality.FORGOT, ReviewQuality.EASY).reveal()
        val restored = assertNotNull(HifzSession.decode(current.encode()))
        assertEquals(current, restored)
        assertEquals(2, restored.index)
        assertTrue(restored.revealed)
        assertEquals(ReviewQuality.FORGOT, restored.items[0].rating)
        assertNull(restored.items[2].rating)
    }

    @Test
    fun aFinishedSessionSurvivesToo() {
        val done = sessionOf(1, 2).rateAll(ReviewQuality.GOOD, ReviewQuality.HARD)
        assertEquals(done, HifzSession.decode(done.encode()))
    }

    @Test
    fun aRetryRoundKeepsItsRound() {
        val retry = assertNotNull(sessionOf(1, 2).rateAll(ReviewQuality.HARD, ReviewQuality.GOOD).retryHard())
        assertEquals(2, HifzSession.decode(retry.encode())?.round)
    }

    @Test
    fun garbageIsNotASession() {
        assertNull(HifzSession.decode(null))
        assertNull(HifzSession.decode(""))
        assertNull(HifzSession.decode(HifzViewModel.SavedOff))
        assertNull(HifzSession.decode("h2|7|12|0|0|1|100:1:-1"))
        assertNull(HifzSession.decode("h1|7|12|0|0|1|"))
        assertNull(HifzSession.decode("h1|7|12|0|0|1|100:1"))
        assertNull(HifzSession.decode("h1|7|12|5|0|1|100:1:-1"))
        assertNull(HifzSession.decode("h1|7|12|0|2|1|100:1:-1"))
        assertNull(HifzSession.decode("h1|7|12|0|0|1|100:1:9"))
        assertNull(HifzSession.decode("h1|x|12|0|0|1|100:1:-1"))
    }

    @Test
    fun aRevealedFlagOnAFinishedSessionIsDropped() {
        // index == size means nothing is left to reveal.
        val decoded = assertNotNull(HifzSession.decode("h1|7|12|1|1|1|100:1:2"))
        assertTrue(decoded.finished)
        assertFalse(decoded.revealed)
    }

    // -- The hint ----------------------------------------------------------------------------------------------

    @Test
    fun theFirstWordHintNeedsASecondWord() {
        assertEquals(0, firstWordEnd("الٓمٓ"))
        assertEquals(0, firstWordEnd("طه "))
        assertEquals("ذَٰلِكَ".length, firstWordEnd("ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ"))
        assertEquals(3, firstWordEnd("abc def"))
        assertEquals(0, firstWordEnd(" leading space"))
    }
}
