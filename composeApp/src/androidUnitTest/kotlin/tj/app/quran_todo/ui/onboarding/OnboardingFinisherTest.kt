package tj.app.quran_todo.ui.onboarding

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import tj.app.quran_todo.hifz.HifzPresets
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.ReviewResult
import tj.app.quran_todo.hifz.backup.BackupData
import tj.app.quran_todo.hifz.backup.VerifiedBackup
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.NewPassageResult
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.model.SurahCoverage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.Manzil
import tj.app.quran_todo.hifz.schedule.PlanForecast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The write of the last screen against a fake repository: the order of the calls, nothing written on the way, a repeat that does
 * not write twice, and a failure that leaves the screen able to try again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingFinisherTest {
    /**
     * Records every write; `markKnown` is idempotent like the real one (a covered surah is left alone). The weight of the old
     * grows with the surahs marked as known ([weightOfSurah] characters each), so a read of it before `markKnown` is seen.
     */
    private class FakeHifz : HifzRepository {
        val calls = mutableListOf<String>()
        val known = linkedSetOf<Int>()
        var weightOfSurah = 0
        var failOldWeight = false
        var plan: HifzPlan? = null
        var failOn: String? = null
        var gate: CompletableDeferred<Unit>? = null

        private fun record(call: String) {
            calls += call
            if (failOn != null && call.startsWith(failOn!!)) {
                failOn = null
                error("boom: $call")
            }
        }

        override suspend fun markKnown(surah: Int, startAyah: Int, endAyah: Int, today: Int) {
            gate?.await()
            record("markKnown($surah)")
            known += surah
        }

        override suspend fun setPlan(
            scope: HifzScope,
            surahOrder: List<Int>,
            dailyNewAyahs: Int,
            targetEpochDay: Int?,
            manzilDays: Int,
            today: Int,
        ) {
            record("setPlan($scope,${surahOrder.size},$dailyNewAyahs,$targetEpochDay)")
            plan = HifzPlan(scope, surahOrder, dailyNewAyahs, targetEpochDay, today, manzilDays)
        }

        // Nothing else is touched by the onboarding.
        override fun lesson(today: Int): Flow<LessonPlan> = error("unused")
        override fun lesson(today: Flow<Int>): Flow<LessonPlan> = error("unused")
        override fun plan(): Flow<HifzPlan?> = error("unused")
        override fun forecast(today: Int): Flow<PlanForecast?> = error("unused")
        override fun forecast(today: Flow<Int>): Flow<PlanForecast?> = error("unused")
        override fun forecast(plan: HifzPlan, today: Int): Flow<PlanForecast> = error("unused")
        override fun streak(today: Int): Flow<Int> = error("unused")
        override fun streak(today: Flow<Int>): Flow<Int> = error("unused")
        override fun surahStatuses(): Flow<Map<Int, SurahHifzStatus>> = error("unused")
        override fun coveredCounts(): Flow<Map<Int, Int>> = error("unused")
        override fun coveredAyahs(): Flow<Map<Int, Set<Int>>> = error("unused")
        override fun hardAyahs(): Flow<Map<Int, Set<Int>>> = error("unused")
        override fun surahCoverage(surah: Int): Flow<SurahCoverage> = error("unused")
        override fun reviewLog(fromDay: Int, toDay: Int): Flow<List<ReviewLogEntry>> = error("unused")
        override fun oldWeight(): Flow<Int> = flow {
            if (failOldWeight) error("boom: oldWeight")
            emit(known.size * weightOfSurah)
        }
        override suspend fun estimateMinutes(surah: Int, startAyah: Int, endAyah: Int, isNew: Boolean): Int = error("unused")
        override suspend fun completeNew(
            surah: Int, startAyah: Int, endAyah: Int, grade: ReviewGrade, hardAyahs: Set<Int>, today: Int,
        ): NewPassageResult = error("unused")
        override suspend fun review(passageId: Long, grade: ReviewGrade, hardAyahs: Set<Int>, today: Int): ReviewResult? = error("unused")
        override suspend fun logCheck(
            surah: Int, startAyah: Int, endAyah: Int, grade: ReviewGrade, hardAyahs: Set<Int>, today: Int,
        ): Unit = error("unused")
        override suspend fun forget(surah: Int, startAyah: Int, endAyah: Int): Unit = error("unused")
        override suspend fun learnNext(surah: Int, today: Int): Unit = error("unused")
        override suspend fun clearPlan(): Unit = error("unused")
        override suspend fun importLegacyIfNeeded(): Unit = error("unused")
        override suspend fun exportBackupData(): BackupData = error("unused")
        override suspend fun replaceWithBackup(backup: VerifiedBackup): Unit = error("unused")
    }

    private val today = 20_000
    private val plan = PlanToSave(HifzScope.JUZ_30, HifzPresets.JUZ_30, 5, null, 7)

    @Test
    fun writesTheKnownSurahsThenThePlanThenTheFlag() = runTest {
        val repo = FakeHifz()
        val order = mutableListOf<String>()
        val finisher = OnboardingFinisher(repo) { order += "flag" ; order += repo.calls }
        val result = finisher.finish(OnboardingCommit(known = listOf(114, 113, 112), plan = plan), today)
        assertEquals(FinishResult.Done, result)
        assertEquals(
            listOf("markKnown(114)", "markKnown(113)", "markKnown(112)", "setPlan(JUZ_30,37,5,null)"),
            repo.calls,
        )
        // The flag comes last: when it is set, every write has happened.
        assertEquals(listOf("flag") + repo.calls, order)
        assertEquals(setOf(114, 113, 112), repo.known)
        assertEquals(HifzPresets.JUZ_30, repo.plan?.surahOrder)
    }

    @Test
    fun withNothingKnownOnlyThePlanIsWritten() = runTest {
        val repo = FakeHifz()
        var passed = false
        val result = OnboardingFinisher(repo) { passed = true }.finish(OnboardingCommit(known = emptyList(), plan = plan), today)
        assertEquals(FinishResult.Done, result)
        assertEquals(listOf("setPlan(JUZ_30,37,5,null)"), repo.calls)
        assertTrue(passed)
    }

    @Test
    fun theReadPathMakesNoPlanAndNoProgress() = runTest {
        val repo = FakeHifz()
        var passed = false
        val result = OnboardingFinisher(repo) { passed = true }.finish(OnboardingCommit(known = emptyList(), plan = null), today)
        assertEquals(FinishResult.Done, result)
        assertTrue(repo.calls.isEmpty(), "no write at all: ${repo.calls}")
        assertTrue(passed)
    }

    @Test
    fun aRepeatedPressAfterSuccessDoesNotWriteAgain() = runTest {
        val repo = FakeHifz()
        var flagged = 0
        val finisher = OnboardingFinisher(repo) { flagged++ }
        val commit = OnboardingCommit(known = listOf(114, 113), plan = plan)
        assertEquals(FinishResult.Done, finisher.finish(commit, today))
        val after = repo.calls.toList()
        assertEquals(FinishResult.Done, finisher.finish(commit, today))
        assertEquals(FinishResult.Done, finisher.finish(commit, today))
        assertEquals(after, repo.calls, "no second write")
        assertEquals(1, flagged)
    }

    @Test
    fun aPressWhileTheFirstOneIsWritingDoesNothing() = runTest {
        val repo = FakeHifz().apply { gate = CompletableDeferred() }
        val finisher = OnboardingFinisher(repo) { }
        val commit = OnboardingCommit(known = listOf(114), plan = plan)
        var first: FinishResult? = null
        val job = launch { first = finisher.finish(commit, today) }
        advanceUntilIdle()
        // The first press is stuck inside markKnown: a second one is turned away at once.
        assertEquals(FinishResult.Busy, finisher.finish(commit, today))
        assertTrue(repo.calls.isEmpty())
        repo.gate!!.complete(Unit)
        job.join()
        assertEquals(FinishResult.Done, first)
        assertEquals(listOf("markKnown(114)", "setPlan(JUZ_30,37,5,null)"), repo.calls)
    }

    @Test
    fun aFailureLeavesTheScreenAndNothingAfterItRuns() = runTest {
        val repo = FakeHifz().apply { failOn = "markKnown(113)" }
        var passed = false
        val finisher = OnboardingFinisher(repo) { passed = true }
        val result = finisher.finish(OnboardingCommit(known = listOf(114, 113, 112), plan = plan), today)
        assertIs<FinishResult.Failed>(result)
        assertFalse(passed, "the flag is not set after a failure")
        assertEquals(listOf("markKnown(114)", "markKnown(113)"), repo.calls)
        assertEquals(null, repo.plan)
    }

    @Test
    fun aRetryAfterAFailureFinishesWithTheSameResultAsAnUninterruptedRun() = runTest {
        val clean = FakeHifz()
        OnboardingFinisher(clean) { }.finish(OnboardingCommit(known = listOf(114, 113, 112), plan = plan), today)

        val repo = FakeHifz().apply { failOn = "setPlan" }
        var passed = 0
        val finisher = OnboardingFinisher(repo) { passed++ }
        val commit = OnboardingCommit(known = listOf(114, 113, 112), plan = plan)
        assertIs<FinishResult.Failed>(finisher.finish(commit, today))
        assertEquals(0, passed)
        assertEquals(FinishResult.Done, finisher.finish(commit, today))
        assertEquals(1, passed)
        // The surahs were marked twice by the calls, but the repository leaves covered surahs alone: the state is the same.
        assertEquals(clean.known, repo.known)
        assertEquals(clean.plan, repo.plan)
    }

    // ---- the length of the circle of the old ----

    @Test
    fun theCircleIsSuggestedFromTheWeightOfWhatWasJustMarkedKnown() = runTest {
        // Three known surahs of 11 000 characters each = 33 000 characters of old: a week would be ~29 minutes a day.
        val repo = FakeHifz().apply { weightOfSurah = 11_000 }
        val result = OnboardingFinisher(repo) { }.finish(OnboardingCommit(known = listOf(114, 113, 112), plan = plan), today)
        assertEquals(FinishResult.Done, result)
        assertEquals(Manzil.suggestedDays(33_000), repo.plan?.manzilDays)
        assertEquals(20, repo.plan?.manzilDays)
        assertTrue(Manzil.minutesPerDay(33_000, 20) in 9..11)
    }

    @Test
    fun theWeightIsReadAfterMarkKnownNotBefore() = runTest {
        // With nothing marked the weight is 0 (a week); after the three surahs it is big: the suggestion follows the later read.
        val repo = FakeHifz().apply { weightOfSurah = 30_000 }
        OnboardingFinisher(repo) { }.finish(OnboardingCommit(known = listOf(114, 113, 112), plan = plan), today)
        assertEquals(30, repo.plan?.manzilDays, "90 000 characters: ten minutes would need more than a month, so the longest circle")
    }

    @Test
    fun nothingKnownKeepsTheWeekCircle() = runTest {
        val repo = FakeHifz().apply { weightOfSurah = 30_000 }
        OnboardingFinisher(repo) { }.finish(OnboardingCommit(known = emptyList(), plan = plan), today)
        assertEquals(7, repo.plan?.manzilDays)
    }

    @Test
    fun aSmallKnownSetKeepsTheWeekCircle() = runTest {
        // Al-Fatiha and a few short surahs: well under ten minutes a day even with a week.
        val repo = FakeHifz().apply { weightOfSurah = 800 }
        OnboardingFinisher(repo) { }.finish(OnboardingCommit(known = listOf(1, 114, 113, 112), plan = plan), today)
        assertEquals(7, repo.plan?.manzilDays)
    }

    @Test
    fun theReadPathDoesNotReadTheWeightEither() = runTest {
        val repo = FakeHifz().apply { failOldWeight = true }
        val result = OnboardingFinisher(repo) { }.finish(OnboardingCommit(known = emptyList(), plan = null), today)
        assertEquals(FinishResult.Done, result)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun aFailedWeightReadFailsTheFinishAndALaterTryWorks() = runTest {
        val repo = FakeHifz().apply { weightOfSurah = 11_000; failOldWeight = true }
        var passed = 0
        val finisher = OnboardingFinisher(repo) { passed++ }
        val commit = OnboardingCommit(known = listOf(114, 113, 112), plan = plan)
        assertIs<FinishResult.Failed>(finisher.finish(commit, today))
        assertEquals(0, passed)
        assertEquals(null, repo.plan)
        repo.failOldWeight = false
        assertEquals(FinishResult.Done, finisher.finish(commit, today))
        assertEquals(1, passed)
        assertEquals(20, repo.plan?.manzilDays)
    }
}
