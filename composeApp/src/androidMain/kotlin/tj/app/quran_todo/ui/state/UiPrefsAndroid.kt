package tj.app.quran_todo.ui.state

import android.content.Context

private fun uiPrefsFile(context: Context) = context.getSharedPreferences(UiPrefs.PREFS_NAME, Context.MODE_PRIVATE)

/**
 * The stored reminder settings for code that runs without the Compose app (the boot and alarm receivers). Always the
 * latest persisted values, whichever [UiPrefs] instance wrote them.
 */
internal fun UiPrefs.Companion.readReminder(context: Context): ReminderPrefs {
    val prefs = uiPrefsFile(context)
    return ReminderPrefs(
        enabled = prefs.getBoolean(UiPrefs.KEY_REMINDER_ENABLED, false),
        minuteOfDay = prefs.getInt(UiPrefs.KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE_OF_DAY).coerceIn(REMINDER_MINUTE_RANGE),
    )
}

/** The onboarding v2 flags as stored, for code that runs before the Compose app (splash, `MainActivity`). */
internal fun UiPrefs.Companion.readOnboarding(context: Context): OnboardingFlags {
    val prefs = uiPrefsFile(context)
    return OnboardingFlags(
        completed = prefs.getBoolean(UiPrefs.KEY_ONBOARDING_COMPLETED, false),
        v2Started = prefs.getBoolean(UiPrefs.KEY_ONBOARDING_V2_STARTED, false),
        whatsNewShown = prefs.getBoolean(UiPrefs.KEY_WHATS_NEW_SHOWN, false),
    )
}

/** Marks the onboarding as passed without [UiPrefs]: an existing user is recognised at the first start of this version. */
internal fun UiPrefs.Companion.writeOnboardingCompleted(context: Context) {
    uiPrefsFile(context).edit().putBoolean(UiPrefs.KEY_ONBOARDING_COMPLETED, true).apply()
}

/** The stored analytics choice for code that runs before Koin starts (telemetry in Application.onCreate). */
internal fun UiPrefs.Companion.readAnalyticsEnabled(context: Context): Boolean =
    uiPrefsFile(context).getBoolean(UiPrefs.KEY_ANALYTICS_ENABLED, true)
