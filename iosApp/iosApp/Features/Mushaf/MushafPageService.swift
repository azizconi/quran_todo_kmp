import ComposeApp
import Foundation

/// A page of the mushaf ready to draw: the Kotlin layout (`MushafPage`) and the PostScript name of its registered font.
struct MushafPageData {
    let page: MushafPage
    let fontName: String
}

/// The pages come from the shared `MushafPageProvider` (memory, pinned copy, cache, network; S2); the font file it returns is registered
/// with CoreText by `MushafFonts`.
actor MushafPageService {
    static let shared = MushafPageService()

    func load(_ page: Int, force: Bool = false) async -> MushafPageData? {
        guard let loaded = try? await IosReaderBridge.shared.mushafPages.load(page: Int32(page), force: force) else { return nil }
        guard let name = MushafFonts.register(path: loaded.fontPath) else { return nil }
        return MushafPageData(page: loaded.page, fontName: name)
    }

    func prefetch(_ pages: [Int]) async {
        _ = try? await IosReaderBridge.shared.mushafPages.prefetch(pages: pages.filter { (1...604).contains($0) }.map { KotlinInt(int: Int32($0)) })
    }
}
