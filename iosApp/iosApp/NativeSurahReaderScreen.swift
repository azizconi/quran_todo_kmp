import Combine
import SwiftUI
import ComposeApp

private struct ReaderPreferences: Equatable {
    var showTranslation: Bool
    var autoplayOnOpen: Bool
    var autoAdvanceAyahs: Bool
    var playbackSpeed: Float
    var repeatCount: Int
    var highlightPlayingAyah: Bool

    init(_ source: NativeSurahReaderPreferencesSnapshot) {
        showTranslation = source.showTranslation
        autoplayOnOpen = source.autoplayOnOpen
        autoAdvanceAyahs = source.autoAdvanceAyahs
        playbackSpeed = source.playbackSpeed
        repeatCount = Int(source.repeatCount)
        highlightPlayingAyah = source.highlightPlayingAyah
    }
}

private extension NativeSurahReaderTheme {
    var colorScheme: ColorScheme {
        isDark ? .dark : .light
    }

    var background: Color { Color(argb: backgroundArgb) }
    var surface: Color { Color(argb: surfaceArgb) }
    var surfaceAlt: Color { Color(argb: surfaceAltArgb) }
    var border: Color { Color(argb: borderArgb) }
    var primary: Color { Color(argb: primaryArgb) }
    var primaryWeak: Color { Color(argb: primaryWeakArgb) }
    var onPrimary: Color { Color(argb: onPrimaryArgb) }
    var onSurface: Color { Color(argb: onSurfaceArgb) }
    var textSecondary: Color { Color(argb: textSecondaryArgb) }
    var success: Color { Color(argb: successArgb) }
    var info: Color { Color(argb: infoArgb) }
}

private enum ReaderLoadState {
    case loading
    case content(NativeSurahReaderSnapshot, ReaderPreferences)
    case error(String)
}

/// The ayah nearest the top of the reading viewport is the natural place to
/// resume from. A preference keeps this compatible with the iOS 15.3 target,
/// where the newer ScrollView position APIs are unavailable.
private struct ReaderAyahViewportPreferenceKey: PreferenceKey {
    static let defaultValue: [Int32: CGFloat] = [:]

    static func reduce(value: inout [Int32: CGFloat], nextValue: () -> [Int32: CGFloat]) {
        value.merge(nextValue(), uniquingKeysWith: { _, next in next })
    }
}

private func leadingVisibleAyah(in offsets: [Int32: CGFloat]) -> Int32? {
    if let leadingVisible = offsets
        .filter({ $0.value >= 0 })
        .min(by: { $0.value < $1.value }) {
        return leadingVisible.key
    }
    return offsets.max(by: { $0.value < $1.value })?.key
}

@MainActor
private final class NativeSurahReaderStore: ObservableObject {
    @Published private(set) var state: ReaderLoadState = .loading
    @Published private(set) var surahStatus = ""
    @Published private(set) var statusErrorMessage: String?
    @Published private(set) var presentationCopy: NativeSurahReaderCopy?
    @Published private(set) var presentationTheme: NativeSurahReaderTheme?

    let surahNumber: Int32
    let bridge: NativeSurahReaderBridge
    private var pendingStatusRequestID: UUID?

    init(surahNumber: Int32) {
        self.surahNumber = surahNumber
        bridge = NativeSurahReaderBridgeFactory.shared.create()
    }

    var snapshot: NativeSurahReaderSnapshot? {
        guard case let .content(snapshot, _) = state else { return nil }
        return snapshot
    }

    var preferences: ReaderPreferences? {
        guard case let .content(_, preferences) = state else { return nil }
        return preferences
    }

    var copy: NativeSurahReaderCopy? {
        presentationCopy ?? snapshot?.strings
    }

    var theme: NativeSurahReaderTheme? {
        presentationTheme ?? snapshot?.theme
    }

