package tj.app.quran_todo.ui.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeRevealTest {
    @Test
    fun withoutAHostTheChangeRunsAtOnce() {
        var ran = 0
        ThemeReveal().reveal { ran++ }
        assertEquals(1, ran)
    }

    @Test
    fun systemBarsFollowTheThemeWhenNoRevealRuns() {
        val reveal = ThemeReveal()
        assertTrue(reveal.barIsDark(newDark = true, top = true))
        assertFalse(reveal.barIsDark(newDark = false, top = false))
    }
}
