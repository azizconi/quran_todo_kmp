import ComposeApp
import Foundation
import Observation

/// Whether the tajweed markup is on the device (the "Aa" sheet shows it, the text waits for `.ready`). Port of `TajweedAvailability`.
enum TajweedAvailability: Equatable {
    case missing
    case downloading(Double)
    case needsInternet
    case ready
}

/// The tajweed markup on the device and the colours of an ayah (port of Android `TajweedStore`, task F3b).
///
/// Turning tajweed on fetches the whole Quran in ONE request (`quran-tajweed` of api.alquran.cloud, about 2.5 MB), the Kotlin
/// `TajweedFormat` checks it and every surah becomes its own small file under Application Support (`s002.txt`), the marker `ready`
/// last. A surah is read, parsed (`TajweedMarkup`) and kept in memory (the last 10); the rules are moved onto the text that is drawn
/// (`TajweedAlignment`) one ayah at a time, on first use, and the result is kept. Only the colour changes.
@MainActor
@Observable
final class TajweedService {
    static let shared = TajweedService()

    private(set) var availability: TajweedAvailability
    /// Grows whenever something a row may show changed (a surah was parsed, the file arrived or went).
    private(set) var version = 0

    @ObservationIgnored private let directory: URL
    @ObservationIgnored private var memory: [Int: SurahMarkup] = [:]
    @ObservationIgnored private var order: [Int] = []
    @ObservationIgnored private var loading = Set<Int>()
    @ObservationIgnored private var lastFailure = Date.distantPast
    @ObservationIgnored private var downloadTask: Task<Void, Never>?
    @ObservationIgnored private var built: [Int: QuranAyahDisplay] = [:]

    private nonisolated static let marker = "ready"
    private nonisolated static let markerContent = "tajweed-v1 6236"
    private static let retryAfter: TimeInterval = 30
    private static let maxSurahs = 10

    private final class SurahMarkup {
        let ayahs: [TajweedAyah]
        var aligned: [Int: (text: String, spans: [TajweedRun])] = [:]
        init(_ ayahs: [TajweedAyah]) { self.ayahs = ayahs }
    }