    func load(
        isSystemDark: Bool,
        onLoaded: @escaping (NativeSurahReaderSnapshot) -> Void = { _ in }
    ) {
        state = .loading
        bridge.load(
            surahNumber: surahNumber,
            isSystemDark: isSystemDark,
            onLoaded: { [weak self] snapshot in
                guard let self else { return }
                let preferences = ReaderPreferences(snapshot.preferences)
                self.presentationCopy = snapshot.strings
                self.presentationTheme = snapshot.theme
                if self.pendingStatusRequestID == nil {
                    self.surahStatus = snapshot.surahStatus
                }
                self.state = .content(snapshot, preferences)
                onLoaded(snapshot)
            },
            onError: { [weak self] message in
                self?.state = .error(message)
            }
        )
    }

    func setSurahStatus(_ status: String) {
        guard case .content(_, _) = state else { return }

        let previousStatus = surahStatus
        let requestID = UUID()
        pendingStatusRequestID = requestID
        surahStatus = status
        statusErrorMessage = nil

        bridge.setSurahStatus(
            surahNumber: surahNumber,
            status: status,
            onCompleted: { [weak self] in
                guard self?.pendingStatusRequestID == requestID else { return }
                self?.pendingStatusRequestID = nil
            },
            onError: { [weak self] message in
                guard self?.pendingStatusRequestID == requestID else { return }
                self?.pendingStatusRequestID = nil
                self?.surahStatus = previousStatus
                self?.statusErrorMessage = message
            }
        )
    }

    func clearStatusUpdateError() {
        statusErrorMessage = nil
    }

    func save(_ preferences: ReaderPreferences) {
        bridge.savePreferences(
            showTranslation: preferences.showTranslation,
            autoplayOnOpen: preferences.autoplayOnOpen,
            autoAdvanceAyahs: preferences.autoAdvanceAyahs,
            playbackSpeed: preferences.playbackSpeed,
            repeatCount: Int32(preferences.repeatCount),
            highlightPlayingAyah: preferences.highlightPlayingAyah
        )
    }

    func applyPreferences(_ preferences: ReaderPreferences) {
        guard case let .content(snapshot, _) = state else { return }
        state = .content(snapshot, preferences)
    }

    deinit {
        bridge.close()
    }
}

@MainActor
private final class NativeSurahReaderPlayer: ObservableObject {
    @Published private(set) var selectedAyahNumber: Int32?
    @Published private(set) var isPlaying = false
    @Published private(set) var isPaused = false
    @Published private(set) var positionMs: Int64 = 0
    @Published private(set) var durationMs: Int64 = 0

    private let bridge: NativeSurahReaderBridge
    private var ayahs: [NativeSurahReaderAyah] = []
    private var preferences: ReaderPreferences?
    private var surahNumber: Int32 = 0
    private var currentIndex = 0
    private var repeatsRemaining = 1
    private var playbackGeneration = 0
    private var isLoadingAudio = false
    private var timer: AnyCancellable?
    private var lastSavedReadingAyahNumber: Int32?

    init(bridge: NativeSurahReaderBridge) {
        self.bridge = bridge
        timer = Timer.publish(every: 0.25, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in self?.readProgress() }
    }

    func configure(snapshot: NativeSurahReaderSnapshot) {
        ayahs = snapshot.ayahs
        if surahNumber != snapshot.number {
            lastSavedReadingAyahNumber = nil
        }
        surahNumber = snapshot.number
        preferences = ReaderPreferences(snapshot.preferences)
        guard !ayahs.isEmpty else { return }
        if selectedAyahNumber == nil {
            currentIndex = ayahs.firstIndex {
                $0.globalNumber == snapshot.initialAyahGlobalNumber
            } ?? 0
            selectedAyahNumber = ayahs[currentIndex].globalNumber
            if preferences?.autoplayOnOpen == true {
                start(at: currentIndex)
            }
        }
    }

    func updatePreferences(_ value: ReaderPreferences) {
        preferences = value
        bridge.setAudioPlaybackSpeed(playbackSpeed: value.playbackSpeed)
    }

    func select(_ ayah: NativeSurahReaderAyah) {
        guard let index = ayahs.firstIndex(where: { $0.globalNumber == ayah.globalNumber }) else { return }
        playbackGeneration &+= 1
        isLoadingAudio = false
        bridge.stopAudio()
        currentIndex = index
        selectedAyahNumber = ayah.globalNumber
        saveReadingPosition(ayah.globalNumber)
        isPlaying = false
        isPaused = false
        positionMs = 0
        durationMs = 0
    }

