import ComposeApp
import Observation
import SwiftUI

/// The similar verses of the app (task F6) as an `@Observable`: the index (read once by `SimilarStore`) for the marks, and the state of
/// the sheet, which one host at the root shows over any screen.
@MainActor
@Observable
final class SimilarService {
    static let shared = SimilarService()

    private(set) var index: SimilarIndex?
    private(set) var sheet: SimilarSheetUi?
    private(set) var sheetKey: AyahKey?
    private(set) var names: [KotlinInt: ChapterNameModel] = [:]
    @ObservationIgnored private var tasks: [Task<Void, Never>] = []
    @ObservationIgnored private let store = IosUi.shared.similar

    private init() {
        index = store.index.value
        sheet = store.sheet.value
        sheetKey = store.sheetAyah.value
        names = store.chapterNames.value
        tasks.append(Task { [weak self] in
            guard let flow = self?.store.index else { return }
            do { for try await v in flow { self?.index = v } } catch {}
        })
        tasks.append(Task { [weak self] in
            guard let flow = self?.store.sheet else { return }
            do { for try await v in flow { self?.sheet = v } } catch {}
        })
        tasks.append(Task { [weak self] in
            guard let flow = self?.store.sheetAyah else { return }
            do { for try await v in flow { self?.sheetKey = v } } catch {}
        })
        tasks.append(Task { [weak self] in
            guard let flow = self?.store.chapterNames else { return }
            do { for try await v in flow { self?.names = v } } catch {}
        })
    }

    func hasSimilar(surah: Int, ayah: Int) -> Bool {
        guard ReadingPrefs.shared.similarMarks, let index else { return false }
        return index.has(ref: AyahKey(surah: Int32(surah), ayah: Int32(ayah)))
    }

    func count(surah: Int, ayah: Int) -> Int {
        Int(index?.count(ref: AyahKey(surah: Int32(surah), ayah: Int32(ayah))) ?? 0)
    }

    func open(surah: Int, ayah: Int) { store.openSheet(key: AyahKey(surah: Int32(surah), ayah: Int32(ayah))) }
    func close() { store.closeSheet() }
    func loadNames(_ language: AppLanguage) { store.loadNames(language: language) }

    func placeLabel(_ key: AyahKey, language: AppLanguage) -> String {
        "\(store.surahTitle(number: key.surah, language: language, names: names)) \(key.ayah)"
    }

    func surahTitle(_ number: Int32, language: AppLanguage) -> String { store.surahTitle(number: number, language: language, names: names) }
}

/// The one host of the sheet "Similar verses", at the root of the app (`MainTabs`): the ayah sheet, the mark of an ayah and the line
/// of the lesson only ask `SimilarService` to open it. A tap on a similar ayah opens the reader on it; "Tell apart" opens the drill.
struct SimilarSheetHost: ViewModifier {
    @Environment(Router.self) private var router
    @Environment(Copy.self) private var copy

    func body(content: Content) -> some View {
        let service = SimilarService.shared
        content
            .onAppear { service.loadNames(copy.language) }
            .onChange(of: copy.language) { _, language in service.loadNames(language) }
            .sheet(isPresented: Binding(get: { service.sheetKey != nil && service.sheet != nil }, set: { if !$0 { service.close() } })) {
                if let ui = service.sheet, let key = service.sheetKey {
                    SimilarSheetView(ui: ui, key: key,
                                     onOpen: { target in
                        service.close()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            router.push(.reader(Int(target.surah), ayah: Int(target.ayah), origin: "similar"))
                        }
                    }, onTellApart: {
                        service.close()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            router.push(.tellApart(surah: Int(key.surah), ayah: Int(key.ayah)))
                        }
                    })
                }
            }
    }
}

extension View {
    /// Shows the sheet "Similar verses" over this view when `SimilarService` asks for it. Apply once, at the root of the tabs.
    func similarSheetHost() -> some View { modifier(SimilarSheetHost()) }
}

