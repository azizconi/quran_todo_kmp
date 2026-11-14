import ComposeApp
import SwiftUI

/// "Downloads and storage" (Android `DownloadsScreen`): options, the queue, storage, translations, then the plan, mushaf pages,
/// 30 juz and 114 surahs (with a search). Native `List` in grouped sections in our colours; swipe a row to delete what it holds.
struct DownloadsScreen: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @State private var model = DownloadsModel()
    @State private var query = ""
    @State private var ask: DownloadsAsk?
    private var env: AppEnvironment { AppEnvironment.shared }

    var body: some View {
        let d = copy.s7Downloads
        let ui = model.ui.value
        List {
            optionsSection(d, ui)
            if let queue = ui.queue { queueSection(d, queue, ui) }
            storageSection(d, ui.storage)
            translationsSection(ui)
            if ui.loaded {
                if let planAll = ui.planAll {
                    Section { rows([planAll] + ui.planRows) } header: { header(d.sectionPlan) }
                }
                Section { rows(ui.juz) } header: { header(d.sectionJuz) }
                pagesSection(ui)
                surahsSection(d, ui)
            }
        }
        .listStyle(.insetGrouped)
        .listSectionSpacing(QhSpacing.x4)
        .navigationTitle(d.title)
        .navigationBarTitleDisplayMode(.large)
        .qhNavigationStyle()
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .automatic), prompt: d.searchPlaceholder)
        .task { model.bind(env: env) }
        .onAppear { model.vm.refreshCache() }
        .confirmationDialog(askTitle(d), isPresented: askBinding, titleVisibility: .visible, presenting: ask) { current in
            Button(askConfirm(current, d), role: .destructive) { confirm(current) }
            Button(d.cancel, role: .cancel) {}
        } message: { current in
            Text(askMessage(current, d, ui))
        }
    }

    // MARK: Sections

    private func header(_ text: String) -> some View {
        QhSectionHeader(text).padding(.top, QhSpacing.x2)
    }

    private func optionsSection(_ d: DownloadsCopy, _ ui: DownloadsUi) -> some View {
        Section {
            QhValueRow(title: d.reciter, value: ui.reciter.shortName(language: env.language), description: d.reciterHint,
                       icon: "waveform", iconTint: qh.colors.gold).card(qh)
            QhSwitchRow(title: d.wifiOnly, isOn: Binding(get: { ui.wifiOnly }, set: { model.vm.setWifiOnly(value: $0) }),
                        description: d.wifiOnlyHint, icon: "wifi", iconTint: qh.colors.primary).card(qh)
            QhSwitchRow(title: d.withTranslationLabel(languageName: env.language.nativeLabel),
                        isOn: Binding(get: { ui.withTranslation }, set: { model.vm.setWithTranslation(value: $0) }),
                        description: d.withTranslationHint, icon: "character.book.closed", iconTint: qh.colors.due).card(qh)
        }
    }

    private func queueSection(_ d: DownloadsCopy, _ queue: QueueUi, _ ui: DownloadsUi) -> some View {
        let pages = copy.s7Pages
        let failure = [d.failureLabel(ayahs: queue.failedAyahs, translations: queue.failedTranslations),
                       pages.failedPages(n: queue.failedPages)].filter { !$0.isEmpty }.joined(separator: " · ")
        let status: String? = {
            guard queue.hasActive else { return nil }
            if queue.paused { return d.queuePaused }
            if queue.phase == .waiting { return ui.wifiOnly ? d.queueWaitingWifi : d.queueWaitingNetwork }
            return nil
        }()
        let details = [status,
                       queue.remainingBytes.map { d.queueRemainingLabel(bytes: $0.int64Value) },
                       queue.waiting > 0 ? d.queueWaitingLabel(n: queue.waiting) : nil].compactMap { $0 }.joined(separator: " · ")
        return Section {
            VStack(alignment: .leading, spacing: QhSpacing.x2) {
                if queue.hasActive {
                    Text(d.queueTitleLabel(title: queue.title, done: queue.done, total: queue.total))
                        .qhText({ $0.title2 }).qhTabular().lineLimit(2)
                    QhLinearProgress(progress: queue.total > 0 ? Double(queue.done) / Double(queue.total) : 0)
                    if !details.isEmpty { Text(details).qhText({ $0.callout }, color: { $0.inkSecondary }) }
                }
                if !failure.isEmpty {
                    HStack {
                        Text(failure).qhText({ $0.callout }, color: { $0.danger })
                        Spacer(minLength: QhSpacing.x2)
                        QhButton(d.retry, variant: .tonal, size: .s) { model.vm.retry() }
                    }
                }
                HStack(spacing: QhSpacing.x2) {
                    if queue.hasActive {
                        QhButton(queue.paused ? d.resume : d.pauseAll, variant: .tonal, size: .s,
                                 icon: queue.paused ? "play.fill" : "pause.fill") {
                            if queue.paused { model.vm.resume() } else { model.vm.pauseAll() }
                        }
                    }
                    QhButton(d.cancelAll, variant: .text, size: .s) { ask = .cancelAll }
                }
            }
            .padding(.vertical, QhSpacing.x2)
            .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: queue.hasActive)
            .card(qh)
        }
    }

    private func storageSection(_ d: DownloadsCopy, _ s: StorageUi) -> some View {
        Section {
            storageLine(title: d.storageDownloads, value: d.sizeLabel(bytes: s.downloadsBytes), hint: d.storageDownloadsHint,
                        action: d.storageDeleteAll, enabled: s.downloadsBytes > 0, icon: "arrow.down.circle", tint: qh.colors.primary) { ask = .deleteAll }
            storageLine(title: d.storageCache, value: d.sizeLabel(bytes: s.cacheBytes), hint: d.storageCacheHint,
                        action: d.storageClear, enabled: s.cacheBytes > 0, icon: "trash.slash", tint: qh.colors.gold) { ask = .clearCache }
            storageLine(title: d.storageRecordings, value: d.sizeLabel(bytes: s.recordingsBytes), hint: d.storageRecordingsHint,
                        action: d.storageDeleteRecordings, enabled: s.recordingsBytes > 0, icon: "mic", tint: qh.colors.due) { ask = .deleteRecordings }
            QhValueRow(title: d.storageFree, value: d.sizeLabel(bytes: s.freeBytes), icon: "internaldrive", iconTint: qh.colors.learned).card(qh)
        } header: { header(d.sectionStorage) }
    }

    private func storageLine(title: String, value: String, hint: String, action: String, enabled: Bool, icon: String,
                             tint: Color, onAction: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            HStack(spacing: QhSpacing.x3) {
                QhIconTile(icon: icon, tint: tint)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).qhText({ $0.title2 })
                    Text(hint).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
                Spacer(minLength: QhSpacing.x2)
                Text(value).qhText({ $0.title2 }).qhTabular().lineLimit(1)
            }
            QhButton(action, variant: .tonal, size: .s, action: onAction).disabled(!enabled)
        }
        .padding(.vertical, QhSpacing.x2)
        .accessibilityElement(children: .contain)
        .card(qh)
    }

    private func translationsSection(_ ui: DownloadsUi) -> some View {
        let t = copy.s7Translation
        let d = copy.s7Downloads
        return Section {
            ForEach(ui.translations, id: \.key) { row in translationRow(row, t, d) }
        } header: {
            header(t.downloadsSection)
        } footer: {
            Text(t.downloadsHint).qhText({ $0.callout }, color: { $0.inkSecondary })
        }
    }

    private func pagesSection(_ ui: DownloadsUi) -> some View {
        let p = copy.s7Pages
        let all = (ui.pagesAll.map { [$0] } ?? []) + ui.pagesJuz
        return Section {
            rows(all)
        } header: {
            header(p.downloadsSection)
        } footer: {
            Text(p.downloadsHint).qhText({ $0.callout }, color: { $0.inkSecondary })
        }
    }

    private func surahsSection(_ d: DownloadsCopy, _ ui: DownloadsUi) -> some View {
        let shown: [RowUi] = {
            guard !query.trimmingCharacters(in: .whitespaces).isEmpty, let index = ui.searchIndex else { return ui.surahs }
            let byNumber = Dictionary(uniqueKeysWithValues: ui.surahs.map { (Int($0.number), $0) })
            return index.search(query: query).compactMap { byNumber[$0.intValue] }
        }()
        return Section {
            if shown.isEmpty {
                Text(d.searchEmpty).qhText({ $0.body }, color: { $0.inkSecondary }).frame(maxWidth: .infinity).padding(.vertical, QhSpacing.x4).card(qh)
            } else {
                rows(shown)
            }
        } header: { header(d.sectionSurahs) }
    }

    private func rows(_ list: [RowUi]) -> some View {
        ForEach(list, id: \.id) { row in
            DownloadRowView(row: row, copy: copy.s7Downloads, pages: copy.s7Pages, language: env.language,
                            download: { model.download(row.id) },
                            pause: { model.vm.pauseAll() }, resume: { model.vm.resume() },
                            cancel: { model.vm.cancel(rowId: row.id) },
                            delete: { ask = .deleteRow(row) })
                .card(qh)
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    if row.hasFiles {
                        Button(role: .destructive) { ask = .deleteRow(row) } label: { Label(copy.s7Downloads.actionDelete, systemImage: "trash") }
                    }
                }
        }
    }

    private func translationRow(_ row: TranslationRowUi, _ t: TranslationCopy, _ d: DownloadsCopy) -> some View {
        let status: String
        switch onEnum(of: row.queue) {
        case .waiting: status = d.rowQueued
        case let .active(a): status = d.rowDownloadingLabel(done: a.done, total: a.total)
        case let .paused(p): status = "\(d.queuePaused) · \(d.rowDownloadingLabel(done: p.done, total: p.total))"
        case .failed: status = t.downloadsFailed
        case .none:
            status = row.complete ? (row.updateAvailable ? t.updateAvailable : d.rowCompleteLabel(bytes: row.wireBytes))
                : t.offer(size: d.sizeLabel(bytes: row.wireBytes))
        }
        let busy: Bool = { if case .none = onEnum(of: row.queue) { return false } else { return true } }()
        return HStack(spacing: QhSpacing.x3) {
            QhIconTile(icon: "character.book.closed", tint: qh.colors.primary).frame(width: 40, height: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.name).qhText({ $0.title2 }).lineLimit(2)
                Text(row.author.isEmpty ? status : "\(row.author) · \(status)")
                    .qhText({ $0.callout }, color: { row.queueIsFailed ? $0.danger : $0.inkSecondary }).lineLimit(2)
            }
            Spacer(minLength: QhSpacing.x2)
            if busy {
                QhIconButton(icon: "xmark", label: "\(d.actionCancel): \(row.name)", filled: false, tint: qh.colors.inkSecondary) {
                    model.vm.cancel(rowId: DownloadRequest.Companion.shared.translationId(editionKey: row.key))
                }
            } else if row.complete {
                QhIconButton(icon: "trash", label: "\(d.actionDelete): \(row.name)", filled: false, tint: qh.colors.inkSecondary) {
                    ask = .deleteTranslation(row)
                }
            } else {
                QhIconButton(icon: "arrow.down.circle", label: "\(d.actionDownload): \(row.name)", filled: false, tint: qh.colors.primary) {
                    model.downloadTranslation(row.key)
                }
            }
        }
        .padding(.vertical, QhSpacing.x1)
        .accessibilityElement(children: .combine)
        .card(qh)
    }

    // MARK: Dialogs

    private var askBinding: Binding<Bool> {
        Binding(get: { ask != nil }, set: { if !$0 { ask = nil } })
    }

    private func askTitle(_ d: DownloadsCopy) -> String {
        switch ask {
        case .deleteRow, .deleteTranslation: return d.deleteTitle
        case .deleteAll: return d.deleteAllTitle
        case .deleteRecordings: return d.recordingsDeleteTitle
        case .clearCache: return d.cacheClearTitle
        case .cancelAll: return d.cancelAllTitle
        case nil: return ""
        }
    }

    private func askConfirm(_ a: DownloadsAsk, _ d: DownloadsCopy) -> String {
        switch a {
        case .deleteRow, .deleteTranslation: return d.deleteConfirm
        case .deleteAll: return d.deleteAllConfirm
        case .deleteRecordings: return d.recordingsDeleteConfirm
        case .clearCache: return d.cacheClearConfirm
        case .cancelAll: return d.cancelAllConfirm
        }
    }

    private func askMessage(_ a: DownloadsAsk, _ d: DownloadsCopy, _ ui: DownloadsUi) -> String {
        switch a {
        case let .deleteRow(row): return d.deleteBodyLabel(title: row.displayTitle(d), bytes: row.bytesOnPhone)
        case let .deleteTranslation(row): return copy.s7Translation.deleteBody(name: row.name)
        case .deleteAll: return d.deleteAllBodyLabel(bytes: ui.storage.downloadsBytes)
        case .deleteRecordings: return d.recordingsDeleteBodyLabel(bytes: ui.storage.recordingsBytes)
        case .clearCache: return d.cacheClearBody
        case .cancelAll: return d.cancelAllBody
        }
    }

    private func confirm(_ a: DownloadsAsk) {
        switch a {
        case let .deleteRow(row): model.vm.delete(rowId: row.id)
        case let .deleteTranslation(row): model.vm.deleteTranslation(key: row.key)
        case .deleteAll: model.vm.deleteAll()
        case .deleteRecordings: model.vm.deleteRecordings()
        case .clearCache: model.vm.clearCache()
        case .cancelAll: model.vm.cancelAll()
        }
    }
}

extension View {
    /// A list row on the card surface with our separator colour.
    func card(_ qh: QhTheme) -> some View {
        self
            .listRowBackground(qh.colors.surface)
            .listRowSeparatorTint(qh.colors.hairline)
            .listRowInsets(EdgeInsets(top: 0, leading: QhSpacing.card, bottom: 0, trailing: QhSpacing.card))
    }
}

extension TranslationRowUi {
    var queueIsFailed: Bool { if case .failed = onEnum(of: queue) { return true } else { return false } }
}
