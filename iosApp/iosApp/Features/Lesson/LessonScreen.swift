import ComposeApp
import SwiftUI
import UIKit

/// Today's lesson (`LessonScreen.kt`): the header with the three steps, then the card the person works on (the new passage in three
/// phases, then the repetitions one by one) and the summary. Pushed from the Lesson tab (`Route.lesson`).
///
/// - Back leaves the lesson; with ayahs opened in "Recall" it asks first ("What you did is saved") and the swipe-back gesture is off.
/// - Every card is written the moment it is graded, so leaving loses nothing; the position inside the card is kept as text in
///   `@SceneStorage` (the process may die) and restored into the view model.
/// - The screen stays awake while a card is being worked on, and the lesson clock runs only while the screen is in the foreground.
struct LessonScreen: View {
    @State private var model = LessonModel()
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @Environment(\.scenePhase) private var scenePhase
    @SceneStorage("lesson.cardProgress") private var savedProgress = ""
    @State private var pause: PlanPause?
    @State private var showExit = false
    @State private var started = false

    var body: some View {
        let ui = model.ui.value
        let welcome = model.welcomeBack.value
        let working: LessonUiWorking? = {
            if case let .working(w) = onEnum(of: ui), welcome == nil { return w }
            return nil
        }()
        let opened = working.map(Self.hasRecallProgress) ?? false
        let isSummary: Bool = { if case .summary = onEnum(of: ui) { return true } else { return false } }()
        ZStack {
            qh.colors.bg.ignoresSafeArea()
            content(ui: ui, welcome: welcome)
        }
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .background(SwipeBackGate(enabled: !opened))
        .confirmationDialog(copy.lessonCopy.exitTitle, isPresented: $showExit, titleVisibility: .visible) {
            Button(copy.lessonCopy.leave, role: .destructive) { leave() }
            Button(copy.lessonCopy.stay, role: .cancel) {}
        } message: {
            Text(copy.lessonCopy.exitBody)
        }
        .task {
            guard !started else { return }
            started = true
            model.bind(toaster: env.toaster, copy: copy)
            model.restore(savedProgress)
            model.startProgressSaving { savedProgress = $0 }
            model.vm.onForeground()
            for await next in IosLessonBridge.shared.pauseToday() { pause = next }
        }
        .onChange(of: copy.language, initial: false) { _, language in model.vm.onLanguage(language: language) }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                IosUi.shared.ticker.refresh()
                model.vm.onForeground()
            } else {
                model.vm.onBackground()
            }
        }
        .onChange(of: working != nil, initial: true) { _, awake in UIApplication.shared.isIdleTimerDisabled = awake }
        .onDisappear {
            model.vm.onBackground()
            UIApplication.shared.isIdleTimerDisabled = false
        }
        // A finished lesson is the happy moment for the store's review card (`ReviewPrompt` decides whether it is due); the wait lets
        // the summary and its celebration play first.
        .task(id: isSummary) {
            guard isSummary else { return }
            try? await Task.sleep(for: .seconds(2.5))
            try? await IosLessonBridge.shared.onLessonFinished()
        }
    }

    private static func hasRecallProgress(_ work: LessonUiWorking) -> Bool {
        guard work.progress.phase == .recall, let recall = work.progress.recall else { return false }
        return recall.index > 0 || recall.revealed
    }

    private func leave() { router.pop() }
    private func back(opened: Bool) { if opened { showExit = true } else { leave() } }

    // MARK: Faces

    @ViewBuilder
    private func content(ui: LessonUi, welcome: WelcomeBackUi?) -> some View {
        let c = copy.lessonCopy
        let emptyAndPaused: Bool = {
            switch onEnum(of: ui) {
            case .noPlan, .nothingToday: return pause != nil
            default: return false
            }
        }()
        if emptyAndPaused, let pause {
            LessonEmpty(title: copy.pause.cardTitle(untilDate: copy.day(Int(pause.untilEpochDay))),
                        message: copy.pause.lessonBody(resumeDate: copy.day(Int(pause.resumeEpochDay))),
                        actionLabel: c.toHome, action: { router.goHome() }, onBack: leave)
        } else if let welcome {
            VStack(spacing: 0) {
                LessonTopBar(title: c.title, subtitle: nil, backLabel: c.back, onBack: leave)
                WelcomeBackView(ui: welcome, onCatchUp: { model.vm.chooseCatchUp() }, onLighter: { model.vm.chooseLighter() })
            }
            .transition(.opacity)
        } else {
            switch onEnum(of: ui) {
            case .loading:
                LessonLoading(onBack: leave)
            case .noPlan:
                LessonEmpty(title: c.noPlanTitle, message: nil, actionLabel: c.noPlanAction,
                            action: { router.replaceTop(with: .plan) }, onBack: leave)
            case let .nothingToday(state):
                let finished = state.plan.planFinished
                LessonEmpty(title: finished ? c.planDoneTitle : c.nothingTitle, message: finished ? c.planDoneBody : c.nothingBody,
                            actionLabel: c.toHome, action: { router.goHome() }, onBack: leave)
            case let .working(work):
                LessonBody(model: model, ui: ui, plan: work.plan, work: work, summary: nil,
                           onBack: { back(opened: Self.hasRecallProgress(work)) })
            case let .summary(summary):
                LessonBody(model: model, ui: ui, plan: summary.plan, work: nil, summary: summary,
                           onBack: leave)
            }
        }
    }
}

