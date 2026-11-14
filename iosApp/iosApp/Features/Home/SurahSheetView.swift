import ComposeApp
import SwiftUI

/// The surah sheet (long press on a row or a chip): three status cards, the coverage line, pin / unpin, "Listen to surah".
/// Choosing a status lights the card, slides the sheet away and then applies it (`onPicked`); asks first when memorized ayahs would
/// leave the repetitions. Port of `SurahSheet.kt`.
struct SurahSheetView: View {
    let home: HomeModel
    let row: SurahRowUi
    let onPicked: (SurahTarget) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    @State private var coverage: SurahCoverage?
    @State private var picked: SurahTarget?
    @State private var confirm: (target: SurahTarget, lost: Int)?
    @State private var closing = false

    private var current: SurahHifzStatus? { SurahHifzStatus.from(row.status) }
    private var shown: SurahTarget { picked ?? SurahStatusChange.shared.targetOf(status: current) }

    var body: some View {
        let learned = coverage.map { Int($0.learnedAyahs.count) } ?? Int((row.learnedFraction * Float(row.ayahCount)).rounded())
        let covered = coverage.map { Int($0.coveredAyahs.count) } ?? learned
        let hard = coverage.map { Int($0.hardAyahs.count) } ?? 0
        let sub = [row.translatedName, copy.home.ayahCount(n: row.ayahCount)].filter { !$0.isEmpty }.joined(separator: " · ")

        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: QhSpacing.x4) {
                SurahNumberBadge(number: Int(row.number), status: row.status == .some(.learned) ? .learned : (row.status == .some(.learning) ? .learning : .notStarted),
                                 learnedFraction: Double(row.learnedFraction), size: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.name).qhText({ $0.title1 }).lineLimit(2).accessibilityAddTraits(.isHeader)
                    Text(sub).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
                }
                Spacer(minLength: 0)
                if !row.arabicName.isEmpty {
                    Text(row.arabicName).qhArabic(qh.type.quranInline(.amiri, size: 24), color: qh.colors.primary).lineLimit(1)
                }
            }
            .padding(.top, QhSpacing.x4)

            QhSectionHeader(copy.home.sheetStatus).padding(.top, QhSpacing.x6).padding(.bottom, QhSpacing.x3)
            HStack(spacing: QhSpacing.x3) {
                statusCard(.notStarted, label: copy.home.statusNotStarted, icon: "circle", accent: qh.colors.inkSecondary, container: qh.colors.surfaceMuted)
                statusCard(.learning, label: copy.home.sheetLearning, icon: "circle.lefthalf.filled", accent: qh.colors.learning, container: qh.colors.learningContainer)
                statusCard(.known, label: copy.home.sheetKnown, icon: "checkmark.circle.fill", accent: qh.colors.learned, container: qh.colors.learnedContainer)
            }
            Text(copy.home.coverageLine(covered: Int32(covered), total: Int32(row.ayahCount), hard: Int32(hard), learned: Int32(learned)))
                .qhText({ $0.callout }, color: { $0.inkSecondary })
                .padding(.top, QhSpacing.x3).padding(.horizontal, QhSpacing.x1)

            sheetAction(icon: row.isPinned ? "pin.slash" : "pin", title: row.isPinned ? copy.home.unpin : copy.home.pin, tint: qh.colors.gold) {
                close { home.togglePin(Int(row.number)) }
            }
            .padding(.top, QhSpacing.x3)
            sheetAction(icon: "headphones", title: copy.home.listenSurah, tint: qh.colors.primary, enabled: row.canOpen) {
                close { home.listen(surah: Int(row.number), readAyah: 1) }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.screen)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(qh.colors.surface)
        .task {
            for await value in home.coverage(surah: Int(row.number)) { coverage = value }
        }
        .confirmationDialog(
            confirm.map { copy.home.forgetTitle(n: Int32($0.lost)) } ?? "",
            isPresented: Binding(get: { confirm != nil }, set: { if !$0 { confirm = nil } }),
            titleVisibility: .visible
        ) {
            Button(copy.home.forgetConfirm, role: .destructive) { if let c = confirm { commit(c.target) } }
            Button(copy.home.cancel, role: .cancel) {}
        } message: { Text(copy.home.forgetBody) }
    }

    private func choose(_ target: SurahTarget) {
        guard !closing, shown != target else { return }
        let covered = coverage.map { Int($0.coveredAyahs.count) } ?? Int((row.learnedFraction * Float(row.ayahCount)).rounded())
        let lost = Int(SurahStatusChange.shared.lostAyahs(current: current, coveredAyahs: Int32(covered), target: target))
        if lost > 0 { confirm = (target, lost) } else { commit(target) }
    }

    private func commit(_ target: SurahTarget) {
        guard !closing else { return }
        withQhAnimation(QhMotion.snappy) { picked = target }
        close { onPicked(target) }
    }

    /// Slides the sheet away first, then runs the action so the row behind changes where the person can see it.
    private func close(_ action: @escaping () -> Void) {
        guard !closing else { return }
        closing = true
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.32) { action() }
    }

    private func statusCard(_ target: SurahTarget, label: String, icon: String, accent: Color, container: Color) -> some View {
        let selected = shown == target
        return VStack(spacing: QhSpacing.x2) {
            Image(systemName: icon).font(.system(size: 28, weight: .regular))
                .foregroundStyle(selected ? accent : qh.colors.inkSecondary)
                .scaleEffect(selected ? 1.18 : 1)
                .qhAnimation(QhMotion.bouncy, value: selected)
            Text(label).qhText({ $0.label }, color: { selected ? $0.ink : $0.inkSecondary })
                .multilineTextAlignment(.center).lineLimit(2)
        }
        .frame(maxWidth: .infinity, minHeight: 96)
        .padding(.horizontal, QhSpacing.x2).padding(.vertical, QhSpacing.x3)
        .background(selected ? container : qh.colors.surface, in: qh.shapes.mdShape)
        .overlay { qh.shapes.mdShape.stroke(selected ? accent : qh.colors.hairline, lineWidth: selected ? 2 : 1) }
        .pressable(haptic: .segment, shape: qh.shapes.mdShape) { choose(target) }
        .qhAnimation(QhMotion.snappy, value: selected)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private func sheetAction(icon: String, title: String, tint: Color, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        HStack(spacing: QhSpacing.x3) {
            QhIconTile(icon: icon, tint: enabled ? tint : qh.colors.inkTertiary)
            Text(title).qhText({ $0.title2 }, color: { enabled ? $0.ink : $0.inkTertiary })
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .pressable(scale: 0.99, action: action)
        .disabled(!enabled)
        .accessibilityLabel(title)
    }
}
