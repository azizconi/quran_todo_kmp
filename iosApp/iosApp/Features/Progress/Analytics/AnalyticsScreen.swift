import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `AnalyticsViewModel` with its state.
@MainActor
@Observable
final class AnalyticsModel {
    @ObservationIgnored private let holder = VMHolder { IosUi.shared.analytics() }
    let ui: FlowState<AnalyticsUi>
    var vm: AnalyticsViewModel { holder.vm }

    init() {
        ui = FlowState(holder.vm.ui)
        holder.vm.setLanguage(value: IosUi.shared.language.value)
    }
}

/// Rows of a list before "Show more".
private let collapsedRows = 6

/// The detailed analytics (`AnalyticsScreen.kt`, `Dest.ProgressDetails`): retention by surah and juz, the year heat map, the forecast by
/// surah of the plan and the hardest places with a way into the drill.
struct AnalyticsScreen: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = AnalyticsModel()
    @State private var stagger = StaggerState(screen: "analytics")

    var body: some View {
        let ui = model.ui.value
        ScrollView {
            VStack(spacing: QhSpacing.section) {
                if ui.ready {
                    RetentionSection(ui: ui)
                        .staggeredEnter(index: 0, state: stagger)
                    HeatSection(ui: ui, onSelectYear: { model.vm.selectYear(year: Int32($0)) })
                        .staggeredEnter(index: 1, state: stagger)
                    ForecastSection(ui: ui)
                        .staggeredEnter(index: 2, state: stagger)
                    HardPlacesSection(ui: ui)
                        .staggeredEnter(index: 3, state: stagger)
                } else {
                    Skeleton(shape: qh.shapes.lgShape).frame(height: 200)
                    Skeleton(shape: qh.shapes.lgShape).frame(height: 160)
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x8)
        }
        .background(qh.colors.bg)
        .navigationTitle(copy.analyticsCopy.screenTitle)
        .navigationBarTitleDisplayMode(.large)
        .qhNavigationStyle()
        .onAppear { model.vm.onResume() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { model.vm.onResume() } }
        .onChange(of: copy.language) { _, language in model.vm.setLanguage(value: language) }
        .onChange(of: ui.ready) { _, ready in if ready { stagger.markPlayed() } }
    }
}

// MARK: - Shared pieces

private struct SectionIntro: View {
    let title: String
    let hint: String

    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x1) {
            QhSectionHeader(title)
            Text(hint).qhText({ $0.callout }, color: { $0.inkSecondary })
        }
        .padding(.horizontal, QhSpacing.x1)
    }
}

/// "Show more (5)" / "Show less" at the foot of a list card.
private struct ExpandRow: View {
    @Environment(Copy.self) private var copy
    let hidden: Int
    let expanded: Bool
    let onToggle: () -> Void

    var body: some View {
        let p = copy.progress
        QhRowDivider(startIndent: QhSpacing.card)
        QhButton(expanded ? p.hardLess : p.hardMore(n: Int32(hidden)), variant: .text, size: .s,
                 icon: expanded ? "chevron.up" : "chevron.down", action: onToggle)
            .frame(maxWidth: .infinity)
            .padding(.vertical, QhSpacing.x1)
    }
}

// MARK: - Retention

