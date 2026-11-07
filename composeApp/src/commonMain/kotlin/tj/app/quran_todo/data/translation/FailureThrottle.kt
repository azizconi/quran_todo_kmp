package tj.app.quran_todo.data.translation

import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remembers failed requests: the same [key] is not requested again for [windowMs] after a failure, unless
 * the person explicitly asks to retry. Keeps a surah that cannot be loaded from hammering the network every
 * time the reader recomposes or the language is reported again.
 */
class FailureThrottle(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long = { getTimeMillis() },
) {
    private val mutex = Mutex()
    private val failedAt = HashMap<String, Long>()

    /** True while [key] failed less than [windowMs] ago. */
    suspend fun isBlocked(key: String): Boolean = mutex.withLock {
        val at = failedAt[key] ?: return@withLock false
        val elapsed = clock() - at
        elapsed in 0 until windowMs
    }

    suspend fun markFailed(key: String) = mutex.withLock { failedAt[key] = clock() }

    suspend fun clear(key: String) = mutex.withLock { failedAt.remove(key) }

    companion object {
        const val DEFAULT_WINDOW_MS = 30_000L
    }
}
