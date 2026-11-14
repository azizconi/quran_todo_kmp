import ComposeApp
import SwiftUI

// MARK: - Rolling number

/// The values a counter shows on its way from `from` to `to` (`counterLadder` of `ProgressMotion.kt`): strictly monotonic, ending on
/// `to`. A short way is taken one by one, a long one in at most 12 eased strides (big first, small at the end).
func counterLadder(from: Int, to: Int) -> [Int] {
    let distance = abs(to - from)
    if distance == 0 { return [] }
    let direction = to > from ? 1 : -1
    if distance <= 12 { return (0..<distance).map { from + direction * ($0 + 1) } }
    var values: [Int] = []
    for step in 1...12 {
        let t = Double(step) / 12
        let eased = 1 - (1 - t) * (1 - t)
        let value = step == 12 ? to : from + Int((Double(to - from) * eased).rounded())
        if values.isEmpty || value != values.last { values.append(value) }
    }
    return values
}

/// A number that climbs from 0 to `target` in a few eased steps once the screen is on, so `AnimatedCounter` rolls its digits like an
/// odometer. Follows later changes of `target` the same way. `target` at once under Reduce Motion.
struct RollingCounter<Content: View>: View {
    @Environment(\.qhReduceMotion) private var reduced
    let target: Int
    var startDelay: Double = 0
    @ViewBuilder let content: (Int) -> Content
    @State private var shown: Int?
    @State private var started = false

    var body: some View {
        content(shown ?? (reduced ? target : 0))
            .task(id: target) {
                if reduced { shown = target; return }
                if !started {
                    try? await Task.sleep(for: .seconds(startDelay))
                    started = true
                }
                for value in counterLadder(from: shown ?? 0, to: target) {
                    try? await Task.sleep(for: .milliseconds(70))
                    if Task.isCancelled { return }
                    shown = value
                }
            }
    }
}

// MARK: - Ring

