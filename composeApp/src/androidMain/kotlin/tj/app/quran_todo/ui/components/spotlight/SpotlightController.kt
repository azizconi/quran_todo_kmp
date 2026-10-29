package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import tj.app.quran_todo.common.analytics.AppTelemetry

/** The tour that is on screen: the steps that could be shown when it started, and the one it is at. */
@Immutable
data class SpotlightRun(
    val tour: SpotlightTour,
    val steps: List<SpotlightStep>,
    val index: Int = 0,
) {
    val step: SpotlightStep get() = steps[index]
    val isLast: Boolean get() = index == steps.lastIndex
    val total: Int get() = steps.size
}

/** Where the events go. Only tour ids, results and numbers: no free text. */
fun interface SpotlightAnalytics {
    fun log(name: String, params: Map<String, String>)

    companion object {
        val Telemetry = SpotlightAnalytics { name, params -> AppTelemetry.logEvent(name, params) }
    }
}

/** A tour a screen has asked for. [exhausted]: the start was tried and nothing was on screen; the screen has to ask again. */
internal class SpotlightRequest(val tour: SpotlightTour) {
    var exhausted by mutableStateOf(false)

    /** How many composables ask for it right now (a card that leaves and the one that comes in both do, for a moment). */
    var holders = 1
}

/**
 * The state of the tips: which tours screens ask for, the conditions that hold them back and the tour that is running.
 * The app root owns one ([SpotlightHost] provides it as [LocalSpotlight]).
 *
 * A screen calls `SpotlightTourEffect(tour, ready)`; when the screen is on top and still, no sheet or dialog is above it
 * and the first target is on screen, the host starts the tour. A tour is shown once: it is marked when it ends or is
 * skipped ([SpotlightFlags]), never when it starts.
 */
