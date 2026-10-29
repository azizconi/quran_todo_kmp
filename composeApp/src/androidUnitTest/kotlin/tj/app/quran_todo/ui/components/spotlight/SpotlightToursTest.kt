package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.ui.graphics.Color
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.settingsCopy
import tj.app.quran_todo.ui.i18n.spotlightCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The tours as data, and their words. */
class SpotlightToursTest {
    @Test
    fun everyTourHasAUniqueIdAndAtLeastOneStep() {
        val ids = SpotlightTours.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        SpotlightTours.all.forEach { assertTrue(it.steps.isNotEmpty(), it.id) }
    }

    @Test
    fun theToursAreTheOnesOfTheBrief() {
        assertEquals(4, SpotlightTours.Home.steps.size)
        assertEquals(5, SpotlightTours.Reader.steps.size)
        assertEquals(2, SpotlightTours.Player.steps.size)
        // The lesson is split in three: the header, the hidden ayah, the two answers.
        assertEquals(1, SpotlightTours.LessonHeader.steps.size)
        assertEquals(1, SpotlightTours.LessonPeek.steps.size)
        assertEquals(1, SpotlightTours.LessonGrade.steps.size)
        assertEquals(setOf(SpotlightScreen.Lesson), listOf(SpotlightTours.LessonHeader, SpotlightTours.LessonPeek, SpotlightTours.LessonGrade).map { it.screen }.toSet())
    }

    @Test
    fun everyTipIsUsedByOneTour() {
        val used = SpotlightTours.all.flatMap { tour -> tour.steps.map { it.tip } }
        assertEquals(SpotlightTip.entries.toSet(), used.toSet())
        assertEquals(used.size, used.toSet().size)
    }

    @Test
    fun everyLanguageSaysEveryTip() {
        AppLanguage.entries.forEach { language ->
            val copy = spotlightCopy(language)
            assertEquals(SpotlightTip.entries.toSet(), copy.covers(), "$language")
            SpotlightTip.entries.forEach { tip ->
                val text = copy.tip(tip)
                assertTrue(text.title.isNotBlank(), "$language $tip title")
                assertTrue(text.body.isNotBlank(), "$language $tip body")
                assertTrue('{' !in text.title + text.body, "$language $tip leaves a placeholder")
            }
            listOf(copy.next, copy.gotIt, copy.skip).forEach { assertTrue(it.isNotBlank(), "$language buttons") }
        }
    }

    @Test
    fun theLanguagesAreTranslatedNotCopied() {
        val english = spotlightCopy(AppLanguage.EN)
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = spotlightCopy(language)
            val same = SpotlightTip.entries.count { other.tip(it) == english.tip(it) }
            assertEquals(0, same, "$language shares tips with English")
            assertNotEquals(english.next, other.next, "$language next")
        }
    }

    @Test
    fun theRussianTextIsTheOneOfTheBrief() {
        val copy = spotlightCopy(AppLanguage.RU)
        assertEquals("Удерживайте суру", copy.tip(SpotlightTip.HomeSurah).title)
        assertEquals("Звук начнётся с этого аята", copy.tip(SpotlightTip.ReaderTap).body)
        assertEquals("Потяните вверх: повтор отрывка, паузы и скорость", copy.tip(SpotlightTip.ReaderPlayer).body)
        assertEquals("От ответа зависит, когда отрывок вернётся", copy.tip(SpotlightTip.LessonGrade).body)
        assertEquals("Время повторить за чтецом", copy.tip(SpotlightTip.PlayerPause).body)
    }

    @Test
    fun theNextButtonShowsTheStepNumber() {
        assertEquals("Далее · 2/4", spotlightCopy(AppLanguage.RU).nextLabel(2, 4))
        assertEquals("Next · 1/5", spotlightCopy(AppLanguage.EN).nextLabel(1, 5))
    }

    @Test
    fun noStarsAnywhereInTheWords() {
        // The owner forbids stars in any form: no star characters and no sparkles in the copy.
        val forbidden = "★☆✦✧✩✪✫✬✭✮✯✰⭐🌟✨⋆*".toSet()
        AppLanguage.entries.forEach { language ->
            val copy = spotlightCopy(language)
            val all = SpotlightTip.entries.flatMap { listOf(copy.tip(it).title, copy.tip(it).body) } +
                listOf(copy.next, copy.gotIt, copy.skip) +
                settingsCopy(language).let { listOf(it.showTipsAgain, it.showTipsAgainDescription, it.tipsWillShowAgain) }
            all.forEach { text -> assertTrue(text.none { it in forbidden }, "$language: $text") }
        }
    }

    @Test
    fun theSettingsRowIsFilledInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = settingsCopy(language)
            assertTrue(copy.showTipsAgain.isNotBlank(), "$language title")
            assertTrue(copy.showTipsAgainDescription.isNotBlank(), "$language description")
            assertTrue(copy.tipsWillShowAgain.isNotBlank(), "$language toast")
        }
        assertEquals("Показать подсказки снова", settingsCopy(AppLanguage.RU).showTipsAgain)
        assertEquals("Подсказки покажутся снова", settingsCopy(AppLanguage.RU).tipsWillShowAgain)
    }

    @Test
    fun theScrimIsTheInkOfTheThemeAtSeventyPercentInBothThemes() {
        val ink = Color(0xFF16201C)
        val nightInk = Color(0xFFECF2EE)
        val bg = Color(0xFFF6F3EC)
        val nightBg = Color(0xFF0D1311)
        val day = scrimColorOf(ink, bg, dark = false)
        assertEquals(ink.red, day.red, 0.01f)
        assertEquals(0.7f, day.alpha, 0.01f)
        // By night the ink is light: the veil takes the dark paper instead, so it still darkens.
        val night = scrimColorOf(nightInk, nightBg, dark = true)
        assertEquals(0.7f, night.alpha, 0.01f)
        assertTrue(night.red < 0.1f && night.green < 0.1f && night.blue < 0.1f, "the night veil must be dark: $night")
    }
}
