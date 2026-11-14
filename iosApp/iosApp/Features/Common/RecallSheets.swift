import SwiftUI

// The sheets of the recall: the five hint levels (`HintSheet.kt`) and the grade of the passage (`RecallGradeSheet.kt`).

/// The ayah the examples of the hint sheet are drawn on when there is none being recalled: Al-Ikhlas 1.
private let sampleWords = ["قُلْ", "هُوَ", "ٱللَّهُ", "أَحَدٌ"]

extension View {
    /// The sheet of the hint levels; a pick works at once and closes the sheet.
    func qhHintSheet(isPresented: Binding<Bool>, host: any RecallActions, ayahWords: [String]?) -> some View {
        qhSheet(isPresented: isPresented, detents: [.large]) {
            HintSheet(host: host, words: ayahWords ?? sampleWords) { isPresented.wrappedValue = false }
        }
    }

    /// The grade sheet. `onConfirm` gets the grade and the hard ayahs (numbers in the surah).
    func recallGradeSheet(isPresented: Binding<Bool>, session: HifzSession, title: String, copy: RecallCopy, busy: Bool = false,
                          initial: RecallResult? = nil, onConfirm: @escaping (ReviewGrade, Set<Int>) -> Void) -> some View {
        qhSheet(isPresented: isPresented, detents: [.large]) {
            RecallGradeSheet(session: session, title: title, copy: copy, busy: busy, initial: initial) { grade, hard in
                isPresented.wrappedValue = false
                onConfirm(grade, hard)
            }
        }
    }
}

struct HintSheet: View {
    @Environment(\.qh) private var qh
    let host: any RecallActions
    let words: [String]
    let dismiss: () -> Void

    var body: some View {
        let copy = host.copy
        let current = host.hintMode
        VStack(alignment: .leading, spacing: 0) {
            QhSheetHeader(title: copy.hintTitle, subtitle: copy.hintSubtitle)
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(HifzHintMode.sheetOrder) { mode in
                        HintRow(copy: copy, mode: mode, selected: mode == current,
                                example: RecallHints.preview(mode, words: words)) {
                            host.setHintMode(mode)
                            dismiss()
                        }
                    }
                }
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }
}

private struct HintRow: View {
    @Environment(\.qh) private var qh
    let copy: RecallCopy
    let mode: HifzHintMode
    let selected: Bool
    let example: String
    let action: () -> Void

    var body: some View {
        let c = qh.colors
        let ink = selected ? c.onPrimaryContainer : c.ink
        let secondary = selected ? c.onPrimaryContainer : c.inkSecondary
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(copy.hintName(mode)).qhText({ $0.title2 }, color: { _ in ink })
                Spacer(minLength: 0)
                if selected { Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(ink) }
            }
            Text(copy.hintInfo(mode)).qhText({ $0.callout }, color: { _ in secondary })
            if !example.isEmpty {
                Text(verbatim: example)
                    .qhArabic(qh.type.quranInline(.amiri, size: 22), color: ink)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .environment(\.layoutDirection, .rightToLeft)
                    .padding(.top, 6)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(selected ? c.primaryContainer : c.surfaceMuted, in: qh.shapes.mdShape)
        .pressable(haptic: .segment, shape: qh.shapes.mdShape, action: action)
        .accessibilityLabel(copy.hintName(mode))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// The sheet at the end of a recall: the passage grade (Forgot, Hard, Good, Easy) with the one that follows from the answers already
/// chosen, and the ayahs answered "hard" as chips that a tap unmarks or marks. Until the person picks a grade, the choice follows the
/// chips.
struct RecallGradeSheet: View {
    @Environment(\.qh) private var qh
    let session: HifzSession
    let title: String
    let copy: RecallCopy
    let busy: Bool
    let onConfirm: (ReviewGrade, Set<Int>) -> Void

    @State private var hard: Set<Int>
    @State private var picked: ReviewGrade?

    init(session: HifzSession, title: String, copy: RecallCopy, busy: Bool, initial: RecallResult?,
         onConfirm: @escaping (ReviewGrade, Set<Int>) -> Void) {
        self.session = session
        self.title = title
        self.copy = copy
        self.busy = busy
        self.onConfirm = onConfirm
        let start = initial?.hard ?? session.hardAyahs
        _hard = State(initialValue: start)
        let suggested = RecallGrading.suggest(total: session.size, hard: start.count)
        _picked = State(initialValue: initial.map(\.grade).flatMap { $0 == suggested ? nil : $0 })
    }

    private var suggested: ReviewGrade { RecallGrading.suggest(total: session.size, hard: hard.count) }
    private var grade: ReviewGrade { picked ?? suggested }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text(copy.gradeTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                    Text(title).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(2)
                    GradeOptions(selected: grade, copy: copy) { picked = $0 }.padding(.top, 8)
                    Text(grade == suggested ? copy.gradeSuggested : copy.gradeChosen)
                        .qhText({ $0.label }, color: { $0.inkSecondary })
                    if session.size > 0 {
                        Text(copy.hardAyahsTitle).qhText({ $0.title2 }).padding(.top, 16).accessibilityAddTraits(.isHeader)
                        Text(copy.hardAyahsHint).qhText({ $0.callout }, color: { $0.inkSecondary })
                        FlowChips(items: session.items.map(\.inSurah)) { n in
                            QhChip(label: "\(copy.ayah) \(n)", selected: hard.contains(n)) {
                                if hard.contains(n) { hard.remove(n) } else { hard.insert(n) }
                            }
                        }
                    }
                }
            }
            .scrollBounceBehavior(.basedOnSize)
            RecallWideButton(copy.done, loading: busy) { onConfirm(grade, hard) }
                .padding(.top, 16)
                .padding(.bottom, QhSpacing.x2)
        }
    }
}

