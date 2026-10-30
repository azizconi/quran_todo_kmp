package tj.app.quran_todo.ui.plan

import tj.app.quran_todo.hifz.HifzPresets
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.ui.home.QuranMeta
import tj.app.quran_todo.ui.home.SurahRowUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The plan screen without Compose: editing the draft, the numbers around it, and what the screen shows. */
class PlanModelTest {
    private val today = 20_000

    private fun saved(
        scope: HifzScope = HifzScope.JUZ_30,
        order: List<Int> = HifzPresets.JUZ_30,
        daily: Int = 5,
        target: Int? = null,
        started: Int = today - 3,
        manzil: Int = 7,
    ) = HifzPlan(scope, order, daily, target, started, manzil)

    // -- Preset, own list --------------------------------------------------------------------------

    @Test
    fun aFirstPlanIsJuzAmmaAtFiveAyahsADay() {
        val draft = PlanDraft.initial()
        assertEquals(HifzScope.JUZ_30, draft.scope)
        assertEquals(HifzPresets.JUZ_30, draft.queue)
        assertEquals(PaceMode.PerDay, draft.mode)
        assertEquals(5, draft.perDay)
        assertEquals(7, draft.manzilDays)
        assertFalse(draft.isCustom)
    }

    @Test
    fun pickingAPresetShowsItsQueue() {
        val draft = PlanDraft.initial()
        assertEquals((77 downTo 67).toList(), draft.withScope(HifzScope.JUZ_29).queue)
        assertEquals((1..114).toList(), draft.withScope(HifzScope.FROM_START).queue)
        assertSame(draft, draft.withScope(HifzScope.JUZ_30))
    }

    @Test
    fun changeTheOrderTurnsAPresetIntoTheOwnListWithTheSameSurahs() {
        val draft = PlanDraft.initial().withScope(HifzScope.JUZ_29).editOrder()
        assertTrue(draft.isCustom)
        assertEquals((77 downTo 67).toList(), draft.queue)
        assertSame(draft, draft.editOrder(), "an own list is already editable")
    }

    @Test
    fun chooseOwnSurahsStartsFromWhatIsShownAndRemembersTheListAfterwards() {
        val own = PlanDraft.initial().withScope(HifzScope.CUSTOM)
        assertEquals(HifzPresets.JUZ_30, own.queue, "the first time the own list is a copy of the preset in view")
        val edited = own.remove(114).remove(113)
        val looked = edited.withScope(HifzScope.FROM_START)
        assertEquals((1..114).toList(), looked.queue)
        val back = looked.withScope(HifzScope.CUSTOM)
        assertEquals(edited.queue, back.queue, "switching back does not lose the own list")
        assertFalse(114 in back.queue)
    }

    @Test
    fun movingASurahChangesTheOrderAndMakesTheListOwn() {
        val draft = PlanDraft.initial().withScope(HifzScope.CUSTOM).copy(customQueue = listOf(114, 113, 112))
        assertEquals(listOf(113, 112, 114), draft.move(0, 2).queue)
        assertEquals(listOf(112, 114, 113), draft.move(2, 0).queue)
        assertEquals(listOf(113, 114, 112), draft.move(1, 0).queue)
        // Nothing to do: the same value comes back, and a preset stays a preset.
        assertSame(draft, draft.move(1, 1))
        assertSame(draft, draft.move(-1, 2))
        assertSame(draft, draft.move(0, 3))
        val preset = PlanDraft.initial()
        assertSame(preset, preset.move(0, 0))
        val moved = preset.move(0, 1)
        assertTrue(moved.isCustom)
        assertEquals(listOf(113, 114), moved.queue.take(2))
    }

