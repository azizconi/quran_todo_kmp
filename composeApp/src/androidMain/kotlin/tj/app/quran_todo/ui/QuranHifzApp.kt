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
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguageSetter
import tj.app.quran_todo.common.i18n.LocalAppStrings
import tj.app.quran_todo.common.i18n.stringsFor
import tj.app.quran_todo.common.reminder.ReminderChannel
import tj.app.quran_todo.common.reminder.ReminderSync
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
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.ui.about.PrivacyScreen
import tj.app.quran_todo.ui.about.SourcesScreen
import tj.app.quran_todo.ui.tafsir.TafsirScreen
import tj.app.quran_todo.ui.widget.LessonWidgetUpdater
import tj.app.quran_todo.ui.navigation.LocalTafsirOpener
import tj.app.quran_todo.ui.catalog.CatalogScreen
import tj.app.quran_todo.ui.components.LocalToaster
import tj.app.quran_todo.ui.components.ToastHost
import tj.app.quran_todo.ui.store.UpdateReadyBanner
import tj.app.quran_todo.ui.components.rememberToaster
import tj.app.quran_todo.ui.components.spotlight.SpotlightNavigationEffect
import tj.app.quran_todo.ui.components.spotlight.SpotlightHost
import tj.app.quran_todo.ui.components.spotlight.rememberSpotlightController
import tj.app.quran_todo.ui.downloads.DownloadsScreen
import tj.app.quran_todo.ui.drill.DrillScreen
import tj.app.quran_todo.ui.home.HomeScreen
import tj.app.quran_todo.ui.khatm.KhatmScreen
import tj.app.quran_todo.ui.lesson.LessonScreen
import tj.app.quran_todo.ui.motion.LocalNavAnimatedVisibilityScope
import tj.app.quran_todo.ui.motion.LocalReducedMotion
import tj.app.quran_todo.ui.motion.LocalSharedTransitionScope
import tj.app.quran_todo.ui.motion.ThemeReveal
import tj.app.quran_todo.ui.motion.ThemeRevealHost
import tj.app.quran_todo.ui.motion.rememberSystemReducedMotion
import tj.app.quran_todo.ui.motion.rememberThemeReveal
import tj.app.quran_todo.ui.mushaf.MushafScreen
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.navigation.NavTransitions
import tj.app.quran_todo.ui.navigation.ReaderRouting
import tj.app.quran_todo.ui.onboarding.OnboardingExit
import tj.app.quran_todo.ui.onboarding.OnboardingGate
import tj.app.quran_todo.ui.onboarding.OnboardingStartup
import tj.app.quran_todo.ui.onboarding.OnboardingScreen
import tj.app.quran_todo.ui.plan.PlanScreen
import tj.app.quran_todo.ui.notes.NotesScreen
import tj.app.quran_todo.ui.progress.ProgressScreen
import tj.app.quran_todo.ui.progress.analytics.AnalyticsScreen
import tj.app.quran_todo.ui.quiz.QuizMode
import tj.app.quran_todo.ui.quiz.QuizScreen
import tj.app.quran_todo.ui.similar.SimilarSheetHost
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.ui.reminder.ReminderOpenRequests
import tj.app.quran_todo.ui.reminder.ReminderTarget
import tj.app.quran_todo.ui.reader.ReaderScreen
import tj.app.quran_todo.ui.settings.SettingsScreen
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.theme.QhTheme
import tj.app.quran_todo.ui.whatsnew.WhatsNewGate
import tj.app.quran_todo.ui.whatsnew.WhatsNewHost

/** Reading font size range of the redesign (docs/redesign/ANDROID_REDESIGN.md, 2.2). */
private const val MinReadingFontSize = 22
private const val MaxReadingFontSize = 40

/**
 * True until the onboarding v2 is passed. People who used an older version (the language and the reading font saved by it) are
 * existing users: they never see it, and the first call marks them as having passed it (see [OnboardingGate]). Shared by the app
 * root, `MainActivity` and the splash screen, which does not wait for data when onboarding is next; reads the stored state
 * directly, so it works before Compose and Koin are up.
 */
