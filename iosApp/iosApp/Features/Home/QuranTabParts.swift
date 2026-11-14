import ComposeApp
import SwiftUI

private func qhStatus(_ status: SurahTodoStatus?) -> QhStatus {
    switch status {
    case .some(.learning): return .learning
    case .some(.learned): return .learned
    default: return .notStarted
    }
}

/// "Meaning · N ayahs".
@MainActor private func subtitle(_ translated: String, _ ayahCount: Int32, _ copy: Copy) -> String {
    [translated, copy.home.ayahCount(n: ayahCount)].filter { !$0.isEmpty }.joined(separator: " · ")
}

/// What a screen reader says about the status of a row.
@MainActor private func stateText(_ status: SurahTodoStatus?, _ fraction: Float, _ copy: Copy) -> String {
    switch status {
    case .none: return copy.home.statusNotStarted
    case .some(.learned): return copy.home.statusLearned
    case .some(.learning):
        let percent = Int((fraction * 100).rounded())
        return percent <= 0 ? copy.home.statusLearning : copy.home.learningPercentPattern.replacingOccurrences(of: "{n}", with: String(percent))
    }
}

// MARK: - Lesson strip

/// The compact "Today's lesson" card on top of the Quran tab (`LessonStripCard.kt`): one line of state and a small button that opens
/// the lesson (zooming out of the button); a tap on the card opens the Lesson tab. The words come from the shared `LessonStrip`.
struct LessonStripView: View {
    let strip: LessonStripUi
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        QhCard(padding: 0, onTap: { router.selectedTab = .lesson }) {
            let layout = typeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: QhSpacing.x3))
                : AnyLayout(HStackLayout(spacing: QhSpacing.x3))
            layout {
                VStack(alignment: .leading, spacing: QhSpacing.x1) {
                    Text(copy.home.lessonTitle.uppercased()).qhText({ $0.overline }, color: { $0.primary }).lineLimit(1)
                    HStack(spacing: QhSpacing.x2) {
                        if strip.done {
                            Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(qh.colors.learned)
                        }
                        Text(strip.text).qhText({ $0.callout }, color: { strip.done ? $0.inkSecondary : $0.ink }).lineLimit(2)
                    }
                }
                if !typeSize.isAccessibilitySize { Spacer(minLength: QhSpacing.x2) }
                if let action = strip.action {
                    QhButton(action, size: .s) { router.push(.lesson) }
                        .fixedSize()
                        .qhZoomSource(QhZoomID.lessonStrip)
                }
            }
            .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x3)
        }
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Hero

/// The "Continue" hero: the surah the person stopped at, how far, and the two ways on: read or listen.
struct HeroCardView: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.dynamicTypeSize) private var type
    let item: ContinueUi
    let playing: Bool
    let onRead: () -> Void
    let onListen: () -> Void

    var body: some View {
        let position = item.isFresh
            ? [item.translatedName, copy.home.ayahCount(n: item.ayahCount)].filter { !$0.isEmpty }.joined(separator: " · ")
            : copy.home.ayahOf(ayah: item.ayah, total: item.ayahCount)
        QhCard(style: .hero, padding: QhSpacing.cardLarge) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: QhSpacing.x2) {
                    Text((item.isFresh ? copy.home.startWithFatiha : copy.home.continueLabel).uppercased())
                        .qhText({ $0.overline }, color: { $0.primary }).lineLimit(2)
                    Text(item.name).qhText({ $0.headline }).lineLimit(2)
                }
                Spacer(minLength: QhSpacing.x3)
                if !item.arabicName.isEmpty {
                    Text(item.arabicName).qhArabic(qh.type.quranHeader(.amiri, size: 30), color: qh.colors.primary).lineLimit(1)
                }
            }
            Text(position).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1).padding(.top, QhSpacing.x2)
            if !item.isFresh {
                QhLinearProgress(progress: Double(item.progress), trackColor: qh.colors.surface.opacity(0.7))
                    .padding(.top, QhSpacing.x3)
                    .accessibilityLabel(position)
            }
            Group {
                if type.isAccessibilitySize {
                    VStack(spacing: QhSpacing.x2) { buttons }
                } else {
                    HStack(spacing: QhSpacing.x3) { buttons }
                }
            }
            .padding(.top, QhSpacing.x5)
        }
    }

    @ViewBuilder private var buttons: some View {
        QhButton(copy.home.read, size: .m, icon: "book", fullWidth: true, action: onRead)
        QhButton(playing ? copy.home.pause : copy.home.listen, variant: .tonal, size: .m,
                 icon: playing ? "waveform" : "headphones", fullWidth: true, action: onListen)
            .accessibilityValue(playing ? copy.home.pause : "")
    }
}

// MARK: - Recent