// MARK: - Body: header, steps, the sliding card

private struct LessonBody: View {
    let model: LessonModel
    let ui: LessonUi
    let plan: LessonPlan
    let work: LessonUiWorking?
    let summary: LessonUiSummary?
    let onBack: () -> Void
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced

    private var cardKey: String { work?.card.key ?? "summary" }

    var body: some View {
        let c = copy.lessonCopy
        let steps = LessonCards.shared.steps(plan: plan)
        let current = work?.card.block
        VStack(spacing: 0) {
            LessonTopBar(title: c.title, subtitle: work != nil && plan.remainingMinutes > 0 ? c.minutesLeft(minutes: plan.remainingMinutes) : nil,
                         backLabel: c.back, onBack: onBack)
            LessonSteps(steps: steps, current: current)
            if plan.catchingUp { QuietNote(text: c.catchingUp) }
            if let until = model.lighterWeekUntil.value {
                QuietNote(text: copy.welcomeBack.lighterLine(date: copy.day(Int(truncating: until))))
            }
            ZStack {
                card
                    .id(cardKey)
                    .transition(reduced ? .opacity : .asymmetric(
                        insertion: .modifier(active: SlideFade(x: 132, opacity: 0), identity: SlideFade(x: 0, opacity: 1)),
                        removal: .modifier(active: SlideFade(x: -132, opacity: 0), identity: SlideFade(x: 0, opacity: 1))))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .animation(reduced ? .easeOut(duration: QhMotion.short) : QhMotion.enter(QhMotion.long), value: cardKey)
        }
    }

    @ViewBuilder private var card: some View {
        if let work {
            switch onEnum(of: work.card) {
            case .new: NewPassageCard(model: model, work: work)
            case let .review(review): ReviewCard(model: model, work: work, card: review)
            }
        } else if let summary {
            LessonSummaryView(model: model, summary: summary)
        }
    }
}

/// Slide from a third of the width and fade (the Android card change: 420 ms decelerate in, 280 ms accelerate out).
private struct SlideFade: ViewModifier {
    let x: CGFloat
    let opacity: Double
    func body(content: Content) -> some View { content.offset(x: x).opacity(opacity) }
}

// MARK: - Top bar and steps

struct LessonTopBar: View {
    @Environment(\.qh) private var qh
    let title: String
    let subtitle: String?
    let backLabel: String
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: QhSpacing.x1) {
            QhIconButton(icon: "chevron.backward", label: backLabel, filled: false, action: onBack)
            Text(title).qhText({ $0.title1 }).lineLimit(1).minimumScaleFactor(0.8).accessibilityAddTraits(.isHeader)
            Spacer(minLength: QhSpacing.x2)
            if let subtitle {
                Text(subtitle).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1).minimumScaleFactor(0.8)
                    .transition(.opacity)
            }
        }
        .padding(.leading, QhSpacing.x1)
        .padding(.trailing, QhSpacing.screen)
        .frame(minHeight: 56)
        // A bar row: stops growing at accessibility1 like the system bars, the content below keeps scaling.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }
}

