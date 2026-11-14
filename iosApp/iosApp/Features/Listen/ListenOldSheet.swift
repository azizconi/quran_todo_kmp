import ComposeApp
import Observation
import SwiftUI

@MainActor
@Observable
final class ListenOldModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.listenOld() }
    @ObservationIgnored private var events: FlowEvents<ListenEvent>?
    let ui: FlowState<ListenOldUi>
    let chapterNames: FlowState<[KotlinInt: ChapterNameModel]>
    var vm: ListenOldViewModel { holder.vm }

    init() {
        let vm = holder.vm
        ui = FlowState(vm.ui)
        chapterNames = FlowState(vm.chapterNames)
        vm.onLanguage(language: IosUi.shared.language.value)
    }

    func bind(copy: Copy, toaster: Toaster, onMarked: @escaping () -> Void) {
        guard events == nil else { return }
        events = FlowEvents(vm.eventFlow) { event in
            switch onEnum(of: event) {
            case let .marked(marked):
                onMarked()
                toaster.show(copy.listen.marked(count: marked.count), kind: .success)
            case .markFailed:
                toaster.show(copy.lessonCopy.saveFailed, kind: .error)
            }
        }
    }

    func title(_ surah: Int32, copy: Copy) -> String { vm.surahTitle(number: surah, language: copy.language, names: chapterNames.value) }
}

/// "Listen to the old" (`ListenOldSheet.kt`): the passages of today's portion of the manzil circle, played one after another with the
/// reciter and the speed of the person, once or with the echo ("Your turn" silences). The sound is a queue of the playback controller:
/// it goes on when this sheet is closed. When it is over, or on "Done", "Mark: I repeated it by ear" writes a "listened" record for each
/// passage heard to the end. Listening is a help, not a repetition: only "Recall" grades, and the circle does not move.
struct ListenOldSheet: View {
    let onDismiss: () -> Void
    @State private var model = ListenOldModel()
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh

    var body: some View {
        let l = copy.listen
        let ui = model.ui.value
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                Text(l.oldTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                if !ui.loaded {
                    Skeleton(shape: qh.shapes.lgShape).frame(height: 160)
                } else if ui.rows.isEmpty {
                    Text(l.sheetEmpty).qhText({ $0.body }, color: { $0.inkSecondary }).padding(.vertical, QhSpacing.x4)
                } else {
                    Text(l.oldSubtitle(count: Int32(ui.rows.count), minutes: ui.minutes)).qhText({ $0.callout }, color: { $0.inkSecondary })
                    QhSegmentedControl(options: [l.modeOnce, l.modeEcho],
                                       selection: Binding(get: { ui.echo ? 1 : 0 }, set: { model.vm.setEcho(echo: $0 == 1) }))
                    Transport(model: model, ui: ui)
                    VStack(spacing: 0) {
                        ForEach(Array(ui.rows.enumerated()), id: \.offset) { position, row in
                            PassageRowView(model: model, position: position, row: row, queue: ui.queue)
                        }
                    }
                    Actions(model: model, ui: ui)
                    Text(l.notRecallNote).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1).padding(.bottom, QhSpacing.x2)
                }
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .task {
            model.bind(copy: copy, toaster: env.toaster, onMarked: onDismiss)
        }
    }
}

/// The passage that plays (or the first one), what is heard of it, and the previous / play / next buttons.
private struct Transport: View {
    let model: ListenOldModel
    let ui: ListenOldUi
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    static func whereText(copy: Copy, position: Int, total: Int, queue: ListenQueueUi?) -> String {
        var text = copy.listen.passageOf(position: Int32(position + 1), total: Int32(total))
        if let queue, queue.ayah > 0, !queue.finished {
            text += " · " + HomeCopyKt.ayahRangeLabel(start: queue.ayah, end: queue.ayah, language: copy.language)
        }
        return text
    }

