package tj.app.quran_todo.common.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderAudioSessionTest {
    @Test
    fun pausingDuringTheSilentGapFreezesTheCountdownAndResumesIt() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio, pause = 3)
        session.toggle()
        runCurrent()
        audio.complete()
        runCurrent()
        assertEquals(3, session.state.value.pauseRemainingSeconds)
        advanceTimeBy(1_100)
        runCurrent()
        session.toggle()
        val remaining = session.state.value.pauseRemainingSeconds
        advanceTimeBy(8_000)
        runCurrent()
        assertEquals(remaining, session.state.value.pauseRemainingSeconds)
        assertEquals(listOf("1"), audio.played)
        session.toggle()
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        session.close()
    }

    @Test
    fun choosingAPauseDoesNotRestartTheCurrentRecitation() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.toggle()
        runCurrent()
        session.updateSettings(SurahReaderPreferences(), ReaderPracticeSettings(true, 1, 3, 1, 5))
        runCurrent()
        assertEquals(listOf("1"), audio.played)
        assertTrue(session.state.value.isPlaying)
        audio.complete()
        runCurrent()
        assertEquals(5, session.state.value.pauseRemainingSeconds)
        session.close()
    }

    @Test
    fun audioCompletionStillAdvancesAfterPausingAndResumingAudio() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.toggle()
        runCurrent()
        session.toggle()
        assertTrue(session.state.value.canResume)
        session.toggle()
        audio.complete()
        runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        session.close()
    }

    @Test
    fun completionRacingWithPauseIsNotLost() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.toggle()
        runCurrent()
        audio.complete()
        session.toggle()
        runCurrent()
        assertEquals(listOf("1"), audio.played)
        session.toggle()
        runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        session.close()
    }

    @Test
    fun nextDuringAGapCancelsTheOldDelayedTransition() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio, pause = 5)
        session.toggle()
        runCurrent()
        audio.complete()
        runCurrent()
        session.next()
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        session.close()
    }

    @Test
    fun staleCompletionAfterSelectingAnotherAyahIsIgnored() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.toggle()
        runCurrent()
        val oldCompletion = audio.completion
        session.select(2)
        oldCompletion()
        runCurrent()
        assertEquals(2, session.state.value.index)
        assertFalse(session.state.value.isPlaying)
        assertEquals(listOf("1"), audio.played)
        session.close()
    }

    @Test
    fun pauseDuringPreparationCancelsThePendingAudio() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio, preparationDelay = 1_000)
        session.toggle()
        runCurrent()
        assertTrue(session.state.value.isPreparing)
        session.toggle()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(emptyList(), audio.played)
        assertFalse(session.state.value.isPlaying)
        session.close()
    }

    @Test
    fun changingRangeDuringAGapCannotStartAnOldAyah() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio, pause = 5)
        session.toggle()
        runCurrent()
        audio.complete()
        runCurrent()
        session.updateSettings(SurahReaderPreferences(), ReaderPracticeSettings(true, 3, 3, 1))
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(listOf("1", "3"), audio.played)
        audio.complete()
        runCurrent()
        assertFalse(session.state.value.isPlaying)
        session.close()
    }

    @Test
    fun anAudioErrorStopsAnEndlessLoop() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.updateSettings(SurahReaderPreferences(), ReaderPracticeSettings(true, 1, 3, -1))
        session.toggle()
        runCurrent()
        audio.failure()
        runCurrent()
        assertTrue(session.state.value.hasError)
        assertFalse(session.state.value.isPlaying)
        assertEquals(listOf("1"), audio.played)
        session.close()
    }

    @Test
    fun leavingTheReaderCancelsItsPendingGap() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio, pause = 5)
        session.toggle()
        runCurrent()
        audio.complete()
        runCurrent()
        session.close()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf("1"), audio.played)
    }

    @Test
    fun restartingAFinishedPassageBeginsAtItsFirstAyah() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.toggle()
        runCurrent()
        repeat(3) { audio.complete(); runCurrent() }
        assertFalse(session.state.value.isPlaying)
        session.toggle()
        runCurrent()
        assertEquals(listOf("1", "2", "3", "1"), audio.played)
        session.close()
    }

    private fun TestScope.createSession(audio: FakeAudio, pause: Int = 0, preparationDelay: Long = 0): ReaderAudioSession {
        return ReaderAudioSession(
            backgroundScope, audio,
            source = { ayah, _ -> delay(preparationDelay); ayah.numberInSurah.toString() },
            nowMs = { testScheduler.currentTime }, persistPosition = { _, _ -> },
        ).also {
            it.configure(1, (1..3).map { number -> ReaderAudioAyah(number, number) },
                SurahReaderPreferences(), ReaderPracticeSettings(true, 1, 3, 1, pause), 1)
        }
    }

    private class FakeAudio : ReaderAudioOutput {
        val played = mutableListOf<String>()
        var completion: () -> Unit = {}
        var failure: () -> Unit = {}
        fun complete() = completion()
        override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) {
            played += url
            completion = onComplete
            failure = onError
        }
        override fun pause() {}
        override fun resume() {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun getPositionMs() = 0L
        override fun getDurationMs() = 1_000L
        override fun stop() {}
    }
}
