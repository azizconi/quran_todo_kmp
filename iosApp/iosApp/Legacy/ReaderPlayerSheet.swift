import SwiftUI
import UIKit
import ComposeApp

struct ReaderPracticeDraft {
    var rangeEnabled: Bool
    var startAyah: Int
    var endAyah: Int
    var repeatCount: Int
    var pauseSeconds: Int

    init(_ settings: ReaderPracticeSettings) {
        rangeEnabled = settings.rangeEnabled
        startAyah = Int(settings.startAyah)
        endAyah = Int(settings.endAyah)
        repeatCount = Int(settings.rangeRepeatCount)
        pauseSeconds = Int(settings.pauseSeconds)
    }
}

enum ReaderPlayerFeature: String {
    case range, pause
    var guideIndex: Int { self == .range ? 0 : 1 }
    var storageKey: String { self == .range ? "Range" : "Pause" }
}

struct ReaderPlayerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var preferences: ReaderPreferences
    @State private var practice: ReaderPracticeDraft
    @State private var showGuide: Bool
    let feature: ReaderPlayerFeature
    let ayahCount: Int
    let copy: PlayerFeatureCopy
    let theme: NativeSurahReaderTheme
    let onGuideSeen: () -> Void
    let onSave: (ReaderPreferences, ReaderPracticeDraft, Bool) -> Void

    init(
        feature: ReaderPlayerFeature, currentAyah: Int,
        preferences: ReaderPreferences, practice: ReaderPracticeSettings, ayahCount: Int,
        copy: PlayerFeatureCopy, theme: NativeSurahReaderTheme, guideSeen: Bool,
        onGuideSeen: @escaping () -> Void,
        onSave: @escaping (ReaderPreferences, ReaderPracticeDraft, Bool) -> Void
    ) {
        _preferences = State(initialValue: preferences)
        var draft = ReaderPracticeDraft(practice)
        if feature == .range && !draft.rangeEnabled {
            draft.startAyah = min(max(currentAyah, 1), max(ayahCount, 1))
            draft.endAyah = draft.startAyah
        }
        if ![3, 5, -1].contains(draft.repeatCount) { draft.repeatCount = 3 }
        _practice = State(initialValue: draft)
        _showGuide = State(initialValue: !guideSeen)
        self.feature = feature
        self.ayahCount = max(ayahCount, 1)
        self.copy = copy
        self.theme = theme
        self.onGuideSeen = onGuideSeen
        self.onSave = onSave
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                if showGuide { guide }
                else if feature == .range { rangePicker }
                else { pausePicker }
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 14)
        .padding(.bottom, 20)
        .background(theme.background.ignoresSafeArea())
        .foregroundStyle(theme.onSurface)
        .tint(theme.primary)
        .preferredColorScheme(theme.colorScheme)
        .modifier(PlayerSheetPresentation(height: showGuide ? 520 : feature == .range ? (practice.rangeEnabled ? 530 : 490) : 360))
    }

    private var header: some View {
        HStack(spacing: 4) {
            if !showGuide {
                Text(feature == .range ? copy.chooseAyahs : copy.pause)
                    .font(.title3.bold()).fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                Button { showGuide = true } label: {
                    Image(systemName: "questionmark.circle").frame(width: 44, height: 44)
                }
                .accessibilityLabel(copy.help)
            } else { Spacer() }
            Button { dismiss() } label: {
                Image(systemName: "xmark").frame(width: 44, height: 44)
            }
            .accessibilityLabel(copy.close)
        }
        .buttonStyle(.plain)
    }

    private var guide: some View {
        VStack(spacing: 12) {
            PlayerGuideArtwork(step: feature.guideIndex).frame(height: 180).accessibilityHidden(true)
            Text(copy.guideTitle(step: Int32(feature.guideIndex)))
                .font(.system(size: 24, weight: .bold)).multilineTextAlignment(.center)
            Text(copy.guideBody(step: Int32(feature.guideIndex)))
                .foregroundStyle(theme.textSecondary).multilineTextAlignment(.center)
            primaryButton(copy.tryIt) {
                onGuideSeen()
                showGuide = false
            }.padding(.top, 12)
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    private var rangePicker: some View {
        VStack(spacing: 16) {
            HStack(spacing: 24) {
                VStack(spacing: 0) {
                    Text(copy.from).font(.subheadline).foregroundStyle(theme.textSecondary)
                    Picker(copy.from, selection: Binding(
                        get: { practice.startAyah },
                        set: { practice.startAyah = $0; practice.endAyah = max($0, practice.endAyah) }
                    )) {
                        ForEach(1...ayahCount, id: \.self) { Text(String($0)).tag($0) }
                    }
                    .pickerStyle(.wheel).labelsHidden()
                    .accessibilityLabel(copy.from)
                }
                VStack(spacing: 0) {
                    Text(copy.to).font(.subheadline).foregroundStyle(theme.textSecondary)
                    Picker(copy.to, selection: $practice.endAyah) {
                        ForEach(practice.startAyah...ayahCount, id: \.self) { Text(String($0)).tag($0) }
                    }
                    .pickerStyle(.wheel).labelsHidden()
                    .accessibilityLabel(copy.to)
                }
            }
            .frame(height: 190).clipped()
            Text("\(copy.ayahsLabel) \(practice.startAyah)–\(practice.endAyah)")
                .font(.body.weight(.semibold)).foregroundStyle(theme.primary)
            HStack(spacing: 8) {
                ForEach([3, 5, -1], id: \.self) { value in
                    Button { practice.repeatCount = value } label: {
                        Text(value == -1 ? copy.onLoop : "\(value) \(value == 3 ? copy.timesFew : copy.timesLabel)")
                            .font(.subheadline.weight(.medium)).frame(maxWidth: .infinity, minHeight: 44)
                            .foregroundStyle(practice.repeatCount == value ? theme.onPrimary : theme.onSurface)
                            .background(practice.repeatCount == value ? theme.primary : theme.surfaceAlt,
                                        in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(practice.repeatCount == value ? .isSelected : [])
                }
            }
            primaryButton(copy.startRepeat) {
                practice.rangeEnabled = true
                preferences.repeatCount = 1
                onSave(preferences, practice, true)
                dismiss()
            }
            if practice.rangeEnabled {
                Button(copy.stopRepeat) {
                    practice.rangeEnabled = false
                    preferences.repeatCount = 1
                    onSave(preferences, practice, false)
                    dismiss()
                }.frame(minHeight: 36)
            }
        }
    }

    private var pausePicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(copy.pauseHint).font(.subheadline).foregroundStyle(theme.textSecondary).padding(.bottom, 8)
            ForEach([0, 3, 5, 10], id: \.self) { seconds in
                Button {
                    practice.pauseSeconds = seconds
                    onSave(preferences, practice, false)
                    dismiss()
                } label: {
                    HStack {
                        Text(seconds == 0 ? copy.noPause : "\(seconds) \(copy.seconds)")
                        Spacer()
                        if seconds == practice.pauseSeconds {
                            Image(systemName: "checkmark").foregroundStyle(theme.primary)
                        }
                    }
                    .padding(.horizontal, 16).frame(minHeight: 52)
                    .background(seconds == practice.pauseSeconds ? theme.surfaceAlt : theme.background,
                                in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(seconds == practice.pauseSeconds ? .isSelected : [])
            }
        }
    }

    private func primaryButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.body.weight(.semibold)).foregroundStyle(theme.onPrimary)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(theme.primary, in: RoundedRectangle(cornerRadius: 16))
        }.buttonStyle(.plain)
    }
}

