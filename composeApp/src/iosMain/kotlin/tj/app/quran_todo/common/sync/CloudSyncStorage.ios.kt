package tj.app.quran_todo.common.sync

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUbiquitousKeyValueStore

private const val KEY_SNAPSHOT = "snapshot_json"

actual object CloudSyncStorage {
    private val ubiquitousStore = NSUbiquitousKeyValueStore.defaultStore
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun saveSnapshot(json: String) {
        ubiquitousStore.setString(json, forKey = KEY_SNAPSHOT)
        ubiquitousStore.synchronize()
        defaults.setObject(json, forKey = KEY_SNAPSHOT)
    }

    actual fun loadSnapshot(): String? {
        val fromCloud = ubiquitousStore.stringForKey(KEY_SNAPSHOT)
        if (!fromCloud.isNullOrBlank()) return fromCloud
        return defaults.stringForKey(KEY_SNAPSHOT)
    }

    actual fun clearSnapshot() {
        ubiquitousStore.removeObjectForKey(KEY_SNAPSHOT)
        ubiquitousStore.synchronize()
        defaults.removeObjectForKey(KEY_SNAPSHOT)
    }

    actual fun providerLabel(): String = "iCloud"
}
