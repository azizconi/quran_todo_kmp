import ComposeApp
import SwiftUI

/// The three tabs Lesson / Quran / Progress in a native `TabView`, each with its own `NavigationStack(path:)` driven by the `Router`.
/// Pushed screens come from `Route.screen()` (App/Router.swift). One `HomeModel` serves both home tabs and the mini player slot.
struct MainTabs: View {
    let whatsNewPending: Bool
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @Environment(\.scenePhase) private var scenePhase
    @State private var home = HomeModel()

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.selectedTab) {
            stack(.lesson) { LessonTabView(home: home) }
                .tabItem { Label(copy.tabs.tabLesson, systemImage: "graduationcap") }
                .tag(AppTab.lesson)
            stack(.quran) { QuranTabView(home: home) }
                .tabItem { Label(copy.home.title, systemImage: "book") }
                .tag(AppTab.quran)
            stack(.progress) { ProgressTabRoot().miniPlayerInset(home) }
                .tabItem { Label(copy.progress.title, systemImage: "chart.bar") }
                .tag(AppTab.progress)
        }
        .tint(qh.colors.primary)
        .toolbarBackground(qh.colors.surface, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .background(qh.colors.bg)
        .whatsNewHost(pending: whatsNewPending, atHome: router.isAtHome)
        .similarSheetHost()
        .onAppear {
            #if DEBUG
            if let url = UserDefaults.standard.string(forKey: "s5route").flatMap(URL.init(string:)) {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { _ = router.open(url: url) }
            }
            #endif
            home.bindEvents(toaster: env.toaster, copy: copy)
            log(router.selectedTab.screenName)
        }
        .onChange(of: router.selectedTab) { _, tab in
            QhHaptics.shared.perform(.tick)
            log(router.topRoute?.screenName ?? tab.screenName)
        }
        .onChange(of: router.topRoute) { _, route in if let route { log(route.screenName) } }
        .onChange(of: copy.language) { _, language in home.setLanguage(language) }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                IosUi.shared.ticker.refresh()
                home.onResume()
            }
        }
    }

    private func stack<Root: View>(_ tab: AppTab, @ViewBuilder root: () -> Root) -> some View {
        NavigationStack(path: router.binding(for: tab)) {
            root()
                .navigationDestination(for: Route.self) { route in
                    route.screen()
                }
        }
    }

    private func log(_ name: String) {
        AppTelemetry.shared.logScreen(screenName: name, params: [:])
    }
}
