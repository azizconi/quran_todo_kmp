import ComposeApp
import SwiftUI

/// What is open on top of the reader.
enum ReaderSheet: Identifiable, Equatable {
    case surah(status: Bool)
    case ayah(Int)
    case appearance
    case goTo
    case player

    var id: String {
        switch self {
        case .surah: return "surah"
        case .ayah(let i): return "ayah-\(i)"
        case .appearance: return "appearance"
        case .goTo: return "goto"
        case .player: return "player"
        }
    }
}

extension View {
    /// Detents, grabber and `xl` corners of a sheet in our style, on the `surface` colour.
    func qhSheetStyle(_ detents: Set<PresentationDetent> = [.medium, .large]) -> some View {
        modifier(QhSheetStyle(detents: detents))
    }
}

private struct QhSheetStyle: ViewModifier {
    @Environment(\.qh) private var qh
    let detents: Set<PresentationDetent>
    func body(content: Content) -> some View {
        content
            .background(qh.colors.surface.ignoresSafeArea())
            .foregroundStyle(qh.colors.ink)
            .presentationDetents(detents)
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(28)
    }
}

// MARK: - Surah sheet (menu + status)

struct SurahSheetContent: View {
    @Environment(\.qh) private var qh
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    let surahName: String
    let status: SurahTodoStatus?
    let learnedAyahs: Int
    let initialStatusPage: Bool
    let onChoice: (SurahTarget) -> Void
    let onGoTo: () -> Void

    @State private var page = 0   // 0 menu, 1 status
    @State private var confirming: SurahTarget?

    private var current: SurahHifzStatus? {
        switch status {
        case .some(.learned): return SurahHifzStatus.learned
        case .some(.learning): return SurahHifzStatus.learning
        default: return nil
        }
    }

    var body: some View {
        let r = copy.reader
        VStack(alignment: .leading, spacing: 0) {
            if page == 1, !initialStatusPage {
                HStack(spacing: 0) {
                    QhIconButton(icon: "chevron.backward", label: r.back, filled: false) { withQhAnimation(QhMotion.snappy) { page = 0 } }
                        .offset(x: -12)
                    Text(r.surahStatusTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                }
            } else {
                Text(page == 0 ? surahName : r.surahStatusTitle).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
            }
            Group {
                if page == 0 { menu(r) } else if let target = confirming { removal(r, target) } else { statusPage(r) }
            }
            .id("\(page)-\(confirming != nil)")
            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .opacity))
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .onAppear { page = initialStatusPage ? 1 : 0 }
        .qhSheetStyle([.height(initialStatusPage ? 330 : 280), .medium])
    }

    private func menu(_ r: ReaderCopy) -> some View {
        QhCard(padding: 0) {
            QhValueRow(title: r.surahStatusTitle, value: label(r), icon: "circle.dashed", iconTint: qh.colors.gold) {
                withQhAnimation(QhMotion.snappy) { page = 1 }
            }
            QhRowDivider()
            QhNavRow(title: r.goToAyah, icon: "number") { dismiss(); DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: onGoTo) }
        }
        .padding(.top, QhSpacing.x2)
    }

    private func label(_ r: ReaderCopy) -> String {
        switch status {
        case .some(.learned): return r.learned
        case .some(.learning): return r.learning
        default: return r.notStarted
        }
    }

    private func statusPage(_ r: ReaderCopy) -> some View {
        VStack(alignment: .leading, spacing: QhSpacing.x4) {
            Text(r.surahStatusHint).qhText({ $0.body }, color: { $0.inkSecondary })
            HStack(spacing: QhSpacing.x3) {
                option("circle", r.notStarted, selected: status == nil, target: SurahTarget.notStarted)
                option("circle.lefthalf.filled", r.choiceLearning, selected: status == .some(.learning), target: SurahTarget.learning)
                option("checkmark.circle.fill", r.choiceKnown, selected: status == .some(.learned), target: SurahTarget.known)
            }
        }
        .padding(.top, QhSpacing.x1)
    }

    private func option(_ icon: String, _ label: String, selected: Bool, target: SurahTarget) -> some View {
        VStack(spacing: QhSpacing.x2) {
            Image(systemName: icon).font(.system(size: 28)).foregroundStyle(selected ? qh.colors.primary : qh.colors.inkSecondary)
                .scaleEffect(selected ? 1.12 : 1).qhAnimation(QhMotion.bouncy, value: selected)
            Text(label).qhText({ $0.label }, color: { selected ? $0.onPrimaryContainer : $0.inkSecondary }).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.horizontal, QhSpacing.x2).padding(.vertical, QhSpacing.x4)
        .background(selected ? qh.colors.primaryContainer : qh.colors.surfaceMuted, in: qh.shapes.lgShape)
        .overlay { qh.shapes.lgShape.stroke(selected ? qh.colors.primary : qh.colors.hairline, lineWidth: selected ? 1.5 : 1) }
        .pressable(haptic: .segment, shape: qh.shapes.lgShape) { choose(target) }
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private func choose(_ target: SurahTarget) {
        let shared = SurahStatusChange.shared
        if shared.isNoop(current: current, target: target) { dismiss(); return }
        if shared.needsConfirmation(current: current, coveredAyahs: Int32(learnedAyahs), target: target) {
            withQhAnimation(QhMotion.snappy) { confirming = target }
        } else {
            onChoice(target); dismiss()
        }
    }

    private func removal(_ r: ReaderCopy, _ target: SurahTarget) -> some View {
        let lost = Int(SurahStatusChange.shared.lostAyahs(current: current, coveredAyahs: Int32(learnedAyahs), target: target))
        return VStack(alignment: .leading, spacing: QhSpacing.x3) {
            Text(r.removeLearned(count: Int32(lost))).qhText({ $0.title2 })
            Text(r.removeLearnedHint).qhText({ $0.body }, color: { $0.inkSecondary })
            QhButton(r.removeAction, variant: .danger, size: .m, fullWidth: true) { onChoice(target); dismiss() }.padding(.top, QhSpacing.x1)
            QhButton(r.cancelAction, variant: .tonal, size: .m, fullWidth: true) { dismiss() }
        }
        .padding(.top, QhSpacing.x1)
    }
}

