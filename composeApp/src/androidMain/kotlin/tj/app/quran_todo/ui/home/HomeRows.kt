package tj.app.quran_todo.ui.home

import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.state.LastReadPosition

internal class RowsInput(
    val language: AppLanguage,
    val surahList: List<SurahModel>,
    val index: QuranIndex,
    val chapterNames: Map<Int, ChapterNameModel>,
    /** Statuses derived from the passages; a surah without an entry is "not started". */
    val statuses: Map<Int, SurahHifzStatus>,
    /** Number of learned ayahs per surah (the passage is past the 7-day repetition); a surah without an entry has none. */
    val coveredAyahs: Map<Int, Int>,
    /** The surah the plan is learning now, if there is a plan. */
    val currentPlanSurah: Int?,
    val pinned: Set<Int>,
)

/** The status of the hifz model as the status the list components draw. */
internal fun SurahHifzStatus.toTodoStatus(): SurahTodoStatus = when (this) {
    SurahHifzStatus.LEARNING -> SurahTodoStatus.LEARNING
    SurahHifzStatus.LEARNED -> SurahTodoStatus.LEARNED
}

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
            val status = input.statuses[number]?.toTodoStatus()
            SurahRowUi(
                number = number,
                name = name,
                latinName = latin,
                translatedName = translated,
                arabicName = chapter?.arabic?.takeIf { it.isNotBlank() } ?: meta?.arabicName.orEmpty(),
                ayahCount = ayahCount,
                status = status,
                learnedFraction = learnedFraction(status, input.coveredAyahs[number] ?: 0, ayahCount),
                canOpen = meta != null,
                isPinned = number in input.pinned,
                inPlan = number == input.currentPlanSurah,
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

    /**
     * The 0..1 fraction of the ring in the top bar: the covered ayahs of the person's PLAN when there is one (the forecast's
     * numbers, the same the big ring of the Progress tab shows), otherwise of the whole Quran (U24).
     */
    fun overallProgress(coveredAyahs: Map<Int, Int>, forecast: PlanForecast? = null): Float {
        if (forecast != null && forecast.totalAyahs > 0) {
            return ((forecast.totalAyahs - forecast.remainingAyahs).toFloat() / forecast.totalAyahs).coerceIn(0f, 1f)
        }
        return (coveredAyahs.values.sum().toFloat() / QuranMeta.TotalAyahs).coerceIn(0f, 1f)
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
