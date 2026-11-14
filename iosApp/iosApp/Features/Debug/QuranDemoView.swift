#if DEBUG
import SwiftUI
import Observation
import UIKit

/// A host for the recall components with no Kotlin behind it: the session is the pure `HifzSession`, the calls change it.
@MainActor @Observable
final class DemoRecallHost: RecallActions {
    var session: HifzSession?
    var copy: RecallCopy
    var listening = false
    var hintMode: HifzHintMode
    private let ayahs: [QuranAyahDisplay]

    init(ayahs: [QuranAyahDisplay], hintMode: HifzHintMode = .firstWord, copy: RecallCopy = .en) {
        self.ayahs = ayahs
        self.hintMode = hintMode
        self.copy = copy
        self.session = .range(surah: ayahs.first?.surah ?? 1, ayahs: ayahs)
    }

    func setHintMode(_ mode: HifzHintMode) { hintMode = mode }
    func words(inSurah: Int) -> [String]? { ayahs.first { $0.ayah == inSurah }?.wordTexts }
    func reveal() { withQhAnimation(QhMotion.gentle) { session = session?.revealedSession() } }
    func mark(_ mark: AyahMark, position: Int) {
        guard session?.index == position else { return }
        withQhAnimation(QhMotion.gentle) { session = session?.marked(mark) }
    }
    func markEarly(_ mark: AyahMark, position: Int) {
        guard session?.index == position else { return }
        withQhAnimation(QhMotion.gentle) { session = session?.marked(mark, requireRevealed: false) }
    }
    func hideMore(position: Int) {
        guard let count = session?.current.flatMap({ words(inSurah: $0.inSurah)?.count }) else { return }
        session = session?.hidingMore(position: position, wordCount: count)
    }
    func peekWord(position: Int, word: Int) { session = session?.peekingWord(position: position, word: word) }
    func peekAyah(position: Int) { session = session?.peekingAyah(position: position) }
    func toggleListen() { listening.toggle() }

    func restart() { session = .range(surah: ayahs.first?.surah ?? 1, ayahs: ayahs) }
}

/// Launch options for screenshots: `QURAN_DEMO="tab=recall;hint=firstLetters;tajweed=1;passage=baqarah;face=madinah;dark=1;step=2;reveal=1;answered=2"`.
private struct DemoConfig {
    var values: [String: String] = [:]
    init() {
        let raw = ProcessInfo.processInfo.environment["QURAN_DEMO"] ?? ""
        for pair in raw.split(separator: ";") {
            let kv = pair.split(separator: "=", maxSplits: 1).map(String.init)
            if kv.count == 2 { values[kv[0]] = kv[1] }
        }
    }
    subscript(key: String) -> String? { values[key] }
}

/// Root of the demo: Text / Recall / Perf tabs. DEBUG only.
struct QuranDemoRoot: View {
    @Environment(\.qh) private var qh
    private let config = DemoConfig()
    @State private var tab: Int
    @State private var face: QhQuranFace
    @State private var size: Double
    @State private var tajweed: Bool

    init() {
        let config = DemoConfig()
        _tab = State(initialValue: ["text": 0, "recall": 1, "perf": 2][config["tab"] ?? "text"] ?? 0)
        _face = State(initialValue: QhQuranFace(rawValue: config["face"] ?? "") ?? .amiri)
        _size = State(initialValue: Double(config["size"] ?? "") ?? 28)
        _tajweed = State(initialValue: config["tajweed"] == "1")
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $tab) {
                Text("Text").tag(0)
                Text("Recall").tag(1)
                Text("Scroll").tag(2)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, QhSpacing.screen)
            .padding(.vertical, QhSpacing.x2)

            HStack(spacing: 12) {
                Menu {
                    ForEach(QhQuranFace.allCases) { f in Button(f.rawValue) { face = f } }
                } label: { Text(face.rawValue).qhText({ $0.label }, color: { $0.primary }) }
                Slider(value: $size, in: 22...40, step: 1).frame(maxWidth: 140)
                Toggle("Tajweed", isOn: $tajweed).labelsHidden()
                Text("Tajweed").qhText({ $0.label })
                Spacer(minLength: 0)
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.bottom, QhSpacing.x2)

