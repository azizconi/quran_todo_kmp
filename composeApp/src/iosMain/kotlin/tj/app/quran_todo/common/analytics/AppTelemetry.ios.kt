package tj.app.quran_todo.common.analytics

actual object AppTelemetry {
    actual fun initialize() = Unit

    actual fun logScreen(screenName: String, params: Map<String, String>) = Unit

    actual fun logEvent(name: String, params: Map<String, String>) = Unit

    actual fun logError(
        throwable: Throwable,
        context: String?,
        params: Map<String, String>,
    ) = Unit
}
