package tj.app.quran_todo.common.analytics

import android.content.Context
import android.content.pm.ApplicationInfo
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
 *
 * A debuggable build never sends anything to Firebase, so the production project shows only real users: the events go to
 * Logcat instead (tag `AppTelemetry`), and the SDK collection is switched off at start. The manifest placeholders of the debug
 * build type deactivate both SDKs before this code runs.
 */
actual object AppTelemetry {
    private val gate: TelemetryGate by lazy {
        // No application context (local unit tests) counts as debug: nothing can reach Firebase.
        val debuggable = runCatching { isDebuggable(AndroidContextHolder.context) }.getOrDefault(true)
        TelemetryGate(if (debuggable) LogcatTelemetrySink() else FirebaseTelemetrySink())
    }

    @Volatile
    private var initialized = false

    actual fun initialize() {
        if (initialized) return
        val context = AndroidContextHolder.context
        // The choice is stored by UiPrefs; Koin is not started yet, so it is read directly.
        val consent = runCatching { UiPrefs.readAnalyticsEnabled(context) }.getOrDefault(true)
        runCatching { FirebaseApp.initializeApp(context) }
            .onFailure { error -> Log.e(TAG, "Telemetry initialization failed", error) }
        if (isDebuggable(context)) FirebaseTelemetrySink().setCollectionEnabled(false)
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

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private const val TAG = "AppTelemetry"
}

/** Debug builds: the events are written to Logcat and never leave the device. */
private class LogcatTelemetrySink : TelemetrySink {
    override fun setCollectionEnabled(enabled: Boolean) = Unit

    override fun clearStoredData() = Unit

    override fun logEvent(name: String, params: Map<String, String>) {
        Log.d(TAG, "event $name $params")
    }

    override fun logScreen(screenName: String, params: Map<String, String>) {
        Log.d(TAG, "screen $screenName $params")
    }

    override fun recordError(throwable: Throwable, context: String?, params: Map<String, String>) {
        Log.w(TAG, "error context=$context $params", throwable)
    }

    private companion object {
        const val TAG = "AppTelemetry"
    }
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
