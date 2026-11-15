import ComposeApp
import SwiftUI

// MARK: - Shared pieces

/// A scrolling column of one step with its title and subtitle.
private struct StepColumn<Content: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder var content: () -> Content
    @Environment(\.qh) private var qh

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.x3) {
                VStack(alignment: .leading, spacing: QhSpacing.x2) {
                    Text(title).qhText({ $0.headline }).accessibilityAddTraits(.isHeader)
                    Text(subtitle).qhText({ $0.body }, color: { $0.inkSecondary })
                }
                .padding(.bottom, QhSpacing.x2)
                content()
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x2).padding(.bottom, QhSpacing.x4)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollIndicators(.hidden)
    }
}

/// A card that is a single choice: tinted `primaryContainer` with a primary outline when selected.
private struct ChoiceCard<Content: View>: View {
    @Environment(\.qh) private var qh
    let selected: Bool
    let action: () -> Void
    @ViewBuilder var content: () -> Content

    var body: some View {
        let shape = qh.shapes.lgShape
        VStack(alignment: .leading, spacing: 4, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(QhSpacing.card)
            .background(selected ? qh.colors.primaryContainer : qh.colors.surface, in: shape)
            .overlay { shape.stroke(selected ? qh.colors.primary : qh.colors.hairline, lineWidth: selected ? 1.5 : 1) }
            .pressable(haptic: .segment, shape: shape, highlight: false, action: action)
            .qhAnimation(QhMotion.snappy, value: selected)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// A chip with a remove button.
private struct RemovableChip: View {
    @Environment(\.qh) private var qh
    let label: String
    let removeLabel: String
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Text(label).qhText({ $0.label }, color: { $0.onPrimaryContainer }).lineLimit(1)
            Image(systemName: "xmark").font(.system(size: 10, weight: .bold)).foregroundStyle(qh.colors.onPrimaryContainer)
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 36)
        .background(qh.colors.primaryContainer, in: qh.shapes.full)
        .padding(.vertical, 6)
        .pressable(haptic: .segment, shape: Capsule(), highlight: false, action: onRemove)
        .accessibilityLabel(removeLabel)
    }
}

// MARK: - Step 1: language

struct LanguageStep: View {
    let language: AppLanguage
    let onSelect: (AppLanguage) -> Void
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    private static let order: [AppLanguage] = [.ru, .en, .tg, .uz, .tr]

    var body: some View {
        let c = copy.onboarding
        ScrollView {
            VStack(spacing: 0) {
                ZStack {
                    ArabesqueLattice(alpha: 0.09)
                    LinearGradient(stops: [.init(color: qh.colors.bg, location: 0), .init(color: .clear, location: 0.35),
                                           .init(color: .clear, location: 0.65), .init(color: qh.colors.bg, location: 1)],
                                   startPoint: .top, endPoint: .bottom)
                    VStack(spacing: QhSpacing.x2) {
                        CalligraphyText(Calligraphy.bismillah, size: 84, revealDuration: 1.6)
                            .frame(height: 100)
                        Text("Quran Hifz").qhText({ $0.display }).accessibilityAddTraits(.isHeader)
                        Text(c.welcomeSubtitle).qhText({ $0.body }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                            .contentTransition(.opacity)
                    }
                    .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x4)
                }
                .clipped()
                .padding(.bottom, QhSpacing.x2)

                QhSectionHeader(c.chooseLanguage).padding(.horizontal, QhSpacing.screen)
                Text(c.chooseLanguageHint).qhText({ $0.callout }, color: { $0.inkSecondary }).multilineTextAlignment(.center)
                    .padding(.horizontal, QhSpacing.screen).padding(.vertical, QhSpacing.x3)
                VStack(spacing: QhSpacing.x2) {
                    ForEach(Self.order, id: \.self) { option in
                        LanguageCard(language: option, selected: option == language) { onSelect(option) }
                    }
                }
                .padding(.horizontal, QhSpacing.screen).padding(.bottom, QhSpacing.x4)
            }
        }
        .scrollIndicators(.hidden)
        .qhAnimation(QhMotion.medium.asEaseInOut, value: language)
    }
}

private extension Double {
    var asEaseInOut: Animation { .easeInOut(duration: self) }
}

struct LanguageCard: View {
    @Environment(\.qh) private var qh
    let language: AppLanguage
    let selected: Bool
    let action: () -> Void