private struct RetentionSection: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ui: AnalyticsUi
    @State private var mode = 0
    @State private var expanded = false

    var body: some View {
        let a = copy.analyticsCopy
        let p = copy.progress
        let rows = mode == 0 ? ui.bySurah : ui.byJuz
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            SectionIntro(title: a.retentionTitle, hint: a.retentionHint)
            QhSegmentedControl(options: [a.toggleSurahs, a.toggleJuz], selection: Binding(get: { mode }, set: { mode = $0; expanded = false }))
            if rows.isEmpty {
                QhCard { Text(a.retentionEmpty).qhText({ $0.callout }, color: { $0.inkSecondary }) }
            } else {
                let visible = expanded ? rows : Array(rows.prefix(collapsedRows))
                QhCard(padding: QhSpacing.x1) {
                    VStack(spacing: 0) {
                        ForEach(Array(visible.enumerated()), id: \.element.id) { index, row in
                            if index > 0 { QhRowDivider(startIndent: QhSpacing.card) }
                            let title = mode == 0 ? ui.nameOf(surah: row.id) : p.juzName(juz: row.id)
                            RetentionRowView(title: title, subtitle: copy.lessonCopy.repetitions(count: row.repetitions),
                                             row: row, percentText: p.percentText(percent: row.percent)) { open(row) }
                        }
                        if rows.count > collapsedRows {
                            ExpandRow(hidden: rows.count - collapsedRows, expanded: expanded) { withQhAnimation(QhMotion.gentle) { expanded.toggle() } }
                        }
                    }
                }
                .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: expanded)
            }
        }
    }

    private func open(_ row: RetentionRow) {
        if mode == 0 {
            router.openReader(Int(row.id), origin: "map", in: .progress)
        } else {
            let range = JuzIndex.shared.range(juz: row.id)
            router.openReader(Int(range.startSurah), ayah: range.startAyah > 1 ? Int(range.startAyah) : nil, origin: "map", in: .progress)
        }
    }
}

private struct RetentionRowView: View {
    @Environment(\.qh) private var qh
    let title: String
    let subtitle: String
    let row: RetentionRow
    let percentText: String
    let action: () -> Void

    var body: some View {
        let bar = row.percent >= 85 ? qh.colors.learned : (row.percent >= 60 ? qh.colors.primary : qh.colors.due)
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            HStack {
                Text(title).qhText({ $0.callout.weight("SemiBold") }).lineLimit(1)
                Spacer(minLength: QhSpacing.x2)
                Text(percentText).qhText({ $0.label }, color: { _ in bar }).qhTabular()
            }
            QhLinearProgress(progress: Double(row.fraction), height: 6, color: bar, trackColor: qh.colors.surfaceMuted)
            Text(subtitle).qhText({ $0.label }, color: { $0.inkSecondary })
        }
        .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .pressable(shape: qh.shapes.smShape, action: action)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(percentText), \(subtitle)")
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Heat map

private struct HeatSection: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ui: AnalyticsUi
    let onSelectYear: (Int) -> Void
    @State private var selected: Int?

    var body: some View {
        if let heat = ui.heat {
            let a = copy.analyticsCopy
            let p = copy.progress
            let index = ui.years.firstIndex { $0.intValue == Int(heat.year) } ?? 0
            let day = selected.flatMap { epoch in heat.grid.flatMap { $0 }.compactMap { $0 }.first { Int($0.epochDay) == epoch } }
            let bubble = day.map { d -> String in
                let date = EpochDay.date(Int(d.epochDay))
                let parts = Calendar.current.dateComponents([.day, .month], from: date)
                return a.bubble(date: p.date(day: Int32(parts.day ?? 1), month: Int32(parts.month ?? 1)), newCount: d.newCount,
                                reviewed: d.reviewedCount, listened: d.listened, paused: d.paused)
            }
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                SectionIntro(title: a.heatTitle, hint: a.heatHint)
                QhCard {
                    VStack(spacing: QhSpacing.x2) {
                        HStack {
                            QhIconButton(icon: "chevron.left", label: a.previousYear, filled: false) {
                                if index > 0 { selected = nil; onSelectYear(ui.years[index - 1].intValue) }
                            }
                            .disabled(index <= 0)
                            VStack(spacing: 0) {
                                Text(String(heat.year)).qhText({ $0.title2 }).qhTabular()
                                Text(a.activeDays(n: heat.activeDays)).qhText({ $0.label }, color: { $0.inkSecondary })
                            }
                            .frame(maxWidth: .infinity)
                            .contentTransition(.numericText())
                            QhIconButton(icon: "chevron.right", label: a.nextYear, filled: false) {
                                if index < ui.years.count - 1 { selected = nil; onSelectYear(ui.years[index + 1].intValue) }
                            }
                            .disabled(index >= ui.years.count - 1)
                        }
                        HeatGrid(heat: heat, selected: $selected, summary: a.activeDays(n: heat.activeDays), bubble: bubble)
                            .id(heat.year)
                        if let bubble {
                            Text(bubble).qhText({ $0.callout }, color: { $0.onPrimaryContainer })
                                .padding(.horizontal, QhSpacing.x3).padding(.vertical, QhSpacing.x2)
                                .background(qh.colors.primaryContainer, in: RoundedRectangle(cornerRadius: QhSpacing.x3, style: .continuous))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .transition(.opacity.combined(with: .scale(scale: 0.96, anchor: .topLeading)))
                                .accessibilityAddTraits(.updatesFrequently)
                        }
                        HeatLegend(showPause: heat.grid.contains { $0.contains { $0?.paused == true } })
                    }
                    .animation(QhMotion.reduced ? nil : QhMotion.snappy, value: selected)
                }
            }
        }
    }
}

