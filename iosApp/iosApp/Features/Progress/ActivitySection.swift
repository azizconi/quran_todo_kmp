import ComposeApp
import SwiftUI

/// Activity over the days given (the week, 7 days): one stacked bar per day, the ayahs repeated at the bottom and the new ones on top, in
/// two tones (and a legend). Empty days are a short hairline, so they are visible. The bars grow one after another when the section
/// appears; a tap on a day, or a finger dragged along the bars, shows its date and numbers in a bubble above the bar (`ActivityChart.kt`).
struct ActivitySection: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let activity: ActivityUi

    var body: some View {
        let p = copy.progress
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhSectionHeader(p.activityTitle) {
                if let summary = p.activitySummary(learned: activity.totalLearned, reviewed: activity.totalReviewed) {
                    Text(summary).qhText({ $0.callout }, color: { $0.inkSecondary })
                }
            }
            QhCard {
                ActivityChart(days: activity.days)
                Spacer().frame(height: QhSpacing.x3)
                HStack(spacing: QhSpacing.x4) {
                    LegendDot(color: qh.colors.primary, label: p.activityNew)
                    LegendDot(color: reviewedTone(qh.colors.gold), label: p.activityReviewed)
                }
                listened
                if activity.isEmpty {
                    Text(p.activityEmpty).qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity).padding(.top, QhSpacing.x3)
                }
            }
        }
    }

    /// "Listened: 3 passages" ("Listen to the old"): a line of its own under the legend, not part of the bars and not a repetition.
    @ViewBuilder private var listened: some View {
        let count = activity.listenedToday > 0 ? Int(activity.listenedToday) : Int(activity.totalListened)
        if count > 0 {
            HStack(spacing: QhSpacing.x2) {
                Image(systemName: "headphones").font(.system(size: 13)).foregroundStyle(qh.colors.inkSecondary)
                Text(copy.listen.progressListened(count: Int32(count))).qhText({ $0.callout }, color: { $0.inkSecondary })
            }
            .padding(.top, QhSpacing.x2)
        }
    }
}

/// The tone of the repeated ayahs, a little softer than the new ones so the stack reads as two parts.
func reviewedTone(_ gold: Color) -> Color { gold.opacity(0.7) }

private struct LegendDot: View {
    @Environment(\.qh) private var qh
    let color: Color
    let label: String
    var body: some View {
        HStack(spacing: QhSpacing.x2) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(label).qhText({ $0.callout }, color: { $0.inkSecondary })
        }
    }
}

private let barsHeight: CGFloat = 112
private let bubbleHeadroom: CGFloat = 44
private let stubHeight: CGFloat = 3
private let minBarHeight: CGFloat = 8
private let maxBarWidth: CGFloat = 9