    var body: some View {
        let l = copy.listen
        let queue = ui.queue
        if let row = ui.rows[safe: Int(queue?.position ?? 0)] {
            let playing = queue?.playing == true
            let title = model.title(row.surah, copy: copy)
            let range = HomeCopyKt.ayahRangeLabel(start: row.startAyah, end: row.endAyah, language: copy.language)
            let position = Int(queue?.position ?? 0)
            let whereText = Self.whereText(copy: copy, position: position, total: ui.rows.count, queue: queue)
            QhCard {
                VStack(spacing: 0) {
                    Text(title).qhText({ $0.title2 }).multilineTextAlignment(.center).lineLimit(2)
                    Text("\(range) · \(whereText)").qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center).lineLimit(2)
                    YourTurnLine(yourTurn: l.yourTurn, secondsShort: l.secondsShort, remaining: Int(queue?.silenceSeconds ?? 0), active: queue?.yourTurn == true)
                        .padding(.top, QhSpacing.x1)
                    HStack(spacing: QhSpacing.x6) {
                        QhIconButton(icon: "backward.fill", label: l.previousPassage, tint: queue == nil ? qh.colors.inkTertiary : nil) { model.vm.previous() }
                            .disabled(queue == nil)
                        ZStack {
                            EchoRing(remaining: Int(queue?.silenceSeconds ?? 0), total: Int(queue?.silenceTotalSeconds ?? 0), active: queue?.yourTurn == true)
                            PlayButton(playing: playing, label: playing ? l.pause : l.play) { model.vm.toggle() }
                        }
                        .frame(width: 76, height: 76)
                        QhIconButton(icon: "forward.fill", label: l.nextPassage, tint: queue == nil ? qh.colors.inkTertiary : nil) { model.vm.next() }
                            .disabled(queue == nil)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

/// One passage of the portion: its number (a check once heard in this run), the name, the minutes, and what is known of it today.
private struct PassageRowView: View {
    let model: ListenOldModel
    let position: Int
    let row: ListenRow
    let queue: ListenQueueUi?
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let l = copy.listen
        let current = queue != nil && Int(queue!.position) == position && !queue!.finished
        let heard = queue?.heard.contains(KotlinInt(int: Int32(position))) == true
        let range = HomeCopyKt.ayahRangeLabel(start: row.startAyah, end: row.endAyah, language: copy.language)
        let title = model.title(row.surah, copy: copy)
        let status = ([l.minutes(minutes: row.minutes)] + (row.listened ? [l.listenedBadge] : []) + (row.recalled ? [l.recalledBadge] : []))
            .joined(separator: " · ")
        HStack(spacing: QhSpacing.x3) {
            ZStack {
                Circle().fill(heard ? qh.colors.learned : qh.colors.surfaceMuted)
                if heard {
                    Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(qh.colors.onLearned)
                        .transition(.scale(scale: 0.3).combined(with: .opacity))
                } else {
                    Text("\(position + 1)").qhText({ $0.label }, color: { $0.inkSecondary })
                }
            }
            .frame(width: 28, height: 28)
            VStack(alignment: .leading, spacing: 0) {
                Text("\(title) · \(range)").qhText({ $0.body }).lineLimit(2)
                Text(status).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(2)
            }
            Spacer(minLength: 0)
            if current { Image(systemName: "headphones").foregroundStyle(qh.colors.primary) }
        }
        .padding(.horizontal, QhSpacing.x3).padding(.vertical, QhSpacing.x2)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(current ? qh.colors.primaryContainer : .clear, in: qh.shapes.mdShape)
        .pressable(scale: 0.99, shape: qh.shapes.mdShape) { model.vm.play(position: Int32(position)) }
        .qhAnimation(QhMotion.snappy, value: heard)
        .qhAnimation(QhMotion.snappy, value: current)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(range). \(status)" + (current ? ". \(l.nowPlayingLabel)" : ""))
        .accessibilityAddTraits(current ? .isSelected : [])
    }
}

/// "Done" while the queue runs; when it is over (or after "Done"): "Mark: I repeated it by ear" with what was heard, and "Listen again".
private struct Actions: View {
    let model: ListenOldModel
    let ui: ListenOldUi
    @Environment(Copy.self) private var copy

    var body: some View {
        let l = copy.listen
        if let queue = ui.queue {
            VStack(spacing: QhSpacing.x2) {
                if !queue.finished {
                    QhButton(l.done, variant: .tonal, size: .m, fullWidth: true) { model.vm.finish() }
                } else {
                    if !queue.heard.isEmpty {
                        Text(l.heardCount(count: Int32(queue.heard.count))).qhText({ $0.callout }, color: { $0.inkSecondary })
                        QhButton(l.markHeard, size: .m, icon: "checkmark", fullWidth: true) { model.vm.mark() }
                    }
                    QhButton(l.startAgain, variant: .text, size: .m, fullWidth: true) { model.vm.start(position: 0) }
                }
            }
            .transition(.opacity)
        }
    }
}
