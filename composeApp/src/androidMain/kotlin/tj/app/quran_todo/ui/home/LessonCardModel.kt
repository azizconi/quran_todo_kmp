package tj.app.quran_todo.ui.home

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.LighterWeek
import tj.app.quran_todo.hifz.schedule.ReturnInfo
import tj.app.quran_todo.hifz.schedule.WelcomeBack

/** Which face the "Today's lesson" card shows. */
enum class LessonCardState {
    /** A lesson is ready and nothing of it is done. */
    Start,

    /** Something of the lesson is done, the rest is not. */
    Continue,

    /** Everything of today's lesson is done. */
    Done,

    /** No plan and nothing to repeat: the card invites to make a plan. */
    NoPlan,

    /** The plan is on pause today (task F2b): "Plan paused until ..." and "Continue now". */
    Paused,

    /** The plan is finished and nothing is left to repeat today: "Plan complete" and "New plan". */
    PlanFinished,

    /**
     * Not started, and the "Welcome back" screen is pending for today (task U16/U17, [WelcomeBack.shouldShow]): "Welcome back",
     * "Missed 3 days", the lines of today and "Continue memorizing", which opens the lesson (where that screen appears).
     */
    Returning,
}

enum class LessonLineKind { New, Recent, Old }

/**
 * One of the (up to) three lines of the card. [surah] and the ayahs describe the first passage of the
 * line (the only one for "New"); [count] is how many passages the line has and [doneCount] how many of
 * them are done. A block without passages has no line.
 */
@Immutable
data class LessonLineUi(
    val kind: LessonLineKind,
    val surah: Int,
    val surahName: String,
    val startAyah: Int,
    val endAyah: Int,
    val count: Int,
    val doneCount: Int,
    /** The Old line only: every passage of the portion was listened to today ("Listened"), which changes nothing else. */
    val listened: Boolean = false,
) {
    val done: Boolean get() = doneCount >= count
}

/**
 * "Listen to the old" (task F1b) on the card: today's portion of the manzil circle as audio. [minutes] is about how long the whole
 * portion takes to listen to once; [listened] says each passage has a "listened" record today (the card says "Listened").
 */
@Immutable
data class OldListenUi(
    val passages: Int,
    val minutes: Int,
    val listened: Boolean,
)

/** What waits tomorrow: the next new passage (null if none) and how many repetitions are due. */
@Immutable
data class LessonTomorrowUi(
    val surah: Int?,
    val surahName: String,
    val startAyah: Int,
    val endAyah: Int,
    val dueCount: Int,
) {
    val hasNew: Boolean get() = surah != null
}

/** The "Today's lesson" card of the home screen, built from the lesson of the repository. */
@Immutable
data class LessonCardUi(
    val state: LessonCardState,
    val lines: List<LessonLineUi> = emptyList(),
    /** Minutes left (the whole lesson while nothing is done); 0 when there is nothing to do. */
    val minutes: Int = 0,
    /** Part of the lesson did not fit into the budget and moved to the next days. */
    val catchingUp: Boolean = false,
    val tomorrow: LessonTomorrowUi? = null,
    /** The portion of the circle as audio; null when today's portion has no passages. */
    val oldListen: OldListenUi? = null,
    /** [LessonCardState.Paused] only: the last day of the pause (the lessons come back the day after). */
    val pausedUntilEpochDay: Int = 0,
    /** [LessonCardState.Returning] only: the full days missed. */
    val missedDays: Int = 0,
    /** Start, Continue, Done: the last day of an active lighter week (the quiet line "Lighter week: until ..."); null otherwise. */
    val lighterUntilEpochDay: Int? = null,
    /** [LessonCardState.NoPlan] only: the goal is reading and listening, so the invitation is one calm line and a secondary button. */
    val quietInvite: Boolean = false,
)