            switch tab {
            case 0: QuranTextDemo(face: face, size: size, tajweed: tajweed, config: config)
            case 1: RecallDemo(face: face, size: size, tajweed: tajweed, config: config)
            default: ScrollDemo(face: face, size: size, tajweed: tajweed)
            }
        }
        .background(qh.colors.readerBg.ignoresSafeArea())
    }
}

// MARK: - Text tab

private struct QuranTextDemo: View {
    @Environment(\.qh) private var qh
    let face: QhQuranFace
    let size: Double
    let tajweed: Bool
    let config: DemoConfig
    @State private var current = 0
    @State private var tapped: String = "tap a word"
    @State private var picked = 0
    private let timer = Timer.publish(every: 0.6, on: .main, in: .common).autoconnect()

    var body: some View {
        let ayahs = QuranDemoData.fatiha + QuranDemoData.baqarah1to20
        ScrollView {
            LazyVStack(spacing: 0) {
                Text(tapped).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.bottom, 8)
                Text(QuranText_bismillah).qhArabic(qh.type.quranHeader(face)).padding(.bottom, 8)
                ForEach(ayahs) { ayah in
                    let isPicked = ayah.id == picked
                    HStack(alignment: .top, spacing: 12) {
                        AyahRosette(number: ayah.ayah, status: nil, weak: ayah.ayah == 3 && ayah.surah == 1)
                        QuranText(
                            ayah: ayah, face: face, readingSize: size, showTajweed: tajweed,
                            currentWord: ayah.id == 1001 ? current % max(ayah.wordCount, 1) : nil,
                            accentWords: ayah.id == 1002 ? [1, 2] : [],
                            endMarker: ayah.surah == 1,
                            onWordTap: { w in
                                tapped = "tapped \(ayah.surah):\(ayah.ayah) word \(w + 1): \(ayah.words[w].text)"
                                picked = ayah.id
                            }
                        )
                        .equatable()
                    }
                    .padding(.horizontal, QhSpacing.screen)
                    .padding(.vertical, QhSpacing.x3)
                    .background(isPicked ? qh.colors.readerHighlight : .clear)
                    Rectangle().fill(qh.colors.hairline).frame(height: 1).padding(.horizontal, QhSpacing.screen)
                }
            }
        }
        .onReceive(timer) { _ in withAnimation(.easeInOut(duration: 0.12)) { current += 1 } }
    }
}

private let QuranText_bismillah = QuranDisplay.bismillahText

// MARK: - Recall tab

private struct RecallDemo: View {
    @Environment(\.qh) private var qh
    let face: QhQuranFace
    let size: Double
    let tajweed: Bool
    @State private var host: DemoRecallHost
    @State private var gradeOpen = false
    @State private var override: RecallResult?
    private let ayahs: [QuranAyahDisplay]

    init(face: QhQuranFace, size: Double, tajweed: Bool, config: DemoConfig) {
        self.face = face; self.size = size; self.tajweed = tajweed
        let ayahs = config["passage"] == "baqarah" ? Array(QuranDemoData.baqarah1to20.prefix(6)) : QuranDemoData.fatiha
        self.ayahs = ayahs
        let mode = HifzHintMode(rawValue: config["hint"] ?? "") ?? .firstWord
        let host = DemoRecallHost(ayahs: ayahs, hintMode: mode, copy: config["lang"] == "ru" ? .ru : .en)
        // Screenshot setup: answer some ayahs, optionally reveal or hide in steps.
        for _ in 0..<(Int(config["answered"] ?? "") ?? 0) {
            host.reveal()
            host.mark(.remembered, position: host.session?.index ?? 0)
        }
        for _ in 0..<(Int(config["step"] ?? "") ?? 0) {
            host.hideMore(position: host.session?.index ?? 0)
        }
        if config["reveal"] == "1" { host.reveal() }
        if config["peek"] == "1" { host.peekAyah(position: host.session?.index ?? 0); host.peekAyah(position: host.session?.index ?? 0) }
        _host = State(initialValue: host)
    }

    var body: some View {
        let session = host.session
        let result: RecallResult? = (session?.finished == true) ? (override ?? session.map { RecallResult(grade: $0.suggestedGrade, hard: $0.hardAyahs) }) : nil
        RecallPassage(host: host, ayahs: ayahs, face: face, readingSize: size, showTajweed: tajweed, compact: true) {
            Color.clear.frame(height: 24)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            RecallBottomBar(host: host, result: result, returnDays: 1, onChange: { gradeOpen = true }, nextLabel: "Next passage",
                            onNext: { override = nil; host.restart() })
        }
        .recallGradeSheet(isPresented: $gradeOpen, session: session ?? HifzSession(surah: 1, items: []), title: "Al-Fatihah 1-7",
                          copy: host.copy, initial: result) { grade, hard in
            override = RecallResult(grade: grade, hard: hard)
        }
    }
}

