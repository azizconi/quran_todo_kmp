package tj.app.quran_todo.common.mushaf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.data.bundled.QuranFacts

/**
 * The rule of the headers and the Bismillahs ([MushafLayoutRules]) against the shape of all 604 pages of quran.com (which lines hold
 * words, which surahs start where; `page_shapes.txt`, made from the answers of 2026-10-31).
 */
class MushafLayoutRulesTest {
    /** The words of a page as the shape says: one placeholder word per line, the first word of a surah's first ayah where it starts. */
    private fun wordLines(shape: MushafFixtures.Shape): Map<Int, List<MushafWord>> {
        val startOfLine = shape.starts.entries.associate { (surah, line) -> line to surah }
        return shape.lines.associateWith { line ->
            val surah = startOfLine[line]
            if (surah != null) {
                listOf(MushafWord(surah, 1, 1, "x", isAyahEnd = false))
            } else {
                listOf(MushafWord(2, 2, 2, "x", isAyahEnd = false))
            }
        }
    }

    @Test
    fun theShapesCoverAll604Pages() {
        assertEquals((1..604).toList(), MushafFixtures.shapes.map { it.page })
    }

    @Test
    fun everyPlaceOfEveryPageIsFilledExactlyOnceByAWordLineAHeaderOrABismillah() {
        for (shape in MushafFixtures.shapes) {
            val lines = MushafLayoutRules.arrange(shape.page, wordLines(shape))
            val count = MushafLayoutRules.lineCount(shape.page)
            assertEquals((1..count).toList(), lines.map { it.number }, "page ${shape.page}: the places of the lines")
        }
    }

    @Test
    fun theHeaderAndTheBismillahStandDirectlyAboveTheFirstAyahOfASurah() {
        for (shape in MushafFixtures.shapes) {
            val lines = MushafLayoutRules.arrange(shape.page, wordLines(shape)).associateBy { it.number }
            for ((surah, line) in shape.starts) {
                val headerAbove = lines[line - 1]
                val twoAbove = lines[line - 2]
                when {
                    !MushafLayoutRules.hasBismillahLine(surah) -> {
                        assertEquals(MushafLine.SurahHeader(line - 1, surah), headerAbove, "page ${shape.page}, surah $surah")
                    }
                    MushafLayoutRules.headerAtBottom[shape.page - 1] == surah -> {
                        // The exception: the header is on the last line of the previous page, here is the Bismillah only.
                        assertEquals(2, line, "page ${shape.page}: an exception starts on the second line")
                        assertEquals(MushafLine.Bismillah(1, surah), headerAbove, "page ${shape.page}, surah $surah")
                    }
                    else -> {
                        assertEquals(MushafLine.SurahHeader(line - 2, surah), twoAbove, "page ${shape.page}, surah $surah")
                        assertEquals(MushafLine.Bismillah(line - 1, surah), headerAbove, "page ${shape.page}, surah $surah")
                    }
                }
            }
        }
    }

    @Test
    fun theTableOfExceptionsIsExactlyThePagesWhoseLastLineIsEmptyAndWhoseNextPageStartsASurahOnItsSecondLine() {
        val derived = buildMap {
            for (shape in MushafFixtures.shapes) {
                if (shape.page >= 604) continue
                val count = MushafLayoutRules.lineCount(shape.page)
                val next = MushafFixtures.shapes[shape.page]
                val surahOnSecondLine = next.starts.entries.firstOrNull { it.value == 2 && it.key != 1 && it.key != 9 }?.key
                // The last line of the page is empty and its last words close a surah: the header of the next surah stands there.
                val lastLineEmpty = count !in shape.lines
                val closesSurah = shape.lastIsEnd && shape.lastAyah == QuranFacts.ayahCount(shape.lastSurah)
                if (lastLineEmpty && closesSurah && surahOnSecondLine != null && surahOnSecondLine == shape.lastSurah + 1) {
                    put(shape.page, surahOnSecondLine)
                }
            }
        }
        assertEquals(derived, MushafLayoutRules.headerAtBottom)
        assertEquals(21, MushafLayoutRules.headerAtBottom.size)
    }

    @Test
    fun noPageHasAnEmptyPlaceThatTheRuleDoesNotExplain() {
        // Count the empty places of every page and what the rule puts in them: nothing is left over, nothing is missing.
        var headers = 0
        var bismillahs = 0
        for (shape in MushafFixtures.shapes) {
            val count = MushafLayoutRules.lineCount(shape.page)
            val empty = (1..count).filter { it !in shape.lines }
            val lines = MushafLayoutRules.arrange(shape.page, wordLines(shape))
            val filled = lines.filter { it !is MushafLine.Words }.map { it.number }
            assertEquals(empty, filled, "page ${shape.page}")
            headers += lines.count { it is MushafLine.SurahHeader }
            bismillahs += lines.count { it is MushafLine.Bismillah }
        }
        // 114 surahs: 114 headers and 112 Bismillah lines (not Al-Fatihah, not At-Tawbah).
        assertEquals(114, headers)
        assertEquals(112, bismillahs)
    }

    @Test
    fun theFirstTwoPagesHaveEightLinesTheOthersFifteen() {
        assertEquals(8, MushafLayoutRules.lineCount(1))
        assertEquals(8, MushafLayoutRules.lineCount(2))
        assertEquals(15, MushafLayoutRules.lineCount(3))
        assertEquals(15, MushafLayoutRules.lineCount(604))
        assertTrue(MushafLayoutRules.hasBismillahLine(2))
        assertTrue(!MushafLayoutRules.hasBismillahLine(1))
        assertTrue(!MushafLayoutRules.hasBismillahLine(9))
    }

    @Test
    fun theStarOfTheRubElHizbIsCutOffTheFirstWordOfExactlyTheAyahsThatHaveItInTheText() {
        val ayahs = tj.app.quran_todo.data.bundled.QuranTsvParser.parse(tj.app.quran_todo.data.BundledFiles.quranTsv()).ayahs
        var cut = 0
        for (ayah in ayahs) {
            val hasSign = '\u06DE' in ayah.text
            // Two glyphs: the sign and a word (7:47 and the like), or the sign, a space and a word (the other 195).
            for (glyph in listOf("AB", "A B")) {
                val result = MushafLayoutRules.withoutRubSign(ayah.surahNumber, ayah.numberInSurah, 1, glyph)
                assertEquals(if (hasSign) "B" else glyph, result, "${ayah.surahNumber}:${ayah.numberInSurah} $glyph")
            }
            if (hasSign) cut++
            // Only the first word carries it.
            assertEquals("AB", MushafLayoutRules.withoutRubSign(ayah.surahNumber, ayah.numberInSurah, 2, "AB"))
        }
        assertEquals(199, cut)
        // A word of one glyph is never cut to nothing.
        assertEquals("A", MushafLayoutRules.withoutRubSign(2, 26, 1, "A"))
    }
}
