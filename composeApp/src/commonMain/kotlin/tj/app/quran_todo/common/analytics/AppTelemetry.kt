package tj.app.quran_todo.common.analytics

expect object AppTelemetry {
    fun initialize()
    /** The person's analytics choice: collection starts or stops at once. */
    fun setEnabled(enabled: Boolean)
    fun logScreen(screenName: String, params: Map<String, String> = emptyMap())
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
    fun logError(
        throwable: Throwable,
        context: String? = null,
        params: Map<String, String> = emptyMap(),
    )
}
