import ComposeApp
import SwiftUI
import UIKit

/// How the report goes out: the card picture (with the text as its caption), a PDF, or the text alone.
private enum ReportFormat: Int { case image, pdf, text }

/// The choice of the report (`ReportSection.kt`): the format ("Image" / "PDF" / "Text") and the period: "Past week", "Past month" or
/// "Custom period" (two date pickers, only days up to today). Pressing a period prepares the report from the progress the screen sees
/// (`ProgressViewModel.prepareReport`) and opens the share sheet; the pressed button shows a spinner meanwhile and the sheet closes
/// when the share sheet opened. The PDF is made by the Kotlin `ReportExporter` (services), the card picture by `ImageRenderer`.
struct ReportSheet: View {
    let model: ProgressModel
    let today: Int
    let onClose: () -> Void
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    @Environment(\.qh) private var qh
    @State private var format = ReportFormat.image.rawValue
    @State private var busy: ReportPeriod?
    @State private var picking = false

    var body: some View {
        let p = copy.progress
        let a = copy.analyticsCopy
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(p.reportAction).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                Text(p.sheetBody).qhText({ $0.body }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1)
                Text(a.formatTitle).qhText({ $0.label }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x3)
                QhSegmentedControl(options: [a.formatImage, a.formatPdf, a.formatText], selection: Binding(
                    get: { format }, set: { if busy == nil { format = $0 } }))
                    .padding(.top, QhSpacing.x2)
                VStack(spacing: QhSpacing.x2) {
                    QhButton(p.periodWeek, size: .l, loading: busy == .week, fullWidth: true) { pick(.week) }
                        .disabled(busy != nil && busy != .week)
                    QhButton(p.periodMonth, variant: .tonal, size: .l, loading: busy == .month, fullWidth: true) { pick(.month) }
                        .disabled(busy != nil && busy != .month)
                    QhButton(a.periodCustom, variant: .tonal, size: .l, loading: busy == .custom, fullWidth: true) { picking = true }
                        .disabled(busy != nil && busy != .custom)
                }
                .padding(.top, QhSpacing.x4)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .sheet(isPresented: $picking) {
            ReportRangePicker(today: today) { from, to in
                picking = false
                pick(.custom, from: from, to: to)
            } onCancel: { picking = false }
        }
    }

    private func pick(_ period: ReportPeriod, from: Int? = nil, to: Int? = nil) {
        guard busy == nil else { return }
        busy = period
        Task {
            defer { busy = nil }
            if await share(period, from: from, to: to) {
                onClose()
            } else {
                env.toaster.show(copy.progress.shareFailed, kind: .error)
            }
        }
    }

    /// Prepares the report and hands it to the system share sheet; true when the sheet was opened.
    @MainActor
    private func share(_ period: ReportPeriod, from: Int?, to: Int?) async -> Bool {
        do {
            guard let prepared = try await model.vm.prepareReport(period: period, fromDay: from.map { KotlinInt(int: Int32($0)) },
                                                                    toDay: to.map { KotlinInt(int: Int32($0)) }) else { return false }
            let toDay = Int(prepared.toDay)
            let name = "quran-hifz-report-\(Self.isoDay(toDay))"
            var items: [Any] = []
            switch ReportFormat(rawValue: format) ?? .image {
            case .image:
                guard let url = ReportFiles.writePNG(doc: prepared.doc, name: name) else { return false }
                items = [prepared.text, url]
            case .pdf:
                ReportFiles.clear()
                guard let exporter = IosLessonBridge.shared.reportExporter(),
                      let location = try await exporter.createPdf(doc: prepared.doc, fileName: "\(name).pdf"),
                      let url = URL(string: location) ?? URL(fileURLWithPath: location) as URL? else { return false }
                items = [prepared.text, url]
            case .text:
                ReportFiles.clear()
                items = [prepared.text]
            }
            model.vm.onReportShared(period: period)
            return Self.present(items)
        } catch {
            AppTelemetry.shared.logError(throwable: error.asKotlin, context: "progress_report_failed", params: [:])
            return false
        }
    }

