package tj.app.quran_todo.ui.settings

import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.data.translation.TranslationChoice
import tj.app.quran_todo.data.translation.TranslationChoiceStore
import tj.app.quran_todo.hifz.backup.BackupSettings
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.ReaderLayout
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Settings <-> the `settings` block of the backup file.
 *
 * What the person set up goes to the file, analytics consent does not. That includes the repeats of the "Listen" phase of the
 * lesson (`UiPrefs.lessonAyahRepeats` / `lessonPassageRepeats`).
 */
internal object TransferSettings {
    /** The settings of this phone, as they are stored now. Values never saved are left out (the phone that imports keeps its own). */
    fun collect(
        uiPrefs: UiPrefs,
        reader: SurahReaderPreferences = SurahReaderPreferences.load(),
        translations: TranslationChoiceStore = TranslationChoice.store,
    ): BackupSettings = BackupSettings(
        language = LanguageStorage.getSavedLanguage()?.code,
        themeMode = ThemeStorage.getSavedThemeMode()?.name,
        themeFollowsSystem = uiPrefs.themeFollowsSystem.value,
        palette = ThemeStorage.getSavedThemePalette()?.name,
        readingFont = ThemeStorage.getSavedReadingFontStyle()?.storageValue,
        readingFontSize = UserSettingsStorage.getReadingFontSize(),
        dailyGoal = UserSettingsStorage.getDailyGoal(),
        showTranslation = reader.showTranslation,
        highlightPlayingAyah = reader.highlightPlayingAyah,
        autoplayOnOpen = reader.autoplayOnOpen,
        autoAdvanceAyahs = reader.autoAdvanceAyahs,
        playbackSpeed = reader.playbackSpeed,
        repeatCount = reader.repeatCount,
        readerLayout = uiPrefs.readerLayout.value.name,
        keepScreenOn = uiPrefs.keepScreenOn.value,
        reciter = uiPrefs.reciter.value.id,
        lessonAyahRepeats = uiPrefs.lessonAyahRepeats.value,
        lessonPassageRepeats = uiPrefs.lessonPassageRepeats.value,
        hifzHint = uiPrefs.hifzHintMode.value.name,
        hapticsEnabled = uiPrefs.hapticsEnabled.value,
        reduceMotion = uiPrefs.reduceMotion.value,
        tajweed = uiPrefs.tajweed.value,
        wordMeanings = uiPrefs.wordMeanings.value,
        similarMarks = uiPrefs.similarMarks.value,
        tafsir = uiPrefs.tafsirSource.value,
        reminderEnabled = uiPrefs.reminderEnabled.value,
        reminderMinuteOfDay = uiPrefs.reminderMinuteOfDay.value,
        translationChoice = translations.choices().takeIf { it.isNotEmpty() },
    )
}

/**
 * Where an imported setting goes. The screen implements it with the setters of the app root (language, theme, palette, font,
 * `AppSettings`: the whole UI follows at once) and the settings ViewModel (`UiPrefs`, reader preferences); the unit test
 * implements it with a recorder.
 */
internal interface TransferSettingsTarget {
    fun setLanguage(language: AppLanguage)

    /** An explicit light/dark choice. It ends "follow the system" (the root's setter does), so [setThemeFollowsSystem] comes after it. */
    fun setThemeMode(mode: ThemeMode)

    fun setThemeFollowsSystem(value: Boolean)

    fun setPalette(palette: ThemePalette)

    fun setReadingFont(style: ReadingFontStyle)

    /** `AppSettings` is written as a whole by the root, so both numbers come in one call (null = leave as it is). */
    fun setAppSettings(dailyGoal: Int?, readingFontSize: Int?)

    /** All the reader fields in one call. */
    fun updateReader(change: (SurahReaderPreferences) -> SurahReaderPreferences)

    fun setReaderLayout(layout: ReaderLayout)

    fun setKeepScreenOn(value: Boolean)

    fun setReciter(reciter: Reciter)

    fun setHifzHintMode(mode: HifzHintMode)

    /** How many times every ayah of a new passage sounds in the lesson. */
    fun setLessonAyahRepeats(value: Int)