extension HeatYear {
    /// The weeks as Swift optionals (a cell is nil outside the year and after today).
    var grid: [[HeatDay?]] { weeks.map { $0.map { $0 as? HeatDay } } }
}

/// The colour of a cell: four steps of `primary` over the muted track.
private func heatColor(_ level: Int, _ c: QhColors) -> Color {
    switch level {
    case 0: return c.surfaceMuted
    case 1: return c.primary.opacity(0.28)
    case 2: return c.primary.opacity(0.5)
    case 3: return c.primary.opacity(0.75)
    default: return c.primary
    }
}

/// A year as weeks of seven cells, Monday first, drawn on a `Canvas`; a tap or a drag across the cells picks the nearest day (the finger is
/// wider than a cell). VoiceOver steps day by day and week by week through the accessibility actions.
private struct HeatGrid: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let heat: HeatYear
    @Binding var selected: Int?
    let summary: String
    let bubble: String?
    private let gap: CGFloat = 2
    private let monthRow: CGFloat = 16

    var body: some View {
        let weeks = heat.weeks.count
        GeometryReader { geo in
            let cell = (geo.size.width - gap * CGFloat(weeks - 1)) / CGFloat(max(weeks, 1))
            Canvas { context, _ in
                draw(context, cell: cell)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0).onChanged { value in
                    let day = HeatGridHit.shared.nearestDay(heat: heat, x: Float(value.location.x), y: Float(value.location.y), cell: Float(cell),
                                                            gap: Float(gap), monthRow: Float(monthRow), tolerancePx: 12)
                    let next = day.map { Int($0.int32Value) }
                    if next != selected {
                        if next != nil { QhHaptics.shared.perform(.step) }
                        selected = next
                    }
                }
            )
        }
        .frame(height: gridHeight)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(heat.year). \(summary)")
        .accessibilityValue(bubble ?? "")
        .accessibilityAdjustableAction { direction in
            selected = step(direction == .increment ? 1 : -1)
        }
        .accessibilityActions {
            Button(copy.a11y.heatPreviousWeek) { selected = step(-7) }
            Button(copy.a11y.heatNextWeek) { selected = step(7) }
        }
    }

    /// The height the grid needs at the width of a card (cells are square).
    private var gridHeight: CGFloat {
        let width = UIScreen.main.bounds.width - QhSpacing.screen * 2 - QhSpacing.card * 2
        let weeks = CGFloat(max(heat.weeks.count, 1))
        let cell = (width - gap * (weeks - 1)) / weeks
        return monthRow + 7 * cell + 6 * gap
    }

    private func step(_ delta: Int) -> Int? {
        HeatGridHit.shared.step(heat: heat, selected: selected.map { KotlinInt(int: Int32($0)) }, delta: Int32(delta)).map { Int($0.int32Value) }
    }

    private func draw(_ context: GraphicsContext, cell: CGFloat) {
        let c = qh.colors
        let months = copy.progress.months
        var lastMonth = 0
        var lastLabelEnd: CGFloat = -1
        for (week, days) in heat.grid.enumerated() {
            let x = CGFloat(week) * (cell + gap)
            if let first = days.compactMap({ $0 }).first {
                let parts = Calendar.current.dateComponents([.day, .month], from: EpochDay.date(Int(first.epochDay)))
                let month = parts.month ?? 1
                if month != lastMonth, (parts.day ?? 1) <= 7 {
                    lastMonth = month
                    let label = context.resolve(Text(months[month - 1]).font(.custom("Manrope-SemiBold", fixedSize: 10)).foregroundStyle(c.inkSecondary))
                    if x >= lastLabelEnd {
                        context.draw(label, at: CGPoint(x: x, y: 0), anchor: .topLeading)
                        lastLabelEnd = x + label.measure(in: CGSize(width: 200, height: 20)).width + gap * 2
                    }
                }
            }
            for (weekday, entry) in days.enumerated() {
                guard let day = entry else { continue }
                let rect = CGRect(x: x, y: monthRow + CGFloat(weekday) * (cell + gap), width: cell, height: cell)
                let shape = Path(roundedRect: rect, cornerRadius: cell * 0.22)
                context.fill(shape, with: .color(heatColor(Int(Analytics.shared.level(count: day.count, max: heat.max)), c)))
                if day.paused { hatch(context, rect: rect, color: c.inkTertiary, width: max(cell * 0.12, 1)) }
                if Int(day.epochDay) == selected {
                    context.stroke(shape, with: .color(c.ink), lineWidth: max(cell * 0.18, 1.5))
                }
            }
        }
    }

    private func hatch(_ context: GraphicsContext, rect: CGRect, color: Color, width: CGFloat) {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + rect.width * 0.15, y: rect.minY + rect.height * 0.85))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.85, y: rect.minY + rect.height * 0.15))
        path.move(to: CGPoint(x: rect.minX + rect.width * 0.15, y: rect.minY + rect.height * 0.5))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.5, y: rect.minY + rect.height * 0.15))
        path.move(to: CGPoint(x: rect.minX + rect.width * 0.5, y: rect.minY + rect.height * 0.85))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.85, y: rect.minY + rect.height * 0.5))
        context.stroke(path, with: .color(color), lineWidth: width)
    }
}

