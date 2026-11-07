package tj.app.quran_todo.ui.notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity

class NotesLogicTest {
    private fun note(surah: Int, ayah: Int, text: String, at: Long) =
        AyahNoteEntity(ayahNumber = QuranFacts.firstAyahNumber(surah) + ayah - 1, surahNumber = surah, note = text, updatedAt = at)

    @Test
    fun globalNumberBecomesAyahInSurah() {
        assertEquals(1, NotesLogic.ayahInSurah(1, 1))
        assertEquals(7, NotesLogic.ayahInSurah(1, 7))
        assertEquals(1, NotesLogic.ayahInSurah(2, 8))
        assertEquals(286, NotesLogic.ayahInSurah(2, 293))
        assertEquals(6, NotesLogic.ayahInSurah(114, 6236))
        // A row of another surah's number does not belong here.
        assertNull(NotesLogic.ayahInSurah(1, 8))
        assertNull(NotesLogic.ayahInSurah(115, 1))
    }

    @Test
    fun itemsAreNewestFirstAndBrokenRowsAreSkipped() {
        val items = NotesLogic.items(
            listOf(
                note(112, 1, "old", at = 100),
                note(2, 255, "newest", at = 300),
                note(1, 3, "middle", at = 200),
                AyahNoteEntity(ayahNumber = 5, surahNumber = 114, note = "orphan", updatedAt = 400),
                note(113, 1, "   ", at = 500),
            ),
        )
        assertEquals(listOf("newest", "middle", "old"), items.map { it.text })
        assertEquals(255, items.first().ayah)
        assertEquals(2, items.first().surah)
    }

    @Test
    fun searchLooksInTheTextIgnoringCaseAndEdges() {
        val items = NotesLogic.items(
            listOf(
                note(1, 1, "Patience in hardship", at = 3),
                note(1, 2, "gratitude", at = 2),
                note(1, 3, "Terms of PATIENCE", at = 1),
            ),
        )
        assertEquals(items, NotesLogic.filter(items, ""))
        assertEquals(items, NotesLogic.filter(items, "   "))
        assertEquals(listOf("Patience in hardship", "Terms of PATIENCE"), NotesLogic.filter(items, "  patience ").map { it.text })
        assertEquals(emptyList(), NotesLogic.filter(items, "zzz"))
        // Cyrillic folds too.
        val ru = NotesLogic.items(listOf(note(1, 1, "Терпение", at = 1)))
        assertEquals(1, NotesLogic.filter(ru, "терпен").size)
    }
}
