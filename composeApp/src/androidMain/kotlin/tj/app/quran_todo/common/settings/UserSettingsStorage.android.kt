package tj.app.quran_todo.common.settings

import android.content.Context
import tj.app.quran_todo.common.platform.AndroidContextHolder

private const val PREFS_NAME = "quran_todo_prefs"
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
    private fun prefs() =
        AndroidContextHolder.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun getDailyGoal(): Int? =
        if (prefs().contains(KEY_DAILY_GOAL)) prefs().getInt(KEY_DAILY_GOAL, 0) else null

    actual fun saveDailyGoal(goal: Int) {
        prefs().edit().putInt(KEY_DAILY_GOAL, goal).apply()
    }

    actual fun getFocusMinutes(): Int? =
        if (prefs().contains(KEY_FOCUS_MINUTES)) prefs().getInt(KEY_FOCUS_MINUTES, 0) else null

    actual fun saveFocusMinutes(minutes: Int) {
        prefs().edit().putInt(KEY_FOCUS_MINUTES, minutes).apply()
    }

    actual fun isReminderEnabled(): Boolean? =
        if (prefs().contains(KEY_REMINDERS)) prefs().getBoolean(KEY_REMINDERS, true) else null

    actual fun saveReminderEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_REMINDERS, enabled).apply()
    }

    actual fun getLastPlaybackSurah(): Int? =
        if (prefs().contains(KEY_LAST_SURAH)) prefs().getInt(KEY_LAST_SURAH, 0) else null

    actual fun getLastPlaybackAyahNumber(): Int? =
        if (prefs().contains(KEY_LAST_AYAH)) prefs().getInt(KEY_LAST_AYAH, 0) else null

    actual fun saveLastPlaybackPosition(surahNumber: Int, ayahNumber: Int) {
        prefs().edit()
            .putInt(KEY_LAST_SURAH, surahNumber)
            .putInt(KEY_LAST_AYAH, ayahNumber)
            .apply()
    }

    actual fun getPlaybackSpeed(): Float? =
        if (prefs().contains(KEY_PLAYBACK_SPEED)) prefs().getFloat(KEY_PLAYBACK_SPEED, 1f) else null

    actual fun savePlaybackSpeed(speed: Float) {
        prefs().edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
    }

    actual fun getPlaybackMode(): String? =
        if (prefs().contains(KEY_PLAYBACK_MODE)) prefs().getString(KEY_PLAYBACK_MODE, null) else null

    actual fun savePlaybackMode(mode: String) {
        prefs().edit().putString(KEY_PLAYBACK_MODE, mode).apply()
    }

    actual fun getRepeatCount(): Int? =
        if (prefs().contains(KEY_REPEAT_COUNT)) prefs().getInt(KEY_REPEAT_COUNT, 1) else null

    actual fun saveRepeatCount(count: Int) {
        prefs().edit().putInt(KEY_REPEAT_COUNT, count).apply()
    }

    actual fun getLoopStartAyahNumber(): Int? =
        if (prefs().contains(KEY_LOOP_START_AYAH)) prefs().getInt(KEY_LOOP_START_AYAH, 0) else null

    actual fun getLoopEndAyahNumber(): Int? =
        if (prefs().contains(KEY_LOOP_END_AYAH)) prefs().getInt(KEY_LOOP_END_AYAH, 0) else null

    actual fun saveLoopRange(startAyahNumber: Int?, endAyahNumber: Int?) {
        val editor = prefs().edit()
        if (startAyahNumber == null) {
            editor.remove(KEY_LOOP_START_AYAH)
        } else {
            editor.putInt(KEY_LOOP_START_AYAH, startAyahNumber)
        }
        if (endAyahNumber == null) {
            editor.remove(KEY_LOOP_END_AYAH)
        } else {
            editor.putInt(KEY_LOOP_END_AYAH, endAyahNumber)
        }
        editor.apply()
    }

    actual fun isTranslationModeEnabled(): Boolean? =
        if (prefs().contains(KEY_TRANSLATION_MODE)) prefs().getBoolean(KEY_TRANSLATION_MODE, false) else null

    actual fun saveTranslationModeEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_TRANSLATION_MODE, enabled).apply()
    }

    actual fun getTranslationDelayMs(): Long? =
        if (prefs().contains(KEY_TRANSLATION_DELAY)) prefs().getLong(KEY_TRANSLATION_DELAY, 3000L) else null

    actual fun saveTranslationDelayMs(delayMs: Long) {
        prefs().edit().putLong(KEY_TRANSLATION_DELAY, delayMs).apply()
    }
}
