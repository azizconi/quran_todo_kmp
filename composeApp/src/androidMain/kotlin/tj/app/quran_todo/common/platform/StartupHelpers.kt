package tj.app.quran_todo.common.platform

import kotlinx.coroutines.CompletableDeferred

/**
 * A value that is worked out once, usually on a worker thread at the start of the app, and read later by code that must not wait for the
 * disk (the splash screen, the first composition). [get] is the safe way: the value if it is there, otherwise it is worked out on the
 * calling thread, as before. [peek] and [await] never work anything out.
 */
internal class OnceResolved<T : Any>(private val compute: () -> T) {
    private val lock = Any()

    @Volatile
    private var value: T? = null
    private val done = CompletableDeferred<T>()

    val isReady: Boolean get() = value != null

    fun peek(): T? = value

    fun get(): T = value ?: synchronized(lock) {
        value ?: compute().also {
            value = it
            done.complete(it)
        }
    }

    /** Works the value out on the calling thread (a worker) and ignores a failure: [get] then tries again for whoever needs it. */
    fun resolveQuietly() {
        runCatching { get() }
    }

    /** Waits, without blocking a thread, until some thread has worked the value out. */
    suspend fun await(): T = done.await()
}

/**
 * Calls that must wait until something is ready (the stored telemetry choice, read on a worker thread): [submit] runs a call at once when
 * the queue is open, otherwise keeps it (at most [limit] of them, the rest is dropped) and [open] runs them in order.
 */
internal class PendingCalls(private val limit: Int = DEFAULT_LIMIT) {
    private val lock = Any()
    private var opened = false
    private val queue = ArrayList<() -> Unit>()

    fun submit(call: () -> Unit) {
        val runNow = synchronized(lock) {
            if (!opened && queue.size < limit) queue.add(call)
            opened
        }
        if (runNow) call()
    }

    fun open() {
        val waiting = synchronized(lock) {
            opened = true
            ArrayList(queue).also { queue.clear() }
        }
        waiting.forEach { call -> runCatching(call) }
    }

    private companion object {
        const val DEFAULT_LIMIT = 64
    }
}
