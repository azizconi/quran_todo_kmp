package tj.app.quran_todo.ui.progress

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.Fx
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.LearnedAyahs
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.PlanForecast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The model of the screen from the answers of the hifz model: the passages go through the real `Coverage`, `HifzStatuses`,
 * `Forecast` and `LessonBuilder` (over the real ayah counts of the 114 surahs), so these tests also pin what the repository
 * feeds the screen.
 */
class ProgressBuilderTest {
    private val today = 20_000

    private fun sources(
        passages: List<Passage> = emptyList(),
        log: List<ReviewLogEntry> = emptyList(),
        plan: HifzPlan? = null,
        streak: Int = 0,
        lesson: LessonPlan? = null,
    ): ProgressSources {
        val metrics = Fx.flat
        val coverage = Coverage(passages, metrics)
        val covered = (1..114).filter { coverage.coveredCount(it) > 0 }.associateWith { coverage.coveredAyahs(it) }
        val learnedAyahs = LearnedAyahs(passages, metrics)
        val learned = (1..114).filter { learnedAyahs.learnedCount(it) > 0 }.associateWith { learnedAyahs.learnedAyahs(it) }
        val newLog = log.filter { it.kind == ReviewKind.NEW }
        return ProgressSources(
            today = today,
            language = AppLanguage.EN,
            names = List(114) { "S${it + 1}" },
            statuses = HifzStatuses.allDisplay(passages, plan, metrics),
            covered = covered,
            learned = learned,
            hard = HifzStatuses.hardAyahs(passages),
            log = log,
            streak = streak,
            lesson = lesson,
            plan = plan,
            forecast = plan?.let { Forecast.compute(it, coverage, newLog, today) },
        )
    }

    private fun entry(
        kind: ReviewKind,
        surah: Int,
        start: Int,
        end: Int,
        day: Int = today,
        grade: ReviewGrade = ReviewGrade.GOOD,
        passageId: Long? = null,
    ) = Fx.log(passageId, surah, start, end, kind, grade, day)

    // -- The ring and the map -------------------------------------------------------------------------

    @Test
    fun nothingLearnedAndNothingLoggedIsTheEmptyState() {
        val ui = ProgressBuilder.build(sources())
        assertEquals(ProgressPhase.Empty, ui.phase)
        assertEquals(0, ui.hero.coveredAyahs)
        assertEquals(6236, ui.hero.totalAyahs)
        assertEquals(0, ui.hero.percent)
        assertEquals(114, ui.cells.size)
        assertTrue(ui.cells.all { it.state == MapState.NotStarted && it.learnedAyahs == 0 })
        assertEquals(30, ui.juz.size)
        assertTrue(ui.juz.all { it.learnedAyahs == 0 && it.percent == 0 })
        assertEquals(PlanStatus.NoPlan, ui.plan.status)
        assertTrue(ui.hard.isEmpty())
        assertNull(ui.tiles.retentionPercent)
    }

    @Test
    fun familiarSurahsAloneAreContentNotEmpty() {
        // "I know it by heart": passages without a single record in the log.
        val passages = listOf(
            Fx.passage(1, 112, 1, 4),
            Fx.passage(2, 113, 1, 2),
        )
        val ui = ProgressBuilder.build(sources(passages))
        assertEquals(ProgressPhase.Content, ui.phase)
        assertEquals(6, ui.hero.coveredAyahs)
        assertEquals(1, ui.hero.learnedSurahs)
        assertEquals(1, ui.hero.learningSurahs)
        // 6 of 6236 is under a percent: the ring never says "<1", it says 1.
        assertEquals(1, ui.hero.percent)
        val full = ui.cells[111]
        assertEquals(MapState.Learned, full.state)
        assertEquals(4, full.learnedAyahs)
        assertEquals(1f, full.fraction)
        val partial = ui.cells[112]
        assertEquals(MapState.Learning, partial.state)
        assertEquals(2, partial.learnedAyahs)
        assertEquals(5, partial.ayahCount)
        assertEquals(0.4f, partial.fraction)
        assertEquals("S112", full.name)
        assertEquals(MapState.NotStarted, ui.cells[0].state)
        // Nothing was logged: no streak, no retention, empty days all the same visible.
        assertNull(ui.tiles.retentionPercent)
        assertEquals(30, ui.activity.days.size)
        assertTrue(ui.activity.isEmpty)
    }

