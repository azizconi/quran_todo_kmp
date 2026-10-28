package tj.app.quran_todo

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
    }
}