// MARK: - Go to

/// One row of the 114 surahs in the list of the "Go to" sheet.
struct GoToSurahRow: Identifiable {
    let number: Int
    let name: String
    let alternatives: [String]
    let arabic: String
    var id: Int { number }
}

/// "Go to" (U21): the surah field (tap: a searchable list of the 114) and the ayah field (digits only, limited to the ayah count, Arabic-Indic
/// digits become ASCII, "2:255" names the surah too) and the button. The rules are `GoToRules` of the shared layer.
struct GoToSheetContent: View {
    @Environment(\.qh) private var qh
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    let surahs: [GoToSurahRow]
    let currentSurah: Int
    let onGoTo: (Int, Int) -> Void

    @State private var surah: Int
    @State private var text = ""
    @State private var picking = false
    @State private var query = ""
    @FocusState private var focused: Bool

    init(surahs: [GoToSurahRow], currentSurah: Int, onGoTo: @escaping (Int, Int) -> Void) {
        self.surahs = surahs; self.currentSurah = currentSurah; self.onGoTo = onGoTo
        _surah = State(initialValue: currentSurah)
    }

    private var target: GoToTarget? { GoToRules.shared.resolve(surah: Int32(surah), text: text) }
    private var limit: Int { Int(GoToRules.shared.limit(surah: Int32(surah))) }

    var body: some View {
        let t = copy.goTo
        VStack(alignment: .leading, spacing: QhSpacing.x4) {
            if picking {
                pickerHeader(t)
                list(t)
            } else {
                Text(t.title).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                field(label: t.surah) {
                    HStack {
                        Text(row(surah)?.name ?? "\(surah)").qhText({ $0.title2 }).lineLimit(1)
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.up.chevron.down").font(.system(size: 13, weight: .semibold)).foregroundStyle(qh.colors.inkTertiary)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { QhHaptics.shared.perform(.tick); focused = false; withQhAnimation(QhMotion.snappy) { picking = true } }
                    .accessibilityAddTraits(.isButton)
                }
                field(label: "\(t.ayah) \u{00B7} \(t.range(n: Int32(limit)))") {
                    TextField("", text: $text, prompt: Text("1"))
                        .keyboardType(.numbersAndPunctuation)
                        .focused($focused)
                        .font(.custom("Manrope-SemiBold", size: 17))
                        .onChange(of: text) { _, raw in
                            let clean = GoToRules.shared.sanitize(surah: Int32(surah), raw: raw)
                            if clean != raw { text = clean }
                            if let target = GoToRules.shared.resolve(surah: Int32(surah), text: clean), clean.contains(":") { surah = Int(target.surah) }
                        }
                        .onSubmit(go)
                        .submitLabel(.go)
                }
                QhButton(t.go, size: .l, fullWidth: true, action: go).disabled(target == nil).padding(.top, QhSpacing.x2)
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .qhSheetStyle([.medium, .large])
        .onAppear { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { focused = true } }
    }

    private func go() {
        guard let target else { return }
        QhHaptics.shared.perform(.success)
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.32) { onGoTo(Int(target.surah), Int(target.ayah)) }
    }

    private func row(_ n: Int) -> GoToSurahRow? { surahs.first { $0.number == n } }

    private func field<Content: View>(label: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).qhText({ $0.overline }, color: { $0.inkTertiary })
            content().padding(.horizontal, QhSpacing.x4).frame(minHeight: 52).background(qh.colors.surfaceMuted, in: qh.shapes.mdShape)
        }
    }

