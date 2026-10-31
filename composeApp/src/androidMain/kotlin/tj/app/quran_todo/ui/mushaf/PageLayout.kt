package tj.app.quran_todo.ui.mushaf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.mushaf.MushafLayoutRules
import tj.app.quran_todo.common.mushaf.MushafLine
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.common.mushaf.MushafWord
import tj.app.quran_todo.ui.reader.BismillahText

/** A word with its glyph measured and its place in the block: [x] is its left edge, [width] its advance. */
internal class PlacedWord(
    val word: MushafWord,
    val lineNumber: Int,
    val layout: TextLayoutResult,
    val x: Float,
    val width: Float,
)

/** A text that stands alone on its line, centred: the name in a header, the Bismillah. */
internal class PlacedText(val lineNumber: Int, val layout: TextLayoutResult, val x: Float, val width: Float)

/** The span of one ayah on one line: where its first and last word are, for the underlay and for the bars of a hidden ayah. */
internal class AyahSpan(val key: AyahKey, val lineNumber: Int, val left: Float, val right: Float)

/**
 * Everything horizontal about a page, worked out once for a page, a block width and a glyph size: the measured glyph of every word
 * and its x, the names of the headers, the Bismillahs. It does not know the height of a line: the lines are placed by the screen when it
 * draws (so a changing height, the mini player sliding in, never measures a text again).
 */
internal class PageLayout(
    val page: MushafPage,
    val blockWidth: Float,
    val glyphSize: Float,
    val words: List<PlacedWord>,
    val headers: List<PlacedText>,
    val bismillahs: List<PlacedText>,
) {
    private val byLine: Map<Int, List<PlacedWord>> = words.groupBy { it.lineNumber }

    /** Where each ayah is on each line, in reading order. */
    val spans: List<AyahSpan> = buildList {
        for ((number, lineWords) in byLine.toSortedMap()) {
            var current: AyahKey? = null
            var left = 0f
            var right = 0f
            for (placed in lineWords) {
                val key = placed.word.key
                if (key != current) {
                    if (current != null) add(AyahSpan(current, number, left, right))
                    current = key
                    left = placed.x
                    right = placed.x + placed.width
                } else {
                    left = minOf(left, placed.x)
                    right = maxOf(right, placed.x + placed.width)
                }
            }
            if (current != null) add(AyahSpan(current, number, left, right))
        }
    }

    /** The spans of [key] (an ayah is on one to five lines). */
    fun spansOf(key: AyahKey): List<AyahSpan> = spans.filter { it.key == key }

    /** The baseline of the glyphs of the page font (all words of one page share it). */
    val baseline: Float = words.firstOrNull()?.layout?.firstBaseline ?: 0f

    /**
     * The ayah under the point ([x], [y]) in block coordinates, or null for the space between the lines, a header and the margin. The
     * line is found by [lineNumber] (the screen knows where lines are); a tap between two words of a line takes the nearer word, so the
     * thin gaps of the fonts do not make a dead zone.
     */
    fun ayahAt(x: Float, lineNumber: Int): AyahKey? {
        val lineWords = byLine[lineNumber] ?: return null
        var best: PlacedWord? = null
        var bestDistance = Float.MAX_VALUE
        for (placed in lineWords) {
            val distance = when {
                x < placed.x -> placed.x - x
                x > placed.x + placed.width -> x - (placed.x + placed.width)
                else -> 0f
            }
            if (distance < bestDistance) {
                best = placed
                bestDistance = distance
            }
        }
        // Nothing within a word width of the tap: the margin, not a word.
        return best?.takeIf { bestDistance <= glyphSize * 0.6f }?.word?.key
    }

    /** The place of a line in a block whose lines are [slot] px high, from [top]: the rectangle that holds the line. */
    fun lineRect(lineNumber: Int, top: Float, slot: Float): Rect =
        Rect(0f, top + (lineNumber - 1) * slot, blockWidth, top + lineNumber * slot)
}

