package tj.app.quran_todo.common.audio

expect class AudioCache() {
    suspend fun getOrFetch(url: String, cacheKey: String): String?
    suspend fun prefetch(url: String, cacheKey: String)
    fun isCached(cacheKey: String): Boolean
    fun clear(cacheKey: String)
}
