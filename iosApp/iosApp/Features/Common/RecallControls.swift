import SwiftUI

// The controls of the recall (`HifzControls.kt`): under the ayah being recalled (the reader's "Check yourself", the drill) and the
// answer pills shared with the pinned bar.

/// What sits under an ayah of the session. Only the ayah being recalled shows anything: first "Listen" and "Reveal", after the reveal
/// the two answers "I remember" / "Hard". It comes in when the ayah becomes the current one and folds away when it is answered.
struct RecallControlsHost: View {
    let host: any RecallActions
    let inSurah: Int

    var body: some View {
        let session = host.session
        if let session, !session.finished, session.current?.inSurah == inSurah {
            RecallControls(host: host, session: session)
                .transition(.opacity.combined(with: .move(edge: .top)))
        }
    }
}

private struct RecallControls: View {
    let host: any RecallActions
    let session: HifzSession

    var body: some View {
        Group {
            if session.revealed {
                MarkControls(host: host, session: session)
            } else {
                RevealControls(host: host, session: session, firstAyah: session.markedCount == 0)
            }
        }
        .padding(.top, 14)
        .qhAnimation(QhMotion.gentle, value: session.revealed)
    }
}

private struct RevealControls: View {
    @Environment(\.qh) private var qh
    @Environment(\.dynamicTypeSize) private var typeSize
    let host: any RecallActions
    let session: HifzSession
    let firstAyah: Bool
    @State private var hintOpen = false

    var body: some View {
        let copy = host.copy
        let item = session.current
        let mode = host.hintMode
        let words = item.flatMap { host.words(inSurah: $0.inSurah) }
        let step = item?.hideStep ?? 0
        let hidesMore = mode == .progressive && words.map { RecallHints.canHideMore(wordCount: $0.count, step: step) } == true
        VStack(alignment: .leading, spacing: 12) {
            Text(copy.reciteFromMemory).qhText({ $0.callout }, color: { $0.inkSecondary })
            let action = Group {
                if hidesMore {
                    QhButton(copy.hideMore, size: .l, icon: "eye.slash", fullWidth: true) { host.hideMore(position: session.index) }
                } else {
                    QhButton(copy.reveal, size: .l, icon: "eye", fullWidth: true) { host.reveal() }
                }
            }
            let listen = QhButton(host.listening ? copy.listenPause : copy.listen, variant: .tonal, size: .m,
                                  icon: host.listening ? "pause.fill" : "headphones") { host.toggleListen() }
            if typeSize.isAccessibilitySize {
                VStack(spacing: 12) { action; listen }
            } else {
                HStack(spacing: 12) { listen; action }
            }
            if hidesMore, step > 0, let item {
                AnswerPills(host: host, item: item, position: session.index, early: true)
            }
            HStack(spacing: 12) {
                HintChip(label: copy.hintChip(mode)) { hintOpen = true }
                if mode == .progressive, step > 0 {
                    Text(copy.hiddenShare(RecallHints.percent(at: step))).qhText({ $0.label }, color: { $0.inkSecondary })
                }
            }
            if firstAyah {
                Text(copy.peekHint).qhText({ $0.label }, color: { $0.inkSecondary })
            }
            if let item, item.suggestsHard {
                Text(copy.peeked(item.peeks)).qhText({ $0.label }, color: { $0.inkSecondary })
            }
        }
        .qhHintSheet(isPresented: $hintOpen, host: host, ayahWords: words)
    }
}

private struct MarkControls: View {
    let host: any RecallActions
    let session: HifzSession

    var body: some View {
        let copy = host.copy
        VStack(alignment: .leading, spacing: 12) {
            Text(copy.markTitle).qhText({ $0.callout }, color: { $0.inkSecondary })
            if let item = session.current {
                AnswerPills(host: host, item: item, position: session.index, early: false)
                QhInlineHint(id: "lesson_grade", text: copy.lessonGradeHint, dismissLabel: copy.dismiss)
                if item.suggestsHard {
                    Text(copy.peeked(item.peeks)).qhText({ $0.label }, color: { $0.inkSecondary })
                }
            }
        }
    }
}

/// The two answers; `early` is before the reveal (the progressive hint, from the first hidden quarter on). An ayah that was peeked at
/// twice or more is offered "Hard" as the main (filled) button, with "I remember" next to it as the quiet one.
struct AnswerPills: View {
    @Environment(\.qh) private var qh
    @Environment(\.dynamicTypeSize) private var typeSize
    let host: any RecallActions
    let item: HifzItem
    let position: Int
    let early: Bool

    var body: some View {
        let c = qh.colors
        let copy = host.copy
        let hardFirst = item.suggestsHard
        let remembered = MarkPill(
            label: copy.remembered,
            container: hardFirst ? c.primaryContainer : c.primary,
            content: hardFirst ? c.onPrimaryContainer : c.onPrimary,
            dot: nil, haptic: .tick
        ) { answer(.remembered) }
        let hard = MarkPill(
            label: copy.hardMark,
            container: hardFirst ? c.due : c.dueContainer,
            content: hardFirst ? c.onDanger : c.ink,
            dot: hardFirst ? nil : c.due, haptic: .segment
        ) { answer(.hard) }
        if typeSize.isAccessibilitySize {
            VStack(spacing: 8) { remembered; hard }
        } else {
            HStack(spacing: 8) { remembered; hard }
        }
    }

    private func answer(_ mark: AyahMark) {
        HintFlags.shared.markUsed("lesson_grade")
        if early { host.markEarly(mark, position: position) } else { host.mark(mark, position: position) }
    }
}

/// An answer button: a pill in the colour of its meaning, with a dot where the fill alone would not tell.
private struct MarkPill: View {
    @Environment(\.qh) private var qh
    let label: String
    let container: Color
    let content: Color
    let dot: Color?
    let haptic: QhHapticKind
    let action: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            if let dot { Circle().fill(dot).frame(width: 8, height: 8) }
            Text(label).qhText({ $0.title2 }, color: { _ in content }).lineLimit(1)
        }
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, minHeight: 56)
        .background(container, in: qh.shapes.full)
        .pressable(haptic: haptic, scale: 0.95, shape: Capsule(), action: action)
        .accessibilityLabel(label)
    }
}

/// "Hint: First word": the level that is in force; a tap opens the sheet of the five levels.
struct HintChip: View {
    @Environment(\.qh) private var qh
    let label: String
    let action: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "lightbulb").font(.system(size: 13, weight: .semibold)).foregroundStyle(qh.colors.gold)
            Text(label).qhText({ $0.label }).lineLimit(1)
            Image(systemName: "chevron.down").font(.system(size: 11, weight: .bold)).foregroundStyle(qh.colors.inkSecondary)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 36)
        .background(qh.colors.surfaceMuted, in: qh.shapes.full)
        .padding(.vertical, 6)
        .pressable(haptic: .segment, shape: Capsule(), highlight: false, action: action)
        .accessibilityLabel(label)
    }
}
