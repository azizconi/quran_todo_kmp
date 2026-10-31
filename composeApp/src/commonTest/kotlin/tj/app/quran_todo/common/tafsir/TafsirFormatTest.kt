package tj.app.quran_todo.common.tafsir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tj.app.quran_todo.common.i18n.AppLanguage

/** The answer of `tafsirs/{id}/by_chapter/{surah}` as quran.com writes it (shape checked on 2026-10-31), the compact copy and the groups. */
class TafsirFormatTest {
    private fun answer(surah: Int, entries: List<Pair<Int, String>>, next: Int? = null, total: Int = entries.size): String = buildJsonObject {
        put(
            "tafsirs",
            buildJsonArray {
                for ((ayah, text) in entries) {
                    add(
                        buildJsonObject {
                            put("id", 84560 + ayah)
                            put("resource_id", 170)
                            put("verse_key", "$surah:$ayah")
                            put("language_id", 138)
                            put("text", text)
                            put("slug", "ru-tafseer-al-saddi")
                        },
                    )
                }
            },
        )
        put(
            "pagination",
            buildJsonObject {
                put("per_page", 50)
                put("current_page", 1)
                put("next_page", next?.let { JsonPrimitive(it) } ?: JsonNull)
                put("total_records", total)
            },
        )
    }.toString()

    // As-Sa'di for surah 2: 2:19 is empty, its text is the one of 2:18 (checked with by_ayah: verses {2:18, 2:19}).
    private val saadi = listOf(
        1 to "Мы уже говорили о толковании.",
        2 to "Это Великое Писание.",
        3 to "Тех, которые веруют в сокрытое.",
        4 to "Тех, которые веруют в то, что ниспослано тебе.",
        5 to "Именно они следуют верным путем.",
        6 to "Неверующие.",
    )

    @Test
    fun aPageIsReadWithItsPaginationAndAnEmptyTextStaysEmpty() {
        val page = assertNotNull(TafsirFormat.parsePage(answer(2, saadi + (7 to ""), next = 2, total = 286), 2))
        assertEquals(7, page.entries.size)
        assertEquals(2, page.nextPage)
        assertEquals(286, page.totalRecords)
        assertEquals(7 to "", page.entries.last())
        assertNull(TafsirFormat.parsePage(answer(2, saadi), 2)!!.nextPage)
    }

    @Test
    fun anAnswerOfAnotherSurahOrNotAnAnswerIsRefused() {
        assertNull(TafsirFormat.parsePage(answer(3, saadi), 2))
        assertNull(TafsirFormat.parsePage("{\"error\":\"nope\"}", 2))
        assertNull(TafsirFormat.parsePage("<html>", 2))
        assertNull(TafsirFormat.parsePage("{\"tafsirs\":[{\"verse_key\":\"x\",\"text\":\"a\"}]}", 2))
    }

    @Test
    fun theChapterIsPutTogetherFromItsPages() {
        val first = TafsirFormat.parsePage(answer(2, saadi.take(3), next = 2, total = 6), 2)!!
        val second = TafsirFormat.parsePage(answer(2, saadi.drop(3), total = 6), 2)!!
        val chapter = assertNotNull(TafsirFormat.assemble(2, TafsirSource.Saadi, listOf(first, second), ayahCount = 6))
        assertEquals(6, chapter.ayahCount)
        assertEquals("Это Великое Писание.", chapter.passage(2)!!.text)
        // A page that is missing makes the surah unusable: the totals do not add up.
        assertNull(TafsirFormat.assemble(2, TafsirSource.Saadi, listOf(first), ayahCount = 6))
        // The same ayah twice, an ayah beyond the end of the surah, and nothing at all.
        assertNull(TafsirFormat.assemble(2, TafsirSource.Saadi, listOf(first, first), ayahCount = 6))
        assertNull(TafsirFormat.assemble(2, TafsirSource.Saadi, listOf(second), ayahCount = 5))
        assertNull(TafsirFormat.assemble(2, TafsirSource.Saadi, emptyList(), ayahCount = 6))
    }

    @Test
    fun anAyahWithoutATextHasTheTextAndTheNoteOfItsGroup() {
        // 2:1 has a text of its own; 2:2 and 2:3 are empty and belong to 2:1; 2:4 starts a group of two (2:4 and 2:5).
        val entries = listOf(1 to "one", 2 to "", 3 to "", 4 to "four", 5 to "", 6 to "six")
        val chapter = TafsirChapter(2, TafsirSource.Saadi, 6, entries.toMap())
        assertEquals(TafsirPassage("one", 1, 3), chapter.passage(1))
        assertEquals(TafsirPassage("one", 1, 3), chapter.passage(3))
        assertTrue(chapter.passage(2)!!.isGroup)
        assertEquals(TafsirPassage("four", 4, 5), chapter.passage(5))
        assertEquals(TafsirPassage("six", 6, 6), chapter.passage(6))
        assertFalse(chapter.passage(6)!!.isGroup)
        assertNull(chapter.passage(7))
        assertNull(chapter.passage(0))
    }