    @Test
    fun removingAndAddingSurahsKeepsTheQueueFreeOfDuplicates() {
        val draft = PlanDraft.initial().withScope(HifzScope.CUSTOM).copy(customQueue = listOf(114, 113))
        assertEquals(listOf(114), draft.remove(113).queue)
        assertSame(draft, draft.remove(5), "not in the queue")
        assertEquals(listOf(114, 113, 2), draft.add(2).queue)
        assertSame(draft, draft.add(113), "already in the queue")
        assertSame(draft, draft.add(0))
        assertSame(draft, draft.add(115))
        assertTrue(PlanDraft.initial().add(1).isCustom, "adding to a preset makes the list the person's own")
        assertEquals(HifzPresets.JUZ_30 + 1, PlanDraft.initial().add(1).queue)
        assertTrue(draft.remove(114).remove(113).queue.isEmpty())
    }

    // -- Pace --------------------------------------------------------------------------------------

    @Test
    fun theDailyAmountStaysBetweenOneAndTwenty() {
        assertEquals(1, PlanDraft.initial().withPerDay(0).perDay)
        assertEquals(20, PlanDraft.initial().withPerDay(99).perDay)
        assertEquals(12, PlanDraft.initial().withPerDay(12).perDay)
    }

    @Test
    fun switchingToADateSuggestsOneOnlyTheFirstTime() {
        val perDay = PlanDraft.initial()
        val byDate = perDay.withMode(PaceMode.ByDate, suggestedTarget = today + 90)
        assertEquals(PaceMode.ByDate, byDate.mode)
        assertEquals(today + 90, byDate.targetDay)
        val picked = byDate.withTarget(today + 30)
        val back = picked.withMode(PaceMode.PerDay, today + 90)
        assertEquals(PaceMode.PerDay, back.mode)
        assertEquals(today + 30, back.targetDay, "the chosen date is kept while the person looks at ayahs a day")
        assertEquals(today + 30, back.withMode(PaceMode.ByDate, today + 90).targetDay)
        assertNull(back.savedTarget, "a plan in the per day mode is saved without a date")
        assertEquals(today + 30, picked.savedTarget)
    }

    @Test
    fun theManzilDaysStayBetweenThreeAndThirty() {
        assertEquals(3, PlanDraft.initial().withManzilDays(1).manzilDays)
        assertEquals(30, PlanDraft.initial().withManzilDays(100).manzilDays)
        assertEquals(14, PlanDraft.initial().withManzilDays(14).manzilDays)
    }

    // -- From and to the saved plan ----------------------------------------------------------------

    @Test
    fun aSavedPresetPlanOpensAsThatPreset() {
        val draft = PlanDraft.of(saved(HifzScope.JUZ_29, HifzPresets.JUZ_29, daily = 8, manzil = 10))
        assertEquals(HifzScope.JUZ_29, draft.scope)
        assertEquals(8, draft.perDay)
        assertEquals(10, draft.manzilDays)
        assertEquals(PaceMode.PerDay, draft.mode)
    }

    @Test
    fun aPresetWhoseQueueWasEditedOpensAsTheOwnList() {
        val draft = PlanDraft.of(saved(HifzScope.JUZ_30, order = listOf(114, 113)))
        assertTrue(draft.isCustom)
        assertEquals(listOf(114, 113), draft.queue)
        val custom = PlanDraft.of(saved(HifzScope.CUSTOM, order = listOf(2, 1)))
        assertEquals(listOf(2, 1), custom.queue)
    }

    @Test
    fun aSavedDateOpensTheDateMode() {
        val draft = PlanDraft.of(saved(target = today + 40))
        assertEquals(PaceMode.ByDate, draft.mode)
        assertEquals(today + 40, draft.targetDay)
        assertNull(PlanDraft.of(null).targetDay)
        assertEquals(PlanDraft.initial(), PlanDraft.of(null))
    }

