package tj.app.quran_todo.common.audio

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where playback finds an ayah: the downloads first, then the listening cache, then the network. */
class AudioFileStoreTest {
    private lateinit var root: File
    private lateinit var downloads: DownloadStorage
    private lateinit var cache: AudioCacheStorage
    private val fetches = mutableListOf<String>()
    private var networkWorks = true

    // File times are whole seconds on some file systems: tick in seconds.
    private var clock = 1_000_000L

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("audio-file-store-test").toFile()
        downloads = DownloadStorage(File(root, "downloads"))
        cache = AudioCacheStorage(File(root, "cache"), limitBytes = 10_000, now = { clock.also { clock += 2_000L } })
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun store() = AudioFileStore(downloads, cache) { url, target ->
        fetches += url
        if (networkWorks) {
            target.writeBytes(ByteArray(2_000) { 7 })
            true
        } else {
            false
        }
    }

    private fun writeDownload(key: String, size: Int = 3_000): File = downloads.fileFor(key).also { it.writeBytes(ByteArray(size) { 1 }) }

    private fun writeCache(key: String, size: Int = 2_000): File = cache.fileFor(key).also {
        it.writeBytes(ByteArray(size) { 2 })
        cache.recordAdded(it)
    }

    @Test
    fun aDownloadedAyahIsPlayedFromTheDownloadsWithoutTheNetwork() = runTest {
        val file = writeDownload("ayah_78_1")
        networkWorks = false
        assertEquals(file.absolutePath, store().getOrFetch("https://cdn/1.mp3", "ayah_78_1"))
        assertTrue(fetches.isEmpty())
    }

    @Test
    fun theDownloadsWinOverTheCacheWhenBothHaveTheAyah() = runTest {
        val download = writeDownload("ayah_78_1")
        writeCache("ayah_78_1")
        assertEquals(download.absolutePath, store().getOrFetch("https://cdn/1.mp3", "ayah_78_1"))
        assertEquals(download.absolutePath, store().localFile("ayah_78_1")?.absolutePath)
    }

    @Test
    fun aDownloadHitDoesNotTouchTheUseTimeOfTheCache() = runTest {
        val download = writeDownload("ayah_78_1")
        val before = download.lastModified()
        val cached = writeCache("ayah_78_1")
        val cachedBefore = cached.lastModified()
        store().getOrFetch("https://cdn/1.mp3", "ayah_78_1")
        assertEquals(before, download.lastModified())
        assertEquals(cachedBefore, cached.lastModified(), "the cache copy was not used, so it is not refreshed")
    }

    @Test
    fun aCachedAyahIsPlayedFromTheCacheAndCountsAsUsed() = runTest {
        val file = writeCache("ayah_78_2")
        file.setLastModified(5_000L)
        assertEquals(file.absolutePath, store().getOrFetch("https://cdn/2.mp3", "ayah_78_2"))
        assertTrue(fetches.isEmpty())
        assertTrue(file.lastModified() > 5_000L, "the hit made it the most recently used file")
    }

    @Test
    fun anAyahThatIsNowhereIsFetchedIntoTheCacheNotIntoTheDownloads() = runTest {
        val path = store().getOrFetch("https://cdn/3.mp3", "ayah_78_3")
        assertEquals(cache.fileFor("ayah_78_3").absolutePath, path)
        assertEquals(listOf("https://cdn/3.mp3"), fetches)
        assertTrue(cache.fileFor("ayah_78_3").exists())
        assertFalse(downloads.fileFor("ayah_78_3").exists())
        assertTrue(downloads.snapshot().isEmpty(), "playing never makes a download")
        // The second time it comes from the cache.
        fetches.clear()
        assertEquals(path, store().getOrFetch("https://cdn/3.mp3", "ayah_78_3"))
        assertTrue(fetches.isEmpty())
    }

    @Test
    fun withoutTheNetworkAnAyahThatIsNotOnThePhoneGivesNull() = runTest {
        networkWorks = false
        assertNull(store().getOrFetch("https://cdn/4.mp3", "ayah_78_4"))
        assertFalse(cache.temporaryFileFor("ayah_78_4").exists(), "no half file is left")
    }

