package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.common.audio.ReaderAudioAyah
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {
    private val practice = ReaderPracticeSettings(rangeEnabled = false, startAyah = 1, endAyah = 3)

    @Test
    fun nothingIsPlayingUntilStartIsCalled() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.toggle()
        controller.next()
        runCurrent()
        assertNull(controller.nowPlaying.value)
        assertFalse(controller.isActiveFor(1))
        assertEquals(emptyList(), audio.played)
    }

    @Test
    fun startPositionsTheSessionAndPlaysFromTheChosenAyah() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(2, ayahCount = 3, firstGlobal = 8), SurahReaderPreferences(), practice, fromIndex = 1)
        runCurrent()
        assertEquals(2, controller.nowPlaying.value?.surahNumber)
        assertEquals(listOf(ReaderAudioAyah(8, 1), ReaderAudioAyah(9, 2), ReaderAudioAyah(10, 3)), controller.nowPlaying.value?.ayahs)
        assertTrue(controller.isActiveFor(2))
        assertEquals(1, controller.state.value.index)
        assertTrue(controller.state.value.isPlaying)
        assertEquals(listOf("2"), audio.played)
    }

    @Test
    fun startWithoutAutoplayOnlyPositionsTheSession() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 2, autoplay = false)
        runCurrent()
        assertEquals(1, controller.nowPlaying.value?.surahNumber)
        assertEquals(2, controller.state.value.index)
        assertFalse(controller.state.value.isPlaying)
        assertEquals(emptyList(), audio.played)
    }

    @Test
    fun theAutoplayOnOpenPreferenceDoesNotStartAudioBehindAPositionOnlyStart() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(
            surah(1, 3), SurahReaderPreferences(autoplayOnOpen = true), practice, fromIndex = 1, autoplay = false,
        )
        runCurrent()
        assertFalse(controller.state.value.isPlaying)
        assertEquals(emptyList(), audio.played)
    }

    @Test
    fun startingAnotherSurahTakesTheSessionOver() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        controller.start(surah(2, 3, firstGlobal = 4), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        assertFalse(controller.isActiveFor(1))
        assertTrue(controller.isActiveFor(2))
        assertEquals(listOf("1", "1"), audio.played)
        assertEquals(0, controller.state.value.index)
    }

    @Test
    fun fromIndexIsClampedToTheSurah() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 99, autoplay = false)
        assertEquals(2, controller.state.value.index)
    }

    @Test
    fun stopHidesThePlayerAndSilencesTheSession() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        controller.stop()
        runCurrent()
        assertNull(controller.nowPlaying.value)
        assertFalse(controller.state.value.isPlaying)
        assertTrue(audio.stopped > 0)
    }

    @Test
    fun transportCommandsDriveTheSession() = runTest {
        val audio = FakeAudio()
        val controller = createController(audio)
        controller.start(surah(1, 3), SurahReaderPreferences(), practice, fromIndex = 0)
        runCurrent()
        controller.next()
        runCurrent()
        assertEquals(1, controller.state.value.index)
        controller.toggle()
        assertTrue(controller.state.value.canResume)
        controller.select(2)
        assertEquals(2, controller.state.value.index)
        assertFalse(controller.state.value.isPlaying)
        assertEquals(listOf("1", "2"), audio.played)
    }

    private fun TestScope.createController(audio: FakeAudio) = PlaybackController(
        ReaderAudioSession(
            backgroundScope, audio,
            source = { ayah, _ -> delay(0); ayah.numberInSurah.toString() },
            nowMs = { testScheduler.currentTime },
            persistPosition = { _, _ -> },
        ),
    )

    private fun surah(number: Int, ayahCount: Int, firstGlobal: Int = 1) = SurahWithAyahs(
        surah = SurahEntity(number, "name", "englishName", "translation", "Meccan", "edition"),
        // Deliberately unsorted: the controller must order ayahs by their number in the surah.
        ayahs = (1..ayahCount).reversed().map { inSurah ->
            AyahEntity(
                number = firstGlobal + inSurah - 1,
                numberInSurah = inSurah,
                juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
                text = "text $inSurah",
                surahNumber = number,
            )
        },
    )

    private class FakeAudio : ReaderAudioOutput {
        val played = mutableListOf<String>()
        var stopped = 0
        override fun play(url: String, onError: () -> Unit, onComplete: () -> Unit) {
            played += url
        }
        override fun pause() {}
        override fun resume() {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun getPositionMs() = 0L
        override fun getDurationMs() = 1_000L
        override fun stop() {
            stopped++
        }
    }
}
