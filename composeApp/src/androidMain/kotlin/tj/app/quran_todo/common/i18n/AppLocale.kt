package tj.app.quran_todo.common.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import tj.app.quran_todo.common.platform.AndroidContextHolder

/**
 * Gives the app's own language choice to Android, so the texts Android and the libraries draw themselves (Material's "Close sheet" on
 * a sheet's scrim, system dialogs, dates) follow the app's language rather than the phone's.
 *
 * - Android 13+: the per-app language of the system (`LocaleManager`). The app then shows in Settings › Apps › Language, and a choice
 *   made there is taken back into the app ([syncFromSystem]). The system recreates the activity when the language changes.
 * - Older Android: the activity's context is made with the language ([wrap]) and the activity is recreated on a change.
 *
 * Compose and Material have no Tajik texts: the ones they speak on sheets are translated in `res/values-tg/library_overrides.xml`.
 */
object AppLocale {
    private const val PREFS_NAME = "quran_todo_prefs"
    private const val KEY_LANGUAGE = "language_code"

    /** The BCP-47 tag given to Android. */
    fun tags(language: AppLanguage): String = language.code

    /** Called after the person picked [language] in the app. */
    fun apply(language: AppLanguage) {
        val context = AndroidContextHolder.context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            val wanted = LocaleList.forLanguageTags(tags(language))
            if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
        } else if (currentLanguageOf(AndroidContextHolder.activity) != language) {
            AndroidContextHolder.activity?.recreate()
        }
    }

    /**
     * At start (Android 13+): a language picked in the system settings wins and is saved as the app's choice; with none there, the
     * app's saved choice is handed to the system (people who chose a language before this existed). True when the saved choice changed.
     */
    fun syncFromSystem(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(LocaleManager::class.java) ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)?.let(::languageFromCode)
        val system = manager.applicationLocales
        if (system.isEmpty) {
            if (saved != null) manager.applicationLocales = LocaleList.forLanguageTags(tags(saved))
            return false
        }
        val fromSystem = languageFromCode(system[0].language)
        if (fromSystem == saved) return false
        prefs.edit().putString(KEY_LANGUAGE, fromSystem.code).apply()
        return true
    }

    /** Before Android 13: the activity's base context in the saved language (unchanged when nothing was chosen). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val code = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null) ?: return base
        val locales = LocaleList.forLanguageTags(tags(languageFromCode(code)))
        Locale.setDefault(locales[0])
        val configuration = Configuration(base.resources.configuration).apply { setLocales(locales) }
        return base.createConfigurationContext(configuration)
    }

    private fun currentLanguageOf(context: Context?): AppLanguage? {
        val locales = context?.resources?.configuration?.locales ?: return null
        return if (locales.isEmpty) null else languageFromCode(locales[0].language)
    }
}
