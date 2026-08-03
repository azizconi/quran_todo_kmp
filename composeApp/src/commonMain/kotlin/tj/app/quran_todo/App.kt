package tj.app.quran_todo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.stringsFor
import tj.app.quran_todo.common.reminder.ReminderScheduler
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.AppTheme
import tj.app.quran_todo.common.theme.AppChromeAppearance
import tj.app.quran_todo.common.theme.AyahCardStyle
import tj.app.quran_todo.common.theme.LocalAyahCardStyle
import tj.app.quran_todo.common.theme.LocalAyahCardStyleSetter
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemeModeSetter
import tj.app.quran_todo.common.theme.LocalThemePalette
import tj.app.quran_todo.common.theme.LocalThemePaletteSetter
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.LocalReadingFontStyleSetter
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.common.theme.appChromeAppearance
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.presentation.hifz.HifzAppShell
import tj.app.quran_todo.presentation.navigation.DefaultHifzRootComponent
import tj.app.quran_todo.presentation.onboarding.FontOnboardingScreen
import tj.app.quran_todo.presentation.onboarding.GoalsOnboardingScreen
import tj.app.quran_todo.presentation.onboarding.LanguageOnboardingScreen

@Composable
@Preview
fun App(
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    onChromeChanged: (AppChromeAppearance) -> Unit = {},
    initialSurahNumber: Int? = null,
    onNativeSurahRequested: ((Int) -> Unit)? = null,
    onNativeSettingsRequested: (() -> Unit)? = null,
    onNativeBack: () -> Unit = {},
) {
    val savedLanguage = remember { LanguageStorage.getSavedLanguage() }
    val initialLanguage = remember(savedLanguage) {
        savedLanguage ?: LanguageStorage.getDeviceLanguage()
    }
    var language by remember { mutableStateOf<AppLanguage>(initialLanguage) }
    var requireLanguageOnboarding by remember { mutableStateOf(savedLanguage == null) }
    val systemDark = isSystemInDarkTheme()
    val initialTheme = remember(systemDark) {
        ThemeStorage.getSavedThemeMode() ?: if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
    }
    var themeMode by remember { mutableStateOf(initialTheme) }
    LaunchedEffect(themeMode) {
        onThemeModeChanged(themeMode)
    }
    val initialPalette = remember {
        ThemeStorage.getSavedThemePalette() ?: ThemePalette.FOREST
    }
    var themePalette by remember { mutableStateOf(initialPalette) }
    LaunchedEffect(themeMode, themePalette) {
        onChromeChanged(appChromeAppearance(themeMode, themePalette))
    }
    val savedReadingFontStyle = remember { ThemeStorage.getSavedReadingFontStyle() }
    var readingFontStyle by remember {
        mutableStateOf(savedReadingFontStyle ?: ReadingFontStyle.MADINAH_MUSHAF)
    }
    var requireFontOnboarding by remember { mutableStateOf(savedReadingFontStyle == null) }
    val savedAyahCardStyle = remember { ThemeStorage.getSavedAyahCardStyle() }
    var ayahCardStyle by remember {
        mutableStateOf(savedAyahCardStyle ?: AyahCardStyle.CLASSIC)
    }
    var requireGoalsOnboarding by remember { mutableStateOf(false) }

    val strings = remember(language) { stringsFor(language) }
    val rootComponent = remember {
        DefaultHifzRootComponent(
            DefaultComponentContext(LifecycleRegistry(initialState = Lifecycle.State.RESUMED)),
            initialSurahNumber = initialSurahNumber,
        )
    }
    val savedReadingFontSize = remember { UserSettingsStorage.getReadingFontSize() }
    val initialSettings = remember {
        val todayEpochDay = currentLocalDate().toEpochDays()
        AppSettings(
            dailyGoal = UserSettingsStorage.getDailyGoal()?.coerceIn(1, 50) ?: 5,
            focusMinutes = UserSettingsStorage.getFocusMinutes()?.coerceIn(5, 60) ?: 10,
            remindersEnabled = UserSettingsStorage.isReminderEnabled() ?: true,
            targetAyahs = UserSettingsStorage.getTargetAyahs()?.coerceIn(50, 6236) ?: 300,
            targetEpochDay = UserSettingsStorage.getTargetEpochDay() ?: (todayEpochDay + 60),
            examModeEnabled = UserSettingsStorage.isExamModeEnabled() ?: false,
            readingFontSize = savedReadingFontSize?.coerceIn(24, 34) ?: 28,
        )
    }
    var appSettings by remember { mutableStateOf(initialSettings) }
    val settingsRevision by SettingsChangeNotifier.revision.collectAsState()

    LaunchedEffect(settingsRevision) {
        if (settingsRevision == 0L) return@LaunchedEffect
        language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        themeMode = ThemeStorage.getSavedThemeMode() ?: themeMode
        readingFontStyle = ThemeStorage.getSavedReadingFontStyle() ?: readingFontStyle
        UserSettingsStorage.getReadingFontSize()?.coerceIn(24, 34)?.let { size ->
            appSettings = appSettings.copy(readingFontSize = size)
        }
    }

    LaunchedEffect(savedReadingFontSize) {
        val normalized = savedReadingFontSize?.coerceIn(24, 34) ?: return@LaunchedEffect
        if (normalized != savedReadingFontSize) {
            UserSettingsStorage.saveReadingFontSize(normalized)
        }
    }

    LaunchedEffect(Unit) {
        UserSettingsStorage.saveReminderEnabled(false)
        ReminderScheduler.syncDailyReminder(
            enabled = false,
            title = strings.reminderTitle,
            body = strings.reminderBody
        )
    }

    val activeScreen = when {
        requireLanguageOnboarding -> "onboarding_language"
        requireFontOnboarding -> "onboarding_font"
        requireGoalsOnboarding -> "onboarding_goals"
        else -> "hifz"
    }

    LaunchedEffect(Unit) {
        AppTelemetry.logEvent("app_opened")
    }

    LaunchedEffect(activeScreen) {
        AppTelemetry.logScreen(activeScreen)
    }

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalAppLanguage provides language,
        LocalAppLanguageSetter provides {
            if (language != it) {
                AppTelemetry.logEvent(
                    name = "language_changed",
                    params = mapOf(
                        "from" to language.name.lowercase(),
                        "to" to it.name.lowercase()
                    )
                )
            }
            language = it
            LanguageStorage.saveLanguage(it)
        },
        LocalThemeMode provides themeMode,
        LocalThemeModeSetter provides {
            if (themeMode != it) {
                AppTelemetry.logEvent(
                    name = "theme_mode_changed",
                    params = mapOf("mode" to it.name.lowercase())
                )
            }
            themeMode = it
            ThemeStorage.saveThemeMode(it)
        },
        LocalThemePalette provides themePalette,
        LocalThemePaletteSetter provides {
            if (themePalette != it) {
                AppTelemetry.logEvent(
                    name = "theme_palette_changed",
                    params = mapOf("palette" to it.name.lowercase())
                )
            }
            themePalette = it
            ThemeStorage.saveThemePalette(it)
        },
        LocalReadingFontStyle provides readingFontStyle,
        LocalReadingFontStyleSetter provides {
            if (readingFontStyle != it) {
                AppTelemetry.logEvent(
                    name = "reading_font_changed",
                    params = mapOf("font" to it.name.lowercase())
                )
            }
            readingFontStyle = it
            ThemeStorage.saveReadingFontStyle(it)
        },
        LocalAyahCardStyle provides ayahCardStyle,
        LocalAyahCardStyleSetter provides {
            if (ayahCardStyle != it) {
                AppTelemetry.logEvent(
                    name = "ayah_card_style_changed",
                    params = mapOf("style" to it.name.lowercase())
                )
            }
            ayahCardStyle = it
            ThemeStorage.saveAyahCardStyle(it)
        },
        LocalAppSettings provides appSettings,
        LocalAppSettingsSetter provides {
            val changedFields = buildList {
                if (appSettings.dailyGoal != it.dailyGoal) add("daily_goal")
                if (appSettings.focusMinutes != it.focusMinutes) add("focus_minutes")
                if (appSettings.remindersEnabled != it.remindersEnabled) add("reminders")
                if (appSettings.targetAyahs != it.targetAyahs) add("target_ayahs")
                if (appSettings.targetEpochDay != it.targetEpochDay) add("target_day")
                if (appSettings.examModeEnabled != it.examModeEnabled) add("exam_mode")
            }
            if (changedFields.isNotEmpty()) {
                AppTelemetry.logEvent(
                    name = "app_settings_changed",
                    params = mapOf("fields" to changedFields.joinToString(","))
                )
            }
            appSettings = it
            UserSettingsStorage.saveDailyGoal(it.dailyGoal)
            UserSettingsStorage.saveFocusMinutes(it.focusMinutes)
            UserSettingsStorage.saveReminderEnabled(it.remindersEnabled)
            UserSettingsStorage.saveTargetAyahs(it.targetAyahs)
            UserSettingsStorage.saveTargetEpochDay(it.targetEpochDay)
            UserSettingsStorage.saveExamModeEnabled(it.examModeEnabled)
            UserSettingsStorage.saveReadingFontSize(it.readingFontSize)
        },
    ) {
        AppTheme(mode = themeMode, palette = themePalette) {
            if (requireLanguageOnboarding) {
                LanguageOnboardingScreen(
                    selected = language,
                    onSelected = { language = it },
                    onContinue = {
                        LanguageStorage.saveLanguage(language)
                        AppTelemetry.logEvent(
                            name = "onboarding_completed",
                            params = mapOf(
                                "step" to "language",
                                "language" to language.name.lowercase()
                            )
                        )
                        requireLanguageOnboarding = false
                    }
                )
            } else if (requireFontOnboarding) {
                FontOnboardingScreen(
                    selected = readingFontStyle,
                    fontSize = appSettings.readingFontSize,
                    onSelected = { readingFontStyle = it },
                    onFontSizeChange = { size ->
                        appSettings = appSettings.copy(readingFontSize = size.coerceIn(24, 34))
                    },
                    onContinue = {
                        ThemeStorage.saveReadingFontStyle(readingFontStyle)
                        UserSettingsStorage.saveReadingFontSize(appSettings.readingFontSize)
                        AppTelemetry.logEvent(
                            name = "onboarding_completed",
                            params = mapOf(
                                "step" to "font",
                                "font" to readingFontStyle.name.lowercase()
                            )
                        )
                        requireFontOnboarding = false
                    }
                )
            } else if (requireGoalsOnboarding) {
                GoalsOnboardingScreen(
                    initialSettings = appSettings,
                    onContinue = { next ->
                        appSettings = next
                        UserSettingsStorage.saveDailyGoal(next.dailyGoal)
                        UserSettingsStorage.saveFocusMinutes(next.focusMinutes)
                        UserSettingsStorage.saveReminderEnabled(next.remindersEnabled)
                        UserSettingsStorage.saveTargetAyahs(next.targetAyahs)
                        UserSettingsStorage.saveTargetEpochDay(next.targetEpochDay)
                        UserSettingsStorage.saveExamModeEnabled(next.examModeEnabled)
                        UserSettingsStorage.saveReadingFontSize(next.readingFontSize)
                        AppTelemetry.logEvent(
                            name = "onboarding_completed",
                            params = mapOf(
                                "step" to "goals",
                                "daily_goal" to next.dailyGoal.toString(),
                                "focus_minutes" to next.focusMinutes.toString()
                            )
                        )
                        requireGoalsOnboarding = false
                    }
                )
            } else {
                HifzAppShell(
                    root = rootComponent,
                    onNativeSurahRequested = onNativeSurahRequested,
                    onNativeSettingsRequested = onNativeSettingsRequested,
                    onNativeBack = onNativeBack,
                )
            }
        }
    }
}

/** Legacy host configuration retained for platform-specific AppNavHost implementations. */
@Serializable
sealed interface AppTab {
    @Serializable
    data object Home : AppTab

    @Serializable
    data object Stats : AppTab

    @Serializable
    data object Settings : AppTab
}
