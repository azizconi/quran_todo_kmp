package tj.app.quran_todo.ui.home

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel

/**
 * The compact "Today's lesson" card at the top of the Quran tab (the Quran tab is home, the Lesson tab is secondary): one line of
 * state and one action. [text] is e.g. "New: Al-Baqarah 1-5 · ~14 min", or "Lesson done" ([done], with a check and no [action]).
 */
@Immutable
data class LessonStripUi(
    val text: String,
    val done: Boolean,
    /** The label of the button that opens the lesson; null when the lesson is done (the card itself leads to the Lesson tab). */
    val action: String?,
)

object LessonStrip {
    /**
     * The strip for [card], or null when the Lesson tab would show no lesson (no plan, a paused or a finished plan, nothing loaded yet):
     * then the Quran tab has nothing to add.
     */
    fun of(card: LessonCardUi?, copy: HomeCopy): LessonStripUi? {
        card ?: return null
        return when (card.state) {
            LessonCardState.NoPlan, LessonCardState.Paused, LessonCardState.PlanFinished -> null
            LessonCardState.Done -> LessonStripUi(text = copy.lessonDone, done = true, action = null)
            LessonCardState.Start, LessonCardState.Continue, LessonCardState.Returning -> {
                val continuing = card.state == LessonCardState.Continue
                // The line of what is still to do: the new passage first, then the recent, then the old.
                val line = card.lines.firstOrNull { it.kind == LessonLineKind.New && !it.done }
                    ?: card.lines.firstOrNull { !it.done }
                    ?: card.lines.firstOrNull()
                val parts = buildList {
                    line?.let { add(lineText(it, copy)) }
                    if (card.minutes > 0) add(if (continuing) copy.lessonMinutesLeft(card.minutes) else copy.lessonMinutes(card.minutes))
                }
                LessonStripUi(
                    text = parts.joinToString(" · ").ifEmpty { copy.lessonTitle },
                    done = false,
                    action = if (continuing) copy.lessonContinue else copy.lessonStart,
                )
            }
        }
    }

    private fun lineText(line: LessonLineUi, copy: HomeCopy): String {
        val range = passageRangeLabel(line.surahName, line.startAyah, line.endAyah)
        return when (line.kind) {
            LessonLineKind.New -> copy.lessonNew(range)
            LessonLineKind.Recent -> copy.lessonRecent(line.count, line.doneCount)
            LessonLineKind.Old -> copy.lessonOld(if (line.count > 1) "$range +${line.count - 1}" else range)
        }
    }
}
