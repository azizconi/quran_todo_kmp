package tj.app.quran_todo.common.reminder

import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

actual object ReminderScheduler {
    private const val REQUEST_ID = "daily_hifz_reminder"

    actual fun syncDailyReminder(
        enabled: Boolean,
        title: String,
        body: String,
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REQUEST_ID))
        if (!enabled) return

        center.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            completionHandler = { granted, _ ->
                if (!granted) return@requestAuthorizationWithOptions

                val content = UNMutableNotificationContent().apply {
                    setTitle(title)
                    setBody(body)
                }

                val components = NSDateComponents().apply {
                    hour = 20
                    minute = 0
                }

                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                    dateComponents = components,
                    repeats = true
                )

                val request = UNNotificationRequest.requestWithIdentifier(
                    identifier = REQUEST_ID,
                    content = content,
                    trigger = trigger
                )
                center.addNotificationRequest(request, withCompletionHandler = null)
            }
        )
    }
}
