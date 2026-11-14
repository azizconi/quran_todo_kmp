import ComposeApp
import Observation
import SwiftUI

/// The Kotlin `ReaderViewModel` of one reader, with its flows as observable values (the pattern of BRIDGE.md). The model also keeps
/// the ayahs ready to draw (`QuranAyahDisplay`, built once with the Kotlin display helpers) so rows never recompute text.
@MainActor
@Observable
final class ReaderModel {
    @ObservationIgnored private let holder: VMHolder<ReaderViewModel>
    @ObservationIgnored private var eventTask: FlowEvents<ReaderEvent>?
    @ObservationIgnored private var surahTask: Task<Void, Never>?

    let route: DestReader
    let surahState: FlowState<SurahWithAyahs?>
    let loadFailed: FlowState<KotlinBoolean>
    let prefs: FlowState<SurahReaderPreferences>
    let practice: FlowState<ReaderPracticeSettings>
    let player: FlowState<PlayerUi>
    let status: FlowState<SurahTodoStatus?>
    let chapterNames: FlowState<[KotlinInt: ChapterNameModel]>
    let russianNames: FlowState<[KotlinInt: String]>
    let statuses: FlowState<[KotlinInt: AyahTodoStatus]>
    let notes: FlowState<[KotlinInt: AyahNoteEntity]>
    let weak: FlowState<Set<KotlinInt>>
    let coverage: FlowState<SurahCoverage?>
    let translations: FlowState<[KotlinInt: String]>
    let translationMissing: FlowState<KotlinBoolean>
    let anythingPlaying: FlowState<KotlinBoolean>
    let sleepTimer: FlowState<SleepTimer>
    let listenQueue: FlowState<ListenQueue?>
    let reciter: FlowState<Reciter>
    /// Clock leaves: only the views that read these redraw with the audio clock.
    let audioProgress: FlowState<KotlinFloat>
    let audioTime: FlowState<AudioTime>
    let pauseRemaining: FlowState<KotlinInt>
    let pauseTotal: FlowState<KotlinInt>
    let sleepRemaining: FlowState<KotlinInt>

    /// Ayahs of the surah as the screens draw them; empty until the Quran is there.
    private(set) var displays: [QuranAyahDisplay] = []

    var vm: ReaderViewModel { holder.vm }
    var surah: SurahWithAyahs? { surahState.value }
    var ayahs: [AyahEntity] { surahState.value?.ayahs ?? [] }
    var number: Int { Int(route.surah) }

    init(route: Route) {
        let dest = route.dest as! DestReader
        self.route = dest
        let holder = VMHolder { IosUi.shared.reader(route: dest) }
        self.holder = holder
        let vm = holder.vm
        surahState = FlowState(vm.surah)
        loadFailed = FlowState(vm.loadFailed)
        prefs = FlowState(vm.prefs)
        practice = FlowState(vm.practice)
        player = FlowState(vm.player)
        status = FlowState(vm.surahStatus)
        chapterNames = FlowState(vm.chapterNames)
        russianNames = FlowState(vm.russianNames)
        statuses = FlowState(vm.ayahStatuses)
        notes = FlowState(vm.notes)
        weak = FlowState(vm.weakAyahs)
        coverage = FlowState(vm.coverage)
        translations = FlowState(vm.translations)
        translationMissing = FlowState(vm.translationMissing)
        anythingPlaying = FlowState(vm.anythingPlaying)
        sleepTimer = FlowState(vm.sleepTimer)
        listenQueue = FlowState(vm.listenQueue)
        reciter = FlowState(IosUi.shared.uiPrefs.reciter)
        audioProgress = FlowState(vm.audioProgress)
        audioTime = FlowState(vm.audioTime)
        pauseRemaining = FlowState(vm.pauseRemainingSeconds)
        pauseTotal = FlowState(vm.pauseTotalSeconds)
        sleepRemaining = FlowState(vm.sleepRemainingSeconds)
        buildDisplays()
        surahTask = Task { [weak self] in
            guard let flow = self?.vm.surah else { return }
            do { for try await _ in flow { self?.buildDisplays() } } catch {}
        }
    }