    @Test
    fun theDraftOfASavedPlanIsNotChangedAndAnEditIs() {
        val plan = saved()
        val draft = PlanDraft.of(plan)
        assertFalse(draft.differsFrom(plan))
        assertTrue(draft.withPerDay(6).differsFrom(plan))
        assertTrue(draft.withScope(HifzScope.JUZ_29).differsFrom(plan))
        assertTrue(draft.withManzilDays(9).differsFrom(plan))
        assertTrue(draft.move(0, 1).differsFrom(plan))
        assertTrue(draft.withTarget(today + 20).differsFrom(plan))
        // An own list that equals the preset is still "Own surahs": the scope differs.
        assertTrue(draft.editOrder().differsFrom(plan))
        assertTrue(draft.differsFrom(null), "nothing is saved yet")
    }

    @Test
    fun inTheDateModeTheDailyAmountThatFollowsTheDateIsNotAnEdit() {
        val plan = saved(target = today + 40, daily = 7)
        val draft = PlanDraft.of(plan)
        assertFalse(draft.copy(perDay = 3).differsFrom(plan), "the amount a day is worked out from the date")
        assertTrue(draft.withTarget(today + 41).differsFrom(plan))
        // Back to the per day mode: the date goes away, that is a change.
        assertTrue(draft.withMode(PaceMode.PerDay, today).copy(perDay = 7).differsFrom(plan))
    }

    @Test
    fun thePlanIsSavedWithTheQueueThePaceAndTheDate() {
        val draft = PlanDraft.of(saved()).withPerDay(8).withManzilDays(10)
        val plan = draft.toPlan(startedEpochDay = today - 3, perDayToSave = draft.perDay)
        assertEquals(HifzScope.JUZ_30, plan.scope)
        assertEquals(HifzPresets.JUZ_30, plan.surahOrder)
        assertEquals(8, plan.dailyNewAyahs)
        assertNull(plan.targetEpochDay)
        assertEquals(10, plan.manzilDays)
        assertEquals(today - 3, plan.startedEpochDay)
        val dated = draft.withTarget(today + 50).toPlan(today, perDayToSave = 99)
        assertEquals(today + 50, dated.targetEpochDay)
        assertEquals(20, dated.dailyNewAyahs, "at most twenty a day")
    }

    // -- Numbers -----------------------------------------------------------------------------------

    @Test
    fun theCurrentSurahIsTheFirstOneWithAyahsLeft() {
        val queue = listOf(114, 113, 112)
        assertEquals(114, PlanMath.currentSurah(queue, emptyMap()))
        assertEquals(113, PlanMath.currentSurah(queue, mapOf(114 to 6)))
        assertEquals(113, PlanMath.currentSurah(queue, mapOf(114 to 6, 113 to 2)))
        assertNull(PlanMath.currentSurah(queue, mapOf(114 to 6, 113 to 5, 112 to 4)))
        assertNull(PlanMath.currentSurah(emptyList(), emptyMap()))
    }

    @Test
    fun remainingAndTotalCountEachSurahOnce() {
        assertEquals(6 + 5, PlanMath.total(listOf(114, 113, 114)))
        assertEquals(6 + 5 - 2, PlanMath.remaining(listOf(114, 113, 114), mapOf(113 to 2)))
        assertEquals(0, PlanMath.remaining(listOf(114), mapOf(114 to 6)))
        assertEquals(0, PlanMath.remaining(emptyList(), emptyMap()))
        val juz30 = PlanMath.total(HifzPresets.JUZ_30)
        assertEquals((78..114).sumOf { QuranMeta.ayahCount(it) }, juz30)
    }

    @Test
    fun theDateNeedsTheRemainingAyahsSpreadOverTheDaysLeft() {
        assertEquals(7, PlanMath.requiredPerDay(remaining = 70, today = today, targetDay = today + 10))
        assertEquals(8, PlanMath.requiredPerDay(remaining = 71, today = today, targetDay = today + 10))
        assertEquals(30, PlanMath.requiredPerDay(remaining = 30, today = today, targetDay = today), "at least one day")
        assertEquals(30, PlanMath.requiredPerDay(remaining = 30, today = today, targetDay = today - 5))
        assertEquals(0, PlanMath.requiredPerDay(remaining = 0, today = today, targetDay = today + 5))
    }

