package tj.app.quran_todo.common.audio.timing

import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.data.translation.FailureThrottle
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordTimingsStoreTest {
    private val root: File = createTempDirectory("timings").toFile()
    private val pinned = File(root, "downloads").apply { mkdirs() }
    private val cache = File(root, "offline_audio").apply { mkdirs() }

    /** A fake server: the answer for each URL; every request is recorded. */
    private class Server(val answers: MutableMap<String, String> = mutableMapOf()) {
        val requests = mutableListOf<String>()
        var offline = false

        val fetch: AudioFetch = { url, target ->
            requests += url
            val answer = answers.entries.firstOrNull { url.startsWith(it.key) }?.value
            if (offline || answer == null) {
                false
            } else {
                target.writeText(answer)
                true
            }
        }
    }

    private var now = 1_000_000L
    private val server = Server(mutableMapOf("https://api.quran.com/api/v4/recitations/7/by_chapter/1" to WordTimingsFixtures.AlafasyFatiha))

    private fun store(
        fetch: AudioFetch = server.fetch,
        throttle: FailureThrottle = FailureThrottle(windowMs = 30_000, clock = { now }),
        pinnedDir: File = pinned,
        cacheDir: File = cache,
    ) = WordTimingsStore(pinned = pinnedDir, cache = cacheDir, fetch = fetch, throttle = throttle)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun theAddressIsOneRequestForTheWholeSurah() {
        assertEquals(
            "https://api.quran.com/api/v4/recitations/7/by_chapter/2?fields=segments&per_page=300",
            store().url(recitationId = 7, surah = 2),
        )
        assertEquals("timings_7_2.json", store().fileName(7, 2))
    }

    @Test
    fun theFirstLoadFetchesOnceAndTheCopyIsKeptInTheCache() = runTest {
        val first = store().load(7, 1)
        assertNotNull(first)
        assertEquals((1..7).toList(), first.ayahs.keys.toList())
        assertEquals(1, server.requests.size)
        assertTrue(File(cache, "timings_7_1.json").isFile)
        // Nothing but the final file is left behind.
        assertEquals(listOf("timings_7_1.json"), cache.list().orEmpty().toList())

        // The same store: from memory. A new store (a new process): from the file. Neither asks the server.
        assertNotNull(store().load(7, 1))
        assertEquals(1, server.requests.size)
        val second = store()
        assertEquals(first, second.load(7, 1))
        assertEquals(1, server.requests.size)
    }

    @Test
    fun withoutACopyAndWithoutNetworkThereIsNothingAndNoError() = runTest {
        server.offline = true
        assertNull(store().load(7, 1))
        assertEquals(emptyList(), cache.list().orEmpty().toList())
        assertNull(store().local(7, 1))
    }

    @Test
    fun aFailedRequestIsNotRepeatedWithinThirtySeconds() = runTest {
        server.offline = true
        val store = store()
        assertNull(store.load(7, 1))
        assertEquals(1, server.requests.size)

        server.offline = false
        now += 29_999
        assertNull(store.load(7, 1), "still held back")
        assertEquals(1, server.requests.size)

        now += 2
        assertNotNull(store.load(7, 1))
        assertEquals(2, server.requests.size)
    }

    @Test
    fun anAnswerThatIsNotTimingsCountsAsAFailure() = runTest {
        server.answers["https://api.quran.com/api/v4/recitations/7/by_chapter/2"] = """{"error":"rate limit"}"""
        val store = store()
        assertNull(store.load(7, 2))
        assertNull(store.load(7, 2))
        assertEquals(1, server.requests.size, "held back")
        assertEquals(emptyList(), cache.list().orEmpty().toList())
    }

    @Test
    fun aDamagedCopyIsDeletedAndFetchedAgain() = runTest {
        File(cache, "timings_7_1.json").writeText("""{"v":1,"r":7,"s":1,"a":{"1":[0,10""")
        val timings = store().load(7, 1)
        assertNotNull(timings)
        assertEquals(1, server.requests.size)
        assertNotNull(store().local(7, 1))
    }

    @Test
    fun aCopyOfAnotherRecitationOrSurahIsNotUsed() = runTest {
        // A file with the right name but the content of another recitation (a renamed or damaged file).
        val other = WordTimingsFormat.encode(WordTimings(recitationId = 3, surah = 1, byAyah = mapOf(1 to listOf(WordSpan(0, 1, 2)))))
        File(cache, "timings_7_1.json").writeText(other)
        assertNull(store().local(7, 1))
        assertNotNull(store().load(7, 1))
        assertEquals(1, server.requests.size)
    }

    @Test
    fun theDownloadsWinOverTheCacheAndAreNotTouched() = runTest {
        val real = WordTimingsFormat.parseQuranCom(WordTimingsFixtures.AlafasyFatiha, 7, 1)!!
        val pinnedCopy = WordTimings(7, 1, real.ayahs.filterKeys { it <= 2 })
        File(pinned, "timings_7_1.json").writeText(WordTimingsFormat.encode(pinnedCopy))
        File(cache, "timings_7_1.json").writeText(WordTimingsFormat.encode(real))
        assertEquals(pinnedCopy, store().load(7, 1))
        assertEquals(0, server.requests.size)
    }

    @Test
    fun pinningTakesTheCopyOfTheCacheWithoutAskingTheServer() = runTest {
        val store = store()
        assertNotNull(store.load(7, 1))
        assertEquals(1, server.requests.size)
        assertFalse(File(pinned, "timings_7_1.json").exists())

        assertTrue(store.pin(7, 1))
        assertEquals(1, server.requests.size)
        assertTrue(File(pinned, "timings_7_1.json").isFile)
        // The listening cache can be cleared: the downloaded copy stays and is what is read.
        cache.listFiles().orEmpty().forEach { it.delete() }
        assertEquals(7, assertNotNull(store().load(7, 1)).ayahs.size)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun pinningFetchesStraightIntoTheDownloadsWhenThereIsNoCopy() = runTest {
        val store = store()
        assertTrue(store.pin(7, 1))
        assertEquals(1, server.requests.size)
        assertTrue(File(pinned, "timings_7_1.json").isFile)
        assertEquals(emptyList(), cache.list().orEmpty().toList(), "the cache is not filled by a download")
        // Already there: nothing more to do.
        assertTrue(store.pin(7, 1))
        assertEquals(1, server.requests.size)
    }

    @Test
    fun pinningReportsAFailureAndLeavesNothingBehind() = runTest {
        server.offline = true
        assertFalse(store().pin(7, 1))
        assertEquals(emptyList(), pinned.list().orEmpty().toList())
        // A failed listening request does not hold the downloads back (the work has its own backoff).
        val store = store()
        assertNull(store.load(7, 1))
        server.offline = false
        assertTrue(store.pin(7, 1))
    }

    @Test
    fun onlyTheLatestSurahsAreKeptInMemory() = runTest {
        for (surah in 1..3) {
            server.answers["https://api.quran.com/api/v4/recitations/7/by_chapter/$surah"] =
                WordTimingsFixtures.AlafasyFatiha.replace("\"1:", "\"$surah:")
        }
        val store = WordTimingsStore(pinned, cache, server.fetch, throttle = FailureThrottle(clock = { now }), memorySize = 2)
        for (surah in 1..3) assertNotNull(store.load(7, surah))
        assertEquals(3, server.requests.size)
        // The files are there, so even the evicted one needs no request.
        for (surah in 1..3) assertNotNull(store.load(7, surah))
        assertEquals(3, server.requests.size)
    }
}
