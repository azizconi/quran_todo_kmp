import SwiftUI
import UIKit

/// The circular theme reveal (ANDROID_REDESIGN.md, 6.6).
///
/// Right before the theme changes, `QhThemeReveal.reveal` snapshots the whole window, lets `change` apply the new theme underneath and
/// draws the old frame on top with an expanding circle cut out of it, centred on the touch point: 520 ms with the emphasized curve.
/// Under Reduce Motion the old frame just fades out in 150 ms.
@MainActor
enum QhThemeReveal {
    /// Where a finger last went down, in window coordinates; updated by `.qhTracksTouch()`.
    static var lastTouch: CGPoint?
    private static var busy = false

    static func reveal(from origin: CGPoint? = nil, change: @escaping () -> Void) {
        guard !busy,
              let window = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).flatMap(\.windows).first(where: \.isKeyWindow),
              let snapshot = window.snapshotView(afterScreenUpdates: false)
        else {
            change()
            return
        }
        busy = true
        let bounds = window.bounds
        let center = origin ?? lastTouch ?? CGPoint(x: bounds.midX, y: bounds.midY)
        snapshot.frame = bounds
        snapshot.isUserInteractionEnabled = false
        window.addSubview(snapshot)
        change()

        func finish() {
            snapshot.removeFromSuperview()
            busy = false
        }

        if QhMotion.reduced {
            UIView.animate(withDuration: 0.15, delay: 0.02, options: [.curveLinear], animations: { snapshot.alpha = 0 }, completion: { _ in finish() })
            return
        }

        // Mask = the whole frame minus a circle (even-odd); the circle grows to cover the farthest corner.
        let corners = [CGPoint(x: bounds.minX, y: bounds.minY), CGPoint(x: bounds.maxX, y: bounds.minY),
                       CGPoint(x: bounds.minX, y: bounds.maxY), CGPoint(x: bounds.maxX, y: bounds.maxY)]
        let maxRadius = corners.map { hypot($0.x - center.x, $0.y - center.y) }.max() ?? 1000
        func path(radius: CGFloat) -> CGPath {
            let p = UIBezierPath(rect: bounds.insetBy(dx: -4, dy: -4))
            p.append(UIBezierPath(arcCenter: center, radius: max(radius, 0.01), startAngle: 0, endAngle: .pi * 2, clockwise: true))
            return p.cgPath
        }
        let mask = CAShapeLayer()
        mask.fillRule = .evenOdd
        mask.path = path(radius: 0)
        snapshot.layer.mask = mask

        CATransaction.begin()
        CATransaction.setCompletionBlock { finish() }
        let animation = CABasicAnimation(keyPath: "path")
        animation.fromValue = path(radius: 0)
        animation.toValue = path(radius: maxRadius)
        animation.duration = QhMotion.hero
        animation.timingFunction = QhMotion.emphasizedTiming
        animation.fillMode = .forwards
        animation.isRemovedOnCompletion = false
        mask.add(animation, forKey: "reveal")
        CATransaction.commit()
    }
}

/// Records the last touch point without taking part in gestures: a SwiftUI `DragGesture(minimumDistance: 0)` at the root
/// swallowed the system back button and the swipe-back of every NavigationStack. A UIKit recognizer on the window only
/// watches `touchesBegan` and then fails, so every other gesture and control gets the touch as usual.
private struct TouchTracker: ViewModifier {
    func body(content: Content) -> some View {
        content.background(WindowTouchHook().allowsHitTesting(false))
    }
}

private struct WindowTouchHook: UIViewRepresentable {
    func makeUIView(context: Context) -> HookView { HookView() }
    func updateUIView(_ uiView: HookView, context: Context) {}

    final class HookView: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard let window, !(window.gestureRecognizers ?? []).contains(where: { $0 is TouchSpy }) else { return }
            window.addGestureRecognizer(TouchSpy())
        }
    }

    final class TouchSpy: UIGestureRecognizer, UIGestureRecognizerDelegate {
        init() {
            super.init(target: nil, action: nil)
            cancelsTouchesInView = false
            delaysTouchesBegan = false
            delaysTouchesEnded = false
            delegate = self
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            if let touch = touches.first, let window = view as? UIWindow {
                QhThemeReveal.lastTouch = touch.location(in: window)
            }
            state = .failed
        }

        func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    }
}

extension View {
    /// Records where a finger last touched, so a theme switch can reveal from there. Apply once at the root.
    func qhTracksTouch() -> some View { modifier(TouchTracker()) }
}