    private var nativeName: String {
        switch language {
        case .ru: return "Русский"
        case .en: return "English"
        case .tg: return "Тоҷикӣ"
        case .uz: return "O‘zbek"
        case .tr: return "Türkçe"
        }
    }
    private var monogram: String {
        switch language {
        case .ru: return "Ру"
        case .en: return "En"
        case .tg: return "Тҷ"
        case .uz: return "Uz"
        case .tr: return "Tr"
        }
    }

    var body: some View {
        let shape = qh.shapes.mdShape
        HStack(spacing: QhSpacing.x3) {
            Text(monogram).qhText({ $0.label.weight("Bold") }, color: { selected ? $0.onPrimary : $0.inkSecondary })
                .frame(width: 40, height: 40)
                .background(selected ? qh.colors.primary : qh.colors.surfaceMuted, in: Circle())
            Text(nativeName).qhText({ $0.title2 }, color: { selected ? $0.onPrimaryContainer : $0.ink }).lineLimit(1)
            Spacer(minLength: 0)
            if selected {
                Image(systemName: "checkmark").font(.system(size: 16, weight: .bold)).foregroundStyle(qh.colors.primary)
                    .transition(.scale(scale: 0.2).combined(with: .opacity))
            }
        }
        .padding(.horizontal, QhSpacing.x4).padding(.vertical, QhSpacing.x2)
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
        .background(selected ? qh.colors.primaryContainer : qh.colors.surface, in: shape)
        .overlay { shape.stroke(selected ? qh.colors.primary : qh.colors.hairline, lineWidth: selected ? 1.5 : 1) }
        .pressable(haptic: .segment, shape: shape, highlight: false, action: action)
        .qhAnimation(QhMotion.bouncy, value: selected)
        .accessibilityLabel(nativeName)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Step 2: preset

struct PresetStep: View {
    let model: OnboardingModel
    let ui: OnboardingUi
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var adding = false

    var body: some View {
        let c = copy.onboarding
        let plan = copy.plan
        let selected = ui.selection.preset
        StepColumn(title: c.presetTitle, subtitle: c.presetSubtitle) {
            ForEach(PresetChoice.allCases, id: \.self) { preset in
                let info = ui.presets.first { $0.preset == preset }
                ChoiceCard(selected: selected == preset) {
                    model.vm.choosePreset(preset: preset)
                    if preset == .custom && ui.selection.customQueue.isEmpty { adding = true }
                } content: {
                    HStack {
                        Text(title(preset, c)).qhText({ $0.title1 })
                        Spacer(minLength: 0)
                        if selected == preset {
                            Image(systemName: "checkmark.circle.fill").font(.system(size: 22)).foregroundStyle(qh.colors.primary)
                        }
                    }
                    if let info {
                        Text(preset == .custom && info.surahCount == 0 ? c.customEmpty : plan.scopeInfo(surahs: info.surahCount, ayahs: info.ayahCount))
                            .qhText({ $0.callout }, color: { $0.inkSecondary })
                        if let lesson = info.firstLesson {
                            Text(c.presetStart(surahName: ui.surahName(number: lesson.surah) ?? "\(lesson.surah)", ayahs: lesson.ayahCount))
                                .qhText({ $0.title2 }, color: { $0.primary })
                        }
                    }
                }
                if preset == .custom && selected == .custom {
                    VStack(alignment: .leading, spacing: QhSpacing.x3) {
                        if !ui.selection.customQueue.isEmpty {
                            Text(c.customOrderHint).qhText({ $0.callout }, color: { $0.inkSecondary })
                            WrapChips {
                                ForEach(Array(ui.selection.customQueue.enumerated()), id: \.element) { index, number in
                                    let name = ui.surahName(number: number.int32Value) ?? "\(number)"
                                    RemovableChip(label: "\(index + 1). \(name)", removeLabel: c.remove(name: name)) {
                                        model.vm.removeCustom(surah: number.int32Value)
                                    }
                                }
                            }
                        }
                        QhButton(plan.addSurah, variant: .tonal, size: .m, icon: "plus", fullWidth: true) { adding = true }
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
            QhButton(c.presetReadOnly, variant: .text, size: .m, fullWidth: true) { model.vm.finish(exit: .reader) }
                .disabled(ui.finishing)
        }
        .qhAnimation(QhMotion.gentle, value: selected)
        .sheet(isPresented: $adding) {
            AddSurahSheet(catalog: ui.catalog, taken: Set(ui.selection.customQueue.map(\.int32Value))) { model.vm.addCustom(surah: $0) }
        }
    }

    private func title(_ preset: PresetChoice, _ c: OnboardingCopy) -> String {
        switch preset {
        case .juzAmma: return c.presetJuzAmma
        case .juzTabarak: return c.presetJuzTabarak
        case .fromStart: return c.presetFromStart
        default: return c.presetChoose
        }
    }
}

/// Wraps its chips onto as many lines as needed.
struct WrapChips<Content: View>: View {
    @ViewBuilder var content: () -> Content
    var body: some View { FlowLayoutRows(spacing: QhSpacing.x2) { content() } }
}

private struct FlowLayoutRows: Layout {
    var spacing: CGFloat
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(proposal.width ?? 320, subviews).size
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for (i, point) in arrange(bounds.width, subviews).origins.enumerated() {
            subviews[i].place(at: CGPoint(x: bounds.minX + point.x, y: bounds.minY + point.y), proposal: .unspecified)
        }
    }
    private func arrange(_ width: CGFloat, _ subviews: Subviews) -> (size: CGSize, origins: [CGPoint]) {
        var origins: [CGPoint] = []
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width { x = 0; y += rowHeight + spacing; rowHeight = 0 }
            origins.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            maxX = max(maxX, x - spacing)
        }
        return (CGSize(width: maxX, height: y + rowHeight), origins)
    }
}

// MARK: - Step 3: known

struct KnownStep: View {
    let model: OnboardingModel
    let ui: OnboardingUi
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @Environment(\.dynamicTypeSize) private var type
    @State private var adding = false

    /// Al-Fatihah, then the last juz from the shortest surah to the longest (114 down to 78).
    private static let grid: [Int32] = [1] + Array((78...114).reversed()).map(Int32.init)

    var body: some View {
        let c = copy.onboarding
        let known = Set(ui.selection.known.map(\.int32Value))
        let extras = ui.selection.known.map(\.int32Value).filter { !Self.grid.contains($0) }
        let columns = type.isAccessibilitySize ? 1 : 2
        StepColumn(title: c.knownTitle, subtitle: c.knownSubtitle) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: QhSpacing.x2), count: columns), spacing: QhSpacing.x2) {
                ForEach(Self.grid, id: \.self) { surah in
                    SurahTile(number: surah, name: ui.surahName(number: surah) ?? "\(surah)", selected: known.contains(surah)) {
                        model.vm.toggleKnown(surah: surah)
                    }
                }
            }
            QhButton(c.knownOther, variant: .tonal, size: .m, icon: "plus", fullWidth: true) { adding = true }
            if !extras.isEmpty {
                WrapChips {
                    ForEach(extras, id: \.self) { surah in
                        let name = ui.surahName(number: surah) ?? "\(surah)"
                        RemovableChip(label: "\(surah) · \(name)", removeLabel: c.remove(name: name)) { model.vm.toggleKnown(surah: surah) }
                    }
                }
            }
        }
        .sheet(isPresented: $adding) {
            AddSurahSheet(catalog: ui.catalog, taken: known) { model.vm.addKnown(surah: $0) }
        }
    }
}

