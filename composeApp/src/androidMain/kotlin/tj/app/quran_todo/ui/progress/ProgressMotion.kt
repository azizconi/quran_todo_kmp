package tj.app.quran_todo.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import tj.app.quran_todo.ui.motion.LocalReducedMotion

/** Longest ladder of numbers a counter climbs; more steps would only blur the rolling digits. */
private const val MaxCounterSteps = 12
private const val CounterStepMillis = 70L

/**
 * A number that climbs from 0 to [target] in a few eased steps once the screen is on, so
 * `AnimatedCounter` rolls its digits like an odometer. Follows later changes of [target] the same way.
 * Under reduced motion it is [target] at once.
 */
@Composable
internal fun rememberRollingValue(target: Int, startDelayMillis: Long = 0L): Int {
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableIntStateOf(if (reduced) target else 0) }
    val started = remember { BooleanArray(1) }
    LaunchedEffect(target, reduced) {
        if (reduced) {
            shown = target
            return@LaunchedEffect
        }
        if (!started[0]) {
            delay(startDelayMillis)
            started[0] = true
        }
        for (value in counterLadder(from = shown, to = target)) {
            delay(CounterStepMillis)
            shown = value
        }
    }
    return shown
}

/**
 * The values a counter shows on its way from [from] to [to], strictly monotonic and ending exactly on
 * [to]. A short way is taken one by one; a long one in at most [MaxCounterSteps] eased strides (big
 * first, small at the end).
 */
internal fun counterLadder(from: Int, to: Int): List<Int> {
    val distance = abs(to - from)
    if (distance == 0) return emptyList()
    val direction = if (to > from) 1 else -1
    if (distance <= MaxCounterSteps) return List(distance) { from + direction * (it + 1) }
    val values = ArrayList<Int>(MaxCounterSteps)
    for (step in 1..MaxCounterSteps) {
        val t = step / MaxCounterSteps.toFloat()
        val eased = 1f - (1f - t) * (1f - t)
        val value = if (step == MaxCounterSteps) to else from + ((to - from) * eased).roundToInt()
        if (values.isEmpty() || value != values.last()) values += value
    }
    return values
}

/** Milliseconds between two neighbouring cells of the map starting their glint. */
internal const val WaveDelayMillis = 12f

/** How long the glint on one cell lasts. */
internal const val WaveGlintMillis = 600f

/** Length of the whole wave over [cellCount] cells. */
internal fun waveTotalMillis(cellCount: Int): Float = (cellCount - 1).coerceAtLeast(0) * WaveDelayMillis + WaveGlintMillis

/**
 * Strength (0..1) of the golden glint on cell [index] at [timeMillis] after the wave began: each cell
 * lights up and fades once, [WaveDelayMillis] after its left neighbour.
 */
internal fun waveIntensity(timeMillis: Float, index: Int): Float {
    val phase = (timeMillis - index * WaveDelayMillis) / WaveGlintMillis
    return if (phase <= 0f || phase >= 1f) 0f else sin(PI * phase).toFloat()
}
