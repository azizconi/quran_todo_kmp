package tj.app.quran_todo.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainTabTest {
    @Test
    fun theBarHasTheThreeTabsInOrder() {
        assertEquals(listOf(MainTab.Quran, MainTab.Lesson, MainTab.Progress), MainTab.entries.toList())
    }

    @Test
    fun theAppAlwaysOpensOnTheQuran() {
        assertEquals(MainTab.Quran, MainTab.Default)
        assertEquals(0, MainTab.Default.ordinal)
    }

    @Test
    fun aSavedTabIsRestoredOrTheDefaultIsUsed() {
        assertEquals(MainTab.Progress, MainTab.restore(MainTab.Progress.ordinal, MainTab.Quran))
        assertEquals(MainTab.Quran, MainTab.restore(null, MainTab.Quran))
        assertEquals(MainTab.Quran, MainTab.restore(7, MainTab.Quran))
        assertEquals(MainTab.Quran, MainTab.restore(-1, MainTab.Quran))
    }

    @Test
    fun backLeadsToTheQuranAndLeavesTheAppFromThere() {
        assertEquals(MainTab.Quran, MainTab.backTarget(MainTab.Lesson, MainTab.Default))
        assertEquals(MainTab.Quran, MainTab.backTarget(MainTab.Progress, MainTab.Default))
        assertNull(MainTab.backTarget(MainTab.Quran, MainTab.Default))
    }

    @Test
    fun screenNamesStayTheSameForAnalytics() {
        assertEquals("home", MainTab.Lesson.screenName)
        assertEquals("progress", MainTab.Progress.screenName)
        assertEquals("quran", MainTab.Quran.screenName)
    }
}
