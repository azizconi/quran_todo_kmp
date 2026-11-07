package tj.app.quran_todo.hifz

import tj.app.quran_todo.data.database.entity.hifz.HifzMetaEntity
import tj.app.quran_todo.data.database.entity.hifz.HifzPassageEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageWithHard
import tj.app.quran_todo.hifz.backup.BackupCodec
import tj.app.quran_todo.hifz.backup.BackupFile
import tj.app.quran_todo.hifz.backup.BackupFormat
import tj.app.quran_todo.hifz.backup.BackupMapping
import tj.app.quran_todo.hifz.backup.BackupParse
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.PassageOps
import tj.app.quran_todo.hifz.schedule.PlanPauses
import tj.app.quran_todo.hifz.schedule.Streak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pause of the plan (task F2b): the lesson of a paused day is empty, the calendar of the plan moves by the length of the pause,
 * a pause can be extended or ended early, the streak bridges the pause days, the forecast does not count the pause as lateness, and
 * the pause survives the backup file. The repository runs these same pure functions inside one transaction
 * (`HifzRepositoryTest` on a device checks that wiring).
 */
class PlanPauseTest {
    private val m = Fx.flat // 100 characters an ayah
    private val today = 1_000

    /** The state the repository holds, and what a step of the pause does to it (the same two functions the repository calls). */
    private data class State(val passages: List<Passage>, val plan: HifzPlan?, val pauses: List<PlanPause> = emptyList())

    private fun State.pause(days: Int, day: Int): State {
        val step = PlanPauses.start(pauses, days, day)
        return State(PlanPauses.shiftPassages(passages, step.shiftDays), plan?.let { PlanPauses.shiftPlan(it, step.shiftDays) }, step.pauses)
    }

    private fun State.resume(day: Int): State {
        val step = PlanPauses.resume(pauses, day) ?: return this
        return State(PlanPauses.shiftPassages(passages, step.shiftDays), plan?.let { PlanPauses.shiftPlan(it, step.shiftDays) }, step.pauses)
    }

    private fun State.lesson(day: Int, log: List<ReviewLogEntry> = emptyList()): LessonPlan =
        LessonBuilder.build(LessonInput(day, plan, passages, log, m, PlanPauses.covering(pauses, day)))

    // A ladder with an overdue repetition, one due soon, one due later; a circle of three old passages; a plan with something new.
    private val overdue = Fx.passage(1, 2, 1, 5, PassageStage.R1, due = today - 1, last = today - 2)
    private val soon = Fx.passage(2, 2, 6, 10, PassageStage.R3, due = today + 2, last = today - 1)
    private val later = Fx.passage(3, 2, 11, 15, PassageStage.R7, due = today + 5, last = today - 2)
    private val old1 = Fx.passage(4, 3, 1, 5, PassageStage.OLD, last = today - 20)
    private val old2 = Fx.passage(5, 3, 6, 10, PassageStage.OLD, last = today - 8)
    private val old3 = Fx.passage(6, 3, 11, 15, PassageStage.OLD, last = null)
    private val passages = listOf(overdue, soon, later, old1, old2, old3)
    private val plan = Fx.plan(listOf(114, 113), daily = 5, target = today + 60, started = today - 30)
    private val base = State(passages, plan)

    // ---- The model and the codec ----

    @Test
    fun aPauseKnowsItsLengthAndTheDayTheLessonsComeBack() {
        val pause = PlanPause(1_000, 1_002)
        assertEquals(3, pause.dayCount)
        assertEquals(1_003, pause.resumeEpochDay)
        assertTrue(1_000 in pause && 1_002 in pause)
        assertFalse(999 in pause || 1_003 in pause)
    }

    @Test
    fun theCodecRoundTripsAListOfPauses() {
        val pauses = listOf(PlanPause(900, 903), PlanPause(1_000, 1_006), PlanPause(1_010, 1_010))
        val text = PlanPauses.encode(pauses)
        assertEquals("900,903;1000,1006;1010,1010", text)
        assertEquals(pauses, PlanPauses.decode(text))
        assertEquals("", PlanPauses.encode(emptyList()))
        assertEquals(emptyList(), PlanPauses.decode(""))
        assertEquals(emptyList(), PlanPauses.decode(null))
    }

