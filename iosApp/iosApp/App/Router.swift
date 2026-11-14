import ComposeApp
import Observation
import SwiftUI

// MARK: - Tabs and routes

/// The three tabs of the app (`MainTab` of Kotlin, same order).
enum AppTab: Int, CaseIterable, Hashable {
    case quran, lesson, progress

    var kotlin: MainTab { MainTab.allCases[rawValue] }
    var screenName: String { kotlin.screenName }
}

/// Every destination that can sit on a tab's navigation stack (`Dest` of Kotlin, without Onboarding / Home / Progress / Catalog).
/// A `Hashable` Swift value so `NavigationStack(path:)` can hold it; `dest` gives the Kotlin `Dest` for the view-model factories.
enum Route: Hashable {
    case reader(surah: Int, ayah: Int?, origin: String, hifz: Bool, rangeStart: Int?, rangeEnd: Int?)
    case mushaf(page: Int, ayahKey: String?)
    case lesson
    case plan
    case quiz
    case tellApart(surah: Int, ayah: Int)
    case khatm(pickTarget: Bool)
    case progressDetails
    case notes
    case settings
    case downloads
    case hardDrill
    case privacy
    case sources
    case tafsir(surah: Int, ayah: Int, tafsirId: String?)

    /// The text reader on [surah] with the usual optional arguments. Use `Router.openReader` to honour the mode "Pages".
    static func reader(_ surah: Int, ayah: Int? = nil, origin: String = "list", hifz: Bool = false,
                       rangeStart: Int? = nil, rangeEnd: Int? = nil) -> Route {
        .reader(surah: surah, ayah: ayah, origin: origin, hifz: hifz, rangeStart: rangeStart, rangeEnd: rangeEnd)
    }

    /// Name for analytics (`AppTelemetry.logScreen`).
    var screenName: String {
        switch self {
        case .reader: return "reader"
        case .mushaf: return "mushaf"
        case .lesson: return "lesson"
        case .plan: return "plan"
        case .quiz: return "quiz"
        case .tellApart: return "tell_apart"
        case .khatm: return "khatm"
        case .progressDetails: return "progress_details"
        case .notes: return "notes"
        case .settings: return "settings"
        case .downloads: return "downloads"
        case .hardDrill: return "hard_drill"
        case .privacy: return "privacy"
        case .sources: return "sources"
        case .tafsir: return "tafsir"
        }
    }

    /// The Kotlin destination, for `IosUi.shared.reader(route:)`, `mushaf(route:)`, `tafsir(route:)`.
    var dest: Dest {
        switch self {
        case let .reader(surah, ayah, origin, hifz, start, end):
            return DestReader(surah: Int32(surah), ayah: ayah.map { KotlinInt(int: Int32($0)) }, origin: origin, hifz: hifz,
                               rangeStart: start.map { KotlinInt(int: Int32($0)) }, rangeEnd: end.map { KotlinInt(int: Int32($0)) })
        case let .mushaf(page, key): return DestMushaf(page: Int32(page), ayahKey: key)
        case .lesson: return DestLesson.shared
        case .plan: return DestPlan.shared
        case .quiz: return DestQuiz.shared
        case let .tellApart(surah, ayah): return DestTellApart(surah: Int32(surah), ayah: Int32(ayah))
        case let .khatm(pick): return DestKhatm(pickTarget: pick)
        case .progressDetails: return DestProgressDetails.shared
        case .notes: return DestNotes.shared
        case .settings: return DestSettings.shared
        case .downloads: return DestDownloads.shared
        case .hardDrill: return DestHardDrill.shared
        case .privacy: return DestPrivacy.shared
        case .sources: return DestSources.shared
        case let .tafsir(surah, ayah, id): return DestTafsir(surah: Int32(surah), ayah: Int32(ayah), tafsirId: id)
        }
    }

