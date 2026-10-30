package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.domain.use_case.GetAyahTranslationsUseCase

/** Makes sure the translation of a surah is in the database, the way the reader gets it. */
internal fun interface TranslationSource {
    /** True when the whole translation of [surah] in [language] is stored (it was, or has just been fetched). */
    suspend fun ensure(language: AppLanguage, surah: Int): Boolean
}

/**
 * The reader's own path: [GetAyahTranslationsUseCase] reads the stored translation of the surah and fetches and stores it when it
 * is missing. `force` skips the 30-second memory of a failure: the worker has its own backoff.
 */
internal class UseCaseTranslations(private val useCase: GetAyahTranslationsUseCase) : TranslationSource {
    override suspend fun ensure(language: AppLanguage, surah: Int): Boolean {
        val expected = QuranFacts.ayahCount(surah)
        return useCase(surahNumber = surah, language = language, expectedCount = expected, force = true).size >= expected
    }
}

/** What the notification shows: the request that is on, how far it is, and how many requests wait behind it. */
internal data class DownloadProgress(
    val request: DownloadRequest,
    val done: Int,
    val total: Int,
    val waiting: Int,
)

/** How one run of the worker ended. */
internal enum class RunOutcome {
    /** There was nothing to do. */
    IDLE,

    /** Everything that could be done was done (some requests may have failed). */
    FINISHED,

    /** The person paused the queue; nothing is lost. */
    PAUSED,

    /** Several files in a row did not come: there is probably no network. The work should wait and try again. */
    NO_NETWORK,
}

/**
 * Goes through the [queue] and fetches what is missing, one ayah at a time:
 *  - the next request is the first one that did not fail; for each of its surahs the translation comes first (it is small), then the
 *    audio of the ayahs;
 *  - an ayah that is in the downloads already is skipped, so a run after an interruption (airplane mode, a killed process, a
 *    restart) simply goes on where it stopped;
 *  - a file is written as `<key>.mp3.download` and renamed when whole ([DownloadStorage.commit]);
 *  - an ayah is tried [attemptsPerAyah] times; [maxConsecutiveFailures] failures in a row stop the run with [RunOutcome.NO_NETWORK]
 *    (the work is retried later by WorkManager with its backoff);
 *  - the queue is read again before every file, so "Pause all" and "Cancel" take effect at once.
 *
 * It has no Android in it: the storage is a folder, the fetch and the translations are parameters.
 */
internal class DownloadRunner(
    private val queue: DownloadQueue,
    private val storage: DownloadStorage,
    private val fetch: AudioFetch,
    private val translations: TranslationSource,
    private val onProgress: (DownloadProgress) -> Unit = {},
    private val pause: suspend (millis: Long) -> Unit = { delay(it) },
    private val attemptsPerAyah: Int = 2,
    private val maxConsecutiveFailures: Int = 3,
) {
    private sealed interface Pass {
        data object Cancelled : Pass
        data object Paused : Pass
        data object NoNetwork : Pass
        data class Ended(val ayahs: Int, val translations: Int) : Pass
    }

    /**
     * @param finalAttempt the work will not be retried after this run: a request that cannot be finished is marked failed, so that the
     *   screen can offer "Retry", instead of the run ending with [RunOutcome.NO_NETWORK].
     */
    suspend fun run(finalAttempt: Boolean = false): RunOutcome {
        storage.deleteUnfinished()
        var didWork = false
        while (true) {
            val state = queue.state.value
            if (state.paused) return RunOutcome.PAUSED
            val request = state.active ?: return if (didWork) RunOutcome.FINISHED else RunOutcome.IDLE
            didWork = true
            when (val pass = process(request)) {
                Pass.Cancelled -> Unit
                Pass.Paused -> return RunOutcome.PAUSED
                Pass.NoNetwork -> {
                    if (!finalAttempt) return RunOutcome.NO_NETWORK
                    queue.fail(request.id, ayahs = missing(request), translations = 0)
                }
                is Pass.Ended -> if (pass.ayahs == 0 && pass.translations == 0) {
                    queue.complete(request.id)
                } else {
                    queue.fail(request.id, pass.ayahs, pass.translations)
                }
            }
        }
    }

    private fun missing(request: DownloadRequest): Int = request.keys().count { !storage.contains(it) }

    private suspend fun process(request: DownloadRequest): Pass {
        val reciter = Reciters.byId(request.reciterId)
        val keys = request.keys()
        val total = keys.size
        var done = keys.count { storage.contains(it) }
        report(request, done, total)

        var failedAyahs = 0
        var failedTranslations = 0
        var consecutive = 0

        // The translation first: it is small, and the reader shows it next to the audio.
        val language = request.language?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
        if (language != null) {
            for (surah in request.segments.map { it.surah }.distinct()) {
                interruption(request)?.let { return it }
                val stored = try {
                    translations.ensure(language, surah)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (stored) {
                    consecutive = 0
                } else {
                    failedTranslations++
                    if (++consecutive >= maxConsecutiveFailures) return Pass.NoNetwork
                }
            }
        }

        for (segment in request.segments) {
            for (ayah in segment.first..segment.last) {
                interruption(request)?.let { return it }
                val key = reciter.cacheKey(segment.surah, ayah)
                if (storage.contains(key)) continue
                val globalAyah = QuranFacts.firstAyahNumber(segment.surah) + ayah - 1
                if (fetchOne(reciter.audioUrl(globalAyah), key)) {
                    consecutive = 0
                    done++
                    report(request, done, total)
                } else {
                    failedAyahs++
                    if (++consecutive >= maxConsecutiveFailures) return Pass.NoNetwork
                }
            }
        }
        return Pass.Ended(failedAyahs, failedTranslations)
    }

    /** The person cancelled this request or paused the queue since the last file. */
    private fun interruption(request: DownloadRequest): Pass? {
        val state = queue.state.value
        return when {
            !state.contains(request.id) -> Pass.Cancelled
            state.paused -> Pass.Paused
            else -> null
        }
    }

    private suspend fun fetchOne(url: String, key: String): Boolean {
        val temporary = storage.temporaryFileFor(key)
        for (attempt in 0 until attemptsPerAyah) {
            temporary.delete()
            try {
                if (fetch(url, temporary) && storage.commit(key, temporary)) return true
            } catch (cancelled: CancellationException) {
                temporary.delete()
                throw cancelled
            } catch (_: Exception) {
                // Counted as a failed attempt below.
            }
            temporary.delete()
            if (attempt < attemptsPerAyah - 1) pause(RETRY_PAUSE_MS shl attempt)
        }
        return false
    }

    private fun report(request: DownloadRequest, done: Int, total: Int) {
        onProgress(DownloadProgress(request, done, total, queue.state.value.waiting))
    }

    private companion object {
        const val RETRY_PAUSE_MS = 1_000L
    }
}
