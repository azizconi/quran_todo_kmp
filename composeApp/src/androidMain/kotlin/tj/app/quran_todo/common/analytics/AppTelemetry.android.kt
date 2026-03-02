package tj.app.quran_todo.common.analytics

import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import tj.app.quran_todo.common.platform.AndroidContextHolder

actual object AppTelemetry {
    @Volatile
    private var initialized = false

    actual fun initialize() {
        if (initialized) return
        runCatching {
            FirebaseApp.initializeApp(AndroidContextHolder.context)
            analytics()?.setAnalyticsCollectionEnabled(true)
            crashlytics()?.setCrashlyticsCollectionEnabled(true)
            initialized = true
            logEvent("telemetry_initialized")
        }.onFailure { error ->
            Log.e(tag, "Telemetry initialization failed", error)
        }
    }

    actual fun logScreen(screenName: String, params: Map<String, String>) {
        val normalizedScreen = normalizeName(screenName, fallback = "screen")
        runCatching {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, normalizedScreen)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "Compose")
                params
                    .entries
                    .take(maxParams)
                    .forEach { (key, value) ->
                        putString(
                            normalizeName(key, fallback = "param"),
                            value.take(maxParamValueLength)
                        )
                    }
            }
            analytics()?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        }.onFailure { error ->
            Log.e(tag, "Screen event failed: $screenName", error)
        }
    }

    actual fun logEvent(name: String, params: Map<String, String>) {
        val eventName = normalizeName(name, fallback = "event")
        runCatching {
            analytics()?.logEvent(eventName, bundleFrom(params))
        }.onFailure { error ->
            Log.e(tag, "Event failed: $name", error)
        }
    }

    actual fun logError(
        throwable: Throwable,
        context: String?,
        params: Map<String, String>,
    ) {
        runCatching {
            crashlytics()?.let { crash ->
                context?.let { crash.log("context=$it") }
                params
                    .entries
                    .take(maxParams)
                    .forEach { (key, value) ->
                        crash.setCustomKey(
                            normalizeName(key, fallback = "key"),
                            value.take(maxParamValueLength)
                        )
                    }
                crash.recordException(throwable)
            }
            logEvent(
                name = "non_fatal_error",
                params = mapOf(
                    "context" to (context ?: "unknown"),
                    "type" to (throwable::class.simpleName ?: "Throwable")
                ) + params
            )
        }.onFailure { error ->
            Log.e(tag, "Error event failed", error)
        }
    }

    private fun bundleFrom(params: Map<String, String>): Bundle {
        val bundle = Bundle()
        params
            .entries
            .take(maxParams)
            .forEach { (key, value) ->
                bundle.putString(
                    normalizeName(key, fallback = "param"),
                    value.take(maxParamValueLength)
                )
            }
        return bundle
    }

    private fun analytics(): FirebaseAnalytics? {
        return runCatching { FirebaseAnalytics.getInstance(AndroidContextHolder.context) }.getOrNull()
    }

    private fun crashlytics(): FirebaseCrashlytics? {
        return runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
    }

    private fun normalizeName(raw: String, fallback: String): String {
        val cleaned = raw
            .trim()
            .lowercase()
            .replace(invalidNameRegex, "_")
            .replace(doubleUnderscoreRegex, "_")
            .trim('_')
            .ifBlank { fallback }
        val withPrefix = if (cleaned.firstOrNull()?.isDigit() == true) {
            "e_$cleaned"
        } else {
            cleaned
        }
        return withPrefix.take(maxNameLength)
    }

    private const val tag = "AppTelemetry"
    private const val maxNameLength = 40
    private const val maxParamValueLength = 100
    private const val maxParams = 25
    private val invalidNameRegex = Regex("[^a-zA-Z0-9_]")
    private val doubleUnderscoreRegex = Regex("_+")
}