    @Test
    fun theCodecSortsAndKeepsWhatItCanReadFromDamagedText() {
        assertEquals(listOf(PlanPause(5, 6), PlanPause(10, 12)), PlanPauses.decode("10,12;5,6"))
        // Not two numbers, a letter, an end before the start, an empty part: dropped, the good parts stay.
        assertEquals(listOf(PlanPause(5, 6)), PlanPauses.decode("5;a,b;9,7;;5,6;1,2,3"))
        assertEquals(listOf(PlanPause(5, 6)), PlanPauses.decode(" 5 , 6 ; x"))
        assertEquals(emptyList(), PlanPauses.decode("garbage"))
    }

    @Test
    fun theCurrentPauseIsTheOneThatIsOnOrAheadAndAPastOneIsNull() {
        val pauses = listOf(PlanPause(900, 903), PlanPause(1_000, 1_006))
        assertEquals(PlanPause(1_000, 1_006), PlanPauses.active(pauses, 1_000))
        assertEquals(PlanPause(1_000, 1_006), PlanPauses.active(pauses, 1_006))
        assertEquals(PlanPause(1_000, 1_006), PlanPauses.active(pauses, 950)) // still ahead
        assertNull(PlanPauses.active(pauses, 1_007))
        assertNull(PlanPauses.covering(pauses, 950))
        assertEquals(PlanPause(900, 903), PlanPauses.covering(pauses, 902))
        assertNull(PlanPauses.active(emptyList(), 1_000))
    }

    @Test
    fun theHistoryKeepsFourHundredDays() {
        val pauses = listOf(PlanPause(100, 103), PlanPause(590, 593), PlanPause(600, 602))
        // today 1000: the pause that ended on 593 is 407 days old and goes; the one that ended on 602 (398 days) stays.
        assertEquals(listOf(PlanPause(600, 602)), PlanPauses.prune(pauses, 1_000))
        // A new pause forgets the old ones the same way.
        val step = PlanPauses.start(pauses, 2, 1_000)
        assertEquals(listOf(PlanPause(600, 602), PlanPause(1_000, 1_001)), step.pauses)
    }

    // ---- The lesson of a paused day ----

    @Test
    fun theLessonOfAPausedDayIsEmpty() {
        val paused = base.pause(3, today)
        for (day in today..today + 2) {
            val lesson = paused.lesson(day)
            assertEquals(PlanPause(today, today + 2), lesson.paused, "day $day")
            assertTrue(lesson.isEmpty)
            assertFalse(lesson.isComplete)
            assertNull(lesson.newBlock)
            assertTrue(lesson.recent.isEmpty() && lesson.old.isEmpty())
            assertEquals(0, lesson.totalMinutes)
            assertEquals(0, lesson.remainingMinutes)
            assertFalse(lesson.catchingUp)
            assertEquals(0, lesson.deferredCount)
            assertTrue(lesson.hasPlan)
        }
        val back = paused.lesson(today + 3)
        assertNull(back.paused)
        assertFalse(back.isEmpty)
    }

    @Test
    fun aPausedDayStaysEmptyEvenWhenThingsWereDoneToday() {
        val doneToday = listOf(Fx.logOf(overdue, ReviewKind.RECENT, ReviewGrade.GOOD, day = today))
        assertFalse(base.lesson(today, doneToday).isEmpty)
        val lesson = base.pause(2, today).lesson(today, doneToday)
        assertTrue(lesson.isEmpty)
        assertNotNull(lesson.paused)
    }

    @Test
    fun theLessonOfAPausedDayWithoutAPlanIsEmptyToo() {
        val paused = State(passages, plan = null).pause(4, today)
        val lesson = paused.lesson(today + 1)
        assertTrue(lesson.isEmpty)
        assertFalse(lesson.hasPlan)
        assertEquals(PlanPause(today, today + 3), lesson.paused)
    }

