import Foundation
import SwiftUI

/// What the app writes into the App Group as `lesson_widget.json` (Kotlin `IosWidgetRefresher.writeSnapshot`): the strings are already in
/// the language of the app, so the widget has no copy of its own.
struct LessonSnapshot: Codable, Equatable {
    var version: Int = 1
    var language: String = "en"
    /// "forest", "sand" or "ocean": the palette chosen in the app (a widget cannot read the app's settings otherwise).
    var palette: String? = "forest"
    var epochDay: Int = 0
    var state: String = "Start"
    var title: String
    var detail: String
    var streak: String?
    var action: String?
    var opensLesson: Bool = true

    static let appGroup = "group.tj.app.quran-todo"
    static let fileName = "lesson_widget.json"

    /// The snapshot of the app, or nil when the app has not written one yet (first install) or it cannot be read.
    static func load() -> LessonSnapshot? {
        guard let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup),
              let data = try? Data(contentsOf: folder.appendingPathComponent(fileName)) else { return nil }
        return try? JSONDecoder().decode(LessonSnapshot.self, from: data)
    }

    /// Shown until the app has written something, and in the widget gallery.
    static let sample = LessonSnapshot(
        title: "Lesson of the day · ~12 min",
        detail: "New: An-Naba 1–5 · 2 repetitions",
        streak: "7 days in a row",
        action: "Start lesson"
    )

    /// "Quran Hifz" with nothing else: no snapshot yet.
    static let empty = LessonSnapshot(title: "Quran Hifz", detail: "", streak: nil, action: nil, opensLesson: false)
}

/// The colours of the app's three palettes (same values as `DesignSystem/QhColors.swift`), light and dark.
struct WidgetPalette {
    let bg, surface, ink, inkSecondary, primary, onPrimary, primaryContainer, gold, hairline: Color

    static func of(_ name: String?, dark: Bool) -> WidgetPalette {
        func c(_ v: UInt32) -> Color {
            Color(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255, opacity: 1)
        }
        let primary: UInt32, onPrimaryDark: UInt32, container: (UInt32, UInt32), bg: (UInt32, UInt32)
        switch name {
        case "sand":
            primary = dark ? 0xE3C278 : 0x8C6A1E; onPrimaryDark = 0x3A2A06; container = (0xF2E5C2, 0x3B3019); bg = (0xF7F2E6, 0x13110C)
        case "ocean":
            primary = dark ? 0x7DCBDF : 0x1A6579; onPrimaryDark = 0x00303B; container = (0xD3E9EF, 0x12333D); bg = (0xF2F5F5, 0x0C1214)
        default:
            primary = dark ? 0x58CDA2 : 0x0E6B52; onPrimaryDark = 0x003324; container = (0xD5EBDF, 0x10392C); bg = (0xF6F3EC, 0x0D1311)
        }
        return WidgetPalette(
            bg: c(dark ? bg.1 : bg.0),
            surface: c(dark ? 0x141C19 : 0xFFFDF9),
            ink: c(dark ? 0xECF2EE : 0x16201C),
            inkSecondary: c(dark ? 0xA7B3AC : 0x5D6862),
            primary: c(primary),
            onPrimary: dark ? c(onPrimaryDark) : .white,
            primaryContainer: c(dark ? container.1 : container.0),
            gold: c(dark ? 0xDDB76A : 0xA97B2C),
            hairline: c(dark ? 0x27322D : 0xE2DBCB)
        )
    }
}
