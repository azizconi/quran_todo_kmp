package tj.app.quran_todo.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.koinInject
import tj.app.quran_todo.common.analytics.AppTelemetry
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
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.theme.AyahCardStyle
import tj.app.quran_todo.common.theme.LocalAyahCardStyle
import tj.app.quran_todo.common.theme.LocalAyahCardStyleSetter
import tj.app.quran_todo.common.theme.LocalReadingFontStyle
import tj.app.quran_todo.common.theme.LocalReadingFontStyleSetter
import tj.app.quran_todo.common.theme.LocalThemeMode
import tj.app.quran_todo.common.theme.LocalThemeModeSetter
import tj.app.quran_todo.common.theme.LocalThemePalette
import tj.app.quran_todo.common.theme.LocalThemePaletteSetter
import tj.app.quran_todo.common.theme.ReadingFontStyle
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.ui.catalog.CatalogScreen
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.ToastHost
import tj.app.quran_todo.ui.components.rememberToaster
import tj.app.quran_todo.ui.home.HomeScreen
import tj.app.quran_todo.ui.motion.LocalNavAnimatedVisibilityScope
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.LocalSharedTransitionScope
import tj.app.quran_todo.ui.motion.rememberSystemReducedMotion
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.NavTransitions
import tj.app.quran_todo.ui.onboarding.OnboardingScreen
import tj.app.quran_todo.ui.progress.ProgressScreen
import tj.app.quran_todo.ui.reader.ReaderScreen
import tj.app.quran_todo.ui.settings.SettingsScreen
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhTheme

/** Reading font size range of the redesign (docs/redesign/ANDROID_REDESIGN.md, 2.2). */
private const val MinReadingFontSize = 22
private const val MaxReadingFontSize = 40

/**
 * True until the person has chosen a language and a reading font (existing users skip onboarding).
 * Shared by the app root and the splash screen, which does not wait for data when onboarding is next.
 */
internal fun needsOnboarding(): Boolean =
    LanguageStorage.getSavedLanguage() == null || ThemeStorage.getSavedReadingFontStyle() == null

/**
 * Root of the Android UI: owns language, theme, font and settings state, provides the `Local*` values
 * that shared code reads, configures the system bars and hosts the single NavHost.
 */
