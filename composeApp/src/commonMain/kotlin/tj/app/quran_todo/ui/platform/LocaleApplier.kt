package tj.app.quran_todo.ui.platform

import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Gives the language the person picked in the app to the system, so the texts the system draws itself (dialogs, date pickers, the
 * language row of the app's page in Settings) follow it.
 *
 * Android: `AppLocale` (per-app language of Android 13+, a recreated activity before). iOS (S2): nothing to apply at run time (SwiftUI
 * reads the copy of the shared layer); `UserDefaults` key `AppleLanguages` is written so the next launch is in the language too.
 */
interface LocaleApplier {
    fun apply(language: AppLanguage)
}

object NoLocaleApplier : LocaleApplier {
    override fun apply(language: AppLanguage) = Unit
}
