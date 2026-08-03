import SwiftUI
import ComposeApp

private struct ReaderSettings: Equatable {
    var showTranslation: Bool
    var autoplayOnOpen: Bool
    var autoAdvanceAyahs: Bool
    var playbackSpeed: Float
    var repeatCount: Int
    var highlightPlayingAyah: Bool

    init(_ source: NativeSettingsPreferencesSnapshot) {
        showTranslation = source.showTranslation
        autoplayOnOpen = source.autoplayOnOpen
        autoAdvanceAyahs = source.autoAdvanceAyahs
        playbackSpeed = source.playbackSpeed
        repeatCount = Int(source.repeatCount)
        highlightPlayingAyah = source.highlightPlayingAyah
    }
}

@MainActor
private final class NativeSettingsStore: ObservableObject {
    @Published var language = "RU"
    @Published var theme = "LIGHT"
    @Published var fontStyle = "madinah_mushaf"
    @Published var fontSize = 28
    @Published var dataStatus: String?
    @Published var copy: NativeSettingsCopy?
    @Published var reader = ReaderSettings(
        NativeSettingsPreferencesSnapshot(
            showTranslation: false,
            autoplayOnOpen: false,
            autoAdvanceAyahs: true,
            playbackSpeed: 1,
            repeatCount: 1,
            highlightPlayingAyah: true
        )
    )

    private let bridge = NativeSettingsBridgeFactory.shared.create()

    var isDark: Bool {
        theme == "DARK"
    }

    func load() {
        let snapshot = bridge.load()
        language = snapshot.language
        theme = snapshot.themeMode
        fontStyle = snapshot.fontStyle
        fontSize = Int(snapshot.readingFontSize)
        reader = ReaderSettings(snapshot.preferences)
        copy = snapshot.localizedCopy
    }

    func saveReader() {
        bridge.updatePreferences(
            showTranslation: reader.showTranslation,
            autoplayOnOpen: reader.autoplayOnOpen,
            autoAdvanceAyahs: reader.autoAdvanceAyahs,
            playbackSpeed: reader.playbackSpeed,
            repeatCount: Int32(reader.repeatCount),
            highlightPlayingAyah: reader.highlightPlayingAyah
        )
    }

    func setLanguage(_ value: String) {
        language = value
        bridge.updateLanguage(value: value)
        copy = bridge.load().localizedCopy
    }
    func setTheme(_ value: String) { theme = value; bridge.updateTheme(value: value) }
    func setFontStyle(_ value: String) { fontStyle = value; bridge.updateFontStyle(value: value) }
    func setFontSize(_ value: Int) { fontSize = value; bridge.updateReadingFontSize(value: Int32(value)) }
    func downloadOffline() {
        bridge.startOfflineDownload()
        dataStatus = copy?.downloadStarted
    }

    func sync() {
        bridge.sync()
        dataStatus = copy?.syncStarted
    }

    func restore() {
        bridge.restore()
        dataStatus = copy?.restoreStarted
    }
}

struct NativeSettingsScreen: View {
    @StateObject private var store = NativeSettingsStore()
    @State private var confirmsRestore = false
    @Binding private var chrome: AppChrome

    init(chrome: Binding<AppChrome>) {
        _chrome = chrome
    }

