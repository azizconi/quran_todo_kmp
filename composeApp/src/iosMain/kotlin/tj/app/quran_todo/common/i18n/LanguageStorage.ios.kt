package tj.app.quran_todo.common.i18n

import platform.Foundation.NSUserDefaults

private const val KEY_LANGUAGE = "language_code"

actual object LanguageStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getSavedLanguage(): AppLanguage? {
        val code = defaults.stringForKey(KEY_LANGUAGE)
        return if (code == null) null else languageFromCode(code)
    }

    actual fun saveLanguage(language: AppLanguage) {
        defaults.setObject(language.code, forKey = KEY_LANGUAGE)
    }

    actual fun getDeviceLanguage(): AppLanguage {
        val languages = defaults.arrayForKey("AppleLanguages") as? List<*>
        val preferred = languages?.firstOrNull() as? String
        val code = preferred?.substringBefore("-") ?: "en"
        return languageFromCode(code)
    }
}
