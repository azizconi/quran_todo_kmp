package tj.app.quran_todo.common.voice

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SilenceSplitterTest {
    private fun tone(seconds: Double) = FloatArray((seconds * 16_000).toInt()) { (0.4 * sin(2 * PI * 220 * it / 16_000)).toFloat() }
    private fun silence(seconds: Double) = FloatArray((seconds * 16_000).toInt())

    @Test
    fun cutsInTheMiddleOfALongPause() {
        val audio = tone(3.0) + silence(0.5) + tone(3.0)
        val pieces = SilenceSplitter.split(audio)
        assertEquals(2, pieces.size)
        val cut = pieces[0].last + 1
        assertTrue(cut in 3 * 16_000..(3.5 * 16_000).toInt(), "cut at $cut")
        assertEquals(cut, pieces[1].first)
        assertEquals(audio.size / SilenceSplitter.FrameSize * SilenceSplitter.FrameSize, pieces[1].last + 1)
    }

    @Test
    fun aShortPauseIsNotACut() {
        val audio = tone(3.0) + silence(0.1) + tone(3.0)
        assertEquals(1, SilenceSplitter.split(audio).size)
    }

    @Test
    fun aShortPieceIsJoinedToTheNext() {
        val audio = tone(1.0) + silence(0.5) + tone(3.0)
        assertEquals(1, SilenceSplitter.split(audio).size)
    }

    @Test
    fun noPausesIsOnePiece() {
        assertEquals(1, SilenceSplitter.split(tone(5.0)).size)
    }
}
