package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.presentation.home.PendingSurahStatus
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.state.LastReadPosition

internal class RowsInput(
    val language: AppLanguage,
    val surahList: List<SurahModel>,
    val index: QuranIndex,
    val chapterNames: Map<Int, ChapterNameModel>,
    /** Saved statuses; a surah without an entry is "not started". */
    val statuses: Map<Int, SurahTodoStatus>,
    /** Changes that are still being saved; they win over [statuses] so the UI answers at once. */
    val pending: Map<Int, PendingSurahStatus>,
    /** Number of memorized ayahs per surah. */
    val learnedAyahs: Map<Int, Int>,
    val pinned: Set<Int>,
)

/** The 114 rows of the list (port of `buildLibraryRows` from the old library screen). */
internal object HomeRows {
    fun build(input: RowsInput): List<SurahRowUi> {
        val russian = input.surahList.associateBy { it.surahNumber }
        // The loaded text decides which surahs can be opened; before it arrives the bundled list
        // still gives names (such rows cannot be opened).
        val numbers = if (!input.index.isEmpty) {
            input.index.surahs.map { it.number }
        } else {
            input.surahList.map { it.surahNumber }.sorted()
        }
        return numbers.map { number ->
            val meta = input.index.meta(number)
            val chapter = input.chapterNames[number]
            val ru = russian[number]
            val fallback = meta?.englishName?.takeIf { it.isNotBlank() }
                ?: ru?.name?.let(::cleanRussianName)
                ?: "Surah $number"
            val latin = chapter?.transliteration?.takeIf { it.isNotBlank() } ?: fallback
            val name = SurahNames.displayName(input.language, ru?.name, chapter?.transliteration, fallback)
            val translated = (chapter?.translated?.takeIf { it.isNotBlank() }
                ?: meta?.englishTranslation?.takeIf { it.isNotBlank() }
                ?: "")
                .takeUnless { it.equals(name, ignoreCase = true) }
                .orEmpty()
            val ayahCount = meta?.ayahCount ?: ru?.ayats ?: QuranMeta.ayahCount(number)
            val status = if (input.pending.containsKey(number)) {
                input.pending.getValue(number).status
            } else {
                input.statuses[number]
            }
            SurahRowUi(
                number = number,
                name = name,
                latinName = latin,
                translatedName = translated,
                arabicName = chapter?.arabic?.takeIf { it.isNotBlank() } ?: meta?.arabicName.orEmpty(),
                ayahCount = ayahCount,
                status = status,
                learnedFraction = learnedFraction(status, input.learnedAyahs[number] ?: 0, ayahCount),
                canOpen = meta != null,
                isPinned = number in input.pinned,
            )
        }
    }

    internal fun learnedFraction(status: SurahTodoStatus?, learnedAyahs: Int, ayahCount: Int): Float = when {
        status == SurahTodoStatus.LEARNED -> 1f
        ayahCount <= 0 -> 0f
        else -> (learnedAyahs.toFloat() / ayahCount).coerceIn(0f, 1f)
    }

    /** Kept for existing callers; the rule lives in [SurahNames]. */
    fun cleanRussianName(raw: String): String = SurahNames.cleanRussianName(raw)

    /** Memorized ayahs of the whole Quran (memorized surahs count in full) as a 0..1 fraction. */
    fun overallProgress(rows: List<SurahRowUi>): Float {
        val learned = rows.sumOf { row ->
            if (row.status == SurahTodoStatus.LEARNED) row.ayahCount else (row.learnedFraction * row.ayahCount).toInt()
        }
        return (learned.toFloat() / QuranMeta.TotalAyahs).coerceIn(0f, 1f)
    }

    /** Pinned surahs first (in the order they were pinned), then the recent ones; no duplicates. */
    fun recent(
        recentSurahs: List<Int>,
        pinned: Set<Int>,
        rowsByNumber: Map<Int, SurahRowUi>,
    ): List<RecentUi> {
        val ordered = (pinned.toList() + recentSurahs).distinct()
        return ordered.mapNotNull { number ->
            val row = rowsByNumber[number] ?: return@mapNotNull null
            RecentUi(surah = number, name = row.name, pinned = number in pinned)
        }
    }

    /**
     * Where the "Continue" hero points. Order: the position saved by the reader, then the old audio
     * position (its ayah is a global number that is converted through [index]), then Al-Fatihah ayah 1
     * marked as fresh.
     */
    fun resolveContinue(
        last: LastReadPosition?,
        index: QuranIndex,
        rowsByNumber: Map<Int, SurahRowUi>,
    ): ContinueUi? {
        val known = last?.takeIf { it.surah in rowsByNumber }
        val surah = known?.surah ?: 1
        val ayah = when {
            known?.ayahInSurah != null -> known.ayahInSurah
            known?.legacyGlobalAyah != null -> index.localAyahInSurah(surah, known.legacyGlobalAyah)
            else -> null
        }
        val row = rowsByNumber[surah] ?: return null
        val fresh = known == null
        return ContinueUi(
            surah = row.number,
            name = row.name,
            arabicName = row.arabicName,
            translatedName = row.translatedName,
            ayah = (ayah ?: 1).coerceIn(1, row.ayahCount.coerceAtLeast(1)),
            ayahCount = row.ayahCount,
            isFresh = fresh,
            canOpen = row.canOpen,
        )
    }
}
