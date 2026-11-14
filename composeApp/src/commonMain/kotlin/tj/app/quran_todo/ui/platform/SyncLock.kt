package tj.app.quran_todo.ui.platform

/** A reentrant lock for code that is called from several threads (the queue of the downloads, the stores). */
expect class SyncLock() {
    fun enter()
    fun exit()
}

inline fun <T> SyncLock.withLock(block: () -> T): T {
    enter()
    try {
        return block()
    } finally {
        exit()
    }
}
