import ComposeApp
import SwiftUI

/// Hosts the one-time "What's new: the lesson of the day" sheet over Home for people who updated from an older version (`WhatsNewGate`).
/// It rises once per launch, 900 ms after Home is on top and at rest, unless a deep link was opened in this launch.
private struct WhatsNewHost: ViewModifier {
    let pending: Bool
    let atHome: Bool
    @Environment(Router.self) private var router
    @State private var visible = false
    @State private var raised = false

    func body(content: Content) -> some View {
        content
            .task(id: pending && atHome && !router.deepLinkedThisLaunch && !raised) {
                guard pending, atHome, !router.deepLinkedThisLaunch, !raised else { return }
                try? await Task.sleep(for: .milliseconds(900))
                guard !Task.isCancelled else { return }
                raised = true
                visible = true
            }
            .sheet(isPresented: $visible) {
                WhatsNewSheet(
                    onOpenLesson: { router.push(.lesson, in: .lesson) },
                    onOpenPlan: { router.push(.plan, in: .lesson) }
                )
            }
    }
}

extension View {
    func whatsNewHost(pending: Bool, atHome: Bool) -> some View { modifier(WhatsNewHost(pending: pending, atHome: atHome)) }
}

@MainActor
@Observable
private final class WhatsNewModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.whatsNew() }
    let hasPlan: FlowState<KotlinBoolean?>
    var vm: WhatsNewViewModel { holder.vm }

    init() { hasPlan = FlowState(holder.vm.hasPlan) }
}

private struct WhatsNewSheet: View {
    let onOpenLesson: () -> Void
    let onOpenPlan: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var model = WhatsNewModel()
    @State private var answered = false

    var body: some View {
        let c = copy.whatsNew
        let planExists = model.hasPlan.value?.boolValue == true
        VStack(spacing: QhSpacing.x3) {
            CalligraphyText(Calligraphy.hifz, size: 44, revealDuration: 0.9)
                .padding(.top, QhSpacing.x4)
            Text(c.title).qhText({ $0.headline }).multilineTextAlignment(.center).accessibilityAddTraits(.isHeader)
            Text(c.body).qhText({ $0.body }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
            Text(model.hasPlan.value == nil ? "" : (planExists ? c.withPlan : c.withoutPlan))
                .qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                .frame(minHeight: 20)
            Spacer(minLength: QhSpacing.x2)
            QhButton(planExists ? c.openLesson : copy.home.lessonMakePlan, fullWidth: true) {
                answer(planExists ? .lesson : .plan, then: planExists ? onOpenLesson : onOpenPlan)
            }
            QhButton(c.later, variant: .text, size: .m, fullWidth: true) { answer(.later) }
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.bottom, QhSpacing.x4)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(qh.colors.surface.ignoresSafeArea())
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .onAppear { model.vm.shown() }
        // A drag-down is "Later".
        .onDisappear { if !answered { model.vm.acted(action: .later) } }
    }

    private func answer(_ action: WhatsNewAction, then: (() -> Void)? = nil) {
        guard !answered else { return }
        answered = true
        model.vm.acted(action: action)
        dismiss()
        if let then { DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: then) }
    }
}
