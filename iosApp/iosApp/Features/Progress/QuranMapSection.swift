import ComposeApp
import SwiftUI

private let mapColumns = 10
private let cellGap: CGFloat = 4
private let minVisibleFill: CGFloat = 0.12
private let waveDelay: Double = 0.012
private let waveGlint: Double = 0.6

/// The glint over cell `index` at `time` seconds after the wave began: each cell lights up and fades once, 12 ms after its left
/// neighbour (`waveIntensity`).
private func waveIntensity(_ time: Double, index: Int) -> Double {
    let phase = (time - Double(index) * waveDelay) / waveGlint
    return phase <= 0 || phase >= 1 ? 0 : sin(Double.pi * phase)
}

private func waveTotal(_ cells: Int) -> Double { Double(max(cells - 1, 0)) * waveDelay + waveGlint }

/// The fourth block of the page: "Quran map", collapsed until the person opens it. Inside are the 114 cells of the surahs and the thirty
/// juz. Nothing of the two is built while the block is closed.
struct QuranMapBlock: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let cells: [MapCellUi]
    let juz: [JuzUi]
    let onOpenSurah: (Int) -> Void
    let onOpenJuz: (JuzUi) -> Void
    @State private var expanded = false

    var body: some View {
        let p = copy.progress
        VStack(alignment: .leading, spacing: 0) {
            QhCard(onTap: { withQhAnimation(QhMotion.gentle) { expanded.toggle() } }) {
                HStack(spacing: QhSpacing.x3) {
                    Text(p.mapTitle).qhText({ $0.title2 })
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.down").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(p.mapTitle). \(expanded ? p.mapCollapse : p.mapExpand)")
            .accessibilityAddTraits(.isButton)
            if expanded {
                VStack(alignment: .leading, spacing: QhSpacing.section) {
                    VStack(alignment: .leading, spacing: QhSpacing.x3) {
                        Text(p.mapHint).qhText({ $0.callout }, color: { $0.inkSecondary })
                        QhCard {
                            QuranMapGrid(cells: cells, onOpen: onOpenSurah)
                            MapLegend().padding(.top, QhSpacing.x4)
                        }
                    }
                    JuzSection(juz: juz, onOpen: onOpenJuz)
                }
                .padding(.top, QhSpacing.x3)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .qhAnimation(QhMotion.gentle, value: expanded)
    }
}

/// 114 small squares, ten to a row. A surah that is not started is a muted square, one in progress is filled with gold from the bottom
/// up by its share of memorized ayahs, a memorized one is solid emerald with a small checkmark. When the map first comes on screen a
/// golden glint runs over the cells in order (not under Reduce Motion). A tap opens a bubble above the cell with the name, the ayah
/// count and "Open".
private struct QuranMapGrid: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let cells: [MapCellUi]
    let onOpen: (Int) -> Void
    @State private var selected: Int?
    @State private var waveStart: Date?
    @State private var wavePlayed = false
    @State private var gridWidth: CGFloat = 0

    var body: some View {
        let rows = (cells.count + mapColumns - 1) / mapColumns
        let cellSize = gridWidth > 0 ? (gridWidth - cellGap * CGFloat(mapColumns - 1)) / CGFloat(mapColumns) : 0
        let hasLearned = cells.contains { $0.state == .learned }
        TimelineView(.animation(paused: waveStart == nil)) { context in
            let time = waveStart.map { context.date.timeIntervalSince($0) } ?? (reduced || wavePlayed ? waveTotal(cells.count) : 0)
            ZStack(alignment: .topLeading) {
                Color.clear.contentShape(Rectangle()).onTapGesture { selected = nil }
                VStack(spacing: cellGap) {
                    ForEach(0..<rows, id: \.self) { row in
                        HStack(spacing: cellGap) {
                            ForEach(0..<mapColumns, id: \.self) { column in
                                let index = row * mapColumns + column
                                if let cell = cells[safe: index] {
                                    MapCell(cell: cell, index: index, selected: selected == Int(cell.number), time: time) {
                                        withQhAnimation(QhMotion.snappy) { selected = selected == Int(cell.number) ? nil : Int(cell.number) }
                                    }
                                } else {
                                    Color.clear.aspectRatio(1, contentMode: .fit)
                                }
                            }
                        }
                    }
                }
                if let number = selected, let cell = cells.first(where: { Int($0.number) == number }), cellSize > 0 {
                    let index = number - 1
                    let column = index % mapColumns, row = index / mapColumns
                    MapBubble(cell: cell, anchorX: CGFloat(column) * (cellSize + cellGap) + cellSize / 2,
                              anchorTop: CGFloat(row) * (cellSize + cellGap), cellSize: cellSize, width: gridWidth) {
                        onOpen(Int(cell.number))
                    }
                    .id(number)
                }
            }
        }
        .background(GeometryReader { geo in Color.clear.onAppear { gridWidth = geo.size.width }.onChange(of: geo.size.width) { _, w in gridWidth = w } })
        .task(id: hasLearned) {
            guard hasLearned, !reduced, !wavePlayed else { return }
            // Let the section settle in before the glint starts.
            try? await Task.sleep(for: .milliseconds(450))
            if Task.isCancelled { return }
            wavePlayed = true
            waveStart = Date()
            try? await Task.sleep(for: .seconds(waveTotal(cells.count) + 0.1))
            waveStart = nil
        }
        .task(id: selected) {
            guard selected != nil else { return }
            try? await Task.sleep(for: .seconds(7))
            if !Task.isCancelled { selected = nil }
        }
    }
}

private struct MapCell: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let cell: MapCellUi
    let index: Int
    let selected: Bool
    let time: Double
    let action: () -> Void

    var body: some View {
        let c = qh.colors
        let p = copy.progress
        let glint = cell.state == .learned ? waveIntensity(time, index: index) : 0
        Canvas { gc, size in
            let rect = CGRect(origin: .zero, size: size)
            let shape = Path(roundedRect: rect, cornerRadius: 6)
            switch cell.state {
            case .notStarted:
                gc.fill(shape, with: .color(c.surfaceMuted))
                // The muted tone alone nearly vanishes on a dark card: a hairline keeps the square.
                gc.stroke(shape, with: .color(c.hairline), lineWidth: 1)
            case .learning:
                gc.fill(shape, with: .color(c.learningContainer))
                let fill = cell.fraction > 0 ? max(CGFloat(cell.fraction), minVisibleFill) : 0
                if fill > 0 {
                    gc.drawLayer { layer in
                        layer.clip(to: shape)
                        let h = size.height * fill
                        layer.fill(Path(CGRect(x: 0, y: size.height - h, width: size.width, height: h)), with: .color(c.learning))
                    }
                }
            default:
                let pop = 1 + 0.16 * glint
                gc.translateBy(x: size.width / 2, y: size.height / 2)
                gc.scaleBy(x: pop, y: pop)
                gc.translateBy(x: -size.width / 2, y: -size.height / 2)
                gc.fill(shape, with: .color(c.learned))
                var check = Path()
                let s = min(size.width, size.height)
                check.move(to: CGPoint(x: s * 0.28, y: s * 0.53)); check.addLine(to: CGPoint(x: s * 0.44, y: s * 0.68)); check.addLine(to: CGPoint(x: s * 0.73, y: s * 0.36))
                gc.stroke(check, with: .color(c.onLearned.opacity(0.72)), style: StrokeStyle(lineWidth: s * 0.11, lineCap: .round, lineJoin: .round))
                if glint > 0 { gc.fill(shape, with: .color(c.gold.opacity(0.92 * glint))) }
            }
            if selected {
                gc.stroke(Path(roundedRect: rect.insetBy(dx: 1, dy: 1), cornerRadius: 6), with: .color(c.ink), lineWidth: 2)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .pressable(scale: 0.84, shape: RoundedRectangle(cornerRadius: 6, style: .continuous), highlight: false, action: action)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(cell.number), \(cell.name)")
        .accessibilityValue("\(status(p)), \(p.ayahsOf(learned: cell.learnedAyahs, total: cell.ayahCount))")
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func status(_ p: ProgressCopy) -> String {
        switch cell.state {
        case .notStarted: return p.statusNotStarted
        case .learning: return p.statusLearning
        default: return p.statusLearned
        }
    }
}

/// The bubble over a cell, with an arrow pointing at it; rows too near the top hang it below.
private struct MapBubble: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let cell: MapCellUi
    let anchorX: CGFloat
    let anchorTop: CGFloat
    let cellSize: CGFloat
    let width: CGFloat
    let onOpen: () -> Void
    @State private var size: CGSize = .zero
    @State private var appear: CGFloat = 0

    var body: some View {
        let p = copy.progress
        let c = qh.colors
        let status: String = {
            switch cell.state {
            case .notStarted: return p.statusNotStarted
            case .learning: return p.statusLearning
            default: return p.statusLearned
            }
        }()
        let arrow: CGFloat = 7
        let above = anchorTop >= size.height + arrow
        let x = min(max(anchorX - size.width / 2, 0), max(width - size.width, 0))
        let y = above ? anchorTop - size.height - arrow : anchorTop + cellSize + arrow
        ZStack(alignment: .topLeading) {
            ArrowShape(up: !above).fill(c.ink)
                .frame(width: 14, height: arrow)
                .offset(x: anchorX - 7, y: above ? anchorTop - arrow - 0.5 : anchorTop + cellSize + 0.5)
                .opacity(appear)
            HStack(spacing: QhSpacing.x3) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(cell.number) · \(cell.name)").qhText({ $0.title2 }, color: { $0.bg }).lineLimit(2)
                    Text("\(status) · \(p.ayahsOf(learned: cell.learnedAyahs, total: cell.ayahCount))").qhText({ $0.callout }, color: { $0.bg.opacity(0.8) })
                }
                Text(p.open).qhText({ $0.label }, color: { $0.ink })
                    .padding(.horizontal, QhSpacing.x4).frame(minHeight: 36)
                    .background(c.bg, in: Capsule())
                    .pressable(shape: Capsule(), highlight: false, action: onOpen)
            }
            .padding(.leading, QhSpacing.x4).padding(.vertical, QhSpacing.x2).padding(.trailing, QhSpacing.x2)
            .frame(maxWidth: 300)
            .background(c.ink, in: qh.shapes.mdShape)
            .fixedSize(horizontal: false, vertical: true)
            .background(GeometryReader { geo in Color.clear.onAppear { size = geo.size }.onChange(of: geo.size) { _, s in size = s } })
            .scaleEffect(0.92 + 0.08 * appear, anchor: above ? .bottom : .top).opacity(appear)
            .offset(x: x, y: y)
        }
        .onAppear { if reduced { appear = 1 } else { withAnimation(QhMotion.snappy) { appear = 1 } } }
        .accessibilityElement(children: .contain)
    }
}