private struct ActivityChart: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let days: [ActivityDayUi]
    @State private var chosen: Int?
    @State private var heights: [CGFloat] = []
    @State private var width: CGFloat = 0

    var body: some View {
        let count = days.count
        let peak = max(days.map { Int($0.total) }.max() ?? 0, 1)
        VStack(spacing: QhSpacing.x2) {
            ZStack(alignment: .topLeading) {
                Canvas { gc, size in
                    guard count > 0 else { return }
                    let slot = size.width / CGFloat(count)
                    let barWidth = min(slot * 0.62, maxBarWidth)
                    let floor = size.height
                    var line = Path()
                    line.move(to: CGPoint(x: 0, y: floor - 0.5)); line.addLine(to: CGPoint(x: size.width, y: floor - 0.5))
                    gc.stroke(line, with: .color(qh.colors.hairline), lineWidth: 1)
                    for (index, day) in days.enumerated() {
                        let left = slot * CGFloat(index) + (slot - barWidth) / 2
                        let total = Int(day.total)
                        if total == 0 {
                            let today = index == count - 1
                            gc.fill(Path(roundedRect: CGRect(x: left, y: floor - stubHeight, width: barWidth, height: stubHeight), cornerRadius: stubHeight / 2),
                                    with: .color(today ? qh.colors.primary.opacity(0.45) : qh.colors.hairline))
                        } else {
                            let share = heights.indices.contains(index) ? heights[index] : 0
                            let height = max(minBarHeight, share * barsHeight)
                            let top = floor - height
                            let reviewedPx = height * CGFloat(day.reviewed) / CGFloat(total)
                            let outline = Path(roundedRect: CGRect(x: left, y: top, width: barWidth, height: height), cornerRadius: min(4, barWidth / 2))
                            gc.drawLayer { layer in
                                layer.clip(to: outline)
                                layer.fill(Path(CGRect(x: left, y: floor - reviewedPx, width: barWidth, height: reviewedPx)), with: .color(reviewedTone(qh.colors.gold)))
                                layer.fill(Path(CGRect(x: left, y: top, width: barWidth, height: height - reviewedPx)), with: .color(qh.colors.primary))
                            }
                        }
                        if chosen == index {
                            let height = total == 0 ? stubHeight : max(minBarHeight, (heights.indices.contains(index) ? heights[index] : 0) * barsHeight)
                            gc.stroke(Path(roundedRect: CGRect(x: left - 2, y: floor - height - 2, width: barWidth + 4, height: height + 4), cornerRadius: 6),
                                      with: .color(qh.colors.ink), lineWidth: 1.5)
                        }
                    }
                }
                .frame(height: barsHeight + bubbleHeadroom)
                .background(GeometryReader { geo in Color.clear.onAppear { width = geo.size.width }.onChange(of: geo.size.width) { _, w in width = w } })
                .contentShape(Rectangle())
                // One touch layer over the whole chart picks the nearest day, by a tap or by a finger dragged along.
                .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                    let index = slotOf(value.location.x, count: count)
                    if chosen != index { QhHaptics.shared.perform(.segment) }
                    chosen = index
                }.onEnded { value in
                    // A tap on the chosen day again puts it away.
                    if abs(value.translation.width) < 4, abs(value.translation.height) < 4, chosen == slotOf(value.startLocation.x, count: count), tappedAgain { chosen = nil }
                    tappedAgain = chosen != nil
                })
                if let chosen, days.indices.contains(chosen), width > 0 {
                    let day = days[chosen]
                    let total = Int(day.total)
                    let barHeight = total == 0 ? stubHeight : max(minBarHeight, CGFloat(total) / CGFloat(peak) * barsHeight)
                    ActivityBubble(text: "\(label(day, today: chosen == count - 1)) · \(value(day))", anchorX: width / CGFloat(count) * (CGFloat(chosen) + 0.5),
                                   anchorTop: bubbleHeadroom + barsHeight - barHeight, width: width)
                        .id(chosen)
                }
            }
            DayLabels(days: days)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(copy.progress.activityTitle)
        .accessibilityValue(days.enumerated().map { "\(label($1, today: $0 == count - 1)): \(value($1))" }.joined(separator: ". "))
        .task(id: days.map { "\($0.epochDay):\($0.total)" }) { await grow(peak: peak) }
        .task(id: chosen) {
            guard chosen != nil else { return }
            try? await Task.sleep(for: .seconds(4.5))
            if !Task.isCancelled { chosen = nil }
        }
    }

    @State private var tappedAgain = false

    private func slotOf(_ x: CGFloat, count: Int) -> Int {
        width > 0 && count > 0 ? min(max(Int(x / width * CGFloat(count)), 0), count - 1) : 0
    }

    /// The bars start at 0 and grow one after another the first time; later changes (a passage learned while open) just move the bar.
    private func grow(peak: Int) async {
        let count = days.count
        if heights.count != count { heights = Array(repeating: 0, count: count) }
        let first = heights.allSatisfy { $0 == 0 }
        for (index, day) in days.enumerated() {
            let target = CGFloat(day.total) / CGFloat(peak)
            if reduced { heights[index] = target; continue }
            if first {
                try? await Task.sleep(for: .milliseconds(index == 0 ? 150 : 12))
                if Task.isCancelled { return }
            }
            withAnimation(QhMotion.gentle) { heights[index] = target }
        }
    }

    private func label(_ day: ActivityDayUi, today: Bool) -> String {
        if today { return copy.progress.today }
        // epoch day -> day of month and month number
        let date = Date(timeIntervalSince1970: TimeInterval(day.epochDay) * 86_400)
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        let c = calendar.dateComponents([.day, .month], from: date)
        return copy.progress.date(day: Int32(c.day ?? 1), month: Int32(c.month ?? 1))
    }

    /// "New 3 · Reviewed 5", and on a line of its own "Listened: 2 passages" when the day has any (never part of the bar).
    private func value(_ day: ActivityDayUi) -> String {
        let p = copy.progress
        if day.listened <= 0 { return p.activityValue(learned: day.learned, reviewed: day.reviewed) }
        let study = day.total == 0 ? nil : p.activityValue(learned: day.learned, reviewed: day.reviewed)
        return [study, copy.listen.progressListened(count: day.listened)].compactMap { $0 }.joined(separator: "\n")
    }
}