    private static func isoDay(_ epochDay: Int) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400)
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"; f.timeZone = TimeZone(identifier: "UTC"); f.locale = Locale(identifier: "en_US_POSIX")
        return f.string(from: date)
    }

    @MainActor
    private static func present(_ items: [Any]) -> Bool {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first(where: { $0.activationState == .foregroundActive }),
              var top = scene.keyWindow?.rootViewController else { return false }
        while let next = top.presentedViewController { top = next }
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.popoverPresentationController?.sourceView = top.view
        controller.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.maxY - 40, width: 1, height: 1)
        top.present(controller, animated: true)
        return true
    }
}

private extension Error {
    var asKotlin: KotlinThrowable { KotlinThrowable(message: localizedDescription) }
}

/// Files of the report: one kept in the temp folder, the previous deleted.
enum ReportFiles {
    private static var folder: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("report", isDirectory: true)
    }

    static func clear() { try? FileManager.default.removeItem(at: folder) }

    @MainActor
    static func writePNG(doc: ReportDoc, name: String) -> URL? {
        clear()
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let theme = QhTheme.resolve(dark: false, palette: .forest)
        let renderer = ImageRenderer(content: ReportCardView(doc: doc).environment(\.qh, theme).environment(\.colorScheme, .light))
        renderer.scale = 3
        guard let data = renderer.uiImage?.pngData() else { return nil }
        let url = folder.appendingPathComponent("\(name).png")
        do { try data.write(to: url) } catch { return nil }
        return url
    }
}

