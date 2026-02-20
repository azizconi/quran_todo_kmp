package tj.app.quran_todo.common.theme

import platform.Foundation.NSUserDefaults

private const val KEY_THEME = "theme_mode"
private const val KEY_THEME_PALETTE = "theme_palette"
private const val KEY_READING_FONT_STYLE = "reading_font_style"
private const val KEY_AYAH_CARD_STYLE = "ayah_card_style"

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

    actual fun getSavedReadingFontStyle(): ReadingFontStyle? {
        val value = defaults.stringForKey(KEY_READING_FONT_STYLE) ?: return null
        return runCatching { ReadingFontStyle.fromStorage(value) }.getOrNull()
    }

    actual fun saveReadingFontStyle(style: ReadingFontStyle) {
        defaults.setObject(style.storageValue, forKey = KEY_READING_FONT_STYLE)
    }

    actual fun getSavedAyahCardStyle(): AyahCardStyle? {
        val value = defaults.stringForKey(KEY_AYAH_CARD_STYLE) ?: return null
        return runCatching { AyahCardStyle.fromStorage(value) }.getOrNull()
    }

    actual fun saveAyahCardStyle(style: AyahCardStyle) {
        defaults.setObject(style.storageValue, forKey = KEY_AYAH_CARD_STYLE)
    }
}
