package tj.app.quran_todo.data.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationSearchTest {
    // -- The normalization: five languages ---------------------------------------------------------------------------------

    @Test
    fun caseIsIgnored() {
        assertEquals("милостив", SearchText.fold("МИЛОСТИВ"))
        assertEquals("the merciful", SearchText.fold("The Merciful"))
    }

    @Test
    fun russianYoIsE() {
        assertEquals("елка все", SearchText.fold("Ёлка всё"))
        assertEquals(SearchText.fold("всё"), SearchText.fold("все"))
    }

    @Test
    fun turkishDottedAndDotlessIAreI() {
        assertEquals("istanbul", SearchText.fold("İstanbul"))
        assertEquals("kapi", SearchText.fold("KAPI"))
        assertEquals("kapi", SearchText.fold("kapı"))
        assertEquals("rahmet", SearchText.fold("RAHMET"))
        // The Turkish letters of the translations are found with the plain Latin ones.
        assertEquals("sahin guc ogut uzum", SearchText.fold("Şahin Güç Öğüt Üzüm"))
        assertEquals("allaha", SearchText.fold("Allah'a"))
        assertEquals("ayet", SearchText.fold("Âyet"))
    }

    @Test
    fun tajikLettersGoToTheirBaseLetters() {
        // ӣ ӯ ҳ қ ғ ҷ -> и у х к г ч
        assertEquals("хамду", SearchText.fold("Ҳамду"))
        assertEquals("ояти", SearchText.fold("Оятӣ"))
        assertEquals("кадр", SearchText.fold("қадр"))
        assertEquals("гафур", SearchText.fold("ғафур"))
        assertEquals("чахон", SearchText.fold("ҷаҳон"))
        assertEquals("пуру", SearchText.fold("пӯру"))
    }

    @Test
    fun uzbekApostrophesAreDropped() {
        assertEquals("ozbek", SearchText.fold("o‘zbek"))
        assertEquals("ozbek", SearchText.fold("o'zbek"))
        assertEquals("gofur", SearchText.fold("g‘ofur"))
        assertEquals("gofur", SearchText.fold("gʻofur"))
        assertEquals("quran", SearchText.fold("Qur’an"))
    }

    @Test
    fun englishTransliterationMarksAreDropped() {
        assertEquals("muhammad ar-rahman", SearchText.fold("Muḥammad ar-Raḥmān"))
        assertEquals("cafe", SearchText.fold("café"))
    }

    @Test
    fun theTokensAreFoldedWordsWithoutEmptyOnes() {
        assertEquals(listOf("милостив", "елка"), SearchText.tokens("  Милостив   Ёлка "))
        assertEquals(emptyList(), SearchText.tokens("   "))
    }

    @Test
    fun aQueryNeedsTwoLetters() {
        assertFalse(SearchText.isSearchable(""))
        assertFalse(SearchText.isSearchable("а"))
        assertFalse(SearchText.isSearchable(" а "))
        assertTrue(SearchText.isSearchable("ал"))
        assertTrue(SearchText.isSearchable("а б"))
        // An apostrophe is not a letter.
        assertFalse(SearchText.isSearchable("'o"))
    }

    @Test
    fun aHitInTheFoldedTextIsPutBackOnTheOriginal() {
        val original = "Qur’an o‘zbek"
        val folded = SearchText.normalize(original)
        assertEquals("quran ozbek", folded.text)
        // The apostrophes are gone from the folded text, so its positions are not the original ones: the range maps them back.
        val at = folded.text.indexOf("ozbek")
        val range = folded.originalRange(at, at + "ozbek".length)
        assertEquals("o‘zbek", original.substring(range))
    }

    // -- The search: a fixture of 20 ayahs ----------------------------------------------------------------------------------

    /** 20 ayahs of the beginning of the Quran in Russian: global numbers 1..20 (Al-Fatihah 1..7, Al-Baqarah 8..). */
    private val fixture: Map<Int, String> = mapOf(
        1 to "Во имя Аллаха, Милостивого, Милосердного!",
        2 to "Хвала Аллаху, Господу миров,",
        3 to "Милостивому, Милосердному,",
        4 to "Властелину Дня воздаяния!",
        5 to "Тебе одному мы поклоняемся и Тебя одного молим о помощи.",
        6 to "Веди нас прямым путём,",
        7 to "путём тех, кого Ты облагодетельствовал, не тех, на кого пал гнев, и не заблудших.",
        8 to "Алиф. Лям. Мим.",
        9 to "Это Писание, в котором нет сомнений, является верным руководством для богобоязненных,",
        10 to "которые веруют в сокровенное, совершают намаз и расходуют из того, чем Мы их наделили,",
        11 to "которые веруют в то, что ниспослано тебе и было ниспослано до тебя, и убеждены в Последней жизни.",
        12 to "Они следуют верным руководством от своего Господа, и именно они преуспеют.",
        13 to "Тем, которые не веруют, безразлично, предостерегал ты их или не предостерегал, — они не уверуют.",
        14 to "Аллах наложил печать на их сердца и слух, а на их взорах — пелена. Им уготованы великие мучения.",
        15 to "Среди людей есть такие, которые говорят: «Мы веруем в Аллаха и в Последний день», но они не верующие.",
        16 to "Они хотят обмануть Аллаха и тех, которые уверовали, но обманывают лишь самих себя и не осознают этого.",
        17 to "В их сердцах болезнь, и Аллах увеличил их болезнь. Им уготованы мучительные страдания за то, что они лгали.",
        18 to "Когда им говорят: «Не распространяйте нечестие на земле», — они отвечают: «Мы лишь устанавливаем порядок».",
        19 to "Воистину, именно они распространяют нечестие, но не осознают этого.",
        20 to "Когда им говорят: «Уверуйте так, как уверовали люди», они отвечают: «Неужели мы уверуем так, как уверовали глупцы?»",
    )

    private val index = TranslationSearchIndex(fixture)

    @Test
    fun hitsComeInTheOrderOfTheQuranWhateverTheOrderOfTheInput() {
        val shuffled = TranslationSearchIndex(fixture.entries.sortedByDescending { it.key }.associate { it.key to it.value })
        val numbers = shuffled.search("милост").hits.map { it.ayahNumber }
        assertEquals(listOf(1, 3), numbers)
        assertEquals(numbers, index.search("милост").hits.map { it.ayahNumber })
    }

    @Test
    fun aHitKnowsItsSurahAndTheNumberInIt() {
        val hits = index.search("алиф").hits
        assertEquals(1, hits.size)
        // Al-Fatihah has 7 ayahs, so global 8 is 2:1.
        assertEquals(2, hits.single().surah)
        assertEquals(1, hits.single().ayahInSurah)
        val first = index.search("милостивого").hits.single()
        assertEquals(1, first.surah)
        assertEquals(1, first.ayahInSurah)
    }

    @Test
    fun theCaseAndTheYoDoNotMatter() {
        assertEquals(index.search("МИЛОСТИВ").hits.map { it.ayahNumber }, index.search("милостив").hits.map { it.ayahNumber })
        assertEquals(listOf(6, 7), index.search("путем").hits.map { it.ayahNumber })
    }

    @Test
    fun everyWordOfTheQueryMustBeThere() {
        assertEquals(listOf(3), index.search("милостивому милосердному").hits.map { it.ayahNumber })
        assertTrue(index.search("милостивому нечестие").hits.isEmpty())
    }

    @Test
    fun theMarkedPlacesAreInTheOriginalText() {
        val hit = index.search("милост").hits.first()
        assertEquals(listOf("Милост"), hit.ranges.map { hit.text.substring(it) })
        // The folded query is found in a differently written word: "путем" marks "путём" with its ё.
        val way = index.search("путем").hits.first()
        assertEquals(listOf("путём"), way.ranges.map { way.text.substring(it) })
    }

    @Test
    fun overlappingWordsAreMergedIntoOneMark() {
        val hit = index.search("мило милос").hits.first()
        // "мило" and "милос" start at the same place: one mark each, the longer one.
        assertEquals(listOf("Милос", "Милос"), hit.ranges.map { hit.text.substring(it) })
    }

    @Test
    fun aQueryShorterThanTwoLettersFindsNothing() {
        assertTrue(index.search("м").hits.isEmpty())
        assertTrue(index.search("").hits.isEmpty())
        assertFalse(index.search("м").truncated)
    }

    @Test
    fun nothingFoundIsAnEmptyListNotAnError() {
        val result = index.search("абракадабра")
        assertTrue(result.hits.isEmpty())
        assertFalse(result.truncated)
    }

    @Test
    fun theResultsAreCutAtTheLimitAndSayThatThereAreMore() {
        val many = (1..300).associateWith { "ayah $it with the word mercy" }
        val big = TranslationSearchIndex(many)
        val result = big.search("mercy")
        assertEquals(200, result.hits.size)
        assertTrue(result.truncated)
        assertEquals((1..200).toList(), result.hits.map { it.ayahNumber })
        // Exactly the limit is not "more".
        val exact = TranslationSearchIndex((1..200).associateWith { "mercy $it" }).search("mercy")
        assertEquals(200, exact.hits.size)
        assertFalse(exact.truncated)
    }

    @Test
    fun theSearchWorksOnFiveLanguages() {
        val tr = TranslationSearchIndex(mapOf(1 to "Rahmân ve rahîm olan ALLAH'IN adıyla.", 2 to "Hamd, âlemlerin Rabbi Allah'a mahsustur."))
        assertEquals(listOf(1), tr.search("rahman").hits.map { it.ayahNumber })
        assertEquals(listOf(1), tr.search("ADIYLA").hits.map { it.ayahNumber })
        assertEquals(listOf(2), tr.search("alemlerin").hits.map { it.ayahNumber })
        val tg = TranslationSearchIndex(mapOf(1 to "Ба номи Худованди бахшояндаи меҳрубон.", 2 to "Ситоиш Аллоҳро, ки Парвардигори ҷаҳониён аст."))
        assertEquals(listOf(1), tg.search("мехрубон").hits.map { it.ayahNumber })
        assertEquals(listOf(2), tg.search("чахониён").hits.map { it.ayahNumber })
        assertEquals(listOf(2), tg.search("аллохро").hits.map { it.ayahNumber })
        val uz = TranslationSearchIndex(mapOf(1 to "Hamd olamlarning Robbi — Allohgadir.", 2 to "O‘zingning yo‘lingga, g‘ofur."))
        assertEquals(listOf(2), uz.search("ozing").hits.map { it.ayahNumber })
        assertEquals(listOf(2), uz.search("gofur").hits.map { it.ayahNumber })
        val en = TranslationSearchIndex(mapOf(1 to "In the name of Allah, the Entirely Merciful, the Especially Merciful.", 2 to "[All] praise is [due] to Allah, Lord of the worlds."))
        assertEquals(listOf(1), en.search("merciful").hits.map { it.ayahNumber })
        assertEquals(listOf(2), en.search("WORLDS").hits.map { it.ayahNumber })
    }

    @Test
    fun locateMapsGlobalNumbersToSurahAndAyah() {
        assertEquals(1 to 1, WholeTranslation.locate(1))
        assertEquals(1 to 7, WholeTranslation.locate(7))
        assertEquals(2 to 1, WholeTranslation.locate(8))
        assertEquals(2 to 255, WholeTranslation.locate(262))
        assertEquals(114 to 6, WholeTranslation.locate(6236))
        assertEquals(112 to 1, WholeTranslation.locate(6222))
        assertEquals(null, WholeTranslation.locate(0))
        assertEquals(null, WholeTranslation.locate(6237))
    }
}
