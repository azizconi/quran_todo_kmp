import ComposeApp
import Observation
import SwiftUI

/// The shared Kotlin copy (every string of the UI layer) in the current language, as an `@Observable`.
///
/// ```swift
/// @Environment(Copy.self) private var copy
/// Text(copy.home.lessonTitle)          // HomeCopy
/// Text(copy.tabs.tabLesson)            // TabsCopy
/// ```
/// Reading `copy.home` reads `language` first, so a view that uses it is redrawn when the language changes. Each copy object is built
/// once per language and cached. Add an accessor here when a screen needs another `*Copy` (the list is `IosCopy`).
@MainActor
@Observable
final class Copy {
    private(set) var language: AppLanguage

    @ObservationIgnored private var cache: [String: Any] = [:]

    init(language: AppLanguage) { self.language = language }

    func set(language: AppLanguage) {
        guard language != self.language else { return }
        self.language = language
    }

    private func cached<T>(_ key: String, _ make: () -> T) -> T {
        let full = "\(language.name)/\(key)"
        if let hit = cache[full] as? T { return hit }
        let value = make()
        cache[full] = value
        return value
    }

    var home: HomeCopy { cached("home") { IosCopy.shared.homeCopy() } }
    var tabs: TabsCopy { cached("tabs") { IosCopy.shared.tabsCopy() } }
    var plan: PlanCopy { cached("plan") { IosCopy.shared.planCopy() } }
    var progress: ProgressCopy { cached("progress") { IosCopy.shared.progressCopy() } }
    var onboarding: OnboardingCopy { cached("onboarding") { IosCopy.shared.onboardingCopy() } }
    var whatsNew: WhatsNewCopy { cached("whatsNew") { IosCopy.shared.whatsNewCopy() } }
    var welcomeBack: WelcomeBackCopy { cached("welcomeBack") { IosCopy.shared.welcomeBackCopy() } }
    var mushaf: MushafCopy { cached("mushaf") { IosCopy.shared.mushafCopy() } }
    var quiz: QuizCopy { cached("quiz") { IosCopy.shared.quizCopy() } }
    var pause: PauseCopy { cached("pause") { IosCopy.shared.pauseCopy() } }
    var trust: TrustCopy { cached("trust") { IosCopy.shared.trustCopy() } }
    var lessonCard: LessonCardCopy { cached("lessonCard") { IosCopy.shared.lessonCardCopy() } }
    var listen: ListenCopy { cached("listen") { IosCopy.shared.listenCopy() } }
    var a11y: A11yCopy { cached("a11y") { IosCopy.shared.a11yCopy() } }
    var khatm: KhatmCopy { cached("khatm") { IosCopy.shared.khatmCopy() } }
}
