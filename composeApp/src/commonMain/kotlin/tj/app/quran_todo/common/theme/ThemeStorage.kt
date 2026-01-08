package tj.app.quran_todo.common.theme

expect object ThemeStorage {
    fun getSavedThemeMode(): ThemeMode?
    fun saveThemeMode(mode: ThemeMode)
    fun getSavedThemePalette(): ThemePalette?
    fun saveThemePalette(palette: ThemePalette)
}
