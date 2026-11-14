import AVFoundation
import UIKit
import UserNotifications

/// The microphone permission of "My reading" (`VoiceService.start` must be called with it granted).
enum MicrophonePermission {
    enum Status { case undetermined, granted, denied }

    static var status: Status {
        switch AVAudioApplication.shared.recordPermission {
        case .granted: return .granted
        case .denied: return .denied
        default: return .undetermined
        }
    }

    /// Asks once; a person who refused is not asked again by the system (send them to `openSettings()`).
    static func request() async -> Bool {
        await AVAudioApplication.requestRecordPermission()
    }

    @MainActor
    static func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }
}

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
