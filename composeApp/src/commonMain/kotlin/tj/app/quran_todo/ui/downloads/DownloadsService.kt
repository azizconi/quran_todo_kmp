package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tj.app.quran_todo.data.translation.TranslationEdition

/**
 * What the screens of "Downloads and storage" (and the translation search) need from the engine that fetches files in the background.
 *
 * Android: `DownloadsManager` (WorkManager, a queue file, the files under `noBackupFilesDir`).
 * iOS (S2): a manager over a background `URLSession` (so downloads go on when the app is suspended), the same queue ([DownloadQueue]
 * with a file-backed store), and files in Application Support (excluded from the iCloud backup). [phase] says whether the session is
 * transferring, waiting for the network or idle.
 */
interface DownloadsService {
    /** The persistent queue: what is asked for, what is running, what failed. */
    val queue: DownloadQueue

    /** The two switches of the screen and the "permission asked" flag. */
    val prefs: DownloadsPrefs

    /** What the work is doing: running, waiting for the network, or not scheduled. */
    val phase: Flow<WorkPhase>

    /** The downloaded audio and timings. */
    val storage: DownloadedFiles

    /** The mushaf pages that are pinned next to the audio, and the cache of the pages that reading fetched. */
    val pages: PinnedPages

    /** Adds [request] to the queue and makes sure the work runs; false when it was queued already. */
    fun enqueue(request: DownloadRequest, estimatedBytes: Long): Boolean

    /** Asks for the whole translation [edition], titled [title] in the language of the interface. */
    fun enqueueTranslation(edition: TranslationEdition, title: String): Boolean

    fun pauseAll()
    fun resume()
    fun cancel(requestId: String)
    fun cancelAll()
    fun retry()

    /** "Wi-Fi only" was switched: a waiting work gets the new constraint. */
    fun onNetworkChoiceChanged()

    /** Deletes the downloaded files of [keys]; returns the bytes that were freed. */
    fun delete(keys: Collection<String>): Long

    /** Deletes the pinned copies of mushaf [pageNumbers]; returns the bytes that were freed. */
    fun deletePages(pageNumbers: Collection<Int>): Long

    /** Every downloaded file goes (audio, timings, pinned pages); the queue stays. Returns the bytes freed. */
    fun deleteAll(): Long
}

/** The downloaded audio files, read as a map. */
interface DownloadedFiles {
    /** Grows every time the set of files changes; the screen reads [snapshot] again. */
    val revision: StateFlow<Long>

    /** Every downloaded file: key (`ayah_78_1`, `husary_78_1`) to its size in bytes. */
    fun snapshot(): Map<String, Long>
}

/** The pinned mushaf pages (downloads) and the cache of the pages that reading fetched. */
interface PinnedPages {
    val revision: StateFlow<Long>

    /** Page number to bytes, for the pages that are pinned whole. */
    fun pinnedSnapshot(): Map<Int, Long>

    /** Bytes of the cache of pages (not the pinned ones). */
    fun cacheBytes(): Long

    /** Empties the cache of pages; returns the bytes that were freed. */
    fun clearCache(): Long
}

/** The cache of the audio that listening fetched (1 GB, least recently used first). iOS: a folder in Caches. */
interface ListeningCache {
    fun totalBytes(): Long
    fun clear(): Long
}

/** The size and the removal of the person's own recordings ("My reading"). */
interface RecordingsStorage {
    suspend fun totalBytes(): Long
    suspend fun deleteAll()
}

/** Free space of the phone, for the check before a big download. iOS: `volumeAvailableCapacityForImportantUsage` of the home directory. */
fun interface StorageSpace {
    fun freeBytes(): Long
}