private struct SurahTile: View {
    @Environment(\.qh) private var qh
    let number: Int32
    let name: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        let shape = qh.shapes.mdShape
        HStack(spacing: QhSpacing.x2) {
            SurahNumberBadge(number: Int(number), status: selected ? .learned : .notStarted, size: 32)
            Text(name).qhText({ $0.callout.weight("SemiBold") }).lineLimit(2).multilineTextAlignment(.leading)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, QhSpacing.x3).padding(.vertical, QhSpacing.x2)
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .background(selected ? qh.colors.primaryContainer : qh.colors.surface, in: shape)
        .overlay { shape.stroke(selected ? qh.colors.primary : qh.colors.hairline, lineWidth: selected ? 1.5 : 1) }
        .pressable(haptic: .segment, shape: shape, highlight: false, action: action)
        .qhAnimation(QhMotion.snappy, value: selected)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(number), \(name)")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Step 4: ready

struct ReadyStep: View {
    let model: OnboardingModel
    let ui: OnboardingUi
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh

    var body: some View {
        let c = copy.onboarding
        let plan = copy.plan
        let perDay = Int(ui.selection.perDay)
        let sel = ui.selection
        StepColumn(title: c.readyTitle, subtitle: c.readySubtitle) {
            QhCard(padding: QhSpacing.cardLarge) {
                VStack(alignment: .leading, spacing: QhSpacing.x4) {
                    if let lesson = ui.firstLesson {
                        let name = ui.surahName(number: lesson.surah) ?? "\(lesson.surah)"
                        summary("book", qh.colors.primary,
                                c.readyLesson(range: HomeCopyKt.passageRangeLabel(surahName: name, start: lesson.startAyah, end: lesson.endAyah), minutes: nil),
                                strong: true)
                    }
                    if !sel.known.isEmpty {
                        summary("checkmark.circle", qh.colors.learned, c.readyKnown(count: Int32(sel.known.count)))
                    }
                    if let finish = ui.forecast.finishEpochDay {
                        summary("calendar", qh.colors.gold, c.readyForecast(name: presetName(sel.preset, plan), date: plan.dateLabel(epochDay: Int32(truncating: finish), today: ui.today)))
                    }
                    if let minutes = ui.lessonMinutes {
                        summary("clock", qh.colors.gold, c.readyMinutes(minutes: Int32(truncating: minutes)))
                    }
                }
            }
            QhCard(padding: QhSpacing.x2) {
                HStack(spacing: QhSpacing.x2) {
                    Text(c.readyPerDay).qhText({ $0.title2 }).padding(.leading, QhSpacing.x3)
                    Spacer(minLength: 0)
                    QhIconButton(icon: "minus", label: c.readyFewer) { model.vm.setPerDay(value: Int32(perDay - 1)) }
                        .disabled(perDay <= 1)
                    AnimatedCounter(value: perDay).frame(minWidth: 32)
                    QhIconButton(icon: "plus", label: c.readyMore) { model.vm.setPerDay(value: Int32(perDay + 1)) }
                        .disabled(perDay >= 10)
                }
            }
        }
    }