    @Test
    fun theCurrentSurahOfThePlanIsLearningOnTheMapEvenWithNothingCovered() {
        val plan = Fx.plan(listOf(114, 113), daily = 3, started = today)
        val ui = ProgressBuilder.build(sources(plan = plan, log = listOf(entry(ReviewKind.NEW, 114, 1, 3, today - 1))))
        assertEquals(MapState.Learning, ui.cells[113].state)
        assertEquals(0, ui.cells[113].learnedAyahs)
        assertEquals(0f, ui.cells[113].fraction)
        assertEquals(1, ui.hero.learningSurahs)
    }

    @Test
    fun theRingIsHonestAtTheEdges() {
        assertEquals(0, ProgressBuilder.hero(0, 0, 0, 0).percent)
        assertEquals(1, ProgressBuilder.hero(1, 1, 0, 0).percent)
        assertEquals(1, ProgressBuilder.hero(32, 32, 0, 0).percent) // 0.51%
        assertEquals(99, ProgressBuilder.hero(6235, 6235, 0, 0).percent)
        assertEquals(100, ProgressBuilder.hero(6236, 6236, 0, 0).percent)
        assertEquals(1f, ProgressBuilder.hero(6236, 6236, 0, 0).fraction)
        assertEquals(50, ProgressBuilder.hero(3118, 3118, 0, 0).percent)
    }

    // -- The tiles ------------------------------------------------------------------------------------

    @Test
    fun theStreakComesFromTheRepository() {
        assertEquals(6, ProgressBuilder.build(sources(streak = 6)).tiles.streakDays)
        assertEquals(0, ProgressBuilder.build(sources()).tiles.streakDays)
    }

    private fun lessonOf(plan: HifzPlan?, passages: List<Passage>, todayLog: List<ReviewLogEntry>): LessonPlan =
        LessonBuilder.build(LessonInput(today, plan, passages, todayLog, Fx.flat))

    @Test
    fun noLessonAtAllIsNotALessonDone() {
        assertEquals(TodayUi.None, ProgressBuilder.today(null))
        assertEquals(TodayUi.None, ProgressBuilder.today(lessonOf(null, emptyList(), emptyList())))
    }

    @Test
    fun aLessonHalfDoneSaysHowManyCardsAreLeft() {
        val plan = Fx.plan(listOf(114), daily = 3, started = today - 5)
        val p1 = Fx.passage(1, 112, 1, 4, PassageStage.R1, due = today, last = today - 1, learned = today - 1)
        val p2 = Fx.passage(2, 113, 1, 5, PassageStage.R1, due = today, last = today - 1, learned = today - 1)
        val passages = listOf(p1, p2)

        // The new passage and two repetitions: three cards, nothing done.
        assertEquals(TodayUi.Left(left = 3, total = 3), ProgressBuilder.today(lessonOf(plan, passages, emptyList())))

        // One repetition done: two of three are left.
        val oneDone = listOf(Fx.logOf(p1, ReviewKind.RECENT, ReviewGrade.GOOD, today))
        assertEquals(TodayUi.Left(left = 2, total = 3), ProgressBuilder.today(lessonOf(plan, passages, oneDone)))

        // Everything done.
        val allDone = oneDone + Fx.logOf(p2, ReviewKind.RECENT, ReviewGrade.GOOD, today) +
            Fx.log(null, 114, 1, 3, ReviewKind.NEW, ReviewGrade.GOOD, today)
        assertEquals(TodayUi.Done, ProgressBuilder.today(lessonOf(plan, passages, allDone)))
    }

