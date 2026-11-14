import ComposeApp
import SwiftUI

extension RowUi {
    /// Something is on the phone for this row (partly or whole): a swipe can delete it.
    var hasFiles: Bool {
        switch onEnum(of: state) {
        case .notDownloaded: return false
        default: return true
        }
    }

    var bytesOnPhone: Int64 {
        switch onEnum(of: state) {
        case .notDownloaded: return 0
        case let .partial(p): return p.bytes
        case let .complete(c): return c.bytes
        }
    }

    func displayTitle(_ d: DownloadsCopy) -> String {
        switch kind {
        case .juz: return d.juzTitleLabel(juz: number)
        case .plan: return d.planAll
        default: return name
        }
    }
}

/// One row of the lists: a badge, the title with the Arabic name, a line and the buttons that fit the state.
struct DownloadRowView: View {
    @Environment(\.qh) private var qh
    let row: RowUi
    let copy: DownloadsCopy
    let pages: PagesCopy
    let language: AppLanguage
    let download: () -> Void
    let pause: () -> Void
    let resume: () -> Void
    let cancel: () -> Void
    let delete: () -> Void

    private struct Line {
        var text: String
        var done = false
        var failed = false
        var progress: Double?
    }

    var body: some View {
        let title = row.displayTitle(copy)
        let line = self.line()
        HStack(spacing: QhSpacing.x3) {
            badge
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: QhSpacing.x2) {
                    Text(title).qhText({ $0.title2 }).lineLimit(2)
                    if !row.arabicName.isEmpty {
                        Text(row.arabicName).qhArabic(qh.type.quranInline(LessonArabic.face), color: qh.colors.primary).lineLimit(1)
                    }
                }
                HStack(spacing: 4) {
                    if line.done {
                        Image(systemName: "checkmark.circle.fill").font(.system(size: 13)).foregroundStyle(qh.colors.learned)
                    }
                    Text(line.text).qhText({ $0.callout }, color: { line.failed ? $0.danger : $0.inkSecondary }).qhTabular().lineLimit(2)
                }
                if let progress = line.progress {
                    QhLinearProgress(progress: progress, height: 4).padding(.top, 2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(title), \(line.text)")
            buttons(title)
        }
        .padding(.vertical, QhSpacing.x2)
        .frame(minHeight: 64)
    }

    @ViewBuilder private var badge: some View {
        switch row.kind {
        case .surah:
            SurahNumberBadge(number: Int(row.number), status: .notStarted, size: 40)
        case .juz:
            ring(Int(row.number))
        case .plan:
            QhIconTile(icon: "arrow.down.circle", tint: qh.colors.primary).frame(width: 40, height: 40)
        case .translation:
            QhIconTile(icon: "character.book.closed", tint: qh.colors.primary).frame(width: 40, height: 40)
        case .pages:
            if row.number == 0 {
                QhIconTile(icon: "book", tint: qh.colors.primary).frame(width: 40, height: 40)
            } else {
                ring(Int(row.number))
            }
        default:
            EmptyView()
        }
    }

    private func ring(_ n: Int) -> some View {
        Text("\(n)").qhText({ $0.label }).qhTabular()
            .frame(width: 40, height: 40)
            .overlay(Circle().strokeBorder(qh.colors.gold.opacity(0.7), lineWidth: 1.5))
            .accessibilityHidden(true)
    }

    private func line() -> Line {
        func fraction(_ done: Int32, _ total: Int32) -> Double { total > 0 ? Double(done) / Double(total) : 0 }
        let about: String
        switch row.kind {
        case .surah: about = HomeCopyKt.ayahCountLabel(n: row.ayahCount, language: language)
        case .juz: about = copy.juzSurahsLabel(first: row.firstSurah, last: row.lastSurah)
        case .plan: about = copy.planAllSubtitleLabel(surahs: row.surahCount)
        case .pages: about = pages.pagesCount(n: row.pageCount)
        default: about = ""
        }
        let untouched = [row.estimateBytes.map { copy.estimateLabel(bytes: $0.int64Value) }, about.isEmpty ? nil : about]
            .compactMap { $0 }.joined(separator: " · ")
        let onDevice: Line
        switch onEnum(of: row.state) {
        case let .complete(c): onDevice = Line(text: copy.rowCompleteLabel(bytes: c.bytes), done: true)
        case let .partial(p): onDevice = Line(text: copy.rowPartialLabel(done: p.done, total: p.total, bytes: p.bytes))
        case .notDownloaded: onDevice = Line(text: untouched)
        }
        switch onEnum(of: row.queue) {
        case .none: return onDevice
        case .waiting: return Line(text: copy.rowQueued)
        case let .active(a): return Line(text: copy.rowDownloadingLabel(done: a.done, total: a.total), progress: fraction(a.done, a.total))
        case let .paused(p):
            return Line(text: "\(copy.queuePaused) · \(copy.rowDownloadingLabel(done: p.done, total: p.total))", progress: fraction(p.done, p.total))
        case .failed: return Line(text: onDevice.text, failed: true)
        }
    }

    @ViewBuilder private func buttons(_ title: String) -> some View {
        switch onEnum(of: row.queue) {
        case .active:
            button("pause.fill", copy.actionPause, title, qh.colors.primary, pause)
        case .paused:
            button("play.fill", copy.actionResume, title, qh.colors.primary, resume)
            button("xmark", copy.actionCancel, title, qh.colors.inkSecondary, cancel)
        case .waiting:
            button("xmark", copy.actionCancel, title, qh.colors.inkSecondary, cancel)
        case .failed:
            button("arrow.down.circle", copy.actionDownload, title, qh.colors.danger, download)
        case .none:
            switch onEnum(of: row.state) {
            case .notDownloaded:
                button("arrow.down.circle", copy.actionDownload, title, qh.colors.primary, download)
            case .partial:
                button("arrow.down.circle", copy.actionDownload, title, qh.colors.primary, download)
                button("trash", copy.actionDelete, title, qh.colors.inkSecondary, delete)
            case .complete:
                button("trash", copy.actionDelete, title, qh.colors.inkSecondary, delete)
            }
        }
    }

    private func button(_ icon: String, _ label: String, _ title: String, _ tint: Color, _ action: @escaping () -> Void) -> some View {
        QhIconButton(icon: icon, label: "\(label): \(title)", filled: false, tint: tint, action: action)
    }
}
