import SwiftUI

/// Right-to-left word flow: subviews are the words in reading order, the first one sits at the right edge, lines break when the next
/// word does not fit and wrap towards the left ("Start" alignment of Arabic text). Every line is `lineHeight` high and a word is centred
/// in it, so a row costs the same whatever marks the letters carry (the Arabic faces have tall ascenders).
///
/// The layout places words itself (it does not depend on the environment layout direction), measures each subview once per layout pass
/// and caches the line breaks of the last width.
struct WordFlowLayout: Layout {
    var lineHeight: CGFloat
    var spacing: CGFloat

    struct Cache {
        var sizes: [CGSize] = []
        var width: CGFloat = -1
        var lines: [[Int]] = []
    }

    func makeCache(subviews: Subviews) -> Cache {
        Cache(sizes: subviews.map { $0.sizeThatFits(.unspecified) })
    }

    func updateCache(_ cache: inout Cache, subviews: Subviews) {
        cache = Cache(sizes: subviews.map { $0.sizeThatFits(.unspecified) })
    }

    private func breakLines(_ cache: inout Cache, width: CGFloat) -> [[Int]] {
        if cache.width == width { return cache.lines }
        var lines: [[Int]] = []
        var line: [Int] = []
        var used: CGFloat = 0
        for (i, size) in cache.sizes.enumerated() {
            let need = line.isEmpty ? size.width : used + spacing + size.width
            if !line.isEmpty, need > width {
                lines.append(line)
                line = [i]
                used = size.width
            } else {
                line.append(i)
                used = need
            }
        }
        if !line.isEmpty { lines.append(line) }
        cache.width = width
        cache.lines = lines
        return lines
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Cache) -> CGSize {
        let width = proposal.width ?? .infinity
        let lines = breakLines(&cache, width: width)
        var widest: CGFloat = 0
        for line in lines {
            var w: CGFloat = spacing * CGFloat(max(line.count - 1, 0))
            for i in line { w += cache.sizes[i].width }
            widest = max(widest, w)
        }
        return CGSize(width: proposal.width ?? widest, height: lineHeight * CGFloat(lines.count))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Cache) {
        let lines = breakLines(&cache, width: bounds.width)
        for (row, line) in lines.enumerated() {
            var cursor: CGFloat = 0
            for i in line {
                let size = cache.sizes[i]
                let x = bounds.maxX - cursor - size.width
                let y = bounds.minY + CGFloat(row) * lineHeight + (lineHeight - size.height) / 2
                subviews[i].place(at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: ProposedViewSize(size))
                cursor += size.width + spacing
            }
        }
    }
}
