import SwiftUI

/// Defaults of `pressable`.
enum PressableDefaults {
    /// Scale while pressed.
    static let scale: CGFloat = 0.97
    /// Opacity of the `surfaceStrong` veil drawn on a pressed element instead of a highlight.
    static let pressedVeilAlpha: Double = 0.4
}

/// The visual of a pressed element: squeezes with `QhMotion.press`, springs back with `snappy`, shows a `surfaceStrong` veil
/// clipped to `shape`. Collapses to nothing under Reduce Motion (the veil stays).
private struct PressedLook<Content: View>: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    let content: Content
    let pressed: Bool
    let scale: CGFloat
    let shape: AnyShape
    let highlight: Bool

    var body: some View {
        content
            .overlay {
                if highlight {
                    shape.fill(qh.colors.surfaceStrong.opacity(PressableDefaults.pressedVeilAlpha))
                        .opacity(pressed ? 1 : 0)
                        .allowsHitTesting(false)
                }
            }
            .scaleEffect(pressed && !reduced ? scale : 1)
            .animation(reduced ? nil : (pressed ? QhMotion.press : QhMotion.snappy), value: pressed)
    }
}

/// `ButtonStyle` behind `pressable`; use it directly for `Button`s of your own.
struct QhPressStyle: ButtonStyle {
    var scale: CGFloat = PressableDefaults.scale
    var shape: AnyShape = AnyShape(Rectangle())
    var highlight = true

    func makeBody(configuration: Configuration) -> some View {
        PressedLook(content: configuration.label, pressed: configuration.isPressed, scale: scale, shape: shape, highlight: highlight)
    }
}

private struct LongPressableLook<Content: View>: View {
    let content: Content
    let scale: CGFloat
    let shape: AnyShape
    let highlight: Bool
    let haptic: QhHapticKind?
    let onTap: () -> Void
    let onLongPress: () -> Void
    @State private var pressed = false

    var body: some View {
        PressedLook(content: content, pressed: pressed, scale: scale, shape: shape, highlight: highlight)
            .contentShape(shape)
            .onTapGesture {
                if let haptic { QhHaptics.shared.perform(haptic) }
                onTap()
            }
            .onLongPressGesture(minimumDuration: 0.45, maximumDistance: 12, perform: {
                QhHaptics.shared.perform(.longPress)
                onLongPress()
            }, onPressingChanged: { pressed = $0 })
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { onTap() }
    }
}

extension View {
    /// The one way to make something tappable in the new UI (ANDROID_REDESIGN.md, 3.3):
    /// squeezes to `scale` on touch down, shows a veil clipped to `shape`, plays `haptic` on tap
    /// (`.longPress` on long press, when `onLongPress` is given). Put it before `clipShape`/`background` so the whole visual scales.
    func pressable<S: Shape>(
        haptic: QhHapticKind? = .tick,
        scale: CGFloat = PressableDefaults.scale,
        shape: S = Rectangle(),
        highlight: Bool = true,
        onLongPress: (() -> Void)? = nil,
        action: @escaping () -> Void
    ) -> some View {
        Group {
            if let onLongPress {
                LongPressableLook(content: self, scale: scale, shape: AnyShape(shape), highlight: highlight,
                                  haptic: haptic, onTap: action, onLongPress: onLongPress)
            } else {
                Button {
                    if let haptic { QhHaptics.shared.perform(haptic) }
                    action()
                } label: {
                    self.contentShape(shape)
                }
                .buttonStyle(QhPressStyle(scale: scale, shape: AnyShape(shape), highlight: highlight))
            }
        }
    }
}
