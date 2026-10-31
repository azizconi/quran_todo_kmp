package tj.app.quran_todo.ui.mushaf

import tj.app.quran_todo.common.mushaf.MushafLayoutRules

/**
 * The arithmetic of a mushaf page, apart from any drawing so that it can be tested.
 *
 * **One glyph size, wider gaps on some pages.** The fonts of the pages (KFGQPC, one font per page) are drawn so that every full line of a
 * page has the same width, but the width differs from page to page. Measured on the fonts of all 604 pages (2026-10-31, 100 px, the
 * advances of the glyphs and the 0.045 em spaces of the font): the widest line of a page is 14.3 to 15.2 em on 90% of the pages (median
 * 14.6 em) and 15.83 em on the densest of the other pages (page 177). So the glyph size is the width of the block divided by
 * [MAX_LINE_EM], the same on every page, and on a page with narrower lines the words of a full line stand 0.1 to 0.3 em further apart (the
 * width of an ordinary word space; the fonts leave 0.045 em) so that the lines still fill the block.
 *
 * **Two exceptions** whose fonts are drawn for a smaller size: page 565 (its widest line is 20.69 em) and page 443 (17.54 em). A glyph
 * of the common size would not fit them, so [buildPageLayout] draws these two pages smaller, as wide as the block allows (x 0.77 and
 * x 0.91); every other page has the common size.
 */
internal object PageGeometry {
    /**
     * The widest natural line of the 602 pages that are not an exception, in em: the sum of the advances of its glyphs and of the spaces
     * between them (15.83 em on page 177), measured with the fonts of the pages (task F3a, 2026-10-31). The glyph size of every page is
     * the width of the block divided by this number.
     */
    const val MAX_LINE_EM = 15.85f

    /** The glyph size leaves this much of the block free on the widest line, so that rounding never pushes a word out of it. */
    const val FIT_MARGIN = 1.015f

    /** The space the fonts of the pages put between two words, in em (they are drawn nearly touching). */
    const val NATURAL_GAP_EM = 0.045f

    /** The gap of a centred line, in em, when the page has no full line to take it from (pages 1 and 2). */
    const val CENTER_GAP_EM = 0.2f

    /** A line is a full line (its words are spread over the block) when it is at least this share of the widest line of its page. */
    const val FULL_LINE_SHARE = 0.9f

    /** The page is at most this wide on a tablet or in landscape (dp): the text stays in the middle with margins. */
    const val MAX_BLOCK_DP = 520f

    /** The side margin of the block on a phone (dp). */
    const val SIDE_MARGIN_DP = 14f

    /** The height of a line in glyph sizes: the least the lines are spaced by (a page that does not fit scrolls) and the most. */
    const val MIN_SLOT = 1.5f
    const val MAX_SLOT = 2.3f

    /** The glyph size (px) for a block [blockWidth] px wide. */
    fun glyphSize(blockWidth: Float): Float = blockWidth / (MAX_LINE_EM * FIT_MARGIN)

    /** The width of the block (px): the width of the screen without margins, at most [MAX_BLOCK_DP]. */
    fun blockWidth(screenWidth: Float, density: Float): Float =
        (screenWidth - 2 * SIDE_MARGIN_DP * density).coerceAtMost(MAX_BLOCK_DP * density).coerceAtLeast(1f)

    /** The height of one line place (px) for a body [bodyHeight] px high and glyphs [glyphSize] px big. */
    fun slotHeight(bodyHeight: Float, glyphSize: Float, lineCount: Int = MushafLayoutRules.FULL_LINES): Float =
        (bodyHeight / lineCount).coerceIn(MIN_SLOT * glyphSize, MAX_SLOT * glyphSize)

    /**
     * The left edges of the words of one line, in reading order (the first word is at the right), in a block [block] px wide.
     *
     * A line that [justify]ies is spread over the whole block: the words keep their widths and the gaps between them are equal. Any
     * other line is centred with the gap [centerGap] between words. A line of one word is always centred.
     */
    fun place(widths: FloatArray, block: Float, justify: Boolean, centerGap: Float): FloatArray {
        val n = widths.size
        val xs = FloatArray(n)
        if (n == 0) return xs
        val sum = widths.sum()
        val gap: Float
        var right: Float
        if (justify && n > 1) {
            gap = ((block - sum) / (n - 1)).coerceAtLeast(0f)
            right = block
        } else {
            gap = centerGap
            val total = sum + gap * (n - 1)
            right = block - (block - total) / 2f
        }
        for (index in 0 until n) {
            xs[index] = right - widths[index]
            right = xs[index] - gap
        }
        return xs
    }

    /** Whether a line of natural width [natural] is a full line on a page whose widest line is [pageMax] (pages 1 and 2 have none). */
    fun isFullLine(natural: Float, pageMax: Float, page: Int): Boolean =
        page > MushafLayoutRules.SHORT_PAGES && natural >= pageMax * FULL_LINE_SHARE
}