    @Test
    fun theLessonOfTheDayReachesTheTile() {
        val plan = Fx.plan(listOf(114), daily = 3, started = today)
        val ui = ProgressBuilder.build(sources(plan = plan, lesson = lessonOf(plan, emptyList(), emptyList())))
        assertEquals(TodayUi.Left(1, 1), ui.tiles.today)
    }

    @Test
    fun retentionIsTheShareOfRepetitionsThatWereNotForgotten() {
        val log = listOf(
            entry(ReviewKind.RECENT, 112, 1, 4, today, ReviewGrade.GOOD),
            entry(ReviewKind.OLD, 113, 1, 5, today - 1, ReviewGrade.EASY),
            entry(ReviewKind.CHECK, 114, 1, 6, today - 2, ReviewGrade.HARD),
            entry(ReviewKind.RECENT, 112, 1, 4, today - 3, ReviewGrade.FORGOT),
            // Not repetitions, or not in the window: ignored.
            entry(ReviewKind.NEW, 111, 1, 5, today, ReviewGrade.FORGOT),
            entry(ReviewKind.RECENT, 112, 1, 4, today - 30, ReviewGrade.FORGOT),
        )
        val retention = ProgressBuilder.retention(log, today - 29, today)
        assertEquals(Retention(75, 4), retention)
        val tiles = ProgressBuilder.build(sources(log = log)).tiles
        assertEquals(75, tiles.retentionPercent)
        assertEquals(4, tiles.retentionRepetitions)
    }

    @Test
    fun retentionNeverReadsHundredWhileSomethingWasForgottenAndHasNoNumberWithoutRepetitions() {
        val good = List(199) { entry(ReviewKind.RECENT, 112, 1, 4, today, ReviewGrade.GOOD) }
        val forgot = entry(ReviewKind.RECENT, 112, 1, 4, today, ReviewGrade.FORGOT)
        assertEquals(99, ProgressBuilder.retention(good + forgot, today - 29, today).percent)
        assertEquals(100, ProgressBuilder.retention(good, today - 29, today).percent)
        assertEquals(1, ProgressBuilder.retention(listOf(good.first()) + List(300) { forgot }, today - 29, today).percent)
        assertEquals(0, ProgressBuilder.retention(List(3) { forgot }, today - 29, today).percent)
        assertNull(ProgressBuilder.retention(emptyList(), today - 29, today).percent)
        assertNull(ProgressBuilder.retention(listOf(entry(ReviewKind.NEW, 112, 1, 4)), today - 29, today).percent)
    }

    @Test
    fun difficultAyahsAreCountedAcrossSurahsAndListedMostAffectedFirst() {
        val passages = listOf(
            Fx.passage(1, 114, 1, 6, hard = mapOf(5 to 2, 2 to 1)),
            Fx.passage(2, 112, 1, 4, hard = mapOf(3 to 2)),
            Fx.passage(3, 113, 1, 5, hard = mapOf(1 to 1, 2 to 1, 4 to 2)),
        )
        val ui = ProgressBuilder.build(sources(passages))
        assertEquals(6, ui.tiles.hardAyahs)
        assertEquals(listOf(113, 114, 112), ui.hard.map { it.surah })
        assertEquals(listOf(1, 2, 4), ui.hard[0].ayahs)
        assertEquals(listOf(2, 5), ui.hard[1].ayahs)
        assertEquals("S113", ui.hard[0].name)
        assertTrue(ProgressBuilder.build(sources(listOf(Fx.passage(1, 112, 1, 4)))).hard.isEmpty())
    }

    // -- Activity -------------------------------------------------------------------------------------

    @Test
    fun activityHasADayForEveryDayOfTheWindowEvenWhenEmpty() {
        val days = ProgressBuilder.activity(emptyList(), today)
        assertEquals(30, days.size)
        assertEquals(today - 29, days.first().epochDay)
        assertEquals(today, days.last().epochDay)
        assertTrue(days.all { it.total == 0 })
        assertEquals(7, ProgressBuilder.activity(emptyList(), today, days = 7).size)
    }

