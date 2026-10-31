package tj.app.quran_todo.ui.settings

import kotlinx.datetime.TimeZone
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.hifz.backup.BackupError
import tj.app.quran_todo.hifz.backup.BackupPreview
import tj.app.quran_todo.hifz.backup.BackupSettings
import tj.app.quran_todo.hifz.backup.BackupSettingsRules
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.ui.i18n.transferCopy
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.ReaderLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The Android side of "Transfer and backup": the texts, the way settings are applied, the mapping of errors. */
class TransferLogicTest {
    private val utc = TimeZone.UTC

    // 2026-03-12T10:40:00Z
    private val march12 = 1_773_312_000_000

    // ---- Texts ----

    @Test
    fun everyLanguageHasEveryTransferString() {
        AppLanguage.entries.forEach { language ->
            val copy = transferCopy(language)
            copy.allStrings().forEach { assertTrue(it.isNotBlank(), "transfer/$language has a blank string") }
            assertTrue("{date}" in copy.lastBackup, "lastBackup/$language")
            assertTrue("{date}" in copy.previewCopyOf, "previewCopyOf/$language")
            assertTrue("{plan}" in copy.previewPlan, "previewPlan/$language")
        }
    }

    @Test
    fun theTransferTextsAreTranslated() {
        val english = transferCopy(AppLanguage.EN)
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = transferCopy(language)
            assertNotEquals(english.sectionTitle, other.sectionTitle, "sectionTitle/$language")
            assertNotEquals(english.saveTitle, other.saveTitle, "saveTitle/$language")
            assertNotEquals(english.restoreTitle, other.restoreTitle, "restoreTitle/$language")
            assertNotEquals(english.importNewer, other.importNewer, "importNewer/$language")
            assertNotEquals(english.footnote, other.footnote, "footnote/$language")
        }
    }

    @Test
    fun russianPreviewReadsLikeTheBrief() {
        val preview = BackupPreview(march12, HifzScope.JUZ_30, planSurahs = 37, passages = 34, hardAyahs = 2, notes = 120, reviews = 400)
        val ru = transferCopy(AppLanguage.RU)
        assertEquals("Копия от 12 марта 2026: план «Джуз Амма», 34 отрывка, 2 трудных аята, 120 заметок.", ru.previewSummary(preview, utc))
        assertEquals(
            "Копия от 12 марта 2026: план «Джуз Амма», 34 отрывка, 2 трудных аята, 120 заметок. " +
                "Текущие данные на этом телефоне будут заменены.",
            ru.importMessage(preview, utc),
        )
    }

    @Test
    fun russianPluralsFollowTheThreeForms() {
        fun passages(n: Int) = transferCopy(AppLanguage.RU).previewSummary(
            BackupPreview(march12, null, 0, passages = n, hardAyahs = 0, notes = 0, reviews = 0),
            utc,
        )
        assertTrue(passages(1).endsWith("1 отрывок."), passages(1))
        assertTrue(passages(2).endsWith("2 отрывка."), passages(2))
        assertTrue(passages(4).endsWith("4 отрывка."), passages(4))
        assertTrue(passages(5).endsWith("5 отрывков."), passages(5))
        assertTrue(passages(11).endsWith("11 отрывков."), passages(11))
        assertTrue(passages(12).endsWith("12 отрывков."), passages(12))
        assertTrue(passages(21).endsWith("21 отрывок."), passages(21))
        assertTrue(passages(34).endsWith("34 отрывка."), passages(34))
        assertTrue(passages(120).endsWith("120 отрывков."), passages(120))
        assertTrue(passages(101).endsWith("101 отрывок."), passages(101))
    }

    @Test
    fun partsThatAreZeroAreLeftOutAndAnEmptyFileSaysSo() {
        val en = transferCopy(AppLanguage.EN)
        assertEquals(
            "Copy of 12 March 2026: plan “From the start of the Quran”, 1 passage, 1 hard ayah, 1 note.",
            en.previewSummary(BackupPreview(march12, HifzScope.FROM_START, 114, 1, 1, 1, 0), utc),
        )
        assertEquals(
            "Copy of 12 March 2026: 5 passages.",
            en.previewSummary(BackupPreview(march12, null, 0, 5, 0, 0, 0), utc),
        )
        assertEquals(
            "Copy of 12 March 2026: it holds no memorization data.",
            en.previewSummary(BackupPreview(march12, null, 0, 0, 0, 0, 0), utc),
        )
    }

    @Test
    fun everyLanguageWritesTheSummaryWithItsNumbers() {
        val preview = BackupPreview(march12, HifzScope.JUZ_29, 11, 34, 2, 120, 5)
        AppLanguage.entries.forEach { language ->
            val copy = transferCopy(language)
            val text = copy.previewSummary(preview, utc)
            listOf("12", "2026", "34", "2", "120").forEach { number -> assertTrue(number in text, "$language: $number in $text") }
            assertTrue(copy.planName(HifzScope.JUZ_29) in text, "$language: plan name in $text")
            assertTrue(copy.previewReplace in copy.importMessage(preview, utc))
        }
    }

    @Test
    fun lastCopyShowsTheDayAndTheYearOnlyWhenItIsNotThisYear() {
        val ru = transferCopy(AppLanguage.RU)
        val march2027 = march12 + 365L * 86_400_000
        assertEquals("Последняя копия: 12 марта", ru.lastBackupLabel(march12, march12 + 3_600_000, utc))
        assertEquals("Последняя копия: 12 марта 2026", ru.lastBackupLabel(march12, march2027, utc))
        assertEquals("Last copy: 12 March", transferCopy(AppLanguage.EN).lastBackupLabel(march12, march12, utc))
    }

    @Test
    fun monthNamesCoverTheWholeYear() {
        AppLanguage.entries.forEach { language ->
            val copy = transferCopy(language)
            val names = (0 until 12).map { month ->
                // The 15th of each month of 2026 (non-leap year), midday UTC.
                val dayOfYear = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)[month] + 14
                copy.formatDate((20454L + dayOfYear) * 86_400_000 + 43_200_000, withYear = false, zone = utc)
            }
            assertEquals(12, names.toSet().size, "$language: $names")
            names.forEach { assertTrue(it.startsWith("15 "), "$language: $it") }
        }
    }

    // ---- Errors ----

    @Test
    fun everyKindOfErrorHasItsOwnToast() {
        assertEquals(ImportProblem.NotABackup, BackupError.NotABackup.toProblem())
        assertEquals(ImportProblem.NewerVersion, BackupError.NewerVersion(2).toProblem())
        assertEquals(ImportProblem.Corrupted, BackupError.Corrupted("x").toProblem())
        assertEquals(ImportProblem.OutOfRange, BackupError.OutOfRange("x").toProblem())
        AppLanguage.entries.forEach { language ->
            val copy = transferCopy(language)
            val texts = listOf(copy.importNotBackup, copy.importNewer, copy.importCorrupt, copy.importOutOfRange, copy.importUnreadable)
            assertEquals(texts.size, texts.toSet().size, "$language: the toasts differ")
        }
    }

    // ---- The names in the file match the app's enums ----

    @Test
    fun namesInTheFileRulesMatchTheEnumsOfTheApp() {
        assertEquals(ReaderLayout.entries.map { it.name }.toSet(), BackupSettingsRules.READER_LAYOUTS)
        assertEquals(HifzHintMode.entries.map { it.name }.toSet(), BackupSettingsRules.HINT_MODES)
        // The speeds and repeats of the reader are the ones the settings screen offers.
        assertEquals(setOf(0.75f, 1f, 1.25f, 1.5f, 2f), BackupSettingsRules.PLAYBACK_SPEEDS)
        assertEquals(setOf(1, 3, 5, SurahReaderPreferences.RepeatForever), BackupSettingsRules.REPEAT_COUNTS)
        assertTrue(MinReadingFontSize == BackupSettingsRules.READING_FONT_SIZE.first)
        assertTrue(MaxReadingFontSize == BackupSettingsRules.READING_FONT_SIZE.last)
    }

    // ---- Applying settings ----

    private class Recorder : TransferSettingsTarget {
        val calls = mutableListOf<String>()
        var reader = SurahReaderPreferences(
            showTranslation = false, autoplayOnOpen = false, autoAdvanceAyahs = true, playbackSpeed = 1f, repeatCount = 1,
            highlightPlayingAyah = true,
        )

        override fun setLanguage(language: AppLanguage) { calls += "language=$language" }
        override fun setThemeMode(mode: ThemeMode) { calls += "mode=$mode" }
        override fun setThemeFollowsSystem(value: Boolean) { calls += "follows=$value" }
        override fun setPalette(palette: ThemePalette) { calls += "palette=$palette" }
        override fun setReadingFont(style: ReadingFontStyle) { calls += "font=$style" }
        override fun setAppSettings(dailyGoal: Int?, readingFontSize: Int?) { calls += "app=$dailyGoal/$readingFontSize" }
        override fun updateReader(change: (SurahReaderPreferences) -> SurahReaderPreferences) {
            reader = change(reader)
            calls += "reader"
        }
        override fun setReaderLayout(layout: ReaderLayout) { calls += "layout=$layout" }
        override fun setKeepScreenOn(value: Boolean) { calls += "keepOn=$value" }
        override fun setReciter(reciter: Reciter) { calls += "reciter=${reciter.id}" }
        override fun setHifzHintMode(mode: HifzHintMode) { calls += "hint=$mode" }
        override fun setLessonAyahRepeats(value: Int) { calls += "lessonAyah=$value" }
        override fun setLessonPassageRepeats(value: Int) { calls += "lessonPassage=$value" }
        override fun setHapticsEnabled(value: Boolean) { calls += "haptics=$value" }
        override fun setReduceMotion(value: Boolean) { calls += "reduce=$value" }
        override fun setTajweed(value: Boolean) { calls += "tajweed=$value" }
        override fun setReminderMinuteOfDay(value: Int) { calls += "reminderAt=$value" }
        override fun setReminderEnabled(value: Boolean) { calls += "reminder=$value" }
    }

    @Test
    fun anEmptySettingsBlockChangesNothing() {
        val target = Recorder()
        BackupSettings().applyTo(target)
        assertEquals(emptyList(), target.calls)
    }

    @Test
    fun everySettingOfTheFileIsApplied() {
        val target = Recorder()
        BackupSettings(
            language = "tg", themeMode = "DARK", themeFollowsSystem = false, palette = "OCEAN", readingFont = "amiri_quran",
            readingFontSize = 34, dailyGoal = 9, showTranslation = true, highlightPlayingAyah = false, autoplayOnOpen = true,
            autoAdvanceAyahs = false, playbackSpeed = 1.5f, repeatCount = 3, readerLayout = "MUSHAF", keepScreenOn = false,
            reciter = "husary", lessonAyahRepeats = 4, lessonPassageRepeats = 3, hifzHint = "FIRST_WORD",
            hapticsEnabled = false, reduceMotion = true, tajweed = true, reminderEnabled = true, reminderMinuteOfDay = 1230,
        ).applyTo(target)
        assertEquals(
            listOf(
                "language=TG", "mode=DARK", "follows=false", "palette=OCEAN", "font=AMIRI_QURAN", "app=9/34", "reader",
                "layout=MUSHAF", "keepOn=false", "reciter=husary", "hint=FIRST_WORD", "lessonAyah=4", "lessonPassage=3", "haptics=false",
                "reduce=true", "tajweed=true", "reminderAt=1230", "reminder=true",
            ),
            target.calls,
        )
        assertEquals(
            SurahReaderPreferences(
                showTranslation = true, autoplayOnOpen = true, autoAdvanceAyahs = false, playbackSpeed = 1.5f, repeatCount = 3,
                highlightPlayingAyah = false,
            ),
            target.reader,
        )
    }

    @Test
    fun theTajweedSwitchOfAnOldCopyIsLeftAloneAndOfANewOneApplied() {
        val old = Recorder()
        BackupSettings(language = "ru").applyTo(old)
        assertEquals(listOf("language=RU"), old.calls)

        val off = Recorder()
        BackupSettings(tajweed = false).applyTo(off)
        assertEquals(listOf("tajweed=false"), off.calls)
    }

    @Test
    fun theReminderTimeIsSetBeforeTheSwitchSoTheAlarmIsPlannedOnceAtTheRightTime() {
        val target = Recorder()
        BackupSettings(reminderEnabled = true, reminderMinuteOfDay = 480).applyTo(target)
        assertEquals(listOf("reminderAt=480", "reminder=true"), target.calls)
    }

    @Test
    fun aCopyWithTheReminderOffTurnsItOffAndAnOldCopyWithoutItLeavesItAlone() {
        val off = Recorder()
        BackupSettings(reminderEnabled = false).applyTo(off)
        assertEquals(listOf("reminder=false"), off.calls)

        val old = Recorder()
        BackupSettings(language = "ru").applyTo(old)
        assertEquals(listOf("language=RU"), old.calls)
    }

    @Test
    fun followingTheSystemIsSetAfterTheThemeModeBecauseTheModeTurnsItOff() {
        val target = Recorder()
        BackupSettings(themeMode = "LIGHT", themeFollowsSystem = true).applyTo(target)
        assertEquals(listOf("mode=LIGHT", "follows=true"), target.calls)
    }

    @Test
    fun onlyTheReaderFieldsTheFileHasAreChanged() {
        val target = Recorder()
        BackupSettings(playbackSpeed = 0.75f).applyTo(target)
        assertEquals(listOf("reader"), target.calls)
        assertEquals(SurahReaderPreferences(playbackSpeed = 0.75f), target.reader)
    }

    @Test
    fun theTwoNumbersOfAppSettingsComeInOneCallSoOneCannotOverwriteTheOther() {
        val target = Recorder()
        BackupSettings(dailyGoal = 12).applyTo(target)
        BackupSettings(readingFontSize = 30).applyTo(target)
        assertEquals(listOf("app=12/null", "app=null/30"), target.calls)
    }

    @Test
    fun namesThatDoNotExistAreSkippedNotCrashed() {
        val target = Recorder()
        BackupSettings(language = "xx", themeMode = "SEPIA", palette = "NEON", readingFont = "none", readerLayout = "GRID", reciter = "nobody", hifzHint = "ALL")
            .applyTo(target)
        assertEquals(emptyList(), target.calls)
    }

    @Test
    fun everyReciterAndPaletteOfTheAppCanBeNamedInTheFile() {
        Reciters.all.forEach { assertTrue(it.id in BackupSettingsRules.RECITERS) }
        ThemePalette.entries.forEach { assertTrue(it.name in BackupSettingsRules.PALETTES) }
        ReadingFontStyle.entries.forEach { assertTrue(it.storageValue in BackupSettingsRules.READING_FONTS) }
        AppLanguage.entries.forEach { assertTrue(transferCopy(it).sectionTitle.isNotBlank()) }
    }
}
