package tj.app.quran_todo.ui.widget

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.home.LessonCardState
import tj.app.quran_todo.ui.home.LessonCardUi
import tj.app.quran_todo.ui.home.LessonLineKind
import tj.app.quran_todo.ui.home.components.tomorrowText
import tj.app.quran_todo.ui.i18n.homeCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.i18n.pauseCopy
import tj.app.quran_todo.ui.i18n.widgetCopy
import tj.app.quran_todo.ui.lesson.formatDay

/**
 * What the "Lesson of the day" widget (task F5b) shows, as plain text. It is the same lesson card as the home screen ([LessonCardUi],
 * built by `LessonCardBuilder`), so the widget and the card always agree: this only picks the words for a small surface.
 *
 * - [title]: "Lesson of the day · ~12 min" (while something is done: "· ~8 min left").
 * - [detail]: the line under it, "New: An-Naba 1–5 · 2 repetitions"; a done day says "All done today · tomorrow: ...".
 * - [streak]: "7 days in a row" for the bigger size; null with no streak.
 * - [action]: the label of the state for the bigger size ("Start lesson"); null when there is nothing to start.
 * - [opensLesson]: a tap goes straight to the lesson; false means the main screen (no plan, a finished plan, a pause).
 */
internal data class LessonWidgetModel(
    val state: LessonCardState,
    val title: String,
    val detail: String,
    val streak: String?,
    val action: String?,
    val opensLesson: Boolean,
)

internal object LessonWidgetBuilder {
    fun build(card: LessonCardUi, streakDays: Int, language: AppLanguage): LessonWidgetModel {
        val home = homeCopy(language)
        val extra = widgetCopy(language)
        val streak = extra.streak(streakDays).takeIf { streakDays > 0 }
        return when (card.state) {
            LessonCardState.Start, LessonCardState.Continue -> {
                val continuing = card.state == LessonCardState.Continue
                val minutes = if (continuing) home.lessonMinutesLeft(card.minutes) else home.lessonMinutes(card.minutes)
                LessonWidgetModel(
                    state = card.state,
                    title = "${home.lessonTitle} · $minutes",
                    detail = remainingLine(card, language),
                    streak = streak,
                    action = if (continuing) home.lessonContinue else home.lessonStart,
                    opensLesson = true,
                )
            }

            LessonCardState.Done -> {
                val tomorrow = card.tomorrow?.let { tomorrowText(it, home) }
                    ?.replaceFirstChar { it.lowercase() }
                LessonWidgetModel(
                    state = card.state,
                    title = home.lessonTitle,
                    detail = listOfNotNull(extra.allDone, tomorrow).joinToString(" · "),
                    streak = streak,
                    action = home.lessonDone,
                    opensLesson = true,
                )
            }

            LessonCardState.NoPlan -> LessonWidgetModel(
                state = card.state,
                title = home.lessonTitle,
                detail = extra.choosePlan,
                streak = null,
                action = home.lessonMakePlan,
                opensLesson = false,
            )

            LessonCardState.Paused -> LessonWidgetModel(
                state = card.state,
                title = home.lessonTitle,
                detail = pauseCopy(language).cardTitle(formatDay(card.pausedUntilEpochDay, language)),
                streak = streak,
                action = null,
                opensLesson = false,
            )

            LessonCardState.PlanFinished -> LessonWidgetModel(
                state = card.state,
                title = home.lessonPlanFinished,
                detail = home.lessonPlanFinishedBody,
                streak = streak,
                action = home.lessonNewPlan,
                opensLesson = false,
            )
        }
    }

    /** "New: An-Naba 1–5 · 2 repetitions": the new passage when it is not done yet, then the repetitions that are left. */
    private fun remainingLine(card: LessonCardUi, language: AppLanguage): String {
        val home = homeCopy(language)
        val extra = widgetCopy(language)
        val newLine = card.lines.firstOrNull { it.kind == LessonLineKind.New && !it.done }
        val repetitions = card.lines
            .filter { it.kind != LessonLineKind.New }
            .sumOf { (it.count - it.doneCount).coerceAtLeast(0) }
        val parts = listOfNotNull(
            newLine?.let { home.lessonNew(passageRangeLabel(it.surahName, it.startAyah, it.endAyah)) },
            extra.repetitions(repetitions).takeIf { repetitions > 0 },
        )
        return parts.joinToString(" · ")
    }
}
