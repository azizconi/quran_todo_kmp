package tj.app.quran_todo.common.tajweed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TajweedAlignmentTest {
    /** The rules of [markup] on [ours] as (code, text of the range in our text). */
    private fun moved(markup: String, ours: String): List<Pair<Char, String>> =
        TajweedAlignment.align(TajweedMarkup.parse(markup), ours).map { it.rule.code to ours.substring(it.range.first, it.range.last + 1) }

    // -- The real ayahs ---------------------------------------------------------------------------------------

    @Test
    fun everyFixtureAyahGetsExactlyTheRulesItsIndependentAlignmentGave() {
        TajweedFixtures.all.forEach { fixture ->
            assertEquals(fixture.expected, moved(fixture.theirs, fixture.ours), fixture.ref)
        }
    }

    @Test
    fun theFixturesHoldAllSeventeenRules() {
        val seen = TajweedFixtures.all.flatMap { fixture -> fixture.expected.map { it.first } }.toSet()
        assertEquals(TajweedRule.entries.map { it.code }.toSet(), seen)
        assertTrue(TajweedFixtures.all.size >= 30, "${TajweedFixtures.all.size} fixtures")
    }

    @Test
    fun theMovedRangesNeverOverlapAndStayInsideOurText() {
        TajweedFixtures.all.forEach { fixture ->
            val spans = TajweedAlignment.align(TajweedMarkup.parse(fixture.theirs), fixture.ours)
            spans.forEach {
                assertTrue(it.range.first >= 0 && it.range.last < fixture.ours.length, "${fixture.ref}: ${it.range}")
            }
            spans.zipWithNext().forEach { (a, b) -> assertTrue(a.range.last < b.range.first, "${fixture.ref}: ${a.range} ${b.range}") }
        }
    }

    @Test
    fun aRuleMakesItThroughOnlyOnceWhenAnotherRuleCutItInTwo() {
        // 47:31: the lone fatha of [o[َ[s[اْ]] belongs to no letter, so the madd has nothing to colour; the silent alif stays.
        val fixture = TajweedFixtures.all.first { it.ref == "47:31" }
        val ayah = TajweedMarkup.parse(fixture.theirs)
        assertEquals(11, ayah.ruleCount)
        val sources = TajweedAlignment.align(ayah, fixture.ours).map { it.source }.toSet()
        assertEquals(10, sources.size)
    }

    // -- The differences between their text and ours, one by one -------------------------------------------------

    @Test
    fun aMaddOnTatweelAndDaggerAlifColoursTheDaggerAlifOfOurText() {
        // Theirs: tatweel + dagger alif. Ours: only the dagger alif, written on the meem.
        assertEquals(listOf('n' to "ٰ"), moved("ٱلرَّحْمَ[n[ـٰ]نِ", "ٱلرَّحْمَٰنِ"))
    }

    @Test
    fun aTatweelThatOursHasTooBelongsToTheRule() {
        assertEquals(listOf('n' to "ـٰ"), moved("وَقَ[n[ـٰ]تِلُوا", "وَقَـٰتِلُوا"))
    }

    @Test
    fun u0672IsTheDaggerAlifOfOurText() {
        assertEquals(listOf('n' to "ٰ"), moved("ذ[n[َٲ]لِكَ", "ذَٰلِكَ"))
    }

    @Test
    fun marksThatOnlyOursHasStayInsideTheRange() {
        // U+06ED and U+06E2 are in our text only: they follow their letter, so they are in the range of the rule on it.
        assertEquals(listOf('q' to "بْۭ"), moved("[q[بْ]", "بْۭ"))
        assertEquals(listOf('i' to "مٍۢ بَ"), moved("[i[مٍ ب]َ", "مٍۢ بَ"))
    }

    @Test
    fun u06DFAgainstTheSukunDoesNotMatter() {
        assertEquals(listOf('s' to "ا۟"), moved("ءَامَنُو[s[اْ] ", "ءَامَنُوا۟ "))
    }

    @Test
    fun aSpaceBeforeAPauseSignAgainstU200CDoesNotMatter() {
        val theirs = "رَيْبَ‌ۛ فِ[p[ي]هِ"
        assertEquals(listOf('p' to "ي"), moved(theirs, "رَيْبَ ۛ فِيهِ"))
    }

    @Test
    fun anExtraAlefInOursIsSkipped() {
        // Ours has an alef after the hamza that theirs does not; the khaa is still the khaa.
        assertEquals(listOf('q' to "خِ"), moved("ٱلْأَ[q[خِ]رَةِ", "ٱلْءَاخِرَةِ"))
        // ... and one inside the stretch of a rule is part of it.
        assertEquals(listOf('o' to "جَاءَ"), moved("[o[جَءَ]", "جَاءَ"))
    }

    @Test
    fun aMissingAlefInOursLeavesTheRestOfTheRule() {
        // Theirs has a letter that ours has not: the rule keeps the letters ours has.
        assertEquals(listOf('o' to "جَءَ"), moved("[o[جَاءَ]", "جَءَ"))
    }

    @Test
    fun hamzaAgainstAlefWithHamzaPairsTheHamzaNotTheAlef() {
        // Theirs أ meets our ء (not the alef after it): the madd range is "ءَ", the alef after it is not coloured.
        assertEquals(listOf('o' to "ءَ"), moved("ٱلْ[o[أَ]خِرَةِ", "ٱلْءَاخِرَةِ"))
    }

    @Test
    fun alefMaksuraAgainstDotlessBeh() {
        assertEquals(listOf('n' to "ىٰ"), moved("ٱفْتَرَ[n[ٮٰ]هُ", "ٱفْتَرَىٰهُ"))
    }

    @Test
    fun theSmallYehOfTheTwoTextsIsTheSameLetter() {
        // U+06E7 in theirs, U+06E6 in ours (2:125): the madd on it is not lost.
        assertEquals(listOf('n' to "ۦ"), moved("هِـ[n[ـۧ]مَ", "هِۦمَ"))
    }

    @Test
    fun aRuleCanRunAcrossASpaceAndTakesTheMarksOfItsLastLetter() {
        assertEquals(listOf('u' to "دًۭى لِّ"), moved("هُ[u:12[دًى ل]ِّلْمُتَّق", "هُدًۭى لِّلْمُتَّق"))
    }

    // -- What is dropped -----------------------------------------------------------------------------------------

    @Test
    fun aRuleThatHoldsNoLetterIsDropped() {
        assertEquals(emptyList(), moved("بَ[o[َ]", "بَ"))
        assertEquals(emptyList(), moved("بَ[s[‌ۚ] جَ", "بَ ۚ جَ"))
    }

    @Test
    fun aRuleWhoseLettersOursHasNotIsDropped() {
        assertEquals(listOf('h' to "ٱ"), moved("[h[ٱ]ل[q[ظ]مَ", "ٱلمَ"))
    }

    @Test
    fun noRulesOrNoTextGiveNothing() {
        assertEquals(emptyList(), moved("قُلْ هُوَ", "قُلْ هُوَ"))
        assertEquals(emptyList(), moved("[h[ٱ]ل", ""))
        assertEquals(emptyList(), moved("[h[ٱ]ل", " "))
    }

    @Test
    fun theSameTextMovesEveryRuleOnTheSameLetters() {
        val theirs = "[h:1[ٱ]للَّهِ [h:2[ٱ][l[ل]رَّحْمَ[n[ـٰ]نِ"
        val plain = TajweedMarkup.parse(theirs).plain
        val spans = TajweedAlignment.align(TajweedMarkup.parse(theirs), plain)
        assertEquals(listOf('h', 'h', 'l', 'n'), spans.map { it.rule.code })
        assertEquals(TajweedMarkup.parse(theirs).spans.map { it.range }, spans.map { it.range })
    }

    // -- The pairing of letters -------------------------------------------------------------------------------------

    @Test
    fun aVeryLongAyahWithAnExtraLetterInTheMiddleStillAlignsAtTheEnd() {
        // 2:282 has about 540 letters. Theirs: 90 words and a rule on the last letter; ours: the same, but one word has an extra alef.
        val words = List(90) { "بِسْمِ" }
        val theirs = words.joinToString(" ") + " [h[ٱ]"
        val ours = words.mapIndexed { index, word -> if (index == 45) "بِاسْمِ" else word }.joinToString(" ") + " ٱ"
        val spans = TajweedAlignment.align(TajweedMarkup.parse(theirs), ours)
        assertEquals(1, spans.size)
        assertEquals("ٱ", ours.substring(spans[0].range.first, spans[0].range.last + 1))
        assertEquals(ours.length - 1, spans[0].range.first)
    }

    @Test
    fun theScoreOrdersTheSpellings() {
        assertEquals(4, TajweedAlignment.score('ء', 'ء'))
        assertEquals(3, TajweedAlignment.score('ء', 'أ'))
        assertEquals(3, TajweedAlignment.score('ٲ', 'ٰ'))
        assertEquals(2, TajweedAlignment.score('ا', 'أ'))
        assertEquals(0, TajweedAlignment.score('ب', 'ت'))
        assertEquals(0, TajweedAlignment.score('ا', 'ى'))
    }
}
