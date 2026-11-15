import AVFoundation
import UIKit
import UserNotifications

/// The notification permission of the reminder (the planner also asks by itself when it was never asked).
enum NotificationPermission {
    enum Status { case undetermined, granted, denied }

    static func status() async -> Status {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined: return .undetermined
        case .denied: return .denied
        default: return .granted
        }
    }

    static func request() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }
}
