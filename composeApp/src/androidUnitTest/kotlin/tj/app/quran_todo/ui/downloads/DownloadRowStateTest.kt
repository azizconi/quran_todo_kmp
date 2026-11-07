package tj.app.quran_todo.ui.downloads

import tj.app.quran_todo.common.audio.Reciters
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadRowStateTest {
    private val keys = DownloadSelection.keys(DownloadSelection.surah(112), Reciters.Alafasy) // ayah_112_1 .. ayah_112_4

    @Test
    fun aRowWithNoFilesIsNotDownloaded() {
        assertEquals(RowState.NotDownloaded, rowState(keys, emptyMap()))
        // Files of other surahs do not count.
        assertEquals(RowState.NotDownloaded, rowState(keys, mapOf("ayah_111_1" to 1000L, "ayah_2_1" to 1000L)))
    }

    @Test
    fun someFilesMakeAPartialRowWithTheirSize() {
        val files = mapOf("ayah_112_1" to 1_000L, "ayah_112_3" to 2_500L, "ayah_111_1" to 9_999L)
        assertEquals(RowState.Partial(done = 2, total = 4, bytes = 3_500L), rowState(keys, files))
    }

    @Test
    fun everyFileMakesACompleteRow() {
        val files = keys.associateWith { 1_000L } + mapOf("ayah_2_1" to 5L)
        assertEquals(RowState.Complete(total = 4, bytes = 4_000L), rowState(keys, files))
    }

    @Test
    fun theFilesOfAnotherReciterDoNotCount() {
        val husary = DownloadSelection.keys(DownloadSelection.surah(112), Reciters.Husary)
        assertEquals(RowState.NotDownloaded, rowState(keys, husary.associateWith { 1_000L }))
        assertEquals(RowState.Complete(4, 4_000L), rowState(husary, husary.associateWith { 1_000L }))
    }

    @Test
    fun aJuzIsPartialUntilEverySurahInsideIsThere() {
        val juz = DownloadSelection.keys(DownloadSelection.juz(30), Reciters.Alafasy)
        val withoutLast = juz.dropLast(1).associateWith { 500L }
        assertEquals(RowState.Partial(done = 563, total = 564, bytes = 563 * 500L), rowState(juz, withoutLast))
        assertEquals(RowState.Complete(564, 564 * 500L), rowState(juz, juz.associateWith { 500L }))
    }

    @Test
    fun aRowWithoutAyahsIsNotDownloaded() {
        assertEquals(RowState.NotDownloaded, rowState(emptyList(), mapOf("ayah_1_1" to 1L)))
    }
}
