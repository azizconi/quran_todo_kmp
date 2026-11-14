package tj.app.quran_todo.ui.platform

import android.content.Context
import android.content.SharedPreferences

/** [KeyValueStore] over one SharedPreferences file: the files and keys of the app are the same as before the move to common code. */
class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    constructor(context: Context, fileName: String) :
        this(context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE))

    override fun contains(key: String) = prefs.contains(key)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun getString(key: String, default: String?) = prefs.getString(key, default)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}
