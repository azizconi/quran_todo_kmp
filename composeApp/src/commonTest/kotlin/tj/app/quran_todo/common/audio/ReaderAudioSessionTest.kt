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

    // -- Scripts (the lesson's "every ayah N times, then the passage M times") ----------------------------------

    private val lessonScript = listOf(0, 0, 1, 1, 0, 1)

    @Test
    fun aScriptPlaysItsAyahsInOrderWithRepeatsAndThenStops() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.playScript(lessonScript)
        runCurrent()
        repeat(lessonScript.size) { audio.complete(); runCurrent() }
        assertEquals(listOf("1", "1", "2", "2", "1", "2"), audio.played)
        assertFalse(session.state.value.isPlaying)
        assertFalse(session.state.value.canResume)
        session.close()
    }

    @Test
    fun aScriptIgnoresTheRepeatAndRangeSettings() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        // The session is configured with a repeated range 1..3; the script must not follow it.
        session.updateSettings(SurahReaderPreferences(repeatCount = 5), ReaderPracticeSettings(true, 1, 3, 5, 0))
        session.playScript(listOf(2, 1))
        runCurrent()
        repeat(2) { audio.complete(); runCurrent() }
        assertEquals(listOf("3", "2"), audio.played)
        assertFalse(session.state.value.isPlaying)
        session.close()
    }

    @Test
    fun aScriptCanBePausedAndResumedAndContinuesWhereItWas() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.playScript(lessonScript)
        runCurrent()
        audio.complete(); runCurrent()
        session.toggle()
        assertTrue(session.state.value.canResume)
        session.toggle()
        audio.complete(); runCurrent()
        assertEquals(listOf("1", "1", "2"), audio.played)
        assertEquals(1, session.state.value.index)
        session.close()
    }

    @Test
    fun nextAndPreviousMoveAlongTheScript() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.playScript(lessonScript)
        runCurrent()
        session.next(); runCurrent()
        session.next(); runCurrent()
        session.previous(); runCurrent()
        assertEquals(listOf("1", "1", "2", "1"), audio.played)
        session.close()
    }

    @Test
    fun aFinishedScriptStartsAgainFromItsBeginning() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.playScript(listOf(1, 2))
        runCurrent()
        repeat(2) { audio.complete(); runCurrent() }
        assertFalse(session.state.value.isPlaying)
        session.toggle()
        runCurrent()
        assertEquals(listOf("2", "3", "2"), audio.played)
        session.close()
    }

    @Test
    fun selectingAnAyahEndsTheScript() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.playScript(listOf(0, 1, 2))
        runCurrent()
        session.select(2)
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        // Back to the session's own rules (the range 1..3, one pass): ayah 3 is the last one, the script's
        // remaining ayahs are not played.
        assertEquals(listOf("1", "3"), audio.played)
        assertFalse(session.state.value.isPlaying)
        session.close()
    }

    @Test
    fun aScriptWithoutValidAyahsDoesNothing() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        session.playScript(listOf(7, -1))
        runCurrent()
        assertEquals(emptyList(), audio.played)
        assertFalse(session.state.value.isPlaying)
        session.close()
    }

    // -- The echo mode (task F1b): the silence for the person, a ring, pause and skip -----------------------------

    private val echoOnce = ReaderPracticeSettings(true, 1, 3, 1, 0, echo = true, echoRepeats = 1)

    private fun TestScope.createEchoSession(
        audio: FakeAudio,
        practice: ReaderPracticeSettings = echoOnce,
        preferences: SurahReaderPreferences = SurahReaderPreferences(),
    ): ReaderAudioSession = ReaderAudioSession(
        backgroundScope, audio,
        source = { ayah, _ -> delay(0); ayah.numberInSurah.toString() },
        nowMs = { testScheduler.currentTime }, persistPosition = { _, _ -> },
    ).also { it.configure(1, (1..3).map { number -> ReaderAudioAyah(number, number) }, preferences, practice, 1) }

    @Test
    fun theEchoSilenceFollowsEveryAyahAndTheLastOneToo() = runTest {
        val audio = FakeAudio()
        val session = createEchoSession(audio)
        session.toggle()
        runCurrent()
        audio.complete()
        runCurrent()
        // The ayah lasts 1 s: the silence is the 2 s minimum, and the person sees it (the ring counts it down).
        assertTrue(session.state.value.isPlaying)
        assertEquals(2, session.state.value.pauseRemainingSeconds)
        assertEquals(2, session.state.value.pauseTotalSeconds)
        assertEquals(listOf("1"), audio.played)
        advanceTimeBy(1_900)
        runCurrent()
        assertEquals(listOf("1"), audio.played)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        audio.complete(); runCurrent()
        advanceTimeBy(2_100); runCurrent()
        assertEquals(listOf("1", "2", "3"), audio.played)
        audio.complete(); runCurrent()
        // The last ayah is followed by its silence as well: the sequence is not over until the person has had the turn.
        assertTrue(session.state.value.isPlaying)
        assertEquals(2, session.state.value.pauseRemainingSeconds)
        advanceTimeBy(2_100); runCurrent()
        assertFalse(session.state.value.isPlaying)
        assertFalse(session.state.value.canResume)
        assertEquals(0, session.state.value.pauseRemainingSeconds)
        assertEquals(listOf("1", "2", "3"), audio.played)
        session.close()
    }

    @Test
    fun anEchoRoundReadsTheSameAyahAgainAfterTheSilence() = runTest {
        val audio = FakeAudio()
        val session = createEchoSession(audio, echoOnce.copy(echoRepeats = 2))
        session.toggle()
        runCurrent()
        repeat(2) {
            audio.complete(); runCurrent()
            advanceTimeBy(2_100); runCurrent()
        }
        assertEquals(listOf("1", "1", "2"), audio.played)
        session.close()
    }

    @Test
    fun theSilenceIsTheLengthOfALongAyahTimesOnePointTwo() = runTest {
        val audio = FakeAudio().apply { duration = 10_000 }
        val session = createEchoSession(audio)
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        assertEquals(12, session.state.value.pauseRemainingSeconds)
        assertEquals(12, session.state.value.pauseTotalSeconds)
        advanceTimeBy(11_000); runCurrent()
        assertEquals(listOf("1"), audio.played)
        assertEquals(1, session.state.value.pauseRemainingSeconds)
        advanceTimeBy(1_100); runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        session.close()
    }

    @Test
    fun theEchoSilenceReplacesThePlainPause() = runTest {
        val audio = FakeAudio()
        val session = createEchoSession(audio, echoOnce.copy(pauseSeconds = 10))
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        assertEquals(2, session.state.value.pauseRemainingSeconds)
        advanceTimeBy(2_100); runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        session.close()
    }

    @Test
    fun theEchoSilenceCanBePausedAndResumedFromWhereItStood() = runTest {
        val audio = FakeAudio().apply { duration = 10_000 }
        val session = createEchoSession(audio)
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        advanceTimeBy(4_100); runCurrent()
        session.toggle()
        assertTrue(session.state.value.canResume)
        assertFalse(session.state.value.isPlaying)
        val left = session.state.value.pauseRemainingSeconds
        assertTrue(left in 7..8, "left $left")
        advanceTimeBy(30_000); runCurrent()
        assertEquals(left, session.state.value.pauseRemainingSeconds)
        assertEquals(listOf("1"), audio.played)
        session.toggle()
        advanceTimeBy(7_000); runCurrent()
        assertEquals(listOf("1"), audio.played)
        advanceTimeBy(1_500); runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
        session.close()
    }

    @Test
    fun nextSkipsTheEchoSilenceAndPreviousGoesBackDuringIt() = runTest {
        val audio = FakeAudio().apply { duration = 10_000 }
        val session = createEchoSession(audio)
        session.select(1)
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        assertEquals(12, session.state.value.pauseRemainingSeconds)
        session.next(); runCurrent()
        assertEquals(listOf("2", "3"), audio.played)
        assertEquals(0, session.state.value.pauseRemainingSeconds)
        audio.complete(); runCurrent()
        session.previous(); runCurrent()
        assertEquals(listOf("2", "3", "2"), audio.played)
        // The skipped silences do not come back later.
        advanceTimeBy(30_000); runCurrent()
        assertEquals(listOf("2", "3", "2"), audio.played)
        assertEquals(1, session.state.value.index)
        session.close()
    }

    @Test
    fun stopAfterTheAyahEndsAtOnceWithoutTheSilence() = runTest {
        val audio = FakeAudio()
        val session = createEchoSession(audio)
        session.stopAfterAyah(1)
        session.select(1)
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        assertFalse(session.state.value.isPlaying)
        assertEquals(0, session.state.value.pauseRemainingSeconds)
        assertEquals(listOf("2"), audio.played)
        session.close()
    }

    @Test
    fun aScriptHasTheSilenceAfterEveryReadingOfIt() = runTest {
        val audio = FakeAudio()
        val session = createEchoSession(audio)
        session.playScript(listOf(0, 0, 1))
        runCurrent()
        repeat(3) {
            audio.complete(); runCurrent()
            assertEquals(2, session.state.value.pauseRemainingSeconds)
            advanceTimeBy(2_100); runCurrent()
        }
        assertEquals(listOf("1", "1", "2"), audio.played)
        assertFalse(session.state.value.isPlaying)
        session.close()
    }

    @Test
    fun theOwnerIsToldWhenTheSequenceEndsAndCanContinueWithSomethingElse() = runTest {
        val audio = FakeAudio()
        val session = createEchoSession(audio, echoOnce.copy(startAyah = 1, endAyah = 1))
        var ended = 0
        session.onSequenceEnd = {
            ended++
            true
        }
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        // The silence of the last ayah comes first; the owner is told only when it is over.
        assertEquals(0, ended)
        advanceTimeBy(2_100); runCurrent()
        assertEquals(1, ended)
        // The owner handled it: the session did not go idle by itself.
        assertTrue(session.state.value.isPlaying)
        session.close()
    }

    @Test
    fun theOwnerIsNotToldWhenTheSleepTimerCutsPlayback() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio)
        var ended = 0
        session.onSequenceEnd = { ended++; false }
        session.stopAfterAyah(0)
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        assertEquals(0, ended)
        assertFalse(session.state.value.isPlaying)
        session.close()
    }

    @Test
    fun withoutTheEchoNothingChanged() = runTest {
        val audio = FakeAudio()
        val session = createSession(audio, pause = 3)
        session.toggle()
        runCurrent()
        audio.complete(); runCurrent()
        assertEquals(3, session.state.value.pauseRemainingSeconds)
        assertEquals(3, session.state.value.pauseTotalSeconds)
        advanceTimeBy(3_100); runCurrent()
        assertEquals(listOf("1", "2"), audio.played)
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
        var duration = 1_000L
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
        override fun getDurationMs() = duration
        override fun stop() {}
    }
}
