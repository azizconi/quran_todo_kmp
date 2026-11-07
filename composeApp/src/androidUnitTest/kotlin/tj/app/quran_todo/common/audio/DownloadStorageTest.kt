package tj.app.quran_todo.common.audio

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadStorageTest {
    private lateinit var dir: File
    private lateinit var storage: DownloadStorage

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("download-storage-test").toFile()
        storage = DownloadStorage(File(dir, "downloads"))
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun temporary(key: String, size: Int) = storage.temporaryFileFor(key).also { it.writeBytes(ByteArray(size)) }

    @Test
    fun theFilesAreNamedLikeTheFilesOfTheCache() {
        assertEquals("ayah_78_1.mp3", storage.fileFor("ayah_78_1").name)
        assertEquals("husary_78_1.mp3.download", storage.temporaryFileFor("husary_78_1").name)
        assertEquals(
            Reciters.Husary.cacheKey(78, 1) + ".mp3",
            storage.fileFor(Reciters.Husary.cacheKey(78, 1)).name,
        )
    }

    @Test
    fun aFileThatIsCommittedAppearsAsAWholeFileAndTheTemporaryOneIsGone() {
        val tmp = temporary("ayah_1_1", 2_000)
        assertFalse(storage.contains("ayah_1_1"), "a half-written file is not a download")
        assertTrue(storage.commit("ayah_1_1", tmp))
        assertTrue(storage.contains("ayah_1_1"))
        assertFalse(tmp.exists())
        assertEquals(mapOf("ayah_1_1" to 2_000L), storage.snapshot())
        assertNotNull(storage.existing("ayah_1_1"))
    }

    @Test
    fun aFileThatIsTooSmallIsNotCommitted() {
        val tmp = temporary("ayah_1_1", 100)
        assertFalse(storage.commit("ayah_1_1", tmp))
        assertFalse(tmp.exists())
        assertNull(storage.existing("ayah_1_1"))
        assertTrue(storage.snapshot().isEmpty())
    }

    @Test
    fun theSnapshotIgnoresUnfinishedFilesAndSumsTheRest() {
        storage.commit("ayah_1_1", temporary("ayah_1_1", 1_000))
        storage.commit("ayah_1_2", temporary("ayah_1_2", 2_000))
        temporary("ayah_1_3", 5_000)
        assertEquals(setOf("ayah_1_1", "ayah_1_2"), storage.snapshot().keys)
        assertEquals(3_000L, storage.totalBytes())
    }

    @Test
    fun theRevisionChangesWithEveryAddAndDelete() {
        val start = storage.revision.value
        storage.commit("ayah_1_1", temporary("ayah_1_1", 1_000))
        val afterAdd = storage.revision.value
        assertTrue(afterAdd > start)
        storage.delete(listOf("ayah_1_1"))
        assertTrue(storage.revision.value > afterAdd)
    }

    @Test
    fun deletingKeysFreesTheirBytesAndLeavesTheOthers() {
        storage.commit("ayah_1_1", temporary("ayah_1_1", 1_000))
        storage.commit("ayah_1_2", temporary("ayah_1_2", 2_000))
        assertEquals(1_000L, storage.delete(listOf("ayah_1_1", "ayah_9_9")))
        assertEquals(setOf("ayah_1_2"), storage.snapshot().keys)
    }

    @Test
    fun deletingEverythingAlsoRemovesTheUnfinishedFiles() {
        storage.commit("ayah_1_1", temporary("ayah_1_1", 1_000))
        temporary("ayah_1_2", 700)
        assertEquals(1_700L, storage.deleteAll())
        assertEquals(emptyList(), storage.directory.list()!!.toList())
    }

    @Test
    fun theLeftoversOfInterruptedDownloadsCanBeRemovedWithoutTouchingWholeFiles() {
        storage.commit("ayah_1_1", temporary("ayah_1_1", 1_000))
        temporary("ayah_1_2", 700)
        storage.deleteUnfinished()
        assertEquals(listOf("ayah_1_1.mp3"), storage.directory.list()!!.toList())
    }
}