private struct ArrowShape: Shape {
    let up: Bool
    func path(in rect: CGRect) -> Path {
        var p = Path()
        if up {
            p.move(to: CGPoint(x: rect.minX, y: rect.maxY)); p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY)); p.addLine(to: CGPoint(x: rect.midX, y: rect.minY))
        } else {
            p.move(to: CGPoint(x: rect.minX, y: rect.minY)); p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY)); p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        }
        p.closeSubpath()
        return p
    }
}

private struct MapLegend: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let p = copy.progress
        ChipFlowLayout(spacing: QhSpacing.x4, lineSpacing: QhSpacing.x2) {
            item(.notStarted, p.statusNotStarted)
            item(.learning, p.statusLearning)
            item(.learned, p.statusLearned)
        }
    }

    private func item(_ state: MapState, _ label: String) -> some View {
        HStack(spacing: QhSpacing.x2) {
            let c = qh.colors
            Canvas { gc, size in
                let shape = Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 4)
                switch state {
                case .notStarted:
                    gc.fill(shape, with: .color(c.surfaceMuted)); gc.stroke(shape, with: .color(c.hairline), lineWidth: 1)
                case .learning:
                    gc.fill(shape, with: .color(c.learningContainer))
                    gc.drawLayer { l in l.clip(to: shape); l.fill(Path(CGRect(x: 0, y: size.height / 2, width: size.width, height: size.height / 2)), with: .color(c.learning)) }
                default:
                    gc.fill(shape, with: .color(c.learned))
                }
            }
            .frame(width: 16, height: 16)
            Text(label).qhText({ $0.callout }, color: { $0.inkSecondary })
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
    }
}

