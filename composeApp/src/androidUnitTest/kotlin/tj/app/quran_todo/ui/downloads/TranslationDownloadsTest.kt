package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.domain.repository.WholeDownloadResult
import tj.app.quran_todo.ui.i18n.downloadsCopy
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The request kind "translation" (task F4a): the whole Quran of one edition in one request, through the same queue and runner. */
class TranslationDownloadsTest {
    private lateinit var dir: File
    private lateinit var storage: DownloadStorage
    private val asked = mutableListOf<String>()
    private val finished = mutableListOf<Pair<String, FinishResult>>()
    private val progress = mutableListOf<DownloadProgress>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("translation-downloads-test").toFile()
        storage = DownloadStorage(File(dir, "downloads"))
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun newQueue() = DownloadQueue(FileQueueStore(File(dir, "state/queue.json"))) { request, result -> finished += request.id to result }

    private fun request(key: String = "ru.abuadel") = DownloadRequest(
        id = DownloadRequest.translationId(key),
        kind = DownloadKind.TRANSLATION,
        title = "Translation: $key",
        reciterId = "alafasy",
        segments = emptyList(),
        edition = key,
    )

    private fun source(vararg answers: WholeDownloadResult, gate: CompletableDeferred<WholeDownloadResult>? = null): TranslationSource {
        val queued = answers.toMutableList()
        return object : TranslationSource {
            override suspend fun ensure(language: AppLanguage, surah: Int) = true

            override suspend fun downloadWhole(editionKey: String): WholeDownloadResult {
                asked += editionKey
                return gate?.await() ?: queued.removeFirstOrNull() ?: WholeDownloadResult.FAILED
            }
        }
    }

    private fun runner(queue: DownloadQueue, translations: TranslationSource) = DownloadRunner(
        queue = queue,
        storage = storage,
        fetch = { _, _ -> error("a translation fetches no audio") },
        translations = translations,
        onProgress = { progress += it },
        pause = {},
    )

    @Test
    fun aTranslationIsOneRequestAndDoesNotTouchTheAudio() = runTest {
        val queue = newQueue()
        queue.enqueue(request("ru.abuadel"))

        assertEquals(RunOutcome.FINISHED, runner(queue, source(WholeDownloadResult.DONE)).run())

        assertEquals(listOf("ru.abuadel"), asked)
        assertTrue(queue.state.value.requests.isEmpty())
        assertEquals(listOf("translation-ru.abuadel" to FinishResult.DONE), finished)
        assertTrue(storage.snapshot().isEmpty())
    }

    @Test
    fun theProgressHasNothingToCountSoTheTitleIsNotZeroOfZero() = runTest {
        val queue = newQueue()
        queue.enqueue(request())
        runner(queue, source(WholeDownloadResult.DONE)).run()

        assertEquals(0, progress.first().total)
        AppLanguage.entries.forEach { language ->
            val title = downloadsCopy(language).notificationTitle("Abu Adel", 0, 0)
            assertTrue("Abu Adel" in title, title)
            assertTrue("0" !in title && '{' !in title, "$language: $title")
        }
    }

    @Test
    fun aFailedFirstTryIsRepeated() = runTest {
        val queue = newQueue()
        queue.enqueue(request())

        runner(queue, source(WholeDownloadResult.FAILED, WholeDownloadResult.DONE)).run()

        assertEquals(2, asked.size)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun anAnswerThatIsNotWholeFailsTheRequestSoThatTheScreenCanOfferRetry() = runTest {
        val queue = newQueue()
        queue.enqueue(request())

        assertEquals(RunOutcome.FINISHED, runner(queue, source(WholeDownloadResult.FAILED, WholeDownloadResult.FAILED)).run())

        assertEquals(1, queue.state.value.failures.size)
        assertEquals(1, queue.state.value.failedTranslations)
        assertEquals(0, queue.state.value.failedAyahs)
        assertEquals(listOf("translation-ru.abuadel" to FinishResult.FAILED), finished)
        // "Retry" takes it again.
        queue.retryFailed()
        assertEquals(RunOutcome.FINISHED, runner(queue, source(WholeDownloadResult.DONE)).run())
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun withoutANetworkTheWorkWaitsAndTheRequestStaysQueued() = runTest {
        val queue = newQueue()
        queue.enqueue(request())

        val outcome = runner(queue, source(WholeDownloadResult.NO_NETWORK, WholeDownloadResult.NO_NETWORK)).run(finalAttempt = false)

        assertEquals(RunOutcome.NO_NETWORK, outcome)
        assertEquals(listOf(request().id), queue.state.value.requests.map { it.id })
        assertTrue(queue.state.value.failures.isEmpty())
    }

    @Test
    fun onTheLastAttemptWithoutANetworkTheRequestIsMarkedFailed() = runTest {
        val queue = newQueue()
        queue.enqueue(request())

        runner(queue, source(WholeDownloadResult.NO_NETWORK, WholeDownloadResult.NO_NETWORK)).run(finalAttempt = true)

        assertEquals(1, queue.state.value.failedTranslations)
    }

    @Test
    fun cancellingWhileTheRequestIsOnItsWayDropsTheAnswer() = runTest {
        val queue = newQueue()
        queue.enqueue(request())
        val gate = CompletableDeferred<WholeDownloadResult>()
        var outcome: RunOutcome? = null
        val job = launch { outcome = runner(queue, source(gate = gate)).run() }
        runCurrent()
        assertEquals(1, asked.size)

        queue.cancel(request().id)
        runCurrent()
        job.join()

        assertEquals(RunOutcome.FINISHED, outcome)
        assertEquals(listOf(request().id to FinishResult.CANCELLED), finished)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun pausingWhileTheRequestIsOnItsWayStopsTheRunAndKeepsTheRequest() = runTest {
        val queue = newQueue()
        queue.enqueue(request())
        val gate = CompletableDeferred<WholeDownloadResult>()
        var outcome: RunOutcome? = null
        val job = launch { outcome = runner(queue, source(gate = gate)).run() }
        runCurrent()

        queue.pause()
        runCurrent()
        job.join()

        assertEquals(RunOutcome.PAUSED, outcome)
        assertEquals(listOf(request().id), queue.state.value.requests.map { it.id })
    }

    @Test
    fun theSameEditionIsNotQueuedTwiceButTwoEditionsAre() {
        val queue = newQueue()
        assertTrue(queue.enqueue(request("ru.abuadel")))
        assertTrue(!queue.enqueue(request("ru.abuadel")))
        assertTrue(queue.enqueue(request("qc:139")))
        assertEquals(2, queue.state.value.requests.size)
    }

    @Test
    fun aTranslationRequestHasNoFilesAndSurvivesTheQueueFile() {
        val queue = newQueue()
        queue.enqueue(request("qc:139"))
        val reloaded = FileQueueStore(File(dir, "state/queue.json")).load()
        val back = reloaded.requests.single()
        assertEquals(DownloadKind.TRANSLATION, back.kind)
        assertEquals("qc:139", back.edition)
        assertTrue(back.keys().isEmpty())
        assertEquals(0, back.ayahCount)
    }
}
