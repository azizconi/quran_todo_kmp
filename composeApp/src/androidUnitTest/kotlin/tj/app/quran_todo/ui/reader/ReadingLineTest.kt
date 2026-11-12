package tj.app.quran_todo.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/** The ayah counted as "being read" for the top bar counter (ReadingLine.kt). */
class ReadingLineTest {
    private val height = 1000 // reading line at 200

    @Test
    fun aSliverOfThePreviousAyahUnderTheTopBarDoesNotCount() {
        // 2:254's translation tail (item 254) shows 60 px; 2:255 (item 255) fills the rest.
        val items = listOf(ListItemSpan(254, -500, 560), ListItemSpan(255, 60, 1400))
        assertEquals(254, ayahOnReadingLine(items, height, atEnd = false, total = 286)) // index 254 = ayah 255
    }

    @Test
    fun theAyahCrossingTheLineCounts() {
        val items = listOf(ListItemSpan(3, -100, 400), ListItemSpan(4, 300, 300))
        assertEquals(2, ayahOnReadingLine(items, height, atEnd = false, total = 7))
    }

    @Test
    fun theHeaderOnTopIsReported() {
        val items = listOf(ListItemSpan(0, 0, 700), ListItemSpan(1, 700, 200))
        assertEquals(ReaderViewModel.HeaderOnTop, ayahOnReadingLine(items, height, atEnd = false, total = 7))
    }

    @Test
    fun atTheEndTheLastAyahOnScreenCounts() {
        // Item 287 is the footer ("next surah"), below the last ayah.
        val items = listOf(ListItemSpan(284, -50, 500), ListItemSpan(285, 450, 200), ListItemSpan(286, 650, 150), ListItemSpan(287, 800, 300))
        assertEquals(285, ayahOnReadingLine(items, height, atEnd = true, total = 286))
    }

    @Test
    fun nothingLaidOutIsNotMeasured() {
        assertEquals(ReaderViewModel.NotMeasured, ayahOnReadingLine(emptyList(), height, atEnd = false, total = 7))
        assertEquals(ReaderViewModel.NotMeasured, ayahOnReadingLine(listOf(ListItemSpan(1, 0, 10)), 0, atEnd = false, total = 7))
    }
}
