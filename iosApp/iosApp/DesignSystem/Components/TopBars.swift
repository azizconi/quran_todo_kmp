import SwiftUI

// Top bars. Screens inside a `NavigationStack` use the system large title (with `.qhNavigationStyle()` for our colours) and
// `QhIconLabelButton` toolbar items. `QhLargeTitleScroll` is the self-contained variant of the Android `QhLargeTopBar`: the title
// in `display` collapses into a compact `title2` as the content scrolls, with a hairline and a `bg` backdrop appearing on the way.

private struct ScrollOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// Collapse distance of the large title, in points.
private let collapseDistance: CGFloat = 48

/// Opacity of the overline at a collapse fraction: gone within the first 40%.
func qhOverlineAlpha(_ fraction: CGFloat) -> CGFloat { min(max(1 - fraction / 0.4, 0), 1) }

/// Opacity of the compact title: with an overline it waits until the overline is gone, so the two never overlap.
func qhCompactTitleAlpha(_ fraction: CGFloat, hasOverline: Bool) -> CGFloat {
    hasOverline ? min(max((fraction - 0.4) / 0.6, 0), 1) : min(max(fraction, 0), 1)
}

/// The bar itself: draw it over the content (a `ZStack`/`overlay`) and give the content top padding for the expanded height.
struct QhLargeTopBar<Leading: View, Actions: View>: View {
    @Environment(\.qh) private var qh
    let title: String
    /// 0 = fully expanded, 1 = collapsed.
    let collapsed: CGFloat
    var overline: String?
    @ViewBuilder var leading: () -> Leading
    @ViewBuilder var actions: () -> Actions

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                leading()
                ZStack(alignment: .leading) {
                    if let overline {
                        Text(overline).qhText({ $0.callout }, color: { $0.inkSecondary }).lineLimit(1)
                            .opacity(qhOverlineAlpha(collapsed))
                    }
                    Text(title).qhText({ $0.title2 }).lineLimit(1)
                        .opacity(qhCompactTitleAlpha(collapsed, hasOverline: overline != nil))
                        .accessibilityHidden(true)
                }
                .padding(.leading, QhSpacing.x2)
                .frame(maxWidth: .infinity, alignment: .leading)
                actions()
            }
            .padding(.horizontal, QhSpacing.x1)
            .frame(minHeight: 56)
            Text(title)
                .qhText({ $0.display })
                .lineLimit(2)
                .minimumScaleFactor(0.7)
                .padding(.horizontal, QhSpacing.screen)
                .padding(.bottom, QhSpacing.x2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(max(0, 1 - collapsed * 1.4))
                .frame(height: nil)
                .accessibilityAddTraits(.isHeader)
        }
        .background(alignment: .bottom) {
            ZStack(alignment: .bottom) {
                qh.colors.bg.opacity(0.96 * collapsed)
                qh.colors.hairline.opacity(collapsed).frame(height: 1)
            }
            .ignoresSafeArea(edges: .top)
        }
    }
}

/// A scroll screen with the large collapsing title bar over it.
struct QhLargeTitleScroll<Content: View, Leading: View, Actions: View>: View {
    @Environment(\.qh) private var qh
    let title: String
    var overline: String?
    @ViewBuilder var leading: () -> Leading
    @ViewBuilder var actions: () -> Actions
    @ViewBuilder var content: () -> Content
    @State private var offset: CGFloat = 0

    init(title: String, overline: String? = nil,
         @ViewBuilder leading: @escaping () -> Leading = { EmptyView() },
         @ViewBuilder actions: @escaping () -> Actions = { EmptyView() },
         @ViewBuilder content: @escaping () -> Content) {
        self.title = title
        self.overline = overline
        self.leading = leading
        self.actions = actions
        self.content = content
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Color.clear.frame(height: 56 + 52)
                content()
            }
            .background(
                GeometryReader { geo in
                    Color.clear.preference(key: ScrollOffsetKey.self, value: -geo.frame(in: .named("qhLargeTitleScroll")).minY)
                }
            )
        }
        .coordinateSpace(name: "qhLargeTitleScroll")
        .onPreferenceChange(ScrollOffsetKey.self) { offset = $0 }
        .overlay(alignment: .top) {
            QhLargeTopBar(title: title, collapsed: min(max(offset / collapseDistance, 0), 1), overline: overline, leading: leading, actions: actions)
        }
        .background(qh.colors.bg)
    }
}

extension View {
    /// Navigation bar colours of the new UI for screens that use the system bar.
    func qhNavigationStyle() -> some View { modifier(QhNavigationStyle()) }
}

private struct QhNavigationStyle: ViewModifier {
    @Environment(\.qh) private var qh
    func body(content: Content) -> some View {
        content
            .toolbarBackground(qh.colors.bg, for: .navigationBar)
            .toolbarColorScheme(qh.isDark ? .dark : .light, for: .navigationBar)
            .scrollContentBackground(.hidden)
            .background(qh.colors.bg)
    }
}

#Preview("TopBar light") { TopBarPreview().qhPreviewTheme() }
#Preview("TopBar dark") { TopBarPreview().qhPreviewTheme(dark: true) }

private struct TopBarPreview: View {
    var body: some View {
        QhLargeTitleScroll(title: "Progress", overline: "Good morning") {
            QhIconButton(icon: "chevron.backward", label: "Back", filled: false) {}
        } actions: {
            QhIconLabelButton(icon: "calendar", label: "Plan") {}
        } content: {
            VStack(spacing: 12) {
                ForEach(0..<14) { i in QhCard { Text("Row \(i)").qhText({ $0.title2 }) } }
            }
            .padding(.horizontal, QhSpacing.screen)
        }
    }
}
