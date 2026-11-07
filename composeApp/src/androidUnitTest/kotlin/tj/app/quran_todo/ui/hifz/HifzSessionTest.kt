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
        assertNull(HifzSession.decode("h4|7|0|0|100:1:-1:0::0"))
    }

    @Test
    fun aRevealedFlagOnAFinishedSessionIsDropped() {
        // index == size means nothing is left to reveal.
        val decoded = assertNotNull(HifzSession.decode("h2|7|1|1|100:1:0"))
        assertTrue(decoded.finished)
        assertFalse(decoded.revealed)
    }

    // -- Peeking -----------------------------------------------------------------------------------------------

    @Test
    fun aTapOnAHiddenWordOpensItAndCountsOnePeek() {
        val fresh = sessionOf(1, 2, 3)
        val peeked = fresh.peekWord(0, 4)
        assertEquals(setOf(4), peeked.items[0].peeked)
        assertEquals(1, peeked.items[0].peeks)
        // The same word again is not another peek.
        assertEquals(peeked, peeked.peekWord(0, 4))
        val twice = peeked.peekWord(0, 1)
        assertEquals(setOf(1, 4), twice.items[0].peeked)
        assertEquals(2, twice.items[0].peeks)
        assertTrue(twice.items[0].suggestsHard)
        assertFalse(peeked.items[0].suggestsHard)
    }

    @Test
    fun holdingTheWholeAyahCountsAsTwoPeeks() {
        val held = sessionOf(1, 2).peekAyah(0)
        assertEquals(WholeAyahPeeks, held.items[0].peeks)
        assertEquals(2, WholeAyahPeeks)
        assertTrue(held.items[0].suggestsHard, "a hold alone makes \"Hard\" the main answer")
        assertTrue(held.items[0].peeked.isEmpty(), "no word is marked by a hold")
        assertEquals(4, held.peekAyah(0).items[0].peeks)
    }

    @Test
    fun onlyTheAyahBeingRecalledCanBePeekedAtAndOnlyBeforeTheReveal() {
        val fresh = sessionOf(1, 2, 3)
        assertEquals(fresh, fresh.peekWord(1, 0), "an ayah that waits its turn")
        assertEquals(fresh, fresh.peekAyah(2))
        assertEquals(fresh, fresh.peekWord(7, 0), "no such position")
        assertEquals(fresh, fresh.peekWord(0, -1))
        val revealed = fresh.reveal()
        assertEquals(revealed, revealed.peekWord(0, 2))
        assertEquals(revealed, revealed.peekAyah(0))
        val moved = fresh.peekWord(0, 1).reveal().mark(ok)
        assertEquals(moved, moved.peekWord(0, 3), "an answered ayah")
        assertEquals(sessionOf(1).answerAll(ok), sessionOf(1).answerAll(ok).peekWord(0, 0))
    }

    @Test
    fun thePeeksStayOnTheAyahAfterItIsAnswered() {
        val answered = sessionOf(1, 2, 3).peekWord(0, 2).peekWord(0, 5).reveal().mark(ok)
        assertEquals(2, answered.items[0].peeks)
        assertEquals(setOf(2, 5), answered.items[0].peeked)
        assertEquals(0, answered.items[1].peeks, "the next ayah starts clean")
    }

    @Test
    fun aPeekedAyahAnsweredRememberedIsNotHardOnItsOwn() {
        // The suggestion is only the order of the buttons: the person decides, and the grade sheet starts from the answers.
        val done = sessionOf(1, 2, 3).peekWord(0, 0).peekWord(0, 1).reveal().mark(ok).reveal().mark(ok).reveal().mark(ok)
        assertTrue(done.items[0].suggestsHard)
        assertEquals(emptySet(), done.hardAyahs)
        assertEquals(ReviewGrade.GOOD, done.suggestedGrade)
    }

    @Test
    fun theProgressiveStepMovesThroughTheQuartersOfTheWords() {
        // Ten words: 3, 5, 8, then all of them hidden.
        var session = sessionOf(1, 2)
        val seen = ArrayList<Int>()
        repeat(4) {
            session = session.hideMore(0, wordCount = 10)
            seen += session.items[0].hideStep
        }
        assertEquals(listOf(1, 2, 3, 4), seen)
        assertEquals(session, session.hideMore(0, wordCount = 10), "already hidden entirely")
        assertEquals(0, session.items[1].hideStep, "the next ayah is untouched")
        // A one-word ayah goes from whole straight to hidden.
        assertEquals(4, sessionOf(1).hideMore(0, wordCount = 1).items[0].hideStep)
    }

    @Test
    fun theAnswersWithoutARevealAreForTheProgressiveHint() {
        val fresh = sessionOf(1, 2)
        assertEquals(fresh, fresh.mark(ok), "the plain answer still needs the reveal")
        val answered = fresh.hideMore(0, 8).markWithoutReveal(hard)
        assertEquals(1, answered.index)
        assertEquals(hard, answered.items[0].mark)
        assertFalse(answered.revealed)
        assertEquals(sessionOf(1).answerAll(ok), sessionOf(1).answerAll(ok).markWithoutReveal(hard))
    }

    @Test
    fun peeksAndStepsSurviveBeingSavedAsText() {
        val current = sessionOf(4, 5, 6).peekWord(0, 3).peekWord(0, 12).reveal().mark(ok).hideMore(1, 9).hideMore(1, 9).peekAyah(1)
        assertEquals(current, assertNotNull(HifzSession.decode(current.encode())))
        val restored = assertNotNull(HifzSession.decode(current.encode()))
        assertEquals(setOf(3, 12), restored.items[0].peeked)
        assertEquals(2, restored.items[1].hideStep)
        assertEquals(2, restored.items[1].peeks)
        assertTrue(current.encode().startsWith("h3|"))
    }

    @Test
    fun anOldTextFormIsStillReadWithoutPeeks() {
        val decoded = assertNotNull(HifzSession.decode("h2|7|1|1|100:1:0,101:2:-1,102:3:1"))
        assertEquals(listOf(0, 0, 0), decoded.items.map { it.peeks })
        assertEquals(listOf(emptySet(), emptySet(), emptySet()), decoded.items.map { it.peeked })
        assertEquals(listOf(0, 0, 0), decoded.items.map { it.hideStep })
        assertEquals(listOf(AyahMark.REMEMBERED, null, AyahMark.HARD), decoded.items.map { it.mark })
        assertEquals(1, decoded.index)
        assertTrue(decoded.revealed)
    }

    @Test
    fun aBrokenNewTextFormIsNotASession() {
        assertNull(HifzSession.decode("h3|7|0|0|100:1:-1"), "the new form needs its three extra fields")
        assertNull(HifzSession.decode("h3|7|0|0|100:1:-1:x::0"))
        assertNull(HifzSession.decode("h3|7|0|0|100:1:-1:-2::0"))
        assertNull(HifzSession.decode("h3|7|0|0|100:1:-1:0:a.b:0"))
        assertNull(HifzSession.decode("h3|7|0|0|100:1:-1:0:-3:0"))
        assertNull(HifzSession.decode("h3|7|0|0|100:1:-1:0::5"))
        assertNotNull(HifzSession.decode("h3|7|0|0|100:1:-1:3:0.4.9:2"))
    }
}
