package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private val practice = ReaderPracticeSettings(rangeEnabled = false, startAyah = 1, endAyah = 3)

    @Test
    fun aMinutesTimerPausesPlaybackWhenItRunsOut() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        assertTrue(controller.state.value.isPlaying)

        controller.setSleepTimer(SleepTimer.Minutes(15))
        runCurrent()
        assertEquals(SleepTimer.Minutes(15), controller.sleepTimer.value)
        assertEquals(15 * 60, controller.sleepRemainingSeconds.value)

        advanceTimeBy(15 * 60_000L - 1_000L)
        runCurrent()
        assertTrue(controller.state.value.isPlaying)
        assertEquals(1, controller.sleepRemainingSeconds.value)

        advanceTimeBy(1_100L)
        runCurrent()
        assertFalse(controller.state.value.isPlaying)
        assertTrue(controller.state.value.canResume)
        assertEquals(1, audio.paused)
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
        assertEquals(0, controller.sleepRemainingSeconds.value)
    }

    @Test
    fun replacingOrCancellingTheTimerStopsTheOldCountdown() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()

        controller.setSleepTimer(SleepTimer.Minutes(15))
        advanceTimeBy(5 * 60_000L)
        controller.setSleepTimer(SleepTimer.Minutes(30))
        advanceTimeBy(20 * 60_000L)
        runCurrent()
        // 25 minutes in: the first timer would have fired at 15, the second one has not yet.
        assertTrue(controller.state.value.isPlaying)

        controller.setSleepTimer(SleepTimer.Off)
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertTrue(controller.state.value.isPlaying)
        assertEquals(0, audio.paused)
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
        assertEquals(0, controller.sleepRemainingSeconds.value)
    }

    @Test
    fun aNonPositiveDurationIsTreatedAsOff() = runTest {
        val controller = createController(FakeAudio())
        controller.setSleepTimer(SleepTimer.Minutes(0))
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
    }

    @Test
    fun stoppingThePlayerCancelsTheTimer() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        controller.setSleepTimer(SleepTimer.Minutes(15))
        controller.stop()
        runCurrent()
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
        assertEquals(0, controller.sleepRemainingSeconds.value)
    }

    @Test
    fun theTimerFiringWhileNothingPlaysOnlyResetsIt() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.setSleepTimer(SleepTimer.Minutes(15))
        advanceTimeBy(15 * 60_000L + 1_100L)
        runCurrent()
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
        assertEquals(0, audio.paused)
    }

    @Test
    fun endOfSurahCutsAnEndlessRepeatOfTheLastAyah() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        val forever = SurahReaderPreferences(repeatCount = SurahReaderPreferences.RepeatForever)
        controller.start(surah(1, 3), forever, practice, fromIndex = 2)
        runCurrent()
        controller.setSleepTimer(SleepTimer.EndOfSurah)
        advanceTimeBy(500L)
        runCurrent()

        audio.complete()
        runCurrent()
        assertFalse(controller.state.value.isPlaying)
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
        assertEquals(listOf("3"), audio.played)
    }

    @Test
    fun withoutTheTimerAnEndlessRepeatKeepsGoing() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        val forever = SurahReaderPreferences(repeatCount = SurahReaderPreferences.RepeatForever)
        controller.start(surah(1, 3), forever, practice, fromIndex = 2)
        runCurrent()
        advanceTimeBy(500L)
        audio.complete()
        runCurrent()
        assertTrue(controller.state.value.isPlaying)
        assertEquals(listOf("3", "3"), audio.played)
    }

    @Test
    fun endOfSurahLetsEarlierAyahsAdvanceAndStopsAfterTheLastOne() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        controller.setSleepTimer(SleepTimer.EndOfSurah)

        repeat(2) {
            advanceTimeBy(500L)
            audio.complete()
            runCurrent()
            assertTrue(controller.state.value.isPlaying)
            assertEquals(SleepTimer.EndOfSurah, controller.sleepTimer.value)
        }
        assertEquals(2, controller.state.value.index)

        advanceTimeBy(500L)
        audio.complete()
        runCurrent()
        assertFalse(controller.state.value.isPlaying)
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
        assertEquals(listOf("1", "2", "3"), audio.played)
    }

    @Test
    fun endOfSurahSetBeforeAnythingPlaysAppliesToTheNextStart() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.setSleepTimer(SleepTimer.EndOfSurah)
        val forever = SurahReaderPreferences(repeatCount = SurahReaderPreferences.RepeatForever)
        controller.start(surah(1, 2), forever, practice.copy(endAyah = 2), fromIndex = 1)
        runCurrent()
        advanceTimeBy(500L)
        audio.complete()
        runCurrent()
        assertFalse(controller.state.value.isPlaying)
        assertEquals(SleepTimer.Off, controller.sleepTimer.value)
    }

    private fun TestScope.createController(audio: FakeAudio) = PlaybackController(
        ReaderAudioSession(
            backgroundScope, audio,
            source = { ayah, _ -> delay(0); ayah.numberInSurah.toString() },
            nowMs = { testScheduler.currentTime },
            persistPosition = { _, _ -> },
        ),
        backgroundScope,
    )

    private fun surah(number: Int, ayahCount: Int) = SurahWithAyahs(
        surah = SurahEntity(number, "name", "englishName", "translation", "Meccan", "edition"),
        ayahs = (1..ayahCount).map { inSurah ->
            AyahEntity(
                number = inSurah,
                numberInSurah = inSurah,
                juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
                text = "text $inSurah",
                surahNumber = number,
            )
        },
    )

    private class FakeAudio : ReaderAudioOutput {
        val played = mutableListOf<String>()
        var paused = 0
        private var onComplete: (() -> Unit)? = null

        override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) {
            played += url
            this.onComplete = onComplete
        }

        override fun pause() {
            paused++
        }

        override fun resume() {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun getPositionMs() = 400L
        override fun getDurationMs() = 1_000L
        override fun stop() {}

        /** The current ayah has played to its end. */
        fun complete() {
            onComplete?.invoke()
        }
    }
}
