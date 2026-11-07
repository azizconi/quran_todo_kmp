package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.AudioFileStore
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.ReaderAudioAyah
import tj.app.quran_todo.common.audio.Reciters
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The source of the reader on top of the real lookup order: downloads, then the cache, then the network. */
@OptIn(ExperimentalCoroutinesApi::class)
class AyahAudioSourceDownloadsTest {
    private lateinit var root: File
    private lateinit var downloads: DownloadStorage
    private lateinit var cache: AudioCacheStorage
    private val fetched = mutableListOf<String>()
    private var networkWorks = true

    private val surah = (1..4).map { ReaderAudioAyah(globalNumber = 4_000 + it, numberInSurah = it) }

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("ayah-source-downloads-test").toFile()
        downloads = DownloadStorage(File(root, "downloads"))
        cache = AudioCacheStorage(File(root, "cache"))
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun files(): AyahFiles {
        val store = AudioFileStore(downloads, cache) { url, target ->
            fetched += url
            if (networkWorks) {
                target.writeBytes(ByteArray(2_000))
                true
            } else {
                false
            }
        }
        return object : AyahFiles {
            override suspend fun getOrFetch(url: String, cacheKey: String): String? = store.getOrFetch(url, cacheKey)

            override suspend fun prefetch(url: String, cacheKey: String): Boolean = store.prefetch(url, cacheKey)
        }
    }

    /** The prefetch hops to the real IO dispatcher: wait (in real time) until [count] files were asked for, or give up. */
    private suspend fun awaitFetches(count: Int) = withContext(Dispatchers.Default) {
        repeat(300) {
            if (fetched.size >= count) return@withContext
            Thread.sleep(10)
        }
    }

    private suspend fun awaitCacheFile(key: String) = withContext(Dispatchers.Default) {
        repeat(300) {
            if (cache.fileFor(key).exists()) return@withContext
            Thread.sleep(10)
        }
    }

    private fun download(key: String) {
        downloads.fileFor(key).writeBytes(ByteArray(3_000))
    }

    @Test
    fun aDownloadedSurahPlaysWithoutAnyNetworkAndPrefetchesNothing() = runTest {
        (1..4).forEach { download("ayah_78_$it") }
        networkWorks = false
        val source = AyahAudioSource(files(), { Reciters.Alafasy }, { surah }, backgroundScope)

        surah.forEach { ayah ->
            assertEquals(downloads.fileFor("ayah_78_${ayah.numberInSurah}").absolutePath, source.resolve(ayah, 78))
        }
        runCurrent()
        assertTrue(fetched.isEmpty(), "nothing was asked from the network: $fetched")
        assertTrue(cache.directory.list()!!.isEmpty(), "nothing was written to the cache")
    }

    @Test
    fun anAyahThatIsNotDownloadedStreamsFromTheRemoteUrlWhenThereIsNoNetwork() = runTest {
        download("ayah_78_1")
        networkWorks = false
        val source = AyahAudioSource(files(), { Reciters.Alafasy }, { surah }, backgroundScope)

        assertEquals(downloads.fileFor("ayah_78_1").absolutePath, source.resolve(surah[0], 78))
        // Ayah 2 is nowhere: the player gets the remote address, tries it and reports "No connection".
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.alafasy/4002.mp3", source.resolve(surah[1], 78))
    }

    @Test
    fun whenOnlySomeAyahsAreDownloadedOnlyTheMissingOnesAreFetchedIntoTheCache() = runTest {
        download("ayah_78_1")
        download("ayah_78_2")
        val source = AyahAudioSource(files(), { Reciters.Alafasy }, { surah }, backgroundScope)

        assertEquals(downloads.fileFor("ayah_78_1").absolutePath, source.resolve(surah[0], 78))
        runCurrent()
        awaitFetches(1)
        // The prefetch looks at ayahs 2 and 3: 2 is downloaded, 3 comes into the cache.
        assertEquals(listOf("https://cdn.islamic.network/quran/audio/128/ar.alafasy/4003.mp3"), fetched)
        awaitCacheFile("ayah_78_3")
        assertTrue(cache.fileFor("ayah_78_3").exists())
        assertTrue(downloads.snapshot().keys == setOf("ayah_78_1", "ayah_78_2"), "the downloads are not written by playing")
    }

    @Test
    fun theDownloadsOfOtherReciterDoNotPlayForThisOne() = runTest {
        (1..4).forEach { download("husary_78_$it") }
        networkWorks = false
        val source = AyahAudioSource(files(), { Reciters.Alafasy }, { surah }, backgroundScope)
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.alafasy/4001.mp3", source.resolve(surah[0], 78))

        val husary = AyahAudioSource(files(), { Reciters.Husary }, { surah }, backgroundScope)
        assertEquals(downloads.fileFor("husary_78_1").absolutePath, husary.resolve(surah[0], 78))
    }
}
