import ComposeApp
import Observation
import SwiftUI

// MARK: - Word clock (word by word recitation)

struct WordSpanValue: Equatable { let index: Int; let start: Int; let end: Int }

/// Follows the recitation of one ayah word by word: the timings come from the shared layer (`IosReaderBridge.wordSpans`, empty when
/// the setting is off or no timings fit the text); while audio sounds the player position is asked about 16 times a second and
/// `word` is the one of the last span that has started (`wordAt`). Only the ayah that sounds owns a clock.
@MainActor
@Observable
final class WordClock {
    private(set) var word: Int?
    private(set) var spans: [WordSpanValue] = []
    @ObservationIgnored private var loop: Task<Void, Never>?
    @ObservationIgnored private var loadedKey = ""

    func load(surah: Int, ayah: Int, wordCount: Int) async {
        let key = "\(surah)/\(ayah)/\(wordCount)/\(IosUi.shared.uiPrefs.reciter.value.recitationId)"
        guard key != loadedKey else { return }
        loadedKey = key
        let found = (try? await IosReaderBridge.shared.wordSpans(surah: Int32(surah), ayah: Int32(ayah), wordCount: Int32(wordCount))) ?? []
        spans = found.map { WordSpanValue(index: Int($0.wordIndex), start: Int($0.startMs), end: Int($0.endMs)) }
    }

    func startOf(_ word: Int) -> Int? { spans.first { $0.index == word }?.start }

    /// Runs while `playing`; when it stops the highlight stays where the recitation paused.
    func run(playing: Bool) {
        loop?.cancel()
        guard playing, !spans.isEmpty else { update(); return }
        loop = Task { [weak self] in
            while !Task.isCancelled {
                self?.update()
                try? await Task.sleep(nanoseconds: 62_000_000)
            }
        }
    }

    func stop() { loop?.cancel(); loop = nil }

    private func update() {
        guard !spans.isEmpty else { if word != nil { word = nil }; return }
        let position = Int(IosUi.shared.playback.positionMs())
        var found: Int? = nil
        if let first = spans.first, position >= first.start {
            var low = 0, high = spans.count - 1
            while low < high {
                let mid = (low + high + 1) / 2
                if spans[mid].start <= position { low = mid } else { high = mid - 1 }
            }
            found = spans[low].index
        }
        if found != word { word = found }
    }

    deinit { loop?.cancel() }
}

/// The Arabic text of the ayah that sounds, was paused halfway or was picked: the word that is recited gets a wash and the words can
/// be tapped. Everything else about it is the plain `QuranText`.
struct FollowedQuranText: View {
    let ayah: QuranAyahDisplay
    let face: QhQuranFace
    let size: CGFloat
    let tajweed: Bool
    let playing: Bool
    let tappable: Bool
    var anchoredWord: Int?
    let onWordTap: (Int, Int?) -> Void
    @State private var clock = WordClock()

    var body: some View {
        QuranText(ayah: ayah, face: face, readingSize: size, showTajweed: tajweed, currentWord: clock.word, anchoredWord: anchoredWord,
                  onWordTap: tappable ? { word in onWordTap(word, clock.startOf(word)) } : nil)
            .animation(QhMotion.reduced ? nil : .easeInOut(duration: 0.12), value: clock.word)
            .task(id: ayah.id) { await clock.load(surah: ayah.surah, ayah: ayah.ayah, wordCount: ayah.wordCount); clock.run(playing: playing) }
            .onChange(of: playing) { _, now in clock.run(playing: now) }
            .onDisappear { clock.stop() }
    }
}

// MARK: - One ayah row

/// What one row draws. Equatable, so a list that re-renders for another reason does not touch rows that did not change.
struct AyahRowData: Equatable {
    var index: Int
    var number: Int
    var ayah: QuranAyahDisplay
    var translation: String?
    var status: QhStatus?
    var weak: Bool
    var hasNote: Bool
    var marked: Bool
    var range: RangePosition
    var markLine: [String?]?
    var markSpoken: String
    var showDivider: Bool
    var actionsOpen: Bool
    var translationMissing: Bool
    var follow: Bool
    var playing: Bool
    var meaningsOn: Bool
    var meaningWord: Int?
    var tajweed: Bool
    var face: QhQuranFace
    var size: Int
    var similar: Bool
}

struct AyahRowCallbacks {
    var select: (Int) -> Void
    var more: (Int) -> Void
    var listen: (Int) -> Void
    var repeatAyah: (Int) -> Void
    var retryTranslation: () -> Void
    var wordTap: (Int, Int, Int?) -> Void
}

