package tj.app.quran_todo.ui.state

import kotlin.test.Test
import kotlin.test.assertEquals

class UiPrefsListsTest {
    @Test
    fun recentSurahsPutTheLatestFirstWithoutDuplicates() {
        assertEquals(listOf(5, 2, 9), listOf(2, 9).withRecent(5))
        assertEquals(listOf(9, 2, 5), listOf(2, 9, 5).withRecent(9))
    }

    @Test
    fun recentSurahsAreLimitedToEight() {
        val full = (1..8).toList()
        val next = full.withRecent(50)
        assertEquals(8, next.size)
        assertEquals(50, next.first())
        assertEquals(listOf(50, 1, 2, 3, 4, 5, 6, 7), next)
    }

    @Test
    fun reopeningTheMostRecentSurahKeepsTheList() {
        val list = listOf(3, 1, 2)
        assertEquals(list, list.withRecent(3))
    }

    @Test
    fun togglingAPinAddsItAtTheEndAndRemovesItOnTheSecondCall() {
        val pinned = emptySet<Int>().toggled(36).toggled(18)
        assertEquals(listOf(36, 18), pinned.toList())
        assertEquals(listOf(18), pinned.toggled(36).toList())
    }
}
