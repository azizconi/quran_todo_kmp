package tj.app.quran_todo.ui.reader.components

import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.ui.reader.AyahSchedule

/** What the ayah sheet shows about one ayah. */
class AyahSheetModel(
    val ayah: AyahEntity,
    val index: Int,
    val surahName: String,
    val arabic: String,
    val translation: String?,
    /** The ayah is covered by a passage (it can be taken out of the repetitions). */
    val learned: Boolean,
    /** LEARNED when its passage is past the 7-day repetition, LEARNING while it is still being learned, null when not covered. */
    val status: AyahTodoStatus? = null,
    /** The ayah is marked as difficult. */
    val hard: Boolean,
    /** When a learned ayah comes back; null when it is not learned. */
    val schedule: AyahSchedule?,
    val note: String,
)
