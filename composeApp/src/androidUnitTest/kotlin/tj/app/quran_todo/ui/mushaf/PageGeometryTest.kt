package tj.app.quran_todo.ui.mushaf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageGeometryTest {
    private fun FloatArray.assertClose(expected: List<Float>, delta: Float = 0.001f) {
        assertEquals(expected.size, size)
        expected.forEachIndexed { index, value -> assertTrue(abs(this[index] - value) <= delta, "[$index] was ${this[index]}, expected $value") }
    }

    @Test
    fun aFullLineFillsTheBlockFromTheRightEdgeToTheLeftEdge() {
        // Three words of 20, 30 and 10 px in a block of 100: the gaps share the other 40 px.
        val xs = PageGeometry.place(floatArrayOf(20f, 30f, 10f), block = 100f, justify = true, centerGap = 5f)
        // Reading order: the first word is at the right, its right edge on the right edge of the block.
        xs.assertClose(listOf(80f, 30f, 0f))
        assertEquals(100f, xs[0] + 20f)
        assertEquals(0f, xs.last())
    }

    @Test
    fun theGapsOfAFullLineAreEqual() {
        val widths = floatArrayOf(40f, 25f, 35f, 20f)
        val xs = PageGeometry.place(widths, block = 200f, justify = true, centerGap = 0f)
        val gaps = (0 until widths.size - 1).map { index -> xs[index] - (xs[index + 1] + widths[index + 1]) }
        assertTrue(gaps.all { abs(it - gaps.first()) < 0.001f }, "$gaps")
        assertTrue(abs(gaps.first() - (200f - widths.sum()) / 3) < 0.001f)
    }

    @Test
    fun aShortLineIsCentredWithTheGapOfThePage() {
        // 20 + 30 + one gap of 10 = 60 px in a block of 100: 20 px free on each side.
        val xs = PageGeometry.place(floatArrayOf(20f, 30f), block = 100f, justify = false, centerGap = 10f)
        xs.assertClose(listOf(60f, 20f))
        val right = xs[0] + 20f
        val left = xs.last()
        assertEquals(100f - right, left, 0.001f)
    }

    @Test
    fun aLineOfOneWordIsAlwaysCentred() {
        val xs = PageGeometry.place(floatArrayOf(40f), block = 100f, justify = true, centerGap = 10f)
        xs.assertClose(listOf(30f))
        assertEquals(0, PageGeometry.place(floatArrayOf(), 100f, true, 5f).size)
    }

    @Test
    fun aLineThatIsWiderThanTheBlockNeverGetsNegativeGaps() {
        val xs = PageGeometry.place(floatArrayOf(60f, 50f), block = 100f, justify = true, centerGap = 0f)
        // The words touch; nothing is squeezed together.
        assertEquals(100f - 60f, xs[0])
        assertEquals(xs[0] - 50f, xs[1])
    }

    @Test
    fun fullLinesAreThoseCloseToTheWidestLineOfThePageAndPagesOneAndTwoHaveNone() {
        assertTrue(PageGeometry.isFullLine(14.5f, 14.6f, page = 3))
        assertTrue(PageGeometry.isFullLine(13.2f, 14.6f, page = 3))
        // The end of a surah is a short line.
        assertTrue(!PageGeometry.isFullLine(7.6f, 14.6f, page = 604))
        assertTrue(!PageGeometry.isFullLine(11.1f, 11.2f, page = 1))
        assertTrue(!PageGeometry.isFullLine(9.6f, 9.7f, page = 2))
    }

    @Test
    fun theGlyphSizeFollowsTheWidthOfTheBlockAndLeavesAMarginOnTheWidestLine() {
        val block = 1000f
        val size = PageGeometry.glyphSize(block)
        // The widest line of the normal pages (15.83 em) fits.
        assertTrue(size * 15.83f < block)
        assertTrue(size * PageGeometry.MAX_LINE_EM <= block)
        assertEquals(PageGeometry.glyphSize(2000f), size * 2, 0.001f)
    }

    @Test
    fun theBlockHasMarginsOnAPhoneAndStopsGrowingOnATablet() {
        // 411 dp at 2.625 px per dp: the screen minus 14 dp on each side.
        val density = 2.625f
        val phone = PageGeometry.blockWidth(411f * density, density)
        assertEquals((411f - 28f) * density, phone, 0.01f)
        // 1280 dp wide: the text stays 520 dp wide, in the middle.
        val tablet = PageGeometry.blockWidth(1280f * density, density)
        assertEquals(520f * density, tablet, 0.01f)
        assertEquals(1f, PageGeometry.blockWidth(0f, density))
    }

    @Test
    fun theLinesAreSpacedByTheBodyWithinLimitsAndAPageThatDoesNotFitScrolls() {
        val glyph = 60f
        // A tall body: 15 lines share it.
        assertEquals(100f, PageGeometry.slotHeight(1500f, glyph))
        // Too tall: the lines stop at 2.3 glyphs and the page is centred.
        assertEquals(60f * 2.3f, PageGeometry.slotHeight(5000f, glyph), 0.001f)
        // A phone held sideways: the lines keep 1.5 glyphs and the page is taller than the body, so it scrolls.
        assertEquals(60f * 1.5f, PageGeometry.slotHeight(300f, glyph), 0.001f)
        assertTrue(15 * PageGeometry.slotHeight(300f, glyph) > 300f)
    }

    @Test
    fun aPageWithEightLinesIsCentredInTheBody() {
        assertEquals(0f, pageTop(height = 900f, slot = 60f, lineCount = 15))
        assertEquals((900f - 480f) / 2f, pageTop(height = 900f, slot = 60f, lineCount = 8))
        // A body shorter than the lines: the top is the top.
        assertEquals(0f, pageTop(height = 300f, slot = 60f, lineCount = 15))
    }
}
