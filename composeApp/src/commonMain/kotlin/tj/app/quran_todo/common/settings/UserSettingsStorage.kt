package tj.app.quran_todo.common.settings

expect object UserSettingsStorage {
    fun getDailyGoal(): Int?
    fun saveDailyGoal(goal: Int)
    fun getFocusMinutes(): Int?
    fun saveFocusMinutes(minutes: Int)
    fun isReminderEnabled(): Boolean?
    fun saveReminderEnabled(enabled: Boolean)
    fun getLastPlaybackSurah(): Int?
    fun getLastPlaybackAyahNumber(): Int?
    fun saveLastPlaybackPosition(surahNumber: Int, ayahNumber: Int)
    fun getPlaybackSpeed(): Float?
    fun savePlaybackSpeed(speed: Float)
    fun getPlaybackMode(): String?
    fun savePlaybackMode(mode: String)
    fun getRepeatCount(): Int?
    fun saveRepeatCount(count: Int)
    fun getLoopStartAyahNumber(): Int?
    fun getLoopEndAyahNumber(): Int?
    fun saveLoopRange(startAyahNumber: Int?, endAyahNumber: Int?)
    fun isTranslationModeEnabled(): Boolean?
    fun saveTranslationModeEnabled(enabled: Boolean)
    fun getTranslationDelayMs(): Long?
    fun saveTranslationDelayMs(delayMs: Long)
}