    @Test
    fun aDamagedFileIsNotAnAyah() = runTest {
        downloads.fileFor("ayah_78_5").writeBytes(ByteArray(10))
        cache.fileFor("ayah_78_6").writeBytes(ByteArray(10))
        val store = store()
        assertFalse(store.isAvailable("ayah_78_5"))
        assertFalse(store.isAvailable("ayah_78_6"))
        assertEquals(cache.fileFor("ayah_78_6").absolutePath, store.getOrFetch("https://cdn/6.mp3", "ayah_78_6"))
        assertEquals(1, fetches.size)
    }

    @Test
    fun anAyahIsAvailableWhenItIsInTheDownloadsOrInTheCache() = runTest {
        writeDownload("ayah_1_1")
        writeCache("ayah_1_2")
        val store = store()
        assertTrue(store.isAvailable("ayah_1_1"))
        assertTrue(store.isAvailable("ayah_1_2"))
        assertFalse(store.isAvailable("ayah_1_3"))
    }

    @Test
    fun prefetchSkipsWhatIsOnThePhoneAndFetchesTheRest() = runTest {
        writeDownload("ayah_1_1")
        val store = store()
        assertTrue(store.prefetch("https://cdn/a.mp3", "ayah_1_1"))
        assertTrue(fetches.isEmpty())
        assertTrue(store.prefetch("https://cdn/b.mp3", "ayah_1_2"))
        assertEquals(listOf("https://cdn/b.mp3"), fetches)
        networkWorks = false
        assertFalse(store.prefetch("https://cdn/c.mp3", "ayah_1_3"))
    }

    @Test
    fun anAyahThatDoesNotPlayIsForgottenEverywhereSoTheNextTryFetchesItAgain() = runTest {
        writeDownload("ayah_1_1")
        writeCache("ayah_1_1")
        val store = store()
        store.clear("ayah_1_1")
        assertFalse(store.isAvailable("ayah_1_1"))
        assertFalse(downloads.fileFor("ayah_1_1").exists())
        assertFalse(cache.fileFor("ayah_1_1").exists())
        assertEquals(cache.fileFor("ayah_1_1").absolutePath, store.getOrFetch("https://cdn/1.mp3", "ayah_1_1"))
    }

    @Test
    fun theDownloadsOfAnotherReciterAreNotTheAyahOfThisOne() = runTest {
        writeDownload("husary_78_1")
        networkWorks = false
        val store = store()
        assertTrue(store.isAvailable("husary_78_1"))
        assertFalse(store.isAvailable("ayah_78_1"))
        assertNull(store.getOrFetch("https://cdn/x.mp3", "ayah_78_1"))
    }

    // -- The cache never evicts the downloads -----------------------------------------------------

    @Test
    fun theLruCacheOnlyEvictsItsOwnFilesAndNeverTheDownloads() = runTest {
        val downloaded = (1..6).map { writeDownload("ayah_2_$it", size = 4_000) } // 24 000 bytes, over the 10 000 limit of the cache
        networkWorks = true
        val store = store()
        // Listening to ten ayahs of 2 000 bytes fills the cache several times over.
        for (n in 1..10) store.getOrFetch("https://cdn/$n.mp3", "ayah_3_$n")

        assertTrue(downloaded.all { it.exists() && it.length() == 4_000L }, "downloads are untouched")
        assertEquals(6, downloads.snapshot().size)
        val cachedBytes = cache.totalBytes()
        assertTrue(cachedBytes <= 10_000, "the cache stays within its limit: $cachedBytes")
        assertTrue(cache.directory.list()!!.count { it.endsWith(".mp3") } < 10, "some listening files were evicted")
        // The oldest listening files went, the newest is there.
        assertTrue(cache.fileFor("ayah_3_10").exists())
        assertFalse(cache.fileFor("ayah_3_1").exists())
    }

    @Test
    fun clearingTheCacheLeavesTheDownloads() = runTest {
        writeDownload("ayah_2_1")
        writeDownload("ayah_2_2")
        writeCache("ayah_3_1")
        writeCache("ayah_3_2")
        cache.clear()
        assertEquals(0, cache.totalBytes())
        assertEquals(setOf("ayah_2_1", "ayah_2_2"), downloads.snapshot().keys)
    }

    @Test
    fun deletingTheDownloadsLeavesTheCache() = runTest {
        writeDownload("ayah_2_1")
        writeCache("ayah_3_1")
        downloads.deleteAll()
        assertTrue(downloads.snapshot().isEmpty())
        assertTrue(cache.fileFor("ayah_3_1").exists())
    }
}
