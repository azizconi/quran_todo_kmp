package tj.app.quran_todo

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.koin.android.ext.android.inject
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.QuranHifzApp
import tj.app.quran_todo.ui.needsOnboarding

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        configureSplashScreen(splashScreen)
        // The app root re-applies the bar icon style whenever the theme changes.
        enableEdgeToEdge()
        setContent { QuranHifzApp() }
    }

    /**
     * Keeps the splash until the Quran is read from the database (at most [SPLASH_MAX_MILLIS]) and
     * shrinks and fades it out when the first screen is ready.
     */
    private fun configureSplashScreen(splashScreen: SplashScreen) {
        val startedAt = SystemClock.elapsedRealtime()
        // Onboarding does not need the data, so there is nothing to wait for.
        val waitsForQuran = !needsOnboarding()
        splashScreen.setKeepOnScreenCondition {
            waitsForQuran &&
                SystemClock.elapsedRealtime() - startedAt < SPLASH_MAX_MILLIS &&
                homeViewModel.uiState.value.let { state ->
                    state.quranLoadState is QuranLoadState.Loading && state.completeQuran.isEmpty()
                }
        }
        splashScreen.setOnExitAnimationListener { splash ->
            splash.view.animate()
                .alpha(0f)
                .scaleX(SPLASH_EXIT_SCALE)
                .scaleY(SPLASH_EXIT_SCALE)
                .setDuration(SPLASH_EXIT_MILLIS)
                .withEndAction { splash.remove() }
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidContextHolder.activity = this
    }

    override fun onPause() {
        if (AndroidContextHolder.activity === this) {
            AndroidContextHolder.activity = null
        }
        super.onPause()
    }

    private companion object {
        const val SPLASH_MAX_MILLIS = 900L
        const val SPLASH_EXIT_MILLIS = 280L
        const val SPLASH_EXIT_SCALE = 0.9f
    }
}