/** Builds the card model from the lesson. Pure. */
internal object LessonCardBuilder {
    /**
     * [nameOf] gives the name of a surah in the language of the app. [returnInfo] and [welcomeBackAnswered] decide the "Returning" face
     * with the same rule the lesson screen uses ([WelcomeBack.shouldShow]); [lighterUntil] is the stored end of a lighter week;
     * [readGoal] is a person whose goal is only reading and listening (a calm "no plan" face).
     */
    fun build(
        lesson: LessonPlan,
        listenedToday: Set<Long> = emptySet(),
        returnInfo: ReturnInfo? = null,
        welcomeBackAnswered: Int? = null,
        lighterUntil: Int? = null,
        readGoal: Boolean = false,
        nameOf: (Int) -> String,
    ): LessonCardUi {
        // A paused day has no lesson at all: the card says so, whatever else the plan holds.
        lesson.paused?.let { pause -> return LessonCardUi(state = LessonCardState.Paused, pausedUntilEpochDay = pause.untilEpochDay) }
        val tomorrow = tomorrowOf(lesson, nameOf)
        if (lesson.isEmpty) {
            val state = when {
                !lesson.hasPlan -> LessonCardState.NoPlan
                lesson.planFinished -> LessonCardState.PlanFinished
                // A plan with something to learn always puts something into the lesson; this is only a safe answer.
                else -> LessonCardState.Done
            }
            return LessonCardUi(
                state = state,
                tomorrow = tomorrow.takeIf { state == LessonCardState.Done },
                quietInvite = state == LessonCardState.NoPlan && readGoal,
            )
        }
        val oldListen = oldListenOf(lesson, listenedToday)

        val lines = buildList {
            lesson.newBlock?.takeIf { it.state != LessonNewState.DEFERRED }?.let { block ->
                add(
                    LessonLineUi(
                        kind = LessonLineKind.New,
                        surah = block.surah,
                        surahName = nameOf(block.surah),
                        startAyah = block.startAyah,
                        endAyah = block.endAyah,
                        count = 1,
                        doneCount = if (block.state == LessonNewState.DONE) 1 else 0,
                    ),
                )
            }
            lesson.recent.firstOrNull()?.let { head ->
                add(
                    LessonLineUi(
                        kind = LessonLineKind.Recent,
                        surah = head.surah,
                        surahName = nameOf(head.surah),
                        startAyah = head.startAyah,
                        endAyah = head.endAyah,
                        count = lesson.recent.size,
                        doneCount = lesson.recent.count { it.done },
                    ),
                )
            }
            lesson.old.firstOrNull()?.let { head ->
                add(
                    LessonLineUi(
                        kind = LessonLineKind.Old,
                        surah = head.surah,
                        surahName = nameOf(head.surah),
                        startAyah = head.startAyah,
                        endAyah = head.endAyah,
                        count = lesson.old.size,
                        doneCount = lesson.old.count { it.done },
                        listened = oldListen?.listened == true,
                    ),
                )
            }
        }
        val anythingDone = lines.any { it.doneCount > 0 }
        val returning = !lesson.isComplete && !anythingDone && returnInfo != null && WelcomeBack.shouldShow(returnInfo, welcomeBackAnswered)
        val state = when {
            lesson.isComplete -> LessonCardState.Done
            anythingDone -> LessonCardState.Continue
            returning -> LessonCardState.Returning
            else -> LessonCardState.Start
        }
        return LessonCardUi(
            state = state,
            missedDays = if (returning) returnInfo!!.missedDays else 0,
            lighterUntilEpochDay = lighterUntil?.takeIf {
                LighterWeek.isActive(it, lesson.today) &&
                    (state == LessonCardState.Start || state == LessonCardState.Continue || state == LessonCardState.Done)
            },
            lines = lines,
            minutes = if (state == LessonCardState.Done) 0 else lesson.remainingMinutes.coerceAtLeast(1),
            catchingUp = lesson.catchingUp,
            tomorrow = tomorrow.takeIf { state == LessonCardState.Done },
            oldListen = oldListen,
        )
    }

    /**
     * The portion of the circle as audio: its passages (done ones too: listening is a help for the recall of any of them) and
     * the minutes of one calm recitation of them all. Null without passages. Display only: it reads the lesson and changes nothing.
     */
    fun oldListenOf(lesson: LessonPlan, listenedToday: Set<Long>): OldListenUi? {
        val old = lesson.old
        if (old.isEmpty()) return null
        val seconds = old.sumOf { it.listenSeconds }
        return OldListenUi(
            passages = old.size,
            minutes = HifzConstants.minutes(seconds).coerceAtLeast(1),
            listened = old.all { it.passageId in listenedToday },
        )
    }

    private fun tomorrowOf(lesson: LessonPlan, nameOf: (Int) -> String): LessonTomorrowUi {
        val next: AyahRange? = lesson.tomorrow.newPassage
        return LessonTomorrowUi(
            surah = next?.surah,
            surahName = next?.let { nameOf(it.surah) }.orEmpty(),
            startAyah = next?.startAyah ?: 0,
            endAyah = next?.endAyah ?: 0,
            dueCount = lesson.tomorrow.dueCount,
        )
    }
}
