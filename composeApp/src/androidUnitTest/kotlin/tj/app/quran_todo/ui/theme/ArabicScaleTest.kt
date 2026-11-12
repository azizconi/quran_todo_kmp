package tj.app.quran_todo.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.a11yCopy

class ArabicScaleTest {
    @Test
    fun defaultReadingSizeKeepsTheDesignSize() {
        assertEquals(24f, scaledArabicSize(24f, 28), 0.001f)
    }

    @Test
    fun largerSettingGrowsTheTextButStaysBounded() {
        assertTrue(scaledArabicSize(24f, 34) > 24f)
        assertEquals(24f * 1.3f, scaledArabicSize(24f, 80), 0.001f)
    }

    @Test
    fun smallerSettingShrinksNoFurtherThanAFloor() {
        assertTrue(scaledArabicSize(24f, 24) < 24f)
        assertEquals(24f * 0.85f, scaledArabicSize(24f, 10), 0.001f)
    }

    @Test
    fun a11yCopyIsFilledForEveryLanguage() {
        AppLanguage.entries.forEach {
            val copy = a11yCopy(it)
            assertTrue(copy.sheetClose.isNotBlank() && copy.sheetHandle.isNotBlank())
        }
    }
}