// MARK: - Scroll tab (all 286 ayahs of Al-Baqarah, with a frame-time meter)

private struct ScrollDemo: View {
    @Environment(\.qh) private var qh
    let face: QhQuranFace
    let size: Double
    let tajweed: Bool
    @State private var meter = FrameMeter()
    @State private var report = "idle"
    private let ayahs = QuranDemoData.baqarah

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button("Auto-scroll") { meter.start { report = $0 } }
                    .buttonStyle(.borderedProminent)
                Text(report).qhText({ $0.label }).lineLimit(2)
            }
            .padding(.horizontal, QhSpacing.screen)
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(ayahs) { ayah in
                        VStack(spacing: 0) {
                            HStack(alignment: .top, spacing: 12) {
                                AyahRosette(number: ayah.ayah, status: ayah.ayah < 20 ? .learned : nil)
                                QuranText(ayah: ayah, face: face, readingSize: size, showTajweed: tajweed).equatable()
                            }
                            .padding(.horizontal, QhSpacing.screen)
                            .padding(.vertical, QhSpacing.x3)
                            Rectangle().fill(qh.colors.hairline).frame(height: 1).padding(.horizontal, QhSpacing.screen)
                        }
                    }
                }
                .background(ScrollFinder { meter.scrollView = $0 })
            }
            .task {
                guard DemoConfig()["auto"] == "1" else { return }
                try? await Task.sleep(for: .seconds(1.5))
                meter.start { report = $0 }
            }
        }
    }
}

/// Finds the `UIScrollView` that hosts a SwiftUI `ScrollView`.
private struct ScrollFinder: UIViewRepresentable {
    let found: (UIScrollView) -> Void
    func makeUIView(context: Context) -> UIView {
        let v = UIView()
        v.isUserInteractionEnabled = false
        return v
    }
    func updateUIView(_ view: UIView, context: Context) {
        DispatchQueue.main.async {
            var up: UIView? = view
            while let current = up, !(current is UIScrollView) { up = current.superview }
            if let scroll = up as? UIScrollView { found(scroll) }
        }
    }
}

/// Drives the scroll view at a fixed speed with a display link and counts the frames that took too long.
@MainActor
final class FrameMeter: NSObject {
    var scrollView: UIScrollView?
    private var link: CADisplayLink?
    private var last: CFTimeInterval = 0
    private var frames = 0
    private var slow = 0
    private var worst: Double = 0
    private var total: Double = 0
    private var done: ((String) -> Void)?
    private let speed: CGFloat = 2400  // points per second
    private var started: CFTimeInterval = 0

    func start(_ done: @escaping (String) -> Void) {
        guard let scrollView, link == nil else { return }
        self.done = done
        scrollView.contentOffset = .zero
        frames = 0; slow = 0; worst = 0; total = 0; last = 0
        let link = CADisplayLink(target: self, selector: #selector(tick(_:)))
        link.add(to: .main, forMode: .common)
        self.link = link
        started = CACurrentMediaTime()
    }

    @objc private func tick(_ l: CADisplayLink) {
        guard let scrollView else { return }
        if last != 0 {
            let dt = l.timestamp - last
            frames += 1
            total += dt
            worst = max(worst, dt)
            if dt > (l.targetTimestamp - l.timestamp) * 1.5 { slow += 1 }
            let maxY = scrollView.contentSize.height - scrollView.bounds.height
            let next = scrollView.contentOffset.y + speed * CGFloat(dt)
            if next >= maxY || CACurrentMediaTime() - started > 20 {
                link?.invalidate(); link = nil
                let avg = total / Double(max(frames, 1)) * 1000
                done?(String(format: "%d frames, avg %.1f ms, worst %.0f ms, slow(>1.5x) %d", frames, avg, worst * 1000, slow))
                return
            }
            scrollView.contentOffset.y = next
        }
        last = l.timestamp
    }
}

#Preview("Quran demo light") { QuranDemoRoot().qhPreviewTheme() }
#Preview("Quran demo dark") { QuranDemoRoot().qhPreviewTheme(dark: true) }
#endif
