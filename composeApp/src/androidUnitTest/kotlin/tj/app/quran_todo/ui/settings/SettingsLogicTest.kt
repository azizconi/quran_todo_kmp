package tj.app.quran_todo.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.ui.i18n.onboardingCopy
import tj.app.quran_todo.ui.i18n.settingsCopy

class SettingsLogicTest {
    @Test
    fun speedAndRepeatLabelsMatchTheChips() {
        assertEquals("0.75×", speedLabel(0.75f))
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("2×", speedLabel(2f))
        assertEquals("1×", repeatLabel(1))
        assertEquals("5×", repeatLabel(5))
        assertEquals("∞", repeatLabel(SurahReaderPreferences.RepeatForever))
    }

    @Test
    fun syncStatusLinesOfTheSharedViewModelAreRecognised() {
        assertEquals(SettingsEvent.BackupDone, classifySyncMessage("Synced to Google backup."))
        assertEquals(SettingsEvent.RestoreDone, classifySyncMessage("Restored from Google backup."))
        assertEquals(SettingsEvent.NoBackup, classifySyncMessage("No cloud snapshot found."))
        assertEquals(SettingsEvent.BackupFailed, classifySyncMessage("Cloud sync failed."))
        assertEquals(SettingsEvent.RestoreFailed, classifySyncMessage("Cloud restore failed."))
        assertEquals(SettingsEvent.Raw("Something else"), classifySyncMessage("Something else"))
    }

    @Test
    fun offlineProgressMapsToTheRowState() {
        assertEquals(OfflineUi.Running(3, 10), OfflineProgress(true, 3, 10, "DOWNLOADING").toUi())
        assertEquals(OfflineUi.Ready, OfflineProgress(false, 10, 10, "READY").toUi())
        assertEquals(OfflineUi.Partial, OfflineProgress(false, 7, 10, "PARTIAL").toUi())
        assertEquals(OfflineUi.Idle, OfflineProgress(false, 0, 0, null).toUi())
    }

    @Test
    fun segmentIconsAreKeptOnlyWhenEveryLabelFitsNextToThem() {
        // 300px control, 4px insets: 97.3px per segment; padding 16 + icon 18 + gap 6 + margin 2 = 42px.
        assertTrue(segmentsFitWithIcons(listOf(50f, 40f, 55f), controlWidthPx = 300f, density = 1f))
        assertFalse(segmentsFitWithIcons(listOf(50f, 40f, 56f), controlWidthPx = 300f, density = 1f))
        // The same labels at 2x density and a proportionally wider control give the same answer.
        assertFalse(segmentsFitWithIcons(listOf(100f, 80f, 112f), controlWidthPx = 600f, density = 2f))
        assertTrue(segmentsFitWithIcons(listOf(100f, 80f, 110f), controlWidthPx = 600f, density = 2f))
    }

    @Test
    fun systemThemeLabelIsShortInEveryLanguage() {
        assertEquals("Система", settingsCopy(AppLanguage.RU).themeSystem)
        assertEquals("Система", settingsCopy(AppLanguage.TG).themeSystem)
        assertEquals("Tizim", settingsCopy(AppLanguage.UZ).themeSystem)
        assertEquals("Sistem", settingsCopy(AppLanguage.TR).themeSystem)
        assertEquals("System", settingsCopy(AppLanguage.EN).themeSystem)
    }

    @Test
    fun everyLanguageHasEveryString() {
        AppLanguage.entries.forEach { language ->
            assertNoBlankStrings(settingsCopy(language), "settings/$language")
            assertNoBlankStrings(onboardingCopy(language), "onboarding/$language")
        }
    }

    @Test
    fun templatesKeepTheirPlaceholdersInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val settings = settingsCopy(language)
            assertTrue("{done}" in settings.offlineProgress && "{total}" in settings.offlineProgress, "offlineProgress/$language")
            assertTrue("12" in settings.offlineProgressLabel(12, 114) && "114" in settings.offlineProgressLabel(12, 114))
            val onboarding = onboardingCopy(language)
            assertTrue("{n}" in onboarding.stepOf && "{total}" in onboarding.stepOf, "stepOf/$language")
            assertTrue("2" in onboarding.stepLabel(2, 4) && "4" in onboarding.stepLabel(2, 4))
        }
    }

    @Test
    fun translationsAreNotCopiesOfTheEnglishText() {
        val english = settingsCopy(AppLanguage.EN)
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = settingsCopy(language)
            assertTrue(other.sectionAppearance != english.sectionAppearance, "sectionAppearance/$language")
            assertTrue(other.dailyGoal != english.dailyGoal, "dailyGoal/$language")
            assertTrue(other.offlineTitle != english.offlineTitle, "offlineTitle/$language")
        }
    }

    private fun assertNoBlankStrings(copy: Any, where: String) {
        copy.javaClass.declaredFields
            .filter { it.type == String::class.java }
            .forEach { field ->
                field.isAccessible = true
                val value = field.get(copy) as String
                assertTrue(value.isNotBlank(), "$where: ${field.name} is blank")
            }
    }
}
