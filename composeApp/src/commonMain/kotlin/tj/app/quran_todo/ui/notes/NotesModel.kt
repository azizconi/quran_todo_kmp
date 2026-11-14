package tj.app.quran_todo.ui.notes

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity

/** One note of "My notes": the ayah it belongs to (surah and number inside the surah), the text and when it was last written. */
@Immutable
data class NoteItem(
    val surah: Int,
    val ayah: Int,
    val text: String,
    val updatedAtMillis: Long,
    /** The stored row, kept so that "Undo" can write back exactly what was deleted. */
    val entity: AyahNoteEntity,
) {
    /** The id of the row: the global ayah number (1..6236). */
    val key: Int get() = entity.ayahNumber
}

/** The pure part of "My notes" (covered by unit tests). */
object NotesLogic {
    /** The number of an ayah inside its surah from the global number the table stores; null when it does not belong to the surah. */
    fun ayahInSurah(surah: Int, globalNumber: Int): Int? {
        val count = QuranFacts.ayahCount(surah)
        if (count == 0) return null
        val ayah = globalNumber - QuranFacts.firstAyahNumber(surah) + 1
        return ayah.takeIf { it in 1..count }
    }

    /** The notes in "My notes" order: newest first; a row that points to no real ayah is left out. */
    fun items(entities: List<AyahNoteEntity>): List<NoteItem> =
        entities
            .mapNotNull { entity ->
                val ayah = ayahInSurah(entity.surahNumber, entity.ayahNumber) ?: return@mapNotNull null
                if (entity.note.isBlank()) return@mapNotNull null
                NoteItem(entity.surahNumber, ayah, entity.note, entity.updatedAt, entity)
            }
            .sortedWith(compareByDescending<NoteItem> { it.updatedAtMillis }.thenByDescending { it.key })

    /** The notes whose text contains [query] (letters in any case, edges trimmed); an empty query keeps all. */
    fun filter(items: List<NoteItem>, query: String): List<NoteItem> {
        val needle = query.trim()
        if (needle.isEmpty()) return items
        return items.filter { it.text.contains(needle, ignoreCase = true) }
    }
}
