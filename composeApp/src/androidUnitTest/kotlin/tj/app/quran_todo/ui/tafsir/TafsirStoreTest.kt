package tj.app.quran_todo.ui.tafsir

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
import tj.app.quran_todo.common.tafsir.TafsirChapter
import tj.app.quran_todo.common.tafsir.TafsirPassage
import tj.app.quran_todo.common.tafsir.TafsirSource
import tj.app.quran_todo.data.translation.FailureThrottle

class TafsirStoreTest {
    private val root: File = createTempDirectory("tafsir-store").toFile()
    private val directory = File(root, "tafsir_cache")
    private var now = 1_000_000L

    private class FakeSource(val make: (TafsirSource, Int) -> TafsirChapter) : TafsirChapterSource {
        val requests = mutableListOf<Pair<TafsirSource, Int>>()
        var offline = false

        override suspend fun fetch(source: TafsirSource, surah: Int): TafsirChapter? {
            requests += source to surah
            return if (offline) null else make(source, surah)
        }
    }

    private fun chapterOf(source: TafsirSource, surah: Int, size: Int = 100) =
        TafsirChapter(surah, source, 3, mapOf(1 to "text of $surah ".repeat(size), 3 to "third"))

    private val fake = FakeSource(::chapterOf)

    private fun store(limit: Long = 30L * 1024 * 1024, source: FakeSource = fake) = TafsirStore(
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
    fun aSurahIsFetchedOnceThenReadFromMemoryAndFromTheDisk() = runTest {
        val first = store()
        assertNull(first.local(TafsirSource.Saadi, 2))
        val loaded = assertNotNull(first.load(TafsirSource.Saadi, 2))
        assertEquals("third", loaded.passage(3)!!.text)
        assertEquals(1, fake.requests.size)
        // Memory.
        assertNotNull(first.load(TafsirSource.Saadi, 2))
        assertEquals(1, fake.requests.size)
        // A new store (the process was restarted) reads the file, no request.
        val second = store()
        assertNotNull(second.local(TafsirSource.Saadi, 2))
        assertNotNull(second.load(TafsirSource.Saadi, 2))
        assertEquals(1, fake.requests.size)
        assertTrue(File(directory, "tafsir-saadi_ru-2.json").isFile)
    }

    @Test
    fun everySourceAndSurahHasItsOwnFile() = runTest {
        val store = store()
        store.load(TafsirSource.Saadi, 2)
        store.load(TafsirSource.IbnKathir, 2)
        store.load(TafsirSource.Saadi, 3)
        assertEquals(3, fake.requests.size)
        assertEquals(
            setOf("tafsir-saadi_ru-2.json", "tafsir-ibn_kathir_en-2.json", "tafsir-saadi_ru-3.json"),
            directory.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name }.toSet(),
        )
    }

    @Test
    fun withoutNetworkAndWithoutACopyTheAnswerIsNullAndAFailureIsNotRepeatedForThirtySeconds() = runTest {
        fake.offline = true
        val store = store()
        assertNull(store.load(TafsirSource.Saadi, 2))
        assertNull(store.load(TafsirSource.Saadi, 2))
        assertEquals(1, fake.requests.size)
        // "Retry" goes past the memory of the failure.
        assertNull(store.load(TafsirSource.Saadi, 2, force = true))
        assertEquals(2, fake.requests.size)
        // After the window it asks again by itself, and a working network gives the text.
        now += 31_000
        fake.offline = false
        assertNotNull(store.load(TafsirSource.Saadi, 2))
        assertEquals(3, fake.requests.size)
    }

    @Test
    fun aCopyOnTheDiskWorksWithoutNetwork() = runTest {
        store().load(TafsirSource.IbnKathir, 2)
        val offline = FakeSource(::chapterOf).also { it.offline = true }
        val restarted = store(source = offline)
        assertNotNull(restarted.load(TafsirSource.IbnKathir, 2))
        assertTrue(offline.requests.isEmpty())
    }

    @Test
    fun aDamagedFileIsDeletedAndFetchedAgain() = runTest {
        store().load(TafsirSource.Saadi, 2)
        File(directory, "tafsir-saadi_ru-2.json").writeText("{ not a copy")
        val restarted = store()
        assertNull(restarted.local(TafsirSource.Saadi, 2))
        assertFalse(File(directory, "tafsir-saadi_ru-2.json").exists())
        assertNotNull(restarted.load(TafsirSource.Saadi, 2))
        assertEquals(2, fake.requests.size)
    }

    @Test
    fun aCopyOfAnotherSurahUnderTheNameIsNotTrusted() = runTest {
        store().load(TafsirSource.Saadi, 2)
        File(directory, "tafsir-saadi_ru-2.json").copyTo(File(directory, "tafsir-saadi_ru-5.json"))
        assertNull(store().local(TafsirSource.Saadi, 5))
    }

