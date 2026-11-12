package tj.app.quran_todo.ui.lesson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.common.audio.ReaderAudioState
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.lessonListenCopy

class ListenProgressTest {
    // 6 ayahs, each x3, then the passage x2: 18 + 12 = 30 steps.
    private val run = ListenRun(surah = 114, ayahCount = 6, ayahRepeats = 3, passageRepeats = 2)

    private fun playing(position: Int, length: Int = 30, positionMs: Long = 0, durationMs: Long = 0) =
        ReaderAudioState(
            index = 0, isPlaying = true, scriptPosition = position, scriptLength = length,
            positionMs = positionMs, durationMs = durationMs,
        )

    @Test
    fun theRunHasTheLengthOfThePlan() {
        assertEquals(30, run.length)
        assertEquals(3, ListenRun(1, 1, 3, 2).length) // one ayah: no passes of its own
    }

    @Test
    fun placeInTheAyahPart() {
        assertEquals(ListenPlace.Ayah(1, 6, 1, 3), ListenPlan.place(run, 0))
        assertEquals(ListenPlace.Ayah(1, 6, 3, 3), ListenPlan.place(run, 2))
        assertEquals(ListenPlace.Ayah(2, 6, 1, 3), ListenPlan.place(run, 3))
        assertEquals(ListenPlace.Ayah(6, 6, 3, 3), ListenPlan.place(run, 17))
    }

    @Test
    fun placeInThePassagePart() {
        assertEquals(ListenPlace.Passage(1, 2), ListenPlan.place(run, 18))
        assertEquals(ListenPlace.Passage(1, 2), ListenPlan.place(run, 23))
        assertEquals(ListenPlace.Passage(2, 2), ListenPlan.place(run, 24))
        assertEquals(ListenPlace.Passage(2, 2), ListenPlan.place(run, 29))
        assertNull(ListenPlan.place(run, 30))
        assertNull(ListenPlan.place(run, -1))
    }

    @Test
    fun theLabelsAreFilledInEveryLanguage() {
        val en = lessonListenCopy(AppLanguage.EN)
        assertEquals("Ayah 2 of 6 · repeat 1 of 3", ListenPlan.place(run, 3)!!.label(en))
        assertEquals("Whole passage · 1 of 2", ListenPlan.place(run, 18)!!.label(en))
        assertEquals("Аят 2 из 6 · повтор 1 из 3", ListenPlan.place(run, 3)!!.label(lessonListenCopy(AppLanguage.RU)))
        AppLanguage.entries.forEach { language ->
            val copy = lessonListenCopy(language)
            assertTrue(copy.translationChip.isNotBlank())
            listOf(ListenPlan.place(run, 3)!!, ListenPlan.place(run, 20)!!).forEach { place ->
                assertFalse(place.label(copy).contains("{"), "$language leaves a placeholder")
            }
        }
    }

    @Test
    fun progressFollowsThePositionAndTheAyahTime() {
        val progress = ListenProgress.next(null, run, 114, playing(position = 14, positionMs = 500, durationMs = 1000))!!
        assertEquals(14, progress.position)
        assertEquals((14.5f / 30f), progress.fraction, 0.0001f)
        assertFalse(progress.done)
        // Paused halfway counts as running; preparing counts the step as just begun.
        val paused = ReaderAudioState(canResume = true, scriptPosition = 4, scriptLength = 30)
        assertEquals(ListenPlace.Ayah(2, 6, 2, 3), ListenProgress.next(null, run, 114, paused)!!.place)
        val preparing = playing(4, positionMs = 900, durationMs = 1000).copy(isPreparing = true)
        assertEquals(4f / 30f, ListenProgress.next(null, run, 114, preparing)!!.fraction, 0.0001f)
    }

    @Test
    fun otherSoundIsNotTheRun() {
        assertNull(ListenProgress.next(null, run, 112, playing(3)))
        assertNull(ListenProgress.next(null, run, 114, playing(0, length = 1))) // one ayah tapped in "Read"
        assertNull(ListenProgress.next(null, null, 114, playing(3)))
    }

    @Test
    fun theSequenceIsDoneWhenItStopsAfterTheLastStep() {
        val last = ListenProgress.next(null, run, 114, playing(29, positionMs = 900, durationMs = 1000))!!
        assertTrue(last.atLastStep)
        // The session is reset right after the end (the player settings are put back): still done.
        val done = ListenProgress.next(last, run, 114, ReaderAudioState())
        assertNotNull(done)
        assertTrue(done.done)
        assertEquals(1f, done.fraction)
        // It stays done until the view model forgets the run.
        assertTrue(ListenProgress.next(done, run, 114, ReaderAudioState())!!.done)
        assertNull(ListenProgress.next(done, null, 114, ReaderAudioState()))
    }

    @Test
    fun stoppedHalfwayOrAfterAnErrorIsNotDone() {
        val middle = ListenProgress.next(null, run, 114, playing(12))!!
        assertNull(ListenProgress.next(middle, run, 114, ReaderAudioState()))
        val last = ListenProgress.next(null, run, 114, playing(29))!!
        assertNull(ListenProgress.next(last, run, 114, ReaderAudioState(hasError = true)))
    }

    @Test
    fun theEchoSilenceAfterTheLastReadingIsNotTheEnd() {
        // During the echo silence the session still plays (waiting) and keeps the position: the same units are counted.
        val silence = playing(29, positionMs = 1000, durationMs = 1000).copy(pauseRemainingSeconds = 3, pauseTotalSeconds = 3)
        val progress = ListenProgress.next(null, run, 114, silence)!!
        assertFalse(progress.done)
        assertEquals(ListenPlace.Passage(2, 2), progress.place)
    }
}
