import SwiftUI

/// The rosette of an ayah in the mode (`HifzRosette.kt`). An answered ayah carries a small round mark of its answer (a glyph as well
/// as a colour) and its name next to it; the mark of the ayah answered last pops in.
struct HifzRosette: View {
    @Environment(\.qh) private var qh
    let number: Int
    var status: QhStatus?
    var weak = false
    let state: HifzRowState
    let copy: RecallCopy
    /// The name of the answer next to the rosette; the compact rows of the lesson leave it out.
    var showLabel = true

    var body: some View {
        HStack(spacing: 12) {
            ZStack(alignment: .bottomTrailing) {
                AyahRosette(number: number, status: status, weak: weak)
                if let mark = state.mark {
                    AnswerMark(mark: mark, pop: state.justMarked)
                        .offset(x: 5, y: 4)
                }
            }
            if let mark = state.mark, showLabel {
                Text(copy.label(mark))
                    .qhText({ $0.label }, color: { $0.inkSecondary })
                    .accessibilityLabel(copy.answered(mark))
            }
        }
    }
}

/// The colour an answer stands for: "I remember" is the brand, "hard" is due.
func hifzMarkColor(_ mark: AyahMark, _ c: QhColors) -> Color { mark == .remembered ? c.primary : c.due }

private struct AnswerMark: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let mark: AyahMark
    let pop: Bool
    @State private var scale: CGFloat

    init(mark: AyahMark, pop: Bool) {
        self.mark = mark
        self.pop = pop
        _scale = State(initialValue: pop ? 0.2 : 1)
    }

    var body: some View {
        let c = qh.colors
        ZStack {
            Circle().fill(hifzMarkColor(mark, c))
            Image(systemName: mark == .remembered ? "checkmark" : "exclamationmark")
                .font(.system(size: 8, weight: .heavy))
                .foregroundStyle(mark == .remembered ? c.onPrimary : c.readerBg)
        }
        .frame(width: 16, height: 16)
        .overlay(Circle().strokeBorder(c.readerBg, lineWidth: 1.5))
        .scaleEffect(scale)
        .onAppear {
            guard scale != 1 else { return }
            if reduced { scale = 1 } else { withAnimation(QhMotion.bouncy) { scale = 1 } }
        }
        .accessibilityHidden(true)
    }
}

/// The passage of a lesson card in the "Recall" phase (`RecallPassage.kt`): the same rows as the reader's "Check yourself" (the hidden
/// ayah, its rosette with the answer, the controls under the ayah being recalled), one under the other. The list follows the ayah being
/// recalled.
///
/// - `hard`: ayahs of the passage that are marked difficult already: their rosettes carry the mark.
/// - `compact` is the lesson: the rows are short, the number sits on the line of the text, and the controls are not under the ayah but
///   in a `RecallBottomBar` the screen pins to its bottom; the list keeps the ayah being recalled (and the one before it) in view.
/// - `bottom` comes after the last ayah (the "grade" button once everything is answered).
struct RecallPassage<Bottom: View>: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced

    let host: any RecallActions
    let ayahs: [QuranAyahDisplay]
    let face: QhQuranFace
    var readingSize: CGFloat = 28
    var showTajweed = false
    var hard: Set<Int> = []
    var compact = false
    @ViewBuilder var bottom: () -> Bottom

    /// The list moves to the ayah being recalled this long after the previous one was answered (it folds first).
    private static var glideDelay: Duration { .milliseconds(160) }

    var body: some View {
        let session = host.session
        let currentIndex = session.flatMap { $0.finished ? nil : $0.index }
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(ayahs) { ayah in
                        row(ayah, session: session)
                            .id(ayah.ayah)
                    }
                    bottom().id("bottom")
                }
            }
            .scrollDismissesKeyboard(.immediately)
            .task(id: currentIndex) {
                guard let position = currentIndex, position > 0, let session else { return }
                try? await Task.sleep(for: reduced ? .zero : Self.glideDelay)
                // The compact list keeps the ayah answered last above the current one, as the way back to what was just said.
                let target = compact ? position - 1 : position
                guard session.items.indices.contains(target) else { return }
                let id = session.items[target].inSurah
                if reduced { proxy.scrollTo(id, anchor: .top) } else { withAnimation(QhMotion.gentle) { proxy.scrollTo(id, anchor: .top) } }
            }
        }
    }

    @ViewBuilder
    private func row(_ ayah: QuranAyahDisplay, session: HifzSession?) -> some View {
        let state: HifzRowState? = session.map { HifzRowState.of($0, inSurah: ayah.ayah, hintMode: host.hintMode) }
        let copy = host.copy
        let content = state.map { st in
            HifzAyahContent(
                state: st, ayah: ayah, face: face, readingSize: readingSize, showTajweed: showTajweed,
                hiddenLabel: copy.hiddenText,
                onReveal: { host.reveal() },
                onPeekAyah: { host.peekAyah(position: st.position) }
            )
        }
        if let state, let content {
            if compact {
                VStack(spacing: 0) {
                    HStack(alignment: .top, spacing: QhSpacing.x3) {
                        HifzRosette(number: ayah.ayah, weak: hard.contains(ayah.ayah), state: state, copy: copy, showLabel: false)
                        content.frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, QhSpacing.screen)
                    .padding(.vertical, QhSpacing.x2)
                    hairline
                }
                .hifzRow(state)
            } else {
                VStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                        HifzRosette(number: ayah.ayah, weak: hard.contains(ayah.ayah), state: state, copy: copy)
                            .frame(height: QhSpacing.minTouch)
                        content
                        RecallControlsHost(host: host, inSurah: ayah.ayah)
                    }
                    .padding(.horizontal, QhSpacing.screen)
                    .padding(.top, QhSpacing.x1)
                    .padding(.bottom, QhSpacing.x4)
                    hairline
                }
                .hifzRow(state)
            }
        }
    }

    private var hairline: some View {
        Rectangle().fill(qh.colors.hairline).frame(height: 1).padding(.horizontal, QhSpacing.screen)
    }
}