struct SimilarSheetView: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let ui: SimilarSheetUi
    let key: AyahKey
    let onOpen: (AyahKey) -> Void
    let onTellApart: () -> Void

    var body: some View {
        let t = copy.similar
        let service = SimilarService.shared
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                HStack(spacing: QhSpacing.x3) {
                    SimilarRings(size: 24, color: qh.colors.primary)
                        .frame(width: 40, height: 40).background(qh.colors.primaryContainer, in: qh.shapes.smShape)
                    Text(t.title).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                }
                Text(t.legend).qhText({ $0.callout }, color: { $0.inkSecondary })
                PlaceCard(place: ui.current, title: service.placeLabel(ui.current.key, language: copy.language), tag: t.thisAyah, onTap: nil)
                if let repeats = ui.repeats {
                    QhSectionHeader(repeats.inOneSurah
                        ? t.repeatsInSurah(count: repeats.total, surahName: service.surahTitle(key.surah, language: copy.language))
                        : t.repeats(count: repeats.total))
                        .padding(.top, QhSpacing.x1)
                    ChipWrap(items: repeats.places.map { place in
                        (repeats.inOneSurah ? "\(place.ayah)" : service.placeLabel(place, language: copy.language), place)
                    }, onTap: onOpen)
                }
                if !ui.similar.isEmpty {
                    QhSectionHeader(t.places).padding(.top, QhSpacing.x1)
                    ForEach(Array(ui.similar.enumerated()), id: \.offset) { _, place in
                        PlaceCard(place: place, title: service.placeLabel(place.key, language: copy.language), tag: nil) { onOpen(place.key) }
                    }
                    if ui.canTellApart {
                        QhButton(t.tellApart, size: .m, fullWidth: true, action: onTellApart)
                    } else {
                        Text(t.tellApartNeedsTwo).qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5).padding(.bottom, QhSpacing.x6)
        }
        .background(qh.colors.surface)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
    }
}

private struct PlaceCard: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    let place: SimilarPlace
    let title: String
    let tag: String?
    let onTap: (() -> Void)?

    var body: some View {
        let t = copy.similar
        let display = QuranAyahDisplay(surah: Int(place.key.surah), ayah: Int(place.key.ayah), displayText: place.text,
                                       ranges: AyahWordsKt.wordRanges(text: place.text).map { Int($0.first)..<(Int($0.last) + 1) })
        let differing = Set(display.words.filter { word in
            place.differing.contains { Int($0.first) < word.offset + word.text.utf16.count && Int($0.last) >= word.offset }
        }.map(\.index))
        QhCard(onTap: onTap) {
            HStack(spacing: QhSpacing.x2) {
                Text(title).qhText({ $0.title2 })
                if let tag { Text("\u{00B7} \(tag)").qhText({ $0.callout }, color: { $0.inkSecondary }) }
                Spacer(minLength: 0)
                if place.learned {
                    Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(qh.colors.learned)
                    Text(t.learned).qhText({ $0.callout }, color: { $0.learned })
                }
            }
            QuranText(ayah: display, face: ReadingPrefs.shared.face, readingSize: CGFloat(min(ReadingPrefs.shared.size, 28)), accentWords: differing)
                .padding(.top, QhSpacing.x2)
        }
        .accessibilityElement(children: .combine)
        .accessibilityHint(onTap != nil ? t.openInReader : "")
    }
}

/// Chips that wrap onto lines.
struct ChipWrap<Item>: View {
    let items: [(String, Item)]
    let onTap: (Item) -> Void

    var body: some View {
        WrapLayout(spacing: QhSpacing.x2, lineSpacing: QhSpacing.x1) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                QhChip(label: item.0, selected: false) { onTap(item.1) }
            }
        }
    }
}

/// Lays children out left to right and wraps at the width offered.
struct WrapLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

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
        var x: CGFloat = 0, y: CGFloat = 0, lineHeight: CGFloat = 0, maxX: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width { x = 0; y += lineHeight + lineSpacing; lineHeight = 0 }
            origins.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
            maxX = max(maxX, x - spacing)
        }
        return (CGSize(width: min(maxX, width), height: y + lineHeight), origins)
    }
}