    @Test
    fun tomorrowOfAPausedDayIsTheFirstDayWithALessonAgain() {
        val lesson = base.pause(3, today).lesson(today)
        // The day the lessons come back: the repetitions that were waiting, the same as without the pause on the first day.
        val back = base.pause(3, today).lesson(today + 3)
        assertEquals(back.recent.size + back.old.size, lesson.tomorrow.dueCount)
        assertEquals(back.newBlock?.let { it.surah to it.startAyah }, lesson.tomorrow.newPassage?.let { it.surah to it.startAyah })
    }

    @Test
    fun afterThePauseTheLessonIsTheOneThatWasWaitingMovedByTheDaysOfThePause() {
        for (days in listOf(1, 3, 7, 14)) {
            val without = base.lesson(today)
            val paused = base.pause(days, today)
            // The lesson of the first day after the pause is the lesson of "today" without it.
            assertEquals(without, paused.lesson(today + days).copy(today = today), "pause of $days days")
            // And so is every day after that.
            for (extra in 1..6) {
                assertEquals(
                    base.lesson(today + extra),
                    paused.lesson(today + days + extra).copy(today = today + extra),
                    "pause of $days days, $extra days later",
                )
            }
        }
    }

    @Test
    fun thePassagesAreMovedByTheDaysOfThePauseAndANullStaysNull() {
        val paused = base.pause(7, today)
        paused.passages.zip(passages).forEach { (after, before) ->
            assertEquals(before.nextDueEpochDay?.plus(7), after.nextDueEpochDay, "due of ${before.id}")
            assertEquals(before.lastReviewedEpochDay?.plus(7), after.lastReviewedEpochDay, "last of ${before.id}")
            assertEquals(before.learnedEpochDay, after.learnedEpochDay)
            assertEquals(before.stage, after.stage)
            assertEquals(before.hard, after.hard)
            assertEquals(before.lapses, after.lapses)
        }
        assertNull(paused.passages.first { it.id == old3.id }.lastReviewedEpochDay)
        assertNull(paused.passages.first { it.id == old1.id }.nextDueEpochDay)
        assertEquals(plan.copy(targetEpochDay = today + 67, startedEpochDay = today - 23), paused.plan)
    }

    @Test
    fun aPlanWithoutATargetOnlyMovesItsStart() {
        val open = base.copy(plan = plan.copy(targetEpochDay = null))
        val paused = open.pause(3, today)
        assertNull(paused.plan?.targetEpochDay)
        assertEquals(today - 27, paused.plan?.startedEpochDay)
    }

    // ---- Extending and ending a pause ----

    @Test
    fun anExtensionMovesTheCalendarOnlyByTheDifferenceOfTheEnds() {
        val first = base.pause(3, today) // today .. today + 2, everything +3
        val extended = first.pause(5, today + 1) // the end becomes today + 1 + 4 = today + 5, +3 more
        assertEquals(listOf(PlanPause(today, today + 5)), extended.pauses)
        assertEquals(base.pause(6, today).passages, extended.passages)
        assertEquals(base.pause(6, today).plan, extended.plan)
    }

    @Test
    fun theEndOfAPauseIsNeverFurtherThanThirteenDaysFromToday() {
        val first = base.pause(14, today) // ends today + 13
        val again = first.pause(14, today + 1) // today + 14 would be the end: it is 14 days from the new day, 13 + 1
        assertEquals(today + 14, again.pauses.single().untilEpochDay)
        val coerced = base.pause(40, today)
        assertEquals(today + 13, coerced.pauses.single().untilEpochDay)
        assertEquals(base.pause(14, today), coerced)
        assertEquals(today, base.pause(0, today).pauses.single().untilEpochDay) // at least one day
    }

    @Test
    fun aSecondShorterPauseOnTheSameDayShortensTheFirst() {
        val first = base.pause(7, today)
        val shorter = first.pause(2, today)
        assertEquals(base.pause(2, today), shorter)
    }

    @Test
    fun endingAPauseOnItsFirstDayRestoresEverythingAndRemovesThePause() {
        val resumed = base.pause(7, today).resume(today)
        assertEquals(base.passages, resumed.passages)
        assertEquals(base.plan, resumed.plan)
        assertTrue(resumed.pauses.isEmpty())
        assertEquals(base.lesson(today), resumed.lesson(today))
    }

