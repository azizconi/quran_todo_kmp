package tj.app.quran_todo.presentation.review

import kotlin.math.roundToInt
import tj.app.quran_todo.common.settings.ReviewMemoryState

object Sm2Scheduler {
    fun initialState(): ReviewMemoryState = ReviewMemoryState()

    fun nextState(
        current: ReviewMemoryState?,
        quality: ReviewQuality,
    ): ReviewMemoryState {
        val safeCurrent = current ?: initialState()
        val q = when (quality) {
            ReviewQuality.HARD -> 2
            ReviewQuality.GOOD -> 4
            ReviewQuality.EASY -> 5
        }

        val nextEasiness = (
            safeCurrent.easiness + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
            ).coerceAtLeast(1.3f)

        if (q < 3) {
            return ReviewMemoryState(
                repetitions = 0,
                intervalDays = 1,
                easiness = nextEasiness
            )
        }

        val nextRepetitions = safeCurrent.repetitions + 1
        val baseInterval = when (safeCurrent.repetitions) {
            0 -> 1
            1 -> 6
            else -> (safeCurrent.intervalDays * safeCurrent.easiness).roundToInt().coerceAtLeast(1)
        }

        val boostedInterval = if (quality == ReviewQuality.EASY && nextRepetitions > 2) {
            (baseInterval * 1.15f).roundToInt().coerceAtLeast(baseInterval)
        } else {
            baseInterval
        }

        return ReviewMemoryState(
            repetitions = nextRepetitions,
            intervalDays = boostedInterval,
            easiness = nextEasiness
        )
    }
}
