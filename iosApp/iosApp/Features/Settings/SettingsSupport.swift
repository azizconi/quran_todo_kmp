import ComposeApp
import SwiftUI
import UIKit

// Copy accessors, links and small helpers of the S7 screens (settings, downloads, about). `Copy` keeps its own cache private, so
// these have their own, per language.

@MainActor
private enum S7CopyCache {
    static var store: [String: Any] = [:]
    static func get<T>(_ copy: Copy, _ key: String, _ make: () -> T) -> T {
        let full = "\(copy.language.name)/\(key)"
        if let hit = store[full] as? T { return hit }
        let value = make()
        store[full] = value
        return value
    }
}

extension Copy {
    var s7Settings: SettingsCopy { S7CopyCache.get(self, "settings") { IosCopy.shared.settingsCopy() } }
    var s7Audio: AudioCopy { S7CopyCache.get(self, "audio") { IosCopy.shared.audioCopy() } }
    var s7Downloads: DownloadsCopy { S7CopyCache.get(self, "downloads") { IosCopy.shared.downloadsCopy() } }
    var s7Pages: PagesCopy { S7CopyCache.get(self, "pages") { IosCopy.shared.pagesCopy() } }
    var s7Translation: TranslationCopy { S7CopyCache.get(self, "translation") { IosCopy.shared.translationCopy() } }
    var s7Widget: WidgetCopy { S7CopyCache.get(self, "widget") { IosCopy.shared.widgetCopy() } }
}

/// Where things live outside the app: the privacy policy, the contact address, the store listing.
enum SettingsLinks {
    private static let policyBase = "https://azizconi.github.io/quran_todo_kmp/privacy"
    /// The developer's contact address for privacy questions (same as Android `ContactEmail`).
    static let contactEmail: String? = "azizconi1@gmail.com"
    /// The App Store id of the app; empty until the app has a listing, then the rate row shows "store unavailable".
    static let appStoreId = ""

    /// Russian for ru / tg / uz, English for en / tr (same rule as Android).
    static func privacyPolicy(_ language: AppLanguage) -> URL? {
        switch language {
        case .ru, .tg, .uz: return URL(string: "\(policyBase)/ru.html")
        default: return URL(string: "\(policyBase)/")
        }
    }

    static var mailto: URL? { contactEmail.flatMap { URL(string: "mailto:\($0)") } }

    static var reviewURL: URL? {
        appStoreId.isEmpty ? nil : URL(string: "itms-apps://itunes.apple.com/app/id\(appStoreId)?action=write-review")
    }

    /// "2.0.0 (12)" from the bundle.
    static var versionLabel: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        if short.isEmpty { return "" }
        return build.isEmpty ? short : "\(short) (\(build))"
    }

    @MainActor
    @discardableResult
    static func open(_ url: URL?) -> Bool {
        guard let url else { return false }
        UIApplication.shared.open(url)
        return true
    }
}

/// The name of an app language in itself ("Русский", "Тоҷикӣ", "O‘zbekcha", "English", "Türkçe").
extension AppLanguage {
    var nativeLabel: String {
        switch self {
        case .ru: return "Русский"
        case .tg: return "Тоҷикӣ"
        case .uz: return "O‘zbekcha"
        case .en: return "English"
        case .tr: return "Türkçe"
        default: return name
        }
    }
}

/// A grouped card of rows (`QhCard` without padding): `SettingsSection("Appearance") { row; QhRowDivider(); row }`, with an optional footnote.
struct SettingsSection<Content: View>: View {
    @Environment(\.qh) private var qh
    let title: String
    var footnote: String?
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: QhSpacing.x2) {
            QhSectionHeader(title).padding(.horizontal, QhSpacing.x1)
            QhCard(padding: 0) { content() }
            if let footnote {
                Text(footnote).qhText({ $0.callout }, color: { $0.inkSecondary }).padding(.horizontal, QhSpacing.x1)
            }
        }
    }
}