/// Three steps with their progress: a check once a step is done, "2 of 5" before; the bar under each animates.
private struct LessonSteps: View {
    let steps: [LessonStep]
    let current: LessonBlock?

    var body: some View {
        if !steps.isEmpty {
            HStack(alignment: .top, spacing: QhSpacing.x3) {
                ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                    StepProgress(step: step, selected: step.block == current)
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x1)
            .padding(.bottom, QhSpacing.x3)
        }
    }
}

private struct StepProgress: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let step: LessonStep
    let selected: Bool

    var body: some View {
        let c = copy.lessonCopy
        let name: String = {
            switch step.block {
            case .theNew: return c.stepNew
            case .recent: return c.stepRecent
            default: return c.stepOld
            }
        }()
        let done = Int(step.done), total = Int(step.total)
        let complete = step.complete
        VStack(alignment: .leading, spacing: 2) {
            Text(name).qhText({ $0.label }, color: { selected || complete ? $0.ink : $0.inkSecondary }).lineLimit(1)
            ZStack(alignment: .leading) {
                if complete {
                    Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(qh.colors.learned)
                        .transition(.scale(scale: 0.4).combined(with: .opacity))
                } else {
                    Text(c.stepProgress(done: Int32(done), total: Int32(total))).qhText({ $0.label }, color: { $0.inkTertiary }).lineLimit(1)
                        .transition(.opacity)
                }
            }
            .frame(height: 16, alignment: .leading)
            QhLinearProgress(progress: total == 0 ? 0 : Double(done) / Double(total), height: 4,
                             color: complete ? qh.colors.learned : (selected ? qh.colors.primary : qh.colors.inkTertiary))
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .qhAnimation(QhMotion.bouncy, value: complete)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(c.stepDescription(name: name, done: Int32(done), total: Int32(total)))
    }
}

struct QuietNote: View {
    @Environment(\.qh) private var qh
    let text: String

    var body: some View {
        Text(text).qhText({ $0.callout }, color: { $0.inkSecondary })
            .padding(.horizontal, QhSpacing.x3).padding(.vertical, QhSpacing.x2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(qh.colors.surfaceMuted, in: qh.shapes.smShape)
            .padding(.horizontal, QhSpacing.screen)
            .padding(.bottom, QhSpacing.x2)
    }
}

// MARK: - Loading and empty

private struct LessonLoading: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            LessonTopBar(title: copy.lessonCopy.title, subtitle: nil, backLabel: copy.lessonCopy.back, onBack: onBack)
            VStack(spacing: QhSpacing.x4) {
                Skeleton().frame(height: 24)
                Skeleton(shape: qh.shapes.lgShape).frame(height: 160)
                Skeleton().frame(height: 20).padding(.trailing, 120)
            }
            .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
            Spacer(minLength: 0)
        }
    }
}

private struct LessonEmpty: View {
    @Environment(Copy.self) private var copy
    let title: String
    let message: String?
    let actionLabel: String
    let action: () -> Void
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            LessonTopBar(title: copy.lessonCopy.title, subtitle: nil, backLabel: copy.lessonCopy.back, onBack: onBack)
            QhEmptyState(title: title, message: message, actionLabel: actionLabel, onAction: action)
                .frame(maxHeight: .infinity)
        }
    }
}

// MARK: - Swipe back

/// Keeps the interactive pop (swipe from the edge) working although the back button is replaced, and turns it off while `enabled` is
/// false (a recall in progress: the person leaves through the exit question).
private struct SwipeBackGate: UIViewControllerRepresentable {
    let enabled: Bool

    func makeUIViewController(context: Context) -> Probe { Probe() }

    func updateUIViewController(_ controller: Probe, context: Context) {
        controller.enabled = enabled
        controller.apply()
    }

    final class Probe: UIViewController {
        var enabled = true
        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            apply()
        }
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            apply()
        }
        func apply() {
            guard let nav = navigationController, let gesture = nav.interactivePopGestureRecognizer else { return }
            gesture.delegate = nil
            gesture.isEnabled = enabled
        }
    }
}