    @Test
    fun activitySeparatesNewFromRepeatedAndCountsAnAyahOncePerKindAndDay() {
        val log = listOf(
            entry(ReviewKind.NEW, 114, 1, 3, today),
            entry(ReviewKind.RECENT, 112, 1, 4, today, passageId = 1),
            entry(ReviewKind.CHECK, 112, 2, 3, today), // inside the repetition above: nothing new to count
            entry(ReviewKind.OLD, 113, 1, 2, today - 2),
            entry(ReviewKind.NEW, 113, 3, 5, today - 5),
            entry(ReviewKind.NEW, 112, 1, 4, today - 40), // out of the window
        )
        val days = ProgressBuilder.activity(log, today)
        assertEquals(ActivityDayUi(today, learned = 3, reviewed = 4), days.last())
        assertEquals(ActivityDayUi(today - 2, learned = 0, reviewed = 2), days[days.size - 3])
        assertEquals(ActivityDayUi(today - 5, learned = 3, reviewed = 0), days[days.size - 6])
        assertEquals(ActivityDayUi(today - 1, 0, 0), days[days.size - 2])
        val activity = ActivityUi(days)
        assertEquals(6, activity.totalLearned)
        assertEquals(6, activity.totalReviewed)
        assertEquals(7, activity.peak)
        assertFalse(activity.isEmpty)
    }

    @Test
    fun activeDaysCountsDaysWithAnyRecord() {
        val log = listOf(
            entry(ReviewKind.NEW, 114, 1, 3, today),
            entry(ReviewKind.RECENT, 112, 1, 4, today),
            entry(ReviewKind.CHECK, 112, 1, 4, today - 3),
            entry(ReviewKind.OLD, 113, 1, 2, today - 8),
        )
        assertEquals(2, ProgressBuilder.activeDays(log, today - 6, today))
        assertEquals(3, ProgressBuilder.activeDays(log, today - 29, today))
    }

    // -- Juz ------------------------------------------------------------------------------------------

    @Test
    fun theThirtyJuzAddUpToTheWholeQuran() {
        val juz = ProgressBuilder.juz(emptyMap())
        assertEquals(30, juz.size)
        assertEquals((1..30).toList(), juz.map { it.juz })
        assertEquals(6236, juz.sumOf { it.ayahCount })
        assertEquals("عم", juz[29].arabicName)
        assertEquals(78 to 1, juz[29].startSurah to juz[29].startAyah)
    }

    @Test
    fun aJuzCountsTheCoveredAyahsOfEverySurahItTouches() {
        // Juz 30 is 78:1 - 114:6 (564 ayahs): all of 114 and 113 and half of 112.
        val covered = mapOf(
            114 to (1..6).toSet(),
            113 to (1..5).toSet(),
            112 to setOf(1, 2),
        )
        val juz30 = ProgressBuilder.juz(covered)[29]
        assertEquals(564, juz30.ayahCount)
        assertEquals(13, juz30.learnedAyahs)
        assertEquals(2, juz30.percent) // 2.3%
        assertTrue(juz30.fraction > 0.02f && juz30.fraction < 0.03f)
    }

    @Test
    fun aJuzThatStartsInsideASurahSplitsItCorrectly() {
        // Juz 1 is 1:1 - 2:141, juz 2 starts at 2:142.
        val covered = mapOf(1 to (1..7).toSet(), 2 to (130..150).toSet())
        val juz = ProgressBuilder.juz(covered)
        assertEquals(7 + 12, juz[0].learnedAyahs) // 2:130-141
        assertEquals(9, juz[1].learnedAyahs) // 2:142-150
        assertEquals(2 to 142, juz[1].startSurah to juz[1].startAyah)
        assertEquals(0, juz[2].learnedAyahs)
    }

    @Test
    fun aFullyCoveredJuzIsOneHundredPercentAndAnAlmostFullOneIsNot() {
        val whole = ProgressBuilder.juz((78..114).associateWith { s -> (1..tj.app.quran_todo.data.bundled.QuranFacts.ayahCount(s)).toSet() })
        assertEquals(100, whole[29].percent)
        assertEquals(1f, whole[29].fraction)
        val almost = ProgressBuilder.juz((78..114).associateWith { s -> (1..tj.app.quran_todo.data.bundled.QuranFacts.ayahCount(s)).toSet() } + (114 to (1..5).toSet()))
        assertEquals(99, almost[29].percent)
        assertEquals(1, ProgressBuilder.juz(mapOf(114 to setOf(1)))[29].percent)
    }

