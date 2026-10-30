package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.downloads.sizeMbBucket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DownloadsCopyTest {
    private val mb = 1024L * 1024L

    @Test
    fun sizesAreShownInTheUnitsAndWithTheSeparatorOfTheLanguage() {
        val ru = downloadsCopy(AppLanguage.RU)
        assertEquals("0 КБ", ru.sizeLabel(0))
        assertEquals("120 КБ", ru.sizeLabel(120 * 1024L))
        assertEquals("2,4 МБ", ru.sizeLabel((2.4 * mb).toLong()))
        assertEquals("54 МБ", ru.sizeLabel(54 * mb))
        assertEquals("230 МБ", ru.sizeLabel(230 * mb))
        assertEquals("1,4 ГБ", ru.sizeLabel(1_450 * mb))
        assertEquals("54 MB", downloadsCopy(AppLanguage.EN).sizeLabel(54 * mb))
        assertEquals("2.4 MB", downloadsCopy(AppLanguage.EN).sizeLabel((2.4 * mb).toLong()))
        assertEquals("1.0 GB", downloadsCopy(AppLanguage.EN).sizeLabel(1_024 * mb))
        assertEquals("54 MB", downloadsCopy(AppLanguage.UZ).sizeLabel(54 * mb))
        assertEquals("1,2 GB", downloadsCopy(AppLanguage.TR).sizeLabel(1_229 * mb))
    }

    @Test
    fun theBoundariesBetweenUnitsRoundUpToTheNextUnit() {
        val en = downloadsCopy(AppLanguage.EN)
        assertEquals("1.0 MB", en.sizeLabel(mb))
        // 1023.6 KB rounds to 1024 KB: a size just under a megabyte is a megabyte, not "1024 KB".
        assertEquals("1.0 MB", en.sizeLabel(mb - 100))
        assertEquals("10 MB", en.sizeLabel(10 * mb))
    }

    @Test
    fun anEstimateStartsWithTheApproximatelySign() {
        assertEquals("≈ 54 МБ", downloadsCopy(AppLanguage.RU).estimateLabel(54 * mb))
        assertEquals("≈ 54 MB", downloadsCopy(AppLanguage.TR).estimateLabel(54 * mb))
    }

    @Test
    fun theQueueTitleMatchesTheNotificationOfTheBrief() {
        assertEquals("Загрузка: Джуз Амма · 120 из 564", downloadsCopy(AppLanguage.RU).notificationTitle("Джуз Амма", 120, 564))
        assertEquals("Downloading: Juz Amma · 120 of 564", downloadsCopy(AppLanguage.EN).queueTitleLabel("Juz Amma", 120, 564))
    }

    @Test
    fun rowStatesCarryTheirNumbers() {
        val ru = downloadsCopy(AppLanguage.RU)
        assertEquals("Скачано 120 из 564 · 12 МБ", ru.rowPartialLabel(120, 564, 12 * mb))
        assertEquals("Скачано · 54 МБ", ru.rowCompleteLabel(54 * mb))
        assertEquals("Скачиваем 3 из 4", ru.rowDownloadingLabel(3, 4))
    }

    @Test
    fun theFailureLineUsesThePluralFormsOfTheLanguage() {
        val ru = downloadsCopy(AppLanguage.RU)
        assertEquals("1 аят не скачался", ru.failureLabel(1, 0))
        assertEquals("21 аят не скачался", ru.failureLabel(21, 0))
        assertEquals("3 аята не скачались", ru.failureLabel(3, 0))
        assertEquals("5 аятов не скачались", ru.failureLabel(5, 0))
        assertEquals("11 аятов не скачались", ru.failureLabel(11, 0))
        assertEquals("Перевод не скачался", ru.failureLabel(0, 2))
        assertEquals("3 аята не скачались · Перевод не скачался", ru.failureLabel(3, 1))
        assertEquals("", ru.failureLabel(0, 0))
        assertEquals("1 ayah did not download", downloadsCopy(AppLanguage.EN).failureLabel(1, 0))
        assertEquals("3 ayahs did not download", downloadsCopy(AppLanguage.EN).failureLabel(3, 0))
        assertEquals("3 oyat yuklanmadi", downloadsCopy(AppLanguage.UZ).failureLabel(3, 0))
        assertEquals("3 ayet indirilemedi", downloadsCopy(AppLanguage.TR).failureLabel(3, 0))
        assertEquals("3 оят боргирӣ нашуд", downloadsCopy(AppLanguage.TG).failureLabel(3, 0))
    }

    @Test
    fun theJuzTitlesKeepTheNamesOfTheTwoFamousOnes() {
        val ru = downloadsCopy(AppLanguage.RU)
        assertEquals("Джуз Амма", ru.juzTitleLabel(30))
        assertEquals("Джуз Таборак", ru.juzTitleLabel(29))
        assertEquals("Джуз 7", ru.juzTitleLabel(7))
        assertEquals("Juz 12", downloadsCopy(AppLanguage.EN).juzTitleLabel(12))
        assertEquals("7. cüz", downloadsCopy(AppLanguage.TR).juzTitleLabel(7))
        assertEquals("7-juz", downloadsCopy(AppLanguage.UZ).juzTitleLabel(7))
        assertEquals("Суры 78–114", ru.juzSurahsLabel(78, 114))
        assertEquals("Сура 2", ru.juzSurahsLabel(2, 2))
    }

    @Test
    fun theTitleOfTheJuzMatchesThePlan() {
        // Juz Amma and Juz Tabarak are named in "My plan" too; the screen must say the same words.
        for (language in AppLanguage.entries) {
            assertEquals(planCopy(language).scopeJuz30, downloadsCopy(language).juzTitleLabel(30), "juz 30 in $language")
            assertEquals(planCopy(language).scopeJuz29, downloadsCopy(language).juzTitleLabel(29), "juz 29 in $language")
        }
    }

    @Test
    fun theSubtitleOfThePlanCountsSurahsInTheLanguage() {
        assertEquals("3 суры", downloadsCopy(AppLanguage.RU).planAllSubtitleLabel(3))
        assertEquals("1 surah", downloadsCopy(AppLanguage.EN).planAllSubtitleLabel(1))
    }

    @Test
    fun everyLanguageHasEveryStringAndTheirPlaceholders() {
        for (language in AppLanguage.entries) {
            val copy = downloadsCopy(language)
            copy.javaClass.declaredFields.filter { it.type == String::class.java }.forEach { field ->
                field.isAccessible = true
                assertTrue((field.get(copy) as String).isNotBlank(), "$language: ${field.name} is blank")
            }
            assertTrue("{title}" in copy.queueTitle && "{done}" in copy.queueTitle && "{total}" in copy.queueTitle, "queueTitle/$language")
            assertTrue("{language}" in copy.withTranslation, "withTranslation/$language")
            assertTrue("{n}" in copy.queueWaiting && "{n}" in copy.notificationQueued, "queueWaiting/$language")
            assertTrue("{size}" in copy.queueRemaining && "{size}" in copy.rowEstimate, "size placeholders/$language")
            assertTrue("{done}" in copy.rowPartial && "{total}" in copy.rowPartial && "{size}" in copy.rowPartial, "rowPartial/$language")
            assertTrue("{title}" in copy.deleteBody && "{size}" in copy.deleteBody, "deleteBody/$language")
            assertTrue("{size}" in copy.deleteAllBody, "deleteAllBody/$language")
            assertTrue("{a}" in copy.juzSurahs && "{b}" in copy.juzSurahs && "{a}" in copy.juzSurahsOne, "juzSurahs/$language")
            assertTrue("{n}" in copy.juzTitle, "juzTitle/$language")
            // Filled patterns leave no braces behind.
            assertTrue("{" !in copy.queueTitleLabel("X", 1, 2), "queueTitleLabel/$language")
            assertTrue("{" !in copy.rowPartialLabel(1, 2, mb), "rowPartialLabel/$language")
            assertTrue("{" !in copy.deleteBodyLabel("X", mb), "deleteBodyLabel/$language")
            assertTrue("{" !in copy.failureLabel(3, 1), "failureLabel/$language")
            assertTrue("{" !in copy.withTranslationLabel("X"), "withTranslationLabel/$language")
        }
    }

    @Test
    fun translationsAreNotCopiesOfTheEnglishText() {
        val english = downloadsCopy(AppLanguage.EN)
        for (language in listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR)) {
            val other = downloadsCopy(language)
            assertNotEquals(english.title, other.title, "title/$language")
            assertNotEquals(english.pauseAll, other.pauseAll, "pauseAll/$language")
            assertNotEquals(english.wifiOnly, other.wifiOnly, "wifiOnly/$language")
            assertNotEquals(english.storageDeleteAll, other.storageDeleteAll, "storageDeleteAll/$language")
        }
    }

    @Test
    fun theSizeBucketOfTheAnalyticsHasNoExactSize() {
        assertEquals("lt_1", sizeMbBucket(100_000))
        assertEquals("1_10", sizeMbBucket(2 * mb))
        assertEquals("10_50", sizeMbBucket(30 * mb))
        assertEquals("50_100", sizeMbBucket(54 * mb))
        assertEquals("100_500", sizeMbBucket(200 * mb))
        assertEquals("500_plus", sizeMbBucket(1_800 * mb))
    }
}
