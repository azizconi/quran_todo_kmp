import ComposeApp
import UIKit

/// The report as a PDF (`UIGraphicsPDFRenderer`), A4: the same content and layout as `ReportPdf` of Android (brand line, title and period,
/// the memorized share as a bar, the rows, the heat strip), the text flows onto further pages. A printed page is always light, so the
/// colours are the light tokens of the app. No star shapes: the only ornament is a hairline.
enum ReportPdfRenderer {
    private static let pageSize = CGSize(width: 595, height: 842)
    private static let margin: CGFloat = 40

    private static let ink = UIColor(argb: 0xFF16201C)
    private static let inkSecondary = UIColor(argb: 0xFF5D6862)
    private static let hairline = UIColor(argb: 0xFFE2DBCB)
    private static let gold = UIColor(argb: 0xFFA97B2C)
    private static let track = UIColor(argb: 0xFFE6E0D2)
    private static let learned = UIColor(argb: 0xFF0E6B52)
    private static let primary = UIColor(argb: 0xFF0E6B52)

    @discardableResult
    static func render(_ doc: ReportDoc, to url: URL) -> Bool {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: pageSize))
        do {
            try renderer.writePDF(to: url) { context in
                var writer = Writer(context: context)
                writer.draw(doc)
            }
            return true
        } catch {
            return false
        }
    }

    private struct Writer {
        let context: UIGraphicsPDFRendererContext
        var y: CGFloat = margin
        var started = false
        let width = pageSize.width - 2 * margin

        init(context: UIGraphicsPDFRendererContext) {
            self.context = context
        }

        mutating func newPage() {
            context.beginPage()
            started = true
            y = margin
        }

        mutating func ensure(_ height: CGFloat) {
            if !started || y + height > pageSize.height - margin { newPage() }
        }

        private func font(_ size: CGFloat, bold: Bool) -> UIFont {
            .systemFont(ofSize: size, weight: bold ? .bold : .regular)
        }

        private func attributes(size: CGFloat, bold: Bool, color: UIColor, kern: CGFloat = 0, align: NSTextAlignment = .natural) -> [NSAttributedString.Key: Any] {
            let style = NSMutableParagraphStyle()
            style.alignment = align
            style.lineHeightMultiple = 1.15
            style.lineBreakMode = .byWordWrapping
            return [.font: font(size, bold: bold), .foregroundColor: color, .kern: kern, .paragraphStyle: style]
        }

        mutating func text(_ string: String, size: CGFloat, bold: Bool, color: UIColor, gapAfter: CGFloat, kern: CGFloat = 0, align: NSTextAlignment = .natural) {
            let attributed = NSAttributedString(string: string, attributes: attributes(size: size, bold: bold, color: color, kern: kern, align: align))
            let bounds = attributed.boundingRect(
                with: CGSize(width: width, height: .greatestFiniteMagnitude),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                context: nil
            )
            let height = ceil(bounds.height)
            ensure(height)
            attributed.draw(with: CGRect(x: margin, y: y, width: width, height: height), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
            y += height + gapAfter
        }

        mutating func rule(_ color: UIColor, gapAfter: CGFloat) {
            ensure(1 + gapAfter)
            let path = UIBezierPath()
            path.move(to: CGPoint(x: margin, y: y))
            path.addLine(to: CGPoint(x: margin + width, y: y))
            path.lineWidth = 0.8
            color.setStroke()
            path.stroke()
            y += 1 + gapAfter
        }

        mutating func bar(_ fraction: CGFloat) {
            ensure(14)
            let h: CGFloat = 8
            track.setFill()
            UIBezierPath(roundedRect: CGRect(x: margin, y: y, width: width, height: h), cornerRadius: h / 2).fill()
            if fraction > 0 {
                // A visible start even for a tiny share, like the ring on the screen.
                let w = max(width * fraction, h)
                learned.setFill()
                UIBezierPath(roundedRect: CGRect(x: margin, y: y, width: w, height: h), cornerRadius: h / 2).fill()
            }
            y += h + 14
        }

        private func level(_ count: Int, _ maxCount: Int) -> Int {
            if count <= 0 || maxCount <= 0 { return 0 }
            return min(max(Int((Double(count) * 4.0 / Double(maxCount)).rounded(.up)), 1), 4)
        }

        private func stripColor(_ level: Int) -> UIColor {
            switch level {
            case 0: return track
            case 1: return primary.withAlphaComponent(0.3)
            case 2: return primary.withAlphaComponent(0.55)
            case 3: return primary.withAlphaComponent(0.8)
            default: return primary
            }
        }

        mutating func draw(_ doc: ReportDoc) {
            newPage()
            text("QURAN HIFZ", size: 10, bold: true, color: inkSecondary, gapAfter: 10, kern: 2.5, align: .center)
            text(doc.title, size: 24, bold: true, color: ink, gapAfter: 4, align: .center)
            text(doc.period, size: 13, bold: false, color: inkSecondary, gapAfter: 16, align: .center)
            rule(gold, gapAfter: 18)

            text(doc.percentText, size: 34, bold: true, color: ink, gapAfter: 2, align: .center)
            text(doc.caption, size: 13, bold: false, color: ink, gapAfter: 10, align: .center)
            bar(CGFloat(doc.fraction))

            for row in doc.rows {
                rule(hairline, gapAfter: 12)
                // Keep the label with its value and the first line.
                ensure(60)
                text(row.label, size: 11, bold: true, color: inkSecondary, gapAfter: 2)
                if !row.value.trimmingCharacters(in: .whitespaces).isEmpty {
                    text(row.value, size: 16, bold: true, color: ink, gapAfter: 3)
                }
                for line in row.lines {
                    text(row.bulleted ? "\u{2022}  \(line)" : line, size: 12, bold: false, color: ink, gapAfter: 2)
                }
                y += 6
            }

            let strip = doc.strip.map { $0.intValue }
            if !strip.isEmpty {
                rule(hairline, gapAfter: 12)
                let cell: CGFloat = 11
                let gap: CGFloat = 3
                let perRow = max(Int((width + gap) / (cell + gap)), 1)
                let rows = (strip.count + perRow - 1) / perRow
                ensure(24 + CGFloat(rows) * (cell + gap))
                text(doc.stripLabel, size: 11, bold: true, color: inkSecondary, gapAfter: 6)
                let maxCount = strip.max() ?? 0
                for (index, count) in strip.enumerated() {
                    let left = margin + CGFloat(index % perRow) * (cell + gap)
                    let top = y + CGFloat(index / perRow) * (cell + gap)
                    stripColor(level(count, maxCount)).setFill()
                    UIBezierPath(roundedRect: CGRect(x: left, y: top, width: cell, height: cell), cornerRadius: 2).fill()
                }
                y += CGFloat(rows) * (cell + gap)
            }
        }
    }
}

private extension UIColor {
    convenience init(argb: UInt32) {
        self.init(
            red: CGFloat((argb >> 16) & 0xFF) / 255,
            green: CGFloat((argb >> 8) & 0xFF) / 255,
            blue: CGFloat(argb & 0xFF) / 255,
            alpha: CGFloat((argb >> 24) & 0xFF) / 255
        )
    }
}
