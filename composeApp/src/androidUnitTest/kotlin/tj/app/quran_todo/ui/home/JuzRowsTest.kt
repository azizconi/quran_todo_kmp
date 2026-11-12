package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.common.JuzIndex
import tj.app.quran_todo.ui.i18n.mushafCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rows of the "Juz" tab (subtitle, memorized share) and its search. */
class JuzRowsTest {
    private val names = mapOf(
        1 to "Al-Fatihah", 2 to "Al-Baqarah", 3 to "Ali 'Imran", 67 to "Al-Mulk", 77 to "Al-Mursalat",
        78 to "An-Naba", 114 to "An-Nas",
    )

    private val surahRows: Map<Int, SurahRowUi> = (1..114).associateWith { n ->
        val name = names[n] ?: "Surah $n"
        SurahRowUi(n, name, name, "", "", QuranMeta.ayahCount(n), null, 0f, canOpen = true, isPinned = false)
    }

    private fun rows(
        covered: Map<Int, Set<Int>> = emptyMap(),
        language: AppLanguage = AppLanguage.EN,
        rowsByNumber: Map<Int, SurahRowUi> = surahRows,
    ) = JuzRows.build(language, rowsByNumber, covered)

    @Test
    fun thereAreThirtyRowsInOrderWithTheirArabicNames() {
        val all = rows()
        assertEquals((1..30).toList(), all.map { it.juz })
        assertEquals("عم", all[29].arabicName)
        assertEquals("الم", all[0].arabicName)
        assertEquals(6236, all.sumOf { it.ayahCount })
    }

    @Test
    fun theSubtitleSaysWhereTheJuzStartsAndEndsAndHowManyAyahsItHas() {
        assertEquals("An-Naba 1 — An-Nas 6 · 564 ayahs", rows()[29].subtitle)
        assertEquals("Al-Mulk 1 — Al-Mursalat 50 · ${JuzIndex.ayahCount(29)} ayahs", rows()[28].subtitle)
        // The second juz stays inside Al-Baqarah.
        assertEquals("Al-Baqarah 142–252 · 111 ayahs", rows()[1].subtitle)
        // Juz 3 crosses into Ali 'Imran.
        assertEquals("Al-Baqarah 253 — Ali 'Imran 92 · ${JuzIndex.ayahCount(3)} ayahs", rows()[2].subtitle)
    }

    @Test
    fun theSubtitleFollowsTheLanguageOfTheApp() {
        val russian = surahRows.mapValues { (n, row) -> row.copy(name = if (n == 78) "Ан-Наба" else if (n == 114) "Ан-Нас" else row.name) }
        assertEquals("Ан-Наба 1 — Ан-Нас 6 · 564 аята", rows(language = AppLanguage.RU, rowsByNumber = russian)[29].subtitle)
        assertEquals("An-Naba 1 — An-Nas 6 · 564 oyat", rows(language = AppLanguage.UZ)[29].subtitle)
        assertEquals("An-Naba 1 — An-Nas 6 · 564 ayet", rows(language = AppLanguage.TR)[29].subtitle)
    }

    @Test
    fun theMemorizedShareCountsInsideTheJuzOnly() {
        // Al-Baqarah 142..252 (all of juz 2), the first 40 ayahs of An-Naba and all of An-Nas; the rest of Al-Baqarah is not known.
        val covered = mapOf(2 to (142..252).toSet(), 78 to (1..40).toSet(), 114 to (1..6).toSet())
        val all = rows(covered)
        assertEquals(111, all[1].coveredCount)
        assertEquals(1f, all[1].fraction)
        assertEquals(SurahTodoStatus.LEARNED, all[1].status)

        assertEquals(46, all[29].coveredCount)
        assertEquals(46f / 564f, all[29].fraction)
        assertEquals(SurahTodoStatus.LEARNING, all[29].status)

        // Nothing of Al-Baqarah before the 142nd ayah is memorized: juz 1 is untouched, juz 3 has none of it either.
        assertEquals(0, all[0].coveredCount)
        assertEquals(null, all[0].status)
        assertEquals(0f, all[0].fraction)
        assertEquals(0, all[2].coveredCount)
    }

    @Test
    fun aSurahThatIsKnownByHeartFillsEveryJuzItTouches() {
        val covered = mapOf(2 to (1..286).toSet())
        val all = rows(covered)
        assertEquals(141, all[0].coveredCount)
        assertEquals(111, all[1].coveredCount)
        assertEquals(SurahTodoStatus.LEARNED, all[1].status)
        assertEquals(34, all[2].coveredCount) // 253..286
        assertEquals(SurahTodoStatus.LEARNING, all[2].status)
    }

    @Test
    fun aRowOpensOnlyWhenItsFirstSurahCanBeOpened() {
        val closed = surahRows + (78 to surahRows.getValue(78).copy(canOpen = false))
        val all = rows(rowsByNumber = closed)
        assertFalse(all[29].canOpen)
        assertTrue(all[28].canOpen)
        assertEquals(78, all[29].startSurah)
        assertEquals(1, all[29].startAyah)
        assertEquals(2, all[1].startSurah)
        assertEquals(142, all[1].startAyah)
    }

