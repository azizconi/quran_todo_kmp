import ComposeApp
import UIKit
import UserNotifications

/// The delegate of the notification centre: a banner while the app is open, the deep link when a reminder is tapped, and the
/// "In an hour" action (one more notification, 60 minutes from now, with the same text).
///
/// A tap opens `quranhifz://lesson` (or `quranhifz://reader?surah=&ayah=`) through the system, which reaches the app's `onOpenURL`.
/// `pendingURL` keeps the last link until the shell has consumed it (a cold start from a tap happens before the views exist).
final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationRouter()

    /// A link from a tap that no one has handled yet; the shell reads and clears it.
    private(set) var pendingURL: URL?

    /// Set by the shell: called on the main thread with the link of a tapped notification.
    var onLink: ((URL) -> Void)?

    func consumePendingURL() -> URL? {
        defer { pendingURL = nil }
        return pendingURL
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let content = response.notification.request.content
        if response.actionIdentifier == IosReminderPlanner.companion.ACTION_SNOOZE {
            snooze(content, center: center)
            return
        }
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
              let text = content.userInfo["url"] as? String,
              let url = URL(string: text) else { return }
        await MainActor.run {
            if let onLink {
                onLink(url)
            } else {
                // Cold start: the shell has not attached yet; it consumes the link once its task runs. Not also through the system
                // (`UIApplication.open` -> `onOpenURL`), or the place would open twice.
                pendingURL = url
            }
        }
    }

    private func snooze(_ content: UNNotificationContent, center: UNUserNotificationCenter) {
        let copy = (content.mutableCopy() as? UNMutableNotificationContent) ?? UNMutableNotificationContent()
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3600, repeats: false)
        center.add(UNNotificationRequest(identifier: "\(IosReminderPlanner.companion.ID_PREFIX).snooze", content: copy, trigger: trigger))
    }
}
