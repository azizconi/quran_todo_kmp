package tj.app.quran_todo.common.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryGateTest {
    private class FakeSink : TelemetrySink {
        val collection = mutableListOf<Boolean>()
        var cleared = 0
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val screens = mutableListOf<Pair<String, Map<String, String>>>()
        val errors = mutableListOf<Triple<Throwable, String?, Map<String, String>>>()

        val sentAnything get() = events.isNotEmpty() || screens.isNotEmpty() || errors.isNotEmpty()

        override fun setCollectionEnabled(enabled: Boolean) {
            collection += enabled
        }

        override fun clearStoredData() {
            cleared++
        }

        override fun logEvent(name: String, params: Map<String, String>) {
            events += name to params
        }

        override fun logScreen(screenName: String, params: Map<String, String>) {
            screens += screenName to params
        }

        override fun recordError(throwable: Throwable, context: String?, params: Map<String, String>) {
            errors += Triple(throwable, context, params)
        }
    }

    @Test
    fun nothingIsSentWhileAnalyticsAreOff() {
        val sink = FakeSink()
        val gate = TelemetryGate(sink, initiallyEnabled = false)

        gate.logEvent("app_opened")
        gate.logEvent("home_surah_opened", mapOf("surah_number" to "18"))
        gate.logScreen("reader")
        gate.logError(IllegalStateException("boom"), context = "load_failed", params = mapOf("language" to "ru"))

        assertFalse(sink.sentAnything)
    }

    @Test
    fun eventsFlowAgainWhenAnalyticsAreTurnedBackOn() {
        val sink = FakeSink()
        val gate = TelemetryGate(sink, initiallyEnabled = true)

        gate.setEnabled(false)
        gate.logEvent("while_off")
        gate.logScreen("while_off")
        assertFalse(sink.sentAnything)

        gate.setEnabled(true)
        gate.logEvent("after_on")
        gate.logScreen("home")
        gate.logError(RuntimeException("x"), context = "ctx")

        assertEquals(listOf("after_on"), sink.events.map { it.first })
        assertEquals(listOf("home"), sink.screens.map { it.first })
        assertEquals(1, sink.errors.size)
    }

    @Test
    fun theChoiceIsPassedToTheSdksAndStoredDataIsClearedOnlyWhenSwitchingOff() {
        val sink = FakeSink()
        val gate = TelemetryGate(sink, initiallyEnabled = true)

        gate.setEnabled(false)
        assertEquals(listOf(false), sink.collection)
        assertEquals(1, sink.cleared)

        // Off again: nothing new to clear.
        gate.setEnabled(false)
        assertEquals(1, sink.cleared)

        gate.setEnabled(true)
        assertEquals(listOf(false, false, true), sink.collection)
        assertEquals(1, sink.cleared)
    }

    @Test
    fun restoringTheStoredChoiceAtStartDoesNotClearAnything() {
        val sink = FakeSink()
        val gate = TelemetryGate(sink)

        gate.restore(false)

        assertFalse(gate.enabled)
        assertEquals(listOf(false), sink.collection)
        assertEquals(0, sink.cleared)
        gate.logEvent("ignored")
        assertFalse(sink.sentAnything)

        gate.restore(true)
        assertTrue(gate.enabled)
        gate.logEvent("sent")
        assertEquals(listOf("sent"), sink.events.map { it.first })
    }

    @Test
    fun freeTextParametersNeverLeaveTheDevice() {
        val sink = FakeSink()
        val gate = TelemetryGate(sink)

        gate.logEvent(
            "home_quran_refresh_failed",
            mapOf("message" to "Unexpected error: user typed this", "reason" to "snapshot_missing", "Note" to "my private note"),
        )
        gate.logError(
            IllegalStateException("boom"),
            context = "surah_note_failed",
            params = mapOf("query" to "search text", "surah_number" to "2", "error_message" to "details"),
        )

        assertEquals(mapOf("reason" to "snapshot_missing"), sink.events.single().second)
        assertEquals(mapOf("surah_number" to "2"), sink.errors.single().third)
    }

    @Test
    fun namesAndValuesAreNormalizedAndLimited() {
        val sink = FakeSink()
        val gate = TelemetryGate(sink)

        gate.logEvent("  Home Surah-Opened! ", mapOf("Surah Number" to "x".repeat(300)))
        gate.logScreen("3rd screen")
        gate.logError(RuntimeException(), context = "Context With Spaces")

        val (name, params) = sink.events.single()
        assertEquals("home_surah_opened", name)
        assertEquals(setOf("surah_number"), params.keys)
        assertEquals(MaxTelemetryParamValueLength, params.getValue("surah_number").length)
        assertEquals("e_3rd_screen", sink.screens.single().first)
        assertEquals("context_with_spaces", sink.errors.single().second)
    }

    @Test
    fun noMoreThanTheAllowedNumberOfParametersIsSent() {
        val sink = FakeSink()
        val gate = TelemetryGate(sink)

        gate.logEvent("many", (1..40).associate { "key_$it" to "v" })

        assertEquals(MaxTelemetryParams, sink.events.single().second.size)
    }

    @Test
    fun blankNamesFallBack() {
        assertEquals("event", normalizeTelemetryName("   ", fallback = "event"))
        assertEquals("param", normalizeTelemetryName("!!!", fallback = "param"))
        assertEquals(MaxTelemetryNameLength, normalizeTelemetryName("a".repeat(100), fallback = "x").length)
    }
}