    func playOrPause() {
        guard !ayahs.isEmpty else { return }
        if isPlaying {
            if isLoadingAudio {
                playbackGeneration &+= 1
                isLoadingAudio = false
                bridge.stopAudio()
                isPlaying = false
                isPaused = false
                return
            }
            bridge.pauseAudio()
            isPlaying = false
            isPaused = true
        } else if isPaused, let preferences {
            bridge.resumeAudio(playbackSpeed: preferences.playbackSpeed)
            isPlaying = true
            isPaused = false
        } else {
            start(at: currentIndex)
        }
    }

    func saveReadingPosition(_ ayahGlobalNumber: Int32) {
        guard surahNumber > 0,
              ayahs.contains(where: { $0.globalNumber == ayahGlobalNumber }),
              lastSavedReadingAyahNumber != ayahGlobalNumber else {
            return
        }
        lastSavedReadingAyahNumber = ayahGlobalNumber
        bridge.saveCurrentAyah(surahNumber: surahNumber, ayahGlobalNumber: ayahGlobalNumber)
    }

    func previous() {
        start(at: max(currentIndex - 1, 0))
    }

    func next() {
        start(at: min(currentIndex + 1, max(ayahs.count - 1, 0)))
    }

    func stop() {
        playbackGeneration &+= 1
        isLoadingAudio = false
        bridge.stopAudio()
        isPlaying = false
        isPaused = false
        positionMs = 0
        durationMs = 0
    }

    private func start(at index: Int, repeatCount: Int? = nil) {
        guard ayahs.indices.contains(index), let preferences else { return }
        playbackGeneration &+= 1
        let generation = playbackGeneration
        currentIndex = index
        let ayah = ayahs[index]
        selectedAyahNumber = ayah.globalNumber
        saveReadingPosition(ayah.globalNumber)
        repeatsRemaining = repeatCount ?? preferences.repeatCount
        isPlaying = true
        isPaused = false
        isLoadingAudio = true
        positionMs = 0
        durationMs = 0
        bridge.playAyah(
            surahNumber: surahNumber,
            ayahGlobalNumber: ayah.globalNumber,
            ayahNumberInSurah: ayah.numberInSurah,
            playbackSpeed: preferences.playbackSpeed,
            onStarted: { [weak self] in
                DispatchQueue.main.async {
                    guard let self, self.playbackGeneration == generation, self.isPlaying else { return }
                    self.isLoadingAudio = false
                }
            },
            onCompleted: { [weak self] in
                DispatchQueue.main.async {
                    guard let self, self.playbackGeneration == generation else { return }
                    self.isLoadingAudio = false
                    self.didFinishAyah()
                }
            }
        )
    }

    private func didFinishAyah() {
        guard isPlaying, let preferences else { return }
        if repeatsRemaining == -1 {
            start(at: currentIndex, repeatCount: -1)
        } else if repeatsRemaining > 1 {
            start(at: currentIndex, repeatCount: repeatsRemaining - 1)
        } else if preferences.autoAdvanceAyahs, currentIndex + 1 < ayahs.count {
            start(at: currentIndex + 1)
        } else {
            isPlaying = false
            isPaused = false
            positionMs = durationMs
        }
    }

    private func readProgress() {
        guard isPlaying || isPaused else { return }
        positionMs = bridge.audioPositionMs()
        durationMs = bridge.audioDurationMs()
    }

    deinit {
        timer?.cancel()
    }
}

