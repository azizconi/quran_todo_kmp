import ComposeApp
import SwiftUI

// MARK: - Where an ayah sits in the repeated passage

enum RangePosition: Equatable { case none, single, start, middle, end }

func rangePosition(_ index: Int, _ range: ClosedRange<Int>?) -> RangePosition {
    guard let range, range.contains(index) else { return .none }
    if range.lowerBound == range.upperBound { return .single }
    if index == range.lowerBound { return .start }
    return index == range.upperBound ? .end : .middle
}

/// The wash and the gold bar of the current ayah (grows from the middle, 200 ms) and the gold bracket of a repeated passage.
struct AyahDecoration: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let marked: Bool
    let range: RangePosition

    var body: some View {
        let c = qh.colors
        ZStack(alignment: .leading) {
            c.readerHighlight.opacity(marked ? 1 : 0)
            Capsule().fill(c.gold).frame(width: 3).scaleEffect(y: marked ? 1 : 0.001).opacity(marked ? 1 : 0)
            if range != .none { RangeBracket(range: range, color: c.gold.opacity(0.85)) }
        }
        .animation(reduced ? nil : .easeOut(duration: 0.2), value: marked)
        .allowsHitTesting(false)
    }
}

struct RangeBracket: View {
    let range: RangePosition
    let color: Color

    var body: some View {
        Canvas { context, size in
            let x = size.width - 8, tick: CGFloat = 6, inset: CGFloat = 6
            let top: CGFloat = (range == .start || range == .single) ? inset : 0
            let bottom: CGFloat = (range == .end || range == .single) ? size.height - inset : size.height
            var line = Path()
            line.move(to: CGPoint(x: x, y: top)); line.addLine(to: CGPoint(x: x, y: bottom))
            if range == .start || range == .single { line.move(to: CGPoint(x: x, y: top)); line.addLine(to: CGPoint(x: x - tick, y: top)) }
            if range == .end || range == .single { line.move(to: CGPoint(x: x, y: bottom)); line.addLine(to: CGPoint(x: x - tick, y: bottom)) }
            context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        }
    }
}

// MARK: - Marks: line, sajda, similar, note

/// "Juz 2 - Hizb 3" on the left, a hairline, "p. 22" on the right. One heading for VoiceOver.
struct MushafMarkLineView: View {
    @Environment(\.qh) private var qh
    let start: String?
    let end: String?
    let spoken: String

    var body: some View {
        HStack(spacing: QhSpacing.x2) {
            if let start { Text(start).qhText({ $0.label }, color: { $0.inkTertiary }).lineLimit(1) }
            Rectangle().fill(qh.colors.hairline).frame(height: 1)
            if let end { Text(end).qhText({ $0.label }, color: { $0.inkTertiary }).lineLimit(1) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spoken)
        .accessibilityAddTraits(.isHeader)
    }
}

/// A pointed arch over a ground line with a dot in it: the mihrab, in strokes (never a star).
struct SajdaArch: View {
    var color: Color
    var size: CGFloat = 16

    var body: some View {
        Canvas { context, s in
            let w = s.width, h = s.height
            let left = 0.16 * w, right = 0.84 * w, shoulder = 0.5 * h, top = 0.06 * h, ground = 0.94 * h
            var arch = Path()
            arch.move(to: CGPoint(x: left, y: ground))
            arch.addLine(to: CGPoint(x: left, y: shoulder))
            arch.addCurve(to: CGPoint(x: 0.5 * w, y: top), control1: CGPoint(x: left, y: 0.24 * h), control2: CGPoint(x: 0.3 * w, y: 0.14 * h))
            arch.addCurve(to: CGPoint(x: right, y: shoulder), control1: CGPoint(x: 0.7 * w, y: 0.14 * h), control2: CGPoint(x: right, y: 0.24 * h))
            arch.addLine(to: CGPoint(x: right, y: ground))
            let style = StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round)
            context.stroke(arch, with: .color(color), style: style)
            var floor = Path()
            floor.move(to: CGPoint(x: 0.04 * w, y: ground)); floor.addLine(to: CGPoint(x: 0.96 * w, y: ground))
            context.stroke(floor, with: .color(color), style: style)
            let r = 0.085 * w
            context.fill(Path(ellipseIn: CGRect(x: 0.5 * w - r, y: 0.62 * h - r, width: 2 * r, height: 2 * r)), with: .color(color))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct SajdaSign: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    let sajda: Sajda

    var body: some View {
        let label = copy.mushaf.sajdaLabel(mark: sajda)
        HStack(spacing: 6) {
            SajdaArch(color: qh.colors.gold)
            Text(label).qhText({ $0.label }, color: { $0.gold }).lineLimit(1)
        }
        .padding(.leading, 8).padding(.trailing, 12).padding(.vertical, 5)
        .background(qh.colors.goldContainer, in: Capsule())
        .frame(minHeight: QhSpacing.minTouch)
        .pressable(shape: Capsule()) { env.toaster.show(copy.mushaf.sajdaExplain) }
        .accessibilityLabel("\(label). \(copy.mushaf.sajdaExplain)")
    }
}

/// Two overlapping rings in the secondary ink (`SimilarIcon`).
struct SimilarRings: View {
    @Environment(\.qh) private var qh
    var size: CGFloat = 22
    var color: Color?

