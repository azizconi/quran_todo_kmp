package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.hifz.model.SurahHifzStatus

/** What has to change in the old tables for one surah. */
class MirrorDelta(
    val surah: Int,
    /** Global numbers of the `ayah_todo` rows to delete. */
    val deleteAyahNumbers: List<Int>,
    val upsertAyahs: List<AyahTodoEntity>,
    val upsertSurah: SurahTodoEntity?,
    val deleteSurah: Boolean,
) {
    val isEmpty: Boolean get() = deleteAyahNumbers.isEmpty() && upsertAyahs.isEmpty() && upsertSurah == null && !deleteSurah
}

/**
 * Until the screens read the passages (progress, reader, home), the old screens read `surah_todo` and `ayah_todo`. So every
 * action of the repository rewrites these rows for the surahs it touched, from the passages:
 *
 * - `ayah_todo`: LEARNED rows for exactly the covered ayahs (global numbers); every other row of the surah is deleted;
 * - `surah_todo`: the derived status of the surah; no status means no row.
 *
 * `ayah_review` is not mirrored.
 */
object LegacyMirror {
    fun globalAyahNumber(surah: Int, ayahInSurah: Int): Int = QuranFacts.firstAyahNumber(surah) + ayahInSurah - 1

    /**
     * [coveredAyahs] are numbers in the surah. Rows that are right already are kept as they are (their `updatedAt` too),
     * so a repetition that does not change the coverage writes nothing.
     */
    fun sync(
        surah: Int,
        coveredAyahs: Set<Int>,
        status: SurahHifzStatus?,
        existingAyahRows: List<AyahTodoEntity>,
        existingSurahRow: SurahTodoEntity?,
        now: Long,
    ): MirrorDelta {
        val wanted = coveredAyahs.associateBy { globalAyahNumber(surah, it) }
        val existing = existingAyahRows.associateBy { it.ayahNumber }
        val delete = existing.keys.filter { it !in wanted }.sorted()
        val upsert = wanted.keys.sorted().mapNotNull { number ->
            val row = existing[number]
            if (row != null && row.status == AyahTodoStatus.LEARNED && row.surahNumber == surah) {
                null
            } else {
                AyahTodoEntity(ayahNumber = number, surahNumber = surah, status = AyahTodoStatus.LEARNED, updatedAt = now)
            }
        }
        val wantedSurahStatus = when (status) {
            SurahHifzStatus.LEARNED -> SurahTodoStatus.LEARNED
            SurahHifzStatus.LEARNING -> SurahTodoStatus.LEARNING
            null -> null
        }
        val upsertSurah = if (wantedSurahStatus != null && existingSurahRow?.status != wantedSurahStatus) {
            SurahTodoEntity(surahNumber = surah, status = wantedSurahStatus)
        } else {
            null
        }
        return MirrorDelta(
            surah = surah,
            deleteAyahNumbers = delete,
            upsertAyahs = upsert,
            upsertSurah = upsertSurah,
            deleteSurah = wantedSurahStatus == null && existingSurahRow != null,
        )
    }
}
