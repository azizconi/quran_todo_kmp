package tj.app.quran_todo.ui.home

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.hifz.model.HifzScope

/** What the screen shows instead of the list while the Quran is not there yet. */
enum class HomePhase { Loading, Content, Error }

/** The segments under the search field. */
enum class SurahFilter { All, Learning, Learned }

/** The "Continue" hero: where the person stopped reading. [ayah] is the number inside the surah. */
@Immutable
data class ContinueUi(
    val surah: Int,
    val name: String,
    val arabicName: String,
    val translatedName: String,
    val ayah: Int,
    val ayahCount: Int,
    /** Nothing was ever read: the hero invites to start with Al-Fatihah. */
    val isFresh: Boolean,
    val canOpen: Boolean,
) {
    val progress: Float get() = if (isFresh || ayahCount <= 0) 0f else (ayah.toFloat() / ayahCount).coerceIn(0f, 1f)
}

/** One of the 114 rows of the list. */
@Immutable
data class SurahRowUi(
    val number: Int,
    /** The name in the app language: Russian name for RU, the transliteration otherwise. */
    val name: String,
    /** Latin transliteration (equals [name] outside RU); only the search reads it. */
    val latinName: String,
    /** Meaning of the name, empty when unknown or equal to [name]. */
    val translatedName: String,
    val arabicName: String,
    val ayahCount: Int,
    /** Derived from the passages of the hifz model; null = not started. */
    val status: SurahTodoStatus?,
    /** Memorized ayahs / all ayahs; 1 for a memorized surah. */
    val learnedFraction: Float,
    val canOpen: Boolean,
    val isPinned: Boolean,
    /** The surah the plan is learning now ("in the plan" label). */
    val inPlan: Boolean = false,
)

/** A chip of the "Recent" row. */
@Immutable
data class RecentUi(val surah: Int, val name: String, val pinned: Boolean)

/** What the search shows first for `2:255`. */
@Immutable
data class SmartResultUi(val row: SurahRowUi, val ayah: Int, val sharesElements: Boolean)

/** The list under the search field after the query and the filter were applied. */
@Immutable
data class ListingUi(
    val rows: List<SurahRowUi> = emptyList(),
    val smartResult: SmartResultUi? = null,
    /** The query is not blank: the filter is ignored and the hero / today / recent give way. */
    val isSearching: Boolean = false,
    val filter: SurahFilter = SurahFilter.All,
)

/** Everything the screen draws except audio. */
@Immutable
data class HomeUi(
    val phase: HomePhase = HomePhase.Loading,
    val continueItem: ContinueUi? = null,
    /** Today's lesson card; null until the repository has answered (and while the Quran is not loaded). */
    val lesson: LessonCardUi? = null,
    /** "Lesson of the day" goes above "Continue reading" (the goal is memorizing, or no goal was chosen). */
    val lessonFirst: Boolean = true,
    val recent: List<RecentUi> = emptyList(),
    /** All surahs in order; the listing and the sheets look rows up here. */
    val rows: List<SurahRowUi> = emptyList(),
    val listing: ListingUi = ListingUi(),
    /** Memorized ayahs / 6236 for the ring in the top bar. */
    val overallProgress: Float = 0f,
    /** The 30 rows of the "Juz" tab with the memorized share of each (D1). */
    val juzRows: List<JuzRowUi> = emptyList(),
    /** [juzRows] after the query (the "Juz" tab has no status filter). */
    val juzListing: List<JuzRowUi> = emptyList(),
    /** Enough is memorized for the quiz "Check" (task F2b): the row under the lesson card shows. */
    val quizReady: Boolean = false,
    /** The plan row under the lesson card (task U15); null when there is no running plan to show. */
    val planRow: PlanRowUi? = null,
)

/**
 * "Juz Amma · covered 10 of 564 · finish ~2 March · Change" under the lesson card (task U15). The numbers are the coverage
 * numbers of the forecast the Progress screen shows ([covered] ayahs of [total]); [finishEpochDay] is where the pace leads, null
 * when unknown. [today] decides whether the date gets a year.
 */
@Immutable
data class PlanRowUi(
    val scope: HifzScope,
    val covered: Int,
    val total: Int,
    val finishEpochDay: Int?,
    val today: Int,
)

/** The floating mini player. Position inside the ayah is a separate flow so this changes rarely. */
@Immutable
data class MiniPlayerUi(
    val surah: Int,
    val name: String,
    /** Number of the playing ayah inside the surah. */
    val ayah: Int,
    val speed: Float,
    val isPlaying: Boolean,
    val isPreparing: Boolean,
    val hasError: Boolean,
)

/**
 * "Listen to the old" while its queue plays or stands (task F1b): the card says "Listening to the old - 2 of 5". [position] counts
 * from 1.
 */
@Immutable
data class ListeningUi(val position: Int, val total: Int, val playing: Boolean)

/** Things the screen reacts to once (a toast), not state. */
sealed interface HomeEvent {
    data object StatusUpdateFailed : HomeEvent

    /** "Continue now" of a paused plan did not work (task F2b). */
    data object ResumeFailed : HomeEvent
}
