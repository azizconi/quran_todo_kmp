package tj.app.quran_todo.common.sync

expect object CloudSyncStorage {
    fun saveSnapshot(json: String)
    fun loadSnapshot(): String?
    fun clearSnapshot()
    fun providerLabel(): String
}