internal fun needsOnboarding(): Boolean = OnboardingStartup.current().gate.showsOnboarding

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

    // The daily lesson reminder (B4): every start plans the alarm again from the stored settings (the alarm does not survive
    // an update or a restart on its own), so a reminder the person turned on keeps coming.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { ReminderSync.sync(appContext) }
    }
    // The name of the reminder channel in the system settings follows the language of the app.
    LaunchedEffect(language) {
        withContext(Dispatchers.Default) { ReminderChannel.renameIfExists(appContext, language) }
        // The widget "Lesson of the day" speaks the language of the app (task F5b).
        withContext(Dispatchers.Default) { LessonWidgetUpdater.refresh(appContext) }
    }
    LaunchedEffect(Unit) {
        AppTelemetry.logEvent("app_opened")
    }

    // Back from the background the day may have changed (midnight passed while the process slept, or the
    // date / time zone was changed): the lesson card follows the day without a restart.
    val dayTicker = koinInject<EpochDayTicker>()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { dayTicker.refresh() }

    val systemReducedMotion = rememberSystemReducedMotion()
    val userReducedMotion by uiPrefs.reduceMotion.collectAsState()
    val hapticsEnabled by uiPrefs.hapticsEnabled.collectAsState()
    val toaster = rememberToaster()
    val spotlight = rememberSpotlightController()

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
            val reveal = rememberThemeReveal()
            val isDark = themeMode == ThemeMode.DARK
            SideEffect { reveal.currentDark = isDark }
            SystemBarsEffect(reveal = reveal, isDark = isDark, background = background)

            ThemeRevealHost(
                reveal = reveal,
                modifier = Modifier.fillMaxSize(),
                background = background,
            ) {
                // The tips (C2): a layer over the navigation and the mini player, in the same window.
                SpotlightHost(spotlight, Modifier.fillMaxSize()) { AppNavigation() }
                ToastHost(Modifier.align(Alignment.TopCenter))
                UpdateReadyBanner(Modifier.align(Alignment.BottomCenter))
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
private fun SystemBarsEffect(reveal: ThemeReveal, isDark: Boolean, background: Color) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    // While the circular theme reveal runs each bar switches icon style when the circle reaches it, so the
    // icons always sit on the colour they were drawn for (outside a reveal both follow the theme at once).
    val latestDark by rememberUpdatedState(isDark)
    val statusDark by remember(reveal) { derivedStateOf { reveal.barIsDark(latestDark, top = true) } }
    val navigationDark by remember(reveal) { derivedStateOf { reveal.barIsDark(latestDark, top = false) } }
    DisposableEffect(activity, statusDark, navigationDark) {
        fun styleFor(dark: Boolean) = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = styleFor(statusDark), navigationBarStyle = styleFor(navigationDark))
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
    // Every entrance of the app asks for a Dest.Reader and gets the reader of the mode the person chose: the text or the pages (F3a).
    val uiPrefs = koinInject<UiPrefs>()
    val openReader: (Dest.Reader) -> Unit = remember(navController, uiPrefs) { { dest -> navController.openReader(dest, uiPrefs) } }

    val currentEntry by navController.currentBackStackEntryAsState()
    val screenName = currentEntry?.destination?.screenName()
    LaunchedEffect(screenName) {
        if (screenName != null) AppTelemetry.logScreen(screenName)
    }

    // The tips start only on a screen that is fully shown (its entry is RESUMED once the transition into it is over).
    SpotlightNavigationEffect(route = screenName, lifecycle = currentEntry?.lifecycle)

    // A tap on the daily reminder: the lesson or the reader opens over Home, and back goes to Home.
    val openRequest by ReminderOpenRequests.pending.collectAsState()
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        // The graph is set up and the screen is still: not in the middle of a transition (the first frames of a cold start).
        val ready = withTimeoutOrNull(OpenRequestWaitMillis) {
            while (!navController.isIdle()) delay(OpenRequestPollMillis)
            true
        }
        ReminderOpenRequests.consume(request)
        if (ready == true) navController.openFromReminder(request.target, uiPrefs)
    }

    // "What's new: the lesson of the day" for people who updated: decided once, now that needsOnboarding() has marked them. A link from
    // the reminder in this launch puts it off to the next one.
    val whatsNewPending = remember { WhatsNewGate.pending(OnboardingStartup.current().flags) }
    var whatsNewSuppressed by remember { mutableStateOf(openRequest != null) }
    LaunchedEffect(openRequest) { if (openRequest != null) whatsNewSuppressed = true }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalTafsirOpener provides { surah, ayah -> navController.navigateOnce(Dest.Tafsir(surah, ayah)) },
        ) {
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
                            onFinished = { exit ->
                                navController.navigateOnce(Dest.Home) {
                                    popUpTo<Dest.Onboarding> { inclusive = true }
                                }
                                // The first lesson or Al-Fatihah opens over Home, so back lands on Home.
                                when (exit) {
                                    OnboardingExit.Lesson -> navController.navigate(Dest.Lesson)
                                    OnboardingExit.Reader -> navController.navigate(
                                        ReaderRouting.route(Dest.Reader(surah = 1, origin = OnboardingOrigin), uiPrefs.readerLayout.value, uiPrefs.lastReadPosition.value),
                                    )
                                    OnboardingExit.Later -> Unit
                                }
                            },
                        )
                    }
                }
                composable<Dest.Home> {
                    ProvideNavScope {
                        HomeScreen(
                            onOpenReader = openReader,
                            onOpenSettings = { navController.navigateOnce(Dest.Settings) },
                            onOpenProgress = { navController.navigateOnce(Dest.Progress) },
                            onOpenLesson = { navController.navigateOnce(Dest.Lesson) },
                            onOpenPlan = { navController.navigateOnce(Dest.Plan) },
                            onOpenQuiz = { navController.navigateOnce(Dest.Quiz) },
                            onOpenKhatm = { navController.navigateOnce(Dest.Khatm()) },
                            onOpenKhatmPicker = { navController.navigateOnce(Dest.Khatm(pickTarget = true)) },
                            // A khatm is read in pages: the pages open whatever mode the reader is in.
                            onOpenMushaf = { page -> navController.navigateOnce(Dest.Mushaf(page = page)) },
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
                            // The next surah and the chip "playing elsewhere" are the text reader's own moves: they stay in the text.
                            onOpenReader = { navController.navigateOnce(it) },
                            onOpenPages = { surah, ayah ->
                                // The switch "Text to Pages": the page of the first ayah on screen replaces the reader.
                                navController.navigateOnce(ReaderRouting.pagesFor(surah, ayah)) {
                                    popUpTo<Dest.Reader> { inclusive = true }
                                }
                            },
                        )
                    }
                }
                composable<Dest.Mushaf> { entry ->
                    val route = entry.toRoute<Dest.Mushaf>()
                    ProvideNavScope {
                        MushafScreen(
                            route = route,
                            onBack = { navController.popOnce() },
                            onOpenText = { reader ->
                                // The switch "Pages to Text" (and "Open as text"): the text reader replaces the pages.
                                navController.navigateOnce(reader) { popUpTo<Dest.Mushaf> { inclusive = true } }
                            },
                        )
                    }
                }
                composable<Dest.Lesson> {
                    ProvideNavScope {
                        LessonScreen(
                            onBack = { navController.popOnce() },
                            onOpenPlan = { navController.navigateOnce(Dest.Plan) },
                            onOpenQuiz = { navController.navigateOnce(Dest.Quiz) },
                            onHome = { navController.goHome() },
                            onOpenDrill = { navController.navigateOnce(Dest.HardDrill) },
                        )
                    }
                }
                composable<Dest.Plan> {
                    ProvideNavScope {
                        PlanScreen(onBack = { navController.popOnce() })
                    }
                }
                composable<Dest.Quiz> {
                    ProvideNavScope {
                        QuizScreen(
                            onBack = { navController.popOnce() },
                            onOpenReader = openReader,
                            onTellApart = { navController.navigateOnce(Dest.TellApart()) },
                        )
                    }
                }
                composable<Dest.TellApart> { entry ->
                    val route = entry.toRoute<Dest.TellApart>()
                    ProvideNavScope {
                        QuizScreen(
                            onBack = { navController.popOnce() },
                            onOpenReader = openReader,
                            mode = QuizMode.TellApart(focus = AyahKey(route.surah, route.ayah).takeIf { route.surah > 0 }),
                        )
                    }
                }
                composable<Dest.Progress> {
                    ProvideNavScope {
                        ProgressScreen(
                            onBack = { navController.popOnce() },
                            onOpenReader = openReader,
                            onOpenPlan = { navController.navigateOnce(Dest.Plan) },
                            onOpenDrill = { navController.navigateOnce(Dest.HardDrill) },
                            onOpenDetails = { navController.navigateOnce(Dest.ProgressDetails) },
                            onOpenNotes = { navController.navigateOnce(Dest.Notes) },
                        )
                    }
                }
                composable<Dest.ProgressDetails> {
                    ProvideNavScope {
                        AnalyticsScreen(
                            onBack = { navController.popOnce() },
                            onOpenReader = openReader,
                            onOpenDrill = { navController.navigateOnce(Dest.HardDrill) },
                            onOpenPlan = { navController.navigateOnce(Dest.Plan) },
                        )
                    }
                }
                composable<Dest.Notes> {
                    ProvideNavScope {
                        NotesScreen(
                            onBack = { navController.popOnce() },
                            onOpenReader = openReader,
                        )
                    }
                }
                composable<Dest.Khatm> { entry ->
                    val route = entry.toRoute<Dest.Khatm>()
                    ProvideNavScope {
                        KhatmScreen(
                            openTargetPicker = route.pickTarget,
                            onBack = { navController.popOnce() },
                            onOpenMushaf = { page -> navController.navigateOnce(Dest.Mushaf(page = page)) },
                        )
                    }
                }
                composable<Dest.HardDrill> {
                    ProvideNavScope {
                        DrillScreen(onBack = { navController.popOnce() })
                    }
                }
                composable<Dest.Settings> {
                    ProvideNavScope {
                        SettingsScreen(
                            onBack = { navController.popOnce() },
                            onOpenPrivacy = { navController.navigateOnce(Dest.Privacy) },
                            onOpenSources = { navController.navigateOnce(Dest.Sources) },
                            onOpenPlan = { navController.navigateOnce(Dest.Plan) },
                            onOpenDownloads = { navController.navigateOnce(Dest.Downloads) },
                            onOpenNotes = { navController.navigateOnce(Dest.Notes) },
                        )
                    }
                }
                composable<Dest.Downloads> {
                    ProvideNavScope {
                        DownloadsScreen(onBack = { navController.popOnce() })
                    }
                }
                composable<Dest.Privacy> {
                    ProvideNavScope {
                        PrivacyScreen(onBack = { navController.popOnce() })
                    }
                }
                composable<Dest.Tafsir> { entry ->
                    val route = entry.toRoute<Dest.Tafsir>()
                    ProvideNavScope {
                        TafsirScreen(route = route, onBack = { navController.popOnce() })
                    }
                }
                composable<Dest.Sources> {
                    ProvideNavScope {
                        SourcesScreen(onBack = { navController.popOnce() })
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

    // The sheet "Similar verses" (F6): one host over every screen; the ayah sheet, the mark of an ayah and the lesson only ask it to open.
    SimilarSheetHost(
        onOpenReader = openReader,
        onTellApart = { key -> navController.navigateOnce(Dest.TellApart(key.surah, key.ayah)) },
    )

    WhatsNewHost(
        pending = whatsNewPending,
        atHome = screenName == "home",
        suppressed = whatsNewSuppressed,
        onOpenLesson = { navController.navigateOnce(Dest.Lesson) },
        onOpenPlan = { navController.navigateOnce(Dest.Plan) },
    )
}

/** Gives the screen of a NavHost entry its [LocalNavAnimatedVisibilityScope] for shared elements. */
@Composable
private fun AnimatedContentScope.ProvideNavScope(content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    // The screen that is leaving (a forward push, a pop, the back gesture) rounds its corners as the transition runs.
    // The value follows the transition, so under the predictive back gesture it follows the finger.
    val corner by transition.animateDp(label = "leaving-corner", transitionSpec = { tween(380) }) { state ->
        if (state == EnterExitState.PostExit && !reduced) NavTransitions.LeavingCornerDp.dp else 0.dp
    }
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (corner > 0.dp) {
                        shape = RoundedCornerShape(corner)
                        clip = true
                    }
                },
        ) { content() }
    }
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

