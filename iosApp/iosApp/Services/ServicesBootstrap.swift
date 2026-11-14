import BackgroundTasks
import ComposeApp
import UIKit
import UserNotifications

/// The one place that connects the Swift services to the shared layer (task IOS-S2).
///
/// Call `ServicesBootstrap.register()` once, in `iOSApp.init()`, right after `KoinInitializerKt.InitKoinIos()`.
/// It registers the Swift hooks (widget reload, PDF, share sheet, review), the notification delegate (deep links and the "In an hour"
/// action), the background refresh task of the reminders, and the foreground observer; then it tells the shared layer that the app started.
///
/// Optional, for downloads that finish while the app is not running: add
/// `@UIApplicationDelegateAdaptor(ServicesAppDelegate.self) var appDelegate` to the `App` struct.
enum ServicesBootstrap {
    /// The identifier in `BGTaskSchedulerPermittedIdentifiers` (Info.plist).
    static let refreshTaskId = "tj.app.quran-todo.reminders.refresh"
    private static var registered = false

    @MainActor
    static func register() {
        guard !registered else { return }
        registered = true

        IosServices.shared.hooks = SwiftPlatformHooks()
        UNUserNotificationCenter.current().delegate = NotificationRouter.shared

        BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTaskId, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else { return }
            handleRefresh(task)
        }

        let center = NotificationCenter.default
        center.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { _ in
            IosServicesRuntime.shared.onForeground()
        }
        center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in
            scheduleRefresh()
        }

        IosServicesRuntime.shared.onLaunch()
    }

    /// Asks the system for a refresh in about six hours (it decides when, and whether).
    static func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handleRefresh(_ task: BGAppRefreshTask) {
        scheduleRefresh()
        var finished = false
        task.expirationHandler = {
            if !finished { task.setTaskCompleted(success: false) }
            finished = true
        }
        IosServicesRuntime.shared.refreshReminders { ok in
            DispatchQueue.main.async {
                if !finished { task.setTaskCompleted(success: ok.boolValue) }
                finished = true
            }
        }
    }
}

/// Only for the transfer of files of background `URLSession` events: forwards them to the shared layer.
final class ServicesAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        IosServicesRuntime.shared.handleBackgroundSessionEvents(completion: completionHandler)
    }
}