    @Test
    fun theSavedPaceIsTheTypedOneOrWhatTheDateNeedsAtMostTwenty() {
        val covered = emptyMap<Int, Int>()
        val perDay = PlanDraft.initial().withPerDay(9)
        assertEquals(9, PlanMath.effectivePerDay(perDay, covered, today))
        val remaining = PlanMath.remaining(perDay.queue, covered)
        val byDate = perDay.withTarget(today + 100)
        assertEquals((remaining + 99) / 100, PlanMath.effectivePerDay(byDate, covered, today))
        val tooSoon = perDay.withTarget(today + 2)
        assertEquals(20, PlanMath.effectivePerDay(tooSoon, covered, today))
        assertTrue(PlanMath.isTooFast(tooSoon, covered, today))
        assertFalse(PlanMath.isTooFast(byDate, covered, today))
        assertFalse(PlanMath.isTooFast(perDay, covered, today), "no date, no hurry")
        // Everything learned: one ayah is the least a plan can have.
        val allDone = HifzPresets.JUZ_30.associateWith { QuranMeta.ayahCount(it) }
        assertEquals(1, PlanMath.effectivePerDay(byDate, allDone, today))
    }

    @Test
    fun theStartDayStaysWhileTheQueueIsTheSameAndRestartsWhenItChanges() {
        val plan = saved(started = today - 30)
        val same = PlanDraft.of(plan).withPerDay(9).withTarget(today + 40)
        assertEquals(today - 30, PlanMath.startDay(same, plan, today))
        assertEquals(today, PlanMath.startDay(PlanDraft.of(plan).move(0, 1), plan, today))
        assertEquals(today, PlanMath.startDay(PlanDraft.initial(), null, today))
    }

    @Test
    fun theSuggestedDateIsWhenThePaceWouldFinishButAtLeastAWeekAway() {
        assertEquals(today + 20, PlanMath.suggestedTarget(remaining = 100, perDay = 5, today = today))
        assertEquals(today + 7, PlanMath.suggestedTarget(remaining = 5, perDay = 5, today = today))
        assertEquals(today + 60, PlanMath.suggestedTarget(remaining = 0, perDay = 5, today = today))
    }

    @Test
    fun theManzilCircleIsPagesADayAndAbsentWhenNothingIsMemorized() {
        // 11 840 characters are ten pages: over 5 days that is 2 pages a day.
        assertEquals(2f, PlanMath.oldPagesPerDay(oldWeight = 11_840, days = 5))
        assertEquals(1f, PlanMath.oldPagesPerDay(oldWeight = 11_840, days = 10))
        assertNull(PlanMath.oldPagesPerDay(oldWeight = 0, days = 7))
    }

    @Test
    fun theCircleCostsMinutesADayAndIsAbsentWhenNothingIsMemorized() {
        assertEquals(19, PlanMath.oldMinutesPerDay(oldWeight = 22_102, days = 7))
        assertEquals(10, PlanMath.oldMinutesPerDay(oldWeight = 22_102, days = 14))
        assertNull(PlanMath.oldMinutesPerDay(oldWeight = 0, days = 7))
    }

    @Test
    fun aLongDayOfTheCircleGetsAHintWithTheSuggestedLength() {
        // 33 000 characters: a week is 29 minutes a day, 20 days is 10.
        val hint = PlanMath.oldHint(oldWeight = 33_000, days = 7)
        assertEquals(OldCircleHint(minutesPerDay = 29, suggestedDays = 20, suggestedMinutes = 10), hint)
        assertEquals(OldCircleHint(minutesPerDay = 25, suggestedDays = 20, suggestedMinutes = 10), PlanMath.oldHint(33_000, 8))
    }