/**
 * The one place that opens the reader (F3a): [dest] goes through [ReaderRouting.route], so it opens the text or the page of the ayah by
 * the mode the person chose ([UiPrefs.readerLayout]). Like every navigation it is taken only while the screen is still.
 */
private fun NavController.openReader(dest: Dest.Reader, uiPrefs: UiPrefs) {
    navigateOnce(ReaderRouting.route(dest, uiPrefs.readerLayout.value, uiPrefs.lastReadPosition.value))
}

/** Back to Home from deep inside (the lesson summary); when Home is not on the stack it becomes the root. */
private fun NavController.goHome() {
    if (!isIdle()) return
    if (!popBackStack<Dest.Home>(inclusive = false)) {
        navigate(Dest.Home) { popUpTo(0) }
    }
}

private const val OpenRequestWaitMillis = 3_000L
private const val OpenRequestPollMillis = 40L

/** Origin of the reader opened by the last onboarding button: it matches no shared element, so the reader simply slides in. */
private const val OnboardingOrigin = "onboarding"

/** Origin of a reader opened from the reminder: it matches no shared element, so the reader simply slides in. */
private const val ReminderOrigin = "reminder"

/**
 * Opens what the daily reminder points at over Home: everything above Home is dropped first, so back always lands on Home. During
 * onboarding (no Home on the stack) nothing happens, and a lesson that is already open stays as it is.
 */
