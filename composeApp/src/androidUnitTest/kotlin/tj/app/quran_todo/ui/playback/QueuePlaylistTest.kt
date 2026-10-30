package tj.app.quran_todo.ui.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The playlist the media session shows while the listening queue plays (task F1b): the ayahs of all the passages in a row. */
class QueuePlaylistTest {
    // 102:1-8 (8 items), 103:1-3 (3 items), 112:2-3 (2 items).
    private val items = listOf(
        ListenItem(1, surah = 102, startAyah = 1, endAyah = 8),
        ListenItem(2, surah = 103, startAyah = 1, endAyah = 3),
        ListenItem(3, surah = 112, startAyah = 2, endAyah = 3),
    )

    private fun queue(position: Int) = ListenQueue(items, position = position, echo = false)

    @Test
    fun theListHasTheAyahsOfEveryPassageAndTheOffsetsAddUp() {
        assertEquals(13, QueuePlaylist.size(queue(0)))
        assertEquals(0, QueuePlaylist.offsetOf(queue(0), 0))
        assertEquals(8, QueuePlaylist.offsetOf(queue(0), 1))
        assertEquals(11, QueuePlaylist.offsetOf(queue(0), 2))
        assertEquals(13, QueuePlaylist.offsetOf(queue(0), 3))
        assertEquals(13, QueuePlaylist.offsetOf(queue(0), 99))
        assertEquals(0, QueuePlaylist.offsetOf(queue(0), -4))
    }

    @Test
    fun theCurrentItemIsTheAyahOfThePassageThePlayerIsAt() {
        assertEquals(0, QueuePlaylist.flatIndex(queue(0), 1))
        assertEquals(7, QueuePlaylist.flatIndex(queue(0), 8))
        // The last ayah of a surah has a next item: the first ayah of the next passage.
        assertEquals(8, QueuePlaylist.flatIndex(queue(1), 1))
        assertEquals(10, QueuePlaylist.flatIndex(queue(1), 3))
        // A passage that does not start at the first ayah of its surah.
        assertEquals(11, QueuePlaylist.flatIndex(queue(2), 2))
        assertEquals(12, QueuePlaylist.flatIndex(queue(2), 3))
        // An ayah outside the passage (the session is somewhere else for a moment) is held inside it.
        assertEquals(12, QueuePlaylist.flatIndex(queue(2), 99))
        assertEquals(8, QueuePlaylist.flatIndex(queue(1), 0))
    }

    @Test
    fun anItemIsFoundByItsPositionInTheList() {
        assertEquals(0 to 1, QueuePlaylist.locate(queue(0), 0))
        assertEquals(0 to 8, QueuePlaylist.locate(queue(0), 7))
        assertEquals(1 to 1, QueuePlaylist.locate(queue(0), 8))
        assertEquals(1 to 3, QueuePlaylist.locate(queue(0), 10))
        assertEquals(2 to 2, QueuePlaylist.locate(queue(0), 11))
        assertEquals(2 to 3, QueuePlaylist.locate(queue(0), 12))
        assertNull(QueuePlaylist.locate(queue(0), 13))
        assertNull(QueuePlaylist.locate(queue(0), -1))
    }

    @Test
    fun locateAndFlatIndexAreInverseToEachOther() {
        for (flat in 0 until QueuePlaylist.size(queue(0))) {
            val (position, ayah) = QueuePlaylist.locate(queue(0), flat)!!
            assertEquals(flat, QueuePlaylist.flatIndex(queue(position), ayah), "flat $flat")
        }
    }
}