/// The date range: two day pickers, only days up to today; one tapped day is a one-day period.
private struct ReportRangePicker: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let today: Int
    let onConfirm: (Int, Int) -> Void
    let onCancel: () -> Void
    @State private var from: Date
    @State private var to: Date

    init(today: Int, onConfirm: @escaping (Int, Int) -> Void, onCancel: @escaping () -> Void) {
        self.today = today; self.onConfirm = onConfirm; self.onCancel = onCancel
        let end = Self.date(today)
        _from = State(initialValue: Calendar.current.date(byAdding: .day, value: -6, to: end) ?? end)
        _to = State(initialValue: end)
    }

    private static func date(_ epochDay: Int) -> Date {
        var c = Calendar(identifier: .gregorian); c.timeZone = .current
        let utc = Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400)
        var u = Calendar(identifier: .gregorian); u.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        let parts = u.dateComponents([.year, .month, .day], from: utc)
        return c.date(from: parts) ?? utc
    }

    private func epochDay(_ date: Date) -> Int {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        var u = Calendar(identifier: .gregorian); u.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        let utc = u.date(from: parts) ?? date
        return Int((utc.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    var body: some View {
        let a = copy.analyticsCopy
        let limit = Self.date(today)
        NavigationStack {
            Form {
                DatePicker("", selection: $from, in: ...to, displayedComponents: .date).datePickerStyle(.graphical).labelsHidden()
                DatePicker("", selection: $to, in: from...limit, displayedComponents: .date).datePickerStyle(.graphical).labelsHidden()
            }
            .scrollContentBackground(.hidden)
            .background(qh.colors.bg)
            .navigationTitle(a.pickRangeTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(a.pickRangeCancel, action: onCancel) }
                ToolbarItem(placement: .confirmationAction) { Button(a.pickRangeConfirm) { onConfirm(epochDay(from), epochDay(to)) } }
            }
        }
        .presentationDetents([.large])
    }
}

// MARK: - The card picture

/// The picture of the report: the same summary as the text (`ReportDoc`) as a card in the style of the app: warm paper, the word "hifz" in
/// calligraphy, the ring with the memorized share, the rows. Always the light theme. The only ornament is a hairline with a dot.
struct ReportCardView: View {
    @Environment(\.qh) private var qh
    let doc: ReportDoc

    var body: some View {
        let c = qh.colors
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                Text(Calligraphy.hifz).font(.custom(QhQuranFace.amiri.postScriptName, fixedSize: 44)).foregroundStyle(c.gold)
                    .environment(\.layoutDirection, .rightToLeft).padding(.bottom, 1)
                Text("QURAN HIFZ").font(.custom("Manrope-Bold", fixedSize: 9.3)).tracking(1.7).foregroundStyle(c.inkSecondary).padding(.bottom, 7)
                Text(doc.title).font(.custom("Manrope-ExtraBold", fixedSize: 20)).foregroundStyle(c.ink).multilineTextAlignment(.center).padding(.bottom, 3)
                Text(doc.period).font(.custom("Manrope-Medium", fixedSize: 12)).foregroundStyle(c.inkSecondary).padding(.bottom, 13)
                Divider().frame(height: 0).overlay { DotRule(color: c.gold.opacity(0.6)) }.padding(.bottom, 13)
                ZStack {
                    Circle().stroke(c.primary.opacity(0.12), lineWidth: 12)
                    Circle().trim(from: 0, to: doc.fraction > 0 ? max(CGFloat(doc.fraction), 0.017) : 0)
                        .stroke(c.learned, style: StrokeStyle(lineWidth: 12, lineCap: .round)).rotationEffect(.degrees(-90))
                    Text(doc.percentText).font(.custom("Manrope-ExtraBold", fixedSize: 29)).foregroundStyle(c.ink)
                }
                .frame(width: 107, height: 107).padding(.bottom, 9)
                Text(doc.caption).font(.custom("Manrope-SemiBold", fixedSize: 12)).foregroundStyle(c.ink).multilineTextAlignment(.center).padding(.bottom, 12)
            }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(doc.rows.enumerated()), id: \.offset) { index, row in
                    Rectangle().fill(c.hairline).frame(height: 0.7).padding(.bottom, 9)
                    Text(row.label).font(.custom("Manrope-SemiBold", fixedSize: 10.7)).foregroundStyle(c.inkSecondary).padding(.bottom, 2)
                    if !row.value.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text(row.value).font(.custom("Manrope-Bold", fixedSize: 15.3)).foregroundStyle(c.ink).padding(.bottom, 2)
                    }
                    ForEach(Array(row.lines.enumerated()), id: \.offset) { _, line in
                        Text(row.bulleted ? "•  \(line)" : line).font(.custom("Manrope-Medium", fixedSize: 11.7)).foregroundStyle(c.ink)
                            .fixedSize(horizontal: false, vertical: true).padding(.bottom, 1)
                    }
                    Spacer().frame(height: index == doc.rows.count - 1 ? 0 : 9)
                }
                if !doc.strip.isEmpty {
                    Spacer().frame(height: 9)
                    Rectangle().fill(c.hairline).frame(height: 0.7).padding(.bottom, 9)
                    Text(doc.stripLabel).font(.custom("Manrope-SemiBold", fixedSize: 10.7)).foregroundStyle(c.inkSecondary).padding(.bottom, 5)
                    HeatStrip(counts: doc.strip.map { Int(truncating: $0) })
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(20)
        .frame(width: 328)
        .background(c.surface, in: RoundedRectangle(cornerRadius: 19, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 19, style: .continuous).strokeBorder(c.hairline, lineWidth: 1))
        .padding(16)
        .frame(width: 360)
        .background(c.bg)
    }
}

private struct DotRule: View {
    let color: Color
    var body: some View {
        HStack(spacing: 8) {
            Rectangle().fill(color).frame(height: 0.7)
            Circle().fill(color).frame(width: 4, height: 4)
            Rectangle().fill(color).frame(height: 0.7)
        }
    }
}

private struct HeatStrip: View {
    @Environment(\.qh) private var qh
    let counts: [Int]

    var body: some View {
        let c = qh.colors
        let peak = counts.max() ?? 0
        ChipFlowLayout(spacing: 2.7, lineSpacing: 2.7) {
            ForEach(Array(counts.enumerated()), id: \.offset) { _, count in
                let level = Analytics.shared.level(count: Int32(count), max: Int32(peak))
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(level == 0 ? c.surfaceMuted : c.primary.opacity([0, 0.3, 0.55, 0.8, 1][min(Int(level), 4)]))
                    .frame(width: 10, height: 10)
            }
        }
    }
}
