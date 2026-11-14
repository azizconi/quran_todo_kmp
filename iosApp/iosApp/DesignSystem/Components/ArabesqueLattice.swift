import SwiftUI

/// A repeating ogee lattice in 1pt lines at 5-9% alpha, made of two mirrored sine waves per column with a dot where neighbouring
/// columns touch: the background of the hero card and the reader header. Fill the parent with it.
///
/// `cell` is the width of one column; the vertical period of the waves is 1.5 cells. `scrollOffset` is the current scroll offset;
/// the pattern follows it at `parallax` speed (0.2). Ignored under Reduce Motion.
struct ArabesqueLattice: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    var color: Color?
    var alpha: Double = 0.07
    var cell: CGFloat = 28
    var scrollOffset: CGFloat = 0
    var parallax: CGFloat = 0.2

    var body: some View {
        Canvas { gc, size in
            let period = cell * 1.5
            let amplitude = cell / 2
            let columns = Int(size.width / cell) + 1
            let step = period / 16
            let shift = reduced ? 0 : (scrollOffset * parallax).truncatingRemainder(dividingBy: period)
            let tint = (color ?? qh.colors.gold).opacity(alpha)

            var waves = Path()
            for k in -1...(columns + 1) {
                let x0 = CGFloat(k) * cell
                for sign in [CGFloat(1), -1] {
                    var y = -period
                    var first = true
                    while y <= size.height + period {
                        let x = x0 + sign * amplitude * CGFloat(sin(2 * Double.pi * Double(y) / Double(period)))
                        let pt = CGPoint(x: x, y: y - shift)
                        if first { waves.move(to: pt); first = false } else { waves.addLine(to: pt) }
                        y += step
                    }
                }
            }
            gc.stroke(waves, with: .color(tint), lineWidth: 1)

            var row = -1
            while CGFloat(row) * period <= size.height + period {
                for k in -1...(columns + 1) {
                    let c = CGPoint(x: CGFloat(k) * cell + amplitude, y: CGFloat(row) * period + 0.75 * period - shift)
                    gc.fill(Path(ellipseIn: CGRect(x: c.x - 1.3, y: c.y - 1.3, width: 2.6, height: 2.6)), with: .color(tint))
                }
                row += 1
            }
        }
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview("Lattice light") { LatticePreview().qhPreviewTheme() }
#Preview("Lattice dark") { LatticePreview().qhPreviewTheme(dark: true) }

private struct LatticePreview: View {
    var body: some View {
        ArabesqueLattice(alpha: 0.4).frame(height: 200).padding()
    }
}
