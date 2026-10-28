package tj.app.quran_todo.ui.progress

import androidx.compose.runtime.Immutable

/** What the screen shows: a skeleton while the data arrives, an invitation when nothing was started, the content. */
enum class ProgressPhase { Loading, Empty, Content }

/** State of one surah on the Quran map. */
enum class MapState { NotStarted, Learning, Learned }

/** The big ring and the line under it. [percent] is whole percent, never 100 before the last ayah. */
@Immutable
data class HeroUi(
    val learnedAyahs: Int,
    val totalAyahs: Int,
    val learnedSurahs: Int,
    val learningSurahs: Int,
    val percent: Int,
    /** Something is memorized but less than 1%: the ring says "<1" instead of a discouraging 0. */
    val belowOnePercent: Boolean,
    /** Memorized ayahs / all ayahs, 0..1: the same number the small ring on the home screen shows. */
    val fraction: Float,
)

/** One of the 114 cells of the Quran map. */
@Immutable
data class MapCellUi(
    val number: Int,
    val name: String,
    val ayahCount: Int,
    /** Memorized ayahs; every ayah for a memorized surah. */
    val learnedAyahs: Int,
    val state: MapState,
) {
    /** Share of memorized ayahs, 0..1: how far the cell is filled from the bottom. */
    val fraction: Float
        get() = if (ayahCount > 0) (learnedAyahs.toFloat() / ayahCount).coerceIn(0f, 1f) else 0f
}

/** The four tiles. */
@Immutable
data class TilesUi(
    val streakDays: Int,
    val dueToday: Int,
    val weakAyahs: Int,
    val learningAyahs: Int,
)

/** One bar of the activity chart: [epochDay] is the local calendar day, [count] the ayahs worked on that day. */
@Immutable
data class ActivityDayUi(val epochDay: Int, val count: Int)

/** The last days, oldest first; the last one is today. */
@Immutable
data class ActivityUi(val days: List<ActivityDayUi>) {
    val total: Int get() = days.sumOf { it.count }
    val max: Int get() = days.maxOfOrNull { it.count } ?: 0
}

/** A surah with weak ayahs; [ayahs] are numbers inside the surah, ascending. */
@Immutable
data class WeakSpotUi(val surah: Int, val name: String, val ayahs: List<Int>)

@Immutable
data class ProgressUi(
    val phase: ProgressPhase = ProgressPhase.Loading,
    val hero: HeroUi = HeroUi(0, 0, 0, 0, 0, false, 0f),
    val cells: List<MapCellUi> = emptyList(),
    val tiles: TilesUi = TilesUi(0, 0, 0, 0),
    val activity: ActivityUi = ActivityUi(emptyList()),
    val weak: List<WeakSpotUi> = emptyList(),
)
