import SwiftUI

/// Debug catalog of the whole design system: colours, type, every component, ornaments and motion. Temporary root of the app until
/// the shell (S3) lands; later reachable from a hidden long-press. Launch with `-qhCatalogPage <0...3>` to open a page directly.
struct DesignCatalogView: View {
    @Environment(\.qh) private var qh
    @Bindable var settings: ThemeSettings = .shared
    @State private var page = UserDefaults.standard.integer(forKey: "qhCatalogPage")
    @State private var toaster = Toaster()

    private let pages = ["Basics", "Controls", "Cards", "Ornaments"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: QhSpacing.section) {
                header
                switch page {
                case 0: BasicsPage()
                case 1: ControlsPage(toaster: toaster)
                case 2: CardsPage()
                default: OrnamentsPage()
                }
            }
            .padding(.horizontal, QhSpacing.screen)
            .padding(.top, QhSpacing.x2)
            .padding(.bottom, 40)
            .animation(nil, value: page)
        }
        .scrollIndicators(.hidden)
        .background(qh.colors.bg.ignoresSafeArea())
        .qhToastHost(toaster)
        .onAppear { QhHaptics.shared.prepare() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x4) {
            Text("Design system").qhText({ $0.display }).accessibilityAddTraits(.isHeader)
            QhSegmentedControl(options: pages, selection: $page)
            HStack(spacing: QhSpacing.x2) {
                ForEach(QhThemePalette.allCases) { palette in
                    QhChip(label: palette.rawValue.capitalized, selected: settings.palette == palette) {
                        QhThemeReveal.reveal { settings.palette = palette }
                    }
                }
                Spacer()
                QhIconButton(icon: qh.isDark ? "sun.max" : "moon", label: "Theme") {
                    QhThemeReveal.reveal { settings.mode = qh.isDark ? .light : .dark }
                }
            }
        }
    }
}

// MARK: - Pages

private struct CatalogGroup<Content: View>: View {
    let title: String
    @ViewBuilder var content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x3) {
            QhSectionHeader(title)
            content()
        }
    }
}

private struct BasicsPage: View {
    @Environment(\.qh) private var qh

    var body: some View {
        CatalogGroup(title: "Colours") {
            let c = qh.colors
            let swatches: [(String, Color)] = [
                ("bg", c.bg), ("surface", c.surface), ("muted", c.surfaceMuted), ("strong", c.surfaceStrong),
                ("primary", c.primary), ("container", c.primaryContainer), ("gold", c.gold), ("goldC", c.goldContainer),
                ("learned", c.learned), ("learnedC", c.learnedContainer), ("due", c.due), ("dueC", c.dueContainer),
                ("danger", c.danger), ("ink", c.ink), ("ink2", c.inkSecondary), ("ink3", c.inkTertiary),
            ]
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                ForEach(swatches, id: \.0) { name, color in
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 10, style: .continuous).fill(color).frame(height: 40)
                            .overlay { RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(c.hairline, lineWidth: 1) }
                        Text(name).qhText({ $0.label.sized(10) }, color: { $0.inkSecondary }).lineLimit(1)
                    }
                }
            }
        }
        CatalogGroup(title: "Typography") {
            VStack(alignment: .leading, spacing: QhSpacing.x2) {
                Text("Display 34").qhText({ $0.display })
                Text("Headline 26").qhText({ $0.headline })
                Text("Title 1 · 21").qhText({ $0.title1 })
                Text("Title 2 · 17").qhText({ $0.title2 })
                Text("Body 16: Қуръон ҳифз, Хотира, Ғ Ӣ Қ Ӯ Ҳ Ҷ").qhText({ $0.body })
                Text("Callout 14 · metadata").qhText({ $0.callout }, color: { $0.inkSecondary })
                Text("Label 13 · chips").qhText({ $0.label })
                Text("Overline 11".uppercased()).qhText({ $0.overline }, color: { $0.inkSecondary })
                Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ").qhArabic(qh.type.quranBody(.amiri))
                Text("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ").qhArabic(qh.type.quranBody(.scheherazade, size: 24))
            }
        }
        CatalogGroup(title: "Shapes and spacing") {
            HStack(spacing: QhSpacing.x3) {
                ForEach([qh.shapes.xs, qh.shapes.sm, qh.shapes.md, qh.shapes.lg, qh.shapes.xl], id: \.self) { r in
                    RoundedRectangle(cornerRadius: r, style: .continuous).fill(qh.colors.primaryContainer).frame(height: 52)
                        .overlay { Text("\(Int(r))").qhText({ $0.label }, color: { $0.onPrimaryContainer }) }
                }
            }
        }
    }
}