    @Test
    fun juzComeFromTheCoveredAyahsOfTheSources() {
        val ui = ProgressBuilder.build(sources(listOf(Fx.passage(1, 114, 1, 6), Fx.passage(2, 113, 1, 5))))
        assertEquals(11, ui.juz[29].learnedAyahs)
    }

    // -- The plan -------------------------------------------------------------------------------------

    private val plan = Fx.plan(listOf(114, 113, 112), daily = 5, target = null, started = 100, scope = HifzScope.JUZ_30)

    private fun forecast(
        finished: Boolean = false,
        finish: Int? = 1_000,
        target: Int? = null,
        required: Int? = null,
        behind: Int = 0,
    ) = PlanForecast(
        totalAyahs = 15,
        remainingAyahs = if (finished) 0 else 10,
        dailyNewAyahs = 5,
        finished = finished,
        finishEpochDay = finish,
        targetEpochDay = target,
        requiredPerDay = required,
        behindAyahs = behind,
    )

    @Test
    fun noPlanInvitesToMakeOne() {
        assertEquals(PlanLineUi(PlanStatus.NoPlan), ProgressBuilder.planLine(null, null))
        assertEquals(PlanLineUi(PlanStatus.NoPlan), ProgressBuilder.planLine(null, forecast()))
    }

    @Test
    fun aPlanWithoutATargetJustSaysWhereThePaceLeads() {
        assertEquals(
            PlanLineUi(PlanStatus.Finish, HifzScope.JUZ_30, finishEpochDay = 1_000),
            ProgressBuilder.planLine(plan, forecast()),
        )
        // The forecast is not here yet: the plan is named without a date.
        assertEquals(PlanLineUi(PlanStatus.Finish, HifzScope.JUZ_30), ProgressBuilder.planLine(plan, null))
    }

    @Test
    fun aFinishedPlanIsFinished() {
        assertEquals(
            PlanLineUi(PlanStatus.Finished, HifzScope.JUZ_30),
            ProgressBuilder.planLine(plan, forecast(finished = true, finish = null)),
        )
    }

    @Test
    fun behindTheTargetNamesTheAyahsBehind() {
        val line = ProgressBuilder.planLine(plan, forecast(finish = 1_100, target = 1_050, required = 9, behind = 12))
        assertEquals(PlanStatus.Behind, line.status)
        assertEquals(12, line.ayahs)
        assertEquals(1_100, line.finishEpochDay)
    }

    @Test
    fun aheadOnTrackAndTooSlowAreToldApart() {
        assertEquals(PlanStatus.Ahead, ProgressBuilder.planLine(plan, forecast(finish = 1_040, target = 1_050)).status)
        assertEquals(PlanStatus.OnTrack, ProgressBuilder.planLine(plan, forecast(finish = 1_050, target = 1_050)).status)
        val slow = ProgressBuilder.planLine(plan, forecast(finish = 1_070, target = 1_050, required = 7, behind = 0))
        assertEquals(PlanStatus.NeedPace, slow.status)
        assertEquals(7, slow.ayahs)
    }

    @Test
    fun theForecastOfTheRealSchedulerReachesTheLine() {
        // 3 ayahs a day over 3 surahs of 6 + 5 + 4 ayahs: 15 ayahs, 5 days; no target.
        val p = Fx.plan(listOf(114, 113, 112), daily = 3, started = today, scope = HifzScope.CUSTOM)
        val ui = ProgressBuilder.build(sources(plan = p))
        assertEquals(PlanStatus.Finish, ui.plan.status)
        assertEquals(today + 5, ui.plan.finishEpochDay)
        assertEquals(HifzScope.CUSTOM, ui.plan.scope)
    }
}
