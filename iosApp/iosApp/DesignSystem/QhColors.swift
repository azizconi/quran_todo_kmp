import SwiftUI

/// Theme mode chosen by the person: follow the system, or force light/dark.
enum QhThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
}

/// Palettes of the "Muskhaf" visual language. Only the brand, the background and the reader paper change.
enum QhThemePalette: String, CaseIterable, Identifiable {
    case forest, sand, ocean
    var id: String { rawValue }
}

extension Color {
    /// `0xAARRGGBB` like Android's `Color(0xFF...)`.
    init(argb: UInt32) {
        let a = Double((argb >> 24) & 0xFF) / 255
        let r = Double((argb >> 16) & 0xFF) / 255
        let g = Double((argb >> 8) & 0xFF) / 255
        let b = Double(argb & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

extension Color {
    /// Blend in sRGB towards `other` (`t` = 0 is self, 1 is other); the equivalent of Compose's `lerp`.
    func blended(with other: Color, by t: Double) -> Color {
        var a: (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        var b: (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        UIColor(self).getRed(&a.0, green: &a.1, blue: &a.2, alpha: &a.3)
        UIColor(other).getRed(&b.0, green: &b.1, blue: &b.2, alpha: &b.3)
        let k = CGFloat(t)
        return Color(.sRGB, red: a.0 + (b.0 - a.0) * k, green: a.1 + (b.1 - a.1) * k, blue: a.2 + (b.2 - a.2) * k, opacity: a.3 + (b.3 - a.3) * k)
    }
}

/// Colour tokens (port of `QhColors.kt`). Status colours are identical for every palette: their meaning never changes.
/// Read them through `@Environment(\.qh)`; never hard-code a colour in a component.
struct QhColors: Equatable {
    /// Screen background (paper by day, ink by night).
    var bg: Color
    /// Cards, sheets, the player.
    var surface: Color
    /// Search field, inactive chips, progress track.
    var surfaceMuted: Color
    /// Pressed state, skeletons.
    var surfaceStrong: Color
    /// 1pt dividers and outlines.
    var hairline: Color
    /// Primary text and Arabic text.
    var ink: Color
    /// Captions.
    var inkSecondary: Color
    /// Metadata, placeholders (decorative contrast only).
    var inkTertiary: Color
    /// Brand: buttons and active elements.
    var primary: Color
    var onPrimary: Color
    /// Tonal buttons, selection.
    var primaryContainer: Color
    var onPrimaryContainer: Color
    /// Ornament and the "memorizing" accent. Never for body text.
    var gold: Color
    var goldContainer: Color
    /// Status "memorized".
    var learned: Color
    var onLearned: Color
    var learnedContainer: Color
    /// Status "memorizing".
    var learning: Color
    var learningContainer: Color
    /// "Time to review" and weak ayahs.
    var due: Color
    var dueContainer: Color
    /// Errors and destructive actions.
    var danger: Color
    var onDanger: Color
    /// Reader paper: warm, never green at night.
    var readerBg: Color
    var readerSurface: Color
    /// Background of the current ayah (a translucent primary).
    var readerHighlight: Color
    /// Scrim under sheets.
    var scrim: Color
    var isDark: Bool

    static func resolve(dark: Bool, palette: QhThemePalette) -> QhColors {
        let brand = BrandTokens.of(palette)
        func pick(_ light: UInt32, _ dk: UInt32) -> Color { Color(argb: dark ? dk : light) }
        let primary = Color(argb: dark ? brand.primaryDark : brand.primaryLight)
        return QhColors(
            bg: Color(argb: dark ? brand.bgDark : brand.bgLight),
            surface: pick(0xFFFFFDF9, 0xFF141C19),
            surfaceMuted: pick(0xFFEFEADF, 0xFF1A2420),
            surfaceStrong: pick(0xFFE6E0D2, 0xFF212C27),
            hairline: pick(0xFFE2DBCB, 0xFF27322D),
            ink: pick(0xFF16201C, 0xFFECF2EE),
            inkSecondary: pick(0xFF5D6862, 0xFFA7B3AC),
            inkTertiary: pick(0xFF8B938E, 0xFF76837C),
            primary: primary,
            onPrimary: dark ? Color(argb: brand.onPrimaryDark) : .white,
            primaryContainer: Color(argb: dark ? brand.primaryContainerDark : brand.primaryContainerLight),
            onPrimaryContainer: pick(0xFF08392C, 0xFFBDEFD9),
            gold: pick(0xFFA97B2C, 0xFFDDB76A),
            goldContainer: pick(0xFFF3E7CA, 0xFF33291A),
            learned: pick(0xFF0E6B52, 0xFF58CDA2),
            onLearned: dark ? Color(argb: 0xFF003324) : .white,
            learnedContainer: pick(0xFFD5EBDF, 0xFF10392C),
            learning: pick(0xFFA97B2C, 0xFFDDB76A),
            learningContainer: pick(0xFFF3E7CA, 0xFF33291A),
            due: pick(0xFFB4573A, 0xFFE89676),
            dueContainer: pick(0xFFF6DDD3, 0xFF3A2219),
            danger: pick(0xFFB3412F, 0xFFF08A7A),
            onDanger: dark ? Color(argb: 0xFF3A140D) : .white,
            readerBg: Color(argb: dark ? brand.readerBgDark : brand.readerBgLight),
            readerSurface: pick(0xFFFFFDF9, 0xFF191815),
            readerHighlight: primary.opacity(dark ? 0.12 : 0.07),
            scrim: Color.black.opacity(dark ? 0.56 : 0.36),
            isDark: dark
        )
    }
}

/// The only values a palette changes.
private struct BrandTokens {
    let primaryLight, primaryDark, onPrimaryDark: UInt32
    let primaryContainerLight, primaryContainerDark: UInt32
    let bgLight, bgDark, readerBgLight, readerBgDark: UInt32

    static func of(_ palette: QhThemePalette) -> BrandTokens {
        switch palette {
        case .forest:
            return BrandTokens(primaryLight: 0xFF0E6B52, primaryDark: 0xFF58CDA2, onPrimaryDark: 0xFF003324,
                               primaryContainerLight: 0xFFD5EBDF, primaryContainerDark: 0xFF10392C,
                               bgLight: 0xFFF6F3EC, bgDark: 0xFF0D1311,
                               readerBgLight: 0xFFFBF8F2, readerBgDark: 0xFF11100E)
        case .sand:
            return BrandTokens(primaryLight: 0xFF8C6A1E, primaryDark: 0xFFE3C278, onPrimaryDark: 0xFF3A2A06,
                               primaryContainerLight: 0xFFF2E5C2, primaryContainerDark: 0xFF3B3019,
                               bgLight: 0xFFF7F2E6, bgDark: 0xFF13110C,
                               readerBgLight: 0xFFFBF6EA, readerBgDark: 0xFF12100B)
        case .ocean:
            return BrandTokens(primaryLight: 0xFF1A6579, primaryDark: 0xFF7DCBDF, onPrimaryDark: 0xFF00303B,
                               primaryContainerLight: 0xFFD3E9EF, primaryContainerDark: 0xFF12333D,
                               bgLight: 0xFFF2F5F5, bgDark: 0xFF0C1214,
                               readerBgLight: 0xFFF8FAFA, readerBgDark: 0xFF0F1213)
        }
    }
}