/// Dates under the bars: every other bar for a week, and "Today" under the last.
private struct DayLabels: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let days: [ActivityDayUi]

    var body: some View {
        let count = days.count
        HStack(spacing: 0) {
            ForEach(Array(days.enumerated()), id: \.offset) { index, day in
                let today = index == count - 1
                let labelled = count <= 8 ? ((index % 2 == 0 && index <= count - 2) || today) : ((index % 6 == 0 && index <= count - 9) || today)
                ZStack(alignment: index == 0 ? .leading : (today ? .trailing : .center)) {
                    if labelled {
                        Text(text(day, today: today)).qhText({ $0.label.sized(11).weight(today ? "Bold" : "Medium") }, color: { today ? $0.primary : $0.inkSecondary })
                            .lineLimit(1).fixedSize()
                    }
                    Color.clear
                }
                .frame(maxWidth: .infinity)
            }
        }
        .accessibilityHidden(true)
    }

    private func text(_ day: ActivityDayUi, today: Bool) -> String {
        if today { return copy.progress.today }
        let date = Date(timeIntervalSince1970: TimeInterval(day.epochDay) * 86_400)
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        let c = calendar.dateComponents([.day, .month], from: date)
        return copy.progress.date(day: Int32(c.day ?? 1), month: Int32(c.month ?? 1))
    }
}

/// A small inverse pill above a bar, placed within the chart width; it pops in with the snappy spring.
private struct ActivityBubble: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let text: String
    let anchorX: CGFloat
    let anchorTop: CGFloat
    let width: CGFloat
    @State private var appear: CGFloat = 0
    @State private var size: CGSize = .zero

    var body: some View {
        Text(text).qhText({ $0.label }, color: { $0.bg }).lineLimit(3)
            .padding(.horizontal, QhSpacing.x3).padding(.vertical, QhSpacing.x2)
            .background(qh.colors.ink, in: qh.shapes.smShape)
            .background(GeometryReader { geo in Color.clear.onAppear { size = geo.size }.onChange(of: geo.size) { _, s in size = s } })
            .scaleEffect(0.9 + 0.1 * appear).opacity(appear)
            .offset(x: min(max(anchorX - size.width / 2, 0), max(width - size.width, 0)), y: max(anchorTop - size.height - 6, 0))
            .allowsHitTesting(false)
            .onAppear { if reduced { appear = 1 } else { withAnimation(QhMotion.snappy) { appear = 1 } } }
    }
}

// MARK: - Hard spots

