package tj.app.quran_todo.ui.progress

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt
import tj.app.quran_todo.hifz.model.HifzScope

/** What the screen shows: a skeleton while the data arrives, an invitation when nothing was started, the content. */
enum class ProgressPhase { Loading, Empty, Content }

/** State of one surah on the Quran map. */
enum class MapState { NotStarted, Learning, Learned }

/** The big ring and the lines under it. [percent] is whole percent, never 100 before the last ayah. */
@Immutable
data class HeroUi(
    val learnedAyahs: Int,
    val totalAyahs: Int,
    /** Surahs whose every ayah is covered ("I know by heart"). */
    val learnedSurahs: Int,
    /** Surahs being learned: some ayahs covered, or the current surah of the plan. */
    val learningSurahs: Int,
    val percent: Int,
    /** Something is memorized but less than 1%: the ring says "<1" instead of a discouraging 0. */
    val belowOnePercent: Boolean,
    /** Covered ayahs / all ayahs, 0..1: the same number the small ring on the home screen shows. */
    val fraction: Float,
)

/** One of the 114 cells of the Quran map. */
@Immutable
data class MapCellUi(
    val number: Int,
    val name: String,
    val ayahCount: Int,
    /** Covered ayahs; every ayah for a memorized surah. */
    val learnedAyahs: Int,
    val state: MapState,
) {
    /** Share of covered ayahs, 0..1: how far the cell is filled from the bottom. */
    val fraction: Float
        get() = if (ayahCount > 0) (learnedAyahs.toFloat() / ayahCount).coerceIn(0f, 1f) else 0f
}

/** Today's lesson as its tile says it. */
sealed interface TodayUi {
    /** No lesson at all today (no plan and nothing to review). */
    data object None : TodayUi

    /** Everything in the lesson is done. */
    data object Done : TodayUi

    /** [left] of [total] cards of the lesson are still to do. */
    data class Left(val left: Int, val total: Int) : TodayUi
}

/** The four tiles. */
@Immutable
data class TilesUi(
    val streakDays: Int,
    val today: TodayUi,
    /**
     * Share of the repetitions of the last 30 days that were not "Forgot", in whole percent; null when there was no repetition
     * yet. Never 100 while a repetition was forgotten.
     */
    val retentionPercent: Int?,
    /** How many repetitions the percentage is made of. */
    val retentionRepetitions: Int,
    /** Difficult ayahs right now (the marks inside the passages). */
    val hardAyahs: Int,
)

/**
 * One bar of the activity chart: [epochDay] is the local calendar day, [learned] the new ayahs of the day and [reviewed] the
 * ayahs repeated that day (every ayah once per kind).
 */
@Immutable
data class ActivityDayUi(val epochDay: Int, val learned: Int, val reviewed: Int) {
    val total: Int get() = learned + reviewed
}

/** The last days, oldest first; the last one is today. */
@Immutable
data class ActivityUi(val days: List<ActivityDayUi>) {
    val totalLearned: Int get() = days.sumOf { it.learned }
    val totalReviewed: Int get() = days.sumOf { it.reviewed }
    val peak: Int get() = days.maxOfOrNull { it.total } ?: 0
    val isEmpty: Boolean get() = days.all { it.total == 0 }
}

/** One of the 30 juz: how much of it is covered. */
@Immutable
data class JuzUi(
    val juz: Int,
    val arabicName: String,
    val ayahCount: Int,
    val coveredAyahs: Int,
    /** Where the juz starts: the reader opens here. */
    val startSurah: Int,
    val startAyah: Int,
) {
    val fraction: Float
        get() = if (ayahCount > 0) (coveredAyahs.toFloat() / ayahCount).coerceIn(0f, 1f) else 0f

    /** Whole percent: 0 only when nothing is covered, 100 only when everything is. */
    val percent: Int
        get() = when {
            coveredAyahs <= 0 -> 0
            coveredAyahs >= ayahCount -> 100
            else -> (fraction * 100f).roundToInt().coerceIn(1, 99)
        }
}

/** What the plan line says about the plan. */
enum class PlanStatus {
    /** There is no plan: the line invites to make one. */
    NoPlan,

    /** Every surah of the plan is covered. */
    Finished,

    /** There is a finish date by the pace and no target date to compare it with. */
    Finish,

    /** Behind the line to the target date by [PlanLineUi.ayahs] ayahs. */
    Behind,

    /** Not behind yet, but the pace is too low for the target date: [PlanLineUi.ayahs] ayahs a day are needed. */
    NeedPace,

    /** The pace finishes before the target date. */
    Ahead,

    /** The pace finishes on the target date. */
    OnTrack,
}

/** The line about the plan. [finishEpochDay] is where the pace leads; [ayahs] depends on [status]. */
@Immutable
data class PlanLineUi(
    val status: PlanStatus,
    val scope: HifzScope? = null,
    val finishEpochDay: Int? = null,
    val ayahs: Int = 0,
)

/** A surah with difficult ayahs; [ayahs] are numbers inside the surah, ascending. */
@Immutable
data class HardSpotUi(val surah: Int, val name: String, val ayahs: List<Int>)

@Immutable
data class ProgressUi(
    val phase: ProgressPhase = ProgressPhase.Loading,
    /** The epoch day the screen is for: dates in the plan line get their year relative to it. */
    val today: Int = 0,
    val hero: HeroUi = HeroUi(0, 0, 0, 0, 0, false, 0f),
    val cells: List<MapCellUi> = emptyList(),
    val tiles: TilesUi = TilesUi(0, TodayUi.None, null, 0, 0),
    val activity: ActivityUi = ActivityUi(emptyList()),
    val juz: List<JuzUi> = emptyList(),
    val plan: PlanLineUi = PlanLineUi(PlanStatus.NoPlan),
    val hard: List<HardSpotUi> = emptyList(),
)