private fun NavController.openFromReminder(target: ReminderTarget, uiPrefs: UiPrefs) {
    if (currentBackStack.value.none { it.destination.hasRoute<Dest.Home>() }) return
    when (target) {
        ReminderTarget.Lesson -> {
            if (currentDestination?.hasRoute<Dest.Lesson>() == true) return
            navigate(Dest.Lesson) { popUpTo<Dest.Home> { inclusive = false } }
        }
        is ReminderTarget.Reader -> navigate(
            ReaderRouting.route(
                Dest.Reader(surah = target.surah, ayah = target.ayah, origin = ReminderOrigin),
                uiPrefs.readerLayout.value,
                uiPrefs.lastReadPosition.value,
            ),
        ) {
            popUpTo<Dest.Home> { inclusive = false }
        }
    }
}

private fun NavDestination.screenName(): String? = when {
    hasRoute<Dest.Onboarding>() -> "onboarding"
    hasRoute<Dest.Home>() -> "home"
    hasRoute<Dest.Reader>() -> "reader"
    hasRoute<Dest.Mushaf>() -> "mushaf"
    hasRoute<Dest.Lesson>() -> "lesson"
    hasRoute<Dest.Plan>() -> "plan"
    hasRoute<Dest.Quiz>() -> "quiz"
    hasRoute<Dest.TellApart>() -> "tell_apart"
    hasRoute<Dest.Progress>() -> "progress"
    hasRoute<Dest.Khatm>() -> "khatm"
    hasRoute<Dest.ProgressDetails>() -> "progress_details"
    hasRoute<Dest.Notes>() -> "notes"
    hasRoute<Dest.HardDrill>() -> "hard_drill"
    hasRoute<Dest.Settings>() -> "settings"
    hasRoute<Dest.Downloads>() -> "downloads"
    hasRoute<Dest.Privacy>() -> "privacy"
    hasRoute<Dest.Sources>() -> "sources"
    hasRoute<Dest.Tafsir>() -> "tafsir"
    hasRoute<Dest.Catalog>() -> "catalog"
    else -> null
}

private fun loadAppSettings(): AppSettings {
    val todayEpochDay = currentLocalDate().toEpochDays()
    return AppSettings(
        dailyGoal = UserSettingsStorage.getDailyGoal()?.coerceIn(1, 50) ?: 5,
        focusMinutes = UserSettingsStorage.getFocusMinutes()?.coerceIn(5, 60) ?: 10,
        // The reminder of the Android app lives in UiPrefs (task B4); this old field is not read and nothing shows it.
        remindersEnabled = false,
        targetAyahs = UserSettingsStorage.getTargetAyahs()?.coerceIn(50, 6236) ?: 300,
        targetEpochDay = UserSettingsStorage.getTargetEpochDay() ?: (todayEpochDay + 60),
        examModeEnabled = UserSettingsStorage.isExamModeEnabled() ?: false,
        readingFontSize = UserSettingsStorage.getReadingFontSize()
            ?.coerceIn(MinReadingFontSize, MaxReadingFontSize) ?: 28,
    )
}
