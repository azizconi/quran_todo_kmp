import SwiftUI

/// Opacity of the hidden text of the ayah being recalled, and of an ayah waiting for its turn.
private let hiddenTextAlpha: CGFloat = 0.35
private let upcomingTextAlpha: CGFloat = 0.6
/// The unblur of "Reveal" (docs/redesign/ANDROID_REDESIGN.md 6.5), and the melt of a word that opens or hides.
private let revealSeconds = 0.32
private let wordFadeSeconds = 0.22

/// Opacity of the whole row by what it is in the session: only the ayah being recalled is at full strength.
func hifzRowAlpha(_ phase: HifzPhase) -> CGFloat {
    switch phase {
    case .current: return 1
    case .done: return 0.6
    case .upcoming, .outside: return 0.5
    }
}

extension View {
    /// The row of an ayah in the mode: dimmed unless it is the ayah being recalled; its height follows the content (the controls come
    /// and go under it) instead of jumping (`hifzRow` of Kotlin).
    func hifzRow(_ state: HifzRowState?) -> some View {
        modifier(HifzRowModifier(state: state))
    }
}

private struct HifzRowModifier: ViewModifier {
    let state: HifzRowState?
    func body(content: Content) -> some View {
        if let state {
            content
                .opacity(hifzRowAlpha(state.phase))
                .qhAnimation(QhMotion.snappy, value: state.phase)
                .qhAnimation(QhMotion.gentle, value: state.revealed)
        } else {
            content
        }
    }
}