/// The "Recent" chips: pinned first with a pin, long press opens the surah sheet.
struct RecentSection: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let recent: [RecentUi]
    let onOpen: (Int32) -> Void
    let onLongPress: (Int32) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            QhSectionHeader(copy.home.recent).padding(.horizontal, QhSpacing.screen)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: QhSpacing.x2) {
                    ForEach(recent, id: \.surah) { item in
                        HStack(spacing: 6) {
                            if item.pinned {
                                Image(systemName: "pin.fill").font(.system(size: 11, weight: .semibold)).foregroundStyle(qh.colors.gold)
                            }
                            Text(item.name).qhText({ $0.callout.weight("SemiBold") }).lineLimit(1)
                        }
                        .padding(.horizontal, 14)
                        .frame(minHeight: QhSpacing.minTouch - 8)
                        .background(qh.colors.surface, in: qh.shapes.full)
                        .overlay { qh.shapes.full.stroke(qh.colors.hairline, lineWidth: 1) }
                        .pressable(shape: Capsule(), onLongPress: { onLongPress(item.surah) }) { onOpen(item.surah) }
                        .accessibilityLabel(item.pinned ? "\(item.name), \(copy.home.pinned)" : item.name)
                        .accessibilityHint(copy.home.open)
                    }
                }
                .padding(.horizontal, QhSpacing.screen)
            }
        }
    }
}

// MARK: - Rows

struct SurahRowView: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let row: SurahRowUi
    let celebrate: Int
    let onOpen: () -> Void
    let onLongPress: () -> Void

    var body: some View {
        let sub = subtitle(row.translatedName, row.ayahCount, copy)
        HStack(spacing: 14) {
            ZStack {
                SurahNumberBadge(number: Int(row.number), status: qhStatus(row.status), learnedFraction: Double(row.learnedFraction), size: 40)
                if celebrate != 0 { CelebrationBurst(trigger: celebrate, radius: 44, haptic: nil) }
            }
            .frame(width: 40, height: 40)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(row.name).qhText({ $0.title2 }).lineLimit(2)
                    if row.isPinned {
                        Image(systemName: "pin.fill").font(.system(size: 12)).foregroundStyle(qh.colors.gold)
                    }
                    if row.inPlan {
                        Text(copy.home.inPlan).qhText({ $0.label }, color: { $0.gold }).lineLimit(1)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(qh.colors.goldContainer, in: qh.shapes.full)
                    }
                }
                if !sub.isEmpty {
                    Text(sub).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(2)
                }
            }
            Spacer(minLength: QhSpacing.x2)
            if !row.arabicName.isEmpty {
                Text(row.arabicName).qhArabic(qh.type.quranInline(.amiri), color: qh.colors.primary).lineLimit(1)
            }
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.vertical, QhSpacing.x3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .pressable(haptic: .tick, scale: 0.985, onLongPress: onLongPress, action: onOpen)
        .disabled(!row.canOpen)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([row.name, sub, row.isPinned ? copy.home.pinned : "", row.inPlan ? copy.home.inPlan : ""]
            .filter { !$0.isEmpty }.joined(separator: ", "))
        .accessibilityValue(stateText(row.status, row.learnedFraction, copy))
        .accessibilityHint(copy.home.open)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: Text(copy.home.sheetStatus)) { onLongPress() }
    }
}

struct JuzRowView: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let row: JuzRowUi
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            SurahNumberBadge(number: Int(row.juz), status: qhStatus(row.status), learnedFraction: Double(row.fraction), size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.subtitle).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(2)
            }
            Spacer(minLength: QhSpacing.x2)
            if !row.arabicName.isEmpty {
                Text(row.arabicName).qhArabic(qh.type.quranInline(.amiri), color: qh.colors.primary).lineLimit(1)
            }
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.vertical, QhSpacing.x3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .pressable(haptic: .tick, scale: 0.985, action: onOpen)
        .disabled(!row.canOpen)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(row.juz), \(row.subtitle)")
        .accessibilityAddTraits(.isButton)
    }
}

/// "Al-Baqarah, ayah 255 - Open" for a query like `2:255`.
struct SmartResultCard: View {
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    let result: SmartResultUi
    let onOpen: () -> Void

    var body: some View {
        QhCard(padding: QhSpacing.card, onTap: onOpen) {
            HStack(spacing: QhSpacing.x3) {
                SurahNumberBadge(number: Int(result.row.number), status: qhStatus(result.row.status),
                                 learnedFraction: Double(result.row.learnedFraction), size: 40)
                Text(copy.home.smartTitle(name: result.row.name, ayah: result.ayah)).qhText({ $0.title2 }).lineLimit(2)
                Spacer(minLength: QhSpacing.x2)
                Text(copy.home.smartOpen).qhText({ $0.callout.weight("SemiBold") }, color: { $0.primary })
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(qh.colors.primary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(copy.home.smartTitle(name: result.row.name, ayah: result.ayah)), \(copy.home.smartOpen)")
        .accessibilityAddTraits(.isButton)
    }
}

/// "No result" / "no surah with this status".
struct ListEmptyState: View {
    @Environment(Copy.self) private var copy
    let searching: Bool
    let filter: SurahFilter

    var body: some View {
        let title: String
        let body: String
        if searching {
            (title, body) = (copy.home.noResultsTitle, copy.home.noResultsBody)
        } else if filter == .learning {
            (title, body) = (copy.home.emptyLearningTitle, copy.home.emptyLearningBody)
        } else {
            (title, body) = (copy.home.emptyLearnedTitle, copy.home.emptyLearnedBody)
        }
        return QhEmptyState(title: title, message: body)
    }
}