    private func pickerHeader(_ t: GoToCopy) -> some View {
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            HStack(spacing: 0) {
                QhIconButton(icon: "chevron.backward", label: copy.reader.back, filled: false) { withQhAnimation(QhMotion.snappy) { picking = false } }.offset(x: -12)
                Text(t.chooseSurah).qhText({ $0.title1 })
            }
            QhSearchField(text: $query, placeholder: t.searchHint, clearLabel: t.clearSearch)
        }
    }

    private func list(_ t: GoToCopy) -> some View {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        let shown = q.isEmpty ? surahs : surahs.filter { s in
            "\(s.number)".hasPrefix(q) || s.name.lowercased().contains(q) || s.alternatives.contains { $0.lowercased().contains(q) } || s.arabic.contains(q)
        }
        return ScrollView {
            LazyVStack(spacing: 0) {
                if shown.isEmpty { Text(t.nothingFound).qhText({ $0.body }, color: { $0.inkSecondary }).padding(.top, QhSpacing.x6) }
                ForEach(shown) { s in
                    HStack(spacing: QhSpacing.x3) {
                        Text("\(s.number)").qhText({ $0.callout }, color: { $0.inkTertiary }).monospacedDigit().frame(width: 32, alignment: .leading)
                        Text(s.name).qhText({ $0.title2 }).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(s.arabic).qhArabic(qh.type.quranInline(ReadingPrefs.shared.face, size: 20), color: qh.colors.inkSecondary).lineLimit(1)
                        if s.number == surah { Image(systemName: "checkmark").foregroundStyle(qh.colors.primary).fontWeight(.bold) }
                    }
                    .frame(minHeight: 52).contentShape(Rectangle())
                    .pressable(haptic: .segment) {
                        surah = s.number
                        text = GoToRules.shared.onSurahChanged(surah: Int32(s.number), text: text)
                        query = ""
                        withQhAnimation(QhMotion.snappy) { picking = false }
                    }
                }
            }
        }
    }
}

// MARK: - Ayah sheet

struct AyahSheetContent: View {
    @Environment(\.qh) private var qh
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    @Environment(Router.self) private var router
    @Environment(AppEnvironment.self) private var env
    let model: ReaderModel
    let index: Int
    let onListen: () -> Void
    let onRepeat: () -> Void
    let onCheck: () -> Void

    @State private var note: String
    @State private var saved: String

    init(model: ReaderModel, index: Int, onListen: @escaping () -> Void, onRepeat: @escaping () -> Void, onCheck: @escaping () -> Void) {
        self.model = model; self.index = index; self.onListen = onListen; self.onRepeat = onRepeat; self.onCheck = onCheck
        let initial = model.ayahs.indices.contains(index) ? model.noteText(model.ayahs[index]) : ""
        _note = State(initialValue: initial)
        _saved = State(initialValue: initial)
    }

    var body: some View {
        if model.ayahs.indices.contains(index) {
            content(model.ayahs[index])
        }
    }

