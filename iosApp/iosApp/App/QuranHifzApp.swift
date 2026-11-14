import ComposeApp
import SwiftUI

@main
struct QuranHifzApp: App {
    @UIApplicationDelegateAdaptor(ServicesAppDelegate.self) private var appDelegate
    private let env: AppEnvironment
    @State private var router = Router()
    private let showOnboardingAtLaunch: Bool
    private let whatsNewPending: Bool

    init() {
        KoinInitializerKt.InitKoinIos()
        ServicesBootstrap.register()
        #if DEBUG
        // QA: `-qhSeedDemo YES` passes the onboarding, fills the database (a plan, history, hard ayahs) in `.task` below.
        if UserDefaults.standard.bool(forKey: "qhSeedDemo") { IosDebugSeed.shared.passOnboarding() }
        #endif
        // The gate first (it marks an existing user as having passed), then the What's-new decision that reads it.
        let gate = IosStartup.shared.resolveGate()
        showOnboardingAtLaunch = gate.showsOnboarding
        whatsNewPending = IosStartup.shared.whatsNewPending()
        env = AppEnvironment.shared
        QhHaptics.shared.prepare()
    }

    var body: some Scene {
        WindowGroup {
            QhThemeRoot(settings: env.theme) {
                AppRoot(showOnboardingAtLaunch: showOnboardingAtLaunch, whatsNewPending: whatsNewPending)
            }
            .qhTracksTouch()
            .qhToastHost(env.toaster)
            .environment(env)
            .environment(env.copy)
            .environment(router)
            .onOpenURL { _ = router.open(url: $0) }
            .task {
                // A tapped reminder: the link of a cold start waits in the router of notifications, later ones come live.
                NotificationRouter.shared.onLink = { url in _ = router.open(url: url) }
                if let url = NotificationRouter.shared.consumePendingURL() { _ = router.open(url: url) }
                #if DEBUG
                if UserDefaults.standard.bool(forKey: "qhSeedDemo") {
                    do { try await IosDebugSeed.shared.seed(withKhatm: UserDefaults.standard.bool(forKey: "qhSeedKhatm")) } catch { NSLog("SEEDFAIL \(error)") }
                }
                // QA and screenshots: `-qhOpen quranhifz://plan` as a launch argument opens that place (simulator taps are not scriptable).
                if let link = UserDefaults.standard.string(forKey: "qhOpen"), let url = URL(string: link) {
                    try? await Task.sleep(for: .milliseconds(600))
                    _ = router.open(url: url)
                }
                #endif
            }
        }
    }
}

/// Onboarding or the main tabs, with a cross-fade between them when the onboarding ends.
struct AppRoot: View {
    let whatsNewPending: Bool
    @State private var onboarding: Bool
    @Environment(Router.self) private var router
    @Environment(\.qh) private var qh

    init(showOnboardingAtLaunch: Bool, whatsNewPending: Bool) {
        self.whatsNewPending = whatsNewPending
        _onboarding = State(initialValue: showOnboardingAtLaunch)
    }

    var body: some View {
        ZStack {
            qh.colors.bg.ignoresSafeArea()
            if onboarding {
                OnboardingView { exit in
                    router.finishedOnboarding(exit)
                    withQhAnimation(QhMotion.emphasized(QhMotion.long)) { onboarding = false }
                }
                .transition(.opacity)
            } else {
                MainTabs(whatsNewPending: whatsNewPending)
                    .transition(.opacity)
            }
        }
    }
}
