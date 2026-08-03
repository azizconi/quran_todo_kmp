package tj.app.quran_todo.common.settings

/**
 * Preferences that affect the focused Quran reader only. They are deliberately
 * separate from the legacy audio-translation pause preferences.
 */
data class SurahReaderPreferences(
    val showTranslation: Boolean = false,
    val autoplayOnOpen: Boolean = false,
    val autoAdvanceAyahs: Boolean = true,
    val playbackSpeed: Float = 1f,
    /** -1 represents an endless repetition of the current ayah. */
    val repeatCount: Int = 1,
    val highlightPlayingAyah: Boolean = true,
) {
    fun save() {
        UserSettingsStorage.saveShowAyahTranslation(showTranslation)
        UserSettingsStorage.saveAutoplayOnSurahOpen(autoplayOnOpen)
        UserSettingsStorage.saveAutoAdvanceAyahs(autoAdvanceAyahs)
        UserSettingsStorage.savePlaybackSpeed(playbackSpeed)
        UserSettingsStorage.saveRepeatCount(repeatCount)
        UserSettingsStorage.saveHighlightPlayingAyah(highlightPlayingAyah)
    }

    companion object {
        const val RepeatForever = -1

        fun load(): SurahReaderPreferences = SurahReaderPreferences(
            showTranslation = UserSettingsStorage.isShowAyahTranslationEnabled() ?: false,
            autoplayOnOpen = UserSettingsStorage.isAutoplayOnSurahOpenEnabled() ?: false,
            autoAdvanceAyahs = UserSettingsStorage.isAutoAdvanceAyahsEnabled() ?: true,
            playbackSpeed = UserSettingsStorage.getPlaybackSpeed()
                ?.takeIf { it in setOf(0.75f, 1f, 1.25f, 1.5f, 2f) }
                ?: 1f,
            repeatCount = UserSettingsStorage.getRepeatCount()
                ?.takeIf { it == 1 || it == 3 || it == 5 || it == RepeatForever }
                ?: 1,
            highlightPlayingAyah = UserSettingsStorage.isHighlightPlayingAyahEnabled() ?: true,
        )
    }
}