    var body: some View {
        Group {
            if let copy = store.copy {
                Form {
            Section {
                Picker(copy.language, selection: Binding(
                    get: { store.language },
                    set: store.setLanguage
                )) {
                    Text("Русский").tag("RU")
                    Text("Тоҷикӣ").tag("TG")
                    Text("O‘zbek").tag("UZ")
                    Text("Türkçe").tag("TR")
                    Text("English").tag("EN")
                }
            } header: {
                Text(copy.applicationSection)
            } footer: {
                Text(copy.languageDescription)
            }

            Section {
                SettingsToggle(copy.showTranslation, copy.showTranslationDescription, isOn: readerBinding(\.showTranslation))
                SettingsToggle(copy.autoplayOnOpen, copy.autoplayOnOpenDescription, isOn: readerBinding(\.autoplayOnOpen))
                SettingsToggle(copy.autoAdvance, copy.autoAdvanceDescription, isOn: readerBinding(\.autoAdvanceAyahs))
                SettingsToggle(copy.highlightAyah, copy.highlightAyahDescription, isOn: readerBinding(\.highlightPlayingAyah))
                Picker(copy.speed, selection: readerBinding(\.playbackSpeed)) {
                    Text("0.75×").tag(Float(0.75))
                    Text("1×").tag(Float(1))
                    Text("1.25×").tag(Float(1.25))
                    Text("1.5×").tag(Float(1.5))
                    Text("2×").tag(Float(2))
                }
                Picker(copy.repeatAyah, selection: readerBinding(\.repeatCount)) {
                    Text("1×").tag(1)
                    Text("3×").tag(3)
                    Text("5×").tag(5)
                    Text(copy.repeatForever).tag(-1)
                }
            } header: {
                Text(copy.readingAudioSection)
            } footer: {
                Text(copy.readerDescription)
            }

            Section {
                Picker(copy.theme, selection: Binding(get: { store.theme }, set: store.setTheme)) {
                    Text(copy.lightTheme).tag("LIGHT")
                    Text(copy.darkTheme).tag("DARK")
                }
                NavigationLink {
                    ReaderTypographySettingsView(store: store)
                } label: {
                    HStack {
                        Text(copy.quranText)
                        Spacer()
                        Text("\(ArabicReaderFontOption.option(for: store.fontStyle, copy: copy).title) · \(store.fontSize) pt")
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
            } header: {
                Text(copy.appearanceSection)
            } footer: {
                Text(copy.appearanceDescription)
            }

            Section {
                Button(copy.downloadOffline, action: store.downloadOffline)
                    .accessibilityHint(copy.downloadDescription)
                Button(copy.sync, action: store.sync)
                    .accessibilityHint(copy.syncDescription)
                Button(copy.restoreData, role: .destructive) { confirmsRestore = true }
                    .accessibilityHint(copy.restoreDescription)
                if let status = store.dataStatus {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } header: {
                Text(copy.dataSection)
            } footer: {
                Text(copy.dataDescription)
            }
                }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle(store.copy?.settingsTitle ?? "")
        .navigationBarTitleDisplayMode(.large)
        .preferredColorScheme(store.isDark ? .dark : .light)
        .tint(chrome.primary)
        .appNavigationChrome(
            background: Color(uiColor: .systemGroupedBackground),
            tint: chrome.primary,
            isDark: store.isDark
        )
        .task { store.load() }
        .confirmationDialog(
            store.copy?.restoreTitle ?? "",
            isPresented: $confirmsRestore,
            titleVisibility: .visible
        ) {
            Button(store.copy?.restore ?? "", role: .destructive, action: store.restore)
            Button(store.copy?.cancel ?? "", role: .cancel) {}
        } message: {
            Text(store.copy?.restoreMessage ?? "")
        }
    }

    private func readerBinding<T>(_ keyPath: WritableKeyPath<ReaderSettings, T>) -> Binding<T> {
        Binding(
            get: { store.reader[keyPath: keyPath] },
            set: { nextValue in
                store.reader[keyPath: keyPath] = nextValue
                store.saveReader()
            }
        )
    }
}

private struct ReaderTypographySettingsView: View {
    @ObservedObject var store: NativeSettingsStore

    var body: some View {
        Group {
            if let copy = store.copy {
                Form {
            Section {
                QuranTypographyPreview(style: store.fontStyle, size: CGFloat(store.fontSize), copy: copy)
            } header: {
                Text(copy.preview)
            } footer: {
                Text(copy.previewDescription)
            }

            Section {
                HStack {
                    Text(copy.size)
                    Spacer()
                    Text("\(store.fontSize) pt")
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
                Slider(
                    value: Binding(
                        get: { Double(store.fontSize) },
                        set: { store.setFontSize(Int($0.rounded())) }
                    ),
                    in: 24...34,
                    step: 1
                )
                HStack {
                    Text(copy.smaller).font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    Text(copy.larger).font(.caption).foregroundStyle(.secondary)
                }
            } header: {
                Text(copy.textSize)
            }

            Section {
                ForEach(ArabicReaderFontOption.all(copy: copy)) { option in
                    Button {
                        store.setFontStyle(option.id)
                    } label: {
                        ReaderFontOptionRow(option: option, isSelected: store.fontStyle == option.id)
                    }
                    .buttonStyle(.plain)
                    .contentShape(Rectangle())
                    .accessibilityLabel("\(option.title), \(option.description)")
                    .accessibilityAddTraits(store.fontStyle == option.id ? .isSelected : [])
                }
            } header: {
                Text(copy.font)
            } footer: {
                Text(copy.fontsDescription)
            }
                }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle(store.copy?.quranText ?? "")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct QuranTypographyPreview: View {
    let style: String
    let size: CGFloat
    let copy: NativeSettingsCopy

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Al-Fātiḥah · 1")
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
            Text("ٱلْحَمْدُ لِلَّٰهِ رَبِّ ٱلْعَٰلَمِينَ 1")
                .font(arabicReaderFont(style: style, size: size))
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(copy.preview): Al-Fātiḥah, 1")
    }
}

private struct ReaderFontOptionRow: View {
    let option: ArabicReaderFontOption
    let isSelected: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(option.title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
                Text(option.description)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Text("بِسْمِ ٱللَّٰهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ")
                    .font(arabicReaderFont(style: option.id, size: 24))
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.tint)
                    .font(.title3)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct ArabicReaderFontOption: Identifiable {
    let id: String
    let title: String
    let description: String

    static func all(copy: NativeSettingsCopy) -> [ArabicReaderFontOption] {
        [
            ArabicReaderFontOption(id: "madinah_mushaf", title: "Madinah (Mushaf)", description: copy.fontMadinahDescription),
            ArabicReaderFontOption(id: "amiri_quran", title: "Amiri Quran", description: copy.fontAmiriDescription),
            ArabicReaderFontOption(id: "scheherazade_new", title: "Scheherazade New", description: copy.fontScheherazadeDescription),
            ArabicReaderFontOption(id: "noto_naskh_arabic", title: "Noto Naskh Arabic", description: copy.fontNotoNaskhDescription),
            ArabicReaderFontOption(id: "lateef", title: "Lateef", description: copy.fontLateefDescription),
            ArabicReaderFontOption(id: "noto_nastaliq_urdu", title: "Noto Nastaliq Urdu", description: copy.fontNastaliqDescription),
            ArabicReaderFontOption(id: "aref_ruqaa_ink", title: "Aref Ruqaa Ink", description: copy.fontRuqaaDescription),
        ]
    }

    static func option(for id: String, copy: NativeSettingsCopy?) -> ArabicReaderFontOption {
        guard let copy else {
            return ArabicReaderFontOption(id: "madinah_mushaf", title: "Madinah (Mushaf)", description: "")
        }
        return all(copy: copy).first { $0.id == id } ?? all(copy: copy)[0]
    }
}

private struct SettingsToggle: View {
    let title: String
    let description: String
    @Binding var isOn: Bool

    init(_ title: String, _ description: String, isOn: Binding<Bool>) {
        self.title = title
        self.description = description
        _isOn = isOn
    }

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(description)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityHint(description)
    }
}
