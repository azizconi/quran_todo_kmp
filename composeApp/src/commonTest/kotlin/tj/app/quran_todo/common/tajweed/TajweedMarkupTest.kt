package tj.app.quran_todo.common.tajweed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TajweedMarkupTest {
    private fun TajweedAyah.pieces(): List<Pair<Char, String>> = spans.map { it.rule.code to plain.substring(it.range.first, it.range.last + 1) }

    @Test
    fun theSeventeenRulesHaveOneCodeAndOneQuranComClassEach() {
        assertEquals(17, TajweedRule.entries.size)
        assertEquals(17, TajweedRule.entries.map { it.code }.toSet().size)
        assertEquals(17, TajweedRule.entries.map { it.quranComClass }.toSet().size)
        TajweedRule.entries.forEach { assertEquals(it, TajweedRule.fromCode(it.code)) }
        assertNull(TajweedRule.fromCode('z'))
        assertNull(TajweedRule.fromCode('H'))
    }

    @Test
    fun theCodesAreTheOnesQuranComUses() {
        // Checked on 15 surahs against /quran/verses/uthmani_tajweed: the same spans carry these classes.
        val expected = mapOf(
            'h' to "ham_wasl", 'l' to "laam_shamsiyah", 's' to "slnt", 'n' to "madda_normal", 'p' to "madda_permissible",
            'o' to "madda_obligatory", 'm' to "madda_necessary", 'g' to "ghunnah", 'a' to "idgham_ghunnah", 'f' to "ikhafa",
            'c' to "ikhafa_shafawi", 'i' to "iqlab", 'q' to "qalaqah", 'u' to "idgham_wo_ghunnah", 'd' to "idgham_mutajanisayn",
            'b' to "idgham_mutaqaribayn", 'w' to "idgham_shafawi",
        )
        assertEquals(expected, TajweedRule.entries.associate { it.code to it.quranComClass })
    }

    @Test
    fun theGroupsAreTheSixOfTheBrief() {
        fun group(vararg codes: Char) = codes.map { TajweedRule.fromCode(it)!!.group }.toSet()
        assertEquals(setOf(TajweedGroup.Silent), group('h', 'l', 's'))
        assertEquals(setOf(TajweedGroup.Madd), group('n', 'p', 'o', 'm'))
        assertEquals(setOf(TajweedGroup.Nasal), group('g', 'a', 'f', 'c'))
        assertEquals(setOf(TajweedGroup.Iqlab), group('i'))
        assertEquals(setOf(TajweedGroup.Qalqalah), group('q'))
        assertEquals(setOf(TajweedGroup.Merge), group('u', 'd', 'b', 'w'))
        assertEquals(6, TajweedGroup.entries.size)
    }

    @Test
    fun anAyahWithoutMarkupHasNoSpans() {
        val ayah = TajweedMarkup.parse("قُلْ هُوَ")
        assertEquals("قُلْ هُوَ", ayah.plain)
        assertTrue(ayah.spans.isEmpty())
        assertEquals(0, ayah.ruleCount)
        assertEquals("", TajweedMarkup.parse("").plain)
    }

    @Test
    fun theMarkupOfTheSecondAyahOfAlBaqarahIsTakenOffAndTheSpansPointAtTheirText() {
        val ayah = TajweedMarkup.parse(
            "ذ[n[َٲ]لِكَ [h:11[ٱ]لْكِتَ[n[ـٰ]بُ لَا رَيْبَ‌ۛ فِيهِ‌ۛ هُ[u:12[دًى ل]ِّلْمُتَّق[p[ِي]نَ",
        )
        assertEquals("ذَٲلِكَ ٱلْكِتَـٰبُ لَا رَيْبَ‌ۛ فِيهِ‌ۛ هُدًى لِّلْمُتَّقِينَ", ayah.plain)
        assertEquals(
            listOf('n' to "َٲ", 'h' to "ٱ", 'n' to "ـٰ", 'u' to "دًى ل", 'p' to "ِي"),
            ayah.pieces(),
        )
        assertEquals(5, ayah.ruleCount)
        assertEquals(listOf(0, 1, 2, 3, 4), ayah.spans.map { it.source })
    }

    @Test
    fun theNumberAfterTheCodeMeansNothing() {
        val withNumber = TajweedMarkup.parse("[h:1234[ٱ]للَّهِ")
        val without = TajweedMarkup.parse("[h[ٱ]للَّهِ")
        assertEquals(without.plain, withNumber.plain)
        assertEquals(without.pieces(), withNumber.pieces())
    }

    @Test
    fun aRuleInsideAnotherOneWinsOnItsOwnCharacters() {
        // 2:190: a silent alif inside the madd of the waw, then a pause sign that still belongs to the madd.
        val ayah = TajweedMarkup.parse("وَلَا تَعْتَد[o[ُوٓ[s[اْ]‌ۚ] إِ")
        assertEquals("وَلَا تَعْتَدُوٓاْ‌ۚ إِ", ayah.plain)
        assertEquals(listOf('o' to "ُوٓ", 's' to "اْ", 'o' to "‌ۚ"), ayah.pieces())
        // The two pieces of the madd are one rule of the markup.
        assertEquals(2, ayah.ruleCount)
        assertEquals(listOf(0, 1, 0), ayah.spans.map { it.source })
        // ... and never overlap.
        ayah.spans.zipWithNext().forEach { (a, b) -> assertTrue(a.range.last < b.range.first) }
    }

    @Test
    fun aRuleThatOpensRightAtTheStartOfAnotherOneLeavesNothingToTheOuterOne() {
        // 2:278: [o[[s[و]ٲٓاْ] the silent waw first, then the madd of what is left.
        val ayah = TajweedMarkup.parse("ٱلرِّبَ[o[[s[و]ٲٓاْ] إِن")
        assertEquals(listOf('s' to "و", 'o' to "ٲٓاْ"), ayah.pieces())
        assertEquals(2, ayah.ruleCount)
    }

    @Test
    fun nestingMoreThanOneLevelDeepStillGivesDisjointPieces() {
        val ayah = TajweedMarkup.parse("[a[ab[o[cd[s[ef]gh]ij]kl")
        assertEquals("abcdefghijkl", ayah.plain)
        assertEquals(listOf('a' to "ab", 'o' to "cd", 's' to "ef", 'o' to "gh", 'a' to "ij"), ayah.pieces())
        assertEquals(3, ayah.ruleCount)
    }

    @Test
    fun aBracketThatOpensNothingStaysInTheText() {
        // 32:3 has [ٮٰ] without a code.
        val ayah = TajweedMarkup.parse("ٱفْتَرَ[ٮٰ]هُ [h[ٱ]ل")
        assertEquals("ٱفْتَرَ[ٮٰ]هُ ٱل", ayah.plain)
        assertEquals(listOf('h' to "ٱ"), ayah.pieces())
        // A "[x:[" without digits is no opening either.
        assertEquals("[x:[a]", TajweedMarkup.parse("[x:[a]").plain)
    }

    @Test
    fun aRuleThatIsNeverClosedEndsWithTheText() {
        val ayah = TajweedMarkup.parse("بِ[h[ٱ]للَّهِ [g[مّ")
        assertEquals("بِٱللَّهِ مّ", ayah.plain)
        assertEquals(listOf('h' to "ٱ", 'g' to "مّ"), ayah.pieces())
    }

    @Test
    fun anUnknownCodeKeepsItsTextAndDropsTheRule() {
        val ayah = TajweedMarkup.parse("[z[ٱ]للَّهِ [h[ٱ]ل")
        assertEquals("ٱللَّهِ ٱل", ayah.plain)
        assertEquals(listOf('h' to "ٱ"), ayah.pieces())
        assertEquals(1, ayah.ruleCount)
    }

    @Test
    fun theFixturesParseToTheRuleCountTheyAreMadeWith() {
        TajweedFixtures.all.forEach { fixture ->
            val ayah = TajweedMarkup.parse(fixture.theirs)
            assertEquals(fixture.ruleCount, ayah.ruleCount, fixture.ref)
            assertNotNull(ayah.plain)
        }
    }
}
