package tj.app.quran_todo

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import tj.app.quran_todo.common.theme.AppChromeAppearance
import tj.app.quran_todo.common.theme.ThemeMode

/**
 * Hosts only the shared library shell. SwiftUI owns iOS navigation and presents
 * the reader itself, which keeps the native interactive back gesture intact.
 */
fun MainViewController(
    onNativeSurahRequested: (Int) -> Unit,
    onNativeSettingsRequested: () -> Unit,
    onChromeChanged: (AppChromeAppearance) -> Unit = {},
): UIViewController {
    lateinit var controller: UIViewController
    fun applyTheme(mode: ThemeMode) {
        controller.overrideUserInterfaceStyle = when (mode) {
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
    }

    controller = ComposeUIViewController {
        App(
            onThemeModeChanged = ::applyTheme,
            onChromeChanged = { appearance ->
                applyTheme(if (appearance.isDark) ThemeMode.DARK else ThemeMode.LIGHT)
                onChromeChanged(appearance)
            },
            onNativeSurahRequested = onNativeSurahRequested,
            onNativeSettingsRequested = onNativeSettingsRequested,
        )
    }
    return controller
}
