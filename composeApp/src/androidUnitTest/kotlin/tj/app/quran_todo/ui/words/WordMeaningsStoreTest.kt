package tj.app.quran_todo.ui.words

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.words.WordFixtures
import tj.app.quran_todo.common.words.WordMeaning
import tj.app.quran_todo.common.words.WordMeaningsChapter
import tj.app.quran_todo.data.translation.FailureThrottle

class WordMeaningsStoreTest {
    private val root: File = createTempDirectory("word-store").toFile()
    private val directory = File(root, "word_meanings")
    private var now = 1_000_000L

    private class FakeSource : WordMeaningsSource {
        val requests = mutableListOf<Int>()
        var offline = false

        override suspend fun fetch(surah: Int): WordMeaningsChapter? {
            requests += surah
            if (offline) return null
            return WordMeaningsChapter(surah, WordFixtures.surah1.mapValues { (_, words) -> words.map { WordMeaning(it.meaning, it.transliteration) } })
        }
    }

    private val fake = FakeSource()

    private fun store(source: FakeSource = fake, limit: Long = 12L * 1024 * 1024) = WordMeaningsStore(
        directory = directory,
        source = source,
        throttle = FailureThrottle(windowMs = 30_000, clock = { now }),
        limitBytes = limit,
        now = { now },
    )

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun theMeaningsOfASurahAreFetchedAtTheFirstTapOnlyAndKeptOnTheDisk() = runTest {
        val store = store()
        assertNull(store.local(1))
        assertEquals("(of) Allah", assertNotNull(store.load(1)).words.getValue(1)[1].text)
        assertNotNull(store.load(1))
        assertEquals(listOf(1), fake.requests)
        assertTrue(File(directory, "words_1.json").isFile)
        // After a restart the file is read, no request.
        val restarted = store()
        assertNotNull(restarted.local(1))
        assertEquals(listOf(1), fake.requests)
    }

    @Test
    fun withoutNetworkTheAnswerIsNullAndRetryAsksAgain() = runTest {
        fake.offline = true
        val store = store()
        assertNull(store.load(3))
        assertNull(store.load(3))
        assertEquals(listOf(3), fake.requests)
        assertNull(store.load(3, force = true))
        assertEquals(listOf(3, 3), fake.requests)
        fake.offline = false
        assertNotNull(store.load(3, force = true))
        assertEquals(listOf(3, 3, 3), fake.requests)
    }

    @Test
    fun aCopyOfAnotherSurahOrAVersionIsNotTrusted() = runTest {
        store().load(1)
        File(directory, "words_1.json").copyTo(File(directory, "words_2.json"))
        assertNull(store().local(2))
        File(directory, "words_1.json").writeText("{\"v\":7}")
        assertNull(store().local(1))
        assertTrue(!File(directory, "words_1.json").exists())
    }

    @Test
    fun theFolderStaysUnderItsLimit() = runTest {
        val store = store(limit = 1_500)
        for (surah in 1..6) {
            store.load(surah)
            now += 1_000
        }
        assertTrue(store.bytes() <= 1_500, "${store.bytes()}")
        assertTrue(File(directory, "words_6.json").isFile)
        assertTrue(!File(directory, "words_1.json").exists())
    }
}
