package tj.app.quran_todo.common.mushaf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MushafLayoutFormatTest {
    private fun parse(page: Int): MushafPage =
        assertNotNull(MushafLayoutFormat.parseQuranCom(MushafFixtures.answer(page), page), "page $page must be readable")

    private fun MushafPage.kinds(): List<String> = lines.map { line ->
        when (line) {
            is MushafLine.Words -> "w${line.number}"
            is MushafLine.SurahHeader -> "h${line.number}:${line.surah}"
            is MushafLine.Bismillah -> "b${line.number}:${line.surah}"
        }
    }

    @Test
    fun alFatihaHasAHeaderAndSevenLinesAndNoBismillahLine() {
        val page = parse(1)
        // Eight lines; line 1 is the header, the Bismillah is the first ayah itself.
        assertEquals(listOf("h1:1", "w2", "w3", "w4", "w5", "w6", "w7", "w8"), page.kinds())
        assertEquals(8, page.lineCount)
        assertEquals((1..7).map { AyahKey(1, it) }, page.ayahs)
        assertEquals(listOf(SurahPart(1, 1, 7)), page.parts)
        assertEquals(AyahKey(1, 1), page.firstAyah)
    }

    @Test
    fun theFirstPageOfAlBaqaraHasTheHeaderAndTheBismillahAboveTheFirstAyah() {
        val page = parse(2)
        assertEquals(listOf("h1:2", "b2:2", "w3", "w4", "w5", "w6", "w7", "w8"), page.kinds())
        assertEquals(8, page.lineCount)
        assertEquals(listOf(SurahPart(2, 1, 5)), page.parts)
    }

    @Test
    fun aPageInTheMiddleOfASurahHasFifteenLinesOfWords() {
        val page = parse(3)
        assertEquals((1..15).map { "w$it" }, page.kinds())
        assertEquals(15, page.lineCount)
        assertEquals(AyahKey(2, 6), page.firstAyah)
        assertEquals(listOf(SurahPart(2, 6, 16)), page.parts)
    }

    @Test
    fun theWordsOfAnAyahCountFromOneAndEndWithTheSignOfTheEnd() {
        val page = parse(3)
        val words = page.wordLines.flatMap { it.words }
        val ayah6 = words.filter { it.ayah == 6 }
        assertEquals((1..ayah6.size).toList(), ayah6.map { it.position })
        assertTrue(ayah6.last().isAyahEnd)
        assertEquals(1, ayah6.count { it.isAyahEnd })
        // The glyph is what the font of the page draws, a private-use character (U+E000..U+F8FF, or the presentation forms of the font).
        assertTrue(words.all { it.glyph.isNotEmpty() })
        // The two ayahs of a line share it in reading order: ayah 6 closes on line 2, ayah 7 begins there.
        val line2 = page.wordLines.first { it.number == 2 }.words
        assertEquals(listOf(6, 7), line2.map { it.ayah }.distinct())
    }

    @Test
    fun aSurahThatStartsAPageGetsItsHeaderAndBismillahAboveItsFirstAyah() {
        // Page 50: Aal Imran starts on line 3, so lines 1 and 2 above it hold the header and the Bismillah of the surah.
        val page = parse(50)
        assertEquals(listOf("h1:3", "b2:3") + (3..15).map { "w$it" }, page.kinds())
        assertEquals(listOf(SurahPart(3, 1, 9)), page.parts)
    }

    @Test
    fun page604HasThreeSurahsEachWithItsHeaderAndBismillah() {
        val page = parse(604)
        assertEquals(
            listOf("h1:112", "b2:112", "w3", "w4", "h5:113", "b6:113", "w7", "w8", "w9", "h10:114", "b11:114", "w12", "w13", "w14", "w15"),
            page.kinds(),
        )
        assertEquals(listOf(SurahPart(112, 1, 4), SurahPart(113, 1, 5), SurahPart(114, 1, 6)), page.parts)
        assertEquals(112, page.firstSurah)
    }

    @Test
    fun atTawbahHasAHeaderOnlyBecauseItHasNoBismillah() {
        val page = parse(187)
        assertEquals("h1:9", page.kinds().first())
        assertEquals("w2", page.kinds()[1])
        assertEquals(1, page.lines.count { it is MushafLine.SurahHeader })
        assertEquals(0, page.lines.count { it is MushafLine.Bismillah })
    }

    @Test
    fun aHeaderThatStandsOnTheLastLineOfThePreviousPageIsFoundThere() {
        // The exception of the rule: An-Nisa starts on line 2 of page 77, its header is on line 15 of page 76.
        val before = parse(76)
        assertEquals(15, before.lineCount)
        val last = before.lines.last()
        assertEquals(MushafLine.SurahHeader(15, 4), last)
        assertEquals((1..14).map { "w$it" } + "h15:4", before.kinds())

        val after = parse(77)
        assertEquals(listOf("b1:4"), after.kinds().take(1))
        assertEquals(0, after.lines.count { it is MushafLine.SurahHeader })
        assertEquals("w2", after.kinds()[1])
    }

    @Test
    fun theExceptionAlsoHoldsWhenTwoSurahsStandOnOnePage() {
        val before = parse(586)
        // Al-Takwir starts at line 3 (header 1, Bismillah 2); the header of Al-Infitar is on the last line.
        assertEquals(listOf("h1:81", "b2:81"), before.kinds().take(2))
        assertEquals("h15:82", before.kinds().last())

        val after = parse(587)
        assertEquals(listOf("b1:82"), after.kinds().take(1))
        // Al-Mutaffifin starts on line 13: header 11, Bismillah 12.
        assertTrue("h11:83" in after.kinds() && "b12:83" in after.kinds())
        assertEquals(listOf(SurahPart(82, 1, 19), SurahPart(83, 1, 6)), after.parts)
    }

    @Test
    fun everyLineOfEveryFixtureHasAPlaceOnThePage() {
        for (number in listOf(1, 2, 3, 50, 76, 77, 187, 586, 587, 604)) {
            val page = parse(number)
            assertTrue(page.lines.all { it.number in 1..page.lineCount }, "page $number")
            assertEquals(page.lines.map { it.number }.distinct(), page.lines.map { it.number }, "page $number: a place is used twice")
            assertEquals(page.lines.map { it.number }.sorted(), page.lines.map { it.number }, "page $number: lines are not in order")
        }
    }

    @Test
    fun theCompactCopyRoundTrips() {
        for (number in listOf(1, 2, 3, 50, 76, 77, 187, 586, 587, 604)) {
            val page = parse(number)
            val copy = MushafLayoutFormat.encode(page)
            assertEquals(page, MushafLayoutFormat.decode(copy, number), "page $number")
            // A copy is small: the raw answer of quran.com is ten times bigger.
            assertTrue(copy.length < MushafFixtures.answer(number).length, "page $number: ${copy.length}")
        }
    }

    @Test
    fun aCopyOfAnotherPageOrVersionOrAnythingElseIsRefused() {
        val copy = MushafLayoutFormat.encode(parse(3))
        assertNull(MushafLayoutFormat.decode(copy, 4), "a copy of another page")
        assertNull(MushafLayoutFormat.decode(copy.replace("\"v\":2", "\"v\":3"), 3), "another version")
        assertNull(MushafLayoutFormat.decode("", 3))
        assertNull(MushafLayoutFormat.decode("{}", 3))
        assertNull(MushafLayoutFormat.decode("[]", 3))
        assertNull(MushafLayoutFormat.decode("not json", 3))
        assertNull(MushafLayoutFormat.decode(copy.take(copy.length / 2), 3), "a half-written copy")
        assertNull(MushafLayoutFormat.decode("""{"v":2,"p":3,"l":[]}""", 3), "a page without words")
        assertNull(MushafLayoutFormat.decode("""{"v":2,"p":3,"l":[["w",99,[2,6,1,"x",0]]]}""", 3), "a line that is not on the page")
        assertNull(MushafLayoutFormat.decode("""{"v":2,"p":3,"l":[["q",1,2]]}""", 3), "an unknown kind of line")
    }

    @Test
    fun anAnswerThatIsNotAPageOfWordsIsNotUsed() {
        assertNull(MushafLayoutFormat.parseQuranCom("", 3))
        assertNull(MushafLayoutFormat.parseQuranCom("{}", 3))
        assertNull(MushafLayoutFormat.parseQuranCom("""{"verses":[]}""", 3), "no verses")
        assertNull(MushafLayoutFormat.parseQuranCom("""<html>Too many requests</html>""", 3))
        // A word without its glyph or its line would be drawn as a hole: the whole answer is refused.
        val noGlyph = """{"verses":[{"verse_key":"2:6","words":[{"position":1,"char_type_name":"word","line_number":1,"page_number":3}]}]}"""
        assertNull(MushafLayoutFormat.parseQuranCom(noGlyph, 3))
        val noLine = """{"verses":[{"verse_key":"2:6","words":[{"position":1,"char_type_name":"word","code_v1":"x","page_number":3}]}]}"""
        assertNull(MushafLayoutFormat.parseQuranCom(noLine, 3))
        val badLine = """{"verses":[{"verse_key":"2:6","words":[{"position":1,"char_type_name":"word","code_v1":"x","line_number":16,"page_number":3}]}]}"""
        assertNull(MushafLayoutFormat.parseQuranCom(badLine, 3), "line 16 does not exist")
        assertNull(MushafLayoutFormat.parseQuranCom(badLine.replace("16", "9"), 1), "page 1 has eight lines")
        val badKey = """{"verses":[{"verse_key":"x","words":[]}]}"""
        assertNull(MushafLayoutFormat.parseQuranCom(badKey, 3))
    }

    @Test
    fun wordsOfAnotherTypeOrAnotherPageAreSkipped() {
        // A rub el hizb sign is not drawn, a word that belongs to another page is not on this one.
        val answer = """
            {"verses":[{"verse_key":"2:6","words":[
              {"position":1,"char_type_name":"word","code_v1":"a","line_number":1,"page_number":3},
              {"position":2,"char_type_name":"rub-el-hizb","code_v1":"b","line_number":1,"page_number":3},
              {"position":3,"char_type_name":"word","code_v1":"c","line_number":1,"page_number":4},
              {"position":4,"char_type_name":"end","code_v1":"d","line_number":1,"page_number":3}
            ]}]}
        """.trimIndent()
        val page = assertNotNull(MushafLayoutFormat.parseQuranCom(answer, 3))
        val words = page.wordLines.single().words
        assertEquals(listOf("a", "d"), words.map { it.glyph })
        assertEquals(listOf(false, true), words.map { it.isAyahEnd })
    }

    @Test
    fun anAyahKeyReadsAndWritesItsText() {
        assertEquals(AyahKey(112, 3), AyahKey.parse("112:3"))
        assertEquals("112:3", AyahKey(112, 3).text)
        assertNull(AyahKey.parse(null))
        assertNull(AyahKey.parse("112"))
        assertNull(AyahKey.parse("112:9"), "112 has four ayahs")
        assertNull(AyahKey.parse("115:1"))
        assertNull(AyahKey.parse("1:0"))
        assertNull(AyahKey.parse("a:b"))
        assertFalse(AyahKey.parse("2:286") == null)
    }
}
