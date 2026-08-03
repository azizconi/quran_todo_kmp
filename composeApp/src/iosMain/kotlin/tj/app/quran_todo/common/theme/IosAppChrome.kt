package tj.app.quran_todo.common.theme

/**
 * Supplies SwiftUI with the saved appearance before the first Compose frame.
 * This prevents the host from briefly drawing a system white/black background
 * around the library while Compose restores its own theme.
 */
object IosAppChrome {
    fun initialAppearance(isSystemDark: Boolean): AppChromeAppearance {
        val mode = ThemeStorage.getSavedThemeMode() ?: if (isSystemDark) {
            ThemeMode.DARK
        } else {
            ThemeMode.LIGHT
        }
        val palette = ThemeStorage.getSavedThemePalette() ?: ThemePalette.FOREST
        return appChromeAppearance(mode, palette)
    }
}
