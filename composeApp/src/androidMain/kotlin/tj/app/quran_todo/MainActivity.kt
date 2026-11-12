package tj.app.quran_todo

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLocale
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.QuranHifzApp
import tj.app.quran_todo.ui.needsOnboarding
import tj.app.quran_todo.ui.onboarding.OnboardingStartup
import tj.app.quran_todo.ui.reminder.ReminderLink
import tj.app.quran_todo.ui.reminder.ReminderOpenRequests
import tj.app.quran_todo.ui.reminder.ReminderTarget
import tj.app.quran_todo.ui.store.AppUpdates

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by inject()

    /** Google Play in-app updates; registered as a property, before the activity starts. */
    private val appUpdates = AppUpdates(this)

    /** Before Android 13 the app's language reaches Android through the activity's context (AppLocale). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Android 13+: a language picked in the system settings becomes the app's choice (before the first screen reads it).
        AppLocale.syncFromSystem(this)
        if (savedInstanceState == null) appUpdates.check()
        // The onboarding decision comes from a worker thread (it reads the stored flags, `OnboardingStartup`): the splash screen stays
        // until it is there, so the main thread never waits for the disk and the first screen is never the wrong one.
        if (OnboardingStartup.isReady) {
            startContent(splashScreen, savedInstanceState)
        } else {
            splashScreen.setKeepOnScreenCondition { true }
            lifecycleScope.launch {
                // On a timeout (the worker failed) needsOnboarding() reads the flags itself.
                withTimeoutOrNull(ONBOARDING_WAIT_MILLIS) { OnboardingStartup.await() }
                startContent(splashScreen, savedInstanceState)
            }
        }
    }

    private fun startContent(splashScreen: SplashScreen, savedInstanceState: Bundle?) {
        configureSplashScreen(splashScreen)
        // The app root re-applies the bar icon style whenever the theme changes.
        enableEdgeToEdge()
        // A recreated activity (state restored) keeps the screens the person had: its intent must not open the link again.
        handleReminderLink(intent, restored = savedInstanceState != null)
        setContent { QuranHifzApp() }
    }

    /** The app is already open and the person taps the reminder: the link arrives here (`launchMode="singleTop"`). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderLink(intent, restored = false)
    }

    /**
     * Takes the link of the daily reminder out of [intent] (open the lesson, or the reader at a surah and ayah) and gives it to the
     * navigation. The extras are removed from the intent in any case, so the same link is never handled twice: not after a rotation
     * or any recreation of the activity (which hands over the same intent again), and not when the app is reopened from the recents
     * (the system then repeats the intent that started the task). Before onboarding is done there is nothing to open on top of: the
     * app just starts.
     */
    private fun handleReminderLink(intent: Intent?, restored: Boolean) {
        val target = ReminderLink.targetFrom(intent)
        val fromWidget = ReminderLink.sourceFrom(intent) == ReminderLink.SOURCE_WIDGET
        if (intent != null) ReminderLink.consume(intent)
        if (target == null || restored || needsOnboarding()) return
        if (intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        AppTelemetry.logEvent(
            name = if (fromWidget) "widget_opened" else "reminder_opened",
            params = mapOf("kind" to if (target is ReminderTarget.Lesson) "lesson" else "reading"),
        )
        ReminderOpenRequests.post(target)
    }

    /**
     * Keeps the splash until the Quran is read from the database (at most [SPLASH_MAX_MILLIS]); the
     * system then plays its own exit. A custom exit animation (`setOnExitAnimationListener`) left the
     * starting window stuck in its reveal on API 35 until the first touch, so it is not used.
     *
     * The keep condition is only re-checked when the content view is about to draw. A static first
     * screen requests no new frame, so the splash would stay until the first touch: when the wait
     * ends, the content view is invalidated to get that frame.
     */
    private fun configureSplashScreen(splashScreen: SplashScreen) {
        // Onboarding does not need the data, so there is nothing to wait for.
        var keepSplash = !needsOnboarding()
        splashScreen.setKeepOnScreenCondition { keepSplash }
        if (keepSplash) {
            lifecycleScope.launch {
                withTimeoutOrNull(SPLASH_MAX_MILLIS) {
                    homeViewModel.uiState.first { state ->
                        state.quranLoadState !is QuranLoadState.Loading || state.completeQuran.isNotEmpty()
                    }
                }
                keepSplash = false
                findViewById<View>(android.R.id.content)?.invalidate()
            }
        }
    }

    /**
     * The activity handles locale changes itself (`configChanges`), so a language picked in the system settings while the app runs
     * arrives here: it is saved as the app's choice and the app root re-reads it.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (AppLocale.syncFromSystem(this)) SettingsChangeNotifier.notifyChanged()
    }

    override fun onResume() {
        super.onResume()
        AndroidContextHolder.activity = this
        appUpdates.onResume()
    }

    override fun onDestroy() {
        appUpdates.onDestroy()
        super.onDestroy()
    }

    override fun onPause() {
        if (AndroidContextHolder.activity === this) {
            AndroidContextHolder.activity = null
        }
        super.onPause()
    }

    private companion object {
        const val SPLASH_MAX_MILLIS = 900L
        const val ONBOARDING_WAIT_MILLIS = 5_000L
    }
}
