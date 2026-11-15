import ComposeApp
import SwiftUI

/// Privacy summary (Android `PrivacyScreen`): an intro, one card of points with icon tiles, the full policy in the browser, a contact button.
struct PrivacyScreen: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @State private var stagger = StaggerState(screen: "privacy")

    private static let icons = ["person.crop.circle.badge.xmark", "iphone", "network", "chart.bar", "lock.shield", "speaker.slash", "trash"]

    var body: some View {
        let t = copy.trust
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                Text(t.privacyIntro).qhText({ $0.body }, color: { $0.inkSecondary })
                QhCard(padding: 0) {
                    ForEach(Array(t.privacyPoints.enumerated()), id: \.offset) { index, point in
                        if index > 0 { QhRowDivider() }
                        HStack(alignment: .top, spacing: QhSpacing.x3) {
                            QhIconTile(icon: Self.icons[index % Self.icons.count], tint: index % 2 == 0 ? qh.colors.primary : qh.colors.gold)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(point.title).qhText({ $0.title2 })
                                Text(point.text).qhText({ $0.callout }, color: { $0.inkSecondary })
                            }
                        }
                        .padding(QhSpacing.card)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityElement(children: .combine)
                    }
                }
                .staggeredEnter(index: 0, state: stagger)
                VStack(spacing: QhSpacing.x2) {
                    QhButton(t.privacyFullText, variant: .primary, icon: "safari", fullWidth: true) {
                        SettingsLinks.open(SettingsLinks.privacyPolicy(copy.language))
                    }
                    if SettingsLinks.mailto != nil {
                        QhButton(t.privacyContact, variant: .tonal, icon: "envelope", fullWidth: true) {
                            SettingsLinks.open(SettingsLinks.mailto)
                        }
                    }
                }
                .staggeredEnter(index: 1, state: stagger)
                OrnamentDivider().padding(.horizontal, QhSpacing.x10).padding(.top, QhSpacing.x2)
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.vertical, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x8)
        }
        .navigationTitle(t.privacyTitle)
        .navigationBarTitleDisplayMode(.large)
        .qhNavigationStyle()
        .task { stagger.markPlayed() }
    }
}

/// Sources and licenses (Android `SourcesScreen`): groups of rows (name, author, provider, terms, note, link). Names are shown as the
/// sources publish them. Android-only libraries are left out and the iOS ones added.
struct SourcesScreen: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @State private var stagger = StaggerState(screen: "sources")

    private static let androidOnly = ["Media3", "AndroidX", "Calf", "Decompose", "Compose Multiplatform", "Firebase"]

    var body: some View {
        let t = copy.trust
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.section) {
                Text(t.sourcesIntro).qhText({ $0.body }, color: { $0.inkSecondary })
                ForEach(Array(groups().enumerated()), id: \.offset) { index, group in
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        QhSectionHeader(title(of: group.id, t)).padding(.horizontal, QhSpacing.x1)
                        QhCard(padding: 0) {
                            ForEach(Array(group.items.enumerated()), id: \.offset) { i, item in
                                if i > 0 { QhRowDivider(startIndent: QhSpacing.card) }
                                row(item, t)
                            }
                        }
                    }
                    .staggeredEnter(index: index, state: stagger)
                }
                Text(t.sourcesFooter).qhText({ $0.callout }, color: { $0.inkSecondary })
                OrnamentDivider().padding(.horizontal, QhSpacing.x10)
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.vertical, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x8)
        }
        .navigationTitle(t.sourcesTitle)
        .navigationBarTitleDisplayMode(.large)
        .qhNavigationStyle()
        .task { stagger.markPlayed() }
    }

    private func groups() -> [SourceGroup] {
        SourcesDataKt.SourceGroups.map { group in
            guard group.id == .libraries else { return group }
            var items = group.items.filter { item in !Self.androidOnly.contains { item.title.hasPrefix($0) } }
            items.append(SourceItem(title: "SKIE", author: "Touchlab", provider: nil, url: "https://skie.touchlab.co", license: "Apache 2.0", note: nil))
            return SourceGroup(id: group.id, items: items)
        }
    }

    private func title(of id: SourceGroupId, _ t: TrustCopy) -> String {
        switch id {
        case .quranText: return t.groupQuranText
        case .translations: return t.groupTranslations
        case .surahNames: return t.groupSurahNames
        case .audio: return t.groupAudio
        case .fonts: return t.groupFonts
        case .libraries: return t.groupLibraries
        }
    }

    private func note(_ n: SourceNote?, _ t: TrustCopy) -> String? {
        switch n {
        case .tanzilTerms: return t.noteTanzilTerms
        case .translatorRights: return t.noteTranslatorRights
        case .appTeamNames: return t.noteAppTeamNames
        case .reciterRights: return t.noteReciterRights
        default: return nil
        }
    }

    private func row(_ item: SourceItem, _ t: TrustCopy) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(item.title).qhText({ $0.title2 })
            if let author = item.author { Text(author).qhText({ $0.callout }, color: { $0.inkSecondary }) }
            if let provider = item.provider { Text(provider).qhText({ $0.label }, color: { $0.inkTertiary }) }
            if let license = item.license {
                Text("\(t.licenseLabel): \(license)").qhText({ $0.label }, color: { $0.inkSecondary })
            }
            if let text = note(item.note, t) { Text(text).qhText({ $0.callout }, color: { $0.inkSecondary }) }
            if let link = item.url, let url = URL(string: link) {
                Link(destination: url) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.up.right.square")
                        Text(SourcesDataKt.displayHost(url: link))
                    }
                    .qhText({ $0.label }, color: { $0.primary })
                }
                .accessibilityLabel("\(t.openLink): \(SourcesDataKt.displayHost(url: link))")
                .padding(.top, 2)
            }
        }
        .padding(QhSpacing.card)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
    }
}
