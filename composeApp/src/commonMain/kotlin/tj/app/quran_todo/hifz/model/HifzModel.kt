package tj.app.quran_todo.hifz.model

/**
 * The ladder of a freshly learned passage and the final stage. The name says "next time in N days":
 * `R1` tomorrow, `R3` in three days ... `R30` in a month; after `R30` the passage becomes [OLD] and is
 * repeated in the manzil circle (see `hifz/schedule/Manzil.kt`). The enum is stored by name in
 * `hifz_passage.stage`.
 */
enum class PassageStage(val intervalDays: Int?) {
    R1(1), R3(3), R7(7), R14(14), R30(30), OLD(null);

    /** Index in the ladder (0 for R1 ... 4 for R30), 5 for [OLD]. */
    val rank: Int get() = ordinal

    val isLadder: Boolean get() = this != OLD

    companion object {
        /** The stage for the ladder rank, anything past R30 is [OLD]. */
        fun ofRank(rank: Int): PassageStage = entries[rank.coerceIn(0, OLD.ordinal)]
    }
}

/** What the person chose as the plan: a preset (a fixed order of surahs) or their own list. */
enum class HifzScope { JUZ_30, JUZ_29, FROM_START, CUSTOM }

/**
 * Where a record of the review log comes from. The enum is stored by name in `review_log.kind`.
 *
 * [LISTEN] (task F1b, "Listen to the old") is "I listened to this passage again": a help, not an exam, so it moves nothing.
 * The ladder, the manzil circle, "done today", the streak, retention, difficult ayahs, the forecast and the reminder never see
 * it: the readers of the log that schedule or count ([tj.app.quran_todo.hifz.HifzRepository.reviewLog], `lesson`, `streak`)
 * leave it out, and the pure functions of `hifz/schedule` ignore it by kind too. It is read only through
 * [tj.app.quran_todo.hifz.HifzRepository.listenLog] and written through `logListened` with the neutral grade
 * [ReviewGrade.GOOD] (a grade is required by the table; nobody reads it for this kind).
 */
enum class ReviewKind {
    NEW, RECENT, OLD, CHECK, LISTEN;

    /** The record is about studying (a new passage, a repetition or a check); false for [LISTEN]. */
    val countsAsStudy: Boolean get() = this != LISTEN
}

/** How a recitation went. The order matters: worse first. */
enum class ReviewGrade { FORGOT, HARD, GOOD, EASY }

/** The status of a surah that follows from its passages (no status = the surah is not started). */
enum class SurahHifzStatus { LEARNING, LEARNED }

/**
 * A continuous range of ayahs of ONE surah (numbers in the surah) that is learned and repeated as a whole.
 * [id] is 0 for a passage that is not stored yet. [hard] are the ayahs the person marked as difficult:
 * `ayahInSurah -> clearsAfter` (how many successful repetitions are left before the mark goes away).
 * All days are epoch days (calendar days of the device time zone).
 */
data class Passage(
    val id: Long,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val learnedEpochDay: Int,
    val stage: PassageStage,
    val nextDueEpochDay: Int?,
    val lastReviewedEpochDay: Int?,
    val lapses: Int,
    val hard: Map<Int, Int> = emptyMap(),
) {
    val ayahCount: Int get() = endAyah - startAyah + 1

    operator fun contains(ayah: Int): Boolean = ayah in startAyah..endAyah
}

/** The one active plan (the row `hifz_plan.id = 1`). */
data class HifzPlan(
    val scope: HifzScope,
    /** Surahs in the order they are learned. */
    val surahOrder: List<Int>,
    /** New ayahs a day, 1..20. */
    val dailyNewAyahs: Int,
    /** The day the person wants to finish by, if any. */
    val targetEpochDay: Int?,
    val startedEpochDay: Int,
    /** In how many days the manzil circle goes round, 3..30. */
    val manzilDays: Int,
)

/**
 * A pause of the plan (task F2b): the lessons stop for the days [fromEpochDay]..[untilEpochDay], both included, and come back on
 * [resumeEpochDay]. The calendar of the plan is moved by the length of the pause (see `PlanPauses`), so no repetition piles up and the
 * streak (which bridges the pause days) stays alive.
 */
data class PlanPause(val fromEpochDay: Int, val untilEpochDay: Int) {
    init {
        require(untilEpochDay >= fromEpochDay) { "the pause ends before it starts: $fromEpochDay..$untilEpochDay" }
    }

    /** The first day with a lesson again. */
    val resumeEpochDay: Int get() = untilEpochDay + 1

    /** How many days the pause lasts, 1 or more. */
    val dayCount: Int get() = untilEpochDay - fromEpochDay + 1

    operator fun contains(epochDay: Int): Boolean = epochDay in fromEpochDay..untilEpochDay
}

/** A row of the review log. [passageId] is null for a check of a range that spans several passages. */
data class ReviewLogEntry(
    val id: Long,
    val passageId: Long?,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val kind: ReviewKind,
    val grade: ReviewGrade,
    val epochDay: Int,
    val timestamp: Long,
)

/** A range of ayahs of one surah (numbers in the surah). */
data class AyahRange(val surah: Int, val startAyah: Int, val endAyah: Int) {
    val ayahCount: Int get() = endAyah - startAyah + 1
}

/** Everything the UI needs to draw one surah in the hifz model. */
data class SurahCoverage(
    val surah: Int,
    val ayahCount: Int,
    /** Ayahs (numbers in the surah) covered by a passage. */
    val coveredAyahs: Set<Int>,
    /** Ayahs (numbers in the surah) that are learned: their passage is past the 7-day repetition (R14, R30, OLD). A subset of [coveredAyahs]. */
    val learnedAyahs: Set<Int>,
    /** Covered ayahs the person marked as difficult. */
    val hardAyahs: Set<Int>,
    /** The passages of the surah, in mushaf order. */
    val passages: List<Passage>,
    /** True when this is the surah the plan is learning now. */
    val isCurrent: Boolean,
    /** The displayed status: learned only when every ayah is in [learnedAyahs]. */
    val status: SurahHifzStatus?,
)

/** The answer of `completeNew`: a passage is refused when some of its ayahs are covered already. */
sealed interface NewPassageResult {
    data class Created(val passageId: Long) : NewPassageResult
    data object Overlaps : NewPassageResult
}
