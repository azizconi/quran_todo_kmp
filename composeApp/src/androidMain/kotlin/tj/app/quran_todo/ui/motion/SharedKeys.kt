package tj.app.quran_todo.ui.motion

/**
 * The only source of shared element keys. `origin` comes from [tj.app.quran_todo.ui.navigation.Origin]
 * so the same surah shown in two places of one screen never produces duplicate keys.
 */
object SharedKeys {
    fun surahContainer(origin: String, surah: Int) = "surah-container-$origin-$surah"
    fun surahTitle(origin: String, surah: Int) = "surah-title-$origin-$surah"
    fun surahArabic(origin: String, surah: Int) = "surah-arabic-$origin-$surah"
    fun surahBadge(origin: String, surah: Int) = "surah-badge-$origin-$surah"

    const val PlayerDock = "player-dock"
    const val PlayButton = "player-play"
    const val ProgressRing = "progress-ring"
    const val SettingsIcon = "settings-icon"
}
