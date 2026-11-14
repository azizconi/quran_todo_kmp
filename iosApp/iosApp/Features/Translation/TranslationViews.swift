import ComposeApp
import Observation
import SwiftUI

/// The edition chosen for the language of the interface (`TranslationChoice`), as an `@Observable` that follows every choice.
@MainActor
@Observable
final class TranslationChoiceModel {
    static let shared = TranslationChoiceModel()
    private(set) var revision = 0
    @ObservationIgnored private var task: Task<Void, Never>?

    private init() {
        task = Task { [weak self] in
            do { for try await _ in TranslationChoice.shared.store.revision { self?.revision += 1 } } catch {}
        }
    }

    func edition(for language: AppLanguage) -> TranslationEdition {
        _ = revision
        return TranslationChoice.shared.store.editionFor(language: language)
    }
}

/// The row "Translation: Kuliev" of the "Aa" sheet (F4a): it opens the sheet of editions.
struct TranslationEditionRow: View {
    @Environment(Copy.self) private var copy
    @State private var open = false

    var body: some View {
        let edition = TranslationChoiceModel.shared.edition(for: copy.language)
        QhNavRow(title: copy.translation.row(name: edition.name.of(language: copy.language))) { open = true }
            .sheet(isPresented: $open) { TranslationEditionSheet() }
    }
}

/// The editions of the language of the interface first, below them "Other languages" (folded), where a translation in another
/// language can be chosen without the interface changing. An edition that is stored whole says so.
struct TranslationEditionSheet: View {
    @Environment(\.qh) private var qh
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    @State private var othersOpen: Bool
    @State private var downloaded: Set<String> = []

    init() {
        let language = IosUi.shared.language.value
        _othersOpen = State(initialValue: TranslationChoice.shared.store.editionFor(language: language).language != language)
    }

    var body: some View {
        let t = copy.translation
        let language = copy.language
        let chosen = TranslationChoiceModel.shared.edition(for: language)
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                Text(t.sheetTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                Text(t.sheetHint).qhText({ $0.callout }, color: { $0.inkSecondary })
                group(TranslationCatalog.shared.editionsOf(language: language), chosen: chosen, language: language)
                Button {
                    QhHaptics.shared.perform(.tick)
                    withQhAnimation(QhMotion.gentle) { othersOpen.toggle() }
                } label: {
                    HStack {
                        Text(t.otherLanguages).qhText({ $0.title2 })
                        Spacer()
                        Image(systemName: "chevron.down").rotationEffect(.degrees(othersOpen ? 180 : 0)).foregroundStyle(qh.colors.inkSecondary)
                    }
                    .frame(minHeight: 48).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if othersOpen {
                    ForEach(AppLanguage.allCases.filter { $0 != language }, id: \.name) { other in
                        let editions = TranslationCatalog.shared.editionsOf(language: other)
                        if !editions.isEmpty {
                            Text(other.displayName).qhText({ $0.title2 }).accessibilityAddTraits(.isHeader)
                            group(editions, chosen: chosen, language: language)
                        }
                    }
                }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5).padding(.bottom, QhSpacing.x8)
        }
        .qhSheetStyle([.large])
        .task { downloaded = Set((try? await IosReaderBridge.shared.downloadedTranslations()) ?? []) }
    }

    private func group(_ editions: [TranslationEdition], chosen: TranslationEdition, language: AppLanguage) -> some View {
        QhCard(padding: 0) {
            ForEach(Array(editions.enumerated()), id: \.element.key) { index, edition in
                if index > 0 { QhRowDivider(startIndent: QhSpacing.card) }
                row(edition, selected: edition.key == chosen.key, language: language)
            }
        }
    }