    private func content(_ ayah: AyahEntity) -> some View {
        let r = copy.reader
        let number = Int(ayah.numberInSurah)
        let status = model.statusOf(ayah)
        let learned = status != nil
        let dirty = note.trimmingCharacters(in: .whitespacesAndNewlines) != saved.trimmingCharacters(in: .whitespacesAndNewlines)
        let shareText = model.shareText(for: ayah, copy: copy)
        let similarCount = SimilarService.shared.count(surah: model.number, ayah: number)
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: QhSpacing.x3) {
                    AyahRosette(number: number, status: rdStatus(status), weak: model.isWeak(ayah), size: 48)
                    VStack(alignment: .leading, spacing: 0) {
                        Text("\(model.title(copy: copy)) \u{00B7} \(r.ayah) \(number)").qhText({ $0.title1 }).lineLimit(2)
                        if let line = stateLine(ayah, r) { Text(line.0).qhText({ $0.callout }, color: { line.1 ? $0.due : $0.inkSecondary }) }
                    }
                }
                if let display = model.displays.indices.contains(index) ? model.displays[index] : nil {
                    QuranText(ayah: display, face: ReadingPrefs.shared.face, readingSize: CGFloat(min(ReadingPrefs.shared.size, 26)))
                        .lineLimit(2).padding(.top, QhSpacing.x3)
                }
                Spacer().frame(height: QhSpacing.x3)
                row("play.fill", r.listenFromHere) { onListen(); dismiss() }
                row("repeat", r.repeatThisAyah) { onRepeat(); dismiss() }
                row("eye", r.checkFromHere) { onCheck(); dismiss() }
                row("book.pages", copy.tafsir.sheetRow) {
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { router.push(.tafsir(surah: model.number, ayah: number, tafsirId: nil)) }
                }
                if similarCount > 0 {
                    row(nil, copy.similar.sheetRow(count: Int32(similarCount)), similar: true) {
                        dismiss()
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { SimilarService.shared.open(surah: model.number, ayah: number) }
                    }
                }
                QhSectionHeader(r.ayahStatus).padding(.top, QhSpacing.x5).padding(.bottom, QhSpacing.x2)
                QhButton(learned ? r.removeFromLearned : r.knowThisAyah, variant: learned ? .tonal : .primary, size: .m, fullWidth: true) {
                    if learned { model.vm.forgetAyah(ayah: ayah) } else { model.vm.markAyahKnown(ayah: ayah) }
                    dismiss()
                }
                QhSectionHeader(r.note).padding(.top, QhSpacing.x5).padding(.bottom, QhSpacing.x2)
                ZStack(alignment: .topLeading) {
                    if note.isEmpty { Text(r.noteHint).qhText({ $0.body }, color: { $0.inkTertiary }).padding(.horizontal, 5).padding(.vertical, 8) }
                    TextEditor(text: $note).scrollContentBackground(.hidden).font(.custom("Manrope-Regular", size: 16)).frame(minHeight: 84, maxHeight: 160)
                }
                .padding(QhSpacing.x3).background(qh.colors.surfaceMuted, in: qh.shapes.mdShape)
                if dirty {
                    QhButton(r.saveNote, size: .m, fullWidth: true) { save(ayah); dismiss() }.padding(.top, QhSpacing.x3)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
                HStack(spacing: QhSpacing.x3) {
                    QhButton(r.copyAction, variant: .tonal, size: .m, icon: "doc.on.doc", fullWidth: true) {
                        UIPasteboard.general.string = shareText
                        env.toaster.show(r.copied, kind: .success)
                        dismiss()
                    }
                    ShareLink(item: shareText) {
                        HStack(spacing: QhSpacing.x2) {
                            Image(systemName: "square.and.arrow.up").font(.system(size: 15, weight: .semibold))
                            Text(r.share).qhText({ $0.callout.weight("SemiBold") }, color: { $0.onPrimaryContainer })
                        }
                        .foregroundStyle(qh.colors.onPrimaryContainer)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(qh.colors.primaryContainer, in: Capsule())
                    }
                }
                .padding(.top, QhSpacing.x5)
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5).padding(.bottom, QhSpacing.x6)
            .qhAnimation(QhMotion.gentle, value: dirty)
        }
        .scrollDismissesKeyboard(.interactively)
        .onDisappear { if dirty { save(ayah) } }
        .qhSheetStyle([.large])
    }

    private func save(_ ayah: AyahEntity) {
        guard note.trimmingCharacters(in: .whitespacesAndNewlines) != saved.trimmingCharacters(in: .whitespacesAndNewlines) else { return }
        model.vm.saveNote(ayah: ayah, note: note)
        saved = note
        env.toaster.show(note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? copy.reader.noteRemoved : copy.reader.noteSaved, kind: .success)
    }

    private func row(_ icon: String?, _ label: String, similar: Bool = false, action: @escaping () -> Void) -> some View {
        HStack(spacing: QhSpacing.x3) {
            if let icon { QhIconTile(icon: icon) } else if similar {
                SimilarRings(size: 24, color: qh.colors.primary).frame(width: 40, height: 40).background(qh.colors.primaryContainer, in: qh.shapes.smShape)
            }
            Text(label).qhText({ $0.title2 })
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .pressable(shape: qh.shapes.mdShape, action: action)
    }

    private func stateLine(_ ayah: AyahEntity, _ r: ReaderCopy) -> (String, Bool)? {
        var parts: [(String, Bool)] = []
        if model.isWeak(ayah) { parts.append((r.weakAyah, true)) }
        if let schedule = model.vm.scheduleOf(ayahInSurah: ayah.numberInSurah) {
            switch onEnum(of: schedule) {
            case .circle: parts.append((r.inOldCircle, false))
            case .due: parts.append((r.reviewDue, true))
            case .inDays(let d): parts.append((r.reviewIn(days: d.days), false))
            }
        }
        guard !parts.isEmpty else { return nil }
        return (parts.map(\.0).joined(separator: " \u{00B7} "), parts.contains { $0.1 })
    }
}