private struct HeatLegend: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let showPause: Bool

    var body: some View {
        let a = copy.analyticsCopy
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            HStack(spacing: QhSpacing.x2) {
                Text(a.legendLess).qhText({ $0.label }, color: { $0.inkSecondary })
                ForEach(0 ..< 5, id: \.self) { level in
                    RoundedRectangle(cornerRadius: 3, style: .continuous).fill(heatColor(level, qh.colors)).frame(width: 14, height: 14)
                }
                Text(a.legendMore).qhText({ $0.label }, color: { $0.inkSecondary })
                Spacer(minLength: 0)
            }
            if showPause {
                HStack(spacing: QhSpacing.x2) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 3, style: .continuous).fill(qh.colors.surfaceMuted)
                        Path { p in
                            p.move(to: CGPoint(x: 2, y: 12)); p.addLine(to: CGPoint(x: 12, y: 2))
                            p.move(to: CGPoint(x: 2, y: 7)); p.addLine(to: CGPoint(x: 7, y: 2))
                            p.move(to: CGPoint(x: 7, y: 12)); p.addLine(to: CGPoint(x: 12, y: 7))
                        }
                        .stroke(qh.colors.inkTertiary, lineWidth: 1.5)
                    }
                    .frame(width: 14, height: 14)
                    Text(a.legendPause).qhText({ $0.label }, color: { $0.inkSecondary })
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Forecast

private struct ForecastSection: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ui: AnalyticsUi
    @State private var expanded = false

    var body: some View {
        let a = copy.analyticsCopy
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            SectionIntro(title: a.forecastTitle, hint: a.forecastHint)
            if ui.forecasts.isEmpty {
                QhCard(onTap: { router.push(.plan, in: .progress) }) { Text(a.forecastEmpty).qhText({ $0.callout }, color: { $0.inkSecondary }) }
            } else {
                // Collapsed: the last learned surah (for orientation) and the next ones; expanded: the whole queue.
                let all = ui.forecasts
                let firstOpen = all.firstIndex { $0.state != .learned } ?? all.count
                let start = expanded ? 0 : max(firstOpen - 1, 0)
                let tail = Array(all.dropFirst(start))
                let visible = expanded ? tail : Array(tail.prefix(collapsedRows))
                let hidden = all.count - visible.count
                QhCard(padding: QhSpacing.x1) {
                    VStack(spacing: 0) {
                        ForEach(Array(visible.enumerated()), id: \.element.surah) { index, item in
                            if index > 0 { QhRowDivider(startIndent: QhSpacing.card) }
                            ForecastRow(item: item, ui: ui) { router.openReader(Int(item.surah), origin: "map", in: .progress) }
                        }
                        if hidden > 0 || expanded {
                            ExpandRow(hidden: hidden, expanded: expanded) { withQhAnimation(QhMotion.gentle) { expanded.toggle() } }
                        }
                    }
                }
                .animation(QhMotion.reduced ? nil : QhMotion.gentle, value: expanded)
            }
        }
    }
}

