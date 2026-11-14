import ComposeApp
import Observation
import SwiftUI
import UIKit

/// The Kotlin `DrillViewModel` (the drill of the difficult ayahs) with its state as observable values.
@MainActor
@Observable
final class DrillModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.drill() }
    let ui: FlowState<DrillUi>
    let saving: FlowState<KotlinBoolean>
    let hintMode: FlowState<ComposeApp.HifzHintMode>
    let listening: FlowState<KotlinBoolean>
    let chapterNames: FlowState<[KotlinInt: ChapterNameModel]>
    @ObservationIgnored private var events: FlowEvents<DrillEvent>?
    var vm: DrillViewModel { holder.vm }

    init() {
        let vm = holder.vm
        ui = FlowState(vm.ui)
        saving = FlowState(vm.saving)
        hintMode = FlowState(vm.hintMode)
        listening = FlowState(vm.listening)
        chapterNames = FlowState(vm.chapterNames)
        vm.onLanguage(language: IosUi.shared.language.value)
    }

    func bind(toaster: Toaster, copy: Copy) {
        guard events == nil else { return }
        events = FlowEvents(holder.vm.eventFlow) { _ in toaster.show(copy.drillCopy.saveFailed, kind: .error) }
    }

    func title(_ surah: Int, language: AppLanguage) -> String {
        vm.surahTitle(number: Int32(surah), language: language, names: chapterNames.value)
    }
}

/// Drill of the hard ayahs (`DrillScreen.kt`, `Dest.HardDrill`): one card per difficult ayah, in the order of the mushaf (at most 20). The
/// card names the surah and the ayah, shows the ayah before it in full as the start of the thought and hides the hard ayah behind the
/// same recall component as the lesson. Cards slide like the lesson's; what was answered is saved when the person leaves.
struct DrillScreen: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @State private var model = DrillModel()
    @State private var host = RecallHostBridge(copy: .en)

    var body: some View {
        let ui = model.ui.value
        let c = copy.drillCopy
        let card: DrillUiCard? = { if case let .card(card) = onEnum(of: ui) { return card } else { return nil } }()
        VStack(spacing: 0) {
            TopBar(title: c.title, closeLabel: c.exit, progress: card.map { (Int($0.index), Int($0.total)) },
                   progressText: card.map { c.progress(position: $0.index + 1, total: $0.total) }, onClose: leave)
            ZStack {
                switch onEnum(of: ui) {
                case .loading:
                    VStack(spacing: QhSpacing.x4) {
                        Skeleton().frame(width: 180, height: 24)
                        Skeleton(shape: qh.shapes.mdShape).frame(height: 120)
                        Skeleton(shape: qh.shapes.mdShape).frame(height: 120)
                    }
                    .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x4)
                    .frame(maxHeight: .infinity, alignment: .top)
                case .empty:
                    QhEmptyState(title: c.emptyTitle, message: c.emptyBody, actionLabel: c.done, onAction: leave)
                case let .card(card):
                    CardView(model: model, host: host, card: card)
                        .id(Int(card.index))
                        .transition(slide)
                case let .done(done):
                    DoneView(done: done, saving: model.saving.value.boolValue, onDone: leave)
                        .id(-1)
                        .transition(slide)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipped()
            .animation(QhMotion.reduced ? .easeOut(duration: QhMotion.short) : QhMotion.emphasized(QhMotion.long), value: cardKey(ui))
        }
        .background(qh.colors.bg.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .task { model.bind(toaster: env.toaster, copy: copy) }
        .onChange(of: copy.language) { _, language in model.vm.onLanguage(language: language) }
        .onChange(of: copy.language, initial: true) { _, _ in host.copy = RecallCopy(copy.hifzKotlinCopy, hints: copy.hintsCopy) }
        .onChange(of: card.map { HifzSession($0.session) }, initial: true) { _, next in host.apply(session: next) }
        .onChange(of: HifzHintMode(model.hintMode.value), initial: true) { _, next in host.hintMode = next }
        .onChange(of: model.listening.value.boolValue, initial: true) { _, next in host.listening = next }
        .onChange(of: card != nil, initial: true) { _, awake in UIApplication.shared.isIdleTimerDisabled = awake }
        .onAppear { configure() }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
            model.vm.exit()
        }
    }

    private var slide: AnyTransition {
        if QhMotion.reduced { return .opacity }
        return .asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                           removal: .move(edge: .leading).combined(with: .opacity))
    }

    private func cardKey(_ ui: DrillUi) -> Int {
        switch onEnum(of: ui) {
        case let .card(card): return Int(card.index)
        case .done: return -1
        default: return -2
        }
    }

    private func leave() {
        model.vm.exit()
        router.pop()
    }

    @MainActor
    private func configure() {
        let model = model
        host.onSetHint = { model.vm.setHintMode(mode: $0.kotlin) }
        host.wordsOf = { inSurah in
            guard case let .card(card) = onEnum(of: model.ui.value), Int(card.ayah.numberInSurah) == inSurah else { return nil }
            return card.ayah.display.wordTexts
        }
        host.onReveal = { model.vm.reveal() }
        host.onMark = { mark, position in model.vm.mark(mark: mark.kotlin, position: Int32(position), early: false) }
        host.onMarkEarly = { mark, position in model.vm.mark(mark: mark.kotlin, position: Int32(position), early: true) }
        host.onHideMore = { position in
            guard case let .card(card) = onEnum(of: model.ui.value) else { return }
            model.vm.hideMore(position: Int32(position), wordCount: Int32(card.ayah.display.wordCount))
        }
        host.onPeekWord = { position, word in model.vm.peekWord(position: Int32(position), word: Int32(word)) }
        host.onPeekAyah = { position in model.vm.peekAyah(position: Int32(position)) }
        host.onToggleListen = {
            guard case let .card(card) = onEnum(of: model.ui.value) else { return }
            model.vm.toggleListen(surah: card.surah, inSurah: card.ayah.numberInSurah)
        }
    }
}

