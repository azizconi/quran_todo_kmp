package tj.app.quran_todo.ui.mushaf.data

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.mushaf.MushafFixtures
import tj.app.quran_todo.common.mushaf.MushafLayoutFormat
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.data.translation.FailureThrottle

class MushafPageStoreTest {
    private val root: File = createTempDirectory("mushaf-store").toFile()
    private val pinned = File(root, "downloads").apply { mkdirs() }
    private val cache = File(root, "mushaf_cache").apply { mkdirs() }
    private var now = 1_000_000L

    /** A real page as the layout of any page number: the tests care about where a page comes from, not about its words. */
    private val sample: MushafPage = MushafLayoutFormat.parseQuranCom(MushafFixtures.answer(3), 3)!!

    private fun pageOf(number: Int) = MushafPage(number, sample.lines)

    /** A font that starts the way a TrueType font does and is big enough. */
    private fun fontBytes(size: Int = 6_000) = ByteArray(size) { if (it < 4) byteArrayOf(0, 1, 0, 0)[it] else 7 }

    private class FakeSource(val make: (Int) -> MushafPage, val font: ByteArray) : MushafLayoutSource {
        val layouts = mutableListOf<Int>()
        val fonts = mutableListOf<Int>()
        var offline = false
        var fontOverride: ByteArray? = null

        override suspend fun layout(page: Int): MushafPage? {
            layouts += page
            return if (offline) null else make(page)
        }

        override suspend fun font(page: Int, target: File): Boolean {
            fonts += page
            if (offline) return false
            target.writeBytes(fontOverride ?: font)
            return true
        }
    }

    private val source = FakeSource(::pageOf, fontBytes())

    private fun store(
        pinnedDir: File = pinned,
        cacheDir: File = cache,
        memoryPages: Int = 10,
        cacheLimit: Long = 48L * 1024 * 1024,
        fake: FakeSource = source,
    ) = MushafPageStore(
        pinned = pinnedDir,
        cache = cacheDir,
        source = fake,
        throttle = FailureThrottle(windowMs = 30_000, clock = { now }),
        memoryPages = memoryPages,
        cacheLimitBytes = cacheLimit,
        now = { now },
    )

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    // -- The order of the search --------------------------------------------------------------------------------

    @Test
    fun theFirstLoadFetchesTheLayoutAndTheFontOnceAndKeepsBothInTheCache() = runTest {
        val loaded = assertNotNull(store().load(3))
        assertEquals(pageOf(3), loaded.page)
        assertEquals(File(cache, "mushaf_f3.ttf"), loaded.font)
        assertEquals(listOf(3), source.layouts)
        assertEquals(listOf(3), source.fonts)
        // Nothing but the two final files is left behind.
        assertEquals(listOf("mushaf_f3.ttf", "mushaf_p3.json"), cache.list().orEmpty().sorted())
        assertTrue(pinned.list().orEmpty().isEmpty(), "reading never writes to the downloads")

        // The same store (memory and files) and a new store (a new process: the files) do not ask the network again.
        assertNotNull(store().load(3))
        assertNotNull(store().load(3))
        assertEquals(listOf(3), source.layouts)
        assertEquals(listOf(3), source.fonts)
    }

    @Test
    fun theLastTenLayoutsStayInMemoryWhenTheFilesAreGone() = runTest {
        val store = store(memoryPages = 2)
        for (page in listOf(3, 4, 5)) assertNotNull(store.load(page))
        cache.listFiles()!!.forEach { it.delete() }
        // Page 3 was the least recently used: gone from memory; 4 and 5 are still there.
        assertNull(store.localPage(3))
        assertNotNull(store.localPage(4))
        assertNotNull(store.localPage(5))
    }