// MARK: - Appearance ("Aa")

struct AppearanceSheetContent: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @Environment(AppEnvironment.self) private var env
    let model: ReaderModel
    let previewIndex: Int

    @State private var tab = 0
    @State private var slider: Double = Double(ReadingPrefs.shared.size)
    @State private var tafsirLegend = false
    @State private var translationSheet = false

    var body: some View {
        let r = copy.reader
        let prefs = ReadingPrefs.shared
        let size = prefs.size
        let showTranslation = model.prefs.value.showTranslation
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x5) {
                Text(r.textSettings).qhText({ $0.title1 }).accessibilityAddTraits(.isHeader)
                QhSegmentedControl(options: [copy.goTo.tabText, copy.goTo.tabAdvanced], selection: $tab)
                if tab == 0 {
                    preview(size)
                    VStack(alignment: .leading, spacing: QhSpacing.x1) {
                        HStack {
                            QhSectionHeader(r.textSize)
                            AnimatedCounter(value: size, style: { $0.title2 }, color: qh.colors.primary)
                        }
                        QhSlider(value: Binding(get: { slider }, set: { v in
                            slider = v
                            ReadingPrefs.shared.setSize(Int(v.rounded()))
                        }), range: Double(ReadingPrefs.minSize)...Double(ReadingPrefs.maxSize), step: 1)
                    }
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        QhSectionHeader(r.fontStyle)
                        FontCarousel()
                    }
                    QhCard(padding: 0) {
                        QhSwitchRow(title: r.translation, isOn: Binding(get: { showTranslation }, set: { model.vm.setShowTranslation(show: $0) }))
                        QhRowDivider(startIndent: QhSpacing.card)
                        TranslationEditionRow()
                    }
                } else {
                    advanced(r, prefs)
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        QhSectionHeader(r.theme)
                        QhSegmentedControl(options: [copy.settingsCopy.themeLight, copy.settingsCopy.themeDark, copy.settingsCopy.themeSystem],
                                           selection: Binding(get: { themeIndex }, set: { setTheme($0) }))
                    }
                    marksLegend
                }
            }
            .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x5).padding(.bottom, QhSpacing.x8)
        }
        .qhSheetStyle([.large])
        .onChange(of: tab) { _, _ in QhHaptics.shared.perform(.segment) }
    }

    private var themeIndex: Int {
        switch env.theme.mode { case .light: return 0; case .dark: return 1; case .system: return 2 }
    }

    private func setTheme(_ i: Int) { env.setThemeMode(i == 0 ? .light : (i == 1 ? .dark : .system)) }

    private func preview(_ size: Int) -> some View {
        let display = model.displays.indices.contains(previewIndex) ? model.displays[previewIndex] : nil
        return Group {
            if let display {
                QuranText(ayah: display, face: ReadingPrefs.shared.face, readingSize: CGFloat(size), showTajweed: ReadingPrefs.shared.tajweed)
                    .lineLimit(2).allowsHitTesting(false)
            } else {
                Text(QuranDisplay.bismillahText).qhArabic(qh.type.quranBody(ReadingPrefs.shared.face, size: CGFloat(size)), color: qh.colors.ink)
            }
        }
        .padding(.horizontal, QhSpacing.card).padding(.vertical, QhSpacing.x4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(qh.colors.readerBg, in: qh.shapes.mdShape)
        .overlay { qh.shapes.mdShape.stroke(qh.colors.hairline, lineWidth: 1) }
        .animation(nil, value: size)
    }

    @ViewBuilder private func advanced(_ r: ReaderCopy, _ prefs: ReadingPrefs) -> some View {
        QhCard(padding: 0) {
            QhSwitchRow(title: r.highlightWords, isOn: Binding(get: { prefs.highlightWords }, set: { prefs.setHighlightWords($0) }))
            QhRowDivider(startIndent: QhSpacing.card)
            TajweedSettingsRows()
            QhRowDivider(startIndent: QhSpacing.card)
            QhSwitchRow(title: copy.tafsir.wordsSwitchTitle, isOn: Binding(get: { prefs.wordMeanings }, set: { prefs.setWordMeanings($0) }),
                        description: copy.tafsir.wordsSwitchHint)
            QhRowDivider(startIndent: QhSpacing.card)
            QhSwitchRow(title: copy.similar.switchTitle, isOn: Binding(get: { prefs.similarMarks }, set: { prefs.setSimilarMarks($0) }),
                        description: copy.similar.switchHint)
            QhRowDivider(startIndent: QhSpacing.card)
            QhSwitchRow(title: r.keepScreenOn, isOn: Binding(get: { prefs.keepScreenOn }, set: { prefs.setKeepScreenOn($0) }))
        }
    }

    private var marksLegend: some View {
        let t = copy.goTo
        return VStack(alignment: .leading, spacing: QhSpacing.x2) {
            QhSectionHeader(t.legendTitle)
            legendRow(t.legendHard) { Circle().fill(qh.colors.due).frame(width: 10, height: 10) }
            legendRow(t.legendSimilar) { SimilarRings(size: 22) }
            legendRow(t.legendNote) { Image(systemName: "note.text").font(.system(size: 15, weight: .semibold)).foregroundStyle(qh.colors.gold) }
        }
    }

    private func legendRow<Mark: View>(_ text: String, @ViewBuilder mark: () -> Mark) -> some View {
        HStack(spacing: QhSpacing.x2) {
            mark().frame(width: 36)
            Text(text).qhText({ $0.body }, color: { $0.inkSecondary })
        }
        .accessibilityElement(children: .combine)
    }
}