    private func buildDisplays() {
        guard displays.isEmpty, let surah = surahState.value else { return }
        let n = Int(surah.surah.number)
        displays = surah.ayahs.map { ayah in
            let shown = BismillahKt.splitBismillah(surahNumber: Int32(n), ayahNumberInSurah: ayah.numberInSurah, text: ayah.text)
            let ranges = AyahWordsKt.wordRanges(text: shown).map { Int($0.first)..<(Int($0.last) + 1) }
            return QuranAyahDisplay(surah: n, ayah: Int(ayah.numberInSurah), displayText: shown, ranges: ranges)
        }
    }

    func bindEvents(toaster: Toaster, copy: Copy, celebrate: @escaping () -> Void) {
        guard eventTask == nil else { return }
        eventTask = FlowEvents(vm.events) { [weak self] event in
            switch onEnum(of: event) {
            case .surahLearned:
                celebrate()
                QhHaptics.shared.perform(.success)
                toaster.show(copy.reader.learnedToast(surahName: self?.title(copy: copy) ?? ""), kind: .success)
            case .statusFailed:
                QhHaptics.shared.perform(.reject)
                toaster.show(copy.reader.statusError, kind: .error)
            }
        }
    }

    // MARK: Names

    func chapter(_ number: Int) -> ChapterNameModel? { chapterNames.value[KotlinInt(int: Int32(number))] }

    func displayName(_ number: Int, copy: Copy) -> String {
        vm.surahDisplayName(number: Int32(number), language: copy.language, names: chapterNames.value, russian: russianNames.value)
    }

    func title(copy: Copy) -> String { displayName(number, copy: copy) }

    func meaning() -> String { chapter(number)?.translated ?? surah?.surah.englishNameTranslation ?? "" }
    func arabicName() -> String { chapter(number)?.arabic ?? surah?.surah.name ?? "" }

    func nextSurah(copy: Copy) -> NextSurahDisplay? {
        guard number < 114 else { return nil }
        return vm.nextSurahDisplay(number: Int32(number + 1), language: copy.language, names: chapterNames.value, russian: russianNames.value)
    }

    // MARK: Per ayah

    func statusOf(_ ayah: AyahEntity) -> AyahTodoStatus? { statuses.value[KotlinInt(int: ayah.number)] }
    func isWeak(_ ayah: AyahEntity) -> Bool { weak.value.contains(KotlinInt(int: ayah.number)) }
    func hasNote(_ ayah: AyahEntity) -> Bool { notes.value[KotlinInt(int: ayah.number)] != nil }
    func noteText(_ ayah: AyahEntity) -> String { notes.value[KotlinInt(int: ayah.number)]?.note ?? "" }
    func translation(_ ayah: AyahEntity) -> String? { translations.value[KotlinInt(int: ayah.number)] }

    var learnedFraction: Double {
        let total = max(ayahs.count, 1)
        return Double(statuses.value.values.filter { $0 == .learned }.count) / Double(total)
    }

    /// The ayah with its tajweed colours when the setting is on and the markup is ready, else the plain one.
    func display(at index: Int, tajweed: Bool) -> QuranAyahDisplay? {
        guard displays.indices.contains(index) else { return nil }
        guard tajweed else { return displays[index] }
        return TajweedService.shared.tajweedDisplay(for: displays[index]) ?? displays[index]
    }

    /// Index of the ayah the list marks as current (`PlayerUi.highlightIndex`).
    func highlightIndex() -> Int? {
        player.value.highlightIndex(highlightPlaying: prefs.value.highlightPlayingAyah).map { Int(truncating: $0) }
    }

    func place(at index: Int) -> MushafPlace? { vm.places.indices.contains(index) ? vm.places[index] : nil }
    func marks(at index: Int) -> AyahMarks? { vm.marks.indices.contains(index) ? vm.marks[index] : nil }

    func shareText(for ayah: AyahEntity, copy: Copy) -> String {
        ReaderPositionKt.ayahShareText(
            arabic: BismillahKt.cutBismillah(surahNumber: Int32(number), ayahNumberInSurah: ayah.numberInSurah, text: ayah.text),
            translation: translation(ayah),
            quranLabel: copy.reader.quranRef,
            surahNumber: Int32(number),
            ayahNumberInSurah: ayah.numberInSurah,
            surahName: title(copy: copy)
        )
    }
}
