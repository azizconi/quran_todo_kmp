package tj.app.quran_todo.presentation.settings

import org.koin.mp.KoinPlatformTools
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.NativeSettingsCopy
import tj.app.quran_todo.common.i18n.nativeSettingsCopy
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.presentation.home.HomeViewModel

/** Shared persistence bridge for the SwiftUI settings screen. */
class NativeSettingsBridge internal constructor(
    private val homeViewModel: HomeViewModel,
) {
    fun load(): NativeSettingsSnapshot = NativeSettingsSnapshot(
        language = (LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()).name,
        themeMode = (ThemeStorage.getSavedThemeMode() ?: ThemeMode.LIGHT).name,
        fontStyle = (ThemeStorage.getSavedReadingFontStyle() ?: ReadingFontStyle.MADINAH_MUSHAF).storageValue,
        readingFontSize = UserSettingsStorage.getReadingFontSize()?.coerceIn(24, 34) ?: 28,
        preferences = SurahReaderPreferences.load().toSnapshot(),
        localizedCopy = nativeSettingsCopy(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()),
    )

    fun updateLanguage(value: String) {
        AppLanguage.entries.firstOrNull { it.name == value }?.let(LanguageStorage::saveLanguage)
        SettingsChangeNotifier.notifyChanged()
    }

    fun updateTheme(value: String) {
        ThemeMode.entries.firstOrNull { it.name == value }?.let(ThemeStorage::saveThemeMode)
        SettingsChangeNotifier.notifyChanged()
    }

    fun updateFontStyle(value: String) {
        ThemeStorage.saveReadingFontStyle(ReadingFontStyle.fromStorage(value))
        SettingsChangeNotifier.notifyChanged()
    }

    fun updateReadingFontSize(value: Int) {
        UserSettingsStorage.saveReadingFontSize(value.coerceIn(24, 34))
        SettingsChangeNotifier.notifyChanged()
    }

    fun updatePreferences(
        showTranslation: Boolean,
        autoplayOnOpen: Boolean,
        autoAdvanceAyahs: Boolean,
        playbackSpeed: Float,
        repeatCount: Int,
        highlightPlayingAyah: Boolean,
    ) {
        SurahReaderPreferences(
            showTranslation = showTranslation,
            autoplayOnOpen = autoplayOnOpen,
            autoAdvanceAyahs = autoAdvanceAyahs,
            playbackSpeed = playbackSpeed,
            repeatCount = repeatCount,
            highlightPlayingAyah = highlightPlayingAyah,
        ).save()
        SettingsChangeNotifier.notifyChanged()
    }

    fun startOfflineDownload() {
        val language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        homeViewModel.startOfflinePackage(language)
    }

    fun sync() {
        homeViewModel.syncProgressToCloud(
            AppSettings(
                dailyGoal = 5,
                focusMinutes = 10,
                remindersEnabled = false,
                targetAyahs = 300,
                targetEpochDay = 0,
                examModeEnabled = false,
                readingFontSize = UserSettingsStorage.getReadingFontSize()?.coerceIn(24, 34) ?: 28,
            ),
        )
    }

    fun restore() {
        homeViewModel.restoreProgressFromCloud()
    }

    private fun SurahReaderPreferences.toSnapshot() = NativeSettingsPreferencesSnapshot(
        showTranslation = showTranslation,
        autoplayOnOpen = autoplayOnOpen,
        autoAdvanceAyahs = autoAdvanceAyahs,
        playbackSpeed = playbackSpeed,
        repeatCount = repeatCount,
        highlightPlayingAyah = highlightPlayingAyah,
    )
}

object NativeSettingsBridgeFactory {
    fun create(): NativeSettingsBridge {
        val koin = KoinPlatformTools.defaultContext().get()
        return NativeSettingsBridge(homeViewModel = koin.get())
    }
}

class NativeSettingsSnapshot(
    val language: String,
    val themeMode: String,
    val fontStyle: String,
    val readingFontSize: Int,
    val preferences: NativeSettingsPreferencesSnapshot,
    val localizedCopy: NativeSettingsCopy,
)

class NativeSettingsPreferencesSnapshot(
    val showTranslation: Boolean,
    val autoplayOnOpen: Boolean,
    val autoAdvanceAyahs: Boolean,
    val playbackSpeed: Float,
    val repeatCount: Int,
    val highlightPlayingAyah: Boolean,
)