    @Test
    fun theLeastRecentlyUsedSurahGoesWhenTheFolderIsOverTheLimit() = runTest {
        val store = store(limit = 3_000)
        // Each copy is about 1.2 KB of text.
        for (surah in 2..5) {
            store.load(TafsirSource.Saadi, surah)
            now += 1_000
        }
        val names = directory.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name }.toSet()
        assertTrue("tafsir-saadi_ru-5.json" in names, "the file just written stays: $names")
        assertFalse("tafsir-saadi_ru-2.json" in names, "the oldest goes first: $names")
        assertTrue(store.bytes() <= 3_000)
    }

    @Test
    fun onlyTheLastThreeSurahsStayInMemory() = runTest {
        val store = store()
        for (surah in 2..5) store.load(TafsirSource.Saadi, surah)
        // Memory holds 3 and the disk the rest: without the files, only 3 are found.
        directory.listFiles()!!.forEach { it.delete() }
        val found = (2..5).count { store.local(TafsirSource.Saadi, it) != null }
        assertEquals(3, found)
        assertNull(store.local(TafsirSource.Saadi, 2))
    }

    @Test
    fun theSourceOfQuranComAsksPageAfterPageUntilThereIsNoNext() = runTest {
        val urls = mutableListOf<String>()
        val pages = mapOf(
            1 to """{"tafsirs":[{"verse_key":"112:1","text":"a"},{"verse_key":"112:2","text":"b"}],"pagination":{"next_page":2,"total_records":4}}""",
            2 to """{"tafsirs":[{"verse_key":"112:3","text":""},{"verse_key":"112:4","text":""}],"pagination":{"next_page":null,"total_records":4}}""",
        )
        val source = QuranComTafsirSource(
            scratch = File(root, "scratch"),
            fetch = { url, target ->
                urls += url
                target.writeText(pages.getValue(url.substringAfterLast("page=").toInt()))
                true
            },
            apiBase = "https://example.test/api/v4/",
        )
        val chapter = assertNotNull(source.fetch(TafsirSource.Saadi, 112))
        assertEquals(
            listOf(
                "https://example.test/api/v4/tafsirs/170/by_chapter/112?per_page=50&page=1",
                "https://example.test/api/v4/tafsirs/170/by_chapter/112?per_page=50&page=2",
            ),
            urls,
        )
        assertEquals(4, chapter.ayahCount)
        // 112:3 and 112:4 are empty: they are part of the group of 112:2.
        assertEquals(TafsirPassage("b", 2, 4), chapter.passage(3))
        // A failed page, and an answer that is not understood, give nothing and leave no scratch file behind.
        val broken = QuranComTafsirSource(File(root, "scratch"), fetch = { _, _ -> false }, apiBase = "https://example.test/")
        assertNull(broken.fetch(TafsirSource.Saadi, 112))
        val garbage = QuranComTafsirSource(File(root, "scratch"), fetch = { _, target -> target.writeText("<html>"); true })
        assertNull(garbage.fetch(TafsirSource.Saadi, 112))
        assertEquals(0, File(root, "scratch").listFiles()!!.size)
    }

    @Test
    fun theSourceOfQuranEncAsksOneUrlPerSurahAndTakesTheVersionFromTheList() = runTest {
        val urls = mutableListOf<String>()
        val answer = """{"result":[{"sura":"112","aya":"1","translation":"a"},{"sura":"112","aya":"2","translation":"b"},{"sura":"112","aya":"3","translation":"c"},{"sura":"112","aya":"4","translation":"d"}]}"""
        val list = """{"translations":[{"key":"uzbek_mokhtasar","version":"1.2.0"}]}"""
        val source = QuranEncTafsirSource(
            scratch = File(root, "scratch"),
            fetch = { url, target ->
                urls += url
                target.writeText(if (url.endsWith("translations/list")) list else answer)
                true
            },
            apiBase = "https://example.test/api/v1/",
        )
        val chapter = assertNotNull(source.fetch(TafsirSource.MokhtasarUz, 112))
        assertEquals(
            listOf("https://example.test/api/v1/translation/sura/uzbek_mokhtasar/112", "https://example.test/api/v1/translations/list"),
            urls,
        )
        assertEquals("1.2.0", chapter.version)
        assertEquals("c", chapter.passage(3)!!.text)
        // The list without the key (the Mukhtasar is not in it today) or not reachable: the text still comes, without a version.
        val noList = QuranEncTafsirSource(
            File(root, "scratch"),
            fetch = { url, target -> if (url.endsWith("list")) false else { target.writeText(answer); true } },
        )
        assertNull(noList.fetch(TafsirSource.MokhtasarUz, 112)!!.version)
        // The text is the answer's, a failed request gives nothing.
        assertNull(QuranEncTafsirSource(File(root, "scratch"), fetch = { _, _ -> false }).fetch(TafsirSource.MokhtasarUz, 112))
        assertEquals(0, File(root, "scratch").listFiles()!!.size)
    }

    @Test
    fun eachSourceGoesToItsProvider() = runTest {
        val seen = mutableListOf<String>()
        fun fake(name: String) = object : TafsirChapterSource {
            override suspend fun fetch(source: TafsirSource, surah: Int): TafsirChapter? {
                seen += "$name:${source.key}"
                return null
            }
        }
        val source = ProviderTafsirSource(fake("com"), fake("enc"))
        source.fetch(TafsirSource.Saadi, 1)
        source.fetch(TafsirSource.MokhtasarTr, 1)
        source.fetch(TafsirSource.Muyassar, 1)
        assertEquals(listOf("com:saadi_ru", "enc:mokhtasar_tr", "com:muyassar_ar"), seen)
    }

    @Test
    fun theVersionOfACachedEditionSurvivesARestart() = runTest {
        val versioned = FakeSource { source, surah -> TafsirChapter(surah, source, 3, mapOf(1 to "x"), version = "3.4.5") }
        store(source = versioned).load(TafsirSource.MokhtasarTr, 2)
        assertEquals("3.4.5", store(source = versioned).local(TafsirSource.MokhtasarTr, 2)!!.version)
        assertTrue(File(directory, "tafsir-mokhtasar_tr-2.json").isFile)
    }
}
