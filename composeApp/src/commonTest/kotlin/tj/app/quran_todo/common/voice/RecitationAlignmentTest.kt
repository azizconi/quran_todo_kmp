package tj.app.quran_todo.common.voice

import tj.app.quran_todo.common.similar.normalizedWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecitationAlignmentTest {
    // Ayah 2:2 and 112:1-2 as the bundled text writes them (with marks) and as a model writes them down (other marks, other alefs).
    private val reference = normalizedWords("ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى لِّلْمُتَّقِينَ").words
    private val sameFromModel = heardWords("ذَلِكَ الْكِتَابُ لَا رَيْبَ فِيهِ هُدًى لِلْمُتَّقِينَ")

    @Test
    fun spellingAndMarksAreNotErrors() {
        val check = alignRecitation(reference, sameFromModel)
        assertEquals(0, check.errors)
        assertEquals(reference.size, check.correct)
    }

    @Test
    fun aWordMissingInTheSpeechIsMissed() {
        val heard = sameFromModel.toMutableList().also { it.removeAt(3) } // "raiba"
        val check = alignRecitation(reference, heard)
        assertEquals(WordVerdict.Missed, check.words[3].verdict)
        assertEquals(1, check.errors)
    }

    @Test
    fun aWordSaidInsteadOfAnotherIsReplacedAndTellsWhat() {
        val heard = sameFromModel.toMutableList().also { it[5] = "نور" }
        val check = alignRecitation(reference, heard)
        assertEquals(WordVerdict.Replaced, check.words[5].verdict)
        assertEquals("نور", check.words[5].heard)
        assertEquals(1, check.errors)
    }

    @Test
    fun anExtraWordIsListedWithItsPlace() {
        val heard = sameFromModel.toMutableList().also { it.add(2, "حقا") }
        val check = alignRecitation(reference, heard)
        assertEquals(0, check.missed + check.replaced)
        assertEquals(listOf(2), check.extras.map { it.gap })
        assertEquals(listOf("حقا"), check.extras.map { it.word })
    }

    @Test
    fun aMissingSpaceIsNotAnError() {
        // The model writes two words of the text as one ("la raiba" -> "laraiba") and one word as two ("ya hasratan" for "yahasratan").
        val joined = alignRecitation(reference, heardWords("ذلك الكتاب لاريب فيه هدى للمتقين"))
        assertEquals(0, joined.errors)
        val split = alignRecitation(normalizedWords("يَٰحَسْرَةً عَلَى ٱلْعِبَادِ").words, heardWords("يا حسرة على العباد"))
        assertEquals(0, split.errors)
    }

    @Test
    fun skeletonIgnoresAlefHamzaAndDoubledLetters() {
        assertTrue(sameWord("الرحمن", "الرحمان", WordMatching.Strict))
        assertTrue(sameWord("امنوا", "ءامنوا", WordMatching.Strict))
        assertTrue(sameWord("المتقين", "المتقين", WordMatching.Strict))
        assertFalse(sameWord("يعلمون", "تعلمون", WordMatching.Strict))
    }

    @Test
    fun fuzzyAllowsOneWrongLetterOfALongWordOnly() {
        assertTrue(sameWord("يعلمون", "تعلمون", WordMatching.Fuzzy))
        assertFalse(sameWord("كان", "كاف", WordMatching.Fuzzy)) // too short: a different word
        assertFalse(sameWord("يعلمون", "تعملون", WordMatching.Strict))
    }

    @Test
    fun nothingHeardMakesEveryWordMissed() {
        val check = alignRecitation(reference, emptyList())
        assertEquals(reference.size, check.missed)
        assertTrue(check.extras.isEmpty())
    }

    @Test
    fun heardWordsDropsTokensWithoutLetters() {
        assertEquals(listOf("الحمد", "لله"), heardWords("الْحَمْدُ ۖ لِلَّهِ"))
    }
}
