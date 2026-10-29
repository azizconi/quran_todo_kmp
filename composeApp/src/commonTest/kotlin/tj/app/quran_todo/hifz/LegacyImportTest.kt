package tj.app.quran_todo.hifz

import kotlinx.datetime.TimeZone
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.hifz.legacy.LegacyImport
import tj.app.quran_todo.hifz.legacy.LegacyImportResult
import tj.app.quran_todo.hifz.legacy.LegacyInput
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.PassageStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyImportTest {
    private val m = Fx.flat // real ayah counts, 100 characters an ayah, 10 ayahs a page of a surah
    private val today = 5_000
    private val dayMillis = 86_400_000L

    private fun global(surah: Int, ayah: Int) = QuranFacts.firstAyahNumber(surah) + ayah - 1

    private fun surah(number: Int, status: SurahTodoStatus) = SurahTodoEntity(number, status)

    private fun ayah(surah: Int, ayah: Int, status: AyahTodoStatus, updatedDay: Int = 4_000) =
        AyahTodoEntity(global(surah, ayah), surah, status, updatedDay * dayMillis + 3_600_000)

    private fun review(surah: Int, ayah: Int, index: Int, dueDay: Int, lastDay: Int = 4_990) =
        AyahReviewEntity(global(surah, ayah), surah, dueDay * dayMillis + 7_200_000, index, lastDay * dayMillis + 7_200_000)

    private fun run(
        surahs: List<SurahTodoEntity> = emptyList(),
        ayahs: List<AyahTodoEntity> = emptyList(),
        reviews: List<AyahReviewEntity> = emptyList(),
        weak: Set<String> = emptySet(),
        goal: Int? = null,
    ): LegacyImportResult =
        LegacyImport.run(LegacyInput(surahs, ayahs, reviews, weak, goal, today, TimeZone.UTC), m)

    private fun LegacyImportResult.of(surah: Int) = passages.filter { it.surah == surah }.sortedBy { it.startAyah }

    // ---- 1. learned surahs ----

    @Test
    fun aLearnedSurahIsCoveredByOldPassagesNeverReviewed() {
        val result = run(surahs = listOf(surah(112, SurahTodoStatus.LEARNED)))
        val p = result.passages.single()
        assertEquals(Triple(112, 1, 4), Triple(p.surah, p.startAyah, p.endAyah))
        assertEquals(PassageStage.OLD, p.stage)
        assertNull(p.nextDueEpochDay)
        assertNull(p.lastReviewedEpochDay)
        assertEquals(0, p.lapses)
        assertEquals(0L, p.id)
        assertNull(result.plan, "a learned surah is not a surah being learned")
    }

    @Test
    fun aLongLearnedSurahIsCutByPages() {
        val parts = run(surahs = listOf(surah(2, SurahTodoStatus.LEARNED))).passages
        assertEquals(29, parts.size) // 286 ayahs, 10 on a page
        assertEquals(1 to 10, parts.first().let { it.startAyah to it.endAyah })
        assertEquals(281 to 286, parts.last().let { it.startAyah to it.endAyah })
        assertEquals(286, parts.sumOf { it.ayahCount })
        assertTrue(parts.all { it.stage == PassageStage.OLD && it.lastReviewedEpochDay == null })
    }

    // ---- 2. learned ayahs ----

    @Test
    fun learnedAyahsBecomeOldPassagesPerRun() {
        val result = run(
            ayahs = listOf(
                ayah(112, 1, AyahTodoStatus.LEARNED), ayah(112, 2, AyahTodoStatus.LEARNED),
                ayah(114, 2, AyahTodoStatus.LEARNED),
            ),
        )
        assertEquals(listOf(1 to 2), result.of(112).map { it.startAyah to it.endAyah })
        assertEquals(listOf(2 to 2), result.of(114).map { it.startAyah to it.endAyah })
        assertTrue(result.passages.all { it.stage == PassageStage.OLD && it.lastReviewedEpochDay == null })
        assertEquals(4_000, result.of(112).single().learnedEpochDay, "the day of the old record")
    }

    @Test
    fun learnedAyahsInALearnedSurahAddNothing() {
        val result = run(
            surahs = listOf(surah(112, SurahTodoStatus.LEARNED)),
            ayahs = listOf(ayah(112, 2, AyahTodoStatus.LEARNED), ayah(112, 3, AyahTodoStatus.LEARNING)),
            reviews = listOf(review(112, 3, 1, today)),
        )
        assertEquals(1, result.passages.size)
        assertEquals(PassageStage.OLD, result.passages.single().stage)
    }

    // ---- 3. the ladder ----

    @Test
    fun learningAyahsTakeTheStageFromTheReviewAndTheMinimumOverThePassage() {
        val result = run(
            ayahs = (1..3).map { ayah(114, it, AyahTodoStatus.LEARNING) },
            reviews = listOf(
                review(114, 1, index = 2, dueDay = 5_003),
                review(114, 2, index = 1, dueDay = 5_001),
                review(114, 3, index = 3, dueDay = 5_010),
            ),
        )
        val p = result.passages.single()
        assertEquals(1 to 3, p.startAyah to p.endAyah)
        assertEquals(PassageStage.R3, p.stage, "index 1 is the smallest")
        assertEquals(5_001, p.nextDueEpochDay, "the smallest due day")
    }

    @Test
    fun theIntervalIndexMapsToTheStages() {
        val expected = listOf(
            PassageStage.R1, PassageStage.R3, PassageStage.R7, PassageStage.R14, PassageStage.R30, PassageStage.OLD, PassageStage.OLD,
        )
        // Seven ayahs of Al-Fatiha, one passage each: they are cut by page only, so use different pages through different surahs.
        val surahs = listOf(114, 113, 112, 111, 110, 109, 108)
        val result = run(
            ayahs = surahs.map { ayah(it, 1, AyahTodoStatus.LEARNING) },
            reviews = surahs.mapIndexed { i, s -> review(s, 1, index = i, dueDay = 5_000 + 10 * i) },
        )
        assertEquals(expected, surahs.map { result.of(it).single().stage })
        assertEquals(5_020, result.of(112).single().nextDueEpochDay)
        assertNull(result.of(109).single().nextDueEpochDay, "an OLD passage has no due day")
    }

    @Test
    fun aDueDayInThePastStaysInThePastSoItIsOverdue() {
        val result = run(
            ayahs = listOf(ayah(114, 1, AyahTodoStatus.LEARNING)),
            reviews = listOf(review(114, 1, index = 2, dueDay = 4_990)),
        )
        assertEquals(4_990, result.passages.single().nextDueEpochDay)
    }

    @Test
    fun aLearningAyahWithoutAReviewIsR1DueToday() {
        val result = run(ayahs = listOf(ayah(114, 1, AyahTodoStatus.LEARNING)))
        val p = result.passages.single()
        assertEquals(PassageStage.R1, p.stage)
        assertEquals(today, p.nextDueEpochDay)
    }

    @Test
    fun anAyahWithoutAReviewInAPassageMakesItR1DueToday() {
        val result = run(
            ayahs = listOf(ayah(112, 1, AyahTodoStatus.LEARNING), ayah(112, 2, AyahTodoStatus.LEARNING)),
            reviews = listOf(review(112, 2, index = 3, dueDay = 5_020)),
        )
        val p = result.passages.single()
        assertEquals(PassageStage.R1, p.stage)
        assertEquals(today, p.nextDueEpochDay)
    }

    @Test
    fun anAyahWithAReviewButWithoutAStatusJoinsTheLadder() {
        val result = run(reviews = listOf(review(113, 4, index = 0, dueDay = 5_002)))
        val p = result.passages.single()
        assertEquals(Triple(113, 4, 4), Triple(p.surah, p.startAyah, p.endAyah))
        assertEquals(PassageStage.R1, p.stage)
        assertEquals(5_002, p.nextDueEpochDay)
    }

    @Test
    fun learnedAyahsKeepTheirOldClassEvenWithAReview() {
        val result = run(
            ayahs = listOf(ayah(112, 1, AyahTodoStatus.LEARNED)),
            reviews = listOf(review(112, 1, index = 1, dueDay = today)),
        )
        assertEquals(PassageStage.OLD, result.passages.single().stage)
    }

    // ---- 4. cutting ----

    @Test
    fun runsAreCutByThePagesOfTheMushaf() {
        // Al-Baqarah 8..14: ayahs 8..10 are on the first page, 11..14 on the second.
        val result = run(ayahs = (8..14).map { ayah(2, it, AyahTodoStatus.LEARNED) })
        assertEquals(listOf(8 to 10, 11 to 14), result.of(2).map { it.startAyah to it.endAyah })
    }

    @Test
    fun aRunDoesNotCrossAGapOrAChangeOfClass() {
        val gap = run(ayahs = listOf(1, 2, 4, 5).map { ayah(2, it, AyahTodoStatus.LEARNED) })
        assertEquals(listOf(1 to 2, 4 to 5), gap.of(2).map { it.startAyah to it.endAyah })

        val mixed = run(ayahs = listOf(ayah(2, 5, AyahTodoStatus.LEARNED), ayah(2, 6, AyahTodoStatus.LEARNING)))
        assertEquals(listOf(PassageStage.OLD, PassageStage.R1), mixed.of(2).map { it.stage })
        assertEquals(listOf(5 to 5, 6 to 6), mixed.of(2).map { it.startAyah to it.endAyah })
    }

    // ---- 5. weak ayahs ----

    @Test
    fun weakKeysAreReadAsGlobalWhenTheyFitTheGlobalRangeOtherwiseAsNumbersInTheSurah() {
        val result = run(
            surahs = listOf(surah(112, SurahTodoStatus.LEARNED)),
            ayahs = (40..60).map { ayah(2, it, AyahTodoStatus.LEARNED) },
            weak = setOf(
                "112:${global(112, 3)}", // global number of 112:3
                "112:2", // 2 is not in 6222..6225: a number in the surah
                "2:${global(2, 43)}", // 50 is in the global range of surah 2 (8..293): 2:43
                "114:99", // neither: dropped
                "113:3", // in the surah, but nothing is learned there: dropped
                "garbage",
            ),
        )
        assertEquals(mapOf(2 to 2, 3 to 2), result.of(112).single().hard)
        val holder = result.of(2).single { 43 in it }
        assertEquals(mapOf(43 to 2), holder.hard)
        assertEquals(3, result.droppedWeakKeys)
        assertEquals(2, result.passages.count { it.hard.isNotEmpty() })
    }

    @Test
    fun aGlobalKeyWinsOverAnAmbiguousNumberInTheSurah() {
        // "2:50": 50 is in the global range of surah 2 (8..293) -> 2:43, not 2:50.
        val result = run(surahs = listOf(surah(2, SurahTodoStatus.LEARNED)), weak = setOf("2:50"))
        val marked = result.passages.single { it.hard.isNotEmpty() }
        assertEquals(setOf(43), marked.hard.keys)
    }

    // ---- 6. the plan ----

    @Test
    fun surahsBeingLearnedMakeACustomPlanFromTheShortestToTheLongestInTheLastJuz() {
        val result = run(surahs = listOf(surah(112, SurahTodoStatus.LEARNING), surah(114, SurahTodoStatus.LEARNING), surah(80, SurahTodoStatus.LEARNING)))
        val plan = assertNotNull(result.plan)
        assertEquals(HifzScope.CUSTOM, plan.scope)
        assertEquals(listOf(114, 112, 80), plan.surahOrder)
        assertEquals(5, plan.dailyNewAyahs)
        assertEquals(today, plan.startedEpochDay)
        assertNull(plan.targetEpochDay)
        assertEquals(7, plan.manzilDays)
        assertTrue(result.passages.isEmpty())
    }

    @Test
    fun otherSurahsAreOrderedByNumber() {
        val result = run(surahs = listOf(surah(112, SurahTodoStatus.LEARNING), surah(2, SurahTodoStatus.LEARNING), surah(36, SurahTodoStatus.LEARNING)))
        assertEquals(listOf(2, 36, 112), result.plan?.surahOrder)
    }

    @Test
    fun partlyCoveredSurahsAreBeingLearnedToo() {
        val result = run(ayahs = listOf(ayah(2, 1, AyahTodoStatus.LEARNED), ayah(114, 1, AyahTodoStatus.LEARNING)))
        assertEquals(listOf(2, 114), result.plan?.surahOrder)
    }

    @Test
    fun learnedAndFullyCoveredSurahsAreNotInThePlan() {
        val result = run(
            surahs = listOf(surah(112, SurahTodoStatus.LEARNED), surah(113, SurahTodoStatus.LEARNING), surah(114, SurahTodoStatus.LEARNING)),
            ayahs = (1..6).map { ayah(114, it, AyahTodoStatus.LEARNED) },
        )
        assertEquals(listOf(113), result.plan?.surahOrder)
    }

    @Test
    fun noSurahBeingLearnedMeansNoPlan() {
        assertNull(run(surahs = listOf(surah(112, SurahTodoStatus.LEARNED))).plan)
        assertNull(run().plan)
        assertTrue(run().passages.isEmpty())
    }

    @Test
    fun theDailyGoalOfTheOldHomeScreenIsUsedWhenItFitsThePlan() {
        val learning = listOf(surah(112, SurahTodoStatus.LEARNING))
        assertEquals(7, run(surahs = learning, goal = 7).plan?.dailyNewAyahs)
        assertEquals(20, run(surahs = learning, goal = 20).plan?.dailyNewAyahs)
        assertEquals(1, run(surahs = learning, goal = 1).plan?.dailyNewAyahs)
        assertEquals(5, run(surahs = learning, goal = 21).plan?.dailyNewAyahs)
        assertEquals(5, run(surahs = learning, goal = 0).plan?.dailyNewAyahs)
        assertEquals(5, run(surahs = learning, goal = null).plan?.dailyNewAyahs)
    }

    // ---- everything together ----

    @Test
    fun aTypicalOldUserIsConvertedAsAWhole() {
        val result = run(
            surahs = listOf(
                surah(112, SurahTodoStatus.LEARNED), surah(113, SurahTodoStatus.LEARNED),
                surah(114, SurahTodoStatus.LEARNING), surah(110, SurahTodoStatus.LEARNING),
            ),
            ayahs = listOf(
                ayah(114, 1, AyahTodoStatus.LEARNED), ayah(114, 2, AyahTodoStatus.LEARNED), ayah(114, 3, AyahTodoStatus.LEARNING),
            ),
            reviews = listOf(review(114, 3, 1, 5_002)),
            weak = setOf("114:2", "113:${global(113, 5)}"),
            goal = 3,
        )
        assertEquals(listOf(1 to 4), result.of(112).map { it.startAyah to it.endAyah })
        assertEquals(listOf(1 to 5), result.of(113).map { it.startAyah to it.endAyah })
        assertEquals(listOf(PassageStage.OLD, PassageStage.R3), result.of(114).map { it.stage })
        assertEquals(mapOf(2 to 2), result.of(114).first().hard)
        assertEquals(mapOf(5 to 2), result.of(113).single().hard)
        assertEquals(listOf(114, 110), result.plan?.surahOrder)
        assertEquals(3, result.plan?.dailyNewAyahs)
        assertEquals(0, result.droppedWeakKeys)
    }
}
