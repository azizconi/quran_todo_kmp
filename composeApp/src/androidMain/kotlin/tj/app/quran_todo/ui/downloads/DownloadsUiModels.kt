package tj.app.quran_todo.ui.downloads

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.ui.home.SurahSearch

/** Where a row stands in the queue. */
sealed interface RowQueue {
    /** Not asked for (or already finished). */
    data object None : RowQueue

    /** In the queue behind another request. */
    data object Waiting : RowQueue

    /** The request the worker is on. */
    data class Active(val done: Int, val total: Int) : RowQueue

    /** The request is next but the queue is paused. */
    data class Paused(val done: Int, val total: Int) : RowQueue

    /** The request ended with something missing; "Retry" takes it again. */
    data object Failed : RowQueue
}

/** One row of the screen: a surah, a juz or "everything in my plan". */
@Immutable
internal data class RowUi(
    val id: String,
    val kind: DownloadKind,
    /** The surah or the juz number; 0 for the plan. */
    val number: Int,
    /** The surah name in the language of the app (empty for a juz and the plan). */
    val name: String,
    val arabicName: String,
    val firstSurah: Int,
    val lastSurah: Int,
    val surahCount: Int,
    val ayahCount: Int,
    /** The size before downloading, estimated from the text; null while the text is not known yet. */
    val estimateBytes: Long?,
    val state: RowState,
    val queue: RowQueue,
)

/** The queue card: what is downloading and how far, and what is waiting. */
@Immutable
internal data class QueueUi(
    val title: String,
    val done: Int,
    val total: Int,
    val waiting: Int,
    val remainingBytes: Long?,
    val paused: Boolean,
    val phase: WorkPhase,
    val failedAyahs: Int,
    val failedTranslations: Int,
    /** Something is left to download (the queue is not only requests that failed). */
    val hasActive: Boolean,
)

@Immutable
internal data class StorageUi(
    val downloadsBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val freeBytes: Long = 0L,
)

/** Everything the screen draws. */
@Immutable
internal data class DownloadsUi(
    val loaded: Boolean = false,
    val reciter: Reciter = Reciters.default,
    val wifiOnly: Boolean = true,
    val withTranslation: Boolean = true,
    val queue: QueueUi? = null,
    val storage: StorageUi = StorageUi(),
    /** "Everything in my plan"; null when there is no plan (the section is hidden). */
    val planAll: RowUi? = null,
    val planRows: List<RowUi> = emptyList(),
    val juz: List<RowUi> = emptyList(),
    val surahs: List<RowUi> = emptyList(),
    val searchIndex: SurahSearch.Index? = null,
)

/** Things the screen reacts to once. */
internal sealed interface DownloadsEvent {
    data object NotEnoughSpace : DownloadsEvent

    data object Deleted : DownloadsEvent

    data object CacheCleared : DownloadsEvent
}