    @Test
    fun aPinnedCopyIsTakenBeforeTheCacheAndTheNetwork() = runTest {
        // A page that is in the downloads: its layout has a mark the cache copy does not have.
        val inDownloads = MushafPage(3, sample.lines.take(2))
        File(pinned, "mushaf_p3.json").writeText(MushafLayoutFormat.encode(inDownloads))
        File(pinned, "mushaf_f3.ttf").writeBytes(fontBytes(7_000))
        File(cache, "mushaf_p3.json").writeText(MushafLayoutFormat.encode(sample))
        File(cache, "mushaf_f3.ttf").writeBytes(fontBytes(5_000))

        val loaded = assertNotNull(store().load(3))
        assertEquals(inDownloads, loaded.page)
        assertEquals(File(pinned, "mushaf_f3.ttf"), loaded.font)
        assertTrue(source.layouts.isEmpty() && source.fonts.isEmpty())
    }

    @Test
    fun theCacheIsTakenBeforeTheNetwork() = runTest {
        File(cache, "mushaf_p3.json").writeText(MushafLayoutFormat.encode(sample))
        File(cache, "mushaf_f3.ttf").writeBytes(fontBytes())
        assertNotNull(store().load(3))
        assertTrue(source.layouts.isEmpty() && source.fonts.isEmpty())
    }

    @Test
    fun onlyTheMissingHalfIsFetched() = runTest {
        File(cache, "mushaf_p3.json").writeText(MushafLayoutFormat.encode(sample))
        assertNotNull(store().load(3))
        assertTrue(source.layouts.isEmpty(), "the layout was in the cache")
        assertEquals(listOf(3), source.fonts)
    }

    @Test
    fun aDamagedCopyIsDeletedAndFetchedAgain() = runTest {
        File(cache, "mushaf_p3.json").writeText("{\"v\":2,\"p\":3,\"l\":[[\"w\"")
        File(cache, "mushaf_f3.ttf").writeText("<html>Too many requests</html>".repeat(300))
        val loaded = assertNotNull(store().load(3))
        assertEquals(pageOf(3), loaded.page)
        assertEquals(listOf(3), source.layouts)
        assertEquals(listOf(3), source.fonts)
        assertEquals(6_000L, loaded.font.length())
    }

    // -- The network fails ------------------------------------------------------------------------------------------

    @Test
    fun withoutTheNetworkAndWithoutACopyThereIsNoPageAndNoRequestIsRepeatedForThirtySeconds() = runTest {
        source.offline = true
        val store = store()
        assertNull(store.load(3))
        assertEquals(listOf(3), source.layouts)
        assertEquals(listOf(3), source.fonts)

        // Turning pages back and forth does not hammer the server.
        now += 29_999
        assertNull(store.load(3))
        assertEquals(1, source.layouts.size)
        assertEquals(1, source.fonts.size)
        // Another page is its own request.
        assertNull(store.load(4))
        assertEquals(listOf(3, 4), source.layouts)

        // After 30 seconds it is tried again, and works when the network is back.
        now += 1
        source.offline = false
        assertNotNull(store.load(3))
        assertEquals(listOf(3, 4, 3), source.layouts)
    }

    @Test
    fun retryOfThePlaceholderAsksAgainAtOnce() = runTest {
        source.offline = true
        val store = store()
        assertNull(store.load(3))
        source.offline = false
        assertNull(store.load(3), "still held back")
        assertNotNull(store.load(3, force = true))
    }

    @Test
    fun aFontThatIsAnErrorPageIsNotKept() = runTest {
        source.fontOverride = "<html><body>429 Too Many Requests</body></html>".toByteArray().let { it + ByteArray(6_000) { 32 } }
        assertNull(store().load(3))
        assertNull(File(cache, "mushaf_f3.ttf").takeIf { it.exists() }, "an error page must not become the font")
        assertTrue(cache.list().orEmpty().none { it.endsWith(".part") }, "no half-written file is left")
    }

    @Test
    fun aFontThatIsTooSmallToHoldGlyphsIsNotKept() = runTest {
        source.fontOverride = fontBytes(100)
        assertNull(store().font(3))
    }

    // -- The pinned copies (the downloads) -----------------------------------------------------------------------------

