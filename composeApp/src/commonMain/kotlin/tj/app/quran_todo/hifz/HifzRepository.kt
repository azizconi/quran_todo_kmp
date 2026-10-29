package tj.app.quran_todo.hifz

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.NewPassageResult
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.model.SurahCoverage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.PlanForecast

/** What a repetition did to the passage. */
data class ReviewResult(
    val passageId: Long,
    /** RECENT for a passage of the ladder, OLD for a passage of the manzil circle. */
    val kind: ReviewKind,
    val stage: PassageStage,
    /** Null when the passage is OLD. */
    val nextDueEpochDay: Int?,
    /** The passage became OLD and was merged with its OLD neighbours. */
    val merged: Boolean,
)

/** The preset orders of surahs of the plan. */
object HifzPresets {
    /** Juz 30 (Amma) from the shortest surah to the longest: 114 down to 78. */
    val JUZ_30: List<Int> = (114 downTo 78).toList()

    /** Juz 29 (Tabarak) from the shortest surah to the longest: 77 down to 67. */
    val JUZ_29: List<Int> = (77 downTo 67).toList()

    /** The order of the mushaf: 1 up to 114. */
    val FROM_START: List<Int> = (1..114).toList()

    /** The order of surahs of a preset; an empty list for [HifzScope.CUSTOM] (the person picks the surahs). */
    fun order(scope: HifzScope): List<Int> = when (scope) {
        HifzScope.JUZ_30 -> JUZ_30
        HifzScope.JUZ_29 -> JUZ_29
        HifzScope.FROM_START -> FROM_START
        HifzScope.CUSTOM -> emptyList()
    }
}

/**
 * The memorization model of the app: passages, the plan, the lesson of the day and the review log. The only way the UI
 * changes the progress is through this repository (the old tables `surah_todo` and `ayah_todo` are a mirror of it for the
 * screens that are not moved yet). See `docs/redesign/API.md`, section "Хифз".
 *
 * Reads are [Flow]s: they are recomputed when a table changes (and, for the ones that take `today`, when the day changes -
 * pass `EpochDayTicker.day` through the `Flow<Int>` overloads). Writes are `suspend` functions, each one a single Room
 * transaction under one mutex: they either happen completely (including the log record and the mirror) or not at all.
 */
interface HifzRepository {
    // ---- Reads ----

    /** The lesson of the day [today] (an epoch day). */
    fun lesson(today: Int): Flow<LessonPlan>

    /** The lesson that follows a changing day, e.g. `EpochDayTicker.day`. */
    fun lesson(today: Flow<Int>): Flow<LessonPlan>

    /** The active plan; null when there is none. */
    fun plan(): Flow<HifzPlan?>

    /** The forecast of the plan; null when there is no plan. */
    fun forecast(today: Int): Flow<PlanForecast?>

    fun forecast(today: Flow<Int>): Flow<PlanForecast?>

    /** Days in a row with a record in the review log, ending today or yesterday. */
    fun streak(today: Int): Flow<Int>

    fun streak(today: Flow<Int>): Flow<Int>

    /** The derived statuses of the surahs that have one (a surah that is not in the map is not started). */
    fun surahStatuses(): Flow<Map<Int, SurahHifzStatus>>

    /** Covered and difficult ayahs and the passages of a surah. */
    fun surahCoverage(surah: Int): Flow<SurahCoverage>

    /** Records of the review log for the days [fromDay]..[toDay] inclusive, oldest first. */
    fun reviewLog(fromDay: Int, toDay: Int): Flow<List<ReviewLogEntry>>

    /**
     * Total characters of the text of all OLD passages (the weight of the manzil circle). The plan screen shows "about N pages
     * a day" = `oldWeight / HifzConstants.CHARS_PER_PAGE / manzilDays`.
     */
    fun oldWeight(): Flow<Int>

    /**
     * The time estimate of a passage in minutes, rounded up, with the very formulas of the lesson (`HifzConstants`):
     * [isNew] true - learning it for the first time, false - one repetition. `IllegalArgumentException` for a bad range.
     */
    suspend fun estimateMinutes(surah: Int, startAyah: Int, endAyah: Int, isNew: Boolean): Int

    // ---- Actions ----

    /**
     * A new passage was learned: it is stored in the stage R1, due tomorrow; a NEW record goes to the log. [hardAyahs] are
     * the ayahs the person marked as difficult. Refused ([NewPassageResult.Overlaps]) when some ayah of the range is covered.
     */
    suspend fun completeNew(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        grade: ReviewGrade,
        hardAyahs: Set<Int> = emptySet(),
        today: Int = EpochDays.now(),
    ): NewPassageResult

    /**
     * A repetition of the stored passage: the ladder, or the manzil rule for an OLD passage; a RECENT or OLD record goes to
     * the log. Null when there is no such passage.
     */
    suspend fun review(
        passageId: Long,
        grade: ReviewGrade,
        hardAyahs: Set<Int> = emptySet(),
        today: Int = EpochDays.now(),
    ): ReviewResult?

    /**
     * "Check yourself" (the reader): only a CHECK record and the difficult ayahs of the passages that cover the marked ayahs.
     * Stages and due days do not change.
     */
    suspend fun logCheck(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        grade: ReviewGrade,
        hardAyahs: Set<Int> = emptySet(),
        today: Int = EpochDays.now(),
    )

    /** "I know it by heart": the uncovered ayahs of the range become OLD passages cut by the pages of the mushaf. */
    suspend fun markKnown(
        surah: Int,
        startAyah: Int = 1,
        endAyah: Int = LAST_AYAH,
        today: Int = EpochDays.now(),
    )

    /** Takes the ayahs of the range out of the passages (a passage can be cut in two); the rest keeps its stage. */
    suspend fun forget(surah: Int, startAyah: Int = 1, endAyah: Int = LAST_AYAH)

    /**
     * Sets the plan. [surahOrder] is the queue (take it from [HifzPresets] for a preset). The start day of the plan stays when
     * only the pace, the target or the manzil days change, and is [today] when the queue changes.
     */
    suspend fun setPlan(
        scope: HifzScope,
        surahOrder: List<Int>,
        dailyNewAyahs: Int,
        targetEpochDay: Int? = null,
        manzilDays: Int = 7,
        today: Int = EpochDays.now(),
    )

    /**
     * "I am learning this surah": no plan - a `CUSTOM` plan of this surah at 5 ayahs a day; a plan exists - the surah goes to the
     * front of the queue and the scope becomes `CUSTOM`.
     */
    suspend fun learnNext(surah: Int, today: Int = EpochDays.now())

    /** Deletes the plan (passages stay). */
    suspend fun clearPlan()

    /** The one-time conversion of the old data (see `LegacyImport`); does nothing when it has run already. */
    suspend fun importLegacyIfNeeded()

    companion object {
        /** "To the last ayah of the surah" for the `endAyah` of [markKnown] and [forget]. */
        const val LAST_AYAH: Int = Int.MAX_VALUE
    }
}
