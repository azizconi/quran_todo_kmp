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
    /** For a pages row (task F3a): how many pages it holds (21 for a juz, 604 for all); 0 for every other row. */
    val pageCount: Int = 0,
    /** The size before downloading, estimated from the text; null while the text is not known yet. */
    val estimateBytes: Long?,
    val state: RowState,
    val queue: RowQueue,
)

/**
 * One row of the section "Translations" (task F4a): the edition chosen for the language of the interface, and every other edition that
 * is stored whole (so it can be deleted). [wireBytes] is the size of the download.
 */
@Immutable
internal data class TranslationRowUi(
    val key: String,
    val name: String,
    val author: String,
    val wireBytes: Long,
    val complete: Boolean,
    /** The edition on the phone is older than the version the app knows: "Update available". */
    val updateAvailable: Boolean = false,
    val chosen: Boolean,
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
    /** Pages of the mushaf that did not download (task F3a). */
    val failedPages: Int = 0,
    /** Something is left to download (the queue is not only requests that failed). */
    val hasActive: Boolean,
)

@Immutable
internal data class StorageUi(
    val downloadsBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    /** The person's own recordings ("My reading", task F2a). */
    val recordingsBytes: Long = 0L,
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
    /** "All 604 pages" of the section "Mushaf pages" (task F3a). */
    val pagesAll: RowUi? = null,
    /** The pages of each juz, in the section "Mushaf pages". */
    val pagesJuz: List<RowUi> = emptyList(),
    val juz: List<RowUi> = emptyList(),
    val surahs: List<RowUi> = emptyList(),
    /** The section "Translations" (task F4a). */
    val translations: List<TranslationRowUi> = emptyList(),
    val searchIndex: SurahSearch.Index? = null,
)

/** Things the screen reacts to once. */
internal sealed interface DownloadsEvent {
    data object NotEnoughSpace : DownloadsEvent

    data object Deleted : DownloadsEvent

    data object CacheCleared : DownloadsEvent

    data object RecordingsDeleted : DownloadsEvent
}
