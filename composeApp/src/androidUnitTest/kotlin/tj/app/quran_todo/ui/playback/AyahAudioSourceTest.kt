package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.ReaderAudioAyah
import tj.app.quran_todo.common.audio.Reciters
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AyahAudioSourceTest {
    private val surah = (1..6).map { ReaderAudioAyah(globalNumber = 100 + it, numberInSurah = it) }

    private class FakeFiles(private val offline: Boolean = false) : AyahFiles {
        val fetched = mutableListOf<Pair<String, String>>()
        val prefetched = mutableListOf<Pair<String, String>>()

        override suspend fun getOrFetch(url: String, cacheKey: String): String? {
            fetched += url to cacheKey
            return if (offline) null else "/cache/$cacheKey.mp3"
        }

        override suspend fun prefetch(url: String, cacheKey: String): Boolean {
            prefetched += url to cacheKey
            return !offline
        }
    }

    @Test
    fun anAyahPlaysFromTheCachedFileOfTheCurrentReciter() = runTest {
        val files = FakeFiles()
        val source = AyahAudioSource(files, { Reciters.Alafasy }, { surah }, backgroundScope)
        assertEquals("/cache/ayah_78_3.mp3", source.resolve(surah[2], 78))
        assertEquals(listOf("https://cdn.islamic.network/quran/audio/128/ar.alafasy/103.mp3" to "ayah_78_3"), files.fetched)
    }

    @Test
    fun theReciterIsReadAtEveryCallSoAChangeTakesEffectImmediately() = runTest {
        val files = FakeFiles()
        var reciter = Reciters.Alafasy
        val source = AyahAudioSource(files, { reciter }, { surah }, backgroundScope)
        source.resolve(surah[0], 78)
        reciter = Reciters.Husary
        assertEquals("/cache/husary_78_1.mp3", source.resolve(surah[0], 78))
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.husary/101.mp3", files.fetched.last().first)
    }

    @Test
    fun theNextTwoAyahsAreDownloadedInTheBackground() = runTest {
        val files = FakeFiles()
        val source = AyahAudioSource(files, { Reciters.Alafasy }, { surah }, backgroundScope)
        source.resolve(surah[1], 78)
        runCurrent()
        assertEquals(listOf("ayah_78_3", "ayah_78_4"), files.prefetched.map { it.second })
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.alafasy/103.mp3", files.prefetched.first().first)
    }

    @Test
    fun nothingIsPrefetchedPastTheEndOfTheSurah() = runTest {
        val files = FakeFiles()
        val source = AyahAudioSource(files, { Reciters.Alafasy }, { surah }, backgroundScope)
        source.resolve(surah[4], 78)
        runCurrent()
        assertEquals(listOf("ayah_78_6"), files.prefetched.map { it.second })
        files.prefetched.clear()
        source.resolve(surah[5], 78)
        runCurrent()
        assertEquals(emptyList(), files.prefetched)
    }

    @Test
    fun anAyahThatCannotBeDownloadedFallsBackToItsRemoteUrlAndPrefetchesNothing() = runTest {
        val files = FakeFiles(offline = true)
        val source = AyahAudioSource(files, { Reciters.Minshawi }, { surah }, backgroundScope)
        val played = source.resolve(surah[0], 78)
        runCurrent()
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.minshawi/101.mp3", played)
        assertEquals(emptyList(), files.prefetched)
    }

    @Test
    fun prefetchUsesTheReciterOfTheAyahThatStartedIt() = runTest {
        val files = FakeFiles()
        val source = AyahAudioSource(files, { Reciters.AbdulBasit }, { surah }, backgroundScope)
        source.resolve(surah[0], 78)
        runCurrent()
        assertEquals(listOf("abdulbasit_78_2", "abdulbasit_78_3"), files.prefetched.map { it.second })
        assertEquals(
            "https://cdn.islamic.network/quran/audio/192/ar.abdulbasitmurattal/102.mp3",
            files.prefetched.first().first,
        )
    }
}