    @Test
    fun pinningFetchesBothFilesIntoTheDownloadsAndTheStoreFindsThemThere() = runTest {
        val store = store()
        assertFalse(store.isPinned(3))
        assertTrue(store.pin(3))
        assertTrue(store.isPinned(3))
        assertTrue(store.has(3))
        assertEquals(listOf("mushaf_f3.ttf", "mushaf_p3.json"), pinned.list().orEmpty().sorted())
        assertTrue(cache.list().orEmpty().isEmpty(), "a pinned page does not take room in the cache")
        val snapshot = store.pinnedSnapshot()
        assertEquals(setOf(3), snapshot.keys)
        assertEquals(File(pinned, "mushaf_p3.json").length() + File(pinned, "mushaf_f3.ttf").length(), snapshot.getValue(3))

        // The network is gone: the page still opens, from the downloads.
        source.offline = true
        val loaded = assertNotNull(store().load(3))
        assertEquals(File(pinned, "mushaf_f3.ttf"), loaded.font)
    }

    @Test
    fun aPageThatIsInTheCacheIsCopiedToTheDownloadsWithoutAnyRequest() = runTest {
        val store = store()
        assertNotNull(store.load(3))
        source.layouts.clear()
        source.fonts.clear()
        assertTrue(store.pin(3))
        assertTrue(source.layouts.isEmpty() && source.fonts.isEmpty())
        assertTrue(store.isPinned(3))
    }

    @Test
    fun pinningAPageThatIsPinnedAlreadyAsksNothing() = runTest {
        val store = store()
        assertTrue(store.pin(3))
        source.layouts.clear()
        source.fonts.clear()
        assertTrue(store.pin(3))
        assertTrue(source.layouts.isEmpty() && source.fonts.isEmpty())
    }

    @Test
    fun aPinThatFailsLeavesNoHalfAPageAndIsNotHeldBack() = runTest {
        source.offline = true
        val store = store()
        assertFalse(store.pin(3))
        assertFalse(store.isPinned(3))
        assertTrue(store.pinnedSnapshot().isEmpty())
        // A download has its own backoff: the next pin asks again at once.
        source.offline = false
        assertTrue(store.pin(3))
        assertEquals(2, source.layouts.size)
    }

    @Test
    fun theSnapshotHasOnlyWholePages() = runTest {
        val store = store()
        assertTrue(store.pin(3))
        // A page with only its layout, one with only a font, one with a tiny error file.
        File(pinned, "mushaf_p4.json").writeText(MushafLayoutFormat.encode(pageOf(4)))
        File(pinned, "mushaf_f5.ttf").writeBytes(fontBytes())
        File(pinned, "mushaf_p6.json").writeText("x")
        File(pinned, "mushaf_f6.ttf").writeBytes(fontBytes())
        File(pinned, "ayah_1_1.mp3").writeBytes(ByteArray(1_000))
        assertEquals(setOf(3), store.pinnedSnapshot().keys)
    }

    @Test
    fun deletingPagesFreesTheirBytesAndTellsTheScreens() = runTest {
        val store = store()
        assertTrue(store.pin(3))
        assertTrue(store.pin(4))
        val before = store.revision.value
        val bytes = store.pinnedSnapshot().getValue(3)
        assertEquals(bytes, store.deletePinned(listOf(3)))
        assertTrue(store.revision.value > before)
        assertEquals(setOf(4), store.pinnedSnapshot().keys)
        assertFalse(File(pinned, "mushaf_p3.json").exists() || File(pinned, "mushaf_f3.ttf").exists())
    }

    @Test
    fun deletingAllPinnedPagesAlsoTakesHalfPagesAndUnfinishedFiles() = runTest {
        val store = store()
        assertTrue(store.pin(3))
        File(pinned, "mushaf_p9.json").writeText(MushafLayoutFormat.encode(pageOf(9)))
        File(pinned, "mushaf_f10.ttf.part").writeBytes(fontBytes())
        File(pinned, "ayah_1_1.mp3").writeBytes(ByteArray(1_000))
        store.deleteAllPinned()
        assertEquals(listOf("ayah_1_1.mp3"), pinned.list().orEmpty().toList(), "the audio is not the store's to delete")
    }

    @Test
    fun theUnfinishedFilesOfAnInterruptedRunAreRemoved() {
        File(pinned, "mushaf_f10.ttf.part").writeBytes(fontBytes())
        File(pinned, "mushaf_p10.json.part").writeText("{")
        File(pinned, "mushaf_p3.json").writeText("x".repeat(400))
        store().deleteUnfinished()
        assertEquals(listOf("mushaf_p3.json"), pinned.list().orEmpty().toList())
    }

