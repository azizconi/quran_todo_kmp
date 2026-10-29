package tj.app.quran_todo.common.analytics

/** Where telemetry finally goes. The app uses Firebase; tests use a fake. */
internal interface TelemetrySink {
    /** Turns the collection of the underlying SDKs on or off. */
    fun setCollectionEnabled(enabled: Boolean)

    /** Called on a switch from on to off: forget what the SDK stored on the device. */
    fun clearStoredData()

    fun logEvent(name: String, params: Map<String, String>)

    fun logScreen(screenName: String, params: Map<String, String>)

    fun recordError(throwable: Throwable, context: String?, params: Map<String, String>)
}

/**
 * The consent gate in front of the telemetry [sink]. While [enabled] is false nothing reaches the sink
 * (no events, screens, error keys or non-fatal reports); the names and parameters that do pass are
 * cleaned first, so no free text leaves the device even if shared code puts it in a parameter.
 */
internal class TelemetryGate(
    private val sink: TelemetrySink,
    initiallyEnabled: Boolean = true,
) {
    @Volatile
    var enabled: Boolean = initiallyEnabled
        private set

    /** Applies the person's choice to the gate and to the SDKs behind the sink. */
    fun setEnabled(value: Boolean) {
        val wasEnabled = enabled
        enabled = value
        sink.setCollectionEnabled(value)
        if (wasEnabled && !value) sink.clearStoredData()
    }

    /** Takes the stored choice at app start: applied to the SDKs, but not a change, so nothing is cleared. */
    fun restore(value: Boolean) {
        enabled = value
        sink.setCollectionEnabled(value)
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (!enabled) return
        sink.logEvent(normalizeTelemetryName(name, fallback = "event"), sanitizeTelemetryParams(params))
    }

    fun logScreen(screenName: String, params: Map<String, String> = emptyMap()) {
        if (!enabled) return
        sink.logScreen(normalizeTelemetryName(screenName, fallback = "screen"), sanitizeTelemetryParams(params))
    }

    fun logError(throwable: Throwable, context: String? = null, params: Map<String, String> = emptyMap()) {
        if (!enabled) return
        sink.recordError(
            throwable = throwable,
            context = context?.let { normalizeTelemetryName(it, fallback = "unknown") },
            params = sanitizeTelemetryParams(params),
        )
    }
}

internal const val MaxTelemetryNameLength = 40
internal const val MaxTelemetryParamValueLength = 100
internal const val MaxTelemetryParams = 25

/**
 * Parameter keys that carry free text: error messages, notes, search input. Their values never leave
 * the device, whatever the shared code passes (it sends an error message with `home_quran_refresh_failed`).
 */
internal val FreeTextTelemetryKeys = setOf(
    "message", "error", "error_message", "exception",
    "note", "notes", "comment", "text", "body", "title",
    "query", "search", "search_query",
)

internal fun sanitizeTelemetryParams(params: Map<String, String>): Map<String, String> {
    val clean = LinkedHashMap<String, String>()
    for ((rawKey, value) in params) {
        if (clean.size >= MaxTelemetryParams) break
        val key = normalizeTelemetryName(rawKey, fallback = "param")
        if (key in FreeTextTelemetryKeys) continue
        clean[key] = value.take(MaxTelemetryParamValueLength)
    }
    return clean
}

private val InvalidNameRegex = Regex("[^a-zA-Z0-9_]")
private val RepeatedUnderscoreRegex = Regex("_+")

/** Firebase names: letters, digits and underscores, not starting with a digit, at most 40 characters. */
internal fun normalizeTelemetryName(raw: String, fallback: String): String {
    val cleaned = raw
        .trim()
        .lowercase()
        .replace(InvalidNameRegex, "_")
        .replace(RepeatedUnderscoreRegex, "_")
        .trim('_')
        .ifBlank { fallback }
    val withPrefix = if (cleaned.first().isDigit()) "e_$cleaned" else cleaned
    return withPrefix.take(MaxTelemetryNameLength)
}
