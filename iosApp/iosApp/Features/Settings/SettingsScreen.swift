import ComposeApp
import SwiftUI
import UniformTypeIdentifiers

/// The settings screen (Android `SettingsScreen`): a large title over grouped cards of rows with coloured icon tiles. Language, theme,
/// palette go through `AppEnvironment` (the whole UI follows at once, theme and palette with the circular reveal); everything else
/// through the Kotlin `SettingsViewModel`.
struct SettingsScreen: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(Router.self) private var router
    @Environment(\.toaster) private var toaster
    @State private var model = SettingsModel()
    @State private var stagger = StaggerState(screen: "settings")
    @State private var sheet: SettingsSheetKind?
    @State private var backupAction: BackupAction?
    @State private var exportIntent: ExportIntent = .share
    @State private var showImporter = false
    @State private var audioBytes: Int64?
    private var env: AppEnvironment { AppEnvironment.shared }

    var body: some View {
        let s = copy.s7Settings
        let trust = copy.trust
        let transfer = s.transfer
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.section) {
                appearance(s).staggeredEnter(index: 0, state: stagger)
                reading(s).staggeredEnter(index: 1, state: stagger)
                sound(s).staggeredEnter(index: 2, state: stagger)
                SettingsSection(title: s.sectionMemorization) {
                    QhNavRow(title: copy.plan.title, description: s.myPlanDescription, icon: "flag", iconTint: qh.colors.gold) {
                        router.push(.plan)
                    }
                }
                .staggeredEnter(index: 3, state: stagger)
                ReminderSection(model: model).staggeredEnter(index: 4, state: stagger)
                SettingsSection(title: s.sectionLanguage) {
                    QhValueRow(title: s.language, value: env.language.nativeLabel, icon: "globe", iconTint: qh.colors.primary) {
                        sheet = .language
                    }
                }
                .staggeredEnter(index: 5, state: stagger)
                SettingsSection(title: s.sectionData) {
                    QhValueRow(title: copy.s7Downloads.title, value: audioBytes.map { copy.s7Downloads.sizeLabel(bytes: $0) } ?? "…",
                               icon: "arrow.down.circle", iconTint: qh.colors.primary) {
                        router.push(.downloads)
                    }
                    QhRowDivider()
                    QhNavRow(title: copy.analyticsCopy.notesRowTitle, description: copy.analyticsCopy.notesRowBody,
                             icon: "note.text", iconTint: qh.colors.gold) {
                        router.push(.notes)
                    }
                }
                .staggeredEnter(index: 6, state: stagger)
                SettingsSection(title: transfer.sectionTitle, footnote: transfer.footnote) {
                    QhNavRow(title: transfer.backupTitle, description: lastBackupLine(transfer),
                             icon: "square.and.arrow.down", iconTint: qh.colors.primary) { sheet = .backup }
                        .disabled(model.busy.value.boolValue)
                }
                .staggeredEnter(index: 7, state: stagger)
                SettingsSection(title: trust.sectionPrivacy) {
                    QhNavRow(title: s.privacyPolicy, description: trust.privacyRowDescription, icon: "hand.raised", iconTint: qh.colors.gold) {
                        router.push(.privacy)
                    }
                    QhRowDivider()
                    QhNavRow(title: trust.sourcesRowTitle, description: trust.sourcesRowDescription, icon: "books.vertical",
                             iconTint: qh.colors.primary) { router.push(.sources) }
                }
                .staggeredEnter(index: 8, state: stagger)
                about(s).staggeredEnter(index: 9, state: stagger)
                footer
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x2)
            .padding(.bottom, QhSpacing.x8)
        }
        .navigationTitle(s.native.settingsTitle)
        .navigationBarTitleDisplayMode(.large)
        .qhNavigationStyle()
        .task {
            model.bind(env: env) { location, name in present(location: location, name: name) }
            await model.refreshNotificationStatus()
            audioBytes = IosTransferApply.shared.audioBytes()
            stagger.markPlayed()
        }
        .onAppear { QhHaptics.shared.prepare() }
        .sheet(item: $sheet, onDismiss: runBackupAction) { kind in
            switch kind {
            case .typography: TypographySheet(model: model)
            case .language: LanguageSheet()
            case .reciter: ReciterSheet(model: model)
            case .backup: BackupSheet { backupAction = $0 }
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .plainText, .data]) { result in
            guard case let .success(url) = result else { return }
            if let location = TransferAccess.stage(url) {
                model.vm.importFrom(location: location)
            } else {
                toaster.show(transfer.importUnreadable, kind: .error)
            }
        }
        .alert(transfer.importDialogTitle, isPresented: importBinding, presenting: model.importPrompt.value) { prompt in
            Button(transfer.replace, role: .destructive) { model.vm.confirmReplace() }
            Button(transfer.cancel, role: .cancel) {}
        } message: { prompt in
            Text(transfer.importMessage(preview: prompt.preview, zone: .companion.currentSystemDefault()))
        }
    }

    // MARK: Sections

    private func appearance(_ s: SettingsCopy) -> some View {
        SettingsSection(title: s.sectionAppearance) {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                HStack(spacing: QhSpacing.x3) {
                    QhIconTile(icon: "circle.lefthalf.filled", tint: qh.colors.gold)
                    Text(s.theme).qhText({ $0.title2 })
                }
                QhSegmentedControl(options: [s.themeLight, s.themeDark, s.themeSystem], selection: themeBinding,
                                   icons: ["sun.max", "moon", "iphone"])
                    .frame(height: 44)
                Text(s.palette).qhText({ $0.callout }, color: { $0.inkSecondary })
                PaletteSwatches(selected: env.theme.palette, labels: [s.paletteForest, s.paletteSand, s.paletteOcean]) { palette in
                    env.setPalette(palette)
                    IosTransferApply.shared.refreshWidget()
                }
            }
            .padding(QhSpacing.card)
            QhRowDivider()
            QhValueRow(title: s.arabicFont, value: "\(fontShortName(model.fontStorage)) · \(model.fontSize)",
                       icon: "textformat.size", iconTint: qh.colors.due) { sheet = .typography }
            QhRowDivider()
            QhSwitchRow(title: s.haptics, isOn: hapticsBinding, description: s.hapticsDescription, icon: "iphone.radiowaves.left.and.right",
                        iconTint: qh.colors.primary)
            QhRowDivider()
            QhSwitchRow(title: s.reduceMotion, isOn: reduceMotionBinding, description: s.reduceMotionDescription,
                        icon: "figure.walk.motion", iconTint: qh.colors.gold)
        }
    }

    private func reading(_ s: SettingsCopy) -> some View {
        SettingsSection(title: s.sectionReading) {
            QhSwitchRow(title: s.translation, isOn: readerBinding(\.showTranslation, model.setShowTranslation),
                        description: s.translationDescription, icon: "character.book.closed", iconTint: qh.colors.primary)
            QhRowDivider()
            QhSwitchRow(title: s.highlightAyah, isOn: readerBinding(\.highlightPlayingAyah, model.setHighlight),
                        description: s.highlightAyahDescription, icon: "highlighter", iconTint: qh.colors.gold)
            QhRowDivider()
            QhSwitchRow(title: s.keepScreenOn,
                        isOn: Binding(get: { model.keepScreenOn.value.boolValue }, set: { model.vm.setKeepScreenOn(value: $0) }),
                        description: s.keepScreenOnDescription, icon: "eye", iconTint: qh.colors.learned)
        }
    }

    private func sound(_ s: SettingsCopy) -> some View {
        SettingsSection(title: s.sectionSound) {
            QhSwitchRow(title: s.autoplay, isOn: readerBinding(\.autoplayOnOpen, model.setAutoplay),
                        description: s.autoplayDescription, icon: "play.circle", iconTint: qh.colors.primary)
            QhRowDivider()
            QhValueRow(title: s.reciter, value: model.reciter.value.shortName(language: env.language),
                       icon: "waveform", iconTint: qh.colors.gold) { sheet = .reciter }
        }
    }

    private func about(_ s: SettingsCopy) -> some View {
        SettingsSection(title: s.sectionAbout) {
            if !SettingsLinks.versionLabel.isEmpty {
                QhValueRow(title: s.version, value: SettingsLinks.versionLabel, icon: "info.circle", iconTint: qh.colors.primary)
                QhRowDivider()
            }
            // No dead button: the rate row appears once the app has an App Store id (SettingsLinks.appStoreId).
            if let review = SettingsLinks.reviewURL {
                QhNavRow(title: s.rateApp, description: s.rateAppDescription, icon: "hand.thumbsup", iconTint: qh.colors.gold) {
                    if !SettingsLinks.open(review) { toaster.show(s.storeUnavailable, kind: .error) }
                }
                QhRowDivider()
            }
            QhNavRow(title: s.showTipsAgain, description: s.showTipsAgainDescription, icon: "lightbulb", iconTint: qh.colors.gold) {
                HintFlags.shared.resetAll()
                toaster.show(s.tipsWillShowAgain, kind: .success)
            }
        }
    }

    private var footer: some View {
        VStack(spacing: QhSpacing.x3) {
            OrnamentDivider().padding(.horizontal, QhSpacing.x10)
            let version = SettingsLinks.versionLabel
            Text(version.isEmpty ? "Quran Hifz" : "Quran Hifz · \(version)")
                .qhText({ $0.callout }, color: { $0.inkSecondary })
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Bindings

    private var themeBinding: Binding<Int> {
        Binding(
            get: {
                switch env.theme.mode {
                case .light: return 0
                case .dark: return 1
                case .system: return 2
                }
            },
            set: { index in env.setThemeMode(index == 0 ? .light : (index == 1 ? .dark : .system)) }
        )
    }

    private var hapticsBinding: Binding<Bool> {
        Binding(get: { model.haptics.value.boolValue }, set: { on in
            env.setHaptics(on)
            // The theme's own haptics are still off at this moment: let the person feel the new state.
            if on { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        })
    }

    private var reduceMotionBinding: Binding<Bool> {
        Binding(get: { model.reduceMotion.value.boolValue }, set: { env.setReduceMotion($0) })
    }

    private func readerBinding(_ keyPath: KeyPath<SurahReaderPreferences, Bool>, _ set: @escaping (Bool) -> Void) -> Binding<Bool> {
        Binding(get: { model.reader.value[keyPath: keyPath] }, set: set)
    }

    private var importBinding: Binding<Bool> {
        Binding(get: { model.importPrompt.value != nil }, set: { if !$0 { model.vm.dismissImportPrompt() } })
    }

    // MARK: Backup

    private func lastBackupLine(_ transfer: TransferCopy) -> String {
        guard let at = model.lastExportAt.value else { return transfer.backupDescription }
        return transfer.lastBackupLabel(epochMillis: at.int64Value, nowMillis: Int64(Date().timeIntervalSince1970 * 1000),
                                        zone: .companion.currentSystemDefault())
    }

    /// The sheet is gone: run what the person chose in it (the system pickers need the screen, not the sheet, in front).
    private func runBackupAction() {
        guard let action = backupAction else { return }
        backupAction = nil
        switch action {
        case .save: exportIntent = .save; model.vm.exportForShare()
        case .share: exportIntent = .share; model.vm.exportForShare()
        case .restore: showImporter = true
        }
    }

    @MainActor
    private func present(location: String, name: String) {
        guard let url = TransferAccess.url(of: location) else { return }
        switch exportIntent {
        case .share: ShareSheet.present(items: [url])
        case .save: FileExportPicker.present(url: url)
        }
    }
}

enum SettingsSheetKind: String, Identifiable {
    case typography, language, reciter, backup
    var id: String { rawValue }
}

enum BackupAction { case save, share, restore }
private enum ExportIntent { case save, share }

/// "Save to a file": the system's document picker in export mode over the temporary copy (the file is copied where the person chooses).
@MainActor
enum FileExportPicker {
    static func present(url: URL) {
        guard let root = ShareSheet.topViewController() else { return }
        let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
        root.present(picker, animated: true)
    }
}