@Composable
fun QuranHifzApp() {
    val uiPrefs = koinInject<UiPrefs>()

    // Language
    val initialLanguage = remember { LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage() }
    var language by remember { mutableStateOf<AppLanguage>(initialLanguage) }
    val strings = remember(language) { stringsFor(language) }

    // Theme. The stored mode is what the person picked; it is ignored while "follow system" is on.
    val systemDark = isSystemInDarkTheme()
    val followsSystem by uiPrefs.themeFollowsSystem.collectAsState()
    var storedThemeMode by remember {
        mutableStateOf(ThemeStorage.getSavedThemeMode() ?: if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT)
    }
    val themeMode = if (followsSystem) {
        if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
    } else {
        storedThemeMode
    }
    var themePalette by remember { mutableStateOf(ThemeStorage.getSavedThemePalette() ?: ThemePalette.FOREST) }

    // Reading
    var readingFontStyle by remember {
        mutableStateOf(ThemeStorage.getSavedReadingFontStyle() ?: ReadingFontStyle.MADINAH_MUSHAF)
    }
    var ayahCardStyle by remember {
        mutableStateOf(ThemeStorage.getSavedAyahCardStyle() ?: AyahCardStyle.CLASSIC)
    }
    var appSettings by remember { mutableStateOf(loadAppSettings()) }

    // Screens that persist settings on their own (the settings screen) announce it through the notifier.
    val settingsRevision by SettingsChangeNotifier.revision.collectAsState()
    var lastSyncedSavedTheme by remember { mutableStateOf(ThemeStorage.getSavedThemeMode()) }
    LaunchedEffect(settingsRevision) {
        if (settingsRevision == 0L) return@LaunchedEffect
        language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
        val savedTheme = ThemeStorage.getSavedThemeMode()
        if (savedTheme != null && savedTheme != lastSyncedSavedTheme) {
            // A screen that does not know about UiPrefs saved an explicit theme: stop following the system.
            uiPrefs.setThemeFollowsSystem(false)
        }
        lastSyncedSavedTheme = savedTheme
        storedThemeMode = savedTheme ?: storedThemeMode
        themePalette = ThemeStorage.getSavedThemePalette() ?: themePalette
        readingFontStyle = ThemeStorage.getSavedReadingFontStyle() ?: readingFontStyle
        ayahCardStyle = ThemeStorage.getSavedAyahCardStyle() ?: ayahCardStyle
        appSettings = loadAppSettings()
    }

    LaunchedEffect(Unit) {
        UserSettingsStorage.saveReminderEnabled(false)
        ReminderScheduler.syncDailyReminder(
            enabled = false,
            title = strings.reminderTitle,
            body = strings.reminderBody,
        )
    }
    LaunchedEffect(Unit) {
        AppTelemetry.logEvent("app_opened")
    }

    val systemReducedMotion = rememberSystemReducedMotion()
    val userReducedMotion by uiPrefs.reduceMotion.collectAsState()
    val hapticsEnabled by uiPrefs.hapticsEnabled.collectAsState()
    val toaster = rememberToaster()

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalAppLanguage provides language,
        LocalAppLanguageSetter provides { next ->
            if (language != next) {
                AppTelemetry.logEvent(
                    name = "language_changed",
                    params = mapOf("from" to language.name.lowercase(), "to" to next.name.lowercase()),
                )
            }
            language = next
            LanguageStorage.saveLanguage(next)
        },
        LocalThemeMode provides themeMode,
        LocalThemeModeSetter provides { next ->
            if (themeMode != next) {
                AppTelemetry.logEvent(
                    name = "theme_mode_changed",
                    params = mapOf("mode" to next.name.lowercase()),
                )
            }
            // An explicit choice ends "follow system".
            uiPrefs.setThemeFollowsSystem(false)
            storedThemeMode = next
            ThemeStorage.saveThemeMode(next)
            lastSyncedSavedTheme = next
        },
        LocalThemePalette provides themePalette,
        LocalThemePaletteSetter provides { next ->
            if (themePalette != next) {
                AppTelemetry.logEvent(
                    name = "theme_palette_changed",
                    params = mapOf("palette" to next.name.lowercase()),
                )
            }
            themePalette = next
            ThemeStorage.saveThemePalette(next)
        },
        LocalReadingFontStyle provides readingFontStyle,
        LocalReadingFontStyleSetter provides { next ->
            if (readingFontStyle != next) {
                AppTelemetry.logEvent(
                    name = "reading_font_changed",
                    params = mapOf("font" to next.name.lowercase()),
                )
            }
            readingFontStyle = next
            ThemeStorage.saveReadingFontStyle(next)
        },
        LocalAyahCardStyle provides ayahCardStyle,
        LocalAyahCardStyleSetter provides { next ->
            if (ayahCardStyle != next) {
                AppTelemetry.logEvent(
                    name = "ayah_card_style_changed",
                    params = mapOf("style" to next.name.lowercase()),
                )
            }
            ayahCardStyle = next
            ThemeStorage.saveAyahCardStyle(next)
        },
        LocalAppSettings provides appSettings,
        LocalAppSettingsSetter provides { next ->
            val changedFields = buildList {
                if (appSettings.dailyGoal != next.dailyGoal) add("daily_goal")
                if (appSettings.focusMinutes != next.focusMinutes) add("focus_minutes")
                if (appSettings.remindersEnabled != next.remindersEnabled) add("reminders")
                if (appSettings.targetAyahs != next.targetAyahs) add("target_ayahs")
                if (appSettings.targetEpochDay != next.targetEpochDay) add("target_day")
                if (appSettings.examModeEnabled != next.examModeEnabled) add("exam_mode")
            }
            if (changedFields.isNotEmpty()) {
                AppTelemetry.logEvent(
                    name = "app_settings_changed",
                    params = mapOf("fields" to changedFields.joinToString(",")),
                )
            }
            appSettings = next
            UserSettingsStorage.saveDailyGoal(next.dailyGoal)
            UserSettingsStorage.saveFocusMinutes(next.focusMinutes)
            UserSettingsStorage.saveReminderEnabled(next.remindersEnabled)
            UserSettingsStorage.saveTargetAyahs(next.targetAyahs)
            UserSettingsStorage.saveTargetEpochDay(next.targetEpochDay)
            UserSettingsStorage.saveExamModeEnabled(next.examModeEnabled)
            UserSettingsStorage.saveReadingFontSize(next.readingFontSize)
        },
        LocalReducedMotion provides (systemReducedMotion || userReducedMotion),
        LocalToaster provides toaster,
    ) {
        QhTheme(
            mode = themeMode,
            palette = themePalette,
            reducedMotion = userReducedMotion,
            hapticsEnabled = hapticsEnabled,
        ) {
            val background = QhTheme.colors.bg
            SystemBarsEffect(isDark = themeMode == ThemeMode.DARK, background = background)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background),
            ) {
                AppNavigation()
                ToastHost(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * Edge-to-edge with transparent system bars; icons are light on a dark theme and dark on a light one.
 * Screens draw their own backgrounds under the bars. The window itself is painted with the theme
 * background so no frame (rotation, first frame, overscroll) shows a foreign colour.
 */
@Composable
private fun SystemBarsEffect(isDark: Boolean, background: Color) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    DisposableEffect(activity, isDark) {
        val style = if (isDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Otherwise 3-button navigation gets a grey contrast scrim outside the app palette.
            activity.window.isNavigationBarContrastEnforced = false
        }
        onDispose { }
    }
    SideEffect {
        activity.window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavigation() {
    val context = LocalContext.current
    val isDebuggable = remember {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val reducedMotion = LocalReducedMotion.current
    val startDestination = remember { if (needsOnboarding()) Dest.Onboarding else Dest.Home }
    val navController = rememberNavController()

    val currentEntry by navController.currentBackStackEntryAsState()
    val screenName = currentEntry?.destination?.screenName()
    LaunchedEffect(screenName) {
        if (screenName != null) AppTelemetry.logScreen(screenName)
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
                enterTransition = NavTransitions.enter(reducedMotion),
                exitTransition = NavTransitions.exit(reducedMotion),
                popEnterTransition = NavTransitions.popEnter(reducedMotion),
                popExitTransition = NavTransitions.popExit(reducedMotion),
            ) {
                composable<Dest.Onboarding> {
                    ProvideNavScope {
                        OnboardingScreen(
                            onFinished = {
                                navController.navigateOnce(Dest.Home) {
                                    popUpTo<Dest.Onboarding> { inclusive = true }
                                }
                            },
                        )
                    }
                }
                composable<Dest.Home> {
                    ProvideNavScope {
                        HomeScreen(
                            onOpenReader = { navController.navigateOnce(it) },
                            onOpenSettings = { navController.navigateOnce(Dest.Settings) },
                            onOpenProgress = { navController.navigateOnce(Dest.Progress) },
                            onOpenCatalog = if (isDebuggable) {
                                { navController.navigateOnce(Dest.Catalog) }
                            } else {
                                null
                            },
                        )
                    }
                }
                composable<Dest.Reader> { entry ->
                    val route = entry.toRoute<Dest.Reader>()
                    ProvideNavScope {
                        ReaderScreen(
                            route = route,
                            onBack = { navController.popOnce() },
                            onOpenReader = { navController.navigateOnce(it) },
                        )
                    }
                }
                composable<Dest.Progress> {
                    ProvideNavScope {
                        ProgressScreen(
                            onBack = { navController.popOnce() },
                            onOpenReader = { navController.navigateOnce(it) },
                        )
                    }
                }
                composable<Dest.Settings> {
                    ProvideNavScope {
                        SettingsScreen(onBack = { navController.popOnce() })
                    }
                }
                if (isDebuggable) {
                    composable<Dest.Catalog> {
                        ProvideNavScope {
                            CatalogScreen(onBack = { navController.popOnce() })
                        }
                    }
                }
            }
        }
    }
}

/** Gives the screen of a NavHost entry its [LocalNavAnimatedVisibilityScope] for shared elements. */
@Composable
private fun AnimatedContentScope.ProvideNavScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this, content = content)
}

