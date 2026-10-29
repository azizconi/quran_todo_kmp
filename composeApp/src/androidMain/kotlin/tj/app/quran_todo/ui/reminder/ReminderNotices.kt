package tj.app.quran_todo.ui.reminder

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.ui.i18n.passageRangeLabel
import tj.app.quran_todo.ui.i18n.reminderCopy
import tj.app.quran_todo.ui.state.LastReadPosition

/** What kind of notification it is (the `kind` of the `reminder_shown` event). */
internal enum class ReminderKind(val analyticsName: String) {
    /** The lesson of the day, a tap opens the lesson. */
    LESSON("lesson"),

    /** There is no lesson to do: "Continue reading", a tap opens the reader. */
    READING("reading"),
}

/** Where a tap on the notification goes. */
internal sealed interface ReminderTarget {
    /** The lesson of the day, over Home. */
    data object Lesson : ReminderTarget

    /** The reader at [surah]; [ayah] is the number in the surah, null when only the surah is known. */
    data class Reader(val surah: Int, val ayah: Int?) : ReminderTarget
}

/** The notification as it will be posted. */
internal data class ReminderNotice(
    val kind: ReminderKind,
    val title: String,
    val body: String,
    val target: ReminderTarget,
)

/**
 * Decides whether a notification is due and what it says, from the lesson of the day (task B4). Pure: the tests feed it lessons
 * made by the real scheduler.
 *
 * - The lesson is done (`isComplete`): nothing is shown.
 * - Something is left to do: "Today: An-Naba 1–5 and 3 repetitions · ~12 min"; when part of the lesson is already done the same
 *   line starts with "Left:" and counts only what is left. A new passage that did not fit into the budget today (deferred) is
 *   not in today's lesson, so it is not in the text.
 * - Nothing to do at all (no plan and nothing to repeat) and the person has a reading position: "Continue reading: Al-Kahf, ayah 12".
 * - Otherwise nothing.
 */
internal object ReminderNotices {
    fun build(
        language: AppLanguage,
        lesson: LessonPlan,
        lastRead: LastReadPosition?,
        surahName: (Int) -> String,
    ): ReminderNotice? {
        if (lesson.isComplete) return null
        val copy = reminderCopy(language)

        val newToLearn = lesson.newBlock?.takeIf { it.state == LessonNewState.PENDING }
        val repetitionsLeft = (lesson.recent + lesson.old).count { !it.done }
        if (newToLearn != null || repetitionsLeft > 0) {
            val partlyDone = lesson.newBlock?.state == LessonNewState.DONE || (lesson.recent + lesson.old).any { it.done }
            val body = copy.lessonBody(
                newRange = newToLearn?.let { passageRangeLabel(surahName(it.surah), it.startAyah, it.endAyah) },
                repetitions = repetitionsLeft,
                minutes = lesson.remainingMinutes,
                partlyDone = partlyDone,
            )
            return ReminderNotice(ReminderKind.LESSON, copy.lessonTitle, body, ReminderTarget.Lesson)
        }

        val position = lastRead?.let(::readingTarget) ?: return null
        return ReminderNotice(
            kind = ReminderKind.READING,
            title = copy.readingTitle,
            body = copy.readingBody(surahName(position.surah), position.ayah),
            target = position,
        )
    }

    /**
     * The reader target of a reading position. A position kept by an old version has only the global ayah number: it is turned into
     * the number in the surah, or dropped (the surah alone opens) when it does not fit the surah.
     */
    internal fun readingTarget(position: LastReadPosition): ReminderTarget.Reader? {
        if (position.surah !in 1..SURAH_COUNT) return null
        val inSurah = position.ayahInSurah
            ?: position.legacyGlobalAyah?.let { it - QuranFacts.firstAyahNumber(position.surah) + 1 }
        val ayah = inSurah?.takeIf { it in 1..QuranFacts.ayahCount(position.surah) }
        return ReminderTarget.Reader(position.surah, ayah)
    }

    private const val SURAH_COUNT = 114
}
