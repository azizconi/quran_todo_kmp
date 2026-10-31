package tj.app.quran_todo.ui.tajweed

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import tj.app.quran_todo.common.tajweed.TajweedFormat
import tj.app.quran_todo.common.tajweed.TajweedRule
import tj.app.quran_todo.data.bundled.QuranFacts

class TajweedStoreTest {
    private val directories = mutableListOf<File>()

    private fun directory(): File = Files.createTempDirectory("tajweed-store").toFile().also { directories += it }

    @AfterTest
    fun cleanUp() {
        directories.forEach { it.deleteRecursively() }
    }

    /** A whole-Quran answer whose ayahs are `[h[ٱ]ل<surah>:<ayah>`. */
    private fun answer(ayahsOf: (Int) -> Int = QuranFacts::ayahCount, surahs: Int = QuranFacts.SURAH_COUNT): String = buildString {
        append("""{"code":200,"status":"OK","data":{"surahs":[""")
        for (surah in 1..surahs) {
            if (surah > 1) append(',')
            append("""{"number":$surah,"ayahs":[""")
            for (ayah in 1..ayahsOf(surah)) {
                if (ayah > 1) append(',')
                append("""{"number":$ayah,"text":"[h[ٱ]ل$surah-$ayah","numberInSurah":$ayah}""")
            }
            append("]}")
        }
        append("]}}")
    }

    private class FakeNetwork(private val body: () -> String?) {
        var requests = 0
        var requestedUrl: String? = null

        val fetch: suspend (String, File, (Long) -> Unit) -> Boolean = fetch@{ url, target, onBytes ->
            requests++
            requestedUrl = url
            val text = body() ?: return@fetch false
            target.writeText(text)
            onBytes(text.length.toLong() / 2)
            onBytes(text.length.toLong())
            true
        }
    }

    private var now = 1_000_000L

    private fun store(dir: File, network: FakeNetwork, maxSurahs: Int = 10) = TajweedStore(
        directory = dir,
        fetch = network.fetch,
        // Everything runs on the calling thread: the fake network never suspends.
        scope = CoroutineScope(Dispatchers.Unconfined),
        now = { now },
        maxSurahs = maxSurahs,
    )

    @Test
    fun theFirstSwitchFetchesTheWholeQuranInOneRequestAndKeepsEverySurah() {
        val dir = directory()
        val network = FakeNetwork { answer() }
        val store = store(dir, network)
        assertEquals(TajweedAvailability.Missing, store.availability.value)

        store.ensureDownloaded(force = true)

        assertEquals(TajweedAvailability.Ready, store.availability.value)
        assertEquals(1, network.requests)
        assertEquals("https://api.alquran.cloud/v1/quran/quran-tajweed", network.requestedUrl)
        assertEquals(QuranFacts.SURAH_COUNT + 1, dir.listFiles()!!.size, "114 surah files and the marker, nothing else")
        assertTrue(File(dir, "s001.txt").isFile)
        assertTrue(File(dir, "s114.txt").isFile)
        assertFalse(File(dir, "download.part").exists())
    }

    @Test
    fun aFileThatIsThereIsReadyWithoutAnyRequest() {
        val dir = directory()
        store(dir, FakeNetwork { answer() }).ensureDownloaded(force = true)

        val network = FakeNetwork { error("no request expected") }
        val again = store(dir, network)
        assertEquals(TajweedAvailability.Ready, again.availability.value)
        again.ensureDownloaded()
        assertEquals(0, network.requests)
    }

    @Test
    fun withoutNetworkItNeedsTheInternetAndRetryFetchesAtOnce() {
        val dir = directory()
        var online = false
        val network = FakeNetwork { if (online) answer() else null }
        val store = store(dir, network)

        store.ensureDownloaded(force = true)
        assertEquals(TajweedAvailability.NeedsInternet, store.availability.value)
        assertFalse(File(dir, "ready").exists())

        online = true
        store.ensureDownloaded(force = true)
        assertEquals(TajweedAvailability.Ready, store.availability.value)
        assertEquals(2, network.requests)
    }

    @Test
    fun theReaderDoesNotAskAgainBeforeThirtySecondsButRetryDoes() {
        val dir = directory()
        var online = false
        val network = FakeNetwork { if (online) answer() else null }
        val store = store(dir, network)
        store.ensureDownloaded()
        assertEquals(TajweedAvailability.NeedsInternet, store.availability.value)

        // The reader opens a surah a moment later: no new request.
        runBlocking { store.prepare(2) }
        assertEquals(1, network.requests)

        // Thirty seconds later it asks again, and now the network is there.
        now += TajweedStore.RETRY_AFTER_MS + 1
        online = true
        runBlocking { store.prepare(2) }
        assertEquals(2, network.requests)
        assertEquals(TajweedAvailability.Ready, store.availability.value)
    }