/**
 * Navigation is accepted only while the current screen is fully shown: its back stack entry is RESUMED
 * once the transition into it has finished. This swallows double taps and taps during a transition.
 */
private fun NavController.isIdle(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

private fun NavController.navigateOnce(route: Dest, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (isIdle()) navigate(route, builder)
}

private fun NavController.popOnce() {
    if (isIdle()) popBackStack()
}

private fun NavDestination.screenName(): String? = when {
    hasRoute<Dest.Onboarding>() -> "onboarding"
    hasRoute<Dest.Home>() -> "home"
    hasRoute<Dest.Reader>() -> "reader"
    hasRoute<Dest.Progress>() -> "progress"
    hasRoute<Dest.Settings>() -> "settings"
    hasRoute<Dest.Catalog>() -> "catalog"
    else -> null
}

private fun loadAppSettings(): AppSettings {
    val todayEpochDay = currentLocalDate().toEpochDays()
    return AppSettings(
        dailyGoal = UserSettingsStorage.getDailyGoal()?.coerceIn(1, 50) ?: 5,
        focusMinutes = UserSettingsStorage.getFocusMinutes()?.coerceIn(5, 60) ?: 10,
        remindersEnabled = UserSettingsStorage.isReminderEnabled() ?: true,
        targetAyahs = UserSettingsStorage.getTargetAyahs()?.coerceIn(50, 6236) ?: 300,
        targetEpochDay = UserSettingsStorage.getTargetEpochDay() ?: (todayEpochDay + 60),
        examModeEnabled = UserSettingsStorage.isExamModeEnabled() ?: false,
        readingFontSize = UserSettingsStorage.getReadingFontSize()
            ?.coerceIn(MinReadingFontSize, MaxReadingFontSize) ?: 28,
    )
}
