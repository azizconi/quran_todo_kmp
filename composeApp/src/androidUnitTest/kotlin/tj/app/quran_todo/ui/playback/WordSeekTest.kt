package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.ReaderAudioOutput
import tj.app.quran_todo.common.audio.ReaderAudioSession
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Seeking inside an ayah and playing from a word: the commands behind the tap on a word of the reader (F1a). */
@OptIn(ExperimentalCoroutinesApi::class)
class WordSeekTest {
    private val practice = ReaderPracticeSettings(rangeEnabled = false, startAyah = 1, endAyah = 3)

    private class Extras : OutputExtras {
        val seeks = mutableListOf<Long>()
        val startsAt = mutableListOf<Long>()
        var position = 0L
        override fun playSample(source: String, onFinished: (failed: Boolean) -> Unit) {}
        override fun stopSample() {}
        override fun releaseFocus() {}
        override fun currentPositionMs(): Long = position
        override fun seekTo(positionMs: Long) {
            seeks += positionMs
        }
        override fun startNextAt(positionMs: Long) {
            startsAt += positionMs
        }
    }

    private class Audio : ReaderAudioOutput {
        val played = mutableListOf<String>()
        override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) {
            played += url
        }
        override fun pause() {}
        override fun resume() {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun getPositionMs() = 0L
        override fun getDurationMs() = 5_000L
        override fun stop() {}
    }

    private class Setup(val controller: PlaybackController, val extras: Extras, val audio: Audio)

    private fun TestScope.setup(): Setup {
        val audio = Audio()
        val extras = Extras()
        val controller = PlaybackController(
            ReaderAudioSession(
                backgroundScope, audio,
                source = { ayah, _ -> delay(0); ayah.numberInSurah.toString() },
                nowMs = { testScheduler.currentTime },
                persistPosition = { _, _ -> },
            ),
            timerScope = backgroundScope,
            extras = extras,
        )
        return Setup(controller, extras, audio)
    }

    private fun surah() = SurahWithAyahs(
        surah = SurahEntity(1, "name", "englishName", "translation", "Meccan", "edition"),
        ayahs = (1..3).map { inSurah ->
            AyahEntity(
                number = inSurah, numberInSurah = inSurah,
                juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
                text = "text $inSurah", surahNumber = 1,
            )
        },
    )

    @Test
    fun theClockReadsThePlayerAtThisMoment() = runTest {
        val s = setup()
        s.extras.position = 1_234
        assertEquals(1_234L, s.controller.positionMs())
        s.extras.position = 1_300
        assertEquals(1_300L, s.controller.positionMs())
    }

    @Test
    fun withoutAnOutputThereIsNoPositionAndSeekingDoesNothing() = runTest {
        val audio = Audio()
        val controller = PlaybackController(
            ReaderAudioSession(backgroundScope, audio, { a, _ -> a.numberInSurah.toString() }, { 0 }, { _, _ -> }),
        )
        controller.start(surah(), SurahReaderPreferences(), practice, 0)
        runCurrent()
        assertEquals(0L, controller.positionMs())
        controller.seekInAyah(1_000)
        controller.playAyahFrom(1, 1_000)
    }

    @Test
    fun theClockTicksAboutSixteenTimesASecond() = runTest {
        val s = setup()
        s.extras.position = 100
        val ticks = mutableListOf<Long>()
        val job = launch {
            s.controller.positions().take(4).toList(ticks)
        }
        runCurrent()
        assertEquals(1, ticks.size)
        s.extras.position = 160
        advanceTimeBy(WordClockIntervalMs)
        runCurrent()
        s.extras.position = 220
        advanceTimeBy(WordClockIntervalMs)
        runCurrent()
        s.extras.position = 280
        advanceTimeBy(WordClockIntervalMs)
        runCurrent()
        job.join()
        assertEquals(listOf(100L, 160L, 220L, 280L), ticks)
        assertTrue(WordClockIntervalMs in 50..65, "15..20 times a second")
    }

    @Test
    fun seekingMovesTheAyahThatSounds() = runTest {
        val s = setup()
        s.controller.start(surah(), SurahReaderPreferences(), practice, 0)
        runCurrent()
        assertFalse(s.controller.state.value.isPreparing)
        s.controller.seekInAyah(2_500)
        assertEquals(listOf(2_500L), s.extras.seeks)
        assertEquals(emptyList(), s.extras.startsAt)
    }

    @Test
    fun seekingAnAyahThatIsStillBeingPreparedWaitsForItsStart() = runTest {
        val s = setup()
        s.controller.start(surah(), SurahReaderPreferences(), practice, 0)
        // The source of the ayah is not ready yet.
        assertTrue(s.controller.state.value.isPreparing)
        s.controller.seekInAyah(1_700)
        assertEquals(emptyList(), s.extras.seeks)
        assertEquals(listOf(1_700L), s.extras.startsAt)
    }

    @Test
    fun nothingIsSoughtWhenNothingIsLoaded() = runTest {
        val s = setup()
        s.controller.seekInAyah(1_000)
        s.controller.playAyahFrom(1, 1_000)
        runCurrent()
        assertEquals(emptyList(), s.extras.seeks)
        assertEquals(emptyList(), s.extras.startsAt)
        assertEquals(emptyList(), s.audio.played)
    }

    @Test
    fun playingFromAWordSelectsTheAyahStartsItAndAsksForTheStart() = runTest {
        val s = setup()
        s.controller.start(surah(), SurahReaderPreferences(), practice, fromIndex = 0, autoplay = false)
        s.controller.playAyahFrom(index = 2, positionMs = 3_100)
        runCurrent()
        assertEquals(2, s.controller.state.value.index)
        assertTrue(s.controller.state.value.isPlaying)
        assertEquals(listOf("3"), s.audio.played)
        assertEquals(listOf(3_100L), s.extras.startsAt)
    }

    @Test
    fun playingFromAWordInTheMiddleOfAnotherAyahLeavesTheOldOneForTheNewOne() = runTest {
        val s = setup()
        s.controller.start(surah(), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        s.controller.playAyahFrom(index = 1, positionMs = 900)
        runCurrent()
        assertEquals(1, s.controller.state.value.index)
        assertEquals(listOf("1", "2"), s.audio.played)
        assertEquals(listOf(900L), s.extras.startsAt)
    }
}
