package tj.app.quran_todo.common.i18n

expect object LanguageStorage {
    fun getSavedLanguage(): AppLanguage?
    fun saveLanguage(language: AppLanguage)
    fun getDeviceLanguage(): AppLanguage
}
