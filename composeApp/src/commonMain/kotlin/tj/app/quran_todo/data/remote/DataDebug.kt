package tj.app.quran_todo.data.remote

/**
 * Debug switches decided by the platform DI (Android: the app is debuggable; iOS: a debug binary).
 * In a release build [enabled] is false: the HTTP client logs nothing and [log] is never called.
 */
class DataDebug(
    val enabled: Boolean,
    private val sink: (String) -> Unit = {},
) {
    /** Writes [message] to the platform log in debug builds only; the lambda is not even evaluated in release. */
    inline fun log(message: () -> String) {
        if (enabled) write(message())
    }

    fun write(message: String) = sink(message)

    companion object {
        val Off = DataDebug(enabled = false)
    }
}
