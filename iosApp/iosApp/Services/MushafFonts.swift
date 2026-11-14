import CoreText
import SwiftUI
import UIKit

/// The per-page fonts of the mushaf (KFGQPC, one font file per page): the shared store gives the path of the file
/// (`LoadedPage.fontPath`); this registers it with CoreText for the process and gives the font.
///
/// `MushafFonts.font(path:size:)` returns nil when the file is not a font (the screen then shows its "page not loaded" placeholder).
enum MushafFonts {
    private static var names: [String: String] = [:]
    private static let lock = NSLock()

    /// The PostScript name of the font in the file at [path], registered for this process (once per path).
    static func register(path: String) -> String? {
        lock.lock()
        defer { lock.unlock() }
        if let known = names[path] { return known }
        let url = URL(fileURLWithPath: path) as CFURL
        guard let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url) as? [CTFontDescriptor],
              let descriptor = descriptors.first,
              let name = CTFontDescriptorCopyAttribute(descriptor, kCTFontNameAttribute) as? String else { return nil }
        var error: Unmanaged<CFError>?
        // Already registered (another page of the same file, a second start of the screen) is not a failure.
        if !CTFontManagerRegisterFontsForURL(url, .process, &error) {
            let code = error.map { CFErrorGetCode($0.takeRetainedValue()) }
            // 105: kCTFontManagerErrorAlreadyRegistered.
            if code != 105 { return nil }
        }
        names[path] = name
        return name
    }

    static func uiFont(path: String, size: CGFloat) -> UIFont? {
        register(path: path).flatMap { UIFont(name: $0, size: size) }
    }

    static func font(path: String, size: CGFloat) -> Font? {
        register(path: path).map { Font.custom($0, fixedSize: size) }
    }
}
