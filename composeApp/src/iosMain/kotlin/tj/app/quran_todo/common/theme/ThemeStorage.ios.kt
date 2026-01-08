package tj.app.quran_todo.common.theme

import platform.Foundation.NSUserDefaults

private const val KEY_THEME = "theme_mode"
private const val KEY_THEME_PALETTE = "theme_palette"

actual object ThemeStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getSavedThemeMode(): ThemeMode? {
        val value = defaults.stringForKey(KEY_THEME) ?: return null
        return runCatching { ThemeMode.valueOf(value) }.getOrNull()
    }

    actual fun saveThemeMode(mode: ThemeMode) {
        defaults.setObject(mode.name, forKey = KEY_THEME)
    }

    actual fun getSavedThemePalette(): ThemePalette? {
        val value = defaults.stringForKey(KEY_THEME_PALETTE) ?: return null
        return runCatching { ThemePalette.valueOf(value) }.getOrNull()
    }

    actual fun saveThemePalette(palette: ThemePalette) {
        defaults.setObject(palette.name, forKey = KEY_THEME_PALETTE)
    }
}
