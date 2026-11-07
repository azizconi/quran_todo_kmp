package tj.app.quran_todo.ui.downloads

import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.mushaf.MushafPages
import tj.app.quran_todo.ui.mushaf.data.PagesSource

/** The "Mushaf pages" of "Downloads and storage" (task F3a): the request, the size, the run of the work, the queue. */
class PagesDownloadsTest {
    private lateinit var dir: File
    private lateinit var storage: DownloadStorage
    private val finished = mutableListOf<Pair<String, FinishResult>>()
    private val progress = mutableListOf<DownloadProgress>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("pages-downloads-test").toFile()
        storage = DownloadStorage(File(dir, "downloads"))
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun newQueue() = DownloadQueue(FileQueueStore(File(dir, "state/queue.json"))) { request, result -> finished += request.id to result }

    private fun pagesRequest(juz: Int?, pages: List<Int> = if (juz == null) (1..604).toList() else MushafPages.pagesOfJuz(juz).toList()) = DownloadRequest(
        id = DownloadRequest.pagesId(juz),
        kind = DownloadKind.PAGES,
        title = if (juz == null) "All" else "Juz $juz",
        reciterId = "alafasy",
        segments = emptyList(),
        pages = pages,
    )

    /** The pinned folder as the runner sees it: which pages are there, which ones cannot be had. */
    private class FakePages(val offline: Set<Int> = emptySet(), var down: Boolean = false) : PagesSource {
        val pinned = linkedSetOf<Int>()
        val asked = mutableListOf<Int>()
        var cleaned = 0

        override fun has(page: Int) = page in pinned

        override suspend fun pin(page: Int): Boolean {
            asked += page
            if (down || page in offline) return false
            pinned += page
            return true
        }

        override fun deleteUnfinished() {
            cleaned++
        }
    }

    private fun runner(
        queue: DownloadQueue,
        pages: PagesSource,
        maxFailures: Int = 3,
        onFetchAudio: () -> Unit = {},
    ) = DownloadRunner(
        queue = queue,
        storage = storage,
        fetch = { _, _ -> onFetchAudio(); false },
        translations = { _, _ -> true },
        onProgress = { progress += it },
        pause = {},
        attemptsPerAyah = 2,
        maxConsecutiveFailures = maxFailures,
        parallelism = 1,
        pages = pages,
        pageParallelism = 1,
    )

    // -- The request ---------------------------------------------------------------------------------------------

    @Test
    fun aPagesRequestIsKeyedByItsPagesAndNotByAReciter() {
        val juz30 = pagesRequest(30)
        assertEquals("pages-juz-30", juz30.id)
        assertEquals("pages-all", DownloadRequest.pagesId(null))
        assertEquals((582..604).map { "mushaf-page-$it" }, juz30.keys())
        assertEquals(23, juz30.keys().size)
        assertEquals(0, juz30.ayahCount)
        assertEquals(DownloadKind.PAGES, juz30.kind)
        assertEquals("pages", DownloadKind.PAGES.analyticsName)
        // A key of a page is never the key of an audio file, and the other way round.
        assertEquals(30, PageKeys.pageOf(PageKeys.of(30)))
        assertNull(PageKeys.pageOf("ayah_78_1"))
        assertNull(PageKeys.pageOf("husary_78_1"))
        assertNull(PageKeys.pageOf("mushaf-page-x"))
    }

    @Test
    fun theQueueFileKeepsThePagesAndReadsAnOldFileWithoutThem() {
        val queue = newQueue()
        queue.enqueue(pagesRequest(30))
        val reloaded = DownloadQueue(FileQueueStore(File(dir, "state/queue.json")))
        val request = reloaded.state.value.requests.single()
        assertEquals(DownloadKind.PAGES, request.kind)
        assertEquals((582..604).toList(), request.pages)

        // A queue file written before the pages existed has no `pages` and no `pages` in a failure.
        val old = """{"requests":[{"id":"surah-112-alafasy","kind":"SURAH","title":"S","reciterId":"alafasy","segments":[{"surah":112,"first":1,"last":4}]}],
            |"failures":[{"requestId":"surah-112-alafasy","ayahs":2,"translations":0}]}""".trimMargin()
        val state = Json { ignoreUnknownKeys = true }.decodeFromString(QueueState.serializer(), old)
        assertEquals(emptyList(), state.requests.single().pages)
        assertEquals(0, state.failures.single().pages)
        assertEquals(2, state.failedAyahs)
        assertEquals(0, state.failedPages)
    }

