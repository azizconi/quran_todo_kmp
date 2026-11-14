import SwiftUI

/// Corner radii (port of `QhShapes.kt`): buttons are pills, cards are `lg`, the player dock and sheets are `xl` at the top.
/// All shapes use the continuous (squircle) corner style, the native iOS look.
struct QhShapes: Equatable {
    var xs: CGFloat = 8
    var sm: CGFloat = 12
    var md: CGFloat = 16
    var lg: CGFloat = 22
    var xl: CGFloat = 28

    var xsShape: RoundedRectangle { RoundedRectangle(cornerRadius: xs, style: .continuous) }
    var smShape: RoundedRectangle { RoundedRectangle(cornerRadius: sm, style: .continuous) }
    var mdShape: RoundedRectangle { RoundedRectangle(cornerRadius: md, style: .continuous) }
    var lgShape: RoundedRectangle { RoundedRectangle(cornerRadius: lg, style: .continuous) }
    var xlShape: RoundedRectangle { RoundedRectangle(cornerRadius: xl, style: .continuous) }
    /// Pills: buttons, chips, fields.
    var full: Capsule { Capsule(style: .continuous) }
    /// Top of sheets and the player dock.
    var xlTop: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: xl, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: xl, style: .continuous)
    }
}