    /// The route of a Kotlin destination (what `ReaderRouting.route` returns); nil for Home / Onboarding / Progress / Catalog.
    init?(dest: Dest) {
        switch dest {
        case let d as DestReader:
            self = .reader(surah: Int(d.surah), ayah: d.ayah.map { Int(truncating: $0) }, origin: d.origin, hifz: d.hifz,
                           rangeStart: d.rangeStart.map { Int(truncating: $0) }, rangeEnd: d.rangeEnd.map { Int(truncating: $0) })
        case let d as DestMushaf: self = .mushaf(page: Int(d.page), ayahKey: d.ayahKey)
        case is DestLesson: self = .lesson
        case is DestPlan: self = .plan
        case is DestQuiz: self = .quiz
        case let d as DestTellApart: self = .tellApart(surah: Int(d.surah), ayah: Int(d.ayah))
        case let d as DestKhatm: self = .khatm(pickTarget: d.pickTarget)
        case is DestProgressDetails: self = .progressDetails
        case is DestNotes: self = .notes
        case is DestSettings: self = .settings
        case is DestDownloads: self = .downloads
        case is DestHardDrill: self = .hardDrill
        case is DestPrivacy: self = .privacy
        case is DestSources: self = .sources
        case let d as DestTafsir: self = .tafsir(surah: Int(d.surah), ayah: Int(d.ayah), tafsirId: d.tafsirId)
        default: return nil
        }
    }
}

// MARK: - The one place that maps a route to a screen

/// THE routing table. To plug a screen in, replace its `ComingSoon` line with the real view; nothing else needs to change.
/// Screens that need a view model create it themselves through `VMHolder` (see docs/redesign/ios/BRIDGE.md).
extension Route {
    @ViewBuilder
    func screen() -> some View {
        switch self {
        case .lesson: LessonScreen()
        case .reader: ReaderScreen(route: self)
        case .mushaf: MushafScreen(route: self)
        case .tafsir: TafsirScreen(route: self)
        case .plan: PlanScreen()
        case .quiz: QuizScreen(mode: .mixed)
        case let .tellApart(surah, ayah): QuizScreen(mode: .tellApart(surah: surah, ayah: ayah))
        case let .khatm(pick): KhatmScreen(pickTarget: pick)
        case .progressDetails: AnalyticsScreen()
        case .notes: NotesScreen()
        case .hardDrill: DrillScreen()
        case .settings: SettingsScreen()
        case .downloads: DownloadsScreen()
        case .privacy: PrivacyScreen()
        case .sources: SourcesScreen()
        }
    }
}

// MARK: - Router

/// Navigation state of the app: the selected tab, one `[Route]` stack per tab, deep links and the replace-in-stack rules.
/// Inject with `.environment(router)`; read with `@Environment(Router.self)`.
@MainActor
@Observable
final class Router {
    var selectedTab: AppTab = .quran
    var lessonPath: [Route] = []
    var quranPath: [Route] = []
    var progressPath: [Route] = []
    /// A link (reminder, widget) was opened in this launch: the "What's new" sheet waits for the next launch.
    var deepLinkedThisLaunch = false

    var topRoute: Route? { path(of: selectedTab).last }
    var isAtHome: Bool { path(of: selectedTab).isEmpty }

    /// Swallows a second push in the same instant (`navigateOnce`: a double tap).
    @ObservationIgnored private var lastPush = Date.distantPast

    func path(of tab: AppTab) -> [Route] {
        switch tab {
        case .lesson: return lessonPath
        case .quran: return quranPath
        case .progress: return progressPath
        }
    }

    func setPath(_ path: [Route], of tab: AppTab) {
        switch tab {
        case .lesson: lessonPath = path
        case .quran: quranPath = path
        case .progress: progressPath = path
        }
    }

    func binding(for tab: AppTab) -> Binding<[Route]> {
        Binding(get: { self.path(of: tab) }, set: { self.setPath($0, of: tab) })
    }

    // -- Pushing ------------------------------------------------------------------------------------------------------

    /// Pushes [route] on the selected tab (or [tab]). A second push within 350 ms is ignored (double tap).
    func push(_ route: Route, in tab: AppTab? = nil) {
        let now = Date()
        guard now.timeIntervalSince(lastPush) > 0.35 else { return }
        lastPush = now
        let target = tab ?? selectedTab
        selectedTab = target
        setPath(path(of: target) + [route], of: target)
    }

    /// Opens the reader the way every entrance of the app does: `ReaderRouting.route` sends it to the pages when the mode is "Pages".
    func openReader(_ surah: Int, ayah: Int? = nil, origin: String, in tab: AppTab? = nil) {
        let request = DestReader(surah: Int32(surah), ayah: ayah.map { KotlinInt(int: Int32($0)) }, origin: origin, hifz: false,
                                  rangeStart: nil, rangeEnd: nil)
        let routed = ReaderRouting.shared.route(dest: request, layout: IosUi.shared.uiPrefs.readerLayout.value,
                                                lastRead: IosUi.shared.uiPrefs.lastReadPosition.value)
        push(Route(dest: routed) ?? .reader(surah, ayah: ayah, origin: origin), in: tab)
    }