    @Test
    fun theHintOnlyComesAboveFifteenMinutesAndOnlyWhenALongerCircleHelps() {
        assertNull(PlanMath.oldHint(oldWeight = 0, days = 7), "nothing memorized")
        assertNull(PlanMath.oldHint(oldWeight = 17_000, days = 7), "exactly 15 minutes is still fine")
        assertEquals(16, PlanMath.oldHint(oldWeight = 18_000, days = 7)?.minutesPerDay)
        assertNull(PlanMath.oldHint(oldWeight = 33_000, days = 20), "the suggested length itself needs no hint")
        assertNull(PlanMath.oldHint(oldWeight = 33_000, days = 25), "already longer than suggested")
        // 78 810 characters: even 30 days is 16 minutes, but there is no longer circle to offer.
        assertNull(PlanMath.oldHint(oldWeight = 78_810, days = 30))
        assertEquals(30, PlanMath.oldHint(oldWeight = 78_810, days = 29)?.suggestedDays)
    }

    @Test
    fun aFirstPlanStartsWithTheSuggestedCircleAndASavedPlanKeepsItsOwn() {
        assertEquals(20, PlanDraft.initial(manzilDays = 20).manzilDays)
        assertEquals(30, PlanDraft.initial(manzilDays = 99).manzilDays)
        assertEquals(20, PlanDraft.of(null, suggestedManzilDays = 20).manzilDays)
        assertEquals(7, PlanDraft.of(null).manzilDays)
        // A saved plan is never changed silently, whatever is suggested.
        assertEquals(7, PlanDraft.of(saved(manzil = 7), suggestedManzilDays = 20).manzilDays)
        assertEquals(10, PlanDraft.of(saved(manzil = 10), suggestedManzilDays = 20).manzilDays)
    }

    @Test
    fun theSuggestedCircleOfAFirstPlanIsNotAnEditButMovingBackToAWeekIs() {
        val suggested = build(PlanDraft.initial(manzilDays = 20), oldWeight = 33_000)
        assertFalse(suggested.edited, "the proposed first plan is not an edit")
        assertNull(suggested.oldHint)
        assertEquals(20, suggested.draft.manzilDays)
        assertEquals(10, suggested.oldMinutesPerDay)

        val week = build(PlanDraft.initial(manzilDays = 20).withManzilDays(7), oldWeight = 33_000)
        assertTrue(week.edited, "the person moved the slider")
        assertEquals(29, week.oldHint?.minutesPerDay, "and the hint comes back")
    }

    @Test
    fun theChipSetsTheSliderAndTheHintGoes_andASavedPlanIsOnlyHinted() {
        val plan = saved(manzil = 7)
        val before = build(PlanDraft.of(plan), saved = plan, oldWeight = 33_000)
        assertEquals(7, before.draft.manzilDays, "nothing changed silently")
        assertFalse(before.dirty)
        assertFalse(before.edited)
        val hint = before.oldHint
        assertEquals(20, hint?.suggestedDays)

        val after = build(PlanDraft.of(plan).withManzilDays(hint!!.suggestedDays), saved = plan, oldWeight = 33_000)
        assertEquals(20, after.draft.manzilDays)
        assertNull(after.oldHint)
        assertTrue(after.dirty, "saved only by the Save button")
        assertTrue(after.edited)
    }

    @Test
    fun theNewPassageOfTheCurrentSurahFollowsTheSchedulersRule() {
        fun passage(start: Int, end: Int) = Passage(1, 78, start, end, today - 1, PassageStage.R1, today, today - 1, 0)
        // Nothing learned of a long surah: the first five.
        assertEquals(AyahRange(78, 1, 5), PlanMath.nextPassage(78, 40, emptyList(), daily = 5))
        // Five learned: the next five.
        assertEquals(AyahRange(78, 6, 10), PlanMath.nextPassage(78, 40, listOf(passage(1, 5)), daily = 5))
        // A short surah that is not started is taken whole (6 ayahs <= 5 * 1.5).
        assertEquals(AyahRange(114, 1, 6), PlanMath.nextPassage(114, 6, emptyList(), daily = 5))
        // The whole surah is covered: nothing to learn.
        assertNull(PlanMath.nextPassage(78, 40, listOf(passage(1, 40)), daily = 5))
        assertNull(PlanMath.nextPassage(78, 0, emptyList(), daily = 5))
    }