@Stable
class SpotlightController(
    private val flags: SpotlightFlags,
    private val analytics: SpotlightAnalytics = SpotlightAnalytics.Telemetry,
) {
    val registry = SpotlightRegistry()

    private var route: String? by mutableStateOf(null)
    private var navigationIdle: Boolean by mutableStateOf(false)
    private var sheetsOpen: Int by mutableIntStateOf(0)

    /** Set by the host from the window focus. Dialogs, menus and sheets take the focus from the window of the app. */
    var windowFocused: Boolean by mutableStateOf(true)

    private val requests = mutableStateListOf<SpotlightRequest>()
    private var shownLogged = false

    /** The tour on screen, or null. */
    var run: SpotlightRun? by mutableStateOf(null)
        private set

    // -- What the app tells -------------------------------------------------------------------------------------

    /** The root reports the screen on top and whether it is fully shown (its transition is over). */
    fun onNavigation(route: String?, idle: Boolean) {
        if (this.route != route) this.route = route
        if (navigationIdle != idle) navigationIdle = idle
    }

    /** A [tj.app.quran_todo.ui.components.QhSheet] came on screen: no tour starts above it. */
    fun sheetShown() {
        sheetsOpen++
    }

    fun sheetHidden() {
        sheetsOpen = (sheetsOpen - 1).coerceAtLeast(0)
    }

    // -- What the screens ask -----------------------------------------------------------------------------------

    /** The screen is ready for [tour]. Several composables may ask for the same tour; it is wanted until all of them [withdraw]. */
    fun request(tour: SpotlightTour) {
        val existing = requests.firstOrNull { it.tour.id == tour.id }
        if (existing != null) existing.holders++ else requests += SpotlightRequest(tour)
    }

    /** The screen is not ready any more (or left). A tour of it that is running is dropped without being marked as shown. */
    fun withdraw(tour: SpotlightTour) {
        val existing = requests.firstOrNull { it.tour.id == tour.id }
        if (existing != null) {
            existing.holders--
            if (existing.holders > 0) return
            requests.remove(existing)
        }
        if (run?.tour?.id == tour.id) run = null
    }

    /** "Show tips again" in the settings. */
    fun resetAll() {
        flags.resetAll()
        requests.forEach { it.exhausted = false }
    }

    // -- What the host does -------------------------------------------------------------------------------------

    internal fun gateState(): SpotlightGateState = SpotlightGateState(
        route = route,
        navigationIdle = navigationIdle,
        sheetsOpen = sheetsOpen,
        windowFocused = windowFocused,
        tourRunning = run != null,
    )

    /** The first requested tour that may start now, or null. Read it in a snapshot flow: it follows every input. */
    internal fun nextCandidate(): SpotlightTour? {
        val state = gateState()
        return requests.firstOrNull { !it.exhausted && !flags.isSeen(it.tour) && SpotlightGate.canStart(it.tour, state) }?.tour
    }

    /** Nothing of [tour] was on screen: leave it for the next time the screen asks. */
    internal fun markExhausted(tour: SpotlightTour) {
        requests.firstOrNull { it.tour.id == tour.id }?.exhausted = true
    }

    /** Starts [tour] with [steps] (the ones that were on screen). With no step nothing starts and nothing is marked. */
    internal fun start(tour: SpotlightTour, steps: List<SpotlightStep>): Boolean {
        if (steps.isEmpty() || run != null || flags.isSeen(tour)) return false
        shownLogged = false
        run = SpotlightRun(tour, steps)
        return true
    }

    /** The host put the step at [index] on screen. The first one counts as "the tour was shown". */
    internal fun stepShown(index: Int) {
        val current = run ?: return
        if (current.index != index || shownLogged) return
        shownLogged = true
        analytics.log("spotlight_shown", mapOf("tour" to current.tour.id))
    }

    /** The next step, or the end of the tour after the last one. */
    fun advance() {
        val current = run ?: return
        if (current.isLast) finish(current, SpotlightResult.Done) else run = current.copy(index = current.index + 1)
    }

    /** "Skip" and the system "Back": the tour ends and counts as shown. */
    fun skip() {
        val current = run ?: return
        finish(current, SpotlightResult.Skipped)
    }

    /**
     * The target of the current step is gone (the screen changed under the tour): on to the next step. When that was the
     * last one the tour ends as done if something was shown, and leaves no mark if nothing was.
     */
    internal fun dropStep() {
        val current = run ?: return
        when {
            !current.isLast -> run = current.copy(index = current.index + 1)
            shownLogged -> finish(current, SpotlightResult.Done)
            else -> {
                run = null
                markExhausted(current.tour)
            }
        }
    }

    private fun finish(current: SpotlightRun, result: SpotlightResult) {
        flags.markSeen(current.tour)
        requests.removeAll { it.tour.id == current.tour.id }
        run = null
        if (shownLogged) {
            analytics.log(
                "spotlight_finished",
                mapOf("tour" to current.tour.id, "result" to result.value, "step" to (current.index + 1).toString()),
            )
        }
    }

    companion object {
        /** What screens get without a host (previews, the catalog): flags in memory, never starts anything. */
        val Inert: SpotlightController by lazy { SpotlightController(SpotlightFlags(InMemorySpotlightStore()), SpotlightAnalytics { _, _ -> }) }
    }
}

private enum class SpotlightResult(val value: String) {
    Done("done"),
    Skipped("skipped"),
}

/** [SpotlightStore] that forgets everything on exit: for tests and for the inert controller. */
class InMemorySpotlightStore(initial: Set<String> = emptySet()) : SpotlightStore {
    private val ids = initial.toMutableSet()

    override fun read(): Set<String> = ids.toSet()

    override fun add(tourId: String) {
        ids += tourId
    }

    override fun clear() {
        ids.clear()
    }
}

/** The controller of the app. Without a [SpotlightHost] above, an inert one: nothing ever starts. */
val LocalSpotlight = staticCompositionLocalOf { SpotlightController.Inert }

@Composable
fun rememberSpotlightController(): SpotlightController {
    val context = LocalContext.current.applicationContext
    return remember(context) { SpotlightController(SpotlightFlags(SharedPreferencesSpotlightStore(context))) }
}