    @Test
    fun aBrokenOrPartialAnswerIsNeverKept() {
        val dir = directory()
        val partial = FakeNetwork { answer(surahs = 100) }
        val store = store(dir, partial)
        store.ensureDownloaded(force = true)
        assertEquals(TajweedAvailability.NeedsInternet, store.availability.value)
        assertFalse(File(dir, "ready").exists())

        val broken = FakeNetwork { "<html>502</html>" }
        val second = store(dir, broken)
        second.ensureDownloaded(force = true)
        assertEquals(TajweedAvailability.NeedsInternet, second.availability.value)
        assertFalse(File(dir, "ready").exists())
    }

    @Test
    fun theProgressIsReportedWhileTheFileComes() {
        val dir = directory()
        val seen = mutableListOf<TajweedAvailability>()
        lateinit var store: TajweedStore
        val fetch: suspend (String, File, (Long) -> Unit) -> Boolean = { _, target, onBytes ->
            target.writeText(answer())
            onBytes(TajweedFormat.EXPECTED_BYTES / 2)
            seen += store.availability.value
            true
        }
        store = TajweedStore(dir, fetch, CoroutineScope(Dispatchers.Unconfined), { now })
        store.ensureDownloaded(force = true)
        val midway = assertIs<TajweedAvailability.Downloading>(seen.single())
        assertTrue(midway.fraction in 0.45f..0.55f, "${midway.fraction}")
        assertEquals(TajweedAvailability.Ready, store.availability.value)
    }

    @Test
    fun aSurahIsReadLazilyAndItsRulesAreMovedOntoTheTextOfTheScreen() {
        val dir = directory()
        val store = store(dir, FakeNetwork { answer() })
        store.ensureDownloaded(force = true)

        // Nothing is in memory, nothing is asked of the file by the lookup.
        assertNull(store.spansIfReady(2, 3, "ٱل2-3"))
        assertEquals(emptyList(), store.surahsInMemory())

        assertTrue(runBlocking { store.load(2) })
        assertEquals(listOf(2), store.surahsInMemory())
        val spans = assertNotNull(store.spansIfReady(2, 3, "ٱل2-3"))
        assertEquals(1, spans.size)
        assertEquals(TajweedRule.HamzatWasl, spans.single().rule)
        assertEquals(0..0, spans.single().range)
        // Another text for the same ayah is aligned again, not served from the old result.
        assertEquals(1..1, store.spansIfReady(2, 3, "اٱل2-3")!!.single().range)
        // An ayah that does not exist gives nothing.
        assertEquals(emptyList(), store.spansIfReady(2, 287, "ٱل"))
    }

    @Test
    fun onlyTheLastTenSurahsStayParsedInMemory() {
        val dir = directory()
        val store = store(dir, FakeNetwork { answer() })
        store.ensureDownloaded(force = true)
        runBlocking { (1..12).forEach { store.load(it) } }
        assertEquals((3..12).toList(), store.surahsInMemory())
        // A surah that is used again moves to the end and is not the next to go.
        store.spansIfReady(3, 1, "ٱل3-1")
        runBlocking { store.load(13) }
        assertEquals((5..12).toList() + listOf(3, 13), store.surahsInMemory())
    }

    @Test
    fun aSurahFileThatCannotBeReadIsDroppedAndFetchedAgain() {
        val dir = directory()
        val network = FakeNetwork { answer() }
        val store = store(dir, network)
        store.ensureDownloaded(force = true)
        File(dir, "s005.txt").writeText("not enough lines")

        assertFalse(runBlocking { store.load(5) })
        assertEquals(TajweedAvailability.Missing, store.availability.value)
        assertFalse(File(dir, "ready").exists())

        // The next time the reader asks, the file comes again.
        runBlocking { store.prepare(5) }
        assertEquals(TajweedAvailability.Ready, store.availability.value)
        assertEquals(2, network.requests)
        assertTrue(runBlocking { store.load(5) })
    }

    @Test
    fun aSurahThatDoesNotExistIsNotLoaded() {
        val dir = directory()
        val store = store(dir, FakeNetwork { answer() })
        store.ensureDownloaded(force = true)
        assertFalse(runBlocking { store.load(0) })
        assertFalse(runBlocking { store.load(115) })
        assertNull(store.spansIfReady(115, 1, "x"))
    }

    @Test
    fun theVersionGrowsWhenTheFileArrivesAndWhenASurahIsRead() {
        val dir = directory()
        val store = store(dir, FakeNetwork { answer() })
        val start = store.version.value
        store.ensureDownloaded(force = true)
        val afterDownload = store.version.value
        assertTrue(afterDownload > start)
        runBlocking { store.load(1) }
        assertTrue(store.version.value > afterDownload)
    }
}
