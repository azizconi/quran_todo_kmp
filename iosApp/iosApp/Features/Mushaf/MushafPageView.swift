import ComposeApp
import SwiftUI
import UIKit

/// One page: skeleton, then the page drawn as an image (words, headers, Bismillahs) with the underlays of the picked and sounding ayah
/// under it; hidden ayahs of "Check yourself" are a second, blurred image.
struct MushafPageView: View {
    @Environment(\.qh) private var qh
    @Environment(\.qhReduceMotion) private var reduced
    @Environment(Copy.self) private var copy
    let page: Int
    let model: MushafModel
    let selected: AyahRef?
    let playing: AyahRef?
    let flash: AyahRef?
    let looks: [AyahRef: AyahLook]
    let top: CGFloat
    let bottom: CGFloat
    let active: Bool
    let onTap: (AyahRef) -> Void
    let onLong: (AyahRef) -> Void
    let onBlank: () -> Void
    let onOpenText: () -> Void

    private enum PageState { case loading, ready(MushafPageData), missing }
    @State private var state = PageState.loading
    @State private var layout: MushafLayout?
    @State private var image: UIImage?
    @State private var hiddenImage: UIImage?
    @State private var attempt = 0

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let bodyHeight = max(size.height - top - bottom, 100)
            ZStack(alignment: .topLeading) {
                switch state {
                case .loading: skeleton(size)
                case .missing: missing(size)
                case .ready(let data): pageBody(size: size, bodyHeight: bodyHeight, data: data)
                }
                Text("\(page)").qhText({ $0.label }, color: { $0.inkTertiary }).monospacedDigit()
                    .frame(width: size.width).offset(y: size.height - bottom + 8)
                    .accessibilityHidden(true)
            }
            .frame(width: size.width, height: size.height)
            .background(qh.colors.readerBg)
            .environment(\.layoutDirection, .leftToRight)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(copy.pages.pageOf(page: Int32(page)))
        .task(id: "\(page)-\(attempt)-\(active)") {
            guard active else { return }
            if case .ready = state { return }
            if let data = await MushafPageService.shared.load(page, force: attempt > 0) { state = .ready(data) } else { state = .missing }
        }
    }

    @ViewBuilder private func pageBody(size: CGSize, bodyHeight: CGFloat, data: MushafPageData) -> some View {
        let block = MushafLayout.blockWidth(screen: size.width)
        let glyph = MushafLayout.glyphSize(block: block)
        let prefs = ReadingPrefs.shared
        let looksKey = looks.map { "\($0.key.surah):\($0.key.ayah)=\($0.value)" }.sorted().joined()
        let key = "\(page)-\(Int(size.width))-\(Int(bodyHeight))-\(prefs.style.storageValue)-\(qh.isDark)-\(looksKey)"
        let slot = MushafLayout.slotHeight(body: bodyHeight, glyph: layout?.glyphSize ?? glyph, lineCount: Int(data.page.lineCount))
        ZStack(alignment: .topLeading) {
            if let layout {
                let areaTop = top + MushafLayout.pageTop(height: bodyHeight, slot: slot, lineCount: layout.lineCount)
                let left = (size.width - layout.blockWidth) / 2
                underlays(layout, left: left, top: areaTop, slot: slot)
                if let image { Image(uiImage: image).resizable().frame(width: size.width, height: bodyHeight).offset(y: top) }
                if let hiddenImage {
                    Image(uiImage: hiddenImage).resizable().frame(width: size.width, height: bodyHeight).offset(y: top)
                        .blur(radius: layout.glyphSize * 0.5)
                }
                TouchCatcher(onTap: { point in
                    if let ref = hit(point, layout: layout, size: size, bodyHeight: bodyHeight, slot: slot) { onTap(ref) } else { onBlank() }
                }, onLong: { point in
                    if let ref = hit(point, layout: layout, size: size, bodyHeight: bodyHeight, slot: slot) {
                        QhHaptics.shared.perform(.longPress)
                        onLong(ref)
                    }
                })
            } else {
                skeleton(size)
            }
        }
        .task(id: key) { await render(data: data, size: size, bodyHeight: bodyHeight, block: block, glyph: glyph) }
    }

    private func render(data: MushafPageData, size: CGSize, bodyHeight: CGFloat, block: CGFloat, glyph: CGFloat) async {
        let prefs = ReadingPrefs.shared
        let ink = PageInk(qh.colors)
        let scale = UIScreen.main.scale
        var texts: [AyahRef: [String]] = [:]
        if prefs.face != .madinah {
            for (k, v) in model.vm.wordTexts(page: data.page) { texts[AyahRef(k)] = v }
        }
        let built = MushafLayout.build(data: data, glyphSize: glyph, blockWidth: block, arabicName: { model.arabic($0) },
                                       textFace: prefs.face == .madinah ? nil : prefs.face, wordTexts: texts)
        let usedSlot = MushafLayout.slotHeight(body: bodyHeight, glyph: built.glyphSize, lineCount: built.lineCount)
        let imgSize = CGSize(width: size.width, height: bodyHeight)
        let looksCopy = looks
        let result = await Task.detached(priority: .userInitiated) { () -> (UIImage, UIImage?) in
            let visible = MushafRenderer.render(built, size: imgSize, slot: usedSlot, looks: looksCopy, hidden: false, ink: ink, scale: scale)
            let anyHidden = looksCopy.values.contains { $0 == .hidden || $0 == .waiting }
            let hid = anyHidden ? MushafRenderer.render(built, size: imgSize, slot: usedSlot, looks: looksCopy, hidden: true, ink: ink, scale: scale) : nil
            return (visible, hid)
        }.value
        layout = built
        withAnimation(reduced ? nil : .easeOut(duration: 0.2)) { image = result.0; hiddenImage = result.1 }
    }

    private func hit(_ point: CGPoint, layout: MushafLayout, size: CGSize, bodyHeight: CGFloat, slot: CGFloat) -> AyahRef? {
        let x = point.x - (size.width - layout.blockWidth) / 2
        let areaTop = top + MushafLayout.pageTop(height: bodyHeight, slot: slot, lineCount: layout.lineCount)
        let y = point.y - areaTop
        guard y >= 0 else { return nil }
        let line = Int(y / slot) + 1
        guard (1...layout.lineCount).contains(line) else { return nil }
        return layout.ayah(atX: x, line: line)
    }

    private func underlays(_ layout: MushafLayout, left: CGFloat, top: CGFloat, slot: CGFloat) -> some View {
        let c = qh.colors
        let pad = layout.glyphSize * 0.12
        let gold = c.gold.opacity(c.isDark ? 0.30 : 0.24)
        let current: AyahRef? = model.check.value?.current.map { AyahRef($0.key) }
        let entries: [(AyahRef?, Color)] = [(selected, gold), (flash, gold), (current, gold), (playing, c.primary.opacity(c.isDark ? 0.30 : 0.18))]
        return ZStack(alignment: .topLeading) {
            ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                if let ref = entry.0 {
                    ForEach(Array(layout.spans(of: ref).enumerated()), id: \.offset) { _, span in
                        RoundedRectangle(cornerRadius: layout.glyphSize * 0.28, style: .continuous).fill(entry.1)
                            .frame(width: span.right - span.left + 2 * pad, height: slot * 0.8)
                            .offset(x: left + span.left - pad, y: top + CGFloat(span.line - 1) * slot + slot * 0.1)
                    }
                }
            }
        }
        .allowsHitTesting(false)
        .animation(reduced ? nil : .easeOut(duration: 0.2), value: selected)
        .animation(reduced ? nil : .easeOut(duration: 0.2), value: playing)
    }

    private func skeleton(_ size: CGSize) -> some View {
        VStack(spacing: 14) {
            ForEach(0..<15, id: \.self) { i in
                Skeleton(shape: Capsule()).frame(width: size.width * (i % 5 == 4 ? 0.55 : 0.82), height: 16)
            }
        }
        .frame(width: size.width).padding(.top, top + 24)
    }

    private func missing(_ size: CGSize) -> some View {
        VStack(spacing: QhSpacing.x3) {
            QhEmptyState(title: copy.pages.notLoaded, actionLabel: copy.pages.retry) { state = .loading; attempt += 1 }
            QhButton(copy.pages.openAsText, variant: .text, size: .m, action: onOpenText)
        }
        .frame(width: size.width).padding(.top, top)
    }
}

/// A transparent view that reports the position of a tap and of a long press (SwiftUI gestures do not say where).
struct TouchCatcher: UIViewRepresentable {
    let onTap: (CGPoint) -> Void
    let onLong: (CGPoint) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        let long = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.longPressed(_:)))
        long.minimumPressDuration = 0.45
        tap.require(toFail: long)
        view.addGestureRecognizer(tap)
        view.addGestureRecognizer(long)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) { context.coordinator.parent = self }
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject {
        var parent: TouchCatcher
        init(_ parent: TouchCatcher) { self.parent = parent }
        @objc func tapped(_ g: UITapGestureRecognizer) { parent.onTap(g.location(in: g.view)) }
        @objc func longPressed(_ g: UILongPressGestureRecognizer) { if g.state == .began { parent.onLong(g.location(in: g.view)) } }
    }
}