    /// Replaces the top of the stack (Reader / Mushaf do this for "Go to", Text <-> Pages, "Open as text").
    func replaceTop(with route: Route) {
        var path = path(of: selectedTab)
        if path.isEmpty { path.append(route) } else { path[path.count - 1] = route }
        setPath(path, of: selectedTab)
    }

    func pop() {
        var path = path(of: selectedTab)
        guard !path.isEmpty else { return }
        path.removeLast()
        setPath(path, of: selectedTab)
    }

    /// Lesson "To home": drops everything above Home and selects the Quran tab (home).
    func goHome() {
        lessonPath = []
        quranPath = []
        selectedTab = .quran
    }

    func popToRoot(of tab: AppTab) { setPath([], of: tab) }

    // -- After onboarding, deep links ---------------------------------------------------------------------------------

    /// Where the onboarding leaves to (`Finished(exit)`): the first lesson over Home, Home (the Quran tab), or Al-Fatihah in the reader.
    func finishedOnboarding(_ exit: OnboardingExit) {
        lessonPath = []; quranPath = []; progressPath = []
        switch exit {
        // The stacks are created right after this; a path set before they exist is dropped, so the push waits for the tabs.
        case .lesson:
            selectedTab = .quran
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { self.quranPath = [.lesson] }
        case .reader:
            selectedTab = .quran
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { self.openReader(1, origin: "onboarding", in: .quran) }
        default:
            selectedTab = .quran
        }
    }

    /// Settings and its children: pushed after the tab stacks exist (a path set during launch is dropped).
    private func openStack(_ routes: [Route]) {
        selectedTab = .lesson
        lessonPath = routes
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [self] in
            if lessonPath != routes { lessonPath = routes }
        }
    }

    /// `quranhifz://lesson`, `quranhifz://reader/2/255`, `quranhifz://quran`, `quranhifz://progress`, `quranhifz://plan`
    /// (notifications, the widget). Returns true when it was one of ours. A deep link drops everything above Home first.
    @discardableResult
    func open(url: URL) -> Bool {
        guard url.scheme == "quranhifz" else { return false }
        deepLinkedThisLaunch = true
        let parts = [url.host].compactMap { $0 } + url.pathComponents.filter { $0 != "/" }
        switch parts.first {
        case "lesson":
            // The lesson opens over home (the Quran tab), so back lands on home.
            lessonPath = []
            selectedTab = .quran
            quranPath = [.lesson]
        case "reader":
            // Both quranhifz://reader/2/255 and the reminders' quranhifz://reader?surah=2&ayah=255.
            let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
            let fromQuery = ["surah", "ayah"].compactMap { key in query.first { $0.name == key }?.value.flatMap { Int($0) } }
            let numbers = fromQuery.isEmpty ? parts.dropFirst().compactMap { Int($0) } : fromQuery
            guard let surah = numbers.first else { return false }
            quranPath = []
            openReader(surah, ayah: numbers.dropFirst().first, origin: "reminder", in: .quran)
        case "quran": quranPath = []; selectedTab = .quran
        case "progress": progressPath = []; selectedTab = .progress
        case "plan": selectedTab = .lesson; lessonPath = [.plan]
        case "settings": openStack([.settings])
        case "downloads": openStack([.settings, .downloads])
        case "about": openStack([.settings, .privacy])
        case "sources": openStack([.settings, .sources])
        case "khatm": selectedTab = .lesson; lessonPath = [.khatm(pickTarget: false)]
        case "drill": selectedTab = .progress; progressPath = [.hardDrill]
        case "quiz": selectedTab = .lesson; lessonPath = [.quiz]
        case "analytics": selectedTab = .progress; progressPath = [.progressDetails]
        #if DEBUG
        case "tafsir":
            let n = parts.dropFirst().compactMap { Int($0) }
            guard n.count >= 2 else { return false }
            selectedTab = .quran; quranPath = [.tafsir(surah: n[0], ayah: n[1], tafsirId: nil)]
        #endif
        default: return false
        }
        return true
    }
}
