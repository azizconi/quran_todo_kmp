package tj.app.quran_todo.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurahSearchTest {
    private val items = listOf(
        SearchItem(1, names = listOf("Аль-Фатиха", "Al-Fatihah"), meanings = listOf("Открывающая"), arabic = listOf("الفاتحة")),
        SearchItem(2, names = listOf("Аль-Бакара", "Al-Baqarah"), meanings = listOf("Корова"), arabic = listOf("البقرة")),
        SearchItem(3, names = listOf("Аль Имран", "Ali 'Imran"), meanings = listOf("Семейство Имрана"), arabic = listOf("آل عمران")),
        SearchItem(26, names = listOf("аш-Шуара", "Ash-Shu'ara"), meanings = listOf("Поэты"), arabic = listOf("الشعراء")),
        SearchItem(36, names = listOf("Йа Син", "Ya-Sin"), meanings = listOf("Йа Син"), arabic = listOf("يس")),
        SearchItem(55, names = listOf("ар-Рахман", "Ar-Rahman"), meanings = listOf("Милостивый"), arabic = listOf("الرحمن")),
        SearchItem(67, names = listOf("аль-Мульк", "Al-Mulk"), meanings = listOf("Власть"), arabic = listOf("الملك")),
        SearchItem(114, names = listOf("Ан-Нас", "An-Nas"), meanings = listOf("Люди"), arabic = listOf("الناس")),
    )
    private val index = SurahSearch.Index.build(items)

    @Test
    fun normalizeFoldsCaseHyphensApostrophesAndAccents() {
        assertEquals("аль бакара", SurahSearch.normalize("Аль-Бакара"))
        assertEquals("ash shuara", SurahSearch.normalize("Ash-Shu'ara"))
        assertEquals("ash shuara", SurahSearch.normalize("ASH-SHUʻARĀʼ"))
        assertEquals("sure", SurahSearch.normalize("Sûre"))
        assertEquals("ibrahim", SurahSearch.normalize("Ibrāhīm"))
        assertEquals("istanbul", SurahSearch.normalize("İstanbul"))
        assertEquals("иа син", SurahSearch.normalize("Йа Син"))
        assertEquals("", SurahSearch.normalize("  - ' "))
    }

    @Test
    fun normalizeStripsArabicVowelMarksTatweelAndFoldsLetterVariants() {
        // Vowel marks (U+064B..U+0652), superscript alef (U+0670), alef wasla, teh marbuta.
        assertEquals("البقره", SurahSearch.normalize("ٱلْبَقَرَةِ"))
        assertEquals("الرحمن", SurahSearch.normalize("ٱلرَّحْمَٰنِ"))
        assertEquals("الفاتحه", SurahSearch.normalize("الفــاتحة"))
        assertEquals("ال عمران", SurahSearch.normalize("آل عِمْرَان"))
    }

    @Test
    fun normalizeMapsArabicIndicDigits() {
        assertEquals("255", SurahSearch.normalize("٢٥٥"))
        assertEquals("36", SurahSearch.normalize("۳۶"))
    }

    @Test
    fun normalizeNameDropsTheLeadingArticle() {
        assertEquals("бакара", SurahSearch.normalizeName("Аль-Бакара"))
        assertEquals("бакара", SurahSearch.normalizeName("аль-бакара"))
        assertEquals("имран", SurahSearch.normalizeName("аль Имран"))
        assertEquals("shuara", SurahSearch.normalizeName("Ash-Shu'ara"))
        assertEquals("rahman", SurahSearch.normalizeName("Ar-Rahmān"))
        assertEquals("nas", SurahSearch.normalizeName("An-Nas"))
        assertEquals("лаил", SurahSearch.normalizeName("Ал - Лайл")) // й is folded to и
        assertEquals("мухаммад", SurahSearch.normalizeName("Сура Мухаммад"))
        assertEquals("бакара", SurahSearch.normalizeName("Сура Аль-Бакара"))
        assertEquals("ya sin", SurahSearch.normalizeName("Ya-Sin"))
        assertEquals("نس", SurahSearch.normalizeName("ٱلنَّس"))
    }

    @Test
    fun aSingleWordIsNeverEmptiedByArticleRemoval() {
        assertEquals("аль", SurahSearch.normalizeName("аль"))
        assertEquals("an", SurahSearch.normalizeName("An"))
    }

    @Test
    fun blankQueryMatchesEveryone() {
        assertEquals(items.map { it.number }, index.search(""))
        assertEquals(items.map { it.number }, index.search("   "))
        assertEquals(items.map { it.number }, index.search("-'"))
    }

    @Test
    fun digitsMatchTheSurahNumberExactly() {
        assertEquals(listOf(2), index.search("2"))
        assertEquals(listOf(114), index.search("114"))
        assertEquals(listOf(36), index.search("٣٦"))
        assertEquals(emptyList(), index.search("200"))
        assertEquals(listOf(2), index.search("2:"))
    }

    @Test
    fun theArticleAndSeparatorsDoNotMatter() {
        assertEquals(listOf(2), index.search("бакара"))
        assertEquals(listOf(2), index.search("Аль-Бакара"))
        assertEquals(listOf(2), index.search("аль бакара"))
        assertEquals(listOf(2), index.search("альбакара"))
        assertEquals(listOf(2), index.search("baqarah"))
        assertEquals(listOf(2), index.search("Al Baqarah"))
        assertEquals(listOf(26), index.search("shuara"))
        assertEquals(listOf(26), index.search("Ash-Shu'ara"))
        assertEquals(listOf(36), index.search("yasin"))
        assertEquals(listOf(36), index.search("ya sin"))
        assertEquals(listOf(36), index.search("ya-sin"))
    }

    @Test
    fun arabicQueriesIgnoreVowelMarksAndTheArticle() {
        assertEquals(listOf(2), index.search("بقرة"))
        assertEquals(listOf(2), index.search("ٱلْبَقَرَة"))
        assertEquals(listOf(55), index.search("الرَّحْمَٰن"))
    }

    @Test
    fun matchesAreRankedExactThenPrefixThenWordThenSubstring() {
        val ranked = SurahSearch.Index.build(
            listOf(
                SearchItem(10, names = listOf("Alpha Beta")), // second word starts with "beta"
                SearchItem(11, names = listOf("Betamax")), // starts with
                SearchItem(12, names = listOf("Zbeta")), // contains
                SearchItem(13, names = listOf("Beta")), // exact
            ),
        )
        assertEquals(listOf(13, 11, 10, 12), ranked.search("beta"))
    }

    @Test
    fun aMatchOnTheNameBeatsAMatchOnItsMeaning() {
        val ranked = SurahSearch.Index.build(
            listOf(
                SearchItem(1, names = listOf("Ayat"), meanings = listOf("Light")),
                SearchItem(2, names = listOf("Nur"), meanings = listOf("Light")),
                SearchItem(3, names = listOf("Light")),
            ),
        )
        assertEquals(listOf(3, 1, 2), ranked.search("light"))
    }

    @Test
    fun theMeaningOfTheNameIsSearchable() {
        assertEquals(listOf(2), index.search("корова"))
        assertEquals(listOf(67), index.search("власть"))
    }

    @Test
    fun aLoneArticleListsEveryNameWithThatArticle() {
        assertEquals(listOf(1, 2, 3, 67), index.search("аль"))
        assertEquals(listOf(1, 2, 3, 67), index.search("аль-"))
    }

    @Test
    fun nothingMatchesNonsense() {
        assertEquals(emptyList(), index.search("qqq"))
        assertEquals(emptyList(), index.search("2 255"))
    }

    // -- Ayah references --------------------------------------------------------------------------

    @Test
    fun parsesColonSpaceAndDotSeparators() {
        assertEquals(AyahReference(2, 255), SurahSearch.parseAyahReference("2:255"))
        assertEquals(AyahReference(2, 255), SurahSearch.parseAyahReference("2 255"))
        assertEquals(AyahReference(2, 255), SurahSearch.parseAyahReference("2.255"))
        assertEquals(AyahReference(2, 255), SurahSearch.parseAyahReference("  2 : 255  "))
        assertEquals(AyahReference(36, 1), SurahSearch.parseAyahReference("36,1"))
        assertEquals(AyahReference(114, 6), SurahSearch.parseAyahReference("114/6"))
        assertEquals(AyahReference(2, 255), SurahSearch.parseAyahReference("٢:٢٥٥"))
    }

    @Test
    fun rejectsReferencesOutsideTheQuran() {
        assertNull(SurahSearch.parseAyahReference("0:1"))
        assertNull(SurahSearch.parseAyahReference("115:1"))
        assertNull(SurahSearch.parseAyahReference("2:0"))
        assertNull(SurahSearch.parseAyahReference("2:287"))
        assertNull(SurahSearch.parseAyahReference("1:8"))
        assertNull(SurahSearch.parseAyahReference("114:7"))
    }

    @Test
    fun acceptsTheLastAyahOfEverySurah() {
        for (surah in 1..114) {
            val last = QuranMeta.ayahCount(surah)
            assertEquals(AyahReference(surah, last), SurahSearch.parseAyahReference("$surah:$last"), "surah $surah")
            assertNull(SurahSearch.parseAyahReference("$surah:${last + 1}"), "surah $surah")
        }
    }

    @Test
    fun rejectsTextThatIsNotAPairOfNumbers() {
        assertNull(SurahSearch.parseAyahReference(""))
        assertNull(SurahSearch.parseAyahReference("2"))
        assertNull(SurahSearch.parseAyahReference("2:"))
        assertNull(SurahSearch.parseAyahReference("2:25:3"))
        assertNull(SurahSearch.parseAyahReference("baqarah 255"))
        assertNull(SurahSearch.parseAyahReference("2:2a"))
        assertNull(SurahSearch.parseAyahReference("2:2555"))
    }

    @Test
    fun usesTheGivenAyahCounts() {
        val counts = mapOf(1 to 3)
        assertEquals(AyahReference(1, 3), SurahSearch.parseAyahReference("1:3") { counts[it] ?: 0 })
        assertNull(SurahSearch.parseAyahReference("1:4") { counts[it] ?: 0 })
    }

    @Test
    fun quranMetaIsTheStandardNumbering() {
        assertEquals(114, (1..114).count { QuranMeta.ayahCount(it) > 0 })
        assertEquals(6236, (1..114).sumOf { QuranMeta.ayahCount(it) })
        assertEquals(7, QuranMeta.ayahCount(1))
        assertEquals(286, QuranMeta.ayahCount(2))
        assertEquals(6, QuranMeta.ayahCount(114))
        assertEquals(0, QuranMeta.ayahCount(0))
        assertEquals(0, QuranMeta.ayahCount(115))
        assertTrue(QuranMeta.ayahCount(2) > QuranMeta.ayahCount(3))
    }
}
