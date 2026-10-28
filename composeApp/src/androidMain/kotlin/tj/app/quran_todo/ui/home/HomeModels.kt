package tj.app.quran_todo.ui.home

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus

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
    /** Includes a change that is still being saved. */
    val status: SurahTodoStatus?,
    /** Memorized ayahs / all ayahs; 1 for a memorized surah. */
    val learnedFraction: Float,
    val canOpen: Boolean,
    val isPinned: Boolean,
)

/** A chip of the "Recent" row. */
@Immutable
data class RecentUi(val surah: Int, val name: String, val pinned: Boolean)

/** Today's tasks. [done] of [goal] is the counter next to the heading. */
@Immutable
data class TodayUi(val done: Int, val goal: Int, val body: TodayBody)

@Immutable
sealed interface TodayBody {
    /** One card per kind of work that is left (new, review, weak). */
    @Immutable
    data class Cards(val cards: List<TodayCardUi>) : TodayBody

    /** There was work but nothing is left for today. */
    data object AllDone : TodayBody

    /** Memorizing was chosen as a goal but no surah is marked yet. */
    data object Invite : TodayBody
}

/**
 * A card of the Today row. [surah] is the surah the reader opens ([startAyah]..[endAyah] inside it);
 * [totalAyahs] and [surahCount] describe all the work of this [kind].
 */
@Immutable
data class TodayCardUi(
    val kind: TodayKind,
    val surah: Int,
    val surahName: String,
    val startAyah: Int,
    val endAyah: Int,
    val totalAyahs: Int,
    val surahCount: Int,
    /** False when another card already uses this surah for shared element keys. */
    val sharesElements: Boolean,
)

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
    val today: TodayUi? = null,
    val recent: List<RecentUi> = emptyList(),
    /** All surahs in order; the listing and the sheets look rows up here. */
    val rows: List<SurahRowUi> = emptyList(),
    val listing: ListingUi = ListingUi(),
    /** Memorized ayahs / 6236 for the ring in the top bar. */
    val overallProgress: Float = 0f,
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

/** Things the screen reacts to once (a toast), not state. */
sealed interface HomeEvent {
    data object StatusUpdateFailed : HomeEvent
}