/// The ring with the word "hifz" in calligraphy over the percentage; the ring itself fills from 0 on first show.
struct BigRing: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let hero: HeroUi

    var body: some View {
        let p = copy.progress
        let description = p.ringDescription(percent: hero.percent, covered: hero.ringCovered, total: hero.ringTotal, learned: hero.ringLearned,
                                            scope: hero.planScope.map { p.scopeName(scope: $0) })
        QhProgressRing(progress: Double(hero.fraction), size: 160, lineWidth: 14, trackColor: qh.colors.primary.opacity(0.12)) {
            VStack(spacing: QhSpacing.x1) {
                CalligraphyText(Calligraphy.hifz, size: 20, revealDuration: 0.7, shimmer: false)
                RollingCounter(target: Int(hero.percent), startDelay: 0.25) { value in
                    Percentage(value: value, signFirst: p.percentFirst)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(description)
        .accessibilityValue("\(Int(hero.percent)) %")
    }
}

/// "42%" ("%42" in Turkish) with the sign as a small superscript.
private struct Percentage: View {
    @Environment(\.qh) private var qh
    let value: Int
    let signFirst: Bool

    var body: some View {
        let sign = Text("%").qhText({ $0.title1 }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x1)
        HStack(alignment: .top, spacing: 2) {
            if signFirst { sign }
            AnimatedCounter(value: value, style: { $0.display.sized(40) }, color: qh.colors.ink)
            if !signFirst { sign }
        }
    }
}

/// The hero of the screen: a big ring with the covered share of the PLAN counting up in its middle (the whole Quran without a plan),
/// "Juz Amma: 10 of 564 · 2 %" and the whole Quran as one line under it, and the surahs known / being learned.
struct RingHero: View {
    @Environment(Copy.self) private var copy
    let hero: HeroUi

    var body: some View {
        let p = copy.progress
        QhCard(style: .hero, padding: 0) {
            VStack(spacing: 0) {
                BigRing(hero: hero)
                Spacer().frame(height: QhSpacing.x5)
                if let scope = hero.planScope {
                    // By the plan: "Juz Amma: 10 of 564 · 2 %", and the whole Quran as one line under it.
                    Text(p.planRingLine(scope: scope, covered: hero.ringCovered, total: hero.ringTotal, percent: hero.percent))
                        .qhText({ $0.callout.weight("SemiBold") }).multilineTextAlignment(.center)
                    Text(p.wholeQuranLine(covered: hero.coveredAyahs, total: hero.totalAyahs, learned: hero.learnedAyahs))
                        .qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center).padding(.top, QhSpacing.x1)
                } else {
                    Text(p.heroAyahs(covered: hero.coveredAyahs, learned: hero.learnedAyahs))
                        .qhText({ $0.callout.weight("SemiBold") }).multilineTextAlignment(.center)
                }
                // "I know by heart: 3 surahs · Learning: 5 surahs"; a part that is zero is left out.
                if let line = p.surahsLine(learnedSurahs: hero.learnedSurahs, learningSurahs: hero.learningSurahs) {
                    Text(line).qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center).padding(.top, QhSpacing.x1)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, QhSpacing.cardLarge).padding(.vertical, QhSpacing.x7)
        }
    }
}

// MARK: - Plan and streak

/// The plan and the streak in one card: "Juz Amma: you will finish ~16 February" and under it whether the person is behind or ahead of
/// the date they set. A tap opens "My plan". Without a plan the card invites to make one.
struct PlanSection: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let line: PlanLineUi
    let streakDays: Int
    let today: Int32
    let onOpenPlan: () -> Void

    var body: some View {
        let p = copy.progress
        let c = qh.colors
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhSectionHeader(p.planTitle)
            if line.status == .noPlan {
                QhCard {
                    HStack(spacing: QhSpacing.x3) {
                        PlanIcon(tint: c.gold)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(p.planNoneTitle).qhText({ $0.title2 })
                            Text(p.planNoneBody).qhText({ $0.callout }, color: { $0.inkSecondary })
                        }
                    }
                    QhButton(p.planCreate, variant: .tonal, size: .m, action: onOpenPlan).padding(.top, QhSpacing.x3)
                    StreakLine(days: streakDays)
                }
            } else {
                let headline = p.planHeadline(line: line, today: today)
                let status = p.planStatusLine(line: line)
                let statusColor: Color = {
                    switch line.status {
                    case .behind, .needPace: return c.due
                    case .ahead, .onTrack: return c.learned
                    default: return c.inkSecondary
                    }
                }()
                QhCard(onTap: onOpenPlan) {
                    HStack(spacing: QhSpacing.x3) {
                        PlanIcon(tint: line.status == .finished ? c.learned : c.gold)
                        VStack(alignment: .leading, spacing: 0) {
                            if let headline { Text(headline).qhText({ $0.title2 }) }
                            if let status { Text(status).qhText({ $0.callout }, color: { _ in statusColor }) }
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.inkTertiary)
                    }
                    StreakLine(days: streakDays)
                }
            }
        }
    }
}

/// "5 days in a row" under the plan; nothing while there is no streak.
private struct StreakLine: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let days: Int

    var body: some View {
        if days > 0 {
            HStack(spacing: QhSpacing.x2) {
                Image(systemName: "flame.fill").font(.system(size: 16)).foregroundStyle(qh.colors.gold)
                Text("\(days)").qhText({ $0.title2 })
                Text(copy.progress.streakWords(n: Int32(days))).qhText({ $0.callout }, color: { $0.inkSecondary })
            }
            .padding(.top, QhSpacing.x3)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(days) \(copy.progress.streakWords(n: Int32(days)))")
        }
    }
}

private struct PlanIcon: View {
    let tint: Color
    var body: some View {
        Image(systemName: "calendar").font(.system(size: 17, weight: .semibold)).foregroundStyle(tint)
            .frame(width: 36, height: 36).background(tint.opacity(0.14), in: Circle())
    }
}
