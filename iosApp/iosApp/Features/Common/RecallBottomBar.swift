import SwiftUI

/// The controls of the lesson's "Recall", pinned to the bottom above the safe area (`RecallBottomBar.kt`), so the answer is always where
/// the thumb is, whatever the list does:
///
/// - the ayah being recalled is hidden: the main button "Reveal" (or "Hide more" of the progressive hint) and above it one row of the
///   quieter actions "Listen", "Hint", "Record reading";
/// - the ayah is revealed: the line "The answer decides when the passage comes back" and two large answers;
/// - every ayah is answered: the result row (the grade, when the passage comes back, "Change") and the button `nextLabel`.
///
/// Use it as `.safeAreaInset(edge: .bottom) { RecallBottomBar(...) }`. `result` is the grade about to be written (nil while ayahs are
/// left); `returnDays` says when the passage comes back (nil: leave it out). `onRecord` opens "Record my reading", `onChange` the grade
/// sheet, `onNext` writes the grade and goes on; `saving` holds the button.
struct RecallBottomBar: View {
    @Environment(\.qh) private var qh

    let host: any RecallActions
    var result: RecallResult?
    var returnDays: Int?
    var recordLabel: String?
    var onRecord: () -> Void = {}
    /// False hides the "Record reading" action (a screen without a recording sheet yet).
    var showsRecord = true
    var onChange: () -> Void = {}
    var nextLabel = ""
    var onNext: () -> Void = {}
    var saving = false

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(qh.colors.hairline).frame(height: 1)
            VStack(alignment: .leading, spacing: QhSpacing.x2) {
                if let session = host.session {
                    if let result {
                        ResultBar(host: host, result: result, returnDays: returnDays, onChange: onChange, nextLabel: nextLabel,
                                  onNext: onNext, saving: saving)
                    } else if session.revealed {
                        AnswerBar(host: host, session: session)
                    } else {
                        RevealBar(host: host, session: session, recordLabel: recordLabel ?? host.copy.recordShort, showsRecord: showsRecord, onRecord: onRecord)
                    }
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.vertical, QhSpacing.x3)
        }
        .background(qh.colors.surface.ignoresSafeArea(edges: .bottom))
        .qhAnimation(QhMotion.gentle, value: host.session?.revealed)
        .qhAnimation(QhMotion.gentle, value: result == nil)
    }
}

// MARK: - Every ayah answered

private struct ResultBar: View {
    @Environment(\.qh) private var qh
    let host: any RecallActions
    let result: RecallResult
    let returnDays: Int?
    let onChange: () -> Void
    let nextLabel: String
    let onNext: () -> Void
    let saving: Bool

    var body: some View {
        let copy = host.copy
        HStack {
            VStack(alignment: .leading, spacing: 0) {
                Text(copy.result(result.grade)).qhText({ $0.title2 })
                if let returnDays {
                    Text(copy.returns(days: returnDays)).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
            }
            Spacer(minLength: 0)
            Text(copy.change)
                .qhText({ $0.label }, color: { $0.primary })
                .padding(.horizontal, QhSpacing.x3)
                .frame(minHeight: QhSpacing.minTouch)
                .pressable(shape: Capsule(), action: onChange)
                .accessibilityLabel(copy.change)
        }
        QhButton(nextLabel, loading: saving, fullWidth: true, action: onNext)
            .disabled(saving)
    }
}

// MARK: - The ayah is revealed

private struct AnswerBar: View {
    let host: any RecallActions
    let session: HifzSession

    var body: some View {
        let copy = host.copy
        if let item = session.current {
            QhInlineHint(id: "lesson_grade", text: copy.lessonGradeHint, dismissLabel: copy.dismiss)
            if item.suggestsHard {
                Text(copy.peeked(item.peeks)).qhText({ $0.label }, color: { $0.inkSecondary })
            }
            AnswerPills(host: host, item: item, position: session.index, early: false)
        }
    }
}

// MARK: - The ayah is hidden

private struct RevealBar: View {
    @Environment(\.qh) private var qh
    let host: any RecallActions
    let session: HifzSession
    let recordLabel: String
    let showsRecord: Bool
    let onRecord: () -> Void
    @State private var hintOpen = false

    var body: some View {
        let copy = host.copy
        let item = session.current
        let mode = host.hintMode
        let words = item.flatMap { host.words(inSurah: $0.inSurah) }
        let step = item?.hideStep ?? 0
        // The progressive hint: "Hide more" takes the place of "Reveal" until the ayah is hidden entirely, and the answers are on offer
        // from the first hidden quarter on.
        let hidesMore = mode == .progressive && words.map { RecallHints.canHideMore(wordCount: $0.count, step: step) } == true
        if session.markedCount == 0 {
            Text(copy.peekHint).qhText({ $0.label }, color: { $0.inkSecondary })
        }
        if mode == .progressive, step > 0 {
            Text(copy.hiddenShare(RecallHints.percent(at: step))).qhText({ $0.label }, color: { $0.inkSecondary })
        }
        if let item, item.suggestsHard {
            Text(copy.peeked(item.peeks)).qhText({ $0.label }, color: { $0.inkSecondary })
        }
        HStack(spacing: QhSpacing.x2) {
            BarAction(icon: host.listening ? "pause.fill" : "headphones", label: host.listening ? copy.listenPause : copy.listen) {
                host.toggleListen()
            }
            BarAction(icon: "lightbulb", label: copy.hintTitle) { hintOpen = true }
            if showsRecord { BarAction(icon: "mic", label: recordLabel, action: onRecord) }
        }
        if hidesMore {
            QhButton(copy.hideMore, size: .l, icon: "eye.slash", fullWidth: true) { host.hideMore(position: session.index) }
            if step > 0, let item {
                AnswerPills(host: host, item: item, position: session.index, early: true)
            }
        } else {
            QhButton(copy.reveal, size: .l, icon: "eye", fullWidth: true) { host.reveal() }
        }
        Color.clear.frame(height: 0).qhHintSheet(isPresented: $hintOpen, host: host, ayahWords: words)
    }
}

/// A quiet action of the bar: the icon over a short name, at least 56 high; the name may take two lines.
private struct BarAction: View {
    @Environment(\.qh) private var qh
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: icon).font(.system(size: 17, weight: .semibold)).foregroundStyle(qh.colors.primary)
            Text(label).qhText({ $0.label }).lineLimit(2).multilineTextAlignment(.center)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, minHeight: 56)
        .background(qh.colors.surfaceMuted, in: qh.shapes.mdShape)
        .pressable(haptic: .segment, shape: qh.shapes.mdShape, action: action)
        .accessibilityLabel(label)
    }
}