    // -- The cache ------------------------------------------------------------------------------------------------

    @Test
    fun theCacheIsLimitedAndTheLeastRecentlyUsedPageGoesFirst() = runTest {
        // A page is about 6 KB of font and 3 KB of layout: room for three of them.
        val store = store(cacheLimit = 30_000)
        for (page in 3..6) {
            now += 1_000
            assertNotNull(store.load(page))
        }
        now += 1_000
        // The oldest is gone, the newest are there.
        assertFalse(File(cache, "mushaf_f3.ttf").exists() && File(cache, "mushaf_p3.json").exists())
        assertTrue(File(cache, "mushaf_f6.ttf").exists())
        assertTrue(File(cache, "mushaf_p6.json").exists())
        assertTrue(store.cacheBytes() <= 30_000, "${store.cacheBytes()}")
    }

    @Test
    fun aPageThatIsReadAgainBecomesTheMostRecentlyUsed() = runTest {
        val store = store(cacheLimit = 30_000)
        for (page in 3..4) {
            now += 1_000
            assertNotNull(store.load(page))
        }
        now += 1_000
        // Page 3 is read again (a new store: from the files), so page 4 is now the oldest.
        assertNotNull(store().load(3))
        for (page in 5..6) {
            now += 1_000
            assertNotNull(store.load(page))
        }
        assertTrue(File(cache, "mushaf_f3.ttf").exists(), "page 3 was used again")
        assertFalse(File(cache, "mushaf_f4.ttf").exists(), "page 4 is the least recently used")
    }

    @Test
    fun clearingTheCacheLeavesThePinnedPagesAndReportsTheBytes() = runTest {
        val store = store()
        assertTrue(store.pin(3))
        assertNotNull(store.load(4))
        val cached = store.cacheBytes()
        assertTrue(cached > 0)
        assertEquals(cached, store.clearCache())
        assertEquals(0L, store.cacheBytes())
        assertTrue(store.isPinned(3))
        assertNotNull(store.localPage(3))
        // Page 4 was only in the cache: gone from memory too.
        assertNull(store.localPage(4))
    }

    @Test
    fun prefetchFetchesTheNeighboursThatAreNotThereAndIgnoresPagesThatDoNotExist() = runTest {
        val store = store()
        assertNotNull(store.load(10))
        store.prefetch(listOf(11, 9, 12, 10, 0, 605, -1))
        assertEquals(listOf(10, 11, 9, 12), source.layouts)
        assertTrue(store.isLocal(11) && store.isLocal(9) && store.isLocal(12))
    }

    @Test
    fun theUrlsAreThoseOfQuranCom() {
        val quranCom = QuranComMushafSource(scratch = File(root, "scratch"))
        assertEquals(
            "https://api.quran.com/api/v4/verses/by_page/3?words=true&word_fields=code_v1,line_number,page_number&per_page=50&fields=chapter_id",
            quranCom.layoutUrl(3),
        )
        assertEquals("https://verses.quran.com/fonts/quran/hafs/v1/ttf/p604.ttf", quranCom.fontUrl(604))
    }

    @Test
    fun theSourceOfQuranComParsesTheAnswerAndRemovesTheRawFile() = runTest {
        val scratch = File(root, "scratch")
        val requested = mutableListOf<String>()
        val quranCom = QuranComMushafSource(scratch = scratch, fetch = { url, target ->
            requested += url
            target.writeText(MushafFixtures.answer(3))
            true
        })
        val page = assertNotNull(quranCom.layout(3))
        assertEquals(3, page.number)
        assertEquals(1, requested.size)
        assertTrue(scratch.list().orEmpty().isEmpty(), "the raw answer is not kept")

        val broken = QuranComMushafSource(scratch = scratch, fetch = { _, target ->
            target.writeText("<html>")
            true
        })
        assertNull(broken.layout(3))
        val offline = QuranComMushafSource(scratch = scratch, fetch = { _, _ -> false })
        assertNull(offline.layout(3))
        assertTrue(scratch.list().orEmpty().isEmpty())
    }
}
