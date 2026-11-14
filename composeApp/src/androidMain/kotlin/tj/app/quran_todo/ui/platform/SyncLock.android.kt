package tj.app.quran_todo.ui.platform

import java.util.concurrent.locks.ReentrantLock

actual class SyncLock {
    private val lock = ReentrantLock()
    actual fun enter() = lock.lock()
    actual fun exit() = lock.unlock()
}
