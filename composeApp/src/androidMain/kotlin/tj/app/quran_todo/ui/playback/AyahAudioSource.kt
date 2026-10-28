package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.ReaderAudioAyah
import tj.app.quran_todo.common.audio.Reciter

/** The part of [AudioCache] that [AyahAudioSource] needs; a fake in tests. */
internal interface AyahFiles {
    /** Path of the cached file for [cacheKey], downloading [url] first when needed; null when that fails. */
    suspend fun getOrFetch(url: String, cacheKey: String): String?

    /** Downloads [url] into the cache unless it is already there. */
    suspend fun prefetch(url: String, cacheKey: String): Boolean
}

internal class AudioCacheFiles(private val cache: AudioCache = AudioCache()) : AyahFiles {
    override suspend fun getOrFetch(url: String, cacheKey: String): String? = cache.getOrFetch(url, cacheKey)

    override suspend fun prefetch(url: String, cacheKey: String): Boolean = cache.prefetch(url, cacheKey)
}

/**
 * Tells the reader session what to play for an ayah: the cached file of the current reciter (downloaded
 * on the spot when it is not cached), or the remote URL as a fallback so that ExoPlayer can stream it
 * (and report a network error that the output retries).
 *
 * Once an ayah is ready, the next [prefetchCount] ayahs of the playlist are downloaded in the background,
 * so the next ayah starts from a file right away and there is no audible gap between ayahs.
 *
 * [reciter] and [playlist] are read at the moment of each call, so a changed reciter or a new surah
 * is picked up without rebuilding anything.
 */
internal class AyahAudioSource(
    private val files: AyahFiles,
    private val reciter: () -> Reciter,
    private val playlist: () -> List<ReaderAudioAyah>,
    private val scope: CoroutineScope,
    private val prefetchCount: Int = DefaultPrefetchCount,
) {
    private var prefetchJob: Job? = null

    suspend fun resolve(ayah: ReaderAudioAyah, surahNumber: Int): String {
        val reciter = reciter()
        val remote = reciter.audioUrl(ayah.globalNumber)
        val local = files.getOrFetch(remote, reciter.cacheKey(surahNumber, ayah.numberInSurah))
        if (local != null) prefetchAfter(reciter, ayah, surahNumber)
        return local ?: remote
    }

    private fun prefetchAfter(reciter: Reciter, ayah: ReaderAudioAyah, surahNumber: Int) {
        val ayahs = playlist()
        val at = ayahs.indexOfFirst { it.globalNumber == ayah.globalNumber }
        if (at < 0) return
        val upcoming = ayahs.drop(at + 1).take(prefetchCount)
        if (upcoming.isEmpty()) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            for (next in upcoming) {
                ensureActive()
                files.prefetch(reciter.audioUrl(next.globalNumber), reciter.cacheKey(surahNumber, next.numberInSurah))
            }
        }
    }

    companion object {
        const val DefaultPrefetchCount = 2
    }
}
