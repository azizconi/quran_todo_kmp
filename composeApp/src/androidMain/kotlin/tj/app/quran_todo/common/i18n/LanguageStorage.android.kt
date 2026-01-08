package tj.app.quran_todo.common.i18n

import android.content.Context
import java.util.Locale
import tj.app.quran_todo.common.platform.AndroidContextHolder

private const val PREFS_NAME = "quran_todo_prefs"
private const val KEY_LANGUAGE = "language_code"

actual object LanguageStorage {
    private fun prefs() =
        AndroidContextHolder.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun getSavedLanguage(): AppLanguage? {
        val code = prefs().getString(KEY_LANGUAGE, null)
        return if (code == null) null else languageFromCode(code)
    }

    actual fun saveLanguage(language: AppLanguage) {
        prefs().edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    actual fun getDeviceLanguage(): AppLanguage {
        val code = Locale.getDefault().language
        return languageFromCode(code)
    }
}