    private func presetName(_ preset: PresetChoice?, _ plan: PlanCopy) -> String {
        switch preset {
        case .juzAmma: return plan.scopeJuz30
        case .juzTabarak: return plan.scopeJuz29
        case .fromStart: return plan.scopeFromStart
        default: return plan.scopeCustom
        }
    }

    private func summary(_ icon: String, _ tint: Color, _ text: String, strong: Bool = false) -> some View {
        HStack(alignment: .top, spacing: QhSpacing.x3) {
            Image(systemName: icon).font(.system(size: 18, weight: .semibold)).foregroundStyle(tint).frame(width: 24)
            Text(text).qhText({ strong ? $0.title2 : $0.body }, color: { strong ? $0.ink : $0.inkSecondary })
            Spacer(minLength: 0)
        }
    }
}

// MARK: - Add surah

/// Search the 114 surahs and add some; the added ones leave the list. `taken` are the surahs already chosen.
struct AddSurahSheet: View {
    let catalog: [SurahRowUi]
    let taken: Set<Int32>
    let onAdd: (Int32) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(Copy.self) private var copy
    @Environment(\.qh) private var qh
    @State private var query = ""

    private var rows: [SurahRowUi] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        return catalog.filter { row in
            !taken.contains(row.number) && (q.isEmpty || row.name.lowercased().contains(q) || row.latinName.lowercased().contains(q) || "\(row.number)" == q)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            QhSheetHeader(title: copy.plan.addSurah, closeLabel: nil, onClose: { dismiss() })
            QhSearchField(text: $query, placeholder: copy.home.searchPlaceholder, clearLabel: copy.home.clearSearch)
                .padding(.bottom, QhSpacing.x2)
            List(rows, id: \.number) { row in
                HStack(spacing: QhSpacing.x3) {
                    SurahNumberBadge(number: Int(row.number), status: .notStarted, size: 36)
                    Text(row.name).qhText({ $0.title2 }).lineLimit(1)
                    Spacer(minLength: 0)
                    Text(row.arabicName).qhArabic(qh.type.quranInline(.amiri), color: qh.colors.primary)
                }
                .frame(minHeight: 48)
                .contentShape(Rectangle())
                .onTapGesture { QhHaptics.shared.perform(.segment); onAdd(row.number) }
                .listRowBackground(Color.clear)
                .listRowSeparatorTint(qh.colors.hairline)
                .accessibilityAddTraits(.isButton)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
        .padding(.horizontal, QhSpacing.screen).padding(.top, QhSpacing.x4)
        .background(qh.colors.surface.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
    }
}
