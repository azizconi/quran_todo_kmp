package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.i18n.AppLanguage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadRunnerTest {
    private lateinit var dir: File
    private lateinit var storage: DownloadStorage
    private val fetched = mutableListOf<String>()
    private val translated = mutableListOf<Pair<AppLanguage, Int>>()
    private val finished = mutableListOf<Pair<String, FinishResult>>()
    private val progress = mutableListOf<DownloadProgress>()
    private val events = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("download-runner-test").toFile()
        storage = DownloadStorage(File(dir, "downloads"))
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun newQueue() = DownloadQueue(FileQueueStore(File(dir, "state/queue.json"))) { request, result -> finished += request.id to result }

    private fun request(surah: Int, language: String? = null, reciter: String = "alafasy") = DownloadRequest(
        id = DownloadRequest.idOf(DownloadKind.SURAH, surah, reciter),
        kind = DownloadKind.SURAH,
        title = "S$surah",
        reciterId = reciter,
        language = language,
        segments = DownloadSelection.surah(surah),
    )

    /** An answer of the network that writes [size] bytes; [onFetch] can do something at the moment of the n-th call. */
    private fun network(
        size: Int = 4_000,
        failWhen: (url: String) -> Boolean = { false },
        onFetch: (count: Int) -> Unit = {},
    ): AudioFetch = { url, target ->
        fetched += url
        onFetch(fetched.size)
        events += "audio"
        if (failWhen(url)) {
            false
        } else {
            // The file is written where the runner says: a temporary file next to the final one.
            assertTrue(target.name.endsWith(".mp3.download"), "written to ${target.name}")
            target.writeBytes(ByteArray(size) { 1 })
            true
        }
    }

    private val goodTranslations = TranslationSource { language, surah ->
        translated += language to surah
        events += "translation"
        true
    }

    private fun runner(
        queue: DownloadQueue,
        fetch: AudioFetch = network(),
        translations: TranslationSource = goodTranslations,
        attempts: Int = 2,
        maxFailures: Int = 3,
        parallelism: Int = 1,
    ) = DownloadRunner(
        queue = queue,
        storage = storage,
        fetch = fetch,
        translations = translations,
        onProgress = { progress += it },
        pause = {},
        attemptsPerAyah = attempts,
        maxConsecutiveFailures = maxFailures,
        parallelism = parallelism,
    )

    private fun url(surah: Int, ayah: Int, edition: String = "128/ar.alafasy"): String {
        val global = tj.app.quran_todo.data.bundled.QuranFacts.firstAyahNumber(surah) + ayah - 1
        return "https://cdn.islamic.network/quran/audio/$edition/$global.mp3"
    }

    // -- The basics -------------------------------------------------------------------------------

    @Test
    fun anEmptyQueueIsIdle() = runTest {
        assertEquals(RunOutcome.IDLE, runner(newQueue()).run())
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun aSurahIsFetchedAyahByAyahFromTheUrlOfItsReciterAndTheRequestIsDone() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))

        assertEquals(RunOutcome.FINISHED, runner(queue).run())

        assertEquals((1..4).map { url(112, it) }, fetched)
        assertEquals(setOf("ayah_112_1", "ayah_112_2", "ayah_112_3", "ayah_112_4"), storage.snapshot().keys)
        assertTrue(queue.state.value.requests.isEmpty())
        assertEquals(listOf("surah-112-alafasy" to FinishResult.DONE), finished)
    }

    @Test
    fun anotherReciterGetsItsOwnUrlAndFileNames() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112, reciter = "abdulbasit"))
        runner(queue).run()
        assertEquals((1..4).map { url(112, it, "192/ar.abdulbasitmurattal") }, fetched)
        assertEquals(setOf("abdulbasit_112_1", "abdulbasit_112_2", "abdulbasit_112_3", "abdulbasit_112_4"), storage.snapshot().keys)
    }

    @Test
    fun aFileIsWrittenAsADownloadFileAndRenamedWhenWhole() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        runner(queue).run()
        // No ".download" file is left, every ayah is a whole ".mp3".
        assertEquals(emptyList(), storage.directory.list()!!.filter { it.endsWith(".download") })
        assertEquals(4, storage.directory.list()!!.count { it.endsWith(".mp3") })
    }

    @Test
    fun theProgressCountsWhatIsThereFromTheStart() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        runner(queue).run()
        assertEquals(listOf(0, 1, 2, 3, 4), progress.map { it.done })
        assertTrue(progress.all { it.total == 4 && it.request.title == "S112" })
    }

    // -- Skipping, resuming -----------------------------------------------------------------------

    @Test
    fun whatIsDownloadedAlreadyIsSkipped() = runTest {
        storage.fileFor("ayah_112_1").writeBytes(ByteArray(2_000))
        storage.fileFor("ayah_112_3").writeBytes(ByteArray(2_000))
        val queue = newQueue()
        queue.enqueue(request(112))

        assertEquals(RunOutcome.FINISHED, runner(queue).run())

        assertEquals(listOf(url(112, 2), url(112, 4)), fetched)
        assertEquals(4, storage.snapshot().size)
        assertEquals(listOf(2, 3, 4), progress.map { it.done })
    }

    @Test
    fun aRunAfterARestartOfTheProcessCarriesOnFromTheFilesAndTheStoredQueue() = runTest {
        val firstQueue = newQueue()
        firstQueue.enqueue(request(112))
        firstQueue.enqueue(request(111))
        // The process dies after two files of the first request.
        val dying = runner(firstQueue, network(onFetch = { count -> if (count == 2) throw KillSwitch() }))
        try {
            dying.run()
        } catch (_: KillSwitch) {
            // killed
        }
        assertEquals(1, storage.snapshot().size, "one file was whole, the second was cut")
        fetched.clear()

        // A new process: the queue comes from its file, the runner from scratch.
        val secondQueue = newQueue()
        assertEquals(2, secondQueue.state.value.requests.size)
        assertEquals(RunOutcome.FINISHED, runner(secondQueue).run())

        // The three missing ayahs of 112 and the five of 111; the first ayah is not fetched again.
        assertEquals((2..4).map { url(112, it) } + (1..5).map { url(111, it) }, fetched)
        assertEquals(9, storage.snapshot().size)
        assertTrue(secondQueue.state.value.requests.isEmpty())
    }

    /** Not an Exception: nothing in the runner may catch it, like the death of the process. */
    private class KillSwitch : Error()

    @Test
    fun theLeftoversOfAnInterruptedFileAreRemovedAndTheFileIsFetchedAgain() = runTest {
        storage.temporaryFileFor("ayah_112_2").writeBytes(ByteArray(300))
        val queue = newQueue()
        queue.enqueue(request(112))
        runner(queue).run()
        assertEquals(emptyList(), storage.directory.list()!!.filter { it.endsWith(".download") })
        assertTrue(storage.contains("ayah_112_2"))
    }

    // -- Pause, cancel ----------------------------------------------------------------------------

    @Test
    fun pausingStopsAfterTheFileInProgressAndResumingGoesOn() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        val outcome = runner(queue, network(onFetch = { count -> if (count == 2) queue.pause() })).run()

        assertEquals(RunOutcome.PAUSED, outcome)
        assertEquals(2, fetched.size, "the file that was in progress finished, no new one started")
        assertEquals(2, storage.snapshot().size)
        assertEquals(1, queue.state.value.requests.size, "the request waits")

        queue.resume()
        fetched.clear()
        assertEquals(RunOutcome.FINISHED, runner(queue).run())
        assertEquals(listOf(url(112, 3), url(112, 4)), fetched)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun aPausedQueueIsNotTouched() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.pause()
        assertEquals(RunOutcome.PAUSED, runner(queue).run())
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun cancellingTheRequestInProgressStopsItAtOnceAndTheNextOneStarts() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        val outcome = runner(queue, network(onFetch = { count -> if (count == 2) queue.cancel("surah-112-alafasy") })).run()

        assertEquals(RunOutcome.FINISHED, outcome)
        // Two files of 112 (the second one finished), then the five of 111.
        assertEquals((1..2).map { url(112, it) } + (1..5).map { url(111, it) }, fetched)
        assertTrue(queue.state.value.requests.isEmpty())
        assertEquals(
            listOf("surah-112-alafasy" to FinishResult.CANCELLED, "surah-111-alafasy" to FinishResult.DONE),
            finished,
        )
        // What was downloaded of the cancelled request stays.
        assertTrue(storage.contains("ayah_112_1") && storage.contains("ayah_112_2"))
    }

    @Test
    fun cancellingEverythingEndsTheRun() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        val outcome = runner(queue, network(onFetch = { count -> if (count == 1) queue.cancelAll() })).run()
        assertEquals(RunOutcome.FINISHED, outcome)
        assertEquals(1, fetched.size)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    // -- The translation --------------------------------------------------------------------------

    @Test
    fun theTranslationOfASurahComesBeforeItsAudio() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112, language = "RU"))
        runner(queue).run()
        assertEquals(listOf("translation", "audio", "audio", "audio", "audio"), events)
        assertEquals(listOf(AppLanguage.RU to 112), translated)
    }

    @Test
    fun aJuzFetchesTheTranslationOfEverySurahInIt() = runTest {
        val queue = newQueue()
        queue.enqueue(
            DownloadRequest(
                id = "juz-30-alafasy", kind = DownloadKind.JUZ, title = "Juz", reciterId = "alafasy", language = "TR",
                segments = listOf(Segment(113, 1, 5), Segment(114, 1, 6)),
            ),
        )
        runner(queue).run()
        assertEquals(listOf(AppLanguage.TR to 113, AppLanguage.TR to 114), translated)
        // All translations first, then all audio.
        assertEquals(listOf("translation", "translation") + List(11) { "audio" }, events)
    }

    @Test
    fun withoutTheTranslationSwitchNothingIsAskedFor() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112, language = null))
        runner(queue).run()
        assertTrue(translated.isEmpty())
    }

    @Test
    fun aTranslationThatDoesNotComeIsAFailureOfTheRequestButTheAudioIsStillFetched() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112, language = "RU"))
        val outcome = runner(queue, translations = { _, _ -> false }).run()

        assertEquals(RunOutcome.FINISHED, outcome)
        assertEquals(4, storage.snapshot().size)
        assertEquals(1, queue.state.value.failedTranslations)
        assertEquals(0, queue.state.value.failedAyahs)
        assertEquals(listOf("surah-112-alafasy" to FinishResult.FAILED), finished)
        assertNull(queue.state.value.active)
    }

    // -- Failures ---------------------------------------------------------------------------------

    @Test
    fun anAyahIsTriedTwiceBeforeItIsGivenUp() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        var failuresLeft = 1
        runner(queue, network(failWhen = { failuresLeft-- > 0 })).run()
        // The first try of the first ayah failed, the second one worked: nothing failed.
        assertEquals(5, fetched.size)
        assertEquals(4, storage.snapshot().size)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun anAyahThatNeverComesIsCountedAndTheRestOfTheSurahIsStillFetched() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        val outcome = runner(queue, network(failWhen = { it == url(112, 2) })).run()

        assertEquals(RunOutcome.FINISHED, outcome)
        assertEquals(setOf("ayah_112_1", "ayah_112_3", "ayah_112_4"), storage.snapshot().keys)
        assertEquals(1, queue.state.value.failedAyahs)
        assertEquals(1, queue.state.value.requests.size, "a failed request stays for 'Retry'")

        // "Retry": only the missing ayah is fetched.
        fetched.clear()
        assertTrue(queue.retryFailed())
        assertEquals(RunOutcome.FINISHED, runner(queue).run())
        assertEquals(listOf(url(112, 2)), fetched)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun aFileThatIsTooSmallToBeAudioIsNotKept() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        val outcome = runner(queue, network(size = 100)).run()
        // Nothing is kept, and answers like that in a row look like a network that is not there.
        assertEquals(RunOutcome.NO_NETWORK, outcome)
        assertTrue(storage.snapshot().isEmpty())
        assertEquals(emptyList(), storage.directory.list()!!.toList())
    }

    @Test
    fun severalFailuresInARowStopTheRunSoTheWorkCanWaitForTheNetwork() = runTest {
        val queue = newQueue()
        queue.enqueue(request(2)) // 286 ayahs: it must stop long before the end
        val outcome = runner(queue, network(failWhen = { true })).run()

        assertEquals(RunOutcome.NO_NETWORK, outcome)
        // Three ayahs, two tries each.
        assertEquals(6, fetched.size)
        // Nothing is marked failed: the work is retried later and goes on.
        assertEquals(1, queue.state.value.requests.size)
        assertFalse(queue.state.value.hasFailures)
    }

    @Test
    fun theLastAttemptMarksTheRequestsThatCannotBeFinishedAsFailed() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        val outcome = runner(queue, network(failWhen = { true })).run(finalAttempt = true)

        assertEquals(RunOutcome.FINISHED, outcome)
        assertEquals(4 + 5, queue.state.value.failedAyahs)
        assertNull(queue.state.value.active)
        assertEquals(2, finished.count { it.second == FinishResult.FAILED })
    }

    @Test
    fun theNetworkComingBackLetsTheNextRunFinishWhatWasWaiting() = runTest {
        val queue = newQueue()
        queue.enqueue(request(112))
        assertEquals(RunOutcome.NO_NETWORK, runner(queue, network(failWhen = { true })).run())
        assertTrue(storage.snapshot().isEmpty())

        fetched.clear()
        assertEquals(RunOutcome.FINISHED, runner(queue).run())
        assertEquals(4, storage.snapshot().size)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    // -- Several files at once --------------------------------------------------------------------

    private val ayahsOfTwoSurahs = listOf(Segment(114, 1, 6), Segment(113, 1, 5)) // 11 files

    private fun twoSurahs() = DownloadRequest(
        id = "juz-30-alafasy", kind = DownloadKind.JUZ, title = "Juz", reciterId = "alafasy", segments = ayahsOfTwoSurahs,
    )

    @Test
    fun severalFilesAreOnTheWayAtOnceButNeverMoreThanTheLimit() = runTest {
        val queue = newQueue()
        queue.enqueue(twoSurahs())
        var inFlight = 0
        var most = 0
        val slow: AudioFetch = { _, target ->
            inFlight++
            most = maxOf(most, inFlight)
            kotlinx.coroutines.delay(10)
            target.writeBytes(ByteArray(4_000))
            inFlight--
            true
        }
        assertEquals(RunOutcome.FINISHED, runner(queue, slow, parallelism = 3).run())
        assertEquals(3, most)
        assertEquals(11, storage.snapshot().size)
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun theProgressOfParallelDownloadsEndsAtTheTotalWithoutGoingBack() = runTest {
        val queue = newQueue()
        queue.enqueue(twoSurahs())
        val slow: AudioFetch = { _, target ->
            kotlinx.coroutines.delay(10)
            target.writeBytes(ByteArray(4_000))
            true
        }
        runner(queue, slow, parallelism = 3).run()
        val done = progress.map { it.done }
        assertEquals(0, done.first())
        assertEquals(11, done.last())
        assertEquals(done.sorted(), done, "the count only grows")
        assertEquals(12, done.size)
    }

    @Test
    fun pausingWhileSeveralFilesAreOnTheWayLetsThemFinishAndStartsNoNewOne() = runTest {
        val queue = newQueue()
        queue.enqueue(twoSurahs())
        val slow: AudioFetch = { url, target ->
            fetched += url
            if (fetched.size == 2) queue.pause()
            kotlinx.coroutines.delay(10)
            target.writeBytes(ByteArray(4_000))
            true
        }
        assertEquals(RunOutcome.PAUSED, runner(queue, slow, parallelism = 3).run())
        // Three were started before the pause was seen; each of them finished and was kept.
        assertEquals(3, fetched.size)
        assertEquals(3, storage.snapshot().size)

        queue.resume()
        fetched.clear()
        assertEquals(RunOutcome.FINISHED, runner(queue, parallelism = 3).run())
        assertEquals(8, fetched.size, "only the files that are missing")
        assertEquals(fetched.size, fetched.toSet().size, "none twice")
        assertEquals(11, storage.snapshot().size)
    }

    @Test
    fun cancellingWhileSeveralFilesAreOnTheWayStopsStartingNewOnes() = runTest {
        val queue = newQueue()
        queue.enqueue(twoSurahs())
        val slow: AudioFetch = { url, target ->
            fetched += url
            if (fetched.size == 1) queue.cancelAll()
            kotlinx.coroutines.delay(10)
            target.writeBytes(ByteArray(4_000))
            true
        }
        assertEquals(RunOutcome.FINISHED, runner(queue, slow, parallelism = 3).run())
        assertTrue(fetched.size <= 3, "at most the files that were already on the way: ${fetched.size}")
        assertTrue(queue.state.value.requests.isEmpty())
    }

    @Test
    fun withoutANetworkTheParallelRunStopsAfterTheFirstFailuresToo() = runTest {
        val queue = newQueue()
        queue.enqueue(twoSurahs())
        val outcome = runner(queue, network(failWhen = { true }), parallelism = 3).run()
        assertEquals(RunOutcome.NO_NETWORK, outcome)
        assertTrue(fetched.size <= 3 * 2 + 3, "no endless tries: ${fetched.size}")
        assertTrue(storage.snapshot().isEmpty())
        assertEquals(1, queue.state.value.requests.size)
        assertFalse(queue.state.value.hasFailures)
    }
}