    @Test
    fun emptyAyahsAtTheStartTakeTheFirstTextAndAnAyahTheAnswerDoesNotListIsAGroupMember() {
        // Muyassar lists only the first ayah of a group: 16 has 4 of 6 ayahs here.
        val chapter = TafsirChapter(4, TafsirSource.Muyassar, 6, mapOf(2 to "two", 3 to "three", 5 to "five"))
        assertEquals(TafsirPassage("two", 1, 2), chapter.passage(1))
        assertEquals(TafsirPassage("three", 3, 4), chapter.passage(4))
        assertEquals(TafsirPassage("five", 5, 6), chapter.passage(6))
    }

    @Test
    fun aSurahWithoutAnyTextHasNoPassagesButIsAChapter() {
        // Ibn Kathir has nothing for surah 105: every ayah is listed with an empty text.
        val page = TafsirFormat.parsePage(answer(105, (1..5).map { it to "" }), 105)!!
        val chapter = assertNotNull(TafsirFormat.assemble(105, TafsirSource.IbnKathir, listOf(page), ayahCount = 5))
        assertNull(chapter.passage(3))
        val again = assertNotNull(TafsirFormat.decode(TafsirFormat.encode(chapter), 105, TafsirSource.IbnKathir))
        assertNull(again.passage(3))
    }

    @Test
    fun theCompactCopyKeepsTheTextsAndRefusesAnotherSurahSourceOrVersion() {
        val chapter = TafsirChapter(2, TafsirSource.IbnKathir, 6, mapOf(1 to "<h2>Title</h2><p>Text “with” quotes \\ and \n a break</p>", 4 to "four"))
        val copy = TafsirFormat.encode(chapter)
        assertFalse("resource" in copy)
        val back = assertNotNull(TafsirFormat.decode(copy, 2, TafsirSource.IbnKathir))
        assertEquals(chapter.texts, back.texts)
        assertEquals(6, back.ayahCount)
        assertEquals(TafsirPassage(chapter.texts.getValue(1), 1, 3), back.passage(2))
        assertNull(TafsirFormat.decode(copy, 3, TafsirSource.IbnKathir))
        assertNull(TafsirFormat.decode(copy, 2, TafsirSource.Saadi))
        assertNull(TafsirFormat.decode(copy.replace("\"v\":1", "\"v\":2"), 2, TafsirSource.IbnKathir))
        assertNull(TafsirFormat.decode("not json", 2, TafsirSource.IbnKathir))
        assertNull(TafsirFormat.decode(copy.replace("\"n\":6", "\"n\":3"), 2, TafsirSource.IbnKathir))
    }

    @Test
    fun theDefaultSourceFollowsTheLanguageOfTheApp() {
        assertEquals(TafsirSource.Saadi, TafsirSource.defaultFor(AppLanguage.RU))
        assertEquals(TafsirSource.Saadi, TafsirSource.defaultFor(AppLanguage.TG))
        assertEquals(TafsirSource.IbnKathir, TafsirSource.defaultFor(AppLanguage.EN))
        // QuranEnc: the Mukhtasar in the language of the reader where there is one.
        assertEquals(TafsirSource.MokhtasarTr, TafsirSource.defaultFor(AppLanguage.TR))
        assertEquals(TafsirSource.MokhtasarUz, TafsirSource.defaultFor(AppLanguage.UZ))
    }

    @Test
    fun theChipsAreTheOnesOfTheBriefAndTheDefaultIsFirst() {
        assertEquals(
            listOf(TafsirSource.Saadi, TafsirSource.MokhtasarRu, TafsirSource.IbnKathir, TafsirSource.Muyassar),
            TafsirSource.chipsFor(AppLanguage.RU),
        )
        assertEquals(
            listOf(TafsirSource.MokhtasarTr, TafsirSource.IbnKathir, TafsirSource.MokhtasarEn, TafsirSource.Muyassar),
            TafsirSource.chipsFor(AppLanguage.TR),
        )
        assertEquals(
            listOf(TafsirSource.MokhtasarUz, TafsirSource.Saadi, TafsirSource.MokhtasarRu, TafsirSource.IbnKathir),
            TafsirSource.chipsFor(AppLanguage.UZ),
        )
        assertEquals(listOf(TafsirSource.Saadi, TafsirSource.MokhtasarRu, TafsirSource.IbnKathir), TafsirSource.chipsFor(AppLanguage.TG))
        assertEquals(listOf(TafsirSource.IbnKathir, TafsirSource.MokhtasarEn, TafsirSource.Muyassar), TafsirSource.chipsFor(AppLanguage.EN))
        AppLanguage.entries.forEach { assertEquals(TafsirSource.defaultFor(it), TafsirSource.chipsFor(it).first(), it.name) }
    }

