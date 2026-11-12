package tj.app.quran_todo.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoToRulesTest {
    @Test
    fun limitIsTheAyahCountOfTheSurah() {
        assertEquals(286, GoToRules.limit(2))
        assertEquals(7, GoToRules.limit(1))
        assertEquals(6, GoToRules.limit(114))
    }

    @Test
    fun plainNumberAboveTheLimitBecomesTheLimit() {
        assertEquals("7", GoToRules.sanitize(1, "9"))
        assertEquals("286", GoToRules.sanitize(2, "999"))
        assertEquals("255", GoToRules.sanitize(2, "255"))
    }

    @Test
    fun surahChangeMovesTheLimit() {
        assertEquals("286", GoToRules.sanitize(2, "250".plus("0")))
        assertEquals("6", GoToRules.onSurahChanged(114, "255"))
        assertEquals("5", GoToRules.onSurahChanged(114, "5"))
        assertEquals("", GoToRules.onSurahChanged(1, ""))
    }

    @Test
    fun sanitizeKeepsDigitsAndOneSeparator() {
        assertEquals("12", GoToRules.sanitize(2, "1a2"))
        assertEquals("2:255", GoToRules.sanitize(1, "2.255"))
        assertEquals("2:25", GoToRules.sanitize(1, "2:2:5"))
        assertEquals("5", GoToRules.sanitize(2, "٥"))
        assertEquals("", GoToRules.sanitize(2, ":"))
    }

    @Test
    fun resolveOfAPlainNumberStaysInTheSurah() {
        assertEquals(GoToTarget(2, 255), GoToRules.resolve(2, "255"))
        assertEquals(GoToTarget(2, 1), GoToRules.resolve(2, "1"))
        assertNull(GoToRules.resolve(2, "0"))
        assertNull(GoToRules.resolve(2, "287"))
        assertNull(GoToRules.resolve(1, "8"))
        assertNull(GoToRules.resolve(2, ""))
    }

    @Test
    fun resolveReadsASurahAndAyahReference() {
        assertEquals(GoToTarget(2, 255), GoToRules.resolve(1, "2:255"))
        assertEquals(GoToTarget(112, 4), GoToRules.resolve(2, "112:4"))
        assertNull(GoToRules.resolve(2, "112:5"))
        assertNull(GoToRules.resolve(2, "115:1"))
        assertNull(GoToRules.resolve(2, "2:"))
    }
}