// MARK: - Juz

/// Progress by juz: thirty thin bars, each the share of the juz that is covered ("Juz 30", its traditional Arabic name, the percentage).
/// A tap opens the reader at the start of the juz. Two columns, one at big type sizes.
struct JuzSection: View {
    @Environment(Copy.self) private var copy
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.qh) private var qh
    let juz: [JuzUi]
    let onOpen: (JuzUi) -> Void

    var body: some View {
        let p = copy.progress
        let columns = typeSize > .xLarge ? 1 : 2
        VStack(alignment: .leading, spacing: 0) {
            QhSectionHeader(p.juzTitle)
            Text(p.juzHint).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1)
            QhCard(padding: QhSpacing.x2) {
                VStack(spacing: 0) {
                    ForEach(Array(stride(from: 0, to: juz.count, by: columns)), id: \.self) { start in
                        HStack(spacing: QhSpacing.x2) {
                            ForEach(start..<min(start + columns, juz.count), id: \.self) { i in
                                JuzCell(item: juz[i]) { onOpen(juz[i]) }
                            }
                            if start + columns > juz.count { ForEach(0..<(start + columns - juz.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) } }
                        }
                    }
                }
            }
            .padding(.top, QhSpacing.x3)
        }
    }
}

private struct JuzCell: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let item: JuzUi
    let action: () -> Void

    var body: some View {
        let p = copy.progress
        let done = item.percent >= 100
        VStack(alignment: .leading, spacing: QhSpacing.x1) {
            HStack {
                Text(p.juzName(juz: item.juz)).qhText({ $0.callout.weight("SemiBold") }).lineLimit(1)
                Spacer(minLength: QhSpacing.x2)
                Text(verbatim: item.arabicName).qhArabic(qh.type.quranInline(LessonArabic.face, size: 17), color: qh.colors.inkSecondary).lineLimit(1)
                    .environment(\.layoutDirection, .rightToLeft)
            }
            HStack(spacing: QhSpacing.x2) {
                QhLinearProgress(progress: Double(item.fraction), height: 6, color: done ? qh.colors.learned : qh.colors.primary)
                Text(p.percentText(percent: item.percent)).qhText({ $0.label }, color: { done ? $0.learned : $0.inkSecondary }).qhTabular()
                    .frame(minWidth: 40, alignment: .trailing)
            }
        }
        .padding(.horizontal, QhSpacing.x2).padding(.vertical, QhSpacing.x2)
        .frame(maxWidth: .infinity, minHeight: QhSpacing.minTouch, alignment: .leading)
        .pressable(shape: qh.shapes.smShape, action: action)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(p.juzDescription(juz: item.juz, percent: item.percent))
    }
}