    @Test
    fun endingAPauseEarlyMovesTheCalendarBackByTheDaysThatWereLeft() {
        val paused = base.pause(7, today) // today .. today + 6
        val resumed = paused.resume(today + 2) // the pause was on for today, today + 1: two days
        assertEquals(listOf(PlanPause(today, today + 1)), resumed.pauses)
        assertEquals(base.pause(2, today).passages, resumed.passages)
        assertEquals(base.pause(2, today).plan, resumed.plan)
        // The lessons are back that very day, and it is the lesson of "today" moved by the two days that did pass.
        val lesson = resumed.lesson(today + 2)
        assertNull(lesson.paused)
        assertEquals(base.lesson(today), lesson.copy(today = today))
    }

    @Test
    fun endingAPauseWithNothingToEndChangesNothing() {
        assertNull(PlanPauses.resume(emptyList(), today))
        assertNull(PlanPauses.resume(listOf(PlanPause(today - 10, today - 3)), today))
        assertEquals(base, base.resume(today))
    }

    @Test
    fun whatWasDoneTodayBeforeThePauseStaysDoneAfterContinuingTheSameDay() {
        val doneToday = listOf(
            Fx.logOf(overdue, ReviewKind.RECENT, ReviewGrade.GOOD, day = today),
            Fx.logOf(old1, ReviewKind.OLD, ReviewGrade.GOOD, day = today),
        )
        // The log is the one of the day; the passages were moved by the repetitions themselves before (here: as they stand).
        val before = base.lesson(today, doneToday)
        assertTrue(before.recent.any { it.passageId == overdue.id && it.done })
        assertTrue(before.old.any { it.passageId == old1.id && it.done })
        val after = base.pause(5, today).resume(today).lesson(today, doneToday)
        assertEquals(before, after)
    }

    // ---- The streak ----

    @Test
    fun theDaysOfAPauseBridgeTheStreakAndDoNotAddToIt() {
        // Studied on 1-3, paused 4-6, today is 7 and nothing is done yet: 3, and alive.
        val pauses = listOf(PlanPause(4, 6))
        assertEquals(3, Streak.compute(listOf(1, 2, 3), today = 7, pauses = pauses))
        // A lesson on day 7: 4.
        assertEquals(4, Streak.compute(listOf(1, 2, 3, 7), today = 7, pauses = pauses))
        // Without the pause the same days are a broken streak.
        assertEquals(0, Streak.compute(listOf(1, 2, 3), today = 7))
        assertEquals(1, Streak.compute(listOf(1, 2, 3, 7), today = 7))
    }

    @Test
    fun theStreakIsAliveInsideAPauseAndOnTheDayItEnds() {
        val pauses = listOf(PlanPause(4, 9))
        for (day in 4..10) assertEquals(3, Streak.compute(listOf(1, 2, 3), today = day, pauses = pauses), "day $day")
        // The first day after the pause is "not yet", the second without a lesson breaks it.
        assertEquals(3, Streak.compute(listOf(1, 2, 3), today = 10, pauses = pauses))
        assertEquals(0, Streak.compute(listOf(1, 2, 3), today = 11, pauses = pauses))
    }

    @Test
    fun aPauseDayWithARecordIsADayOfStudyLikeAnyOther() {
        // The lesson of day 4 was done, then the pause was set for 4-6.
        val pauses = listOf(PlanPause(4, 6))
        assertEquals(4, Streak.compute(listOf(1, 2, 3, 4), today = 5, pauses = pauses))
        assertEquals(5, Streak.compute(listOf(1, 2, 3, 4, 7), today = 7, pauses = pauses))
        assertEquals(4, Streak.compute(listOf(1, 2, 3, 4), today = 4, pauses = pauses))
    }

