import ComposeApp
import SwiftUI
import UIKit

// MARK: - Palette swatches

/// The three palettes painted with their real tokens; the selected one pops with a ring and a check. Changing it plays the reveal.
struct PaletteSwatches: View {
    @Environment(\.qh) private var qh
    let selected: QhThemePalette
    let labels: [String]
    let onSelect: (QhThemePalette) -> Void

    var body: some View {
        HStack(spacing: QhSpacing.x3) {
            ForEach(Array(QhThemePalette.allCases.enumerated()), id: \.element) { index, palette in
                swatch(palette, label: labels[safe: index] ?? palette.rawValue)
            }
        }
    }

    private func swatch(_ palette: QhThemePalette, label: String) -> some View {
        let colors = QhColors.resolve(dark: qh.isDark, palette: palette)
        let isSelected = palette == selected
        return VStack(spacing: 6) {
            ZStack {
                Circle().fill(colors.bg)
                // Half the disc in the primary, a gold dot: the palette in one glance.
                Circle().fill(colors.primary).mask(
                    Rectangle().frame(width: 28, height: 56).offset(x: 14)
                )
                Circle().fill(colors.gold).frame(width: 14, height: 14).offset(x: -14, y: 14)
            }
            .frame(width: 56, height: 56)
            .clipShape(Circle())
            .overlay(Circle().strokeBorder(qh.colors.hairline, lineWidth: 1))
            .padding(4)
            .overlay(Circle().strokeBorder(qh.colors.primary, lineWidth: 2).opacity(isSelected ? 1 : 0))
            .overlay(alignment: .bottomTrailing) {
                if isSelected {
                    Image(systemName: "checkmark").font(.system(size: 10, weight: .bold))
                        .foregroundStyle(qh.colors.onPrimary)
                        .frame(width: 20, height: 20)
                        .background(qh.colors.primary, in: Circle())
                        .overlay(Circle().strokeBorder(qh.colors.surface, lineWidth: 2))
                        .transition(.scale(scale: 0.3).combined(with: .opacity))
                }
            }
            .scaleEffect(isSelected ? 1.04 : 1)
            Text(label).qhText({ $0.label }, color: { isSelected ? $0.ink : $0.inkSecondary }).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        .onTapGesture {
            guard !isSelected else { return }
            QhHaptics.shared.perform(.segment)
            withQhAnimation(QhMotion.bouncy) { onSelect(palette) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Reading fonts

/// The seven reading fonts of Kotlin `ReadingFontStyle` with the face to draw them in.
struct ReadingFontChoice: Identifiable, Hashable {
    let storage: String
    let name: String
    let family: String?
    let postScript: String?
    var id: String { storage }

    /// A usable font name (a face that is not registered falls back to the system's Arabic face).
    func font(size: CGFloat) -> Font {
        if let postScript, UIFont(name: postScript, size: size) != nil { return .custom(postScript, size: size) }
        if let family, let first = UIFont.fontNames(forFamilyName: family).first { return .custom(first, size: size) }
        return .system(size: size)
    }

    static let all: [ReadingFontChoice] = [
        .init(storage: "madinah_mushaf", name: "Madinah", family: nil, postScript: "KFGQPCHafsEx1UthmanicScript-Reg"),
        .init(storage: "amiri_quran", name: "Amiri", family: nil, postScript: "AmiriQuran-Regular"),
        .init(storage: "scheherazade_new", name: "Scheherazade", family: nil, postScript: "ScheherazadeNew-Regular"),
        .init(storage: "noto_naskh_arabic", name: "Naskh", family: nil, postScript: "NotoNaskhArabic-Regular"),
        .init(storage: "lateef", name: "Lateef", family: nil, postScript: "Lateef-Regular"),
        .init(storage: "noto_nastaliq_urdu", name: "Nastaliq", family: nil, postScript: "NotoNastaliqUrdu-Regular"),
        .init(storage: "aref_ruqaa_ink", name: "Ruqaa", family: "Aref Ruqaa Ink", postScript: nil),
    ]
}

func fontShortName(_ storage: String) -> String {
    ReadingFontChoice.all.first { $0.storage == storage }?.name ?? "Madinah"
}

// MARK: - Typography sheet

/// "Arabic font": a live preview of Al-Fatiha 1:2 on reader paper (a new size springs, a new font crossfades), a size slider and the
/// carousel of the seven fonts.
struct TypographySheet: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(\.dismiss) private var dismiss
    let model: SettingsModel
    @State private var size: Double = 28

    private let sample = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"

    var body: some View {
        let n = copy.s7Settings.native
        let current = ReadingFontChoice.all.first { $0.storage == model.fontStorage } ?? ReadingFontChoice.all[0]
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                QhSheetHeader(title: copy.s7Settings.arabicFont, subtitle: n.fontsDescription, closeLabel: n.done) { dismiss() }
                preview(current)
                VStack(alignment: .leading, spacing: QhSpacing.x2) {
                    HStack {
                        Text(n.textSize).qhText({ $0.title2 })
                        Spacer()
                        Text("\(Int(size))").qhText({ $0.title2 }, color: { $0.primary }).contentTransition(.numericText(value: size))
                    }
                    HStack(spacing: QhSpacing.x3) {
                        Image(systemName: "textformat.size.smaller").foregroundStyle(qh.colors.inkSecondary).accessibilityHidden(true)
                        QhSlider(value: $size, range: 22...40, step: 1)
                            .accessibilityLabel(n.textSize)
                            .accessibilityValue("\(Int(size))")
                        Image(systemName: "textformat.size.larger").foregroundStyle(qh.colors.inkSecondary).accessibilityHidden(true)
                    }
                }
                Text(n.font).qhText({ $0.title2 })
                fonts(current, native: n)
            }
            .padding(.top, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x6)
        }
        .scrollIndicators(.hidden)
        .onAppear { size = Double(model.fontSize) }
        .onChange(of: size) { _, new in model.setFontSize(Int(new)) }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackground(qh.colors.surface)
        .padding(.horizontal, QhSpacing.screen)
        .padding(.top, QhSpacing.x2)
    }

    private func preview(_ font: ReadingFontChoice) -> some View {
        ZStack {
            Text(sample)
                .font(font.font(size: CGFloat(size)))
                .foregroundStyle(qh.colors.ink)
                .multilineTextAlignment(.center)
                .lineSpacing(CGFloat(size) * 0.5)
                .environment(\.layoutDirection, .rightToLeft)
                .id(font.storage)
                .transition(.opacity)
                .minimumScaleFactor(0.5)
        }
        .animation(.easeInOut(duration: QhMotion.medium), value: font.storage)
        .animation(QhMotion.gentle, value: size)
        .frame(maxWidth: .infinity, minHeight: 120)
        .padding(QhSpacing.cardLarge)
        .background(qh.colors.readerBg, in: qh.shapes.lgShape)
        .overlay(qh.shapes.lgShape.strokeBorder(qh.colors.hairline, lineWidth: 1))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(copy.s7Settings.native.ayahPreview)
    }

    private func fonts(_ current: ReadingFontChoice, native: NativeSettingsCopy) -> some View {
        ScrollView(.horizontal) {
            HStack(spacing: QhSpacing.x3) {
                ForEach(ReadingFontChoice.all) { choice in
                    let selected = choice == current
                    VStack(spacing: QhSpacing.x2) {
                        Text("بِسْمِ").font(choice.font(size: 30)).foregroundStyle(qh.colors.ink)
                            .environment(\.layoutDirection, .rightToLeft).frame(height: 56)
                        Text(choice.name).qhText({ $0.label }, color: { selected ? $0.primary : $0.inkSecondary })
                    }
                    .frame(width: 104).padding(.vertical, QhSpacing.x3)
                    .background(selected ? qh.colors.primaryContainer : qh.colors.surfaceMuted, in: qh.shapes.mdShape)
                    .overlay(qh.shapes.mdShape.strokeBorder(selected ? qh.colors.primary : .clear, lineWidth: 2))
                    .overlay(alignment: .topTrailing) {
                        if selected {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(qh.colors.primary).padding(6)
                        }
                    }
                    .pressable(haptic: .segment, shape: qh.shapes.mdShape) { model.setFont(choice.storage) }
                    .accessibilityLabel(choice.name)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            .padding(.vertical, 2)
        }
        .scrollIndicators(.hidden)
        .padding(.horizontal, -QhSpacing.screen)
        .contentMargins(.horizontal, QhSpacing.screen, for: .scrollContent)
    }
}

// MARK: - Language sheet

struct LanguageSheet: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(\.dismiss) private var dismiss
    private var env: AppEnvironment { AppEnvironment.shared }

    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            QhSheetHeader(title: copy.s7Settings.languageSheetTitle, closeLabel: copy.s7Settings.native.done) { dismiss() }
            ForEach(Array(AppLanguage.allCases), id: \.self) { language in
                let selected = language == env.language
                HStack(spacing: QhSpacing.x3) {
                    Text(language.nativeLabel).qhText({ $0.title2 })
                    Spacer()
                    Text(language.code.uppercased()).qhText({ $0.label }, color: { $0.inkTertiary })
                    Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(selected ? qh.colors.primary : qh.colors.inkTertiary)
                        .font(.system(size: 22))
                        .contentTransition(.symbolEffect(.replace))
                }
                .padding(.horizontal, QhSpacing.card).frame(minHeight: 56)
                .background(selected ? qh.colors.primaryContainer : qh.colors.surfaceMuted, in: qh.shapes.mdShape)
                .overlay(qh.shapes.mdShape.strokeBorder(selected ? qh.colors.primary : .clear, lineWidth: 1.5))
                .pressable(haptic: .segment, shape: qh.shapes.mdShape) {
                    env.setLanguage(language)
                    DispatchQueue.main.asyncAfter(deadline: .now() + (QhMotion.reduced ? 0 : 0.26)) { dismiss() }
                }
                .accessibilityLabel(language.nativeLabel)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.screen)
        .padding(.top, QhSpacing.x4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(qh.colors.surface.ignoresSafeArea())
        .presentationDetents([.height(480)])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackground(qh.colors.surface)
    }
}

// MARK: - Backup sheet

struct BackupSheet: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(\.dismiss) private var dismiss
    let choose: (BackupAction) -> Void

    var body: some View {
        let t = copy.s7Settings.transfer
        VStack(alignment: .leading, spacing: QhSpacing.x1) {
            QhSheetHeader(title: t.backupTitle, closeLabel: copy.s7Settings.native.done) { dismiss() }
            QhNavRow(title: t.saveTitle, description: t.saveDescription, icon: "square.and.arrow.down", iconTint: qh.colors.primary) {
                choose(.save); dismiss()
            }
            QhNavRow(title: t.shareTitle, description: t.shareDescription, icon: "square.and.arrow.up", iconTint: qh.colors.gold) {
                choose(.share); dismiss()
            }
            QhNavRow(title: t.restoreTitle, description: t.restoreDescription, icon: "doc.badge.arrow.up", iconTint: qh.colors.danger) {
                choose(.restore); dismiss()
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.x2)
        .padding(.top, QhSpacing.x4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(qh.colors.surface.ignoresSafeArea())
        .presentationDetents([.height(380)])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackground(qh.colors.surface)
    }
}

// MARK: - Reciter sheet

/// The reciters in three groups with a sample button (Surah 112:1 on the app's one player); the chosen one has a check and an outline.
/// The sheet stays open to compare.
struct ReciterSheet: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(\.dismiss) private var dismiss
    @Environment(\.toaster) private var toaster
    let model: SettingsModel
    @State private var preview = FlowState(IosUi.shared.reciterPreview.state)
    @State private var failures: FlowEvents<KotlinUnit>?
    private var env: AppEnvironment { AppEnvironment.shared }

    var body: some View {
        let audio = copy.s7Audio
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                QhSheetHeader(title: audio.reciterSheetTitle, subtitle: audio.reciterSheetHint, closeLabel: copy.s7Settings.native.done) { dismiss() }
                ForEach([ReciterGroup.teaching, .murattal, .mujawwad], id: \.self) { group in
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(audio.groupTitle(group: group)).qhText({ $0.title2 }).accessibilityAddTraits(.isHeader)
                            if group == .teaching {
                                Text(audio.groupTeachingHint).qhText({ $0.callout }, color: { $0.inkSecondary })
                            }
                        }
                        ForEach(Reciters.shared.inGroup(group: group), id: \.id) { reciter in row(reciter, audio: audio) }
                    }
                    .padding(.top, QhSpacing.x2)
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x4)
            .padding(.bottom, QhSpacing.x6)
        }
        .scrollIndicators(.hidden)
        .background(qh.colors.surface.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
        .presentationBackground(qh.colors.surface)
        .onAppear {
            failures = FlowEvents(IosUi.shared.reciterPreview.failures) { _ in toaster.show(audio.sampleFailed, kind: .error) }
        }
        .onDisappear { IosUi.shared.reciterPreview.stop() }
    }

    private func row(_ reciter: Reciter, audio: AudioCopy) -> some View {
        let selected = model.reciter.value.id == reciter.id
        let state = sampleState(reciter)
        return HStack(spacing: QhSpacing.x3) {
            VStack(alignment: .leading, spacing: 2) {
                Text(reciter.name(language: env.language)).qhText({ $0.title2 })
                Text(audio.styleOf(reciter: reciter)).qhText({ $0.callout }, color: { $0.inkSecondary })
            }
            Spacer(minLength: QhSpacing.x2)
            Button {
                QhHaptics.shared.perform(.tick)
                IosUi.shared.reciterPreview.toggle(reciter: reciter)
            } label: {
                ZStack {
                    Circle().fill(state == .idle ? qh.colors.surfaceStrong : qh.colors.primary)
                    switch state {
                    case .idle: Image(systemName: "play.fill").foregroundStyle(qh.colors.ink)
                    case .loading: DotSpinner(color: qh.colors.onPrimary)
                    case .playing: Image(systemName: "stop.fill").foregroundStyle(qh.colors.onPrimary)
                    }
                }
                .font(.system(size: 14, weight: .bold))
                .frame(width: 40, height: 40)
                .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(state == .playing ? audio.stopSample : audio.playSample)
            Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 22))
                .foregroundStyle(selected ? qh.colors.primary : qh.colors.inkTertiary)
                .accessibilityHidden(true)
        }
        .padding(.leading, QhSpacing.card).padding(.trailing, QhSpacing.x2).padding(.vertical, QhSpacing.x1)
        .frame(minHeight: 64)
        .background(selected ? qh.colors.primaryContainer : qh.colors.surfaceMuted, in: qh.shapes.mdShape)
        .overlay(qh.shapes.mdShape.strokeBorder(selected ? qh.colors.primary : .clear, lineWidth: 1.5))
        .contentShape(qh.shapes.mdShape)
        .onTapGesture {
            QhHaptics.shared.perform(.segment)
            withQhAnimation(QhMotion.snappy) { model.vm.setReciter(value: reciter) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private enum Sample { case idle, loading, playing }

    private func sampleState(_ reciter: Reciter) -> Sample {
        switch onEnum(of: preview.value) {
        case .idle: return .idle
        case let .loading(l): return l.reciterId == reciter.id ? .loading : .idle
        case let .playing(p): return p.reciterId == reciter.id ? .playing : .idle
        }
    }
}