// MARK: - Pieces

/// The close button, the title, "3 / 12" and the thin gold bar of how far the drill is.
private struct TopBar: View {
    @Environment(\.qh) private var qh
    let title: String
    let closeLabel: String
    let progress: (Int, Int)?
    let progressText: String?
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: QhSpacing.x1) {
                QhIconButton(icon: "xmark", label: closeLabel, filled: false, action: onClose)
                Text(title).qhText({ $0.title1 }).lineLimit(1).accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                if let progressText {
                    Text(progressText).qhText({ $0.callout }, color: { $0.inkSecondary }).qhTabular()
                        .contentTransition(.numericText())
                }
            }
            .padding(.leading, 4).padding(.trailing, QhSpacing.screen)
            .frame(height: 56)
            if let progress {
                QhLinearProgress(progress: Double(progress.0) / Double(max(progress.1, 1)), height: 3, color: qh.colors.gold,
                                 trackColor: qh.colors.gold.opacity(0.25))
                    .padding(.horizontal, QhSpacing.screen)
            }
        }
    }
}

private struct CardView: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let model: DrillModel
    let host: RecallHostBridge
    let card: DrillUiCard

    var body: some View {
        let c = copy.drillCopy
        let ayah = card.ayah.display
        let state = host.session.map { HifzRowState.of($0, inSurah: ayah.ayah, hintMode: host.hintMode) }
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(model.title(Int(card.item.surah), language: copy.language)).qhText({ $0.title1 })
                        .accessibilityAddTraits(.isHeader)
                    Text(c.ayah(number: card.item.ayah)).qhText({ $0.callout }, color: { $0.due })
                }
                if let previous = card.previous {
                    VStack(alignment: .leading, spacing: QhSpacing.x1) {
                        Text(c.previousAyah).qhText({ $0.label }, color: { $0.inkSecondary })
                        QuranText(ayah: previous.display, face: LessonArabic.face, readingSize: LessonArabic.lessonSize)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, QhSpacing.x4).padding(.vertical, QhSpacing.x3)
                    .background(qh.colors.surfaceMuted, in: qh.shapes.mdShape)
                }
                Text(c.question).qhText({ $0.callout }, color: { $0.inkSecondary })
                if let state {
                    VStack(alignment: .leading, spacing: 0) {
                        HifzAyahContent(state: state, ayah: ayah, face: LessonArabic.face, readingSize: LessonArabic.lessonSize,
                                        hiddenLabel: host.copy.hiddenText,
                                        onReveal: { host.reveal() }, onPeekAyah: { host.peekAyah(position: state.position) })
                        RecallControlsHost(host: host, inSurah: ayah.ayah)
                    }
                    .hifzRow(state)
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x3)
            .padding(.bottom, QhSpacing.x8)
        }
        .scrollDismissesKeyboard(.immediately)
    }
}

/// "Practice finished", "I remember: 7 · Still hard: 3", "4 more next time" and "Done" (it waits for the last write).
private struct DoneView: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let done: DrillUiDone
    let saving: Bool
    let onDone: () -> Void
    @State private var shown = false

    var body: some View {
        let c = copy.drillCopy
        VStack {
            VStack(spacing: QhSpacing.x3) {
                AnimatedCheckmark()
                    .frame(width: 72, height: 72)
                Text(c.doneTitle).qhText({ $0.headline }).multilineTextAlignment(.center).accessibilityAddTraits(.isHeader)
                Text(c.summary(remembered: done.remembered, stillHard: done.stillHard))
                    .qhText({ $0.title2 }).multilineTextAlignment(.center).qhTabular()
                if done.more > 0 {
                    Text(c.more(count: done.more)).qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                }
            }
            .frame(maxHeight: .infinity)
            .opacity(shown || reduced ? 1 : 0).offset(y: shown || reduced ? 0 : 12)
            QhButton(c.done, loading: saving, fullWidth: true, action: onDone).disabled(saving)
        }
        .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x6)
        .onAppear {
            QhHaptics.shared.perform(.success)
            withAnimation(QhMotion.enter(QhMotion.long)) { shown = true }
        }
    }
}
