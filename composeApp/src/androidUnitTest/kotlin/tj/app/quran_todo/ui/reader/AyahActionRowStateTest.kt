package tj.app.quran_todo.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.ayahActionsCopy

/** Which ayah has its row of actions open (UX_PLAN U19). */
class AyahActionRowStateTest {
    @Test
    fun nothingIsOpenAtFirst() {
        assertNull(AyahActionRowState().openIndex)
    }

    @Test
    fun aTapOpensTheRow() {
        assertEquals(4, AyahActionRowState().tapped(4).openIndex)
    }

    @Test
    fun tappingTheSameAyahAgainClosesIt() {
        assertNull(AyahActionRowState().tapped(4).tapped(4).openIndex)
    }

    @Test
    fun tappingAnotherAyahMovesTheRow() {
        assertEquals(7, AyahActionRowState().tapped(4).tapped(7).openIndex)
    }

    @Test
    fun aSequenceOfTaps() {
        val state = AyahActionRowState().tapped(1).tapped(2).tapped(2).tapped(3).tapped(2)
        assertEquals(2, state.openIndex)
    }

    @Test
    fun scrollingAwayClosesOnlyWhenFarOff() {
        val open = AyahActionRowState().tapped(10)
        assertEquals(10, open.scrolledAway(8, 14).openIndex)
        assertEquals(10, open.scrolledAway(13, 18).openIndex) // 3 ayahs above the screen: still near
        assertNull(open.scrolledAway(14, 19).openIndex)
        assertEquals(10, open.scrolledAway(2, 7).openIndex)
        assertNull(open.scrolledAway(0, 6).openIndex)
    }

    @Test
    fun closingAClosedRowChangesNothing() {
        assertEquals(AyahActionRowState(), AyahActionRowState().closed())
        assertEquals(AyahActionRowState(), AyahActionRowState().scrolledAway(50, 55))
    }

    @Test
    fun everyLanguageHasTheTexts() {
        AppLanguage.entries.forEach { language ->
            val copy = ayahActionsCopy(language)
            listOf(copy.listen, copy.repeat, copy.more, copy.offerQuestion, copy.yes, copy.no)
                .forEach { assertTrue(it.isNotBlank(), language.name) }
        }
    }
}
