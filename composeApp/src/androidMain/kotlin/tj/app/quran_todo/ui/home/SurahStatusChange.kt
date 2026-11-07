package tj.app.quran_todo.ui.home

import tj.app.quran_todo.hifz.model.SurahHifzStatus

/** The three choices of the surah sheet. */
enum class SurahTarget { NotStarted, Learning, Known }

/** What one call of the repository does to apply a choice, in order. */
internal enum class StatusStep {
    /** `HifzRepository.forget(surah)`: the whole surah leaves the passages. */
    Forget,

    /** `HifzRepository.learnNext(surah)`: the surah goes to the front of the plan. */
    LearnNext,

    /** `HifzRepository.markKnown(surah)`: the uncovered ayahs become OLD passages. */
    MarkKnown,

    /** Takes the surah out of the queue of the plan (a plan of the other surahs stays), so that it does not come back as "learning". */
    LeaveThePlan,
}

/**
 * How a choice in the surah sheet turns into repository calls (the same meaning as in the reader's sheet):
 *
 * - "Know by heart" (`markKnown`): the ayahs that are not covered become OLD passages; the covered ones stay as they are.
 * - "Memorizing" (`learnNext`): the surah goes to the front of the plan. A memorized surah is first taken out of the
 *   passages (`forget`), otherwise it would stay memorized.
 * - "Not started" (`forget`): the surah leaves the passages and the queue of the plan, so that it is not "learning" through
 *   the plan either.
 *
 * Taking covered ayahs out of the passages deletes their schedule: [lostAyahs] says how many, and the screen asks first.
 */
internal object SurahStatusChange {
    /** The status the choice makes, as it will be shown (null = not started). */
    fun resulting(target: SurahTarget): SurahHifzStatus? = when (target) {
        SurahTarget.NotStarted -> null
        SurahTarget.Learning -> SurahHifzStatus.LEARNING
        SurahTarget.Known -> SurahHifzStatus.LEARNED
    }

    /** The choice that shows [status] in the sheet. */
    fun targetOf(status: SurahHifzStatus?): SurahTarget = when (status) {
        null -> SurahTarget.NotStarted
        SurahHifzStatus.LEARNING -> SurahTarget.Learning
        SurahHifzStatus.LEARNED -> SurahTarget.Known
    }

    /** Memorized ayahs that the choice takes out of the repetitions; 0 means no confirmation is needed. */
    fun lostAyahs(current: SurahHifzStatus?, coveredAyahs: Int, target: SurahTarget): Int = when {
        target == SurahTarget.NotStarted -> coveredAyahs
        target == SurahTarget.Learning && current == SurahHifzStatus.LEARNED -> coveredAyahs
        else -> 0
    }

    fun needsConfirmation(current: SurahHifzStatus?, coveredAyahs: Int, target: SurahTarget): Boolean =
        lostAyahs(current, coveredAyahs, target) > 0

    /** A choice that changes nothing (the card is selected already). */
    fun isNoop(current: SurahHifzStatus?, target: SurahTarget): Boolean = targetOf(current) == target

    fun steps(current: SurahHifzStatus?, target: SurahTarget, inPlan: Boolean): List<StatusStep> = when {
        isNoop(current, target) -> emptyList()
        target == SurahTarget.Known -> listOf(StatusStep.MarkKnown)
        target == SurahTarget.Learning ->
            if (current == SurahHifzStatus.LEARNED) listOf(StatusStep.Forget, StatusStep.LearnNext) else listOf(StatusStep.LearnNext)
        else -> buildList {
            add(StatusStep.Forget)
            if (inPlan) add(StatusStep.LeaveThePlan)
        }
    }
}
