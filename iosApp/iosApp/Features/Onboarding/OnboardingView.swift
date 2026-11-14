import ComposeApp
import SwiftUI

/// Owns the Kotlin `OnboardingViewModel` and its flows.
@MainActor
@Observable
final class OnboardingModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.onboarding() }
    @ObservationIgnored private var events: FlowEvents<OnboardingEvent>?

    let ui: FlowState<OnboardingUi>
    let analyticsEnabled: FlowState<KotlinBoolean>
    var vm: OnboardingViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        analyticsEnabled = FlowState(holder.vm.analyticsEnabled)
    }

    func bind(onFinished: @escaping (OnboardingExit) -> Void, onFailed: @escaping () -> Void) {
        guard events == nil else { return }
        events = FlowEvents(vm.eventFlow) { event in
            switch onEnum(of: event) {
            case .finished(let done): onFinished(done.exit)
            case .finishFailed: onFailed()
            }
        }
    }
}

/// A move of the steps: forward slides in from the trailing side, back from the leading side (12% of the width + a fade).
private struct StepShift: ViewModifier {
    let x: CGFloat
    let opacity: Double
    func body(content: Content) -> some View { content.offset(x: x).opacity(opacity) }
}

private extension AnyTransition {
    static func stepMove(forward: Bool, reduced: Bool) -> AnyTransition {
        if reduced { return .opacity }
        let dx: CGFloat = 46 * (forward ? 1 : -1)
        return .asymmetric(
            insertion: .modifier(active: StepShift(x: dx, opacity: 0), identity: StepShift(x: 0, opacity: 1)),
            removal: .modifier(active: StepShift(x: -dx, opacity: 0), identity: StepShift(x: 0, opacity: 1))
        )
    }
}

/// The onboarding: Language -> Preset -> Known -> Ready. Nothing of the progress is written until the last screen.
struct OnboardingView: View {
    let onFinished: (OnboardingExit) -> Void

    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @State private var model = OnboardingModel()
    @State private var forward = true
    @State private var addSheet = false

    var body: some View {
        let ui = model.ui.value
        let step = ui.position.step
        let c = copy.onboarding
        VStack(spacing: 0) {
            topRow(ui)
            ZStack {
                Group {
                    switch step {
                    case .language: LanguageStep(language: env.language, onSelect: { env.setLanguage($0) })
                    case .preset: PresetStep(model: model, ui: ui)
                    case .known: KnownStep(model: model, ui: ui)
                    case .ready: ReadyStep(model: model, ui: ui)
                    }
                }
                .id(step)
                .transition(.stepMove(forward: forward, reduced: reduced))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            footer(ui: ui, step: step, c: c)
        }
        .background(qh.colors.bg.ignoresSafeArea())
        .qhAnimation(QhMotion.enter(0.38), value: step)
        .onAppear {
            model.bind(onFinished: onFinished, onFailed: { env.toaster.show(copy.onboarding.finishFailed, kind: .error) })
            model.vm.setLanguage(next: env.language)
        }
        .onChange(of: copy.language) { _, language in model.vm.setLanguage(next: language) }
    }

    private var backLabel: String {
        switch copy.language {
        case .ru: return "Назад"
        case .tg: return "Ба қафо"
        case .uz: return "Orqaga"
        case .tr: return "Geri"
        default: return "Back"
        }
    }

    // MARK: Chrome

    private func topRow(_ ui: OnboardingUi) -> some View {
        let index = Int(ui.segmentIndex)
        let total = Int(ui.segmentCount)
        return HStack(spacing: QhSpacing.x3) {
            ZStack {
                if index > 0 {
                    QhIconButton(icon: "chevron.backward", label: backLabel, filled: false) {
                        forward = false
                        _ = model.vm.back()
                    }
                    .transition(.scale(scale: 0.6).combined(with: .opacity))
                }
            }
            .frame(width: QhSpacing.minTouch, height: QhSpacing.minTouch)
            HStack(spacing: 6) {
                ForEach(0..<total, id: \.self) { i in
                    Capsule().fill(i <= index ? qh.colors.primary : qh.colors.surfaceStrong).frame(height: 4)
                        .qhAnimation(QhMotion.snappy, value: index)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(copy.onboarding.stepLabel(step: Int32(index + 1), total: Int32(total)))
            Color.clear.frame(width: QhSpacing.minTouch, height: 1)
        }
        .padding(.horizontal, QhSpacing.x2)
        .qhAnimation(QhMotion.snappy, value: index > 0)
    }

    private func footer(ui: OnboardingUi, step: OnboardingStep, c: OnboardingCopy) -> some View {
        let sel = ui.selection
        let known = sel.known
        var primary = c.next
        var note: String?
        var enabled = true
        var loading = false
        var secondary: String?
        var onPrimary: () -> Void = { forward = true; model.vm.next() }
        var onSecondary: () -> Void = {}

        switch step {
        case .language:
            primary = c.start
        case .preset:
            enabled = sel.preset != nil && !sel.queue.isEmpty && !ui.finishing
        case .known:
            if OnboardingPlanMath.shared.nothingToLearn(selection: sel) {
                enabled = false
                switch sel.preset {
                case .juzAmma: note = c.allKnownJuzAmma
                case .juzTabarak: note = c.allKnownJuzTabarak
                case .fromStart: note = c.allKnownFromStart
                default: note = c.allKnownCustom
                }
            } else {
                primary = known.isEmpty ? c.knownNone : c.next
                note = known.isEmpty ? nil : c.knownCount(count: Int32(known.count))
            }
        case .ready:
            primary = c.readyStart
            loading = ui.finishing
            secondary = c.later
            onPrimary = { model.vm.finish(exit: .lesson) }
            onSecondary = { model.vm.finish(exit: .later) }
        }
        return VStack(spacing: QhSpacing.x2) {
            if let note {
                Text(note).qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            QhButton(primary, loading: loading, fullWidth: true, action: onPrimary).disabled(!enabled)
            if let secondary {
                QhButton(secondary, variant: .text, size: .m, fullWidth: true, action: onSecondary).disabled(ui.finishing)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x2)
        .background(qh.colors.bg)
        .qhAnimation(QhMotion.gentle, value: note)
        .qhAnimation(QhMotion.gentle, value: secondary)
    }
}