/// Three measured square cells in the generated transparent atlas, shared with Compose.
private struct PlayerGuideArtwork: View {
    let step: Int
    private static let images: [UIImage] = {
        guard let atlas = UIImage(named: "PlayerGuideArt")?.cgImage else { return [] }
        let width = atlas.width / 3
        return (0..<3).compactMap { index in
            atlas.cropping(to: CGRect(x: index * width, y: 0, width: width, height: atlas.height))
                .map { UIImage(cgImage: $0) }
        }
    }()

    var body: some View {
        if Self.images.indices.contains(step) {
            Image(uiImage: Self.images[step]).resizable().scaledToFit()
                .frame(maxWidth: .infinity)
        }
    }
}

private struct PlayerSheetPresentation: ViewModifier {
    let height: CGFloat
    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.presentationDetents([.height(height), .large])
                .presentationDragIndicator(.visible)
        } else {
            content.background(LegacyPlayerSheetConfiguration())
        }
    }
}

private struct LegacyPlayerSheetConfiguration: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> SheetController { SheetController() }
    func updateUIViewController(_ uiViewController: SheetController, context: Context) {}
    final class SheetController: UIViewController {
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            var ancestor: UIViewController? = parent
            while let controller = ancestor {
                if let sheet = controller.sheetPresentationController {
                    sheet.detents = [.medium(), .large()]
                    sheet.prefersGrabberVisible = true
                    sheet.preferredCornerRadius = 28
                    return
                }
                ancestor = controller.parent
            }
        }
    }
}
