package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.Reciters
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ReciterPreviewTest {
    /** Stands in for the playback controller's player: remembers what it was asked to play. */
    private class FakePlayer {
        val played = mutableListOf<String>()
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        var stops = 0

        fun play(source: String, onFinished: (Boolean) -> Unit) {
            played += source
            callbacks += onFinished
        }

        fun stop() {
            stops++
        }

        /** The [index]-th sample ran to its end (or failed). */
        fun finish(index: Int = callbacks.lastIndex, failed: Boolean = false) {
            callbacks[index](failed)
        }
    }

    private class Files(var path: String?) : AyahFiles {
        val keys = mutableListOf<String>()
        override suspend fun getOrFetch(url: String, cacheKey: String): String? {
            keys += cacheKey
            return path
        }
        override suspend fun prefetch(url: String, cacheKey: String) = true
    }

    private fun TestScope.preview(player: FakePlayer, files: Files) =
        ReciterPreview(player::play, player::stop, files, backgroundScope)

    @Test
    fun theSampleIsAyahOneOfSurahAlIkhlasFromTheCache() = runTest {
        val player = FakePlayer()
        val files = Files("/cache/sample.mp3")
        val preview = preview(player, files)
        preview.toggle(Reciters.Husary)
        assertEquals(PreviewState.Loading("husary"), preview.state.value)
        runCurrent()
        assertEquals(PreviewState.Playing("husary"), preview.state.value)
        assertEquals(listOf("/cache/sample.mp3"), player.played)
        assertEquals(listOf("husary_112_1"), files.keys)
    }

    @Test
    fun theDefaultReciterSampleUsesTheLegacyCacheKey() = runTest {
        val files = Files("/cache/sample.mp3")
        preview(FakePlayer(), files).toggle(Reciters.Alafasy)
        runCurrent()
        assertEquals(listOf("ayah_112_1"), files.keys)
    }

    @Test
    fun aSampleThatCannotBeDownloadedIsStreamedFromItsUrl() = runTest {
        val player = FakePlayer()
        val preview = preview(player, Files(null))
        preview.toggle(Reciters.AbdulBasit)
        runCurrent()
        assertEquals(
            listOf("https://cdn.islamic.network/quran/audio/192/ar.abdulbasitmurattal/6222.mp3"),
            player.played,
        )
    }

    @Test
    fun tappingTheSameSampleAgainStopsItAndTappingAnotherSwitches() = runTest {
        val player = FakePlayer()
        val preview = preview(player, Files("/cache/s.mp3"))
        preview.toggle(Reciters.Husary)
        runCurrent()
        preview.toggle(Reciters.Husary)
        assertEquals(PreviewState.Idle, preview.state.value)
        assertEquals(1, player.stops)
        preview.toggle(Reciters.Husary)
        runCurrent()
        preview.toggle(Reciters.Minshawi)
        runCurrent()
        assertEquals(PreviewState.Playing("minshawi"), preview.state.value)
        assertEquals(3, player.played.size)
    }

    @Test
    fun aSampleThatEndsReturnsToIdleWithoutAFailure() = runTest {
        val player = FakePlayer()
        val preview = preview(player, Files("/cache/s.mp3"))
        var failures = 0
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { preview.failures.collect { failures++ } }
        preview.toggle(Reciters.Husary)
        runCurrent()
        player.finish()
        runCurrent()
        assertEquals(PreviewState.Idle, preview.state.value)
        assertEquals(0, failures)
    }

    @Test
    fun aSampleThatFailsReturnsToIdleAndReportsIt() = runTest {
        val player = FakePlayer()
        val preview = preview(player, Files("/cache/s.mp3"))
        var failures = 0
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { preview.failures.collect { failures++ } }
        preview.toggle(Reciters.Husary)
        runCurrent()
        player.finish(failed = true)
        runCurrent()
        assertEquals(PreviewState.Idle, preview.state.value)
        assertEquals(1, failures)
    }

    @Test
    fun theEndOfAnOldSampleDoesNotTouchTheNewOne() = runTest {
        val player = FakePlayer()
        val preview = preview(player, Files("/cache/s.mp3"))
        preview.toggle(Reciters.Husary)
        runCurrent()
        preview.toggle(Reciters.Minshawi)
        runCurrent()
        // The first sample reports its end late, after the second one has started.
        player.finish(index = 0)
        runCurrent()
        assertEquals(PreviewState.Playing("minshawi"), preview.state.value)
    }

    @Test
    fun stoppingWhileFetchingNeverStartsTheSample() = runTest {
        val player = FakePlayer()
        val preview = preview(player, Files("/cache/s.mp3"))
        preview.toggle(Reciters.Husary)
        preview.stop()
        runCurrent()
        assertEquals(PreviewState.Idle, preview.state.value)
        assertEquals(emptyList(), player.played)
    }
}
