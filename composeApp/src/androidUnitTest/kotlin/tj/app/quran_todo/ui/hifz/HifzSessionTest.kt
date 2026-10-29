package tj.app.quran_todo.ui.hifz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.hifz.model.ReviewGrade

class HifzSessionTest {
    /** A surah of 25 ayahs whose first ayah has the global number 100. */
    private val ayahs = (1..25).map { inSurah ->
        AyahEntity(
            number = 99 + inSurah,
            numberInSurah = inSurah,
            juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
            text = "text $inSurah",
            surahNumber = 7,
        )
    }

    private fun HifzSession.numbers() = items.map { it.inSurah }

    /** A session over exactly these ayahs of the surah (numbers in the surah), unanswered. */
    private fun sessionOf(vararg inSurah: Int): HifzSession {
        val whole = assertNotNull(HifzSessions.range(7, ayahs, inSurah.min(), inSurah.max()))
        return whole.copy(items = whole.items.filter { it.inSurah in inSurah.toList() })
    }

    /** Reveals and answers every ayah in turn. */
    private fun HifzSession.answerAll(vararg marks: AyahMark): HifzSession =
        marks.fold(this) { current, mark -> current.reveal().mark(mark) }

    private val ok = AyahMark.REMEMBERED
    private val hard = AyahMark.HARD

    // -- Building --------------------------------------------------------------------------------------------

    @Test
    fun aRangeIsTheAyahsFromStartToEndInclusive() {
        val session = assertNotNull(HifzSessions.range(7, ayahs, 3, 6))
        assertEquals(listOf(3, 4, 5, 6), session.numbers())
        assertEquals(listOf(102, 103, 104, 105), session.items.map { it.number })
        assertEquals(7, session.surah)
        assertEquals(0, session.index)
        assertFalse(session.revealed)
        assertEquals(3, session.firstAyah)
        assertEquals(6, session.lastAyah)
    }

    @Test
    fun aReversedRangeIsPutRight() {
        assertEquals(listOf(4, 5, 6), HifzSessions.range(7, ayahs, 6, 4)?.numbers())
    }

    @Test
    fun aRangeIsClampedToTheAyahsThatExist() {
        assertEquals(listOf(24, 25), HifzSessions.range(7, ayahs, 24, 40)?.numbers())
        assertEquals(listOf(1, 2), HifzSessions.range(7, ayahs, -3, 2)?.numbers())
    }

    @Test
    fun aRangeWithoutAyahsMakesNoSession() {
        assertNull(HifzSessions.range(7, ayahs, 30, 40))
        assertNull(HifzSessions.range(7, emptyList(), 1, 3))
    }

    @Test
    fun aSingleAyahRangeIsOneAyah() {
        assertEquals(listOf(9), HifzSessions.range(7, ayahs, 9, 9)?.numbers())
    }

    @Test
    fun fromAnAyahTakesAtMostTenAyahs() {
        val session = assertNotNull(HifzSessions.fromAyah(7, ayahs, startIndex = 2))
        assertEquals((3..12).toList(), session.numbers())
        assertEquals(HifzSessions.MaxFromHere, session.size)
    }

    @Test
    fun fromAnAyahNearTheEndStopsAtTheEndOfTheSurah() {
        assertEquals(listOf(23, 24, 25), HifzSessions.fromAyah(7, ayahs, startIndex = 22)?.numbers())
    }

    @Test
    fun fromAnAyahStopsAtTheEndOfTheRepeatPassageEvenWhenLonger() {
        assertEquals(listOf(3, 4, 5), HifzSessions.fromAyah(7, ayahs, startIndex = 2, endIndex = 4)?.numbers())
        assertEquals(15, HifzSessions.fromAyah(7, ayahs, startIndex = 0, endIndex = 14)?.size)
        // A passage that ends where the session starts is a session of that one ayah.
        assertEquals(listOf(5), HifzSessions.fromAyah(7, ayahs, startIndex = 4, endIndex = 4)?.numbers())
    }

    @Test
    fun aPassageThatEndsBeforeTheStartIsIgnored() {
        assertEquals((9..18).toList(), HifzSessions.fromAyah(7, ayahs, startIndex = 8, endIndex = 2)?.numbers())
    }

    @Test
    fun fromAnAyahClampsTheStart() {
        assertEquals(listOf(25), HifzSessions.fromAyah(7, ayahs, startIndex = 99)?.numbers())
        assertEquals(10, HifzSessions.fromAyah(7, ayahs, startIndex = -5)?.size)
        assertNull(HifzSessions.fromAyah(7, emptyList(), startIndex = 0))
    }

    // -- Running ---------------------------------------------------------------------------------------------

