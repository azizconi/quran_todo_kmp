package tj.app.quran_todo.bridge

import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.data.translation.TranslationChoice
import tj.app.quran_todo.hifz.backup.BackupSettings
import tj.app.quran_todo.ui.settings.SettingsViewModel
import tj.app.quran_todo.ui.settings.TransferSettingsTarget
import tj.app.quran_todo.ui.settings.applyTo
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.ReaderLayout

/**
 * Puts the settings of an imported backup in place for the SwiftUI settings screen: the three that need the app root's setters
 * (language, theme mode, palette: they redraw the whole UI, the theme ones with the circular reveal) come back through the three
 * callbacks, everything else is stored here exactly as the Android screen does.
 */
object IosTransferApply {
    fun apply(
        settings: BackupSettings,
        viewModel: SettingsViewModel,
        setLanguage: (AppLanguage) -> Unit,
        setThemeMode: (ThemeMode) -> Unit,
        setPalette: (ThemePalette) -> Unit,
    ) {
        settings.applyTo(
            object : TransferSettingsTarget {
                override fun setLanguage(language: AppLanguage) = setLanguage(language)
                override fun setThemeMode(mode: ThemeMode) = setThemeMode(mode)
                override fun setThemeFollowsSystem(value: Boolean) = viewModel.setThemeFollowsSystem(value)
                override fun setPalette(palette: ThemePalette) = setPalette(palette)
                override fun setReadingFont(style: ReadingFontStyle) = ThemeStorage.saveReadingFontStyle(style)
                override fun setAppSettings(dailyGoal: Int?, readingFontSize: Int?) {
                    dailyGoal?.let { UserSettingsStorage.saveDailyGoal(it) }
                    readingFontSize?.let { UserSettingsStorage.saveReadingFontSize(it.coerceIn(22, 40)) }
                }
                override fun updateReader(change: (SurahReaderPreferences) -> SurahReaderPreferences) =
                    viewModel.updateReaderPreferences(change)
                override fun setReaderLayout(layout: ReaderLayout) = viewModel.setReaderLayout(layout)
                override fun setKeepScreenOn(value: Boolean) = viewModel.setKeepScreenOn(value)
                override fun setReciter(reciter: Reciter) = viewModel.setReciter(reciter)
                override fun setHifzHintMode(mode: HifzHintMode) = viewModel.setHifzHintMode(mode)
                override fun setLessonAyahRepeats(value: Int) = viewModel.setLessonAyahRepeats(value)
                override fun setLessonPassageRepeats(value: Int) = viewModel.setLessonPassageRepeats(value)
                override fun setHapticsEnabled(value: Boolean) = viewModel.setHapticsEnabled(value)
                override fun setReduceMotion(value: Boolean) = viewModel.setReduceMotion(value)
                override fun setTajweed(value: Boolean) = viewModel.setTajweed(value)
                override fun setWordMeanings(value: Boolean) = viewModel.setWordMeanings(value)
                override fun setSimilarMarks(value: Boolean) = viewModel.setSimilarMarks(value)
                override fun setTafsirSource(key: String) = viewModel.setTafsirSource(key)
                override fun setReminderMinuteOfDay(value: Int) = viewModel.setReminderMinuteOfDay(value)
                override fun setReminderEnabled(value: Boolean) = viewModel.setReminderEnabled(value)
                override fun setTranslationChoice(choice: Map<String, String>) = TranslationChoice.store.restore(choice)
            },
        )
    }

    /** Rewrites the widget snapshot (the palette changed in Settings) and reloads the timelines. */
    fun refreshWidget() = org.koin.mp.KoinPlatform.getKoin().get<tj.app.quran_todo.ui.widget.WidgetRefresher>().refresh()

    /** Bytes of downloaded audio plus the listening cache: the value of the "Downloads and storage" row of Settings. */
    fun audioBytes(): Long = DownloadStorage.shared.totalBytes() + AudioCacheStorage.shared.totalBytes()

    // Reader preferences without the Kotlin `copy` (Swift sees it as `doCopy`).
    fun setShowTranslation(viewModel: SettingsViewModel, value: Boolean) =
        viewModel.updateReaderPreferences { it.copy(showTranslation = value) }

    fun setHighlightPlayingAyah(viewModel: SettingsViewModel, value: Boolean) =
        viewModel.updateReaderPreferences { it.copy(highlightPlayingAyah = value) }

    fun setAutoplayOnOpen(viewModel: SettingsViewModel, value: Boolean) =
        viewModel.updateReaderPreferences { it.copy(autoplayOnOpen = value) }

    /** `ReadingFontStyle.storageValue` of the saved choice (default "madinah_mushaf"). */
    fun readingFontStorageValue(): String = (ThemeStorage.getSavedReadingFontStyle() ?: ReadingFontStyle.MADINAH_MUSHAF).storageValue

    fun setReadingFont(storageValue: String) = ThemeStorage.saveReadingFontStyle(ReadingFontStyle.fromStorage(storageValue))

    /** The reading size of the reader, 22..40. */
    fun readingFontSize(): Int = (UserSettingsStorage.getReadingFontSize() ?: 28).coerceIn(22, 40)

    fun setReadingFontSize(value: Int) = UserSettingsStorage.saveReadingFontSize(value.coerceIn(22, 40))
}
