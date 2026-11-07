package tj.app.quran_todo.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerUiTest {
    private fun ui(
        active: Boolean = false,
        index: Int = 4,
        isPlaying: Boolean = false,
        canResume: Boolean = false,
        isPreparing: Boolean = false,
        selected: Boolean = false,
    ) = PlayerUi(
        active = active,
        index = index,
        isPlaying = isPlaying,
        canResume = canResume,
        isPreparing = isPreparing,
        hasError = false,
        selected = selected,
    )

    @Test
    fun nothingIsMarkedUntilTheAudioPlaysOrAnAyahIsPicked() {
        assertNull(ui().highlightIndex(highlightPlaying = true))
        assertNull(ui(active = true).highlightIndex(highlightPlaying = true))
    }

    @Test
    fun aPickedAyahIsMarkedEvenWithoutAudio() {
        assertEquals(4, ui(selected = true).highlightIndex(highlightPlaying = true))
        assertEquals(4, ui(active = true, selected = true).highlightIndex(highlightPlaying = false))
    }

    @Test
    fun thePlayingAyahIsMarkedWhenTheSettingIsOn() {
        assertEquals(4, ui(active = true, isPlaying = true).highlightIndex(highlightPlaying = true))
        assertEquals(4, ui(active = true, canResume = true).highlightIndex(highlightPlaying = true))
        assertEquals(4, ui(active = true, isPreparing = true).highlightIndex(highlightPlaying = true))
    }

    @Test
    fun thePlayingAyahIsNotMarkedWhenTheSettingIsOff() {
        assertNull(ui(active = true, isPlaying = true, selected = true).highlightIndex(highlightPlaying = false))
    }

    @Test
    fun audioOfAnotherSurahNeverCountsAsRunningHere() {
        // Not active: the flags that describe the session are not meaningful for this reader.
        assertFalse(ui(active = false, isPlaying = true).running)
        assertTrue(ui(active = true, isPlaying = true).running)
        assertTrue(ui(active = true, canResume = true).running)
        assertFalse(ui(active = true).running)
    }
}