    // -- The size ------------------------------------------------------------------------------------------------

    @Test
    fun theEstimateOfAllPagesIsTheMeasuredTotalAndAJuzIsAboutThreeMegabytes() {
        // The real total of the 604 fonts and layout copies (2026-10-31): 96 498 614 bytes. The three rates give it back.
        val all = PagesSize.estimateBytes((1..604).toList())
        assertTrue(abs(all - 96_498_614L) < 100_000, "$all")
        val mib = 1024.0 * 1024.0
        for (juz in 2..29) {
            val bytes = PagesSize.estimateBytes(MushafPages.pagesOfJuz(juz).toList())
            assertTrue(bytes / mib in 2.85..3.25, "juz $juz: ${bytes / mib}")
        }
        // Juz 30 (23 pages with smaller fonts) was measured at 3.15 MiB, Juz 1 (21 pages, the first two have small fonts) at 2.92 MiB.
        assertTrue(abs(PagesSize.estimateBytes(MushafPages.pagesOfJuz(30).toList()) / mib - 3.15) < 0.1)
        assertTrue(abs(PagesSize.estimateBytes(MushafPages.pagesOfJuz(1).toList()) / mib - 2.92) < 0.2)
        // Pages 1 and 2 are small.
        assertTrue(PagesSize.bytesOfPage(1) < PagesSize.bytesOfPage(3) / 2)
    }

    @Test
    fun theSizeThatIsLeftCountsEveryMissingPageOnceEvenWhenTwoRequestsAskForIt() {
        val juz30 = pagesRequest(30)
        val all = pagesRequest(null)
        val have = mapOf(PageKeys.of(604) to 100_000L, PageKeys.of(603) to 100_000L)
        val remaining = PagesSize.remainingBytes(listOf(juz30, all), have)
        val expected = PagesSize.estimateBytes((1..604).filter { it != 603 && it != 604 })
        assertEquals(expected, remaining)
        // Audio requests have nothing to do with it.
        val audio = DownloadRequest("surah-112-alafasy", DownloadKind.SURAH, "S", "alafasy", segments = DownloadSelection.surah(112))
        assertEquals(0L, PagesSize.remainingBytes(listOf(audio), emptyMap()))
    }

    // -- The work ---------------------------------------------------------------------------------------------------

    @Test
    fun aPagesRequestPinsEveryPageAndIsDone() = runTest {
        val queue = newQueue()
        queue.enqueue(pagesRequest(30))
        val pages = FakePages()
        var audioFetched = false

        assertEquals(RunOutcome.FINISHED, runner(queue, pages) { audioFetched = true }.run())

        assertEquals((582..604).toList(), pages.asked)
        assertEquals((582..604).toSet(), pages.pinned)
        assertTrue(queue.state.value.requests.isEmpty())
        assertEquals(listOf("pages-juz-30" to FinishResult.DONE), finished)
        assertFalse(audioFetched, "no audio and no translation for a pages request")
        assertEquals(1, pages.cleaned, "the leftovers of an interrupted run are cleaned first")
        // The notification counts pages.
        assertEquals(23, progress.last().total)
        assertEquals(23, progress.last().done)
    }

    @Test
    fun aPageThatIsPinnedAlreadyIsSkippedSoARunAfterAnInterruptionCarriesOn() = runTest {
        val queue = newQueue()
        queue.enqueue(pagesRequest(30))
        val pages = FakePages().apply { pinned += listOf(582, 583, 584) }

        assertEquals(RunOutcome.FINISHED, runner(queue, pages).run())
        assertEquals((585..604).toList(), pages.asked)
        assertEquals(3, progress.first().done, "the bar starts where the last run stopped")
    }