    // -- Search ----------------------------------------------------------------------------------

    private fun found(query: String, language: AppLanguage = AppLanguage.EN): List<Int> =
        JuzRows.search(rows(language = language), query).map { it.juz }

    @Test
    fun aBlankQueryKeepsEveryRow() {
        assertEquals((1..30).toList(), found(""))
        assertEquals((1..30).toList(), found("   "))
        assertEquals((1..30).toList(), found("juz"))
    }

    @Test
    fun theNumberAndJuzNumberFindTheJuz() {
        assertEquals(listOf(30), found("30"))
        assertEquals(listOf(30), found("juz 30"))
        assertEquals(listOf(30), found("Juz 30"))
        assertEquals(listOf(30), found("juz30"))
        assertEquals(listOf(30), found("джуз 30"))
        assertEquals(listOf(30), found("Джуз 30"))
        assertEquals(listOf(29), found("ҷузъ 29"))
        // The titles of the rows in each language are found as they are written: "Ҷузъи 29", "29-juz", "29. Cüz".
        assertEquals(listOf(29), found("Ҷузъи 29"))
        assertEquals(listOf(29), found("29-juz"))
        assertEquals(listOf(29), found("29. Cüz"))
        assertEquals(listOf(9), found("9 juz"))
        assertEquals(listOf(5), found("cüz 5"))
        assertEquals(listOf(7), found("جزء 7"))
        assertEquals(listOf(3), found("3"))
        assertEquals(listOf(3), found("٣")) // Arabic-Indic digit
    }

    @Test
    fun theTitleOfEveryRowInEveryLanguageFindsThatRow() {
        AppLanguage.entries.forEach { language ->
            val copy = mushafCopy(language)
            val all = rows(language = language)
            (1..30).forEach { juz ->
                assertEquals(listOf(juz), JuzRows.search(all, copy.juzLabel(juz)).map { it.juz }, "$language ${copy.juzLabel(juz)}")
            }
        }
    }

    @Test
    fun aNumberOutsideTheQuranFindsNothing() {
        assertEquals(emptyList(), found("31"))
        assertEquals(emptyList(), found("0"))
        assertEquals(emptyList(), found("juz 99"))
        assertEquals(emptyList(), found("juz 3 0"))
    }

    @Test
    fun anAyahReferenceFindsTheJuzThatHoldsIt() {
        assertEquals(listOf(3), found("2:255"))
        assertEquals(listOf(2), found("2:142"))
        assertEquals(listOf(1), found("2:141"))
        assertEquals(listOf(30), found("114:6"))
    }

    @Test
    fun theArabicNameAndTheFirstAndLastSurahFindTheJuz() {
        assertEquals(listOf(30), found("عم"))
        assertEquals(listOf(30), found("naba"))
        assertEquals(listOf(30), found("an-nas"))
        assertEquals(listOf(1, 2, 3), found("baqarah"))
        assertEquals(emptyList(), found("nothing like that"))
    }

    // -- The state of the screen ----------------------------------------------------------------

    @Test
    fun theHomeStateCarriesTheRowsOfTheJuzTab() {
        val index = QuranIndex.standard()
        val sources = HomeSources(
            language = AppLanguage.EN,
            surahList = emptyList(),
            index = index,
            statuses = mapOf(114 to SurahHifzStatus.LEARNED),
            coveredAyahs = mapOf(114 to 6),
            planQueue = emptyList(),
            lesson = null,
            chapterNames = mapOf(
                78 to ChapterNameModel("النبأ", "An-Naba", "The Tidings"),
                114 to ChapterNameModel("الناس", "An-Nas", "Mankind"),
            ),
            loadState = QuranLoadState.Content,
            lastRead = null,
            recentSurahs = emptyList(),
            pinnedSurahs = emptySet(),
            onboardingGoals = emptySet(),
            coveredAyahSets = mapOf(114 to (1..6).toSet()),
            learnedAyahSets = mapOf(114 to (1..6).toSet()),
        )
        val base = HomeStateBuilder.buildBase(sources)
        assertEquals(30, base.juzRows.size)
        assertEquals("An-Naba 1 — An-Nas 6 · 564 ayahs", base.juzRows[29].subtitle)
        assertEquals(6, base.juzRows[29].coveredCount)
        assertEquals(0, base.juzRows[0].coveredCount)
    }

    @Test
    fun theSnapshotOfTheHifzModelDerivesTheCountsFromTheCoveredAyahs() {
        val snapshot = HifzSnapshot(coveredSets = mapOf(2 to (1..10).toSet(), 114 to (1..6).toSet()))
        assertEquals(mapOf(2 to 10, 114 to 6), snapshot.covered)
    }
}
