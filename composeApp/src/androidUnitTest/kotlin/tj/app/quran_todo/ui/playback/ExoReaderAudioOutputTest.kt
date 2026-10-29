package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExoReaderAudioOutputTest {
    private class FakeEngine : PlayerEngine {
        override var listener: PlayerEngine.Listener? = null
        val loaded = mutableListOf<String>()
        var prepares = 0
        var plays = 0
        var pauses = 0
        var stops = 0
        var lastSpeed = 1f
        override fun load(source: String) {
            loaded += source
        }
        override fun prepare() {
            prepares++
        }
        override fun play() {
            plays++
        }
        override fun pause() {
            pauses++
        }
        override fun stop() {
            stops++
        }
        override fun setSpeed(speed: Float) {
            lastSpeed = speed
        }
        override val positionMs: Long get() = 250
        override val durationMs: Long get() = 3_000
    }

    private class Harness(scope: kotlinx.coroutines.CoroutineScope) {
        val engine = FakeEngine()
        val failedSources = mutableListOf<String>()
        var pausedBySystem = 0
        var errors = 0
        var completions = 0
        val output = ExoReaderAudioOutput(
            engine = engine,
            scope = scope,
            pausedBySystem = { pausedBySystem++ },
            onSourceFailed = { failedSources += it },
        )

        fun play(url: String = "/cache/a.mp3") {
            output.play(url, onError = { errors++ }, onComplete = { completions++ })
        }
    }

    @Test
    fun playingLoadsTheSourceAndEndingCompletesTheAyahOnce() = runTest {
        val h = Harness(backgroundScope)
        h.play()
        assertEquals(listOf("/cache/a.mp3"), h.engine.loaded)
        h.engine.listener!!.onEnded()
        h.engine.listener!!.onEnded()
        assertEquals(1, h.completions)
        assertEquals(0, h.errors)
    }

    @Test
    fun aFailedAyahIsRetriedAfterOneAndThenTwoSecondsBeforeItIsAnError() = runTest {
        val h = Harness(backgroundScope)
        h.play()
        h.engine.listener!!.onError()
        runCurrent()
        assertEquals(0, h.engine.prepares)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, h.engine.prepares)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(1, h.engine.prepares)
        assertEquals(0, h.errors)

        h.engine.listener!!.onError()
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, h.engine.prepares)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, h.engine.prepares)
        assertEquals(0, h.errors)

        // The third failure is the last: no more retries.
        h.engine.listener!!.onError()
        runCurrent()
        assertEquals(1, h.errors)
        assertEquals(listOf("/cache/a.mp3"), h.failedSources)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(2, h.engine.prepares)
    }

    @Test
    fun aRetrySucceedingEndsTheAyahNormally() = runTest {
        val h = Harness(backgroundScope)
        h.play()
        h.engine.listener!!.onError()
        advanceTimeBy(1_001)
        runCurrent()
        h.engine.listener!!.onEnded()
        assertEquals(1, h.completions)
        assertEquals(0, h.errors)
    }

    @Test
    fun startingAnotherAyahCancelsTheRetryOfTheFailedOne() = runTest {
        val h = Harness(backgroundScope)
        h.play("/cache/a.mp3")
        h.engine.listener!!.onError()
        h.play("/cache/b.mp3")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, h.engine.prepares)
        assertEquals(0, h.errors)
    }

    @Test
    fun stoppingCancelsThePendingRetryAndForgetsTheCallbacks() = runTest {
        val h = Harness(backgroundScope)
        h.play()
        h.engine.listener!!.onError()
        h.output.stop()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, h.engine.prepares)
        assertEquals(1, h.engine.stops)
        h.engine.listener!!.onEnded()
        assertEquals(0, h.completions)
    }

    @Test
    fun theFailuresOfOneAyahDoNotCountAgainstTheNext() = runTest {
        val h = Harness(backgroundScope)
        h.play("/cache/a.mp3")
        h.engine.listener!!.onError()
        advanceTimeBy(1_001)
        runCurrent()
        h.engine.listener!!.onEnded()
        h.play("/cache/b.mp3")
        h.engine.listener!!.onError()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, h.engine.prepares)
        assertEquals(0, h.errors)
    }

    @Test
    fun aPauseByTheSystemIsReportedEvenBetweenTwoAyahs() = runTest {
        val h = Harness(backgroundScope)
        h.play()
        h.engine.listener!!.onEnded()
        // The ayah has finished (the session is in its silent gap); the headphones are unplugged now.
        h.engine.listener!!.onPausedBySystem()
        assertEquals(1, h.pausedBySystem)
    }

    @Test
    fun pauseResumeSpeedAndClockGoThroughToTheEngine() = runTest {
        val h = Harness(backgroundScope)
        h.play()
        h.output.pause()
        h.output.resume()
        h.output.setPlaybackSpeed(1.25f)
        assertEquals(1, h.engine.pauses)
        assertEquals(1, h.engine.plays)
        assertEquals(1.25f, h.engine.lastSpeed)
        assertEquals(250, h.output.getPositionMs())
        assertEquals(3_000, h.output.getDurationMs())
    }

    @Test
    fun aSamplePlaysOnTheSamePlayerAtNormalSpeedAndEndsWithoutTouchingTheSession() = runTest {
        val h = Harness(backgroundScope)
        h.output.setPlaybackSpeed(1.5f)
        var finished: Boolean? = null
        h.output.playSample("/cache/sample.mp3") { finished = it }
        assertEquals(listOf("/cache/sample.mp3"), h.engine.loaded)
        assertEquals(1f, h.engine.lastSpeed)
        h.engine.listener!!.onEnded()
        assertEquals(false, finished)
        assertEquals(0, h.completions)
        // The player is idle afterwards, so it gives the audio focus back.
        assertEquals(1, h.engine.stops)
    }

    @Test
    fun aSampleThatFailsIsReportedAndIsNotRetried() = runTest {
        val h = Harness(backgroundScope)
        var finished: Boolean? = null
        h.output.playSample("https://example.org/s.mp3") { finished = it }
        h.engine.listener!!.onError()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(true, finished)
        assertEquals(0, h.engine.prepares)
        assertEquals(0, h.errors)
        assertEquals(emptyList(), h.failedSources)
    }

    @Test
    fun aSampleEndsWhenTheSystemPausesTheAudio() = runTest {
        val h = Harness(backgroundScope)
        var finished: Boolean? = null
        h.output.playSample("/cache/sample.mp3") { finished = it }
        h.engine.listener!!.onPausedBySystem()
        assertEquals(false, finished)
        assertEquals(0, h.pausedBySystem)
    }

    @Test
    fun stoppingASampleIsSilentAndAyahPlaybackTakesThePlayerBackFromIt() = runTest {
        val h = Harness(backgroundScope)
        var calls = 0
        h.output.playSample("/cache/sample.mp3") { calls++ }
        h.output.stopSample()
        assertEquals(0, calls)
        assertEquals(1, h.engine.stops)

        h.output.playSample("/cache/sample.mp3") { calls++ }
        h.play("/cache/a.mp3")
        // Starting an ayah cuts the sample (not a failure) and the ayah plays normally.
        assertEquals(1, calls)
        h.engine.listener!!.onEnded()
        assertEquals(1, h.completions)
    }

    @Test
    fun theAudioFocusIsReleasedOnlyWhenNothingIsPlayingAnymore() = runTest {
        val h = Harness(backgroundScope)
        h.play()
        h.output.releaseFocus()
        assertEquals(0, h.engine.stops)
        h.engine.listener!!.onEnded()
        h.output.releaseFocus()
        assertEquals(1, h.engine.stops)
    }

    @Test
    fun theSpeedIsKeptInTheRangeThePlayerSupports() = runTest {
        val h = Harness(backgroundScope)
        h.output.setPlaybackSpeed(5f)
        assertEquals(2f, h.engine.lastSpeed)
        h.output.setPlaybackSpeed(0.1f)
        assertEquals(0.5f, h.engine.lastSpeed)
        assertTrue(DefaultRetryDelaysMs == listOf(1_000L, 2_000L))
    }
}
