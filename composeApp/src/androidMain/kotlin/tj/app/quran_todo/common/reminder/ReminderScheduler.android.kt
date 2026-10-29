package tj.app.quran_todo.common.reminder

import tj.app.quran_todo.common.platform.AndroidContextHolder

/**
 * The Android side of the old shared `expect` (a repeating 20:00 alarm with a fixed text, which the root of the app switched off at
 * every start). Nothing on Android calls it any more: the reminder is now `UiPrefs.reminderEnabled` / `reminderMinuteOfDay`, planned by
 * [ReminderSync], and its text is made from the lesson when the alarm fires ([ReminderNotifier]).
 *
 * It stays only because the shared declaration (used by iOS and the old shared screens) needs an Android `actual`. A call
 * does not take the arguments at face value: the new reminder is not driven by a title, a body or a flag passed in, so the call
 * just brings the alarms in line with the stored settings. That is harmless, can be repeated and never plans anything the
 * person did not turn on.
 */
actual object ReminderScheduler {
    actual fun syncDailyReminder(
        enabled: Boolean,
        title: String,
        body: String,
    ) {
        ReminderSync.sync(AndroidContextHolder.context)
    }
}