private struct ControlsPage: View {
    @Environment(\.qh) private var qh
    let toaster: Toaster
    @State private var seg = 1
    @State private var search = ""
    @State private var chip = 0
    @State private var on = true
    @State private var slider = 28.0
    @State private var wheel = 12
    @State private var loading = false
    @State private var sheet = false
    @State private var counter = 7

    var body: some View {
        CatalogGroup(title: "Buttons") {
            QhButton("Start the lesson", icon: "play.fill") {}
            HStack(spacing: QhSpacing.x2) {
                QhButton("Tonal", variant: .tonal, size: .m) {}
                QhButton("Text", variant: .text, size: .m) {}
                QhButton("Delete", variant: .danger, size: .m, icon: "trash") {}
            }
            HStack(spacing: QhSpacing.x2) {
                QhButton(loading ? "Loading" : "Load", size: .s, loading: loading) {
                    loading = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { loading = false }
                }
                QhButton("Disabled", size: .s) {}.disabled(true)
                QhIconButton(icon: "gearshape", label: "Settings") {}
                QhIconLabelButton(icon: "calendar", label: "Plan") {}
            }
        }
        CatalogGroup(title: "Segmented and chips") {
            QhSegmentedControl(options: ["System", "Light", "Dark"], selection: $seg)
            HStack(spacing: QhSpacing.x2) {
                ForEach(0..<3) { i in QhChip(label: ["All", "Learning", "Memorized"][i], selected: chip == i) { chip = i } }
            }
            HStack(spacing: QhSpacing.x2) {
                StatusPill(status: .notStarted, label: "New")
                StatusPill(status: .learning, label: "Learning")
            }
            HStack(spacing: QhSpacing.x2) {
                StatusPill(status: .learned, label: "Done", onTap: {})
                StatusPill(status: .due, label: "Review")
            }
        }
        CatalogGroup(title: "Search") { QhSearchField(text: $search, placeholder: "Search surah or ayah") }
        CatalogGroup(title: "Rows") {
            QhCard(padding: 0) {
                QhSwitchRow(title: "Reminders", isOn: $on, description: "Every day at 08:00", icon: "bell")
                QhRowDivider()
                QhNavRow(title: "Theme", description: "Palette and mode", icon: "paintpalette") {}
                QhRowDivider()
                QhValueRow(title: "Language", value: "English", icon: "globe") {}
            }
            VStack(alignment: .leading) {
                Text("Arabic size \(Int(slider))").qhText({ $0.callout }, color: { $0.inkSecondary })
                QhSlider(value: $slider, range: 22...40, step: 1)
            }
        }
        CatalogGroup(title: "Ayah wheel") {
            HStack { Spacer(); AyahWheel(label: "From ayah", minimum: 1, maximum: 286, value: $wheel).frame(width: 150); Spacer() }
        }
        CatalogGroup(title: "Skeleton") { SkeletonRow(); SkeletonRow() }
        CatalogGroup(title: "Toast, sheet, counter") {
            HStack(spacing: QhSpacing.x2) {
                QhButton("Success", variant: .tonal, size: .s) { toaster.show("Memorized", kind: .success) }
                QhButton("Error", variant: .tonal, size: .s) { toaster.show("Could not download the audio", kind: .error) }
                QhButton("Sheet", variant: .tonal, size: .s) { sheet = true }
            }
            HStack(spacing: QhSpacing.x4) {
                AnimatedCounter(value: counter, style: { $0.headline })
                QhButton("+1", variant: .tonal, size: .s) { counter += 1 }
                QhButton("x10", variant: .tonal, size: .s) { counter = counter * 10 }
            }
            QhInlineHint(id: "catalog", text: "A hint explains a gesture on the screen and goes away once used.", hints: HintFlags(store: InMemoryHintStore()))
        }
        .qhSheet(isPresented: $sheet, detents: [.medium]) {
            VStack(alignment: .leading, spacing: QhSpacing.x4) {
                QhSheetHeader(title: "Go to ayah", subtitle: "Al-Baqarah", closeLabel: "Close") { sheet = false }
                QhSearchField(text: $search, placeholder: "Ayah number")
                QhButton("Open", size: .m) { sheet = false }
            }
        }
    }
}

private struct CardsPage: View {
    @Environment(\.qh) private var qh
    @State private var celebrate = 0
    @State private var stagger = StaggerState(screen: "catalog")

