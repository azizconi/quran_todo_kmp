package tj.app.quran_todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import org.jetbrains.compose.ui.tooling.preview.Preview
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.stringsFor
import tj.app.quran_todo.common.reminder.ReminderScheduler
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.settings.LocalAppSettings
import tj.app.quran_todo.common.settings.LocalAppSettingsSetter
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.AppTheme
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
import tj.app.quran_todo.common.theme.mutedText
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.navigation.AppNavHost
import tj.app.quran_todo.presentation.onboarding.CardStyleOnboardingScreen
import tj.app.quran_todo.presentation.onboarding.FeatureGuideOnboardingScreen
import tj.app.quran_todo.presentation.onboarding.FontOnboardingScreen
import tj.app.quran_todo.presentation.onboarding.LanguageOnboardingScreen

@Composable
@Preview
fun App() {
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
    val initialPalette = remember {
        ThemeStorage.getSavedThemePalette() ?: ThemePalette.SAND
    }
    var themePalette by remember { mutableStateOf(initialPalette) }
    val savedReadingFontStyle = remember { ThemeStorage.getSavedReadingFontStyle() }
    var readingFontStyle by remember {
        mutableStateOf(savedReadingFontStyle ?: ReadingFontStyle.MUSHAF_MODERN)
    }
    var requireFontOnboarding by remember { mutableStateOf(savedReadingFontStyle == null) }
    val savedAyahCardStyle = remember { ThemeStorage.getSavedAyahCardStyle() }
    var ayahCardStyle by remember {
        mutableStateOf(savedAyahCardStyle ?: AyahCardStyle.CLASSIC)
    }
    var requireAyahCardStyleOnboarding by remember { mutableStateOf(savedAyahCardStyle == null) }
    var requireFeatureGuideOnboarding by remember {
        mutableStateOf(UserSettingsStorage.isFeatureGuideSeen() != true)
    }

    val strings = remember(language) { stringsFor(language) }
    val backStack = remember { mutableStateListOf<AppTab>(AppTab.Home) }
    val current = backStack.lastOrNull() ?: AppTab.Home
    val initialSettings = remember {
        val todayEpochDay = currentLocalDate().toEpochDays()
        AppSettings(
            dailyGoal = UserSettingsStorage.getDailyGoal() ?: 5,
            focusMinutes = UserSettingsStorage.getFocusMinutes() ?: 10,
            remindersEnabled = UserSettingsStorage.isReminderEnabled() ?: true,
            targetAyahs = UserSettingsStorage.getTargetAyahs() ?: 300,
            targetEpochDay = UserSettingsStorage.getTargetEpochDay() ?: (todayEpochDay + 60),
            examModeEnabled = UserSettingsStorage.isExamModeEnabled() ?: false
        )
    }
    var appSettings by remember { mutableStateOf(initialSettings) }

    LaunchedEffect(appSettings.remindersEnabled, strings.reminderTitle, strings.reminderBody) {
        ReminderScheduler.syncDailyReminder(
            enabled = appSettings.remindersEnabled,
            title = strings.reminderTitle,
            body = strings.reminderBody
        )
    }

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalAppLanguage provides language,
        LocalAppLanguageSetter provides {
            language = it
            LanguageStorage.saveLanguage(it)
        },
        LocalThemeMode provides themeMode,
        LocalThemeModeSetter provides {
            themeMode = it
            ThemeStorage.saveThemeMode(it)
        },
        LocalThemePalette provides themePalette,
        LocalThemePaletteSetter provides {
            themePalette = it
            ThemeStorage.saveThemePalette(it)
        },
        LocalReadingFontStyle provides readingFontStyle,
        LocalReadingFontStyleSetter provides {
            readingFontStyle = it
            ThemeStorage.saveReadingFontStyle(it)
        },
        LocalAyahCardStyle provides ayahCardStyle,
        LocalAyahCardStyleSetter provides {
            ayahCardStyle = it
            ThemeStorage.saveAyahCardStyle(it)
        },
        LocalAppSettings provides appSettings,
        LocalAppSettingsSetter provides {
            appSettings = it
            UserSettingsStorage.saveDailyGoal(it.dailyGoal)
            UserSettingsStorage.saveFocusMinutes(it.focusMinutes)
            UserSettingsStorage.saveReminderEnabled(it.remindersEnabled)
            UserSettingsStorage.saveTargetAyahs(it.targetAyahs)
            UserSettingsStorage.saveTargetEpochDay(it.targetEpochDay)
            UserSettingsStorage.saveExamModeEnabled(it.examModeEnabled)
        },
    ) {
        AppTheme(mode = themeMode, palette = themePalette) {
            if (requireLanguageOnboarding) {
                LanguageOnboardingScreen(
                    selected = language,
                    onSelected = { language = it },
                    onContinue = {
                        LanguageStorage.saveLanguage(language)
                        requireLanguageOnboarding = false
                    }
                )
            } else if (requireFontOnboarding) {
                FontOnboardingScreen(
                    selected = readingFontStyle,
                    onSelected = { readingFontStyle = it },
                    onContinue = {
                        ThemeStorage.saveReadingFontStyle(readingFontStyle)
                        requireFontOnboarding = false
                    }
                )
            } else if (requireAyahCardStyleOnboarding) {
                CardStyleOnboardingScreen(
                    selected = ayahCardStyle,
                    onSelected = { ayahCardStyle = it },
                    onContinue = {
                        ThemeStorage.saveAyahCardStyle(ayahCardStyle)
                        requireAyahCardStyleOnboarding = false
                    }
                )
            } else if (requireFeatureGuideOnboarding) {
                FeatureGuideOnboardingScreen(
                    onContinue = {
                        UserSettingsStorage.saveFeatureGuideSeen(true)
                        requireFeatureGuideOnboarding = false
                    }
                )
            } else {
                Scaffold(
                    contentWindowInsets = WindowInsets.statusBars,
                    bottomBar = {
                        Card(
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                            backgroundColor = MaterialTheme.colors.surface,
                            elevation = 10.dp
                        ) {
                            BottomNavigation(
                                backgroundColor = Color.Transparent,
                                elevation = 0.dp,
                                windowInsets = WindowInsets.navigationBars
                            ) {
                                BottomNavigationItem(
                                    selected = current == AppTab.Home,
                                    onClick = {
                                        switchTab(backStack, AppTab.Home)
                                    },
                                    selectedContentColor = MaterialTheme.colors.primary,
                                    unselectedContentColor = MaterialTheme.colors.mutedText,
                                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                    label = { Text(strings.homeTitle) }
                                )
                                BottomNavigationItem(
                                    selected = current == AppTab.Stats,
                                    onClick = {
                                        switchTab(backStack, AppTab.Stats)
                                    },
                                    selectedContentColor = MaterialTheme.colors.primary,
                                    unselectedContentColor = MaterialTheme.colors.mutedText,
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                    label = { Text(strings.statsTitle) }
                                )
                                BottomNavigationItem(
                                    selected = current == AppTab.Settings,
                                    onClick = {
                                        switchTab(backStack, AppTab.Settings)
                                    },
                                    selectedContentColor = MaterialTheme.colors.primary,
                                    unselectedContentColor = MaterialTheme.colors.mutedText,
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text(strings.settingsTitle) }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        AppNavHost(current)
                    }
                }
            }
        }
    }
}

@Serializable
sealed interface AppTab {
    @Serializable
    data object Home : AppTab

    @Serializable
    data object Stats : AppTab

    @Serializable
    data object Settings : AppTab
}

private fun switchTab(backStack: MutableList<AppTab>, tab: AppTab) {
    if (backStack.lastOrNull() == tab) return
    backStack.clear()
    backStack.add(tab)
}
