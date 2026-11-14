import SwiftUI

/// 4pt grid and layout constants (port of `QhSpacing.kt`).
enum QhSpacing {
    static let x1: CGFloat = 4
    static let x2: CGFloat = 8
    static let x3: CGFloat = 12
    static let x4: CGFloat = 16
    static let x5: CGFloat = 20
    static let x6: CGFloat = 24
    static let x7: CGFloat = 28
    static let x8: CGFloat = 32
    static let x10: CGFloat = 40
    static let x12: CGFloat = 48
    /// Horizontal inset of every screen.
    static let screen: CGFloat = 20
    /// Vertical gap between screen sections.
    static let section: CGFloat = 28
    /// Padding inside cards.
    static let card: CGFloat = 16
    static let cardLarge: CGFloat = 20
    /// Minimum touch target. Apple's own minimum is 44; we keep Android's 48 for one rhythm.
    static let minTouch: CGFloat = 48
    static let iconBar: CGFloat = 22
    static let iconInline: CGFloat = 20
}
