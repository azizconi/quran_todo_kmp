package tj.app.quran_todo.common.theme

import platform.Foundation.NSUserDefaults

private const val KEY_THEME = "theme_mode"

actual object ThemeStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getSavedThemeMode(): ThemeMode? {
        val value = defaults.stringForKey(KEY_THEME) ?: return null
        return runCatching { ThemeMode.valueOf(value) }.getOrNull()
    }

    actual fun saveThemeMode(mode: ThemeMode) {
        defaults.setObject(mode.name, forKey = KEY_THEME)
    }
}
