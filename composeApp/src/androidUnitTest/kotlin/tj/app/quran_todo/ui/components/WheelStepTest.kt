package tj.app.quran_todo.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.a11yCopy
import kotlin.test.assertTrue

class WheelStepTest {
    @Test
    fun stepsByOne() {
        assertEquals(6, wheelStep(5, 1, 10, 1))
        assertEquals(4, wheelStep(5, 1, 10, -1))
    }

    @Test
    fun clampsAtTheEnds() {
        assertEquals(10, wheelStep(10, 1, 10, 1))
        assertEquals(1, wheelStep(1, 1, 10, -1))
    }

    @Test
    fun degenerateRangeStaysPut() {
        assertEquals(3, wheelStep(3, 3, 3, 1))
        assertEquals(3, wheelStep(3, 3, 1, -1))
    }

    @Test
    fun wheelActionsAreTranslated() {
        AppLanguage.entries.forEach {
            val c = a11yCopy(it)
            assertTrue(c.wheelMore.isNotBlank() && c.wheelLess.isNotBlank() && c.wheelMore != c.wheelLess)
        }
    }
}
