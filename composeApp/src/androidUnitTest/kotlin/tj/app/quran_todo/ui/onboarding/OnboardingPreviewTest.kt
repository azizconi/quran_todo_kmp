package tj.app.quran_todo.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingPreviewTest {
    private val basmala = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

    @Test
    fun basmalaPrefixOfTheFirstAyahIsCut() {
        assertEquals("ٱلرَّحْمَٰنُ", stripBasmala("$basmala ٱلرَّحْمَٰنُ"))
        assertEquals("الٓمٓ", stripBasmala("$basmala الٓمٓ"))
    }

    @Test
    fun plainLettersAndAlefWaslaVariantsMatch() {
        assertEquals("الم", stripBasmala("بسم الله الرحمن الرحيم الم"))
        assertEquals("الم", stripBasmala("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَـٰنِ ٱلرَّحِيمِ الم"))
    }

    @Test
    fun oddWhitespaceBetweenWordsIsTolerated() {
        assertEquals("ٱلرَّحْمَٰنُ", stripBasmala("  بِسْمِ ٱللَّهِ  ٱلرَّحْمَٰنِ\tٱلرَّحِيمِ ٱلرَّحْمَٰنُ "))
    }

    @Test
    fun textWithoutTheBasmalaIsUntouched() {
        val text = "ٱلرَّحْمَٰنُ عَلَّمَ ٱلْقُرْءَانَ خَلَقَ ٱلْإِنسَٰنَ عَلَّمَهُ ٱلْبَيَانَ"
        assertEquals(text, stripBasmala(text))
    }

    @Test
    fun theBasmalaAloneIsKeptSoNothingIsLeftEmpty() {
        assertEquals(basmala, stripBasmala(basmala))
    }

    @Test
    fun fallbackPreviewIsAyahsOneToFour() {
        assertEquals(listOf(1, 2, 3, 4), FallbackRahmanAyahs.map { it.number })
        assertTrue(FallbackRahmanAyahs.none { it.text.isBlank() })
    }

    @Test
    fun fallbackStoryTextIsTheFirstThreeAyahsOfAlFatihah() {
        assertEquals(listOf(1, 2, 3), FallbackFatihaAyahs.map { it.number })
        assertTrue(FallbackFatihaAyahs.none { it.text.isBlank() })
        // The loaded Quran has nothing yet: the slides use the sample.
        assertEquals(null, fatihaPreview(emptyList()))
    }

    @Test
    fun themeSegmentFollowsSystemFirst() {
        assertEquals(2, themeSegment(followsSystem = true, mode = tj.app.quran_todo.common.theme.ThemeMode.DARK))
        assertEquals(1, themeSegment(followsSystem = false, mode = tj.app.quran_todo.common.theme.ThemeMode.DARK))
        assertEquals(0, themeSegment(followsSystem = false, mode = tj.app.quran_todo.common.theme.ThemeMode.LIGHT))
    }
}