/**
 * Measures [page] with its font [pageFont] at [glyphSize] px in a block [blockWidth] px wide and places the words of every line:
 * the full lines are spread over the block, the short ones (the end of a surah) and the lines of pages 1 and 2 are centred
 * ([PageGeometry]). [arabicName] gives the name written in a header; [hafs] is the font of the headers and the Bismillah.
 *
 * **The reading font of the settings.** With [textFont] (any font but the Madinah one, whose design the page fonts are) a word is
 * the text [wordText] gives for it, drawn in that font; the lines of the page stay as they are, only the glyphs change. A word
 * without a text (an ayah whose words do not match the layout) and the sign that closes an ayah keep the glyph of the page font.
 * The widths of another font are not those of the page fonts, so the size of the page is then fitted to its widest line.
 *
 * Pure of the composition: the caller may run it on any thread (it makes its own [TextMeasurer]).
 */
internal fun buildPageLayout(
    page: MushafPage,
    pageFont: FontFamily,
    hafs: FontFamily,
    glyphSize: Float,
    blockWidth: Float,
    arabicName: (Int) -> String,
    density: Density,
    resolver: FontFamily.Resolver,
    textFont: FontFamily? = null,
    wordText: (MushafWord) -> String? = { null },
    fitted: Boolean = false,
): PageLayout {
    val measurer = TextMeasurer(resolver, density, LayoutDirection.Ltr, 0)
    val glyphStyle = TextStyle(fontFamily = pageFont, fontSize = with(density) { glyphSize.toSp() })
    val textStyle = textFont?.let { TextStyle(fontFamily = it, fontSize = with(density) { (glyphSize * TEXT_FONT_SCALE).toSp() }) }
    val unbounded = Constraints()

    val glyphs = HashMap<String, TextLayoutResult>()
    val texts = HashMap<String, TextLayoutResult>()
    fun measureWord(word: MushafWord): TextLayoutResult {
        val text = if (textStyle != null && !word.isAyahEnd) wordText(word) else null
        return if (text != null) {
            texts.getOrPut(text) { measurer.measure(text, textStyle!!, softWrap = false, maxLines = 1, constraints = unbounded) }
        } else {
            glyphs.getOrPut(word.glyph) { measurer.measure(word.glyph, glyphStyle, softWrap = false, maxLines = 1, constraints = unbounded) }
        }
    }

    // First the widths of every line, to know the widest one of the page and the gap the full lines have.
    class Measured(val line: MushafLine.Words, val layouts: List<TextLayoutResult>, val widths: FloatArray, val natural: Float)

    // The page fonts carry their own spacing in the glyphs; the words of a text font need a real word space between them.
    val naturalGap = (if (textStyle != null) TEXT_GAP_EM else PageGeometry.NATURAL_GAP_EM) * glyphSize
    val measured = page.wordLines.map { line ->
        val layouts = line.words.map(::measureWord)
        val widths = FloatArray(layouts.size) { layouts[it].size.width.toFloat() }
        Measured(line, layouts, widths, widths.sum() + naturalGap * (widths.size - 1).coerceAtLeast(0))
    }
    val pageMax = measured.maxOfOrNull { it.natural } ?: 0f
    if (textStyle != null && !fitted && pageMax > 0f && texts.isNotEmpty()) {
        // Another font: the page takes the size at which its widest line fills the block (within reason, so a sparse page is not huge).
        // A tall font (the Nastaliq) must also keep within the height the page glyphs take, or its lines run into each other.
        val textHeight = texts.values.maxOf { it.size.height }.toFloat()
        val heightFit = if (textHeight > 0f) glyphSize * TEXT_LINE_EM / textHeight else MAX_TEXT_FIT
        val fit = minOf(blockWidth / pageMax * SHRINK_MARGIN, heightFit).coerceIn(MIN_TEXT_FIT, MAX_TEXT_FIT)
        return buildPageLayout(
            page, pageFont, hafs, glyphSize * fit, blockWidth, arabicName, density, resolver, textFont, wordText, fitted = true,
        )
    }
    if (pageMax > blockWidth && pageMax > 0f && glyphSize > 1f) {
        // A page whose font is drawn for a smaller size (565 and 443): the glyphs shrink until its widest line fits the block.
        val shrunk = glyphSize * (blockWidth / pageMax) * SHRINK_MARGIN
        return buildPageLayout(page, pageFont, hafs, shrunk, blockWidth, arabicName, density, resolver, textFont, wordText, fitted = true)
    }

    // The words of another font are not as even in width as the glyphs drawn for this page, so a full line of it may be well short of the widest one.
    fun isFull(natural: Float) = if (textStyle == null) {
        PageGeometry.isFullLine(natural, pageMax, page.number)
    } else {
        page.number > MushafLayoutRules.SHORT_PAGES && natural >= pageMax * TEXT_FULL_LINE_SHARE
    }

    // The gap of the full lines on this page: the centred lines of the page use the same one, so the spacing does not change in the page.
    val fullGaps = measured.filter { isFull(it.natural) && it.widths.size > 1 }
        .map { (blockWidth - it.widths.sum()) / (it.widths.size - 1) }
    val centerGap = if (fullGaps.isEmpty()) {
        PageGeometry.CENTER_GAP_EM * glyphSize
    } else {
        fullGaps.average().toFloat().coerceIn(naturalGap, PageGeometry.CENTER_GAP_EM * glyphSize * 2)
    }

    val placed = ArrayList<PlacedWord>()
    for (item in measured) {
        val full = isFull(item.natural)
        val xs = PageGeometry.place(item.widths, blockWidth, justify = full, centerGap = centerGap)
        item.line.words.forEachIndexed { index, word ->
            placed += PlacedWord(word, item.line.number, item.layouts[index], xs[index], item.widths[index])
        }
    }

    val headerStyle = TextStyle(fontFamily = hafs, fontSize = with(density) { (glyphSize * HEADER_NAME_SCALE).toSp() })
    val bismillahStyle = TextStyle(fontFamily = hafs, fontSize = with(density) { (glyphSize * BISMILLAH_SCALE).toSp() })
    val headers = ArrayList<PlacedText>()
    val bismillahs = ArrayList<PlacedText>()
    for (line in page.lines) {
        when (line) {
            is MushafLine.SurahHeader -> {
                val layout = measurer.measure(arabicName(line.surah), headerStyle, softWrap = false, maxLines = 1, constraints = unbounded)
                headers += PlacedText(line.number, layout, (blockWidth - layout.size.width) / 2f, layout.size.width.toFloat())
            }
            is MushafLine.Bismillah -> {
                val layout = measurer.measure(BismillahText, bismillahStyle, softWrap = false, maxLines = 1, constraints = unbounded)
                bismillahs += PlacedText(line.number, layout, (blockWidth - layout.size.width) / 2f, layout.size.width.toFloat())
            }
            is MushafLine.Words -> Unit
        }
    }
    return PageLayout(page, blockWidth, glyphSize, placed, headers, bismillahs)
}

/** A page that is too wide is drawn this much smaller than exactly fitting, so that the rounding of the widths never pushes a word out. */
private const val SHRINK_MARGIN = 0.995f

/** A text font is drawn a little smaller than the glyphs of the page fonts before the page is fitted to the block. */
private const val TEXT_FONT_SCALE = 0.8f

/** The least gap between the words of a text font, in the size of the page glyphs. */
private const val TEXT_GAP_EM = 0.22f

/** A line in another font at least this share of the widest one is justified; a shorter one (the end of a surah) is centred. */
private const val TEXT_FULL_LINE_SHARE = 0.7f

/** How far fitting a page in another font may grow or shrink it: a page of a few short lines must not become huge. */
private const val MIN_TEXT_FIT = 0.6f
private const val MAX_TEXT_FIT = 1.3f

/** The tallest line box of a text font, in the size of the page glyphs: about the height of the line box of the page fonts. */
private const val TEXT_LINE_EM = 1.9f

/** The name in a surah header and the Bismillah are a little smaller than the glyphs of the page (their font is the plain Hafs). */
private const val HEADER_NAME_SCALE = 0.95f
private const val BISMILLAH_SCALE = 0.95f
