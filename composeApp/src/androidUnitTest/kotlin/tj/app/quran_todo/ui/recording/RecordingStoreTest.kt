package tj.app.quran_todo.ui.recording

import java.io.File
import java.nio.file.Files
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage

/** The files of "My reading" (task F2a): where they are, the 20 of a passage, the sizes and the delete-all of the storage screen. */
class RecordingStoreTest {
    private lateinit var root: File
    private var clock = 1_000L
    private val key = PassageKey(78, 1, 5)
    private val other = PassageKey(112, 1, 4)

    private fun store(keep: Int = RecordingStore.KEEP_PER_PASSAGE) = RecordingStore(root, now = { clock }, keepPerPassage = keep)

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("recordings-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** A recording of [bytes] bytes written the way the recorder does it: a `.part` file, then [RecordingStore.finish]. */
    private fun RecordingStore.record(at: PassageKey = key, bytes: Int = 4_096): Recording? {
        clock += 10
        val part = newPart(at)
        part.writeBytes(ByteArray(bytes) { 1 })
        return finish(part, at)
    }

    @Test
    fun aRecordingLivesInTheFolderOfItsPassageNamedByItsStartTime() {
        val store = store()
        val recording = assertNotNull(store.record())
        assertEquals(File(root, "78_1_5"), recording.file.parentFile)
        assertEquals("${recording.epochMillis}.m4a", recording.file.name)
        assertEquals(1_010L, recording.epochMillis)
        assertEquals(4_096L, recording.sizeBytes)
        assertEquals("78_1_5", key.folder)
    }

    @Test
    fun theListIsNewestFirstAndHoldsOnlyThePassageAsked() {
        val store = store()
        val a = assertNotNull(store.record())
        val b = assertNotNull(store.record())
        store.record(other)
        assertEquals(listOf(b.file, a.file), store.list(key).map { it.file })
        assertEquals(1, store.list(other).size)
        assertEquals(2, store.count(key))
        assertEquals(0, store.count(PassageKey(1, 1, 7)))
    }

    @Test
    fun onlyTheLastTwentyOfAPassageStay() {
        val store = store()
        val made = (1..23).map { assertNotNull(store.record()) }
        val kept = store.list(key)
        assertEquals(20, kept.size)
        assertEquals(made.takeLast(20).map { it.file }.reversed(), kept.map { it.file }, "the three oldest went")
        assertFalse(made.first().file.exists())
        assertTrue(made.last().file.exists())
        assertEquals(20, RecordingStore.KEEP_PER_PASSAGE)
    }

    @Test
    fun theLimitIsPerPassage() {
        val store = store(keep = 2)
        repeat(3) { store.record() }
        repeat(3) { store.record(other) }
        assertEquals(2, store.count(key))
        assertEquals(2, store.count(other))
    }

    @Test
    fun aFileThatIsStillBeingWrittenIsNotListedAndALeftoverGoes() {
        val store = store()
        store.record()
        val part = store.newPart(key)
        part.writeBytes(ByteArray(2_048))
        // The recording that runs is not a recording yet: it is not in the list, and no listing takes it for a leftover.
        assertEquals(1, store.list(key).size)
        assertEquals(1, store.count(key))
        assertTrue(part.exists())
        // After a killed process (a new store over the same folder) nothing is active: the leftover is removed.
        assertEquals(1, store().list(key).size)
        assertFalse(part.exists())
    }

    @Test
    fun aGivenUpRecordingIsRemovedAndItsLeftoverIsNotKept() {
        val store = store()
        val part = store.newPart(key)
        part.writeBytes(ByteArray(4_096))
        store.discard(part)
        assertFalse(part.exists())
        assertEquals(0, store.count(key))
    }

    @Test
    fun aRecordingWithoutSoundIsNotKept() {
        val store = store()
        val part = store.newPart(key)
        part.writeBytes(ByteArray(10))
        assertNull(store.finish(part, key))
        assertFalse(part.exists())
        assertEquals(0, store.count(key))
        // A recording that was never written at all is not kept either.
        assertNull(store.finish(File(root, "78_1_5/missing.m4a.part"), key))
    }

    @Test
    fun twoRecordingsStartedAtTheSameMomentDoNotShareAFile() {
        val store = store()
        val first = store.newPart(key).also { it.writeBytes(ByteArray(2_048)) }
        val second = store.newPart(key)
        assertTrue(first != second)
        assertEquals(first.name.removeSuffix(".m4a.part").toLong() + 1, second.name.removeSuffix(".m4a.part").toLong())
    }

    @Test
    fun deletingTheLastRecordingRemovesTheEmptyFolder() {
        val store = store()
        val one = assertNotNull(store.record())
        val two = assertNotNull(store.record())
        store.delete(one)
        assertTrue(File(root, "78_1_5").exists())
        store.delete(two)
        assertFalse(File(root, "78_1_5").exists())
        assertEquals(0, store.count(key))
    }

    @Test
    fun theSizeCountsEveryRecordingAndDeleteAllEmptiesTheStore() {
        val store = store()
        store.record(bytes = 3_000)
        store.record(bytes = 2_000)
        store.record(other, bytes = 1_500)
        assertEquals(6_500L, store.totalBytes())
        store.deleteAll()
        assertEquals(0L, store.totalBytes())
        assertEquals(0, store.count(key))
        assertTrue(root.exists(), "the root stays for the next recording")
        assertNotNull(store.record())
    }

    @Test
    fun anEmptyStoreIsZeroBytes() {
        val fresh = RecordingStore(File(root, "never-created"))
        assertEquals(0L, fresh.totalBytes())
        assertTrue(fresh.list(key).isEmpty())
    }

    @Test
    fun filesThatAreNotOurNamesAreIgnored() {
        val store = store()
        store.record()
        File(root, "78_1_5/notes.txt").writeText("x")
        File(root, "78_1_5/abc.m4a").writeBytes(ByteArray(4_096))
        assertEquals(1, store.list(key).size)
    }

    // -- The text forms ------------------------------------------------------------------------------------

    @Test
    fun theClockShowsMinutesAndSeconds() {
        assertEquals("0:00", VoiceFormat.clock(0))
        assertEquals("0:42", VoiceFormat.clock(42_000))
        assertEquals("0:42", VoiceFormat.clock(41_600), "rounded to the nearest second")
        assertEquals("12:05", VoiceFormat.clock(725_000))
        assertEquals("10:00", VoiceFormat.clock(RecordingStoreLimit))
        assertEquals("0:00", VoiceFormat.clock(-5))
        assertEquals("0:12 / 0:42", VoiceFormat.progress(12_000, 42_000))
    }

    @Test
    fun theLoudnessBarIsNeverQuiteEmptyAndNeverOverfull() {
        assertEquals(0.04f, VoiceFormat.barShare(0f), 0.0001f)
        assertEquals(1f, VoiceFormat.barShare(1f), 0.0001f)
        assertEquals(1f, VoiceFormat.barShare(7f), 0.0001f)
        assertEquals(0.04f, VoiceFormat.barShare(-1f), 0.0001f)
    }

    @Test
    fun theMomentIsWrittenInTheLanguageOfTheApp() {
        val utc = TimeZone.getTimeZone("UTC")
        val moment = java.time.ZonedDateTime.of(2026, 10, 30, 21, 14, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        listOf(AppLanguage.RU, AppLanguage.EN, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val text = VoiceFormat.moment(moment, language, utc)
            assertTrue("21:14" in text, "$language: $text")
            assertTrue("30" in text, "$language: $text")
        }
    }

    private companion object {
        const val RecordingStoreLimit = 10 * 60 * 1000L
    }
}
