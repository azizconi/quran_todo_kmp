import Combine
import SwiftUI
import ComposeApp

struct ReaderPreferences: Equatable {
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

extension NativeSurahReaderTheme {
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
    @Published private(set) var practice: ReaderPracticeSettings?
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
                self.practice = snapshot.practice
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

    func applyPlayerSettings(preferences: ReaderPreferences, practice: ReaderPracticeDraft, snapshot: NativeSurahReaderSnapshot) {
        self.practice = bridge.savePlayerSettings(
            surahNumber: snapshot.number, ayahCount: Int32(snapshot.ayahs.count),
            rangeEnabled: practice.rangeEnabled, startAyah: Int32(practice.startAyah),
            endAyah: Int32(practice.endAyah), rangeRepeatCount: Int32(practice.repeatCount),
            pauseSeconds: Int32(practice.pauseSeconds), playbackSpeed: preferences.playbackSpeed,
            ayahRepeatCount: Int32(preferences.repeatCount)
        )
        applyPreferences(preferences)
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
    @Published private(set) var pauseRemainingSeconds: Int32 = 0
    @Published private(set) var hasError = false
    private let bridge: NativeSurahReaderBridge
    private var ayahs: [NativeSurahReaderAyah] = []
    private var surahNumber: Int32 = 0

    init(bridge: NativeSurahReaderBridge) {
        self.bridge = bridge
        bridge.observePlayback { [weak self] state in
            guard let self else { return }
            self.selectedAyahNumber = self.ayahs.indices.contains(Int(state.index))
                ? self.ayahs[Int(state.index)].globalNumber : nil
            self.isPlaying = state.isPlaying
            self.isPaused = state.canResume
            self.positionMs = state.positionMs
            self.durationMs = state.durationMs
            self.pauseRemainingSeconds = state.pauseRemainingSeconds
            self.hasError = state.hasError
        }
    }

    func configure(snapshot: NativeSurahReaderSnapshot) {
        ayahs = snapshot.ayahs
        surahNumber = snapshot.number
        if selectedAyahNumber == nil { selectedAyahNumber = snapshot.initialAyahGlobalNumber }
    }

    func updatePreferences(_ value: ReaderPreferences) {
        bridge.updatePlayerPreferences(surahNumber: surahNumber, ayahCount: Int32(ayahs.count))
    }

    func select(_ ayah: NativeSurahReaderAyah) {
        guard let index = ayahs.firstIndex(where: { $0.globalNumber == ayah.globalNumber }) else { return }
        bridge.selectAyah(index: Int32(index))
    }
    func saveReadingPosition(_ globalNumber: Int32) {
        bridge.saveCurrentAyah(surahNumber: surahNumber, ayahGlobalNumber: globalNumber)
    }
    func playOrPause() { bridge.togglePlayback() }
    func previous() { bridge.previousAyah() }
    func next() { bridge.nextAyah() }
    func stop() { bridge.stopAudio() }
}

private enum ReaderSheet: Identifiable {
    case reading(NativeSurahReaderSnapshot)
    case player(NativeSurahReaderSnapshot, ReaderPlayerFeature)
    var id: String {
        switch self { case .reading: return "reading"; case let .player(_, feature): return feature.rawValue }
    }
}

struct NativeSurahReaderScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var store: NativeSurahReaderStore
    @StateObject private var player: NativeSurahReaderPlayer
    @State private var presentedSheet: ReaderSheet?
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
                    if let snapshot = store.snapshot { presentedSheet = .reading(snapshot) }
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
        .sheet(item: $presentedSheet) { destination in
            switch destination {
            case let .reading(snapshot):
                ReaderSettingsSheet(
                    preferences: store.preferences ?? ReaderPreferences(snapshot.preferences),
                    copy: snapshot.strings, theme: snapshot.theme
                ) { next in
                    let previous = store.preferences
                    store.save(next)
                    player.updatePreferences(next)
                    if next.showTranslation != previous?.showTranslation {
                        store.load(isSystemDark: colorScheme == .dark) { player.configure(snapshot: $0) }
                    } else {
                        store.applyPreferences(next)
                    }
                }
            case let .player(snapshot, feature):
                ReaderPlayerSheet(
                    feature: feature, currentAyah: snapshot.ayahs.first(where: { $0.globalNumber == player.selectedAyahNumber }).map { Int($0.numberInSurah) } ?? 1,
                    preferences: store.preferences ?? ReaderPreferences(snapshot.preferences),
                    practice: store.practice ?? snapshot.practice,
                    ayahCount: snapshot.ayahs.count,
                    copy: snapshot.playerCopy, theme: snapshot.theme,
                    guideSeen: store.bridge.hasSeenPlayerGuide(feature: feature.storageKey),
                    onGuideSeen: { store.bridge.markPlayerGuideSeen(feature: feature.storageKey) }
                ) { preferences, practice, startPlayback in
                    store.applyPlayerSettings(preferences: preferences, practice: practice, snapshot: snapshot)
                    if startPlayback {
                        store.bridge.selectAyah(index: Int32(practice.startAyah - 1))
                        store.bridge.togglePlayback()
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
                onPlayPause: player.playOrPause,
                onRepeat: { presentedSheet = .player(snapshot, .range) },
                onPause: { presentedSheet = .player(snapshot, .pause) },
                onSpeed: { speed in
                    var next = preferences
                    next.playbackSpeed = speed
                    store.applyPlayerSettings(preferences: next, practice: ReaderPracticeDraft(store.practice ?? snapshot.practice), snapshot: snapshot)
                },
                playerCopy: snapshot.playerCopy,
                practice: store.practice ?? snapshot.practice,
                pauseRemainingSeconds: player.pauseRemainingSeconds,
                hasError: player.hasError
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
    let onRepeat: () -> Void
    let onPause: () -> Void
    let onSpeed: (Float) -> Void
    let playerCopy: PlayerFeatureCopy
    let practice: ReaderPracticeSettings
    let pauseRemainingSeconds: Int32
    let hasError: Bool

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

            }
            if hasError {
                Text(playerCopy.audioError).font(.caption).foregroundStyle(.red)
            } else if pauseRemainingSeconds > 0 {
                Text("\(playerCopy.waiting) · \(pauseRemainingSeconds) \(playerCopy.seconds)")
                    .font(.caption.weight(.semibold)).foregroundStyle(theme.primary)

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
            HStack(spacing: 8) {
                quickButton(
                    practice.rangeEnabled ? "\(practice.startAyah)–\(practice.endAyah) · \(practice.rangeRepeatCount == -1 ? "∞" : String(practice.rangeRepeatCount) + "×")" : playerCopy.repeatAction,
                    icon: "repeat", active: practice.rangeEnabled, action: onRepeat
                )
                quickButton(practice.pauseSeconds > 0 ? "\(practice.pauseSeconds) \(playerCopy.seconds)" : playerCopy.pauseAction,
                            icon: "timer", active: practice.pauseSeconds > 0, action: onPause)
                Menu {
                    ForEach([Float(0.75), 1, 1.25, 1.5, 2], id: \.self) { value in
                        Button { onSpeed(value) } label: {
                            if value == speed { Label(String(format: "%g×", value), systemImage: "checkmark") }
                            else { Text(String(format: "%g×", value)) }
                        }
                    }
                } label: {
                    quickLabel(String(format: "%g×", speed), icon: nil, active: speed != 1)
                }
                .accessibilityLabel(playerCopy.speed)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .background(theme.surface)
    }

    private func quickButton(_ title: String, icon: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { quickLabel(title, icon: icon, active: active) }
            .accessibilityLabel(icon == "repeat" ? playerCopy.repeatAction : playerCopy.pauseAction)
            .accessibilityValue(title)
    }

    private func quickLabel(_ title: String, icon: String?, active: Bool) -> some View {
        HStack(spacing: 5) {
            if let icon { Image(systemName: icon) }
            Text(title).lineLimit(1).minimumScaleFactor(0.8)
        }
        .font(.system(size: 13, weight: .medium))
        .foregroundStyle(active ? theme.primary : theme.textSecondary)
        .frame(maxWidth: .infinity, minHeight: 44)
        .background(active ? theme.primaryWeak : theme.surfaceAlt, in: RoundedRectangle(cornerRadius: 12))
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
