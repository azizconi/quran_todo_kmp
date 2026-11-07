package tj.app.quran_todo.ui.downloads

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadQueueTest {
    private lateinit var dir: File
    private val finished = mutableListOf<Pair<String, FinishResult>>()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("download-queue-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun queueFile() = File(dir, "state/queue.json")

    private fun newQueue() = DownloadQueue(FileQueueStore(queueFile())) { request, result -> finished += request.id to result }

    private fun request(surah: Int, title: String = "S$surah", reciter: String = "alafasy", language: String? = "RU") = DownloadRequest(
        id = DownloadRequest.idOf(DownloadKind.SURAH, surah, reciter),
        kind = DownloadKind.SURAH,
        title = title,
        reciterId = reciter,
        language = language,
        segments = DownloadSelection.surah(surah),
    )

    @Test
    fun anEmptyQueueHasNothingToDo() {
        val queue = newQueue()
        assertEquals(QueueState(), queue.state.value)
        assertNull(queue.state.value.active)
        assertFalse(queue.state.value.hasWork)
    }

    @Test
    fun requestsAreTakenInTheOrderTheyWereAsked() {
        val queue = newQueue()
        assertTrue(queue.enqueue(request(112)))
        assertTrue(queue.enqueue(request(111)))
        assertEquals("surah-112-alafasy", queue.state.value.active?.id)
        assertEquals(1, queue.state.value.waiting)
        queue.complete("surah-112-alafasy")
        assertEquals("surah-111-alafasy", queue.state.value.active?.id)
        assertEquals(0, queue.state.value.waiting)
        assertEquals(listOf("surah-112-alafasy" to FinishResult.DONE), finished)
    }

    @Test
    fun theSameRequestIsNotQueuedTwice() {
        val queue = newQueue()
        assertTrue(queue.enqueue(request(112)))
        assertFalse(queue.enqueue(request(112)))
        assertEquals(1, queue.state.value.requests.size)
        // The same surah by another reciter is another request.
        assertTrue(queue.enqueue(request(112, reciter = "husary")))
        assertEquals(2, queue.state.value.requests.size)
    }

    @Test
    fun theQueueIsStoredAndTheNextProcessCarriesOnFromIt() {
        val first = newQueue()
        first.enqueue(request(112))
        first.enqueue(request(111, language = null))
        first.pause()

        // The process dies; a new one reads the file.
        val second = newQueue()
        assertEquals(first.state.value, second.state.value)
        assertEquals(listOf("surah-112-alafasy", "surah-111-alafasy"), second.state.value.requests.map { it.id })
        assertEquals("RU", second.state.value.requests[0].language)
        assertNull(second.state.value.requests[1].language)
        assertTrue(second.state.value.paused)
        assertEquals(DownloadSelection.surah(112), second.state.value.requests[0].segments)
    }

    @Test
    fun aDamagedFileIsAnEmptyQueue() {
        queueFile().parentFile.mkdirs()
        queueFile().writeText("{ this is not json")
        assertEquals(QueueState(), newQueue().state.value)
    }

    @Test
    fun noTemporaryFileIsLeftBesideTheQueueFile() {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        assertEquals(listOf("queue.json"), queueFile().parentFile.list()!!.toList())
    }

    @Test
    fun pausingStopsTheWorkUntilResume() {
        val queue = newQueue()
        queue.enqueue(request(112))
        assertTrue(queue.state.value.hasWork)
        queue.pause()
        assertTrue(queue.state.value.paused)
        assertFalse(queue.state.value.hasWork)
        // The request is still there.
        assertEquals(1, queue.state.value.requests.size)
        queue.resume()
        assertTrue(queue.state.value.hasWork)
    }

    @Test
    fun cancellingOneRequestKeepsTheOthersAndReportsIt() {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        assertTrue(queue.cancel("surah-112-alafasy"))
        assertFalse(queue.cancel("surah-112-alafasy"))
        assertEquals(listOf("surah-111-alafasy"), queue.state.value.requests.map { it.id })
        assertEquals(listOf("surah-112-alafasy" to FinishResult.CANCELLED), finished)
    }

    @Test
    fun cancellingAllEmptiesTheQueueAndUnpausesIt() {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        queue.pause()
        queue.cancelAll()
        assertEquals(QueueState(), queue.state.value)
        assertEquals(2, finished.count { it.second == FinishResult.CANCELLED })
        // A new request after "cancel" starts at once.
        queue.enqueue(request(110))
        assertTrue(queue.state.value.hasWork)
    }

    @Test
    fun aFailedRequestStaysAndIsSkippedUntilRetry() {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        queue.fail("surah-112-alafasy", ayahs = 3, translations = 0)

        val state = queue.state.value
        assertEquals("surah-111-alafasy", state.active?.id, "the worker goes on to the next request")
        assertEquals(2, state.requests.size)
        assertEquals(3, state.failedAyahs)
        assertTrue(state.hasFailures)
        assertEquals(listOf("surah-112-alafasy" to FinishResult.FAILED), finished)

        assertTrue(queue.retryFailed())
        assertEquals("surah-112-alafasy", queue.state.value.active?.id)
        assertFalse(queue.state.value.hasFailures)
        assertFalse(queue.retryFailed(), "nothing is left to retry")
    }

    @Test
    fun askingAgainForAFailedRequestRetriesIt() {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.fail("surah-112-alafasy", ayahs = 1, translations = 1)
        assertNull(queue.state.value.active)
        assertTrue(queue.enqueue(request(112)))
        assertEquals("surah-112-alafasy", queue.state.value.active?.id)
        assertEquals(1, queue.state.value.requests.size)
    }

    @Test
    fun theFailuresAreAddedUpForTheScreen() {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.enqueue(request(111))
        queue.fail("surah-112-alafasy", ayahs = 2, translations = 1)
        queue.fail("surah-111-alafasy", ayahs = 3, translations = 0)
        assertEquals(5, queue.state.value.failedAyahs)
        assertEquals(1, queue.state.value.failedTranslations)
    }

    @Test
    fun completingARequestThatIsNotThereChangesNothing() {
        val queue = newQueue()
        queue.enqueue(request(112))
        queue.complete("surah-999-alafasy")
        assertEquals(1, queue.state.value.requests.size)
        assertTrue(finished.isEmpty())
    }
}
