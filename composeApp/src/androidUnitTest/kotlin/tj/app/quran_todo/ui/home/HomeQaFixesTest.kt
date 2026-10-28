package tj.app.quran_todo.ui.home

import androidx.compose.ui.unit.dp
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.home.components.heroPositionText
import tj.app.quran_todo.ui.home.components.listBottomPadding
import tj.app.quran_todo.ui.home.components.miniPlayerTop
import tj.app.quran_todo.ui.home.components.progressPercent
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.state.LastReadPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeQaFixesTest {
    private fun continueItem(fresh: Boolean, ayah: Int = 1) = ContinueUi(
        surah = 1,
        name = "Аль-Фатиха",
        arabicName = "الفاتحة",
        translatedName = "Открывающая",
        ayah = ayah,
        ayahCount = 7,
        isFresh = fresh,
        canOpen = true,
    )

    // -- Hero --------------------------------------------------------------------------------------

    @Test
    fun theHeroSubtitleReadsAyahNOfMOnceAPositionExists() {
        assertEquals("Аят 5 из 7", heroPositionText(continueItem(fresh = false, ayah = 5), homeCopy(AppLanguage.RU)))
        assertEquals("Ayah 5 of 7", heroPositionText(continueItem(fresh = false, ayah = 5), homeCopy(AppLanguage.EN)))
    }

    @Test
    fun aFreshHeroDescribesAlFatihahInstead() {
        assertEquals("Открывающая · 7 аятов", heroPositionText(continueItem(fresh = true), homeCopy(AppLanguage.RU)))
    }

    @Test
    fun aSavedPositionTurnsTheFreshHeroIntoContinue() {
        val rows = (1..2).associateWith { n ->
            SurahRowUi(n, "S$n", "S$n", "", "", 7, null, 0f, canOpen = true, isPinned = false)
        }
        val index = QuranIndex.fromCounts(mapOf(1 to 7, 2 to 10))
        assertTrue(HomeRows.resolveContinue(null, index, rows)!!.isFresh)
        val saved = HomeRows.resolveContinue(LastReadPosition(1, 3, null), index, rows)!!
        assertFalse(saved.isFresh)
        assertEquals(3, saved.ayah)
    }

    @Test
    fun theOldAudioPositionStandsInUntilTheReaderSavedOne() {
        // Nothing saved by the reader, audio was on global ayah 4 of surah 1: continue there.
        val legacy = HomeStateBuilder.lastReadWithLegacy(saved = null, legacySurah = 1, legacyAyah = 4)!!
        assertEquals(1, legacy.surah)
        assertNull(legacy.ayahInSurah)
        assertEquals(4, legacy.legacyGlobalAyah)

        // A legacy-only value from UiPrefs is replaced by the current audio position.
        val refreshed = HomeStateBuilder.lastReadWithLegacy(LastReadPosition(1, null, 2), legacySurah = 2, legacyAyah = 9)!!
        assertEquals(2, refreshed.surah)
        assertEquals(9, refreshed.legacyGlobalAyah)

        // The reader's own position always wins.
        val own = LastReadPosition(36, 12, null)
        assertEquals(own, HomeStateBuilder.lastReadWithLegacy(own, legacySurah = 1, legacyAyah = 3))

        // No audio either: nothing, so the hero starts fresh.
        assertNull(HomeStateBuilder.lastReadWithLegacy(null, null, null))
    }

    // -- Progress ring -----------------------------------------------------------------------------

    @Test
    fun theRingShowsAtLeastOnePercentOnceThereIsProgress() {
        assertEquals(0, progressPercent(0f))
        assertEquals(0, progressPercent(-1f))
        assertEquals(1, progressPercent(0.0004f))
        assertEquals(3, progressPercent(0.03f))
        assertEquals(100, progressPercent(1f))
        assertEquals(100, progressPercent(3f))
    }

    @Test
    fun theRingIsDescribedInEveryLanguage() {
        for (language in AppLanguage.entries) {
            val text = homeCopy(language).progressDescription(3)
            assertTrue(text.contains("3%"), "${language.name}: $text")
            assertFalse(text.contains("{"), "${language.name}: $text")
        }
        assertEquals("Прогресс: 3%", homeCopy(AppLanguage.RU).progressDescription(3))
        assertEquals("Progress: 3%", homeCopy(AppLanguage.EN).progressDescription(3))
    }

    // -- List bottom padding -----------------------------------------------------------------------

    @Test
    fun theLastRowScrollsAboveTheMiniPlayerAtAnyFontSize() {
        for (inset in listOf(0.dp, 24.dp, 48.dp)) {
            for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)) {
                val padding = listBottomPadding(inset, playerVisible = true, fontScale = scale)
                val playerTop = miniPlayerTop(inset, scale)
                assertTrue(padding > playerTop, "inset=$inset scale=$scale: $padding must clear the player top $playerTop")
            }
        }
    }

    @Test
    fun withoutAPlayerOnlyTheNavigationBarAndASmallGapRemain() {
        assertEquals(48.dp, listBottomPadding(24.dp, playerVisible = false, fontScale = 1f))
        assertTrue(
            listBottomPadding(24.dp, playerVisible = true, fontScale = 1f) >
                listBottomPadding(24.dp, playerVisible = false, fontScale = 1f),
        )
    }
}