    @Test
    fun aPageThatCannotBeHadFailsTheRequestWithTheNumberOfPagesAndTheRestIsStillPinned() = runTest {
        val queue = newQueue()
        queue.enqueue(pagesRequest(30))
        val pages = FakePages(offline = setOf(590, 600))

        assertEquals(RunOutcome.FINISHED, runner(queue, pages).run())

        assertEquals(21, pages.pinned.size)
        val failure = queue.state.value.failures.single()
        assertEquals(RequestFailure("pages-juz-30", ayahs = 0, translations = 0, pages = 2), failure)
        assertEquals(2, queue.state.value.failedPages)
        assertEquals(0, queue.state.value.failedAyahs)
        assertEquals(listOf("pages-juz-30" to FinishResult.FAILED), finished)
        // A page is tried twice before it counts as failed.
        assertEquals(2, pages.asked.count { it == 590 })
    }

    @Test
    fun severalPagesInARowThatDoNotComeMeanThereIsNoNetworkAndTheWorkWaits() = runTest {
        val queue = newQueue()
        queue.enqueue(pagesRequest(30))
        val pages = FakePages(down = true)

        assertEquals(RunOutcome.NO_NETWORK, runner(queue, pages).run())

        // Three pages (twice each) and it stops: it does not go through all 23.
        assertEquals(3, pages.asked.toSet().size)
        assertEquals(1, queue.state.value.requests.size)
        assertTrue(queue.state.value.failures.isEmpty())
    }

    @Test
    fun theLastAttemptMarksTheRequestFailedWithThePagesThatAreLeft() = runTest {
        val queue = newQueue()
        queue.enqueue(pagesRequest(30))
        val pages = FakePages(down = true)

        assertEquals(RunOutcome.FINISHED, runner(queue, pages).run(finalAttempt = true))
        assertEquals(RequestFailure("pages-juz-30", ayahs = 0, translations = 0, pages = 23), queue.state.value.failures.single())
    }

    @Test
    fun pausingAndCancellingStopTheNextPage() = runTest {
        val queue = newQueue()
        queue.enqueue(pagesRequest(30))
        val pages = object : PagesSource {
            val pinned = mutableSetOf<Int>()
            override fun has(page: Int) = page in pinned
            override suspend fun pin(page: Int): Boolean {
                pinned += page
                if (pinned.size == 2) queue.pause()
                return true
            }
            override fun deleteUnfinished() = Unit
        }
        assertEquals(RunOutcome.PAUSED, runner(queue, pages).run())
        assertEquals(2, pages.pinned.size, "the page that was on the way finished, no new one started")
        assertEquals(1, queue.state.value.requests.size)

        // Cancelled while it runs: the request is gone and nothing is marked failed.
        val queue2 = DownloadQueue(FileQueueStore(File(dir, "state/queue2.json")))
        queue2.enqueue(pagesRequest(30))
        val cancelling = object : PagesSource {
            var count = 0
            override fun has(page: Int) = false
            override suspend fun pin(page: Int): Boolean {
                if (++count == 3) queue2.cancel("pages-juz-30")
                return true
            }
            override fun deleteUnfinished() = Unit
        }
        runner(queue2, cancelling).run()
        assertEquals(3, cancelling.count)
        assertTrue(queue2.state.value.requests.isEmpty())
        assertTrue(queue2.state.value.failures.isEmpty())
    }

    @Test
    fun audioAndPagesShareOneQueueAndEachGoesItsWay() = runTest {
        val queue = newQueue()
        queue.enqueue(
            DownloadRequest(
                id = DownloadRequest.idOf(DownloadKind.SURAH, 112, "alafasy"),
                kind = DownloadKind.SURAH,
                title = "S112",
                reciterId = "alafasy",
                segments = DownloadSelection.surah(112),
            ),
        )
        queue.enqueue(pagesRequest(30, pages = listOf(604)))
        val pages = FakePages()
        val audio = mutableListOf<String>()
        val runner = DownloadRunner(
            queue = queue,
            storage = storage,
            fetch = { url, target ->
                audio += url
                target.writeBytes(ByteArray(2_000) { 1 })
                true
            },
            translations = { _, _ -> true },
            pause = {},
            parallelism = 1,
            pages = pages,
            pageParallelism = 1,
        )
        assertEquals(RunOutcome.FINISHED, runner.run())
        assertEquals(4, audio.size)
        assertEquals(setOf(604), pages.pinned)
        assertTrue(queue.state.value.requests.isEmpty())
        assertEquals(listOf("surah-112-alafasy", "pages-juz-30"), finished.map { it.first })
    }
}
