package tj.app.quran_todo.ui.platform

import platform.Foundation.NSUserDefaults

/**
 * [KeyValueStore] over NSUserDefaults. [prefix] keeps the files of Android (`quran_hifz_ui_prefs`, `downloads_prefs`...) apart in the one
 * defaults domain: the key `haptics_enabled` of the file `quran_hifz_ui_prefs` is stored as `quran_hifz_ui_prefs.haptics_enabled`.
 */
class UserDefaultsStore(
    private val prefix: String,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStore {
    private fun k(key: String) = "$prefix.$key"

    override fun contains(key: String) = defaults.objectForKey(k(key)) != null
    override fun getBoolean(key: String, default: Boolean) = if (contains(key)) defaults.boolForKey(k(key)) else default
    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = k(key))
    override fun getInt(key: String, default: Int) = if (contains(key)) defaults.integerForKey(k(key)).toInt() else default
    override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), forKey = k(key))
    override fun getLong(key: String, default: Long) = if (contains(key)) defaults.integerForKey(k(key)) else default
    override fun putLong(key: String, value: Long) = defaults.setInteger(value, forKey = k(key))
    override fun getString(key: String, default: String?) = defaults.stringForKey(k(key)) ?: default
    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = k(key))
    override fun remove(key: String) = defaults.removeObjectForKey(k(key))
}
