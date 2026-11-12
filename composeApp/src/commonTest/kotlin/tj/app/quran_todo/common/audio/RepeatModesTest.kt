package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepeatModesTest {
    private fun state(ayah: Int = 1, passage: Boolean = false) =
        RepeatState(ayah, ReaderPracticeSettings(rangeEnabled = passage, startAyah = 2, endAyah = 4, rangeRepeatCount = 5))

    @Test
    fun derivesNoneAyahPassage() {
        assertEquals(RepeatMode.None, RepeatModes.derive(state()))
        assertEquals(RepeatMode.Ayah, RepeatModes.derive(state(ayah = 3)))
        assertEquals(RepeatMode.Ayah, RepeatModes.derive(state(ayah = -1)))
        assertEquals(RepeatMode.Passage, RepeatModes.derive(state(passage = true)))
    }

    @Test
    fun passageWinsWhenBothAreActive() {
        assertEquals(RepeatMode.Passage, RepeatModes.derive(state(ayah = 3, passage = true)))
    }

    @Test
    fun selectingNoneSwitchesBothOff() {
        val next = RepeatModes.select(state(ayah = 3, passage = true), RepeatMode.None)
        assertEquals(1, next.ayahRepeat)
        assertFalse(next.practice.rangeEnabled)
        assertEquals(RepeatMode.None, RepeatModes.derive(next))
    }

    @Test
    fun selectingAyahKeepsCountOrDefaultsAndStopsPassage() {
        val kept = RepeatModes.select(state(ayah = 5, passage = true), RepeatMode.Ayah)
        assertEquals(5, kept.ayahRepeat)
        assertFalse(kept.practice.rangeEnabled)
        val fresh = RepeatModes.select(state(passage = true), RepeatMode.Ayah)
        assertEquals(RepeatModes.DefaultAyahCount, fresh.ayahRepeat)
        assertEquals(RepeatMode.Ayah, RepeatModes.derive(fresh))
    }

    @Test
    fun selectingPassageClearsAyahRepeatAndKeepsThePassage() {
        val next = RepeatModes.select(state(ayah = 3), RepeatMode.Passage)
        assertEquals(1, next.ayahRepeat)
        val withPassage = RepeatModes.select(state(passage = true), RepeatMode.Passage)
        assertTrue(withPassage.practice.rangeEnabled)
        assertEquals(2, withPassage.practice.startAyah)
    }

    @Test
    fun ayahCountSwitchesPassageOff() {
        val next = RepeatModes.withAyahCount(state(passage = true), 3)
        assertEquals(3, next.ayahRepeat)
        assertFalse(next.practice.rangeEnabled)
    }

    @Test
    fun ayahSheetActionShowsAsPassageOfOneAyah() {
        val next = RepeatModes.withPassage(state(ayah = 5), from = 7, to = 7, count = 3)
        assertEquals(1, next.ayahRepeat)
        assertTrue(next.practice.rangeEnabled)
        assertEquals(7, next.practice.startAyah)
        assertEquals(7, next.practice.endAyah)
        assertEquals(3, next.practice.rangeRepeatCount)
        assertEquals(RepeatMode.Passage, RepeatModes.derive(next))
    }
}
