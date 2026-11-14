package tj.app.quran_todo.ui.platform

/**
 * A small synchronous key-value store: the one thing [tj.app.quran_todo.ui.state.UiPrefs] and the other preference classes
 * of the UI layer need from the platform.
 *
 * Writes must be visible to the following reads at once and reach the disk soon (Android: `SharedPreferences.apply()`).
 *
 * iOS implementation: `UserDefaultsStore` (NSUserDefaults; booleans, ints, longs and strings map to the typed NSUserDefaults calls,
 * [contains] is `objectForKey(key) != nil`). A store that the widget must read is created on the App Group suite. The keys are the same
 * strings as on Android.
 */
interface KeyValueStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** A [KeyValueStore] in memory: tests, previews and the default of a platform that has no storage yet. */
class MemoryKeyValueStore : KeyValueStore {
    private val values = HashMap<String, Any>()

    override fun contains(key: String) = key in values
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun getString(key: String, default: String?) = values[key] as? String ?: default
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
