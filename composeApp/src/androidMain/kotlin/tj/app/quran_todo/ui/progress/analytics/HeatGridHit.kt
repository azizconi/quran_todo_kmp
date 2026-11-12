package tj.app.quran_todo.ui.progress.analytics

import kotlin.math.hypot

/**
 * Pure helpers of the year calendar: which day a finger meant, and which day a TalkBack step lands on.
 * A cell is only about 5 dp wide, so a tap picks the nearest day within [tolerancePx] instead of needing an exact hit.
 */
internal object HeatGridHit {
    /** The epoch day nearest to the point ([x], [y]) in the grid drawn with [cell] side, [gap] between cells and [monthRow] on top; null when no day is within [tolerancePx]. */
    fun nearestDay(heat: HeatYear, x: Float, y: Float, cell: Float, gap: Float, monthRow: Float, tolerancePx: Float): Int? {
        if (cell <= 0f) return null
        val pitch = cell + gap
        val week = (x / pitch).toInt()
        val weekday = ((y - monthRow) / pitch).toInt()
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        for (w in week - 1..week + 1) {
            val days = heat.weeks.getOrNull(w) ?: continue
            for (d in weekday - 1..weekday + 1) {
                val day = days.getOrNull(d) ?: continue
                val dx = w * pitch + cell / 2f - x
                val dy = monthRow + d * pitch + cell / 2f - y
                val distance = hypot(dx, dy)
                if (distance <= tolerancePx + cell / 2f && distance < bestDistance) {
                    best = day.epochDay
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /** The day a TalkBack step of [delta] days from [selected] lands on, kept within the year's days; with nothing selected, the latest day. Null for an empty year. */
    fun step(heat: HeatYear, selected: Int?, delta: Int): Int? {
        val days = heat.weeks.asSequence().flatten().filterNotNull().map { it.epochDay }
        val first = days.minOrNull() ?: return null
        val last = days.maxOrNull() ?: return null
        return if (selected == null) last else (selected + delta).coerceIn(first, last)
    }
}
