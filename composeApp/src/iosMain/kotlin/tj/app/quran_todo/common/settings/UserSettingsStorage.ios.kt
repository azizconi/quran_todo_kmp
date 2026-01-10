package tj.app.quran_todo.common.settings

import platform.Foundation.NSUserDefaults

private const val KEY_DAILY_GOAL = "daily_goal"
private const val KEY_FOCUS_MINUTES = "focus_minutes"
private const val KEY_REMINDERS = "reminders_enabled"
private const val KEY_LAST_SURAH = "last_surah"
private const val KEY_LAST_AYAH = "last_ayah"
private const val KEY_PLAYBACK_SPEED = "playback_speed"
private const val KEY_PLAYBACK_MODE = "playback_mode"
private const val KEY_REPEAT_COUNT = "repeat_count"
private const val KEY_LOOP_START_AYAH = "loop_start_ayah"
private const val KEY_LOOP_END_AYAH = "loop_end_ayah"
private const val KEY_TRANSLATION_MODE = "translation_mode"
private const val KEY_TRANSLATION_DELAY = "translation_delay"

actual object UserSettingsStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getDailyGoal(): Int? =
        if (defaults.objectForKey(KEY_DAILY_GOAL) != null) defaults.integerForKey(KEY_DAILY_GOAL).toInt() else null

    actual fun saveDailyGoal(goal: Int) {
        defaults.setInteger(goal.toLong(), forKey = KEY_DAILY_GOAL)
    }

    actual fun getFocusMinutes(): Int? =
        if (defaults.objectForKey(KEY_FOCUS_MINUTES) != null) defaults.integerForKey(KEY_FOCUS_MINUTES).toInt() else null

    actual fun saveFocusMinutes(minutes: Int) {
        defaults.setInteger(minutes.toLong(), forKey = KEY_FOCUS_MINUTES)
    }

    actual fun isReminderEnabled(): Boolean? =
        if (defaults.objectForKey(KEY_REMINDERS) != null) defaults.boolForKey(KEY_REMINDERS) else null

    actual fun saveReminderEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = KEY_REMINDERS)
    }

    actual fun getLastPlaybackSurah(): Int? =
        if (defaults.objectForKey(KEY_LAST_SURAH) != null) defaults.integerForKey(KEY_LAST_SURAH).toInt() else null

    actual fun getLastPlaybackAyahNumber(): Int? =
        if (defaults.objectForKey(KEY_LAST_AYAH) != null) defaults.integerForKey(KEY_LAST_AYAH).toInt() else null

    actual fun saveLastPlaybackPosition(surahNumber: Int, ayahNumber: Int) {
        defaults.setInteger(surahNumber.toLong(), forKey = KEY_LAST_SURAH)
        defaults.setInteger(ayahNumber.toLong(), forKey = KEY_LAST_AYAH)
    }

    actual fun getPlaybackSpeed(): Float? =
        if (defaults.objectForKey(KEY_PLAYBACK_SPEED) != null) {
            defaults.doubleForKey(KEY_PLAYBACK_SPEED).toFloat()
        } else {
            null
        }

    actual fun savePlaybackSpeed(speed: Float) {
        defaults.setDouble(speed.toDouble(), forKey = KEY_PLAYBACK_SPEED)
    }

    actual fun getPlaybackMode(): String? =
        if (defaults.objectForKey(KEY_PLAYBACK_MODE) != null) defaults.stringForKey(KEY_PLAYBACK_MODE) else null

    actual fun savePlaybackMode(mode: String) {
        defaults.setObject(mode, forKey = KEY_PLAYBACK_MODE)
    }

    actual fun getRepeatCount(): Int? =
        if (defaults.objectForKey(KEY_REPEAT_COUNT) != null) defaults.integerForKey(KEY_REPEAT_COUNT).toInt() else null

    actual fun saveRepeatCount(count: Int) {
        defaults.setInteger(count.toLong(), forKey = KEY_REPEAT_COUNT)
    }

    actual fun getLoopStartAyahNumber(): Int? =
        if (defaults.objectForKey(KEY_LOOP_START_AYAH) != null) defaults.integerForKey(KEY_LOOP_START_AYAH).toInt() else null

    actual fun getLoopEndAyahNumber(): Int? =
        if (defaults.objectForKey(KEY_LOOP_END_AYAH) != null) defaults.integerForKey(KEY_LOOP_END_AYAH).toInt() else null

    actual fun saveLoopRange(startAyahNumber: Int?, endAyahNumber: Int?) {
        if (startAyahNumber == null) {
            defaults.removeObjectForKey(KEY_LOOP_START_AYAH)
        } else {
            defaults.setInteger(startAyahNumber.toLong(), forKey = KEY_LOOP_START_AYAH)
        }
        if (endAyahNumber == null) {
            defaults.removeObjectForKey(KEY_LOOP_END_AYAH)
        } else {
            defaults.setInteger(endAyahNumber.toLong(), forKey = KEY_LOOP_END_AYAH)
        }
    }

    actual fun isTranslationModeEnabled(): Boolean? =
        if (defaults.objectForKey(KEY_TRANSLATION_MODE) != null) defaults.boolForKey(KEY_TRANSLATION_MODE) else null

    actual fun saveTranslationModeEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = KEY_TRANSLATION_MODE)
    }

    actual fun getTranslationDelayMs(): Long? =
        if (defaults.objectForKey(KEY_TRANSLATION_DELAY) != null) defaults.doubleForKey(KEY_TRANSLATION_DELAY).toLong() else null

    actual fun saveTranslationDelayMs(delayMs: Long) {
        defaults.setDouble(delayMs.toDouble(), forKey = KEY_TRANSLATION_DELAY)
    }
}
