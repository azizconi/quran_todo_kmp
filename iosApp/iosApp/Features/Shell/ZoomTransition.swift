import SwiftUI

/// iOS 18 zoom transitions: the lesson card zooms into the lesson and a surah row into the reader. iOS 17 keeps the plain push.
/// The namespace is shared through the environment: `MainTabs` provides it, a source view marks itself with `qhZoomSource`, the
/// destination gets `qhZoomDestination` where the stacks build it.
private struct ZoomNamespaceKey: EnvironmentKey { static let defaultValue: Namespace.ID? = nil }

extension EnvironmentValues {
    var qhZoomNamespace: Namespace.ID? {
        get { self[ZoomNamespaceKey.self] }
        set { self[ZoomNamespaceKey.self] = newValue }
    }
}

enum QhZoomID {
    static let lesson = "zoom.lesson"
    /// The compact lesson card on top of the Quran tab (a source of its own: both tabs are alive at once).
    static let lessonStrip = "zoom.lesson.strip"
    static func surah(_ number: Int) -> String { "zoom.surah.\(number)" }
}

extension Route {
    /// The source that pairs with this destination, nil for routes that keep the push.
    func zoomSourceID(from tab: AppTab) -> String? {
        switch self {
        case .lesson: return tab == .quran ? QhZoomID.lessonStrip : QhZoomID.lesson
        case let .reader(surah, _, _, _, _, _): return QhZoomID.surah(surah)
        default: return nil
        }
    }
}

private struct ZoomSource: ViewModifier {
    @Environment(\.qhZoomNamespace) private var namespace
    let id: String

    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 18, *), let namespace {
            content.matchedTransitionSource(id: id, in: namespace)
        } else {
            content
        }
    }
}

private struct ZoomDestination: ViewModifier {
    @Environment(\.qhZoomNamespace) private var namespace
    let route: Route
    let tab: AppTab

    @ViewBuilder func body(content: Content) -> some View {
        if #available(iOS 18, *), let namespace, let id = route.zoomSourceID(from: tab) {
            content.navigationTransition(.zoom(sourceID: id, in: namespace))
        } else {
            content
        }
    }
}

extension View {
    func qhZoomSource(_ id: String) -> some View { modifier(ZoomSource(id: id)) }
    func qhZoomDestination(_ route: Route, from tab: AppTab) -> some View { modifier(ZoomDestination(route: route, tab: tab)) }
}
