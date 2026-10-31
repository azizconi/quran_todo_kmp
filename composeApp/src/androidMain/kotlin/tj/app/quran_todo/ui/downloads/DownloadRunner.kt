package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.domain.use_case.GetAyahTranslationsUseCase
import tj.app.quran_todo.ui.mushaf.data.PagesSource
import java.util.concurrent.atomic.AtomicInteger

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

/**
 * Makes sure the word timings of a surah are kept with its downloads (pinned: they are deleted only with the downloads). They are small
 * and optional: a surah without them is downloaded all the same, only its recitation has no word highlight offline.
 */
internal fun interface TimingsSource {
    /** True when the timings of [surah] for the quran.com recitation [recitationId] are pinned (they were, or have just been fetched). */
    suspend fun ensure(recitationId: Int, surah: Int): Boolean
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
 *  - the word timings of each surah are pinned next to the audio ([TimingsSource]); a failure there is ignored;
 *  - a file is written as `<key>.mp3.download` and renamed when whole ([DownloadStorage.commit]);
 *  - an ayah is tried [attemptsPerAyah] times; [maxConsecutiveFailures] failures in a row stop the run with [RunOutcome.NO_NETWORK]
 *    (the work is retried later by WorkManager with its backoff);
 *  - up to [parallelism] files are on the way at once (the files are small and the time goes into the round trips of the network,
 *    not into the bytes: one at a time, a juz took a quarter of an hour on a slow connection);
 *  - the queue is read again before every file is started, so "Pause all" and "Cancel" take effect at once: files that are on
 *    the way finish, no new ones start.
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
    private val parallelism: Int = DEFAULT_PARALLELISM,
    private val timings: TimingsSource = TimingsSource { _, _ -> true },
    private val pages: PagesSource = PagesSource.None,
    private val pageParallelism: Int = PAGE_PARALLELISM,
) {
    private sealed interface Pass {
        data object Cancelled : Pass
        data object Paused : Pass
        data object NoNetwork : Pass
        data class Ended(val ayahs: Int, val translations: Int, val pages: Int = 0) : Pass
    }

    /**
     * @param finalAttempt the work will not be retried after this run: a request that cannot be finished is marked failed, so that the
     *   screen can offer "Retry", instead of the run ending with [RunOutcome.NO_NETWORK].
     */
    suspend fun run(finalAttempt: Boolean = false): RunOutcome {
        storage.deleteUnfinished()
        pages.deleteUnfinished()
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
                    val left = missing(request)
                    if (request.kind == DownloadKind.PAGES) {
                        queue.fail(request.id, ayahs = 0, translations = 0, pages = left)
                    } else {
                        queue.fail(request.id, ayahs = left, translations = 0)
                    }
                }
                is Pass.Ended -> if (pass.ayahs == 0 && pass.translations == 0 && pass.pages == 0) {
                    queue.complete(request.id)
                } else {
                    queue.fail(request.id, pass.ayahs, pass.translations, pass.pages)
                }
            }
        }
    }

    /** What a request still lacks: ayahs of audio, or pages of the mushaf for a pages request. */
    private fun missing(request: DownloadRequest): Int =
        if (request.kind == DownloadKind.PAGES) request.pages.count { !pages.has(it) } else request.keys().count { !storage.contains(it) }

    /**
     * A pages request ([DownloadKind.PAGES]): the layout and the font of every page that is not pinned yet, [pageParallelism] pages at a
     * time, the same rules as for audio (the queue is read before each page, a page is tried twice, several failures in a row mean no
     * network, a page that is pinned already is skipped so a run after an interruption carries on).
     */
    private suspend fun processPages(request: DownloadRequest): Pass {
        val total = request.pages.size
        val doneCount = AtomicInteger(request.pages.count { pages.has(it) })
        report(request, doneCount.get(), total)
        val failedCount = AtomicInteger(0)
        val consecutiveCount = AtomicInteger(0)
        val gate = Semaphore(pageParallelism.coerceAtLeast(1))
        var stopped: Pass? = null
        coroutineScope {
            for (page in request.pages) {
                if (pages.has(page)) continue
                gate.acquire()
                val reason = interruption(request)
                    ?: Pass.NoNetwork.takeIf { consecutiveCount.get() >= maxConsecutiveFailures }
                if (reason != null) {
                    gate.release()
                    stopped = reason
                    break
                }
                launch {
                    try {
                        if (pinOne(page)) {
                            consecutiveCount.set(0)
                            report(request, doneCount.incrementAndGet(), total)
                        } else {
                            failedCount.incrementAndGet()
                            consecutiveCount.incrementAndGet()
                        }
                    } finally {
                        gate.release()
                    }
                }
            }
        }
        stopped?.let { return it }
        if (consecutiveCount.get() >= maxConsecutiveFailures) return Pass.NoNetwork
        return Pass.Ended(ayahs = 0, translations = 0, pages = failedCount.get())
    }

    private suspend fun pinOne(page: Int): Boolean {
        for (attempt in 0 until attemptsPerAyah) {
            try {
                if (pages.pin(page)) return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Counted as a failed attempt below.
            }
            if (attempt < attemptsPerAyah - 1) pause(RETRY_PAUSE_MS shl attempt)
        }
        return false
    }

    private suspend fun process(request: DownloadRequest): Pass {
        if (request.kind == DownloadKind.PAGES) return processPages(request)
        val reciter = Reciters.byId(request.reciterId)
        val keys = request.keys()
        val total = keys.size
        val done = keys.count { storage.contains(it) }
        report(request, done, total)

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

        // The word timings of the surahs: tiny, one request each, and never a reason to fail the download.
        for (surah in request.segments.map { it.surah }.distinct()) {
            interruption(request)?.let { return it }
            try {
                timings.ensure(reciter.recitationId, surah)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Optional: the audio goes on.
            }
        }

        val doneCount = AtomicInteger(done)
        val failedCount = AtomicInteger(0)
        val consecutiveCount = AtomicInteger(consecutive)
        val gate = Semaphore(parallelism.coerceAtLeast(1))
        var stopped: Pass? = null
        coroutineScope {
            outer@ for (segment in request.segments) {
                for (ayah in segment.first..segment.last) {
                    val key = reciter.cacheKey(segment.surah, ayah)
                    if (storage.contains(key)) continue
                    // A free place first: the queue is looked at when a file is about to start, not when it was planned.
                    gate.acquire()
                    val reason = interruption(request)
                        ?: Pass.NoNetwork.takeIf { consecutiveCount.get() >= maxConsecutiveFailures }
                    if (reason != null) {
                        gate.release()
                        stopped = reason
                        break@outer
                    }
                    launch {
                        try {
                            if (fetchOne(reciter.audioUrl(segment.surah, ayah), key)) {
                                consecutiveCount.set(0)
                                report(request, doneCount.incrementAndGet(), total)
                            } else {
                                failedCount.incrementAndGet()
                                consecutiveCount.incrementAndGet()
                            }
                        } finally {
                            gate.release()
                        }
                    }
                }
            }
        }
        // The files that were on the way have finished here, also when the loop stopped.
        stopped?.let { return it }
        if (consecutiveCount.get() >= maxConsecutiveFailures) return Pass.NoNetwork
        return Pass.Ended(failedCount.get(), failedTranslations)
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

    companion object {
        private const val RETRY_PAUSE_MS = 1_000L

        /** Files on the way at once. */
        const val DEFAULT_PARALLELISM = 3

        /** Pages on the way at once (a page is two requests: its layout and its font). */
        const val PAGE_PARALLELISM = 2
    }
}
