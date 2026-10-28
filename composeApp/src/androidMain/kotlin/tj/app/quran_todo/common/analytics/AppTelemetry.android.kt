package tj.app.quran_todo.common.analytics

import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Firebase Analytics and Crashlytics behind the person's choice ("Anonymous statistics and crash
 * reports" in the settings, [UiPrefs.analyticsEnabled], on by default). Every call goes through a
 * [TelemetryGate]: while the choice is off nothing is sent and the SDK collection is switched off.
 */
actual object AppTelemetry {
    private val gate = TelemetryGate(FirebaseTelemetrySink())

    @Volatile
    private var initialized = false

    actual fun initialize() {
        if (initialized) return
        val context = AndroidContextHolder.context
        // The choice is stored by UiPrefs; Koin is not started yet, so it is read directly.
        val consent = runCatching { UiPrefs.readAnalyticsEnabled(context) }.getOrDefault(true)
        runCatching { FirebaseApp.initializeApp(context) }
            .onFailure { error -> Log.e(TAG, "Telemetry initialization failed", error) }
        // The stored choice, not a change: the gate follows it before anything can be logged.
        gate.restore(consent)
        initialized = true
        logEvent("telemetry_initialized")
    }

    /**
     * Android only: applies the person's choice. Off stops all collection at once (events, crash
     * reports) and clears what the analytics SDK stored on the device.
     */
    fun setEnabled(enabled: Boolean) {
        runCatching { gate.setEnabled(enabled) }
            .onFailure { error -> Log.e(TAG, "Could not change telemetry collection", error) }
    }

    actual fun logScreen(screenName: String, params: Map<String, String>) {
        gate.logScreen(screenName, params)
    }

    actual fun logEvent(name: String, params: Map<String, String>) {
        gate.logEvent(name, params)
    }

    actual fun logError(
        throwable: Throwable,
        context: String?,
        params: Map<String, String>,
    ) {
        gate.logError(throwable, context, params)
    }

    private const val TAG = "AppTelemetry"
}

/** The real destination: Firebase. Every name and value reaching it was cleaned by the [TelemetryGate]. */
private class FirebaseTelemetrySink : TelemetrySink {
    override fun setCollectionEnabled(enabled: Boolean) {
        runCatching {
            analytics()?.setAnalyticsCollectionEnabled(enabled)
            crashlytics()?.setCrashlyticsCollectionEnabled(enabled)
        }.onFailure { error -> Log.e(TAG, "Setting collection failed", error) }
    }

    override fun clearStoredData() {
        runCatching { analytics()?.resetAnalyticsData() }
            .onFailure { error -> Log.e(TAG, "Clearing analytics data failed", error) }
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        runCatching { analytics()?.logEvent(name, bundleOf(params)) }
            .onFailure { error -> Log.e(TAG, "Event failed: $name", error) }
    }

    override fun logScreen(screenName: String, params: Map<String, String>) {
        runCatching {
            val bundle = bundleOf(params).apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "Compose")
            }
            analytics()?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        }.onFailure { error -> Log.e(TAG, "Screen event failed: $screenName", error) }
    }

    override fun recordError(throwable: Throwable, context: String?, params: Map<String, String>) {
        runCatching {
            crashlytics()?.let { crash ->
                context?.let { crash.log("context=$it") }
                params.forEach { (key, value) -> crash.setCustomKey(key, value) }
                crash.recordException(throwable)
            }
            logEvent(
                name = "non_fatal_error",
                params = mapOf(
                    "context" to (context ?: "unknown"),
                    "type" to (throwable::class.simpleName ?: "Throwable"),
                ) + params,
            )
        }.onFailure { error -> Log.e(TAG, "Error event failed", error) }
    }

    private fun bundleOf(params: Map<String, String>): Bundle = Bundle().apply {
        params.forEach { (key, value) -> putString(key, value) }
    }

    private fun analytics(): FirebaseAnalytics? =
        runCatching { FirebaseAnalytics.getInstance(AndroidContextHolder.context) }.getOrNull()

    private fun crashlytics(): FirebaseCrashlytics? =
        runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()

    private companion object {
        const val TAG = "AppTelemetry"
    }
}