    // -- Forecast ----------------------------------------------------------------------------------

    private fun forecast(
        total: Int = 100,
        remaining: Int = 60,
        daily: Int = 5,
        finish: Int? = today + 12,
        target: Int? = null,
        required: Int? = null,
        behind: Int = 0,
    ) = PlanForecast(total, remaining, daily, finished = remaining == 0, finishEpochDay = finish, targetEpochDay = target, requiredPerDay = required, behindAyahs = behind)

    @Test
    fun anEmptyQueueHasNoForecastToWaitFor() {
        val draft = PlanDraft.initial().withScope(HifzScope.CUSTOM).copy(customQueue = emptyList())
        val summary = ForecastSummary.of(draft, forecast = null, covered = emptyMap(), today = today)!!
        assertEquals(ForecastHeadline.Empty, summary.headline)
    }

    @Test
    fun theForecastIsAbsentUntilTheRepositoryAnswers() {
        assertNull(ForecastSummary.of(PlanDraft.initial(), forecast = null, covered = emptyMap(), today = today))
    }

    @Test
    fun theHeadlineIsTheFinishDay() {
        val summary = ForecastSummary.of(PlanDraft.initial(), forecast(), emptyMap(), today)!!
        assertEquals(ForecastHeadline.FinishOn(today + 12), summary.headline)
        assertEquals(60, summary.remainingAyahs)
        assertEquals(100, summary.totalAyahs)
        assertNull(summary.requiredPerDay, "no date, no required amount")
        assertEquals(0, summary.behindAyahs)
        assertFalse(summary.tooFast)
    }

    @Test
    fun aFinishedPlanSaysSo() {
        val summary = ForecastSummary.of(PlanDraft.initial(), forecast(remaining = 0, finish = null), emptyMap(), today)!!
        assertEquals(ForecastHeadline.Finished, summary.headline)
    }

    @Test
    fun withADateTheForecastNamesTheAmountADayAndTheArrears() {
        val draft = PlanDraft.initial().withTarget(today + 10)
        val summary = ForecastSummary.of(draft, forecast(target = today + 10, required = 7, behind = 12), emptyMap(), today)!!
        assertEquals(7, summary.requiredPerDay)
        assertEquals(12, summary.behindAyahs)
        // The same numbers are ignored while the person looks at "ayahs a day".
        val perDay = ForecastSummary.of(PlanDraft.initial(), forecast(target = today + 10, required = 7, behind = 12), emptyMap(), today)!!
        assertNull(perDay.requiredPerDay)
        assertEquals(0, perDay.behindAyahs)
    }

    @Test
    fun aDateThatAsksForMoreThanTwentyAyahsADayIsMarked() {
        val draft = PlanDraft.initial().withTarget(today + 3)
        val required = PlanMath.requiredPerDay(PlanMath.remaining(draft.queue, emptyMap()), today, today + 3)
        val summary = ForecastSummary.of(draft, forecast(target = today + 3, required = required), emptyMap(), today)!!
        assertTrue(summary.tooFast)
        assertTrue(summary.requiredPerDay!! > 20)
    }

    // -- The state of the screen -------------------------------------------------------------------

    private fun rows() = (1..114).map { n ->
        SurahRowUi(
            number = n,
            name = "S$n",
            latinName = "S$n",
            translatedName = "",
            arabicName = "ع$n",
            ayahCount = QuranMeta.ayahCount(n),
            status = null,
            learnedFraction = 0f,
            canOpen = true,
            isPinned = false,
        )
    }

