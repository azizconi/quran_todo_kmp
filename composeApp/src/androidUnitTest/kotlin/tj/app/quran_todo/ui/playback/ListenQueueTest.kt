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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The listening queue of "Listen to the old" (task F1b): passages of different surahs, one after another. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenQueueTest {
    private val prefs = SurahReaderPreferences()

    private val twoSurahs = listOf(
        ListenItem(passageId = 11, surah = 112, startAyah = 1, endAyah = 2, seconds = 10),
        ListenItem(passageId = 12, surah = 113, startAyah = 2, endAyah = 3, seconds = 12),
    )

    private class Setup(
        val controller: PlaybackController,
        val audio: FakeAudio,
        val extras: FakeExtras,
        val saved: List<Pair<Int, Int>>,
    )

    private class FakeExtras : OutputExtras {
        var focusReleases = 0
        override fun playSample(source: String, onFinished: (failed: Boolean) -> Unit) = Unit
        override fun stopSample() = Unit
        override fun releaseFocus() {
            focusReleases++
        }
    }

    /** The source names the file by surah and ayah, so the test sees which passage of which surah plays. */
    private fun TestScope.setup(): Setup {
        val audio = FakeAudio()
        val extras = FakeExtras()
        val saved = mutableListOf<Pair<Int, Int>>()
        val controller = PlaybackController(
            session = ReaderAudioSession(
                backgroundScope, audio,
                source = { ayah, surah -> delay(0); "$surah:${ayah.numberInSurah}" },
                nowMs = { testScheduler.currentTime },
                persistPosition = { surah, ayah -> saved += surah to ayah },
            ),
            timerScope = backgroundScope,
            extras = extras,
        )
        return Setup(controller, audio, extras, saved)
    }

    private fun surah(number: Int, ayahCount: Int = 4) = SurahWithAyahs(
        surah = SurahEntity(number, "name", "englishName", "translation", "Meccan", "edition"),
        ayahs = (1..ayahCount).map { inSurah ->
            AyahEntity(
                number = number * 1_000 + inSurah, numberInSurah = inSurah,
                juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
                text = "text $inSurah", surahNumber = number,
            )
        },
    )

    private fun Setup.start(
        items: List<ListenItem> = twoSurahs,
        echo: Boolean = false,
        echoRepeats: Int = 1,
        from: Int = 0,
    ) {
        controller.startListenQueue(items, loadSurah = { surah(it) }, prefs = prefs, echo = echo, echoRepeats = echoRepeats, fromPosition = from)
    }

    @Test
    fun thePassagesOfDifferentSurahsPlayOneAfterTheOtherAndTheQueueEnds() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        assertEquals(112, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(0, s.controller.state.value.index)
        assertTrue(s.controller.state.value.isPlaying)

        s.audio.complete(); runCurrent()
        assertEquals(1, s.controller.state.value.index)
        s.audio.complete(); runCurrent()
        // The first passage is over: the second one (another surah) is already playing, from its first ayah.
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(1, s.controller.state.value.index)
        assertTrue(s.controller.state.value.isPlaying)
        assertEquals(1, s.controller.listenQueue.value?.position)
        assertEquals(setOf(0), s.controller.listenQueue.value?.heard)
        assertFalse(s.controller.listenQueue.value!!.finished)

        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        assertEquals(listOf("112:1", "112:2", "113:2", "113:3"), s.audio.played)
        assertFalse(s.controller.state.value.isPlaying)
        val queue = s.controller.listenQueue.value
        assertNotNull(queue)
        assertTrue(queue.finished)
        assertEquals(setOf(0, 1), queue.heard)
        assertEquals(listOf(11L, 12L), queue.heardItems.map { it.passageId })
    }

    @Test
    fun theAudioIsNotReleasedBetweenTwoPassagesButIsAtTheEnd() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        // Another passage took over at once: other apps never got the audio focus in between.
        assertEquals(0, s.extras.focusReleases)
        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        assertTrue(s.extras.focusReleases > 0)
    }

    @Test
    fun theQueueKeepsThePlayersSpeedButNotARepeatCount() = runTest {
        val s = setup()
        s.controller.startListenQueue(
            twoSurahs, loadSurah = { surah(it) },
            prefs = SurahReaderPreferences(repeatCount = 5, playbackSpeed = 0.75f), echo = false,
        )
        runCurrent()
        s.audio.complete(); runCurrent()
        // Every ayah is read once, not five times.
        assertEquals(listOf("112:1", "112:2"), s.audio.played)
        assertEquals(listOf(0.75f), s.audio.speeds.distinct())
    }

    @Test
    fun theNextPassageAndThePreviousOneSwitchAtOnce() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.controller.nextListenItem(); runCurrent()
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(1, s.controller.listenQueue.value?.position)
        // The one that was skipped is not "heard".
        assertEquals(emptySet(), s.controller.listenQueue.value?.heard)
        s.controller.previousListenItem(); runCurrent()
        assertEquals(112, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(0, s.controller.state.value.index)
        assertEquals(listOf("112:1", "113:2", "112:1"), s.audio.played)
    }

    @Test
    fun nextAfterTheLastPassageFinishesTheQueueAndStopsTheSound() = runTest {
        val s = setup()
        s.start(from = 1)
        runCurrent()
        s.controller.nextListenItem(); runCurrent()
        assertFalse(s.controller.state.value.isPlaying)
        assertTrue(s.controller.listenQueue.value!!.finished)
        // Play after that starts the last passage again and the queue is not finished any more.
        s.controller.toggle(); runCurrent()
        assertTrue(s.controller.state.value.isPlaying)
        assertFalse(s.controller.listenQueue.value!!.finished)
        assertEquals(listOf("113:2", "113:2"), s.audio.played)
    }

    @Test
    fun theNextAyahOfTheHeadsetAtTheLastAyahOfAPassageMovesToTheNextPassage() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.controller.next(); runCurrent()
        assertEquals(1, s.controller.state.value.index)
        assertEquals(112, s.controller.nowPlaying.value?.surahNumber)
        s.controller.next(); runCurrent()
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(listOf("112:1", "112:2", "113:2"), s.audio.played)
    }

    @Test
    fun thePreviousAyahAtTheStartOfAPassageGoesBackToThePassageBeforeIt() = runTest {
        val s = setup()
        s.start(from = 1)
        runCurrent()
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        // 113:2 is the first ayah of the second passage: back means the passage before it, from its start.
        s.controller.previous(); runCurrent()
        assertEquals(112, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(0, s.controller.state.value.index)
        // At the first ayah of the first passage it plays that ayah again, and inside a passage it is the ayah before.
        s.controller.previous(); runCurrent()
        assertEquals(listOf("113:2", "112:1", "112:1"), s.audio.played)
        s.controller.next(); runCurrent()
        s.controller.previous(); runCurrent()
        assertEquals(listOf("113:2", "112:1", "112:1", "112:2", "112:1"), s.audio.played)
    }

    @Test
    fun theMediaSessionCanJumpToAnAyahOfAPassage() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.controller.seekListenAyah(1, 3); runCurrent()
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(2, s.controller.state.value.index)
        assertEquals(1, s.controller.listenQueue.value?.position)
        assertEquals("113:3", s.audio.played.last())
        // An ayah that is not in the passage plays the passage from its start.
        s.controller.seekListenAyah(0, 99); runCurrent()
        assertEquals(112, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(0, s.controller.state.value.index)
    }

    @Test
    fun doneStopsTheSoundAndKeepsWhatWasHeard() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        s.controller.finishListenQueue()
        runCurrent()
        assertFalse(s.controller.state.value.isPlaying)
        val queue = s.controller.listenQueue.value!!
        assertTrue(queue.finished)
        // Only the first passage was read to its end.
        assertEquals(listOf(11L), queue.heardItems.map { it.passageId })
        s.controller.endListenQueue()
        assertNull(s.controller.listenQueue.value)
        assertNull(s.controller.nowPlaying.value)
    }

    @Test
    fun theEchoQueueHasASilenceAfterEveryAyahAndTheLastOneToo() = runTest {
        val s = setup()
        s.start(echo = true)
        runCurrent()
        s.audio.complete(); runCurrent()
        assertTrue(s.controller.state.value.isPlaying)
        assertEquals(2, s.controller.state.value.pauseRemainingSeconds)
        advanceTimeBy(2_100); runCurrent()
        s.audio.complete(); runCurrent()
        // The last ayah of the passage has its silence before the next passage begins.
        assertEquals(112, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(2, s.controller.state.value.pauseRemainingSeconds)
        assertEquals(emptySet(), s.controller.listenQueue.value?.heard)
        advanceTimeBy(2_100); runCurrent()
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(setOf(0), s.controller.listenQueue.value?.heard)
        assertEquals(listOf("112:1", "112:2", "113:2"), s.audio.played)
    }

    @Test
    fun echoCanBeSwitchedOnWhileAPassagePlaysWithoutRestartingIt() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.controller.setListenEcho(echo = true, echoRepeats = 1)
        runCurrent()
        assertEquals(listOf("112:1"), s.audio.played)
        assertTrue(s.controller.listenQueue.value!!.echo)
        s.audio.complete(); runCurrent()
        assertEquals(2, s.controller.state.value.pauseRemainingSeconds)
    }

    @Test
    fun aSurahThatCannotBeLoadedIsSkipped() = runTest {
        val s = setup()
        s.controller.startListenQueue(
            twoSurahs, loadSurah = { if (it == 112) null else surah(it) }, prefs = prefs, echo = false,
        )
        runCurrent()
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(1, s.controller.listenQueue.value?.position)
    }

    @Test
    fun aQueueThatCannotStartLeavesNothingBehind() = runTest {
        val s = setup()
        s.controller.startListenQueue(twoSurahs, loadSurah = { null }, prefs = prefs, echo = false)
        runCurrent()
        assertNull(s.controller.listenQueue.value)
        assertNull(s.controller.nowPlaying.value)
        s.controller.startListenQueue(emptyList(), loadSurah = { surah(it) }, prefs = prefs, echo = false)
        assertNull(s.controller.listenQueue.value)
    }

    @Test
    fun anotherPlaybackOrStopEndsTheQueue() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.controller.start(surah(1), prefs, ReaderPracticeSettings(), fromIndex = 0)
        runCurrent()
        assertNull(s.controller.listenQueue.value)
        s.start()
        runCurrent()
        s.controller.playSequence(surah(2), listOf(0, 1), prefs)
        assertNull(s.controller.listenQueue.value)
        s.start()
        runCurrent()
        s.controller.stop()
        assertNull(s.controller.listenQueue.value)
        assertNull(s.controller.nowPlaying.value)
    }

    @Test
    fun theSettingsOfAReaderDoNotBreakThePassageOfTheQueue() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        // A reader of the same surah saves its own repeat / pause settings: the passage 1..2 must stay what it was.
        s.controller.updateSettings(SurahReaderPreferences(playbackSpeed = 1.25f), ReaderPracticeSettings(pauseSeconds = 10))
        runCurrent()
        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        assertEquals(113, s.controller.nowPlaying.value?.surahNumber)
        assertEquals(listOf("112:1", "112:2", "113:2"), s.audio.played)
    }

    @Test
    fun twoPassagesOfTheSameSurahFollowEachOther() = runTest {
        val s = setup()
        val items = listOf(
            ListenItem(1, surah = 112, startAyah = 1, endAyah = 1),
            ListenItem(2, surah = 112, startAyah = 3, endAyah = 4),
        )
        s.start(items)
        runCurrent()
        s.audio.complete(); runCurrent()
        assertEquals(2, s.controller.state.value.index)
        assertTrue(s.controller.state.value.isPlaying)
        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        assertEquals(listOf("112:1", "112:3", "112:4"), s.audio.played)
        assertTrue(s.controller.listenQueue.value!!.finished)
    }

    @Test
    fun theOldPassagesOfTheQueueAreNotRememberedAsWhereThePersonStopped() = runTest {
        val s = setup()
        s.start()
        runCurrent()
        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        s.audio.complete(); runCurrent()
        // Nothing of the queue went to the "Continue" position of the home screen.
        assertEquals(emptyList(), s.saved)
        // A reader that takes the session over remembers its ayah again.
        s.controller.start(surah(1), prefs, ReaderPracticeSettings(), fromIndex = 2)
        runCurrent()
        assertEquals(1 to 1_003, s.saved.last())
    }

    @Test
    fun aLessonSequenceWithTheEchoHasTheSilenceAfterEveryReading() = runTest {
        val s = setup()
        // The lesson's "listen": ayah 1 twice, ayah 2 once; with the echo each reading is followed by the silence.
        s.controller.playSequence(surah(4), listOf(0, 0, 1), prefs, echo = true)
        runCurrent()
        repeat(3) {
            s.audio.complete(); runCurrent()
            assertTrue(s.controller.state.value.isPlaying)
            assertEquals(2, s.controller.state.value.pauseRemainingSeconds)
            advanceTimeBy(2_100); runCurrent()
        }
        assertEquals(listOf("4:1", "4:1", "4:2"), s.audio.played)
        assertFalse(s.controller.state.value.isPlaying)
    }

    @Test
    fun aLessonSequenceWithoutTheEchoHasNoSilence() = runTest {
        val s = setup()
        s.controller.playSequence(surah(4), listOf(0, 1), prefs)
        runCurrent()
        s.audio.complete(); runCurrent()
        assertEquals(0, s.controller.state.value.pauseRemainingSeconds)
        assertEquals(listOf("4:1", "4:2"), s.audio.played)
    }

    private class FakeAudio : ReaderAudioOutput {
        val played = mutableListOf<String>()
        val speeds = mutableListOf<Float>()
        var completion: () -> Unit = {}
        fun complete() = completion()
        override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) {
            played += url
            completion = onComplete
        }
        override fun pause() = Unit
        override fun resume() = Unit
        override fun setPlaybackSpeed(speed: Float) {
            speeds += speed
        }
        override fun getPositionMs() = 0L
        override fun getDurationMs() = 1_000L
        override fun stop() = Unit
    }
}