    /** How many times the whole new passage sounds in the lesson after that. */
    fun setLessonPassageRepeats(value: Int)

    fun setHapticsEnabled(value: Boolean)

    fun setReduceMotion(value: Boolean)

    /** The tajweed colours of the reader and the lesson on or off. The markup is fetched when the reader needs it, not here. */
    fun setTajweed(value: Boolean)

    /** "Word meanings (English)" on or off (task F4b). The meanings are fetched at the first tap, not here. */
    fun setWordMeanings(value: Boolean)

    /** "Similar verse marks" on or off (task F6). */
    fun setSimilarMarks(value: Boolean)

    /** The commentary chosen last (`TafsirSource.key`, task F4b). */
    fun setTafsirSource(key: String)

    /** When the daily reminder comes, minutes after midnight. Comes before [setReminderEnabled], so a reminder that is turned on is planned once, at the right time. */
    fun setReminderMinuteOfDay(value: Int)

    /** The daily reminder on or off. The permission to show notifications is not asked here. */
    fun setReminderEnabled(value: Boolean)

    /** The edition of the translation per language of the interface (task F4a): language code to edition key; the text is fetched when the reader needs it. */
    fun setTranslationChoice(choice: Map<String, String>)
}

/**
 * Applies the checked settings of a file. Only the values the file has are set; an absent one leaves the phone's setting as it
 * is. [BackupSettings.sanitized] has run already, so every name here exists; a value that does not is skipped all the same.
 */
internal fun BackupSettings.applyTo(target: TransferSettingsTarget) {
    language?.let { code -> AppLanguage.entries.firstOrNull { it.code == code } }?.let(target::setLanguage)
    themeMode?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }?.let(target::setThemeMode)
    // After the mode: choosing a mode turns "follow the system" off, the file may turn it on again.
    themeFollowsSystem?.let(target::setThemeFollowsSystem)
    palette?.let { name -> ThemePalette.entries.firstOrNull { it.name == name } }?.let(target::setPalette)
    readingFont?.let { value -> ReadingFontStyle.entries.firstOrNull { it.storageValue == value } }?.let(target::setReadingFont)
    if (dailyGoal != null || readingFontSize != null) target.setAppSettings(dailyGoal, readingFontSize)

    if (showTranslation != null || highlightPlayingAyah != null || autoplayOnOpen != null || autoAdvanceAyahs != null ||
        playbackSpeed != null || repeatCount != null
    ) {
        target.updateReader { current ->
            current.copy(
                showTranslation = showTranslation ?: current.showTranslation,
                autoplayOnOpen = autoplayOnOpen ?: current.autoplayOnOpen,
                autoAdvanceAyahs = autoAdvanceAyahs ?: current.autoAdvanceAyahs,
                playbackSpeed = playbackSpeed ?: current.playbackSpeed,
                repeatCount = repeatCount ?: current.repeatCount,
                highlightPlayingAyah = highlightPlayingAyah ?: current.highlightPlayingAyah,
            )
        }
    }
    readerLayout?.let { name -> ReaderLayout.entries.firstOrNull { it.name == name } }?.let(target::setReaderLayout)
    keepScreenOn?.let(target::setKeepScreenOn)
    reciter?.let { id -> Reciters.all.firstOrNull { it.id == id } }?.let(target::setReciter)
    hifzHint?.let { name -> HifzHintMode.entries.firstOrNull { it.name == name } }?.let(target::setHifzHintMode)
    lessonAyahRepeats?.let(target::setLessonAyahRepeats)
    lessonPassageRepeats?.let(target::setLessonPassageRepeats)
    hapticsEnabled?.let(target::setHapticsEnabled)
    reduceMotion?.let(target::setReduceMotion)
    tajweed?.let(target::setTajweed)
    wordMeanings?.let(target::setWordMeanings)
    similarMarks?.let(target::setSimilarMarks)
    tafsir?.let(target::setTafsirSource)
    reminderMinuteOfDay?.let(target::setReminderMinuteOfDay)
    reminderEnabled?.let(target::setReminderEnabled)
    translationChoice?.let(target::setTranslationChoice)
}