    var body: some View {
        CatalogGroup(title: "Hero card") {
            QhCard(style: .hero, padding: QhSpacing.cardLarge) {
                HStack(alignment: .center, spacing: QhSpacing.x4) {
                    VStack(alignment: .leading, spacing: QhSpacing.x2) {
                        Text("Today's lesson").qhText({ $0.callout }, color: { $0.inkSecondary })
                        Text("Al-Mulk, ayah 5-8").qhText({ $0.title1 })
                        Text("12 min").qhText({ $0.callout }, color: { $0.inkSecondary })
                    }
                    Spacer()
                    QhProgressRing(progress: 0.64, size: 76, lineWidth: 7) {
                        Text("64%").qhText({ $0.title2 }).qhTabular()
                    }
                }
                QhButton("Continue", size: .m, icon: "play.fill") {}.padding(.top, QhSpacing.x4)
            }
        }
        CatalogGroup(title: "Surah rows") {
            QhCard(padding: 0) {
                row(1, "Al-Fatihah", .learned, 1, 0)
                QhRowDivider(startIndent: 72)
                row(2, "Al-Baqarah", .learning, 0.42, 1)
                QhRowDivider(startIndent: 72)
                row(3, "Al-Imran", .notStarted, 0, 2)
                QhRowDivider(startIndent: 72)
                row(67, "Al-Mulk", .due, 0.9, 3)
            }
        }
        CatalogGroup(title: "Progress") {
            QhLinearProgress(progress: 0.45)
            QhLinearProgress(progress: 0.8, height: 2)
        }
        CatalogGroup(title: "Celebration") {
            HStack {
                Spacer()
                ZStack {
                    SurahNumberBadge(number: 112, status: .learned, size: 64)
                    CelebrationBurst(trigger: celebrate)
                }
                .frame(width: 120, height: 120)
                Spacer()
            }
            QhButton("Celebrate", variant: .tonal, size: .m) { celebrate += 1 }
        }
    }

    private func row(_ n: Int, _ name: String, _ status: QhStatus, _ fraction: Double, _ i: Int) -> some View {
        HStack(spacing: QhSpacing.x3) {
            SurahNumberBadge(number: n, status: status, learnedFraction: fraction, size: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).qhText({ $0.title2 })
                Text("\([7, 286, 200, 30][i]) ayahs").qhText({ $0.callout }, color: { $0.inkSecondary })
            }
            Spacer()
            Text("سُورَة").qhArabic(qh.type.quranInline(.amiri))
        }
        .padding(.horizontal, QhSpacing.card)
        .padding(.vertical, QhSpacing.x3)
        .staggeredEnter(index: i, state: stagger)
    }
}

private struct OrnamentsPage: View {
    @Environment(\.qh) private var qh
    @State private var replay = 0

    var body: some View {
        CatalogGroup(title: "Calligraphy") {
            VStack(spacing: QhSpacing.x2) {
                CalligraphyText(Calligraphy.bismillah, size: 72).id(replay)
                CalligraphyText(Calligraphy.alhamdulillah, size: 40, revealDelay: 0.3).id(replay)
            }
            .frame(maxWidth: .infinity)
            QhButton("Replay", variant: .tonal, size: .s) { replay += 1 }
        }
        CatalogGroup(title: "Mihrab, medallion, checkmark") {
            HStack(alignment: .center, spacing: QhSpacing.x6) {
                MihrabCalligraphy(width: 96).id(replay)
                VStack(spacing: QhSpacing.x3) {
                    HStack { Medallion(size: 56); Medallion(size: 56, fill: qh.colors.goldContainer) }
                    AnimatedCheckmark(size: 40).id(replay)
                }
            }
            .frame(maxWidth: .infinity)
        }
        CatalogGroup(title: "Badges and rosettes") {
            HStack(spacing: QhSpacing.x4) {
                SurahNumberBadge(number: 1, status: .notStarted, size: 48)
                SurahNumberBadge(number: 36, status: .learning, learnedFraction: 0.6, size: 48)
                SurahNumberBadge(number: 114, status: .learned, size: 48)
            }
            HStack(spacing: QhSpacing.x4) {
                AyahRosette(number: 1, size: 40)
                AyahRosette(number: 12, status: .learning, size: 40)
                AyahRosette(number: 255, status: .learned, weak: true, size: 40)
            }
        }
        CatalogGroup(title: "Divider, cartouche") {
            OrnamentDivider()
            SurahCartouche(background: qh.colors.bg) {
                Text("سُورَةُ البَقَرَة").qhArabic(qh.type.quranHeader(.amiri))
            }
        }
        CatalogGroup(title: "Ogee lattice") {
            ArabesqueLattice(alpha: 0.35).frame(height: 140)
                .background(qh.colors.surface, in: qh.shapes.lgShape)
                .clipShape(qh.shapes.lgShape)
        }
        CatalogGroup(title: "Empty state") {
            QhEmptyState(title: "No notes yet", message: "Add a note to an ayah and it will appear here.", actionLabel: "Open the reader", onAction: {})
                .id(replay)
        }
    }
}

#Preview("Catalog light") { QhThemeRoot { DesignCatalogView() } }
#Preview("Catalog dark") { QhThemeRoot(settings: { let s = ThemeSettings(); s.mode = .dark; return s }()) { DesignCatalogView() } }