struct NativeSurahReaderScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var store: NativeSurahReaderStore
    @StateObject private var player: NativeSurahReaderPlayer
    @State private var isShowingSettings = false
    @State private var isRestoringScrollPosition = true
    private let fallbackChrome: AppChrome

    init(surahNumber: Int32, fallbackChrome: AppChrome) {
        let store = NativeSurahReaderStore(surahNumber: surahNumber)
        _store = StateObject(wrappedValue: store)
        _player = StateObject(wrappedValue: NativeSurahReaderPlayer(bridge: store.bridge))
        self.fallbackChrome = fallbackChrome
    }

    var body: some View {
        Group {
            switch store.state {
            case let .content(snapshot, preferences):
                reader(
                    snapshot: snapshot,
                    preferences: preferences,
                    surahStatus: store.surahStatus,
                    copy: snapshot.strings,
                    theme: snapshot.theme
                )
            case .loading:
                Group {
                    if let copy = store.copy {
                        ProgressView(copy.loadingSurah)
                    } else {
                        ProgressView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            case let .error(error):
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.title)
                        .foregroundStyle(.secondary)
                    if let copy = store.copy {
                        Text(copy.loadErrorTitle).font(.headline)
                    }
                    Text(error).font(.subheadline).foregroundStyle(.secondary)
                }
                .multilineTextAlignment(.center)
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(store.theme?.background ?? fallbackChrome.background)
        .preferredColorScheme(store.theme?.colorScheme ?? fallbackChrome.colorScheme)
        .tint(store.theme?.primary ?? fallbackChrome.primary)
        .appNavigationChrome(
            background: store.theme?.background ?? fallbackChrome.background,
            tint: store.theme?.primary ?? fallbackChrome.primary,
            isDark: store.theme?.isDark ?? fallbackChrome.isDark
        )
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarHidden(false)
        .toolbar {
            ToolbarItem(placement: .principal) {
                ReaderNavigationTitle(snapshot: store.snapshot)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                ReaderSettingsToolbarButton(copy: store.copy) {
                    isShowingSettings = true
                }
            }
        }
        .task {
            store.load(isSystemDark: colorScheme == .dark) { snapshot in
                player.configure(snapshot: snapshot)
            }
        }
        .onDisappear { player.stop() }
        .alert(
            store.copy?.statusUpdateErrorTitle ?? "",
            isPresented: Binding(
                get: { store.statusErrorMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        store.clearStatusUpdateError()
                    }
                }
            )
        ) {
            Button(store.copy?.confirmationLabel ?? "", role: .cancel) {
                store.clearStatusUpdateError()
            }
        } message: {
            Text(store.statusErrorMessage ?? store.copy?.tryAgain ?? "")
        }
        .sheet(isPresented: $isShowingSettings) {
            if let preferences = store.preferences,
               let copy = store.copy,
               let theme = store.theme {
                ReaderSettingsSheet(preferences: preferences, copy: copy, theme: theme) { next in
                    store.save(next)
                    player.updatePreferences(next)
                    if next.showTranslation != preferences.showTranslation {
                        store.load(isSystemDark: colorScheme == .dark) { snapshot in
                            player.configure(snapshot: snapshot)
                        }
                    } else {
                        store.applyPreferences(next)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func reader(
        snapshot: NativeSurahReaderSnapshot,
        preferences: ReaderPreferences,
        surahStatus: String,
        copy: NativeSurahReaderCopy,
        theme: NativeSurahReaderTheme
    ) -> some View {
        let ayahs = snapshot.ayahs
        VStack(spacing: 0) {
            ReaderSurahStatusButton(
                status: surahStatus,
                copy: copy,
                theme: theme,
                onStatusSelected: { store.setSurahStatus($0) }
            )
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(theme.background)

            Divider().overlay(theme.border)

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ReaderHeader(snapshot: snapshot, theme: theme)
                            .padding(.horizontal, 20)
                            .padding(.top, 12)
                            .padding(.bottom, 16)

                        ForEach(ayahs, id: \.globalNumber) { ayah in
                            NativeAyahRow(
                                ayah: ayah,
                                showTranslation: preferences.showTranslation,
                                isCurrent: preferences.highlightPlayingAyah && player.selectedAyahNumber == ayah.globalNumber && (player.isPlaying || player.isPaused),
                                readingFontSize: CGFloat(snapshot.readingFontSize),
                                readingFontStyle: snapshot.readingFontStyle,
                                copy: copy,
                                theme: theme,
                                onSelect: { player.select(ayah) }
                            )
                            .background(
                                GeometryReader { geometry in
                                    Color.clear.preference(
                                        key: ReaderAyahViewportPreferenceKey.self,
                                        value: [
                                            ayah.globalNumber: geometry.frame(
                                                in: .named("reader-ayah-scroll")
                                            ).minY,
                                        ]
                                    )
                                }
                            )
                            if ayah.globalNumber != ayahs.last?.globalNumber {
                                Divider()
                                    .overlay(theme.border)
                                    .padding(.leading, 64)
                            }
                        }
                    }
                    .padding(.bottom, 12)
                }
                .coordinateSpace(name: "reader-ayah-scroll")
                .onPreferenceChange(ReaderAyahViewportPreferenceKey.self) { offsets in
                    guard !isRestoringScrollPosition,
                          let ayahGlobalNumber = leadingVisibleAyah(in: offsets) else {
                        return
                    }
                    player.saveReadingPosition(ayahGlobalNumber)
                }
                .onAppear {
                    isRestoringScrollPosition = true
                    DispatchQueue.main.async {
                        proxy.scrollTo(snapshot.initialAyahGlobalNumber, anchor: .top)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                            isRestoringScrollPosition = false
                        }
                    }
                }
                .onChange(of: snapshot.initialAyahGlobalNumber) { targetAyah in
                    isRestoringScrollPosition = true
                    DispatchQueue.main.async {
                        withAnimation(.easeOut(duration: 0.22)) {
                            proxy.scrollTo(targetAyah, anchor: .top)
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
                            isRestoringScrollPosition = false
                        }
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            NativeReaderMiniPlayer(
                currentAyah: player.selectedAyahNumber.flatMap { current in ayahs.first { $0.globalNumber == current } },
                totalAyahs: ayahs.count,
                isPlaying: player.isPlaying,
                speed: preferences.playbackSpeed,
                positionMs: player.positionMs,
                durationMs: player.durationMs,
                copy: copy,
                theme: theme,
                onPrevious: player.previous,
                onNext: player.next,
                onPlayPause: player.playOrPause
            )
            // Extend only the player's background through the Home Indicator.
            // The controls remain in the safe area managed by safeAreaInset.
            .background(theme.surface.ignoresSafeArea(.container, edges: .bottom))
        }
        .background(theme.background)
    }
}

private struct ReaderNavigationTitle: View {
    let snapshot: NativeSurahReaderSnapshot?

    var body: some View {
        if let snapshot {
            let metadata = "\(snapshot.ayahs.count) \(snapshot.strings.ayahsLabel) · \(snapshot.revelationType)"
            VStack(spacing: 1) {
                Text(snapshot.title)
                    .font(.headline)
                    .foregroundStyle(snapshot.theme.onSurface)
                Text(metadata)
                    .font(.caption2)
                    .foregroundStyle(snapshot.theme.textSecondary)
            }
        } else {
            EmptyView()
        }
    }
}

private struct ReaderSettingsToolbarButton: View {
    let copy: NativeSurahReaderCopy?
    let action: () -> Void

    var body: some View {
        if let copy {
            Button(copy.settings, systemImage: "slider.horizontal.3", action: action)
                .accessibilityLabel(copy.settingsAccessibilityLabel)
        } else {
            EmptyView()
        }
    }
}

private struct ReaderHeader: View {
    let snapshot: NativeSurahReaderSnapshot
    let theme: NativeSurahReaderTheme

    var body: some View {
        VStack(spacing: 6) {
            Text(snapshot.arabicName)
                .font(.system(size: 30, weight: .medium, design: .serif))
                .foregroundStyle(theme.onSurface)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            if !snapshot.subtitle.isEmpty {
                Text(snapshot.subtitle)
                    .font(.subheadline)
                    .foregroundStyle(theme.textSecondary)
            }
        }
    }
}

private struct ReaderSurahStatusButton: View {
    let status: String
    let copy: NativeSurahReaderCopy
    let theme: NativeSurahReaderTheme
    let onStatusSelected: (String) -> Void
    @State private var isShowingStatusPicker = false

    var body: some View {
        Button {
            isShowingStatusPicker = true
        } label: {
            HStack(spacing: 10) {
                Label(copy.statusSurah, systemImage: statusIcon)
                    .font(.body.weight(.semibold))
                Spacer(minLength: 12)
                Text(statusLabel)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(statusColor)
                Image(systemName: "chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(theme.textSecondary)
            }
            .foregroundStyle(theme.onSurface)
            .frame(maxWidth: .infinity, minHeight: 44)
            .padding(.horizontal, 14)
            .background(statusColor.opacity(0.14), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
        }
        .buttonStyle(.plain)
        .confirmationDialog(
            copy.statusSurah,
            isPresented: $isShowingStatusPicker,
            titleVisibility: .visible
        ) {
            Button(copy.notStarted) { onStatusSelected("") }
            Button(copy.learning) { onStatusSelected("learning") }
            Button(copy.learned) { onStatusSelected("learned") }
        }
        .accessibilityLabel("\(copy.statusSurah): \(statusLabel)")
        .accessibilityHint(copy.statusHint)
    }

    private var statusLabel: String {
        switch status {
        case "learned": return copy.learned
        case "learning": return copy.learning
        default: return copy.notStarted
        }
    }

    private var statusIcon: String {
        status == "learned" ? "checkmark.circle.fill" : "bookmark"
    }

    private var statusColor: Color {
        switch status {
        case "learned": return theme.success
        case "learning": return theme.info
        default: return theme.textSecondary
        }
    }
}

private struct NativeAyahRow: View {
    let ayah: NativeSurahReaderAyah
    let showTranslation: Bool
    let isCurrent: Bool
    let readingFontSize: CGFloat
    let readingFontStyle: String
    let copy: NativeSurahReaderCopy
    let theme: NativeSurahReaderTheme
    let onSelect: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                Circle().fill(theme.surfaceAlt)
                Text("\(ayah.numberInSurah)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.textSecondary)
            }
            .frame(width: 38, height: 38)

            Button(action: onSelect) {
                VStack(alignment: .trailing, spacing: 12) {
                    Text(ayah.arabicText)
                        .font(arabicReaderFont(style: readingFontStyle, size: readingFontSize))
                        .foregroundStyle(theme.onSurface)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                    if showTranslation, !ayah.translation.isEmpty {
                        Text(ayah.translation)
                            .font(.body)
                            .foregroundStyle(theme.textSecondary)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 15)
                .background {
                    if isCurrent {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(theme.primaryWeak)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityHint(copy.selectAyahHint)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

func arabicReaderFont(style: String, size: CGFloat) -> Font {
    switch style {
    case "madinah_mushaf":
        return .custom("KFGQPCHafsEx1UthmanicScript-Reg", size: size)
    case "amiri_quran":
        return .custom("AmiriQuran-Regular", size: size)
    case "scheherazade_new":
        return .custom("ScheherazadeNew-Regular", size: size)
    case "noto_naskh_arabic":
        return .custom("NotoNaskhArabic-Regular", size: size)
    case "lateef":
        return .custom("Lateef-Regular", size: size)
    case "noto_nastaliq_urdu":
        return .custom("NotoNastaliqUrdu-Regular", size: size)
    case "aref_ruqaa_ink":
        return .custom("ArefRuqaaInk-Regular", size: size)
    default:
        return .custom("KFGQPCHafsEx1UthmanicScript-Reg", size: size)
    }
}

private struct NativeReaderMiniPlayer: View {
    let currentAyah: NativeSurahReaderAyah?
    let totalAyahs: Int
    let isPlaying: Bool
    let speed: Float
    let positionMs: Int64
    let durationMs: Int64
    let copy: NativeSurahReaderCopy
    let theme: NativeSurahReaderTheme
    let onPrevious: () -> Void
    let onNext: () -> Void
    let onPlayPause: () -> Void

    var body: some View {
        VStack(spacing: 9) {
            HStack(spacing: 8) {
                Image(systemName: "waveform")
                    .foregroundStyle(theme.primary)
                Text("\(copy.reciter) · \(copy.ayahLabel) \(currentAyah?.numberInSurah ?? 1) \(copy.ofLabel) \(totalAyahs)")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(theme.onSurface)
                    .lineLimit(1)
                Spacer()
                Text("\(speed, specifier: "%g")×")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(theme.textSecondary)
            }
            HStack(spacing: 8) {
                Text(durationString(positionMs)).font(.caption2.monospacedDigit()).foregroundStyle(theme.textSecondary)
                ReaderPlaybackProgress(progress: progress, theme: theme)
                Text(durationString(durationMs)).font(.caption2.monospacedDigit()).foregroundStyle(theme.textSecondary)
            }
            HStack(spacing: 26) {
                Button(action: onPrevious) {
                    Image(systemName: "backward.end.fill")
                        .frame(width: 44, height: 44)
                }
                Button(action: onPlayPause) {
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.title3)
                        .frame(width: 46, height: 46)
                        .background(Circle().fill(theme.primary))
                        .foregroundStyle(theme.onPrimary)
                }
                Button(action: onNext) {
                    Image(systemName: "forward.end.fill")
                        .frame(width: 44, height: 44)
                }
            }
            .font(.title3)
            .foregroundStyle(theme.onSurface)
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .background(theme.surface)
    }

    private var progress: Double {
        guard durationMs > 0 else { return 0 }
        return min(max(Double(positionMs) / Double(durationMs), 0), 1)
    }
}

private struct ReaderPlaybackProgress: View {
    let progress: Double
    let theme: NativeSurahReaderTheme

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(theme.surfaceAlt)
                Capsule()
                    .fill(theme.primary)
                    .frame(width: geometry.size.width * progress)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 4)
    }
}

private struct ReaderSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var preferences: ReaderPreferences
    let copy: NativeSurahReaderCopy
    let theme: NativeSurahReaderTheme
    let onSave: (ReaderPreferences) -> Void

    init(
        preferences: ReaderPreferences,
        copy: NativeSurahReaderCopy,
        theme: NativeSurahReaderTheme,
        onSave: @escaping (ReaderPreferences) -> Void
    ) {
        _preferences = State(initialValue: preferences)
        self.copy = copy
        self.theme = theme
        self.onSave = onSave
    }

    var body: some View {
        NavigationView {
            Form {
                Section(copy.readingSection) {
                    Toggle(copy.showTranslation, isOn: $preferences.showTranslation)
                    Toggle(copy.highlightPlayingAyah, isOn: $preferences.highlightPlayingAyah)
                }
                Section(copy.audioSection) {
                    Toggle(copy.autoplayOnOpen, isOn: $preferences.autoplayOnOpen)
                    Toggle(copy.autoAdvanceAyahs, isOn: $preferences.autoAdvanceAyahs)
                    Picker(copy.speed, selection: $preferences.playbackSpeed) {
                        Text("0.75×").tag(Float(0.75))
                        Text("1×").tag(Float(1))
                        Text("1.25×").tag(Float(1.25))
                        Text("1.5×").tag(Float(1.5))
                        Text("2×").tag(Float(2))
                    }
                    Picker(copy.repeatAyah, selection: $preferences.repeatCount) {
                        Text(copy.repeatOnce).tag(1)
                        Text(copy.repeatThreeTimes).tag(3)
                        Text(copy.repeatFiveTimes).tag(5)
                        Text(copy.repeatForever).tag(-1)
                    }
                }
            }
            .tint(theme.primary)
            .preferredColorScheme(theme.colorScheme)
            .appNavigationChrome(
                background: Color(uiColor: .systemGroupedBackground),
                tint: theme.primary,
                isDark: theme.isDark
            )
            .navigationTitle(copy.readingAndAudioTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(copy.done) {
                        onSave(preferences)
                        dismiss()
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
    }
}

private func durationString(_ milliseconds: Int64) -> String {
    let seconds = max(milliseconds / 1_000, 0)
    return String(format: "%d:%02d", seconds / 60, seconds % 60)
}