/// "Hard spots" with a "Train" button that opens the drill: the surahs with difficult ayahs, the most affected first. Each row names the
/// surah and has a chip for every hard ayah; a chip opens the reader at that ayah. Without hard ayahs a calm card says so.
struct HardSpotsSection: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let spots: [HardSpotUi]
    let onOpenAyah: (Int, Int) -> Void
    let onTrain: () -> Void
    @State private var expanded = false

    private let collapsedRows = 5

    var body: some View {
        let p = copy.progress
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhSectionHeader(p.hardTitle)
            if spots.isEmpty {
                QhCard {
                    HStack(spacing: QhSpacing.x3) {
                        Image(systemName: "checkmark").font(.system(size: 16, weight: .bold)).foregroundStyle(qh.colors.learned)
                            .frame(width: 36, height: 36).background(qh.colors.learned.opacity(0.14), in: Circle())
                        VStack(alignment: .leading, spacing: 0) {
                            Text(p.hardNoneTitle).qhText({ $0.title2 })
                            Text(p.hardNoneBody).qhText({ $0.callout }, color: { $0.inkSecondary })
                        }
                    }
                }
            } else {
                let visible = expanded ? spots : Array(spots.prefix(collapsedRows))
                QhCard(padding: QhSpacing.x1) {
                    VStack(spacing: 0) {
                        ForEach(Array(visible.enumerated()), id: \.element.surah) { index, spot in
                            if index > 0 { QhRowDivider() }
                            HardSpotRow(spot: spot, onOpenAyah: onOpenAyah)
                        }
                        if spots.count > collapsedRows {
                            QhRowDivider()
                            QhButton(expanded ? p.hardLess : p.hardMore(n: Int32(spots.count - collapsedRows)), variant: .text, size: .s,
                                     icon: expanded ? "chevron.up" : "chevron.down") { withQhAnimation(QhMotion.gentle) { expanded.toggle() } }
                                .frame(maxWidth: .infinity).padding(.vertical, QhSpacing.x1)
                        }
                    }
                }
                // The drill of the hard ayahs: the way from "these are hard" to doing something about it.
                QhButton(copy.drillCopy.tileTrain, fullWidth: true, action: onTrain)
            }
        }
    }
}

private struct HardSpotRow: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let spot: HardSpotUi
    let onOpenAyah: (Int, Int) -> Void
    private let maxChips = 12

    var body: some View {
        let p = copy.progress
        let ayahs = spot.ayahs.map { Int(truncating: $0) }
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            HStack(spacing: QhSpacing.x3) {
                SurahNumberBadge(number: Int(spot.surah), status: .notStarted)
                VStack(alignment: .leading, spacing: 0) {
                    Text(spot.name).qhText({ $0.title2 }).lineLimit(1)
                    Text(p.hardCount(n: Int32(ayahs.count))).qhText({ $0.callout }, color: { $0.due })
                }
            }
            ChipFlowLayout(spacing: QhSpacing.x2) {
                ForEach(Array(ayahs.prefix(maxChips)), id: \.self) { ayah in
                    QhChip(label: "\(ayah)", selected: false) { onOpenAyah(Int(spot.surah), ayah) }
                        .accessibilityLabel(p.hardAyahDescription(name: spot.name, ayah: Int32(ayah)))
                }
                if ayahs.count > maxChips {
                    Text("+\(ayahs.count - maxChips)").qhText({ $0.label }, color: { $0.inkSecondary })
                        .padding(.horizontal, QhSpacing.x2).padding(.vertical, QhSpacing.x2)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(QhSpacing.card)
    }
}

/// Wraps its children onto as many lines as they need.
struct ChipFlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 0

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(width: proposal.width ?? .infinity, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(width: bounds.width, subviews: subviews)
        for (i, origin) in result.origins.enumerated() {
            subviews[i].place(at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y), proposal: .unspecified)
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> (size: CGSize, origins: [CGPoint]) {
        var origins: [CGPoint] = []
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width { x = 0; y += rowHeight + lineSpacing; rowHeight = 0 }
            origins.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            maxX = max(maxX, x - spacing)
        }
        return (CGSize(width: maxX, height: y + rowHeight), origins)
    }
}