private struct ForecastRow: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let item: SurahForecast
    let ui: AnalyticsUi
    let action: () -> Void

    var body: some View {
        let a = copy.analyticsCopy
        let c = qh.colors
        let current = item.state == .current
        let learned = item.state == .learned
        let name = ui.nameOf(surah: item.surah)
        let right: String = learned ? a.forecastLearned : (item.finishEpochDay.map { a.forecastDate(date: copy.plan.dateLabel(epochDay: $0.int32Value, today: ui.today)) } ?? "")
        let left: String? = learned ? nil : a.forecastLeft(ayahs: item.remainingAyahs)
        let sub = [current ? a.forecastCurrent : nil, left].compactMap { $0 }.joined(separator: " · ")
        HStack(spacing: QhSpacing.x3) {
            SurahNumberBadge(number: Int(item.surah), status: learned ? .learned : .notStarted, learnedFraction: learned ? 1 : 0, size: 36)
            VStack(alignment: .leading, spacing: 0) {
                Text(name).qhText({ $0.callout.weight("SemiBold") }, color: { current ? $0.onPrimaryContainer : $0.ink }).lineLimit(1)
                if !sub.isEmpty { Text(sub).qhText({ $0.label }, color: { current ? $0.onPrimaryContainer : $0.inkSecondary }) }
            }
            Spacer(minLength: 0)
            if !right.isEmpty {
                Text(right).qhText({ $0.label }, color: { learned ? $0.learned : (current ? $0.onPrimaryContainer : $0.inkSecondary) })
            }
        }
        .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3)
        .background(current ? c.primaryContainer : Color.clear)
        .pressable(shape: qh.shapes.smShape, action: action)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([name, current ? a.forecastCurrent : nil, left, right.isEmpty ? nil : right].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Hardest places

private struct HardPlacesSection: View {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let ui: AnalyticsUi

    var body: some View {
        let a = copy.analyticsCopy
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            SectionIntro(title: a.hardPlacesTitle, hint: a.hardPlacesHint)
            if ui.hardPlaces.isEmpty {
                QhCard { Text(a.hardPlacesEmpty).qhText({ $0.callout }, color: { $0.inkSecondary }) }
            } else {
                QhCard(padding: QhSpacing.x1) {
                    VStack(spacing: 0) {
                        ForEach(Array(ui.hardPlaces.enumerated()), id: \.offset) { index, place in
                            if index > 0 { QhRowDivider(startIndent: QhSpacing.card) }
                            let title = a.hardPlace(name: ui.nameOf(surah: place.surah), ayah: place.ayah)
                            let detail = [place.misses > 0 ? a.hardMisses(n: place.misses) : nil, place.current ? a.hardMarked : nil]
                                .compactMap { $0 }.joined(separator: " · ")
                            HStack(spacing: QhSpacing.x3) {
                                SurahNumberBadge(number: Int(place.surah), status: .notStarted, size: 36)
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(title).qhText({ $0.callout.weight("SemiBold") }).lineLimit(1)
                                    if !detail.isEmpty { Text(detail).qhText({ $0.label }, color: { $0.due }) }
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3)
                            .pressable(shape: qh.shapes.smShape) {
                                router.openReader(Int(place.surah), ayah: Int(place.ayah), origin: "map", in: .progress)
                            }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("\(title), \(detail)")
                            .accessibilityAddTraits(.isButton)
                        }
                    }
                }
                if ui.hardPlaces.contains(where: { $0.current }) {
                    QhButton(a.hardPlacesTrain, fullWidth: true) { router.push(.hardDrill, in: .progress) }
                }
            }
        }
    }
}
