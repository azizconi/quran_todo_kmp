package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.ReaderAudioOutput
import tj.app.quran_todo.common.audio.ReaderAudioSession
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the system does to the audio: focus loss and unplugged headphones, a changed reciter, the service link. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSystemEventsTest {
    private val practice = ReaderPracticeSettings(rangeEnabled = false, startAyah = 1, endAyah = 3)

    private class FakeAudio : ReaderAudioOutput {
        val played = mutableListOf<String>()
        var paused = 0
        var resumed = 0
        private var onComplete: (() -> Unit)? = null
        override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) {
            played += url
            this.onComplete = onComplete
        }

        /** The current ayah has played to its end. */
        fun complete() {
            onComplete?.invoke()
        }
        override fun pause() {
            paused++
        }
        override fun resume() {
            resumed++
        }
        override fun setPlaybackSpeed(speed: Float) {}
        override fun getPositionMs() = 400L
        override fun getDurationMs() = 1_000L
        override fun stop() {}
    }

    private class FakeLink : PlaybackServiceLink {
        var connects = 0
        var releases = 0
        override fun ensureConnected() {
            connects++
        }
        override fun release() {
            releases++
        }
    }

    private class FakeExtras : OutputExtras {
        val samples = mutableListOf<String>()
        var sampleStops = 0
        var focusReleases = 0
        override fun playSample(source: String, onFinished: (failed: Boolean) -> Unit) {
            samples += source
        }
        override fun stopSample() {
            sampleStops++
        }
        override fun releaseFocus() {
            focusReleases++
        }
    }

    private class Setup(
        val controller: PlaybackController,
        val audio: FakeAudio,
        val reciter: MutableStateFlow<Reciter>,
        val link: FakeLink,
    )

    /** The source names the file after the reciter that is current when the ayah starts, like the real one. */
    private fun TestScope.setup(extras: OutputExtras? = null): Setup {
        val audio = FakeAudio()
        val reciter = MutableStateFlow(Reciters.Alafasy)
        val link = FakeLink()
        val controller = PlaybackController(
            session = ReaderAudioSession(
                backgroundScope, audio,
                source = { ayah, _ -> delay(0); "${reciter.value.id}:${ayah.numberInSurah}" },
                nowMs = { testScheduler.currentTime },
                persistPosition = { _, _ -> },
            ),
            timerScope = backgroundScope,
            reciter = reciter,
            serviceLink = link,
            extras = extras,
        )
        return Setup(controller, audio, reciter, link)
    }

    private fun surah(ayahCount: Int) = SurahWithAyahs(
        surah = SurahEntity(78, "name", "englishName", "translation", "Meccan", "edition"),
        ayahs = (1..ayahCount).map { inSurah ->
            AyahEntity(
                number = 5_000 + inSurah, numberInSurah = inSurah,
                juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
                text = "text $inSurah", surahNumber = 78,
            )
        },
    )

    @Test
    fun startingConnectsTheBackgroundServiceOnceNotOnEveryCommand() = runTest {
        val s = setup()
        assertEquals(0, s.link.connects)
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        assertEquals(1, s.link.connects)
        s.controller.next()
        s.controller.toggle()
        assertEquals(1, s.link.connects)
    }

    @Test
    fun disconnectingTheServiceReleasesTheLink() = runTest {
        val s = setup()
        s.controller.disconnectService()
        assertEquals(1, s.link.releases)
    }

    @Test
    fun aPauseByTheSystemShowsPausedAndPlayResumesFromThere() = runTest {
        val s = setup()
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 1)
        runCurrent()
        assertTrue(s.controller.state.value.isPlaying)

        s.controller.pauseBySystem()

        val state = s.controller.state.value
        assertFalse(state.isPlaying)
        assertTrue(state.canResume)
        assertEquals(1, state.index)

        s.controller.toggle()
        assertTrue(s.controller.state.value.isPlaying)
        assertEquals(1, s.audio.resumed)
        assertEquals(1, s.audio.played.size)
    }

    @Test
    fun aPauseByTheSystemWhileAlreadyPausedOrIdleChangesNothing() = runTest {
        val s = setup()
        s.controller.pauseBySystem()
        assertEquals(0, s.audio.paused)
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        s.controller.toggle()
        val pausesBefore = s.audio.paused
        s.controller.pauseBySystem()
        assertEquals(pausesBefore, s.audio.paused)
        assertTrue(s.controller.state.value.canResume)
    }

    @Test
    fun changingTheReciterWhilePlayingRestartsTheCurrentAyahWithTheNewVoice() = runTest {
        val s = setup()
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 1)
        runCurrent()
        assertEquals(listOf("alafasy:2"), s.audio.played)

        s.reciter.value = Reciters.Husary
        runCurrent()

        assertEquals(listOf("alafasy:2", "husary:2"), s.audio.played)
        assertTrue(s.controller.state.value.isPlaying)
        assertEquals(1, s.controller.state.value.index)
    }

    @Test
    fun changingTheReciterWhilePausedHalfwayDropsTheOldVoiceInsteadOfResumingIt() = runTest {
        val s = setup()
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        s.controller.toggle()
        assertTrue(s.controller.state.value.canResume)

        s.reciter.value = Reciters.Minshawi
        runCurrent()

        assertFalse(s.controller.state.value.isPlaying)
        assertFalse(s.controller.state.value.canResume)
        s.controller.toggle()
        runCurrent()
        assertEquals(listOf("alafasy:1", "minshawi:1"), s.audio.played)
        assertEquals(0, s.audio.resumed)
    }

    @Test
    fun changingTheReciterWithNothingPlayingStartsNothing() = runTest {
        val s = setup()
        s.reciter.value = Reciters.Husary
        runCurrent()
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 0, autoplay = false)
        runCurrent()
        s.reciter.value = Reciters.Minshawi
        runCurrent()
        assertEquals(emptyList(), s.audio.played)
        assertFalse(s.controller.state.value.isPlaying)
    }
    @Test
    fun aSampleStopsTheReaderAudioWhereItIsAndKeepsThePlace() = runTest {
        val extras = FakeExtras()
        val s = setup(extras)
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 1)
        runCurrent()
        assertTrue(s.controller.state.value.isPlaying)

        s.controller.playSample("/cache/sample.mp3") { }

        val state = s.controller.state.value
        assertFalse(state.isPlaying)
        assertFalse(state.canResume)
        assertEquals(1, state.index)
        assertEquals(78, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(listOf("/cache/sample.mp3"), extras.samples)
    }

    @Test
    fun aSampleWithNoPlayerReportsAFailureInsteadOfStayingSilent() = runTest {
        val s = setup(extras = null)
        var failed: Boolean? = null
        s.controller.playSample("/cache/sample.mp3") { failed = it }
        assertEquals(true, failed)
    }

    @Test
    fun stoppingTheSampleGoesToThePlayer() = runTest {
        val extras = FakeExtras()
        val s = setup(extras)
        s.controller.stopSample()
        assertEquals(1, extras.sampleStops)
    }

    @Test
    fun theAudioFocusIsGivenBackWhenTheLastAyahHasEnded() = runTest {
        val extras = FakeExtras()
        val s = setup(extras)
        s.controller.start(surah(1), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        val whilePlaying = extras.focusReleases
        s.audio.complete()
        runCurrent()
        assertFalse(s.controller.state.value.isPlaying)
        assertEquals(whilePlaying + 1, extras.focusReleases)
    }

    @Test
    fun theAudioFocusIsKeptWhilePausedHalfwayAndBetweenAyahs() = runTest {
        val extras = FakeExtras()
        val s = setup(extras)
        s.controller.start(surah(3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        val whilePlaying = extras.focusReleases
        s.audio.complete()
        runCurrent()
        // Auto-advance went on to the next ayah: still playing, nothing released.
        assertTrue(s.controller.state.value.isPlaying)
        s.controller.toggle()
        runCurrent()
        assertTrue(s.controller.state.value.canResume)
        assertEquals(whilePlaying, extras.focusReleases)
    }
}
