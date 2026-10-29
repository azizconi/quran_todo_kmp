package tj.app.quran_todo.ui.onboarding

import kotlinx.coroutines.CancellationException
import tj.app.quran_todo.hifz.HifzRepository

/** Everything the last screen writes. [plan] is null on the "Read and listen" path: no plan is made. */
internal data class OnboardingCommit(
    val known: List<Int>,
    val plan: PlanToSave?,
)

/** What came of pressing the last button. */
internal sealed interface FinishResult {
    /** All written (or, for a repeated press, was written before): the screen leaves. */
    data object Done : FinishResult

    /** A write failed: nothing is marked as finished, the screen stays and the button works again. */
    data class Failed(val error: Throwable) : FinishResult

    /** The first press is still writing: this one does nothing. */
    data object Busy : FinishResult
}

/**
 * The only place the onboarding writes the memorization progress (the model rule: the UI writes only through [HifzRepository]),
 * and only from the last screen. In one coroutine, in this order:
 *
 *  1. `markKnown` for each known surah (a whole surah; the ones already covered are left alone by the repository);
 *  2. `setPlan`;
 *  3. the "onboarding passed" flag ([markPassed]);
 *
 * and then the caller navigates. A failure at any point is reported and nothing after it runs; pressing again repeats the whole
 * sequence, which is safe because every step is idempotent. Once it has succeeded, further presses return [FinishResult.Done]
 * without writing again, and a press during a running write returns [FinishResult.Busy].
 */
internal class OnboardingFinisher(
    private val hifz: HifzRepository,
    private val markPassed: () -> Unit,
) {
    private var running = false
    private var finished = false

    suspend fun finish(commit: OnboardingCommit, today: Int): FinishResult {
        if (finished) return FinishResult.Done
        if (running) return FinishResult.Busy
        running = true
        return try {
            commit.known.forEach { surah -> hifz.markKnown(surah = surah, today = today) }
            commit.plan?.let { plan ->
                hifz.setPlan(
                    scope = plan.scope,
                    surahOrder = plan.surahOrder,
                    dailyNewAyahs = plan.dailyNewAyahs,
                    targetEpochDay = plan.targetEpochDay,
                    manzilDays = plan.manzilDays,
                    today = today,
                )
            }
            markPassed()
            finished = true
            FinishResult.Done
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            FinishResult.Failed(error)
        } finally {
            running = false
        }
    }
}