    var body: some View {
        let tint = color ?? qh.colors.inkSecondary
        ZStack {
            Circle().stroke(tint, lineWidth: 1.6).frame(width: size * 0.56, height: size * 0.56).offset(x: -size * 0.14)
            Circle().stroke(tint, lineWidth: 1.6).frame(width: size * 0.56, height: size * 0.56).offset(x: size * 0.14)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

// MARK: - Header

struct ReaderHeaderView: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let model: ReaderModel
    let celebration: Int
    let enter: CGFloat
    let onStatus: () -> Void
    let onTitle: () -> Void
    let offerVisible: Bool
    let onOffer: (Bool) -> Void

    var body: some View {
        let n = model.number
        let reader = copy.reader
        let total = model.ayahs.count
        let revelation = model.surah.map { reader.revelationPlace(value: $0.surah.revelationType) } ?? ""
        let meta = "\(revelation) \u{00B7} \(reader.ayahCount(count: Int32(total)))"
        let status = model.status.value
        VStack(spacing: 0) {
            SurahNumberBadge(number: n, status: rdStatus(status), learnedFraction: model.learnedFraction, size: 44)
                .padding(.bottom, QhSpacing.x3)
            SurahCartouche {
                Text(model.arabicName())
                    .qhArabic(qh.type.quranHeader(ReadingPrefs.shared.face, size: 34), color: qh.colors.ink)
                    .multilineTextAlignment(.center)
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .padding(.vertical, QhSpacing.x2)
            }
            Button(action: onTitle) {
                HStack(spacing: 2) {
                    Text(model.title(copy: copy)).qhText({ $0.title1 }).multilineTextAlignment(.center)
                    Image(systemName: "arrowtriangle.down.fill").font(.system(size: 9)).foregroundStyle(qh.colors.inkSecondary)
                }
                .padding(.vertical, 6).padding(.horizontal, 10)
            }
            .buttonStyle(.plain)
            .padding(.top, QhSpacing.x3)
            .accessibilityLabel(model.title(copy: copy))
            .accessibilityHint(copy.goTo.title)
            .accessibilityAddTraits(.isHeader)

            VStack(spacing: 0) {
                if !model.meaning().isEmpty {
                    Text(model.meaning()).qhText({ $0.body }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                }
                HStack(spacing: QhSpacing.x2) {
                    Text(meta).qhText({ $0.callout }, color: { $0.inkSecondary })
                    ZStack {
                        StatusPill(status: rdStatus(status), label: statusLabel(reader, status), onTap: onStatus)
                        CelebrationBurst(trigger: celebration, radius: 90).allowsHitTesting(false)
                    }
                }
                .padding(.top, QhSpacing.x1)
                OrnamentDivider().padding(.horizontal, QhSpacing.x8).padding(.vertical, QhSpacing.x5)
                if QuranDisplay.hasBismillahLine(n) {
                    Text(QuranDisplay.bismillahText)
                        .qhArabic(qh.type.quranBody(ReadingPrefs.shared.face, size: CGFloat(ReadingPrefs.shared.size)), color: qh.colors.ink)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.bottom, QhSpacing.x2)
                        .accessibilityLabel("\u{0628}\u{0650}\u{0633}\u{0652}\u{0645}\u{0650} \u{0671}\u{0644}\u{0644}\u{0651}\u{064E}\u{0647}")
                }
                if offerVisible {
                    TranslationOfferLine(onAnswer: onOffer)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            .opacity(enter)
            .offset(y: (1 - enter) * 24)
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x2)
        .frame(maxWidth: .infinity)
    }

    private func statusLabel(_ c: ReaderCopy, _ status: SurahTodoStatus?) -> String {
        switch status {
        case .some(.learned): return c.learned
        case .some(.learning): return c.learning
        default: return c.notStarted
        }
    }
}

/// U22: the question about the translation, once.
struct TranslationOfferLine: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let onAnswer: (Bool) -> Void

    var body: some View {
        let t = copy.ayahActions
        VStack(spacing: QhSpacing.x2) {
            Text(t.offerQuestion).qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
            HStack(spacing: QhSpacing.x2) {
                QhButton(t.yes, variant: .tonal, size: .s) { onAnswer(true) }
                QhButton(t.no, variant: .text, size: .s) { onAnswer(false) }
            }
        }
        .padding(.vertical, QhSpacing.x2)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Action row under an ayah

/// Listen from here / Repeat this ayah / More (U19). Each chip gets its label width; icons drop before a word would break.
struct AyahActionRow: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let onListen: () -> Void
    let onRepeat: () -> Void
    let onMore: () -> Void

    var body: some View {
        let t = copy.ayahActions
        ViewThatFits(in: .horizontal) {
            row(t, icons: true)
            row(t, icons: false)
        }
        .padding(.top, QhSpacing.x3)
    }

    private func row(_ t: AyahActionsCopy, icons: Bool) -> some View {
        HStack(spacing: QhSpacing.x2) {
            chip(t.listen, icon: icons ? "play.fill" : nil, tint: qh.colors.primary, container: qh.colors.primaryContainer, action: onListen)
            chip(t.repeat, icon: icons ? "repeat" : nil, tint: qh.colors.primary, container: qh.colors.primaryContainer, action: onRepeat)
            chip(t.more, icon: icons ? "ellipsis" : nil, tint: qh.colors.inkSecondary, container: qh.colors.surfaceMuted, action: onMore)
        }
        .fixedSize(horizontal: true, vertical: false)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func chip(_ label: String, icon: String?, tint: Color, container: Color, action: @escaping () -> Void) -> some View {
        HStack(spacing: 6) {
            if let icon { Image(systemName: icon).font(.system(size: 13, weight: .semibold)) }
            Text(label).qhText({ $0.label }, color: { _ in tint }).lineLimit(1)
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 14).frame(minHeight: 40)
        .background(container, in: Capsule())
        .pressable(shape: Capsule(), action: action)
    }
}

// MARK: - Floating chips above the dock

struct FloatingChip: View {
    @Environment(\.qh) private var qh
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 13, weight: .bold))
            Text(label).qhText({ $0.label }, color: { $0.onPrimary }).lineLimit(1)
        }
        .foregroundStyle(qh.colors.onPrimary)
        .padding(.horizontal, 16).frame(minHeight: 40)
        .background(qh.colors.primary, in: Capsule())
        .qhSoftShadow()
        .pressable(shape: Capsule(), action: action)
        .accessibilityLabel(label)
    }
}

// MARK: - End of the surah

struct NextSurahCardView: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let next: NextSurahDisplay?
    let onOpen: () -> Void
    let onStartOver: () -> Void

    var body: some View {
        let r = copy.reader
        Group {
            if let next {
                QhCard(onTap: onOpen) {
                    HStack(spacing: QhSpacing.x3) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(r.nextSurah).qhText({ $0.overline }, color: { $0.inkTertiary })
                            Text(next.title).qhText({ $0.title1 }).lineLimit(2)
                            if !next.meaning.isEmpty { Text(next.meaning).qhText({ $0.callout }, color: { $0.inkSecondary }) }
                        }
                        Spacer(minLength: 0)
                        Text(next.arabicName).qhArabic(qh.type.quranInline(ReadingPrefs.shared.face, size: 22), color: qh.colors.primary).lineLimit(1)
                        Image(systemName: "chevron.forward").font(.system(size: 14, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
                    }
                }
                .accessibilityLabel("\(r.nextSurah): \(next.title)")
            } else {
                QhCard {
                    VStack(spacing: QhSpacing.x3) {
                        Text(r.quranFinished).qhText({ $0.title1 }).multilineTextAlignment(.center)
                        QhButton(r.startOver, variant: .tonal, size: .m, action: onStartOver)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x6)
    }
}