    private func row(_ edition: TranslationEdition, selected: Bool, language: AppLanguage) -> some View {
        let t = copy.translation
        let name = edition.name.of(language: language)
        let author = edition.author.of(language: language)
        let stored = downloaded.contains(edition.key)
        return HStack(spacing: QhSpacing.x3) {
            VStack(alignment: .leading, spacing: 2) {
                Text(edition.scriptDiffersFromInterface ? "\(name) \u{00B7} \(t.cyrillicMark)" : name).qhText({ $0.title2 }).lineLimit(2)
                if author != name { Text(author).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(2) }
                if edition.origin == TranslationOrigin.quranEnc {
                    Text(t.quranEnc(version: edition.version)).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
                }
                if stored {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill").font(.system(size: 13)).foregroundStyle(qh.colors.learned)
                        Text(t.downloadedBadge).qhText({ $0.callout }, color: { $0.learned })
                    }
                }
            }
            Spacer(minLength: 0)
            Image(systemName: selected ? "checkmark.circle.fill" : "circle").font(.system(size: 22))
                .foregroundStyle(selected ? qh.colors.primary : qh.colors.inkTertiary)
        }
        .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3).frame(minHeight: 60)
        .contentShape(Rectangle())
        .pressable(haptic: .segment, scale: 0.99) {
            TranslationChoice.shared.store.select(language: language, edition: edition)
            dismiss()
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Search "In translation"

/// The tab "In translation" of the search (F4a): which translation is searched, then the offer to download it whole, "Downloading...",
/// the failure with "Retry", the hint "type two letters", the results (surah:ayah and the line with the words marked) or "Nothing
/// found". The text is kept in memory only while this view is on screen (`setActive`).
@MainActor
@Observable
final class TranslationSearchModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.translationSearch() }
    let ui: FlowState<TranslationSearchUi>
    var vm: TranslationSearchViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        holder.vm.setLanguage(next: IosUi.shared.language.value)
    }
}

struct TranslationSearchView: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(Router.self) private var router
    @State private var model = TranslationSearchModel()
    let query: String

    var body: some View {
        let t = copy.translation
        let ui = model.ui.value
        let language = copy.language
        let name = ui.edition.name.of(language: language)
        LazyVStack(alignment: .leading, spacing: 0) {
            Text(t.searchIn(name: name)).qhText({ $0.callout }, color: { $0.inkSecondary })
                .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x2)
            switch ui.phase {
            case TranslationSearchPhase.needDownload:
                card(t.needDownload(size: ByteCountFormatter.string(fromByteCount: ui.edition.wireBytes, countStyle: .file)), action: t.download) { model.vm.download() }
            case TranslationSearchPhase.downloading: card(t.downloading)
            case TranslationSearchPhase.failed: card(t.downloadFailed, action: t.retry, failed: true) { model.vm.retry() }
            case TranslationSearchPhase.loading: card(t.loading)
            default:
                let hits = ui.result.hits
                if query.trimmingCharacters(in: .whitespaces).count < 2 || !ui.searched {
                    Text(t.searchTooShort).qhText({ $0.body }, color: { $0.inkSecondary }).padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x2)
                } else if hits.isEmpty {
                    QhEmptyState(title: t.noResultsTitle, message: t.noResultsBody)
                } else {
                    ForEach(Array(hits.enumerated()), id: \.offset) { _, hit in row(hit) }
                    if ui.result.truncated {
                        Text(t.truncatedLabel(n: Int32(hits.count))).qhText({ $0.callout }, color: { $0.inkSecondary })
                            .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
                    }
                }
            }
        }
        .onAppear { model.vm.setActive(value: true) }
        .onDisappear { model.vm.setActive(value: false) }
        .onChange(of: query) { _, text in model.vm.setQuery(value: text) }
        .onChange(of: copy.language) { _, l in model.vm.setLanguage(next: l) }
        .task { model.vm.setQuery(value: query) }
    }

    private func card(_ text: String, action: String? = nil, failed: Bool = false, _ onAction: @escaping () -> Void = {}) -> some View {
        QhCard {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                Text(text).qhText({ $0.body }, color: { failed ? $0.danger : $0.ink })
                if let action { QhButton(action, variant: .tonal, size: .m, action: onAction) }
            }
        }
        .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x1)
    }

    private func row(_ hit: TranslationHit) -> some View {
        let surahName = SimilarService.shared.surahTitle(hit.surah, language: copy.language)
        let title = "\(surahName) \(hit.surah):\(hit.ayahInSurah)"
        var marked = AttributedString(hit.text)
        for range in hit.ranges {
            let start = Int(range.first), end = min(Int(range.last) + 1, hit.text.utf16.count)
            if end > start, let r = Range(NSRange(location: start, length: end - start), in: marked) {
                marked[r].font = .custom("Manrope-Bold", size: 16)
                marked[r].backgroundColor = qh.colors.primaryContainer
                marked[r].foregroundColor = qh.colors.primary
            }
        }
        return VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).qhText({ $0.title2 }, color: { $0.primary }).lineLimit(1)
                Text(marked).qhText({ $0.body }).lineLimit(6)
            }
            .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x3).frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            QhRowDivider(startIndent: QhSpacing.screen)
        }
        .contentShape(Rectangle())
        .pressable(scale: 0.995) { router.openReader(Int(hit.surah), ayah: Int(hit.ayahInSurah), origin: "search") }
        .accessibilityElement(children: .combine)
    }
}
