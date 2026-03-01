package tj.app.quran_todo.common.sync

import android.content.Context
import tj.app.quran_todo.common.platform.AndroidContextHolder

private const val PREFS_NAME = "quran_todo_cloud_sync"
private const val KEY_SNAPSHOT = "snapshot_json"

actual object CloudSyncStorage {
    private fun prefs() =
        AndroidContextHolder.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun saveSnapshot(json: String) {
        prefs().edit().putString(KEY_SNAPSHOT, json).apply()
    }

    actual fun loadSnapshot(): String? =
        prefs().getString(KEY_SNAPSHOT, null)

    actual fun clearSnapshot() {
        prefs().edit().remove(KEY_SNAPSHOT).apply()
    }

    actual fun providerLabel(): String = "Google backup"
}