/// The Arabic text of an ayah (and its translation) as the mode shows it (`HifzAyahContent.kt`). An ayah being recalled or waiting for
/// its turn is hidden behind a blur; the blur melts in 320 ms when it is revealed, and holding the ayah being recalled peeks at it
/// (two peeks). The hint level decides which words stay sharp; at "first letters" the first letter of every hidden word is drawn in
/// its place; a rated ayah and an ayah outside the session show their text as usual.
///
/// `onReveal` is a tap on the hidden ayah, `onPeekAyah` the start of a hold. A screen reader hears "text is hidden" instead of the text.
struct HifzAyahContent: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced

    let state: HifzRowState
    let ayah: QuranAyahDisplay
    let face: QhQuranFace
    var readingSize: CGFloat = 28
    var showTajweed = false
    var translation: String?
    var hiddenLabel = "Ayah text is hidden"
    var onReveal: () -> Void = {}
    var onPeekAyah: () -> Void = {}

    @State private var hidden: CGFloat
    @State private var holding = false
    @State private var keep: Set<Int>

    init(state: HifzRowState, ayah: QuranAyahDisplay, face: QhQuranFace, readingSize: CGFloat = 28, showTajweed: Bool = false,
         translation: String? = nil, hiddenLabel: String = "Ayah text is hidden",
         onReveal: @escaping () -> Void = {}, onPeekAyah: @escaping () -> Void = {}) {
        self.state = state
        self.ayah = ayah
        self.face = face
        self.readingSize = readingSize
        self.showTajweed = showTajweed
        self.translation = translation
        self.hiddenLabel = hiddenLabel
        self.onReveal = onReveal
        self.onPeekAyah = onPeekAyah
        let goal = Self.goal(state, holding: false)
        // A row that appears while the mode runs starts at its resting value; the rows on screen when the mode starts melt in.
        _hidden = State(initialValue: state.animateIn && Self.covered(state) ? 0 : goal)
        _keep = State(initialValue: Self.keepSet(state, ayah: ayah))
    }

    private static func covered(_ s: HifzRowState) -> Bool {
        switch s.phase {
        case .current: return !s.revealed
        case .upcoming: return true
        default: return false
        }
    }

    private static func canPeek(_ s: HifzRowState) -> Bool { s.phase == .current && !s.revealed }

    private static func goal(_ s: HifzRowState, holding: Bool) -> CGFloat {
        covered(s) && !(holding && canPeek(s)) ? 1 : 0
    }

    private static func inSession(_ s: HifzRowState) -> Bool { s.phase == .current || s.phase == .upcoming }

    /// The words that stay sharp. A waiting ayah of the progressive level is hidden whole (the quarters are for the ayah being recalled).
    private static func keepSet(_ s: HifzRowState, ayah: QuranAyahDisplay) -> Set<Int> {
        guard inSession(s) else { return [] }
        let step = s.phase == .upcoming ? RecallHints.progressiveSteps : s.hideStep
        return RecallHints.shownWords(s.hintMode, wordCount: ayah.wordCount, step: step).union(s.peeked.filter { $0 < ayah.wordCount })
    }

    private var goal: CGFloat { Self.goal(state, holding: holding) }
    private var inSession: Bool { Self.inSession(state) }
    private var somethingHidden: Bool { Self.keepSet(state, ayah: ayah).count < ayah.wordCount }

    var body: some View {
        let covered = Self.covered(state)
        let canPeek = Self.canPeek(state)
        let alpha = state.phase == .upcoming ? upcomingTextAlpha : hiddenTextAlpha
        let veil: QuranVeil? = inSession
            ? QuranVeil(hidden: hidden, shown: keep, letters: RecallHints.showsLetters(state.hintMode), peeked: state.peeked, coveredAlpha: alpha)
            : nil
        VStack(alignment: .leading, spacing: 12) {
            QuranText(ayah: ayah, face: face, readingSize: readingSize, showTajweed: showTajweed, veil: veil)
                .equatable()
            if let translation, !translation.isEmpty {
                Text(translation)
                    .qhText({ $0.body }, color: { $0.inkSecondary })
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .opacity(1 - (1 - alpha) * (inSession ? hidden : 0))
                    .blur(radius: inSession ? 14 * hidden : 0)
            }
        }
        .contentShape(Rectangle())
        .modifier(RecallGesture(
            enabled: canPeek,
            onTap: {
                QhHaptics.shared.perform(.tick)
                onReveal()
            },
            onHoldStart: {
                QhHaptics.shared.perform(.longPress)
                holding = true
                onPeekAyah()
            },
            onHoldEnd: { holding = false }
        ))
        .modifier(HiddenAccessibility(hidden: covered && somethingHidden, label: hiddenLabel))
        .onChange(of: goal) { _, new in
            guard hidden != new else { return }
            if reduced {
                hidden = new
            } else if new == 0 && !holding {
                withAnimation(QhMotion.emphasized(revealSeconds)) { hidden = 0 }
            } else if state.animateIn {
                withAnimation(QhMotion.emphasized(QhMotion.long)) { hidden = new }
            } else {
                withAnimation(QhMotion.snappy) { hidden = new }
            }
        }
        .onChange(of: Self.keepSet(state, ayah: ayah)) { _, new in
            if reduced {
                keep = new
            } else {
                withAnimation(.easeOut(duration: wordFadeSeconds)) { keep = new }
            }
        }
    }
}

private struct HiddenAccessibility: ViewModifier {
    let hidden: Bool
    let label: String
    func body(content: Content) -> some View {
        if hidden {
            content.accessibilityElement(children: .ignore).accessibilityLabel(Text(label))
        } else {
            content
        }
    }
}

/// A quick tap and a press-and-hold on the text of the ayah being recalled. A finger that moves is a scroll and is left to the list.
private struct RecallGesture: ViewModifier {
    let enabled: Bool
    let onTap: () -> Void
    let onHoldStart: () -> Void
    let onHoldEnd: () -> Void

    func body(content: Content) -> some View {
        if enabled {
            content
                .onTapGesture(perform: onTap)
                .onLongPressGesture(minimumDuration: 0.45, maximumDistance: 12, perform: onHoldStart, onPressingChanged: { pressing in
                    if !pressing { onHoldEnd() }
                })
                .accessibilityAddTraits(.isButton)
                .accessibilityAction(.default, onTap)
        } else {
            content
        }
    }
}
