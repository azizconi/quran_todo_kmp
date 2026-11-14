import SwiftUI
import Observation

/// What a toast says about: it picks the icon and its colour.
enum ToastKind { case success, error, info }

struct ToastData: Identifiable, Equatable {
    let id: Int
    let message: String
    let kind: ToastKind
    let duration: Double
}

/// Shows one toast at a time; a new message replaces the current one. One instance for the app, provided with `.qhToastHost(toaster)`
/// and read with `@Environment(\.toaster)`.
@Observable
final class Toaster {
    private(set) var current: ToastData?
    private var nextId = 0
    static let defaultDuration: Double = 3

    func show(_ message: String, kind: ToastKind = .info, duration: Double = Toaster.defaultDuration) {
        nextId += 1
        current = ToastData(id: nextId, message: message, kind: kind, duration: duration)
        switch kind {
        case .success: QhHaptics.shared.perform(.success)
        case .error: QhHaptics.shared.perform(.reject)
        case .info: break
        }
    }

    func dismiss() { current = nil }
    fileprivate func dismiss(id: Int) { if current?.id == id { current = nil } }
}

private struct ToasterKey: EnvironmentKey { static let defaultValue = Toaster() }

extension EnvironmentValues {
    var toaster: Toaster {
        get { self[ToasterKey.self] }
        set { self[ToasterKey.self] = newValue }
    }
}

extension View {
    /// Renders the toaster's toast as a floating pill under the status bar, above the content. Auto-hides after its duration;
    /// swipe up to dismiss.
    func qhToastHost(_ toaster: Toaster) -> some View {
        environment(\.toaster, toaster)
            .overlay(alignment: .top) { ToastHost(toaster: toaster) }
    }
}

private struct ToastHost: View {
    @Environment(\.qhReduceMotion) private var reduced
    let toaster: Toaster

    var body: some View {
        ZStack {
            if let toast = toaster.current {
                QhToast(toast: toast) { toaster.dismiss(id: toast.id) }
                    .padding(.top, QhSpacing.x2)
                    .padding(.horizontal, QhSpacing.x4)
                    .transition(reduced ? .opacity : .move(edge: .top).combined(with: .opacity))
                    .task(id: toast.id) {
                        try? await Task.sleep(for: .seconds(toast.duration))
                        guard !Task.isCancelled else { return }
                        withQhAnimation(QhMotion.snappy) { toaster.dismiss(id: toast.id) }
                    }
            }
        }
        .frame(maxWidth: .infinity)
        .animation(reduced ? nil : QhMotion.snappy, value: toaster.current?.id)
    }
}

/// A toast bubble: an icon, one or two lines of text. Normally shown by `qhToastHost`, not directly.
struct QhToast: View {
    @Environment(\.qh) private var qh
    let toast: ToastData
    let onDismiss: () -> Void
    @State private var drag: CGFloat = 0

    private var visuals: (icon: String, tint: Color) {
        switch toast.kind {
        case .success: return ("checkmark.circle.fill", qh.colors.learned)
        case .error: return ("exclamationmark.circle.fill", qh.colors.danger)
        case .info: return ("info.circle.fill", qh.colors.primary)
        }
    }

    var body: some View {
        HStack(spacing: QhSpacing.x3) {
            Image(systemName: visuals.icon).font(.system(size: 20, weight: .semibold)).foregroundStyle(visuals.tint)
            Text(toast.message).qhText({ $0.callout }).lineLimit(2)
        }
        .padding(.leading, QhSpacing.x3).padding(.trailing, QhSpacing.x5).padding(.vertical, QhSpacing.x3)
        .background(qh.colors.surface, in: qh.shapes.full)
        .overlay { qh.shapes.full.strokeBorder(qh.colors.hairline, lineWidth: 1) }
        .shadow(color: .black.opacity(qh.isDark ? 0.4 : 0.10), radius: 12, y: 4)
        .frame(maxWidth: 480)
        .offset(y: drag)
        .gesture(
            DragGesture()
                .onChanged { drag = min($0.translation.height, 0) }
                .onEnded { value in
                    if value.translation.height < -40 || value.velocity.height < -900 {
                        onDismiss()
                    } else {
                        withQhAnimation(QhMotion.snappy) { drag = 0 }
                    }
                }
        )
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
    }
}

#Preview("Toast light") { ToastPreview().qhPreviewTheme() }
#Preview("Toast dark") { ToastPreview(dark: true).qhPreviewTheme(dark: true) }

private struct ToastPreview: View {
    var dark = false
    @State private var toaster = Toaster()
    var body: some View {
        VStack(spacing: 12) {
            QhToast(toast: ToastData(id: 1, message: "Saved", kind: .success, duration: 3)) {}
            QhToast(toast: ToastData(id: 2, message: "Could not download the audio", kind: .error, duration: 3)) {}
            QhToast(toast: ToastData(id: 3, message: "Reminder set for 08:00", kind: .info, duration: 3)) {}
            QhButton("Show toast", size: .m) { toaster.show("Memorized", kind: .success) }
        }
        .padding()
        .qhToastHost(toaster)
    }
}
