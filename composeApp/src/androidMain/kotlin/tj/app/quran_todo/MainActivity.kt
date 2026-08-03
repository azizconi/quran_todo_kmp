package tj.app.quran_todo

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tj.app.quran_todo.common.theme.AppChromeAppearance
import tj.app.quran_todo.common.theme.ThemePalette
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.presentation.settings.NativeSettingsScreen
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.theme.ThemeMode
import tj.app.quran_todo.common.theme.appChromeAppearance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialChrome = savedChromeAppearance()
        updateSystemBars(initialChrome)

        setContent {
            var chromeAppearance by remember { mutableStateOf(initialChrome) }
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val isReaderRoute = currentRoute == "surah/{number}"
            val onChromeChanged: (AppChromeAppearance) -> Unit = { next ->
                chromeAppearance = next
            }

            // Reader's compact player owns the visual bottom edge. Other screens continue
            // their own background into the system navigation area.
            LaunchedEffect(chromeAppearance, isReaderRoute) {
                val navigationBarColor = if (isReaderRoute) {
                    chromeAppearance.readerPlayerSurfaceArgb
                } else {
                    chromeAppearance.backgroundArgb
                }
                updateSystemBars(chromeAppearance, navigationBarColor)
            }

            // Keep a real application surface behind transparent system bars. Individual screens
            // can remain edge-to-edge while their controls continue to receive their own insets.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        ComposeColor(
                            if (isReaderRoute) {
                                chromeAppearance.readerBackgroundArgb
                            } else {
                                chromeAppearance.backgroundArgb
                            },
                        ),
                    ),
            ) {
                NavHost(navController = navController, startDestination = "library") {
                    composable("library") {
                        App(
                            onChromeChanged = onChromeChanged,
                            onNativeSurahRequested = { number -> navController.navigate("surah/$number") },
                            onNativeSettingsRequested = { navController.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        NativeSettingsScreen(
                            onBack = navController::popBackStack,
                            onThemeModeChanged = { mode ->
                                onChromeChanged(chromeAppearanceFor(mode))
                            },
                        )
                    }
                    composable(
                        route = "surah/{number}",
                        arguments = listOf(navArgument("number") { type = NavType.IntType }),
                    ) { entry ->
                        App(
                            onChromeChanged = onChromeChanged,
                            initialSurahNumber = entry.arguments?.getInt("number"),
                            onNativeBack = navController::popBackStack,
                        )
                    }
                }
            }
        }
    }

    private fun updateSystemBars(
        chrome: AppChromeAppearance,
        navigationBarArgb: Long = chrome.backgroundArgb,
    ) {
        val statusBarStyle = if (chrome.isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        val navigationBarColor = navigationBarArgb.toInt()
        val navigationBarStyle = if (chrome.isDark) {
            SystemBarStyle.dark(navigationBarColor)
        } else {
            SystemBarStyle.light(navigationBarColor, navigationBarColor)
        }
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )

        // On Android versions that still render an opaque navigation bar, set
        // the actual window surface too. On Android 15+ the reader player
        // extends through the gesture area with the same token.
        window.navigationBarColor = navigationBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = navigationBarColor
        }

        // Android can otherwise add a contrast scrim over the deliberately chosen
        // navigation surface, producing a grey strip outside the app palette.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun savedChromeAppearance(): AppChromeAppearance {
        val mode = ThemeStorage.getSavedThemeMode() ?: if (isSystemInDarkMode()) {
            ThemeMode.DARK
        } else {
            ThemeMode.LIGHT
        }
        return chromeAppearanceFor(mode)
    }

    private fun chromeAppearanceFor(mode: ThemeMode): AppChromeAppearance = appChromeAppearance(
        mode = mode,
        palette = ThemeStorage.getSavedThemePalette() ?: ThemePalette.FOREST,
    )

    private fun isSystemInDarkMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun onResume() {
        super.onResume()
        AndroidContextHolder.activity = this
        requestNotificationPermissionIfNeeded()
    }

    override fun onPause() {
        if (AndroidContextHolder.activity === this) {
            AndroidContextHolder.activity = null
        }
        super.onPause()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
        )
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 4302
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