    @Test
    fun anAyahCanBeAnsweredOnlyAfterItIsRevealed() {
        val fresh = sessionOf(1, 2, 3)
        assertEquals(fresh, fresh.mark(ok))
        val answered = fresh.reveal().mark(ok)
        assertEquals(1, answered.index)
        assertEquals(ok, answered.items[0].mark)
        assertFalse(answered.revealed, "the next ayah starts hidden")
    }

    @Test
    fun theWholeSessionWalksThroughItsAyahsInOrder() {
        val given = listOf(ok, hard, ok)
        var current = sessionOf(1, 2, 3)
        given.forEachIndexed { position, mark ->
            assertEquals(position, current.index)
            assertFalse(current.finished)
            current = current.reveal().mark(mark)
        }
        assertTrue(current.finished)
        assertNull(current.current)
        assertEquals(given, current.items.map { it.mark })
        assertEquals(3, current.markedCount)
        assertEquals(0, current.remaining)
    }

    @Test
    fun aFinishedSessionIgnoresFurtherCommands() {
        val finished = sessionOf(1).answerAll(ok)
        assertTrue(finished.finished)
        assertEquals(finished, finished.reveal())
        assertEquals(finished, finished.mark(hard))
    }

    @Test
    fun phasesFollowThePosition() {
        val running = sessionOf(3, 4, 5).answerAll(hard)
        assertEquals(HifzPhase.Outside, running.phaseOf(2))
        assertEquals(HifzPhase.Done, running.phaseOf(3))
        assertEquals(HifzPhase.Current, running.phaseOf(4))
        assertEquals(HifzPhase.Upcoming, running.phaseOf(5))
        assertEquals(HifzPhase.Outside, running.phaseOf(6))
        assertEquals(hard, running.itemOf(3)?.mark)
        assertNull(running.itemOf(2))
    }

    @Test
    fun theHardAyahsAreTheOnesAnsweredHard() {
        val done = sessionOf(1, 2, 3, 4, 5).answerAll(hard, ok, hard, ok, ok)
        assertEquals(setOf(1, 3), done.hardAyahs)
        assertEquals(emptySet(), sessionOf(1, 2).answerAll(ok, ok).hardAyahs)
    }

    // -- The suggested grade -----------------------------------------------------------------------------------

    @Test
    fun theSessionOffersTheGradeThatFollowsFromItsAnswers() {
        assertEquals(ReviewGrade.GOOD, sessionOf(1, 2, 3, 4).answerAll(ok, ok, ok, ok).suggestedGrade)
        assertEquals(ReviewGrade.HARD, sessionOf(1, 2, 3, 4).answerAll(ok, hard, ok, ok).suggestedGrade)
        assertEquals(ReviewGrade.FORGOT, sessionOf(1, 2, 3, 4).answerAll(hard, hard, ok, ok).suggestedGrade)
    }

    // -- Saving ------------------------------------------------------------------------------------------------

    @Test
    fun aSessionSurvivesBeingSavedAsText() {
        val current = sessionOf(4, 5, 6, 7).answerAll(hard, ok).reveal()
        val restored = assertNotNull(HifzSession.decode(current.encode()))
        assertEquals(current, restored)
        assertEquals(2, restored.index)
        assertTrue(restored.revealed)
        assertEquals(hard, restored.items[0].mark)
        assertNull(restored.items[2].mark)
    }

    @Test
    fun aFinishedSessionSurvivesToo() {
        val done = sessionOf(1, 2).answerAll(ok, hard)
        assertEquals(done, HifzSession.decode(done.encode()))
    }

    @Test
    fun garbageIsNotASession() {
        assertNull(HifzSession.decode(null))
        assertNull(HifzSession.decode(""))
        assertNull(HifzSession.decode(HifzViewModel.SavedOff))
        // The old format (rating per ayah) is not read.
        assertNull(HifzSession.decode("h1|7|12|0|0|1|100:1:-1"))
        assertNull(HifzSession.decode("h2|7|0|0|"))
        assertNull(HifzSession.decode("h2|7|0|0|100:1"))
        assertNull(HifzSession.decode("h2|7|5|0|100:1:-1"))
        assertNull(HifzSession.decode("h2|7|0|2|100:1:-1"))
        assertNull(HifzSession.decode("h2|7|0|0|100:1:9"))
        assertNull(HifzSession.decode("h2|x|0|0|100:1:-1"))
    }

    @Test
    fun aRevealedFlagOnAFinishedSessionIsDropped() {
        // index == size means nothing is left to reveal.
        val decoded = assertNotNull(HifzSession.decode("h2|7|1|1|100:1:0"))
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
