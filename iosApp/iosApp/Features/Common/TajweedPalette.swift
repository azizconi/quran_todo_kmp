import SwiftUI

/// The colours of the tajweed rules (port of `ui/tajweed/TajweedPalette.kt`): six groups, a light and a dark version. Hex values are
/// copied from Kotlin, so the contrast checks done there (3:1 against every reader background) hold here too.
enum TajweedPalette {
    static func color(_ rule: TajweedRule, dark: Bool) -> Color {
        Color(argb: (dark ? darkValues : lightValues)[rule] ?? 0xFF7F848D)
    }

    private static let lightValues: [TajweedRule: UInt32] = [
        .hamzatWasl: 0xFF7F848D, .lamShamsiyah: 0xFF7F848D, .silentLetter: 0xFF7F848D,
        .maddNormal: 0xFFD4573F, .maddPermissible: 0xFFBF3733, .maddObligatory: 0xFF9C2330, .maddNecessary: 0xFF6A0F22,
        .ghunna: 0xFF12803F, .idghamWithGhunna: 0xFF3F8A24, .ikhfa: 0xFF2A8C74, .ikhfaShafawi: 0xFF6A8A1C,
        .iqlab: 0xFF2F5DD0,
        .qalqalah: 0xFF0A87AD,
        .idghamWithoutGhunna: 0xFF7A4FB8, .idghamMutajanisayn: 0xFF7A4FB8, .idghamMutaqaribayn: 0xFF7A4FB8, .idghamShafawi: 0xFF7A4FB8,
    ]

    private static let darkValues: [TajweedRule: UInt32] = [
        .hamzatWasl: 0xFF9CA3AD, .lamShamsiyah: 0xFF9CA3AD, .silentLetter: 0xFF9CA3AD,
        .maddNormal: 0xFFFFCDC4, .maddPermissible: 0xFFFFAA9B, .maddObligatory: 0xFFFF8878, .maddNecessary: 0xFFF96A5B,
        .ghunna: 0xFF4CD17A, .idghamWithGhunna: 0xFF9BD45B, .ikhfa: 0xFF52D1B0, .ikhfaShafawi: 0xFFC2CF5A,
        .iqlab: 0xFF7EA2FF,
        .qalqalah: 0xFF58C8F0,
        .idghamWithoutGhunna: 0xFFC09BF5, .idghamMutajanisayn: 0xFFC09BF5, .idghamMutaqaribayn: 0xFFC09BF5, .idghamShafawi: 0xFFC09BF5,
    ]
}