    private init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        directory = base.appendingPathComponent("tajweed", isDirectory: true)
        availability = Self.isComplete(directory) ? .ready : .missing
    }

    private static func isComplete(_ dir: URL) -> Bool {
        (try? String(contentsOf: dir.appendingPathComponent(marker), encoding: .utf8))?.trimmingCharacters(in: .whitespacesAndNewlines) == markerContent
    }

    private func surahFile(_ surah: Int) -> URL { directory.appendingPathComponent(String(format: "s%03d.txt", surah)) }

    // MARK: Asking

    /// What a screen does when it shows `surah` with tajweed on: asks for the file when it is missing, reads the surah when it is there.
    func prepare(surah: Int) {
        switch availability {
        case .ready: if memory[surah] == nil { load(surah: surah) }
        case .missing, .needsInternet: ensureDownloaded()
        case .downloading: break
        }
    }

    /// Starts the download when the markup is not on the device, nothing runs, and (unless `force`) the last try is not recent.
    func ensureDownloaded(force: Bool = false) {
        switch availability {
        case .ready, .downloading: return
        default: break
        }
        if !force, Date().timeIntervalSince(lastFailure) < Self.retryAfter { return }
        availability = .downloading(0)
        downloadTask = Task { [weak self] in await self?.download() }
    }

    func retry() { ensureDownloaded(force: true) }

    // MARK: Reading

    private func load(surah: Int) {
        guard availability == .ready, (1...114).contains(surah), !loading.contains(surah) else { return }
        loading.insert(surah)
        let file = surahFile(surah)
        let expected = Int32(Int(QuranFacts.shared.ayahCount(surah: Int32(surah))))
        Task.detached(priority: .userInitiated) {
            let text = try? String(contentsOf: file, encoding: .utf8)
            let parsed = text.flatMap { TajweedFormat.shared.decodeSurah(text: $0, expected: expected) }?.map { TajweedMarkup.shared.parse(markup: $0) }
            await MainActor.run { [weak self] in self?.finishLoad(surah: surah, parsed: parsed) }
        }
    }

    private func finishLoad(surah: Int, parsed: [TajweedAyah]?) {
        loading.remove(surah)
        guard let parsed else {
            // A file that cannot be read: drop all of it, the next ask fetches it again.
            try? FileManager.default.removeItem(at: directory.appendingPathComponent(Self.marker))
            memory.removeAll(); order.removeAll(); built.removeAll()
            availability = .missing
            version += 1
            return
        }
        memory[surah] = SurahMarkup(parsed)
        order.removeAll { $0 == surah }
        order.append(surah)
        while order.count > Self.maxSurahs { memory[order.removeFirst()] = nil }
        version += 1
    }

    /// The ayah with its tajweed runs when the surah is in memory (never waits: asks for the surah and returns nil), else nil.
    func tajweedDisplay(for plain: QuranAyahDisplay) -> QuranAyahDisplay? {
        guard availability == .ready else { return nil }
        guard let surah = memory[plain.surah] else { load(surah: plain.surah); return nil }
        let key = plain.id
        if let hit = built[key], hit.text == plain.text { return hit }
        guard surah.ayahs.indices.contains(plain.ayah - 1) else { return nil }
        let spans: [TajweedRun]
        if let cached = surah.aligned[plain.ayah], cached.text == plain.text {
            spans = cached.spans
        } else {
            let moved = TajweedAlignment.shared.align(theirs: surah.ayahs[plain.ayah - 1], ours: plain.text)
            spans = moved.compactMap { span in
                guard let rule = TajweedRule(rawValue: Character(UnicodeScalar(UInt8(truncatingIfNeeded: span.rule.code)))) else { return nil }
                return TajweedRun(rule: rule, start: Int(span.range.first), end: Int(span.range.last) + 1)
            }
            surah.aligned[plain.ayah] = (plain.text, spans)
        }
        let ranges = AyahWordsKt.wordRanges(text: plain.text).map { Int($0.first)..<(Int($0.last) + 1) }
        let display = QuranAyahDisplay(surah: plain.surah, ayah: plain.ayah, displayText: plain.text, ranges: ranges, tajweed: spans)
        built[key] = display
        if built.count > 2000 { built.removeAll() }
        return display
    }

    // MARK: Download

    private func download() async {
        let fm = FileManager.default
        try? fm.createDirectory(at: directory, withIntermediateDirectories: true)
        try? fm.removeItem(at: directory.appendingPathComponent(Self.marker))
        var request = URLRequest(url: URL(string: TajweedFormat.shared.URL)!)
        request.timeoutInterval = 30
        let expected = Double(TajweedFormat.shared.EXPECTED_BYTES)
        var ok = false
        do {
            // One plain data task: iterating `URLSession.bytes` one byte at a time took tens of seconds for 2.5 MB.
            let (data, response) = try await Self.fetch(request) { [weak self] (received: Int64) in
                Task { @MainActor in
                    guard let self, case .downloading = self.availability else { return }
                    self.availability = .downloading(min(Double(received) / expected, 0.97))
                }
            }
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
            let dir = directory
            // Splitting and writing 114 files is done off the main actor, so the reader keeps scrolling.
            ok = await Task.detached(priority: .userInitiated) { Self.store(String(decoding: data, as: UTF8.self), in: dir) }.value
        } catch {
            ok = false
        }
        if ok { memory.removeAll(); order.removeAll(); built.removeAll(); availability = .ready } else { lastFailure = Date(); availability = .needsInternet }
        version += 1
    }

    /// A data task whose received byte count is reported while it runs (the server may not send a length, so bytes, not a fraction).
    private nonisolated static func fetch(_ request: URLRequest, received: @escaping @Sendable (Int64) -> Void) async throws -> (Foundation.Data, URLResponse) {
        final class Box: @unchecked Sendable { var observation: NSKeyValueObservation? }
        let box = Box()
        defer { box.observation?.invalidate() }
        return try await withCheckedThrowingContinuation { continuation in
            let task = URLSession.shared.dataTask(with: request) { data, response, error in
                if let data, let response { continuation.resume(returning: (data, response)) }
                else { continuation.resume(throwing: error ?? URLError(.unknown)) }
            }
            box.observation = task.observe(\.countOfBytesReceived) { task, _ in received(task.countOfBytesReceived) }
            task.resume()
        }
    }

    /// Writes every surah (a temporary file, then the final name) and the marker; false when the answer is not a whole Quran.
    private nonisolated static func store(_ json: String, in directory: URL) -> Bool {
        guard let surahs = TajweedFormat.shared.splitQuran(json: json) else { return false }
        let fm = FileManager.default
        for (index, markups) in surahs.enumerated() {
            let target = directory.appendingPathComponent(String(format: "s%03d.txt", index + 1))
            let temporary = target.appendingPathExtension("tmp")
            do {
                try TajweedFormat.shared.encodeSurah(markups: markups).write(to: temporary, atomically: false, encoding: .utf8)
                try? fm.removeItem(at: target)
                try fm.moveItem(at: temporary, to: target)
            } catch { return false }
        }
        do { try markerContent.write(to: directory.appendingPathComponent(marker), atomically: true, encoding: .utf8) } catch { return false }
        var dir = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? dir.setResourceValues(values)
        return true
    }
}