/// Chips that wrap onto as many lines as they need.
private struct FlowChips<Chip: View>: View {
    let items: [Int]
    @ViewBuilder let chip: (Int) -> Chip

    var body: some View {
        ChipFlow(spacing: 8) { ForEach(items, id: \.self) { chip($0) } }
    }
}

private struct ChipFlow: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(width: proposal.width ?? .infinity, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(width: bounds.width, subviews: subviews)
        for (i, origin) in result.origins.enumerated() {
            subviews[i].place(at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y), proposal: .unspecified)
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> (size: CGSize, origins: [CGPoint]) {
        var origins: [CGPoint] = []
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width { x = 0; y += rowHeight + 4; rowHeight = 0 }
            origins.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            maxX = max(maxX, x - spacing)
        }
        return (CGSize(width: maxX, height: y + rowHeight), origins)
    }
}

/// The four grades: one row on a wide screen, two by two at big type sizes.
struct GradeOptions: View {
    @Environment(\.dynamicTypeSize) private var typeSize
    let selected: ReviewGrade
    let copy: RecallCopy
    let onPick: (ReviewGrade) -> Void

    var body: some View {
        let pills = ForEach(ReviewGrade.allCases, id: \.self) { g in
            GradePill(grade: g, selected: g == selected, copy: copy, onPick: onPick)
        }
        if typeSize.isAccessibilitySize || typeSize >= .xxLarge {
            let rows = Array(ReviewGrade.allCases.chunked(2))
            VStack(spacing: 8) {
                ForEach(rows.indices, id: \.self) { r in
                    HStack(spacing: 8) {
                        ForEach(rows[r], id: \.self) { GradePill(grade: $0, selected: $0 == selected, copy: copy, onPick: onPick) }
                    }
                }
            }
        } else {
            HStack(spacing: 8) { pills }
        }
    }
}

private extension Array {
    func chunked(_ size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) }
    }
}

/// A grade as a pill in the colour of its meaning. The chosen one gets a ring and a check, so the choice does not rest on colour alone.
private struct GradePill: View {
    @Environment(\.qh) private var qh
    let grade: ReviewGrade
    let selected: Bool
    let copy: RecallCopy
    let onPick: (ReviewGrade) -> Void

    private var look: (container: Color, content: Color, accent: Color) {
        let c = qh.colors
        switch grade {
        case .forgot: return (c.dueContainer, c.ink, c.due)
        case .hard: return (c.learningContainer, c.ink, c.learning)
        case .good: return (c.primaryContainer, c.onPrimaryContainer, c.primary)
        case .easy: return (c.learnedContainer, c.ink, c.learned)
        }
    }

    var body: some View {
        let look = look
        HStack(spacing: 6) {
            if selected {
                Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(look.content)
            } else {
                Circle().fill(look.accent).frame(width: 8, height: 8)
            }
            Text(copy.label(grade)).qhText({ $0.label }, color: { _ in look.content }).lineLimit(1).minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, minHeight: 52)
        .background(look.container, in: qh.shapes.full)
        .overlay(Capsule().strokeBorder(selected ? qh.colors.ink : .clear, lineWidth: 2))
        .pressable(haptic: grade == .forgot ? .reject : .segment, scale: 0.95, shape: Capsule()) { onPick(grade) }
        .accessibilityLabel(copy.label(grade))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
