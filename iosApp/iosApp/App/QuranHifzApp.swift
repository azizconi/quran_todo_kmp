import ComposeApp
import SwiftUI

@main
struct QuranHifzApp: App {
    private let env: AppEnvironment
    @State private var router = Router()
    private let showOnboardingAtLaunch: Bool
    private let whatsNewPending: Bool

    init() {
        KoinInitializerKt.InitKoinIos()
        // ServicesBootstrap.register()   // IOS-S2: iOS platform services (playback, reminders, downloads...) hook in here.
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
            .onOpenURL { router.open(url: $0) }
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
