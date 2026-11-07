package tj.app.quran_todo.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopBarFadeTest {
    @Test
    fun theOverlineIsFullyGoneWithinTheFirstFortyPercent() {
        assertEquals(1f, overlineAlpha(0f))
        assertEquals(0.5f, overlineAlpha(0.2f), 1e-6f)
        assertEquals(0f, overlineAlpha(0.4f), 1e-6f)
        assertEquals(0f, overlineAlpha(0.7f))
        assertEquals(0f, overlineAlpha(1f))
    }

    @Test
    fun theCompactTitleWaitsForTheOverlineWhenThereIsOne() {
        assertEquals(0f, compactTitleAlpha(0f, hasOverline = true))
        assertEquals(0f, compactTitleAlpha(0.4f, hasOverline = true), 1e-6f)
        assertEquals(1f, compactTitleAlpha(1f, hasOverline = true))
        assertTrue(compactTitleAlpha(0.7f, hasOverline = true) in 0.01f..0.99f)
    }

    @Test
    fun theOverlineAndTheCompactTitleNeverShowAtTheSameTime() {
        var fraction = 0f
        while (fraction <= 1f) {
            val both = minOf(overlineAlpha(fraction), compactTitleAlpha(fraction, hasOverline = true))
            assertEquals(0f, both, 1e-6f, "overlap at $fraction")
            fraction += 0.01f
        }
    }

    @Test
    fun withoutAnOverlineTheCompactTitleFadesInWithTheCollapse() {
        assertEquals(0f, compactTitleAlpha(0f, hasOverline = false))
        assertEquals(0.5f, compactTitleAlpha(0.5f, hasOverline = false))
        assertEquals(1f, compactTitleAlpha(1f, hasOverline = false))
        assertEquals(1f, compactTitleAlpha(1.4f, hasOverline = false))
    }
}
