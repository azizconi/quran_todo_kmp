package tj.app.quran_todo.common.audio

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioCacheStorageTest {
    private lateinit var dir: File
    // File times are whole seconds on some file systems: tick in seconds.
    private var clock = 1_000_000L

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("audio-cache-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun storage(limit: Long) = AudioCacheStorage(dir, limitBytes = limit, now = { clock.also { clock += 2_000L } })

    private fun write(storage: AudioCacheStorage, key: String, size: Int): File {
        val file = storage.fileFor(key)
        file.writeBytes(ByteArray(size))
        storage.recordAdded(file)
        return file
    }

    // -- The eviction rule ---------------------------------------------------------------------------

    @Test
    fun nothingIsEvictedWhileTheCacheIsWithinItsLimit() {
        val entries = listOf(CacheEntry("a", 400, 1), CacheEntry("b", 600, 2))
        assertEquals(emptyList(), selectEvictions(entries, limitBytes = 1_000))
    }

    @Test
    fun theLeastRecentlyUsedFilesGoFirstUntilTheCacheFitsAgain() {
        val entries = listOf(
            CacheEntry("new", 400, 30),
            CacheEntry("old", 400, 10),
            CacheEntry("middle", 400, 20),
        )
        assertEquals(listOf("old"), selectEvictions(entries, limitBytes = 1_000).map { it.name })
        assertEquals(listOf("old", "middle"), selectEvictions(entries, limitBytes = 900, targetBytes = 500).map { it.name })
    }

    @Test
    fun theFileThatWasJustWrittenIsNeverEvicted() {
        val entries = listOf(CacheEntry("fresh", 900, 1), CacheEntry("other", 300, 2))
        assertEquals(listOf("other"), selectEvictions(entries, limitBytes = 1_000, keep = setOf("fresh")).map { it.name })
    }

    @Test
    fun equalUseTimesAreEvictedInAStableOrder() {
        val entries = listOf(CacheEntry("b", 600, 5), CacheEntry("a", 600, 5))
        assertEquals(listOf("a"), selectEvictions(entries, limitBytes = 1_000).map { it.name })
    }

    // -- On real files --------------------------------------------------------------------------------

    @Test
    fun goingOverTheLimitDeletesTheOldestFilesDownToTheTarget() {
        val storage = storage(limit = 1_000)
        write(storage, "first", 400)
        write(storage, "second", 400)
        assertEquals(800, storage.totalBytes())
        write(storage, "third", 400)
        // 1200 > 1000: shrink to 90% = 900, the oldest ("first") goes, the new file stays.
        assertFalse(storage.fileFor("first").exists())
        assertTrue(storage.fileFor("second").exists())
        assertTrue(storage.fileFor("third").exists())
        assertEquals(800, storage.totalBytes())
    }

    @Test
    fun aCacheHitMakesTheFileTheMostRecentlyUsedOne() {
        val storage = storage(limit = 1_000)
        val first = write(storage, "first", 400)
        write(storage, "second", 400)
        storage.markUsed(first)
        write(storage, "third", 400)
        assertTrue(storage.fileFor("first").exists(), "the file that was just used must survive")
        assertFalse(storage.fileFor("second").exists())
    }

    @Test
    fun clearDeletesAllAudioAndUnfinishedDownloadsAndReportsTheFreedBytes() {
        val storage = storage(limit = 10_000)
        write(storage, "a", 300)
        write(storage, "b", 200)
        storage.temporaryFileFor("c").writeBytes(ByteArray(50))
        assertEquals(500, storage.totalBytes())
        assertEquals(550, storage.clear())
        assertEquals(0, storage.totalBytes())
        assertEquals(0, dir.listFiles()?.size)
    }

    @Test
    fun unfinishedDownloadsAreNotCountedAsAudio() {
        val storage = storage(limit = 10_000)
        write(storage, "a", 300)
        storage.temporaryFileFor("b").writeBytes(ByteArray(900))
        assertEquals(300, storage.totalBytes())
    }
}