    @Test
    fun aPauseBridgesOnlyItsOwnDays() {
        val pauses = listOf(PlanPause(4, 6))
        // A gap after the pause is a gap.
        assertEquals(0, Streak.compute(listOf(1, 2, 3), today = 9, pauses = pauses))
        assertEquals(1, Streak.compute(listOf(1, 2, 3, 9), today = 9, pauses = pauses))
        // Two pauses with study between them.
        val two = listOf(PlanPause(4, 5), PlanPause(8, 9))
        assertEquals(6, Streak.compute(listOf(1, 2, 3, 6, 7, 10), today = 10, pauses = two))
        // A pause alone, without any study, is no streak.
        assertEquals(0, Streak.compute(emptyList(), today = 8, pauses = listOf(PlanPause(1, 10))))
    }

    @Test
    fun theStreakWithoutPausesIsWhatItWas() {
        assertEquals(3, Streak.compute(listOf(100, 99, 98, 96), today = 100))
        assertEquals(3, Streak.compute(listOf(99, 98, 97), today = 100))
        assertEquals(0, Streak.compute(listOf(98, 97), today = 100))
        assertEquals(0, Streak.compute(emptyList(), today = 100))
    }

    // ---- The forecast ----

    private val surah78 = Fx.plan(listOf(78), daily = 5, started = today)

    private fun coverage(vararg ranges: IntRange) =
        Coverage(ranges.mapIndexed { i, r -> Fx.passage(i + 1L, 78, r.first, r.last, PassageStage.OLD) }, m)

    @Test
    fun withoutATargetTheForecastStartsOnTheFirstDayAfterThePause() {
        val pause = PlanPause(today, today + 2)
        val open = Forecast.compute(surah78, coverage(), emptyList(), today, pause)
        assertEquals(today + 3 + 8, open.finishEpochDay) // 40 ayahs at 5 a day: 8 days from the day the lessons are back
        // Not inside the pause: as before.
        assertEquals(today + 8, Forecast.compute(surah78, coverage(), emptyList(), today).finishEpochDay)
        assertEquals(today + 8, Forecast.compute(surah78, coverage(), emptyList(), today, pause = null).finishEpochDay)
        assertEquals(today + 3 + 8, Forecast.compute(surah78, coverage(), emptyList(), today + 2, pause).finishEpochDay)
    }

    @Test
    fun aPauseIsNotLatenessForAPlanWithATarget() {
        // Surah 78 (40 ayahs), target in 40 days, five ayahs learned on the first day.
        val started = today
        val target = today + 40
        val log = listOf(Fx.log(null, 78, 1, 5, ReviewKind.NEW, day = today))
        val cover = coverage(1..5)
        val straight = Fx.plan(listOf(78), daily = 5, target = target, started = started)

        // Ten days without a pause: 5 ayahs behind (the line asks for 10, 5 are learned).
        val withoutPause = Forecast.compute(straight, cover, log, today + 10)
        assertEquals(5, withoutPause.behindAyahs)

        // The same ten active days with a pause of three days in the middle: the calendar of the plan moved by 3 days.
        val paused = PlanPauses.shiftPlan(straight, 3)
        val afterPause = Forecast.compute(paused, cover, log, today + 13, learnedFromDay = started)
        assertEquals(withoutPause.behindAyahs, afterPause.behindAyahs)
        assertEquals(withoutPause.requiredPerDay, afterPause.requiredPerDay)

        // Inside the pause the same: the days are counted from the first day after it.
        val inside = Forecast.compute(paused, cover, log, today + 6, PlanPause(today + 5, today + 7), learnedFromDay = started)
        val virtual = Forecast.compute(straight, cover, log, today + 5)
        assertEquals(virtual.behindAyahs, inside.behindAyahs)
        assertEquals(virtual.requiredPerDay, inside.requiredPerDay)
    }

    @Test
    fun theLessonsOfTheFirstDaysStayInTheCountWhenThePauseMovedTheStart() {
        val log = listOf(Fx.log(null, 78, 1, 5, ReviewKind.NEW, day = today))
        val cover = coverage(1..5)
        val straight = Fx.plan(listOf(78), daily = 5, target = today + 40, started = today)
        val paused = PlanPauses.shiftPlan(straight, 3)
        val right = Forecast.compute(paused, cover, log, today + 13, learnedFromDay = today).behindAyahs
        // Counting from the moved start would drop the first lesson (it is before the moved start) and show more lateness.
        val wrong = Forecast.compute(paused, cover, log, today + 13).behindAyahs
        assertEquals(5, right)
        assertTrue(wrong > right, "wrong=$wrong right=$right")
    }

