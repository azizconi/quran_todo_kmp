package tj.app.quran_todo.ui.platform

import platform.Foundation.NSRecursiveLock

actual class SyncLock {
    private val lock = NSRecursiveLock()
    actual fun enter() = lock.lock()
    actual fun exit() = lock.unlock()
}
