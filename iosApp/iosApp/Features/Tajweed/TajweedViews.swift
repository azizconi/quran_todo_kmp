import ComposeApp
import SwiftUI

/// The tajweed rows of the "Aa" sheet (F3b), for the card that holds the switches: the "Tajweed" switch, under it the state of the
/// markup file while the setting is on ("Downloading tajweed markup" with a bar, or "An internet connection is needed once" with
/// "Retry"), and the row "What the colours mean" that opens the legend. Turning the switch on fetches the file the first time.
struct TajweedSettingsRows: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @State private var legend = false

    var body: some View {
        let t = copy.tajweed
        let prefs = ReadingPrefs.shared
        let service = TajweedService.shared
        QhSwitchRow(title: t.switchTitle, isOn: Binding(get: { prefs.tajweed }, set: { on in
            prefs.setTajweed(on)
            if on { service.ensureDownloaded(force: true) }
        }), description: t.switchHint)
        if prefs.tajweed, service.availability != .ready {
            VStack(alignment: .leading, spacing: QhSpacing.x2) {
                switch service.availability {
                case .needsInternet:
                    HStack {
                        Text(t.needInternet).qhText({ $0.callout }, color: { $0.inkSecondary })
                        Spacer(minLength: 0)
                        Button(t.retry) { service.retry() }.qhText({ $0.callout }, color: { $0.primary }).frame(minHeight: QhSpacing.minTouch)
                    }
                case .downloading(let fraction):
                    Text(t.downloading).qhText({ $0.callout }, color: { $0.inkSecondary })
                    QhLinearProgress(progress: fraction)
                default:
                    Text(t.downloading).qhText({ $0.callout }, color: { $0.inkSecondary })
                    QhLinearProgress(progress: 0)
                }
            }
            .padding(.horizontal, QhSpacing.card).padding(.bottom, QhSpacing.x3)
            .accessibilityElement(children: .combine)
            .transition(.opacity.combined(with: .move(edge: .top)))
        }
        QhRowDivider(startIndent: QhSpacing.card)
        QhNavRow(title: t.legendRow) { legend = true }
            .sheet(isPresented: $legend) { TajweedLegendSheet() }
    }
}

/// "What the colours mean": the six groups in the order a person learns them, and in each group its rules, every rule with the colour it
/// has now, its name, one line on how it is read and a word from the Quran with the rule coloured in it. Needs no markup file.
struct TajweedLegendSheet: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy

    var body: some View {
        let t = copy.tajweed
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                Text(t.legendTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                Text(t.legendIntro).qhText({ $0.callout }, color: { $0.inkSecondary })
                ForEach(TajweedGroup.allCases, id: \.name) { group in groupView(group, t) }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5).padding(.bottom, QhSpacing.x8)
        }
        .qhSheetStyle([.large])
    }

    private func swiftRule(_ rule: ComposeApp.TajweedRule) -> TajweedRule? {
        TajweedRule(rawValue: Character(UnicodeScalar(UInt8(truncatingIfNeeded: rule.code))))
    }

    private func groupView(_ group: TajweedGroup, _ t: TajweedCopy) -> some View {
        let rules = ComposeApp.TajweedRule.allCases.filter { $0.group == group }
        let representative = rules.first.flatMap(swiftRule)
        return VStack(alignment: .leading, spacing: QhSpacing.x2) {
            HStack(spacing: QhSpacing.x2) {
                Circle().fill(representative.map { TajweedPalette.color($0, dark: qh.isDark) } ?? qh.colors.inkSecondary).frame(width: 12, height: 12)
                Text(t.group(group: group)).qhText({ $0.title2 }).accessibilityAddTraits(.isHeader)
            }
            QhCard(padding: 0) {
                ForEach(Array(rules.enumerated()), id: \.element.code) { index, rule in
                    if index > 0 { QhRowDivider(startIndent: QhSpacing.card) }
                    ruleRow(rule, t)
                }
            }
        }
    }

    private func ruleRow(_ rule: ComposeApp.TajweedRule, _ t: TajweedCopy) -> some View {
        let text = t.rule(rule: rule)
        let color = swiftRule(rule).map { TajweedPalette.color($0, dark: qh.isDark) } ?? qh.colors.inkSecondary
        let example = TajweedExamples.shared.byRule[rule]
        return HStack(alignment: .center, spacing: QhSpacing.x3) {
            Circle().fill(color).frame(width: 14, height: 14).padding(.top, 2)
            VStack(alignment: .leading, spacing: 2) {
                Text(text.name).qhText({ $0.title2 })
                Text(text.how).qhText({ $0.callout }, color: { $0.inkSecondary })
            }
            Spacer(minLength: 0)
            if let example, let swift = swiftRule(rule) {
                Text(exampleText(example, swift))
                    .font(.custom(ReadingPrefs.shared.face.postScriptName, size: 26)).lineLimit(1).frame(maxWidth: 150, alignment: .trailing)
                    .environment(\.layoutDirection, .rightToLeft)
            }
        }
        .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3).frame(minHeight: QhSpacing.minTouch)
        .accessibilityElement(children: .combine)
    }

    private func exampleText(_ example: TajweedExample, _ rule: TajweedRule) -> AttributedString {
        var text = AttributedString(example.text)
        text.foregroundColor = qh.colors.ink
        for range in example.spans {
            let start = Int(range.first), end = min(Int(range.last) + 1, example.text.utf16.count)
            if end > start, let r = Range(NSRange(location: start, length: end - start), in: text) {
                text[r].foregroundColor = TajweedPalette.color(rule, dark: qh.isDark)
            }
        }
        return text
    }
}