/// Seven cards, each with the Bismillah in its own font; the chosen one is raised. Snaps to a card.
struct FontCarousel: View {
    @Environment(\.qh) private var qh
    @Environment(Copy.self) private var copy
    @State private var scrolled: String?

    var body: some View {
        let current = ReadingPrefs.shared.style
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: QhSpacing.x3) {
                    ForEach(ReadingFontStyle.allCases, id: \.storageValue) { style in
                        card(style, selected: style == current).id(style.storageValue)
                    }
                }
                .scrollTargetLayout()
                .padding(.vertical, QhSpacing.x2)
            }
            .scrollTargetBehavior(.viewAligned)
            .scrollClipDisabled()
            .onAppear { proxy.scrollTo(current.storageValue, anchor: .center) }
        }
    }

    private func card(_ style: ReadingFontStyle, selected: Bool) -> some View {
        let face = ReadingPrefs.face(of: style)
        return VStack(spacing: QhSpacing.x1) {
            Text("\u{0628}\u{0650}\u{0633}\u{0652}\u{0645}\u{0650} \u{0671}\u{0644}\u{0644}\u{0651}\u{064E}\u{0647}\u{0650}")
                .qhArabic(qh.type.quranInline(face, size: 22), color: qh.colors.ink).lineLimit(1).frame(maxWidth: .infinity)
            Text(label(style)).qhText({ $0.label }, color: { selected ? $0.onPrimaryContainer : $0.inkSecondary }).lineLimit(1)
        }
        .padding(.horizontal, QhSpacing.x3).padding(.vertical, QhSpacing.x3)
        .frame(width: 132)
        .background(selected ? qh.colors.primaryContainer : qh.colors.readerBg, in: qh.shapes.lgShape)
        .overlay { qh.shapes.lgShape.stroke(selected ? qh.colors.primary : qh.colors.hairline, lineWidth: selected ? 1.5 : 1) }
        .scaleEffect(selected ? 1.05 : 1)
        .qhAnimation(QhMotion.snappy, value: selected)
        .pressable(haptic: .segment, shape: qh.shapes.lgShape) { if !selected { ReadingPrefs.shared.setStyle(style) } }
        .accessibilityLabel(label(style))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private func label(_ style: ReadingFontStyle) -> String {
        let strings = AppStringsKt.stringsFor(language: copy.language)
        return style.label(strings: strings)
    }
}