    @Test
    fun onlyTheUzbekMukhtasarIsCyrillicAndEveryProviderHasItsSources() {
        assertEquals(listOf(TafsirSource.MokhtasarUz), TafsirSource.entries.filter { it.cyrillic })
        assertEquals(3, TafsirSource.entries.count { it.provider == TafsirProvider.QuranCom })
        assertEquals(4, TafsirSource.entries.count { it.provider == TafsirProvider.QuranEnc })
        assertTrue(TafsirSource.isInLanguageOf(TafsirSource.MokhtasarUz, AppLanguage.UZ))
        assertTrue(TafsirSource.isInLanguageOf(TafsirSource.MokhtasarTr, AppLanguage.TR))
    }

    private fun enc(surah: Int, vararg texts: String) = buildJsonObject {
        put(
            "result",
            buildJsonArray {
                texts.forEachIndexed { index, text ->
                    add(
                        buildJsonObject {
                            put("id", "${index + 1}")
                            put("sura", "$surah")
                            put("aya", "${index + 1}")
                            put("arabic_text", "x")
                            put("translation", text)
                            put("footnotes", JsonNull)
                        },
                    )
                }
            },
        )
    }.toString()

    @Test
    fun theAnswerOfQuranEncIsReadAsItIsWithOneTextPerAyah() {
        val text = "Тафсир «как есть»: Эл-Ихлос\n\nвторой абзац"
        val page = assertNotNull(TafsirFormat.parseQuranEnc(enc(112, text, "b", "c", "d"), 112))
        assertNull(page.nextPage)
        val chapter = assertNotNull(TafsirFormat.assemble(112, TafsirSource.MokhtasarUz, listOf(page), ayahCount = 4, version = "1.0.3"))
        // The text is not changed (the terms of QuranEnc), and there is no group: every ayah has its own.
        assertEquals(text, chapter.passage(1)!!.text)
        assertFalse(chapter.passage(2)!!.isGroup)
        assertEquals("1.0.3", chapter.version)
        // The copy of the app keeps the version.
        val back = assertNotNull(TafsirFormat.decode(TafsirFormat.encode(chapter), 112, TafsirSource.MokhtasarUz))
        assertEquals("1.0.3", back.version)
        assertEquals(text, back.passage(1)!!.text)
        assertNull(TafsirFormat.decode(TafsirFormat.encode(chapter), 112, TafsirSource.MokhtasarRu))
        assertNull(TafsirFormat.parseQuranEnc(enc(111, "a"), 112))
        assertNull(TafsirFormat.parseQuranEnc("{\"result\":\"no\"}", 112))
        assertNull(TafsirFormat.parseQuranEnc("<html>", 112))
    }

    @Test
    fun theVersionIsReadFromTheListOfQuranEncWhenTheKeyIsThere() {
        val list = "{\"translations\":[{\"key\":\"turkish_rwwad\",\"version\":\"1.0.4\"},{\"key\":\"turkish_mokhtasar\",\"version\":\"2.1.0\"}]}"
        assertEquals("2.1.0", TafsirFormat.quranEncVersion(list, "turkish_mokhtasar"))
        assertNull(TafsirFormat.quranEncVersion(list, "uzbek_mokhtasar"))
        assertNull(TafsirFormat.quranEncVersion("nope", "turkish_mokhtasar"))
    }

    @Test
    fun onlyRussianIsInTheLanguageOfASource() {
        assertTrue(TafsirSource.isInLanguageOf(TafsirSource.Saadi, AppLanguage.RU))
        assertTrue(TafsirSource.isInLanguageOf(TafsirSource.IbnKathir, AppLanguage.EN))
        assertFalse(TafsirSource.isInLanguageOf(TafsirSource.Saadi, AppLanguage.TG))
        assertFalse(TafsirSource.isInLanguageOf(TafsirSource.IbnKathir, AppLanguage.TR))
        assertFalse(TafsirSource.isInLanguageOf(TafsirSource.Muyassar, AppLanguage.RU))
    }

    @Test
    fun theKeysAreStableAndUnique() {
        assertEquals(
            listOf("saadi_ru", "ibn_kathir_en", "muyassar_ar", "mokhtasar_ru", "mokhtasar_en", "mokhtasar_tr", "mokhtasar_uz"),
            TafsirSource.entries.map { it.key },
        )
        assertEquals(
            listOf("170", "169", "16", "russian_mokhtasar", "english_mokhtasar", "turkish_mokhtasar", "uzbek_mokhtasar"),
            TafsirSource.entries.map { it.remoteId },
        )
        TafsirSource.entries.forEach { assertEquals(it, TafsirSource.byKey(it.key)) }
        assertNull(TafsirSource.byKey("nope"))
        assertNull(TafsirSource.byKey(null))
    }
}