struct AyahRowView: View, Equatable {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(\.qhReduceMotion) private var reduced
    let data: AyahRowData
    let sajdaValue: Sajda?
    let callbacks: AyahRowCallbacks

    static func == (a: AyahRowView, b: AyahRowView) -> Bool { a.data == b.data }

    var body: some View {
        let c = qh.colors
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                if let line = data.markLine {
                    MushafMarkLineView(start: line[0], end: line[1], spoken: data.markSpoken).padding(.bottom, 2)
                }
                HStack(spacing: 0) {
                    AyahRosette(number: data.number, status: data.status, weak: data.weak, size: 34)
                    if data.hasNote {
                        Image(systemName: "note.text").font(.system(size: 15, weight: .semibold)).foregroundStyle(c.gold)
                            .padding(.leading, QhSpacing.x2).accessibilityLabel(copy.reader.hasNote)
                    }
                    if data.similar {
                        SimilarMarkButton(surah: data.ayah.surah, ayah: data.number)
                    }
                    if let sajdaValue { SajdaSign(sajda: sajdaValue).padding(.leading, QhSpacing.x2) }
                    Spacer(minLength: 0)
                }
                .frame(minHeight: QhSpacing.minTouch)
                arabic
                if let t = data.translation, !t.isEmpty {
                    Text(t).qhText({ $0.body }, color: { $0.inkSecondary }).frame(maxWidth: .infinity, alignment: .leading).padding(.top, QhSpacing.x3)
                } else if data.translationMissing {
                    HStack(spacing: 0) {
                        Text("\(copy.reader.translationNotLoaded) \u{00B7} ").qhText({ $0.callout }, color: { $0.inkSecondary })
                        Button(copy.reader.retry, action: callbacks.retryTranslation).qhText({ $0.callout }, color: { $0.primary })
                            .frame(minHeight: QhSpacing.minTouch)
                    }
                }
                if data.actionsOpen {
                    AyahActionRow(onListen: { callbacks.listen(data.index) }, onRepeat: { callbacks.repeatAyah(data.index) },
                                  onMore: { callbacks.more(data.index) })
                        .transition(.opacity.combined(with: .scale(scale: 0.96, anchor: .top)))
                }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x1).padding(.bottom, QhSpacing.x4)
            if data.showDivider {
                Rectangle().fill(c.hairline).frame(height: 1).padding(.horizontal, QhSpacing.screen)
            }
        }
        .background { AyahDecoration(marked: data.marked, range: data.range) }
        .contentShape(Rectangle())
        .onTapGesture { callbacks.select(data.index) }
        .onLongPressGesture(minimumDuration: 0.45) {
            QhHaptics.shared.perform(.longPress)
            callbacks.more(data.index)
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(data.marked ? .isSelected : [])
        .accessibilityAction(named: Text(copy.reader.ayahActions)) { callbacks.more(data.index) }
        .animation(reduced ? nil : QhMotion.gentle, value: data.actionsOpen)
        .overlayPreferenceValue(QuranWordAnchorKey.self) { anchor in
            if let word = data.meaningWord, let anchor {
                WordMeaningHost(surah: data.ayah.surah, ayah: data.number, word: word, wordCount: data.ayah.wordCount, anchor: anchor)
            }
        }
        .zIndex(data.meaningWord != nil ? 2 : 0)
    }

    @ViewBuilder private var arabic: some View {
        let tappable = data.follow || data.meaningsOn
        if data.follow || data.meaningsOn {
            FollowedQuranText(ayah: data.ayah, face: data.face, size: CGFloat(data.size), tajweed: data.tajweed, playing: data.playing,
                              tappable: tappable, anchoredWord: data.meaningWord) { word, startMs in
                callbacks.wordTap(data.index, word, startMs)
            }
        } else {
            QuranText(ayah: data.ayah, face: data.face, readingSize: CGFloat(data.size), showTajweed: data.tajweed)
                .equatable()
        }
    }
}

/// The two rings by the number of an ayah that has similar verses; a tap opens the sheet "Similar verses".
struct SimilarMarkButton: View {
    @Environment(Copy.self) private var copy
    let surah: Int
    let ayah: Int

    var body: some View {
        let store = SimilarService.shared
        if store.hasSimilar(surah: surah, ayah: ayah) {
            SimilarRings(size: 22)
                .frame(width: QhSpacing.minTouch - 8, height: QhSpacing.minTouch)
                .contentShape(Rectangle())
                .onTapGesture { QhHaptics.shared.perform(.tick); store.open(surah: surah, ayah: ayah) }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(copy.similar.markDescription)
                .accessibilityAddTraits(.isButton)
        }
    }
}