    // ---- The backup file ----

    @Test
    fun thePauseSurvivesAnExportAndAnImport() {
        val pauses = listOf(PlanPause(20_480, 20_483), PlanPause(20_500, 20_506))
        val meta = listOf(
            HifzMetaEntity("legacy_import", "done"),
            HifzMetaEntity(PlanPauses.META_KEY, PlanPauses.encode(pauses)),
            HifzMetaEntity(PlanPauses.BASE_START_KEY, "20400"),
        )
        val passage = PassageWithHard(HifzPassageEntity(1, 113, 1, 5, 20_400, PassageStage.OLD, null, 20_490, 0), emptyList())
        val data = BackupMapping.toBackupData(null, listOf(passage), emptyList(), meta, emptyList())
        val file = BackupFile(
            format = BackupFormat.NAME,
            formatVersion = BackupFormat.VERSION,
            appVersion = "1.0.2 (7)",
            exportedAt = 1_773_312_000_000,
            dbVersion = BackupFormat.DB_VERSION,
            hifz = data.hifz,
            notes = data.notes,
        )
        val parse = BackupCodec.decode(BackupCodec.encode(file))
        assertIs<BackupParse.Ok>(parse)
        val rows = BackupMapping.toRows(parse.backup.file)
        val byKey = rows.meta.associate { it.key to it.value }
        assertEquals(pauses, PlanPauses.decode(byKey[PlanPauses.META_KEY]))
        assertEquals("20400", byKey[PlanPauses.BASE_START_KEY])
        assertEquals("done", byKey["legacy_import"])
    }

    // ---- "Quiz": markHard changes the marks and nothing else ----

    @Test
    fun markingHardChangesOnlyTheMarksOfTheCoveringPassages() {
        val log = listOf(
            Fx.logOf(overdue, ReviewKind.RECENT, ReviewGrade.GOOD, day = today - 1),
            Fx.logOf(old1, ReviewKind.OLD, ReviewGrade.GOOD, day = today),
        )
        // What the repository does for markHard: the very rule of "Check yourself", no record in the log.
        val after = PassageOps.check(passages, surah = 2, marks = setOf(3, 8, 13, 40))
        val before = base.lesson(today, log)
        val afterLesson = base.copy(passages = after).lesson(today, log)

        // 3, 8, 13 are covered (by three passages of surah 2), 40 is not: skipped.
        assertEquals(mapOf(3 to 2), after.first { it.id == overdue.id }.hard)
        assertEquals(mapOf(8 to 2), after.first { it.id == soon.id }.hard)
        assertEquals(mapOf(13 to 2), after.first { it.id == later.id }.hard)
        assertTrue(after.filter { it.surah != 2 }.all { it.hard.isEmpty() })

        // Everything else of the passages is as it was.
        after.zip(passages).forEach { (a, b) -> assertEquals(b.copy(hard = a.hard), a) }

        // The lesson is the same but for the difficult marks of its cards (the log is not an input of the rule at all: nothing is written).
        assertEquals(before.copy(recent = emptyList(), old = emptyList()), afterLesson.copy(recent = emptyList(), old = emptyList()))
        assertEquals(before.recent.map { it.copy(hardAyahs = emptySet()) }, afterLesson.recent.map { it.copy(hardAyahs = emptySet()) })
        assertEquals(before.old.map { it.copy(hardAyahs = emptySet()) }, afterLesson.old.map { it.copy(hardAyahs = emptySet()) })
    }

    @Test
    fun markingHardAnAyahAgainStartsItsCounterOver() {
        val marked = Fx.passage(1, 2, 1, 5, PassageStage.R3, due = today + 1, hard = mapOf(3 to 1))
        val after = PassageOps.check(listOf(marked), 2, setOf(3, 4))
        assertEquals(mapOf(3 to 2, 4 to 2), after.single().hard)
        assertEquals(listOf(marked), PassageOps.check(listOf(marked), 2, emptySet()))
        assertEquals(listOf(marked), PassageOps.check(listOf(marked), 3, setOf(3)))
    }
}
