package tj.app.quran_todo.ui.tajweed

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.tajweed.TajweedAlignment
import tj.app.quran_todo.common.tajweed.TajweedMarkup
import tj.app.quran_todo.common.tajweed.TajweedRule
import tj.app.quran_todo.common.tajweed.TajweedSpan
import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.ui.reader.splitBismillah

/**
 * The tajweed rules on the REAL bundled text as the screens draw it (after `splitBismillah` and `hideRubSign`). The Bismillah that opens the
 * first ayah of surahs 2-114 is in our text and not in the markup at all, so the two only meet once the screens' own cut is made.
 */
class TajweedBundledTextTest {
    private val quran = QuranTsvParser.parse(BundledFiles.quranTsv())

    private fun databaseText(surah: Int, ayah: Int): String =
        quran.ayahs.first { it.surahNumber == surah && it.numberInSurah == ayah }.text

    private fun drawn(surah: Int, ayah: Int): String = splitBismillah(surah, ayah, databaseText(surah, ayah))

    @Test
    fun theSamplesAreTheRowsOfTheBundledTextAndTheTextTheScreensDraw() {
        TajweedBundledSamples.all.forEach { sample ->
            val (surah, ayah) = sample.ref.split(':').map(String::toInt)
            assertEquals(sample.databaseText, databaseText(surah, ayah), "${sample.ref}: the bundled text changed")
            assertEquals(sample.drawn, drawn(surah, ayah), "${sample.ref}: what the screens draw changed")
        }
    }

    @Test
    fun theRulesLandOnTheTextThatIsDrawnNotOnTheTextOfTheDatabase() {
        TajweedBundledSamples.all.forEach { sample ->
            val (surah, ayah) = sample.ref.split(':').map(String::toInt)
            val text = drawn(surah, ayah)
            val moved = TajweedAlignment.align(TajweedMarkup.parse(sample.theirs), text)
                .map { it.rule.code to text.substring(it.range.first, it.range.last + 1) }
            assertEquals(sample.expected, moved, sample.ref)
        }
    }

    @Test
    fun theBismillahIsCutOffTheFirstAyahOfSurahs2To114ExceptAtTawbahAndKeptInAlFatiha() {
        fun sample(ref: String) = TajweedBundledSamples.all.first { it.ref == ref }
        fun firstWordLetters(text: String) = text.trim().substringBefore(' ').filter { it.isLetter() && it != '\u0640' }
        val bismillah = "\u0628\u0633\u0645"
        // Cut: the database row starts with the Bismillah, the drawn text does not, and the markup never had it.
        listOf("2:1", "3:1", "36:1", "68:1", "113:1", "114:1").forEach { ref ->
            val sample = sample(ref)
            assertEquals(bismillah, firstWordLetters(sample.databaseText), "$ref: the row starts with the Bismillah")
            assertTrue(sample.drawn.length < sample.databaseText.length, "$ref is not cut")
            assertTrue(sample.databaseText.endsWith(sample.drawn), "$ref: the drawn text is the end of the row")
            assertTrue(firstWordLetters(TajweedMarkup.parse(sample.theirs).plain) != bismillah, "$ref: the markup starts with the Bismillah")
        }
        // Not cut: Al-Fatiha has it as its first ayah (in the markup too), At-Tawbah has none.
        assertEquals(sample("1:1").databaseText, sample("1:1").drawn)
        assertEquals(bismillah, firstWordLetters(TajweedMarkup.parse(sample("1:1").theirs).plain))
        assertEquals(sample("9:1").databaseText, sample("9:1").drawn)
    }

    @Test
    fun theSignThatLooksLikeAStarIsHiddenAndDoesNotShiftTheRules() {
        // 3:153 starts with the rub el hizb sign in both texts; the screens hide it, the rules still land on the right letters.
        val sample = TajweedBundledSamples.all.first { it.ref == "3:153" }
        assertTrue('۞' in sample.databaseText)
        assertTrue('۞' !in sample.drawn)
        assertTrue(sample.theirs.contains('۞'))
        assertTrue(sample.expected.isNotEmpty())
    }

    @Test
    fun everyExampleOfTheLegendIsInTheBundledTextAtItsPlace() {
        assertEquals(TajweedRule.entries.toSet(), TajweedExamples.byRule.keys)
        TajweedExamples.byRule.forEach { (rule, example) ->
            val (surah, ayah) = example.ref.split(':').map(String::toInt)
            val text = drawn(surah, ayah)
            assertTrue(example.text in text, "$rule: '${example.text}' is not in ${example.ref}")
            assertTrue(example.spans.isNotEmpty(), "$rule has no coloured span")
            example.spans.forEach { span ->
                assertTrue(span.first >= 0 && span.last < example.text.length, "$rule: $span is outside '${example.text}'")
            }
        }
    }

    @Test
    fun theColouredTextIsTheSameTextWithColoursOnly() {
        val text = drawn(1, 1)
        val spans = listOf(
            TajweedSpan(TajweedRule.HamzatWasl, 7..7),
            TajweedSpan(TajweedRule.MaddNormal, 19..19),
        )
        val light = tajweedAnnotated(text, spans, dark = false)
        val dark = tajweedAnnotated(text, spans, dark = true)
        // The characters are never changed.
        assertEquals(text, light.text)
        assertEquals(text, dark.text)
        assertEquals(2, light.spanStyles.size)
        assertEquals(7, light.spanStyles[0].start)
        assertEquals(8, light.spanStyles[0].end)
        assertEquals(TajweedPalette.color(TajweedRule.HamzatWasl, false).toArgb(), light.spanStyles[0].item.color.toArgb())
        assertEquals(TajweedPalette.color(TajweedRule.MaddNormal, true).toArgb(), dark.spanStyles[1].item.color.toArgb())
    }

    @Test
    fun aColourNeverStartsOrEndsInsideALetterWithItsSign() {
        // "مَٰ": meem, fatha, dagger alif. A range on the dagger alif alone takes the meem; a range on the meem takes the signs after it.
        val text = "\u0645\u064E\u0670\u0646"
        val onSign = tajweedAnnotated(text, listOf(TajweedSpan(TajweedRule.MaddNormal, 2..2)), dark = false).spanStyles.single()
        assertEquals(0, onSign.start)
        assertEquals(3, onSign.end)
        val onLetter = tajweedAnnotated(text, listOf(TajweedSpan(TajweedRule.Ghunna, 0..0)), dark = false).spanStyles.single()
        assertEquals(0, onLetter.start)
        assertEquals(3, onLetter.end)
    }

    @Test
    fun rangesOutsideTheTextAreClippedNotThrown() {
        val styled = tajweedAnnotated("abc", listOf(TajweedSpan(TajweedRule.Ghunna, 2..10), TajweedSpan(TajweedRule.Ghunna, 7..9)), dark = false)
        assertEquals(1, styled.spanStyles.size)
        assertEquals(2, styled.spanStyles[0].start)
        assertEquals(3, styled.spanStyles[0].end)
    }
}
