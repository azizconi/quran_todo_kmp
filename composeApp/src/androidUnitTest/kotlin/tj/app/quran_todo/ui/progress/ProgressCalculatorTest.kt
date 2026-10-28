package tj.app.quran_todo.ui.progress

import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.ui.navigation.Origin
import tj.app.quran_todo.ui.progress.components.toReaderDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressCalculatorTest {
    private val counts = ProgressCalculator.StandardAyahCounts

    /** Ayah [local] of [surah] as the database stores it: with its global number (1..6236). */
    private fun todo(surah: Int, local: Int, status: AyahTodoStatus, updatedAt: Long = 0L): AyahTodoEntity {
        val global = ProgressCalculator.offsets(counts)[surah - 1] + local
        return AyahTodoEntity(ayahNumber = global, surahNumber = surah, status = status, updatedAt = updatedAt)
    }

    private fun totalsOf(
        statuses: Map<Int, SurahTodoStatus> = emptyMap(),
        todos: List<AyahTodoEntity> = emptyList(),
    ) = ProgressCalculator.totals(ProgressCalculator.surahProgress(statuses, todos))

    // -- Totals ------------------------------------------------------------------------------------

    @Test
    fun theStandardNumberingHas114SurahsAnd6236Ayahs() {
        assertEquals(114, counts.size)
        assertEquals(6236, counts.sum())
        assertEquals(7, counts[0])
        assertEquals(286, counts[1])
        assertEquals(6, counts[113])
    }

    @Test
    fun nothingStartedIsAllZero() {
        val totals = totalsOf()
        assertEquals(0, totals.learnedAyahs)
        assertEquals(0, totals.learningAyahs)
        assertEquals(0, totals.learnedSurahs)
        assertEquals(0, totals.learningSurahs)
        assertEquals(6236, totals.totalAyahs)
        assertEquals(0, totals.percent)
        assertFalse(totals.belowOnePercent)
        assertTrue(totals.isUntouched)
    }

    @Test
    fun aMemorizedSurahCountsItsAyahsOnce() {
        // Al-Fatihah is marked memorized AND all seven of its ayahs are in the ayah table.
        val todos = (1..7).map { todo(1, it, AyahTodoStatus.LEARNED) }
        val totals = totalsOf(mapOf(1 to SurahTodoStatus.LEARNED), todos)
        assertEquals(7, totals.learnedAyahs)
        assertEquals(1, totals.learnedSurahs)
    }

    @Test
    fun ayahsOfAMemorizedSurahAreNotAlsoInWork() {
        val todos = listOf(
            todo(1, 1, AyahTodoStatus.LEARNED),
            todo(1, 2, AyahTodoStatus.LEARNING),
            todo(1, 3, AyahTodoStatus.LEARNING),
        )
        val totals = totalsOf(mapOf(1 to SurahTodoStatus.LEARNED), todos)
        assertEquals(7, totals.learnedAyahs)
        assertEquals(0, totals.learningAyahs)
    }

    @Test
    fun memorizedAyahsOfAnUnfinishedSurahAreCounted() {
        val todos = listOf(
            todo(2, 1, AyahTodoStatus.LEARNED),
            todo(2, 2, AyahTodoStatus.LEARNED),
            todo(2, 3, AyahTodoStatus.LEARNING),
        )
        val totals = totalsOf(mapOf(2 to SurahTodoStatus.LEARNING), todos)
        assertEquals(2, totals.learnedAyahs)
        assertEquals(1, totals.learningAyahs)
        assertEquals(0, totals.learnedSurahs)
        assertEquals(1, totals.learningSurahs)
    }

    @Test
    fun memorizedSurahsAndAyahsOfOtherSurahsAddUp() {
        val todos = listOf(
            todo(1, 1, AyahTodoStatus.LEARNED), // inside the memorized surah: counted once
            todo(2, 1, AyahTodoStatus.LEARNED),
            todo(2, 2, AyahTodoStatus.LEARNED),
        )
        val totals = totalsOf(mapOf(1 to SurahTodoStatus.LEARNED, 2 to SurahTodoStatus.LEARNING), todos)
        assertEquals(7 + 2, totals.learnedAyahs)
    }

    @Test
    fun aSurahMarkedMemorizingWithoutAyahsIsInProgressWithNothingMemorized() {
        val progress = ProgressCalculator.surahProgress(mapOf(3 to SurahTodoStatus.LEARNING), emptyList())
        val surah = progress[2]
        assertEquals(MapState.Learning, surah.state)
        assertEquals(0, surah.learnedAyahs)
        assertEquals(0f, surah.fraction)
    }

    @Test
    fun anUnmarkedSurahWithMarkedAyahsIsInProgress() {
        val progress = ProgressCalculator.surahProgress(
            statuses = emptyMap(),
            ayahTodos = listOf(todo(4, 1, AyahTodoStatus.LEARNED), todo(4, 2, AyahTodoStatus.LEARNING)),
        )
        assertEquals(MapState.Learning, progress[3].state)
        assertEquals(1, progress[3].learnedAyahs)
        assertEquals(1, progress[3].learningAyahs)
        assertEquals(MapState.NotStarted, progress[4].state)
    }

    @Test
    fun aSurahWithEveryAyahMemorizedIsMemorized() {
        val todos = (1..7).map { todo(1, it, AyahTodoStatus.LEARNED) }
        val progress = ProgressCalculator.surahProgress(mapOf(1 to SurahTodoStatus.LEARNING), todos)
        assertEquals(MapState.Learned, progress[0].state)
        assertEquals(1f, progress[0].fraction)
        assertEquals(1, ProgressCalculator.totals(progress).learnedSurahs)
        assertEquals(0, ProgressCalculator.totals(progress).learningSurahs)
    }

    @Test
    fun theFractionOfASurahIsMemorizedOverAllAyahs() {
        val todos = (1..71).map { todo(2, it, AyahTodoStatus.LEARNED) }
        val surah = ProgressCalculator.surahProgress(emptyMap(), todos)[1]
        assertEquals(71f / 286f, surah.fraction)
    }

    @Test
    fun theSameAyahIsCountedOnce() {
        val one = todo(5, 1, AyahTodoStatus.LEARNED)
        val totals = totalsOf(todos = listOf(one, one.copy(updatedAt = 5L)))
        assertEquals(1, totals.learnedAyahs)
    }

    @Test
    fun rowsOutsideTheQuranAreIgnored() {
        val stray = AyahTodoEntity(ayahNumber = 99_999, surahNumber = 115, status = AyahTodoStatus.LEARNED, updatedAt = 0)
        val totals = totalsOf(todos = listOf(stray))
        assertEquals(0, totals.learnedAyahs)
    }

    @Test
    fun aSurahNeverHoldsMoreMemorizedAyahsThanItHas() {
        // Seven distinct "ayahs" claim to belong to the three-ayah surah 108.
        val rows = (1..7).map { AyahTodoEntity(10_000 + it, 108, AyahTodoStatus.LEARNED, 0) }
        val surah = ProgressCalculator.surahProgress(emptyMap(), rows)[107]
        assertEquals(3, surah.learnedAyahs)
    }

    @Test
    fun everythingMemorizedIsOneHundredPercent() {
        val statuses = (1..114).associateWith { SurahTodoStatus.LEARNED }
        val totals = totalsOf(statuses)
        assertEquals(6236, totals.learnedAyahs)
        assertEquals(114, totals.learnedSurahs)
        assertEquals(100, totals.percent)
        assertEquals(1f, totals.fraction)
    }

    // -- Percent -----------------------------------------------------------------------------------

    @Test
    fun aFewAyahsShowLessThanOnePercentInsteadOfZero() {
        val totals = totalsOf(todos = (1..5).map { todo(1, it, AyahTodoStatus.LEARNED) })
        assertEquals(0, totals.percent)
        assertTrue(totals.belowOnePercent)
    }

    @Test
    fun percentIsRoundedToTheNearestWholeNumber() {
        // 412 / 6236 = 6.6%
        val totals = ProgressTotals(412, 0, 6236, 0, 0)
        assertEquals(7, totals.percent)
        assertFalse(totals.belowOnePercent)
        assertEquals(1, ProgressTotals(62, 0, 6236, 0, 0).percent)
    }

    @Test
    fun almostEverythingNeverRoundsUpToOneHundred() {
        assertEquals(99, ProgressTotals(6235, 0, 6236, 0, 0).percent)
        assertEquals(100, ProgressTotals(6236, 0, 6236, 0, 0).percent)
    }

    // -- Weak ayahs --------------------------------------------------------------------------------

    @Test
    fun weakKeysCarryGlobalAyahNumbersThatBecomeNumbersInsideTheSurah() {
        // Global 8 is ayah 1 of Al-Baqarah, global 7 is the last ayah of Al-Fatihah.
        val weak = ProgressCalculator.weakSpots(setOf("2:8", "2:12", "1:7"))
        assertEquals(listOf(2, 1), weak.map { it.surah })
        assertEquals(listOf(1, 5), weak[0].ayahs)
        assertEquals(listOf(7), weak[1].ayahs)
    }

    @Test
    fun weakKeysThatDoNotNameAnAyahOfTheirSurahAreDropped() {
        val weak = ProgressCalculator.weakSpots(
            setOf("1:8", "0:1", "115:1", "abc", "2", "2:x", "2:7", "1:3"),
        )
        // "1:8" is global ayah 8 (it belongs to surah 2), "2:7" is global ayah 7 (it belongs to surah 1).
        assertEquals(listOf(1), weak.map { it.surah })
        assertEquals(listOf(3), weak[0].ayahs)
    }

    @Test
    fun theSurahWithMostWeakAyahsComesFirst() {
        val weak = ProgressCalculator.weakSpots(setOf("3:300", "3:301", "3:302", "2:10", "2:11", "1:1"))
        assertEquals(listOf(3, 2, 1), weak.map { it.surah })
        assertEquals(listOf(3, 2, 1), weak.map { it.ayahs.size })
        // Equal counts: the lower surah first.
        val tie = ProgressCalculator.weakSpots(setOf("2:10", "1:1"))
        assertEquals(listOf(1, 2), tie.map { it.surah })
    }

    @Test
    fun weakAyahsAreSortedAndNotRepeated() {
        // "02:15" is the same ayah as "2:15".
        val weak = ProgressCalculator.weakSpots(setOf("2:20", "2:10", "2:15", "02:15"))
        assertEquals(listOf(3, 8, 13), weak.single().ayahs)
    }

    @Test
    fun theWeakReviewOpensMemorizeModeOnTheRangeOfWeakAyahs() {
        val destination = WeakSpotUi(surah = 2, name = "Al-Baqarah", ayahs = listOf(3, 7, 12)).toReaderDestination()
        assertEquals(2, destination.surah)
        assertEquals(3, destination.ayah)
        assertTrue(destination.hifz)
        assertEquals(3, destination.rangeStart)
        assertEquals(12, destination.rangeEnd)
        assertEquals(Origin.MAP, destination.origin)
    }

    // -- Activity ----------------------------------------------------------------------------------

    private val msPerDay = 86_400_000L
    private val epochDayOf: (Long) -> Int = { millis -> (millis / msPerDay).toInt() }

    private fun review(ayah: Int, reviewedOnDay: Int) = AyahReviewEntity(
        ayahNumber = ayah,
        surahNumber = 1,
        nextReviewAt = 0,
        intervalIndex = 0,
        lastReviewedAt = reviewedOnDay * msPerDay + 1,
    )

    @Test
    fun anAyahMarkedAndReviewedOnTheSameDayCountsOnce() {
        val marked = todo(1, 1, AyahTodoStatus.LEARNED, updatedAt = 100 * msPerDay + 5)
        val byDay = ProgressCalculator.activityByDay(
            ayahTodos = listOf(marked, todo(1, 2, AyahTodoStatus.LEARNING, updatedAt = 100 * msPerDay + 9)),
            reviews = listOf(review(marked.ayahNumber, 100), review(40, 100), review(41, 99)),
            epochDayOf = epochDayOf,
        )
        assertEquals(mapOf(100 to 3, 99 to 1), byDay)
    }

    @Test
    fun rowsWithoutATimestampAreNotActivity() {
        val byDay = ProgressCalculator.activityByDay(
            ayahTodos = listOf(todo(1, 1, AyahTodoStatus.LEARNED, updatedAt = 0)),
            reviews = listOf(AyahReviewEntity(5, 1, 0, 0, lastReviewedAt = 0)),
            epochDayOf = epochDayOf,
        )
        assertTrue(byDay.isEmpty())
    }

    @Test
    fun theChartHasFourteenDaysOldestFirstEndingToday() {
        val days = ProgressCalculator.lastDays(mapOf(100 to 4, 90 to 9, 98 to 2), today = 100)
        assertEquals(14, days.size)
        assertEquals(87, days.first().epochDay)
        assertEquals(100, days.last().epochDay)
        assertEquals(4, days.last().count)
        assertEquals(0, days[12].count) // day 99
        assertEquals(2, days[11].count) // day 98
        assertEquals(9, days.first { it.epochDay == 90 }.count)
        assertEquals(15, ActivityUi(days).total)
        assertEquals(9, ActivityUi(days).max)
    }

    @Test
    fun aDayOutsideTheWindowIsNotShown() {
        val days = ProgressCalculator.lastDays(mapOf(86 to 5), today = 100)
        assertEquals(0, days.sumOf { it.count })
    }

    @Test
    fun theStreakCountsConsecutiveDaysEndingToday() {
        val activity = mapOf(100 to 1, 99 to 3, 98 to 1, 96 to 2)
        assertEquals(3, ProgressCalculator.streak(activity, today = 100))
    }

    @Test
    fun theStreakIsStillAliveBeforeTheFirstAyahOfToday() {
        val activity = mapOf(99 to 1, 98 to 1, 97 to 4)
        assertEquals(3, ProgressCalculator.streak(activity, today = 100))
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        assertEquals(0, ProgressCalculator.streak(mapOf(98 to 1, 97 to 1), today = 100))
        assertEquals(1, ProgressCalculator.streak(mapOf(100 to 1, 98 to 1), today = 100))
        assertEquals(0, ProgressCalculator.streak(emptyMap(), today = 100))
    }

    @Test
    fun theStreakIgnoresDaysAfterToday() {
        assertEquals(0, ProgressCalculator.streak(mapOf(101 to 5, 102 to 5), today = 100))
    }
}