    private fun build(
        draft: PlanDraft,
        saved: HifzPlan? = null,
        covered: Map<Int, Int> = emptyMap(),
        statuses: Map<Int, SurahHifzStatus> = emptyMap(),
        forecast: PlanForecast? = null,
        estimate: PaceEstimate? = null,
        oldWeight: Int = 0,
    ) = PlanUiBuilder.build(draft, saved, covered, statuses, rows(), forecast, estimate, oldWeight, today)

    @Test
    fun learnedSurahsAreMarkedAndTheCurrentOneToo() {
        val ui = build(
            PlanDraft.initial(),
            covered = mapOf(114 to 6, 113 to 5, 112 to 2),
            statuses = mapOf(114 to SurahHifzStatus.LEARNED, 113 to SurahHifzStatus.LEARNED, 112 to SurahHifzStatus.LEARNING),
        )
        assertEquals(HifzPresets.JUZ_30, ui.queue.map { it.surah })
        assertEquals(listOf(true, true, false), ui.queue.take(3).map { it.learned })
        assertEquals(listOf(false, false, true), ui.queue.take(3).map { it.current })
        assertEquals(1, ui.queue.count { it.current })
        assertEquals("S114", ui.queue.first().name)
        assertEquals(6, ui.queue.first().ayahCount)
    }

    @Test
    fun aNewPlanCanBeSavedAndAnUnchangedOneCannot() {
        val fresh = build(PlanDraft.initial())
        assertFalse(fresh.hasSavedPlan)
        assertTrue(fresh.dirty)
        assertTrue(fresh.canSave)
        assertFalse(fresh.edited, "the proposed first plan is not an edit")

        val plan = saved()
        val unchanged = build(PlanDraft.of(plan), saved = plan)
        assertTrue(unchanged.hasSavedPlan)
        assertFalse(unchanged.dirty)
        assertFalse(unchanged.edited)

        val changed = build(PlanDraft.of(plan).withPerDay(9), saved = plan)
        assertTrue(changed.dirty)
        assertTrue(changed.edited)
        assertEquals(9, changed.perDay)

        assertTrue(build(PlanDraft.initial().withPerDay(9)).edited, "a first plan that was touched")
    }

    @Test
    fun anEmptyQueueCannotBeSaved() {
        val empty = PlanDraft.initial().withScope(HifzScope.CUSTOM).copy(customQueue = emptyList())
        val ui = build(empty)
        assertFalse(ui.canSave)
        assertTrue(ui.queue.isEmpty())
        assertNull(ui.estimate)
    }

    @Test
    fun theSavedPaceInTheDateModeIsWhatTheDateNeeds() {
        val draft = PlanDraft.initial().withTarget(today + 137)
        val ui = build(draft)
        val remaining = PlanMath.remaining(draft.queue, emptyMap())
        assertEquals((remaining + 136) / 137, ui.perDay)
        assertTrue(ui.canSave)
        assertFalse(build(draft.copy(targetDay = null)).canSave, "a date mode without a date")
    }

    @Test
    fun theEstimateAndTheCircleOfTheOldAreCarriedToTheScreen() {
        val estimate = PaceEstimate(surah = 78, startAyah = 1, endAyah = 5, minutes = 70)
        assertTrue(estimate.overBudget)
        assertFalse(estimate.copy(minutes = 25).overBudget, "25 minutes is exactly the budget")
        assertTrue(estimate.copy(minutes = 26).overBudget)
        val ui = build(PlanDraft.initial().withManzilDays(5), estimate = estimate, oldWeight = 11_840)
        assertEquals(estimate, ui.estimate)
        assertEquals(2f, ui.oldPagesPerDay)
        assertNull(build(PlanDraft.initial(), oldWeight = 0).oldPagesPerDay)
    }
}
