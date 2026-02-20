package tj.app.quran_todo.common.theme

import android.content.Context
import tj.app.quran_todo.common.platform.AndroidContextHolder

private const val PREFS_NAME = "quran_todo_prefs"
private const val KEY_THEME = "theme_mode"
private const val KEY_THEME_PALETTE = "theme_palette"
private const val KEY_READING_FONT_STYLE = "reading_font_style"
private const val KEY_AYAH_CARD_STYLE = "ayah_card_style"

actual object ThemeStorage {
    private fun prefs() =
        AndroidContextHolder.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun getSavedThemeMode(): ThemeMode? {
        val value = prefs().getString(KEY_THEME, null) ?: return null
        return runCatching { ThemeMode.valueOf(value) }.getOrNull()
    }

    actual fun saveThemeMode(mode: ThemeMode) {
        prefs().edit().putString(KEY_THEME, mode.name).apply()
    }

    actual fun getSavedThemePalette(): ThemePalette? {
        val value = prefs().getString(KEY_THEME_PALETTE, null) ?: return null
        return runCatching { ThemePalette.valueOf(value) }.getOrNull()
    }

    actual fun saveThemePalette(palette: ThemePalette) {
        prefs().edit().putString(KEY_THEME_PALETTE, palette.name).apply()
    }

    actual fun getSavedReadingFontStyle(): ReadingFontStyle? {
        val value = prefs().getString(KEY_READING_FONT_STYLE, null) ?: return null
        return runCatching { ReadingFontStyle.fromStorage(value) }.getOrNull()
    }

    actual fun saveReadingFontStyle(style: ReadingFontStyle) {
        prefs().edit().putString(KEY_READING_FONT_STYLE, style.storageValue).apply()
    }

    actual fun getSavedAyahCardStyle(): AyahCardStyle? {
        val value = prefs().getString(KEY_AYAH_CARD_STYLE, null) ?: return null
        return runCatching { AyahCardStyle.fromStorage(value) }.getOrNull()
    }

    actual fun saveAyahCardStyle(style: AyahCardStyle) {
        prefs().edit().putString(KEY_AYAH_CARD_STYLE, style.storageValue).apply()
    }
}
