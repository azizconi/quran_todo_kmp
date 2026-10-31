package tj.app.quran_todo.hifz

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.hifz.backup.BackupData
import tj.app.quran_todo.hifz.backup.VerifiedBackup
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.NewPassageResult
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.PlanPause
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

    /**
     * The forecast of [plan] as if it were the active plan, for the plan screen to preview a draft that is not saved. Nothing is
     * stored; the covered ayahs and the review log are the real ones, [HifzPlan.startedEpochDay] is the one of the draft.
     */
    fun forecast(plan: HifzPlan, today: Int): Flow<PlanForecast>

    /** Days in a row with a record in the review log, ending today or yesterday; the days of a pause (task F2b) are a bridge. */
    fun streak(today: Int): Flow<Int>

    fun streak(today: Flow<Int>): Flow<Int>

    /**
     * The pause of the plan (task F2b) that is on or still ahead of [today]; null when there is none (a pause that is over is null
     * too). `lesson(today).paused` says whether [today] itself is inside the pause.
     */
    fun pause(today: Int): Flow<PlanPause?>

    /** The pause that follows a changing day, e.g. `EpochDayTicker.day`. */
    fun pause(today: Flow<Int>): Flow<PlanPause?>

    /** The derived statuses of the surahs that have one (a surah that is not in the map is not started). */
    fun surahStatuses(): Flow<Map<Int, SurahHifzStatus>>

    /**
     * How many ayahs of each surah are covered by passages: only the surahs with at least one covered ayah are in the map.
     * One subscription for the whole list of surahs (the list shows "23 of 40" in a row without 114 subscriptions).
     */
    fun coveredCounts(): Flow<Map<Int, Int>>

    /**
     * The covered ayahs of each surah (numbers in the surah): only the surahs with at least one covered ayah are in the map. For
     * progress that does not follow surah boundaries (a juz starts inside a surah); one subscription for the whole Quran.
     */
    fun coveredAyahs(): Flow<Map<Int, Set<Int>>>

    /**
     * The difficult ayahs of every surah (numbers in the surah, ascending): only the surahs with at least one are in the map. One
     * subscription for the whole Quran: the progress screen counts and lists the difficult ayahs without 114 `surahCoverage`.
     */
    fun hardAyahs(): Flow<Map<Int, Set<Int>>>

    /** Covered and difficult ayahs and the passages of a surah. */
    fun surahCoverage(surah: Int): Flow<SurahCoverage>

    /**
     * Records of the study in the review log (NEW, RECENT, OLD, CHECK) for the days [fromDay]..[toDay] inclusive, oldest first.
     * `LISTEN` records are not here: listening is not a repetition, see [listenLog].
     */
    fun reviewLog(fromDay: Int, toDay: Int): Flow<List<ReviewLogEntry>>

    /**
     * The `LISTEN` records ("I repeated it by ear", task F1b) for the days [fromDay]..[toDay] inclusive, oldest first. They are
     * shown ("Listened ✓", "Listened: N passages") and never counted as repetitions.
     */
    fun listenLog(fromDay: Int, toDay: Int): Flow<List<ReviewLogEntry>>

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

    /**
     * The drill of the difficult ayahs (task F2a), one surah at a time: [remembered] ayahs get their counter `clearsAfter` one
     * lower (at 0 the mark is gone), [stillHard] ayahs start over at 2. Only the marks of these ayahs change: stages, due days,
     * `lastReviewed` and lapses stay as they are, so the lesson, the ladder and the circle see nothing. One `CHECK` record goes
     * to the log: `startAyah`..`endAyah` are the first and the last trained ayah, `passageId` is the passage that covers the
     * whole range (null when it spans several), the grade follows the Recall rule (nothing still hard - `GOOD`, less than half of the trained ayahs - `HARD`, half or more - `FORGOT`). An ayah that
     * is not marked difficult any more (or not covered) is skipped; when none is left nothing is written. The mirror of the surah
     * is recomputed (as in [logCheck]: only the marks change, so it writes nothing).
     */
    suspend fun drillHard(
        surah: Int,
        remembered: Set<Int>,
        stillHard: Set<Int>,
        today: Int = EpochDays.now(),
    )

    /**
     * "Listened: I repeated it by ear" (task F1b): one `LISTEN` record per stored passage of [passageIds] (an unknown id is skipped)
     * with the neutral grade GOOD. Nothing else changes: not the stage, the due day, `lastReviewed`, the circle, the streak or the
     * difficult ayahs - only "Recall" grades a passage.
     */
    suspend fun logListened(passageIds: List<Long>, today: Int = EpochDays.now())

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

    /**
     * "Quiz" (task F2b): marks the ayahs [ayahs] (numbers in the surah) as difficult (`clearsAfter = 2`, a mark that is there starts
     * over) in the passages that cover them; an ayah that is not covered is skipped. One transaction, the mirror of the surah is
     * rewritten. Nothing else changes: no stage, no due day, no `lastReviewed`, and **no record in the review log** (the lesson, the
     * streak and the retention do not see a quiz). [today] is accepted for symmetry with the other actions and is not used.
     */
    suspend fun markHard(surah: Int, ayahs: Set<Int>, today: Int = EpochDays.now())

    /**
     * Pauses the plan for [days] (1..14, forced into the range) from [today] (task F2b): the lessons stop, the calendar of the plan
     * moves by [days] (due days of the ladder, `lastReviewed` of every passage that has one, the target and the start day of the plan;
     * a null stays null) and the pause is stored in `hifz_meta`. A pause that is on already is extended: its end becomes
     * `today + days - 1` (never further than `today + 13`) and the calendar moves only by the difference. One transaction.
     */
    suspend fun pausePlan(days: Int, today: Int = EpochDays.now())

    /**
     * "Continue now" (task F2b): the pause that is on ends yesterday (one that started today is removed whole) and the calendar moves
     * back by the days that were left; what was done today before the pause stays done. Does nothing when there is no pause.
     */
    suspend fun resumePlan(today: Int = EpochDays.now())

    /** The one-time conversion of the old data (see `LegacyImport`); does nothing when it has run already. */
    suspend fun importLegacyIfNeeded()

    // ---- Backup file (see `hifz/backup/BackupFormat.kt`) ----

    /**
     * Everything a backup file holds from the database - the plan, passages, difficult ayahs, the review log, the service
     * values and the notes - read in ONE read transaction, so the tables agree with each other.
     */
    suspend fun exportBackupData(): BackupData

    /**
     * Replaces ALL the hifz data and the notes with those of [backup] in ONE transaction: the tables are emptied, the file is
     * written into them, `surah_todo` and `ayah_todo` are rebuilt from the passages for every surah (the mirror) and
     * `hifz_meta.legacy_import = done` is set, so that the one-time conversion of the old tables never runs over the imported
     * data. An error at any point rolls everything back: the previous data stays as it was. The settings of the file are not
     * touched here (they are not in the database).
     */
    suspend fun replaceWithBackup(backup: VerifiedBackup)

    companion object {
        /** "To the last ayah of the surah" for the `endAyah` of [markKnown] and [forget]. */
        const val LAST_AYAH: Int = Int.MAX_VALUE
    }
}
