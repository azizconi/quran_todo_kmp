package tj.app.quran_todo.ui.home

import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodayPlannerTest {
    // Surah 1: ayahs 1..7, surah 2: 8..17 (ten of them), surah 3: 18..23.
    private val index = QuranIndex.fromCounts(mapOf(1 to 7, 2 to 10, 3 to 6))

    private fun todo(global: Int, surah: Int, status: AyahTodoStatus, updatedAt: Long = 0) =
        AyahTodoEntity(ayahNumber = global, surahNumber = surah, status = status, updatedAt = updatedAt)

    private fun review(global: Int, surah: Int, interval: Int = 0, lastReviewedAt: Long = 0) = AyahReviewEntity(
        ayahNumber = global,
        surahNumber = surah,
        nextReviewAt = 0,
        intervalIndex = interval,
        lastReviewedAt = lastReviewedAt,
    )

    private fun plan(
        learning: List<Int> = emptyList(),
        todos: List<AyahTodoEntity> = emptyList(),
        due: List<AyahReviewEntity> = emptyList(),
        weak: Set<String> = emptySet(),
        reviewed: Set<Int> = emptySet(),
        goal: Int = 5,
        done: Int = 0,
        focus: Int = 15,
    ) = TodayPlanner.plan(PlannerInput(index, learning, todos, due, weak, reviewed, goal, done, focus))

    private fun learning(range: IntRange, surah: Int, updatedAt: Long = 1_000) =
        range.map { todo(it, surah, AyahTodoStatus.LEARNING, updatedAt) }

    @Test
    fun newAyahsComeFromTheMemorizingSurahsInReadingOrder() {
        val tasks = plan(learning = listOf(2))
        assertEquals(1, tasks.size)
        val task = tasks.single()
        assertEquals(TodayKind.New, task.kind)
        assertEquals(2, task.surah)
        assertEquals(1, task.startAyah)
        assertEquals(5, task.endAyah)
        assertEquals(listOf(8, 9, 10, 11, 12), task.ayahNumbers)
        assertEquals(5, task.ayahCount)
    }

    @Test
    fun theSessionIsLimitedByTheDailyGoalAndByTheFocusMinutes() {
        assertEquals(3, plan(learning = listOf(2), goal = 3, focus = 60).single().ayahCount)
        // 6 focus minutes fit two new ayahs (three minutes each).
        assertEquals(2, plan(learning = listOf(2), goal = 10, focus = 6).single().ayahCount)
        // The goal is kept inside 1..50.
        assertEquals(1, plan(learning = listOf(2), goal = 0, focus = 60).single().ayahCount)
    }

    @Test
    fun ayahsAlreadyBeingLearnedGoFirstLeastRecentlyTouchedFirst() {
        val tasks = plan(
            learning = listOf(2),
            todos = listOf(
                todo(global = 12, surah = 2, status = AyahTodoStatus.LEARNING, updatedAt = 300),
                todo(global = 9, surah = 2, status = AyahTodoStatus.LEARNING, updatedAt = 100),
            ),
            goal = 2,
            focus = 60,
        )
        val task = tasks.single()
        // The limit is two: the two LEARNING ayahs, ordered by the time they were touched, then by reading order.
        assertEquals(listOf(9, 12), task.ayahNumbers)
        assertEquals(2, task.startAyah)
        assertEquals(5, task.endAyah)
    }

    @Test
    fun learnedAyahsAreSkippedWhenFillingTheSession() {
        val tasks = plan(
            learning = listOf(2),
            todos = listOf(
                todo(8, 2, AyahTodoStatus.LEARNED),
                todo(9, 2, AyahTodoStatus.LEARNED),
            ),
        )
        assertEquals(listOf(10, 11, 12, 13, 14), tasks.single().ayahNumbers)
        assertEquals(3, tasks.single().startAyah)
    }

    @Test
    fun newAyahsSpanSeveralSurahsOneTaskEach() {
        val tasks = plan(learning = listOf(1, 2), goal = 9, focus = 60)
        assertEquals(listOf(TodayKind.New, TodayKind.New), tasks.map { it.kind })
        assertEquals(listOf(1, 2), tasks.map { it.surah })
        assertEquals(listOf(7, 2), tasks.map { it.ayahCount })
    }

    @Test
    fun dueAndWeakAyahsDoNotBecomeNew() {
        val tasks = plan(
            learning = listOf(1),
            due = listOf(review(global = 1, surah = 1)),
            weak = setOf("1:2"),
            goal = 3,
            focus = 60,
        )
        val new = tasks.single { it.kind == TodayKind.New }
        assertEquals(listOf(3, 4, 5), new.ayahNumbers)
    }

    // -- Only ayahs that were never repeated are new ---------------------------------------------

    @Test
    fun rightAfterAMemorizeSessionTheRatedAyahsAreNotNewAgain() {
        // All seven ayahs of Al-Fatihah were rated: each is "memorizing" and has a review in the future
        // (so none is due). The goal of five is met too.
        val todos = learning(1..7, surah = 1)
        val reviewed = (1..7).toSet()
        assertTrue(plan(learning = listOf(1), todos = todos, reviewed = reviewed, goal = 5, done = 7).isEmpty())
        // Even with room left in the goal there is nothing new in that surah: every ayah was repeated.
        assertTrue(plan(learning = listOf(1), todos = todos, reviewed = reviewed, goal = 10, done = 7, focus = 60).isEmpty())
    }

    @Test
    fun afterASessionTheRoomLeftInTheGoalGoesToTheNextMemorizingSurah() {
        val tasks = plan(
            learning = listOf(1, 2),
            todos = learning(1..7, surah = 1),
            reviewed = (1..7).toSet(),
            goal = 10,
            done = 7,
            focus = 60,
        )
        val task = tasks.single()
        assertEquals(TodayKind.New, task.kind)
        assertEquals(2, task.surah)
        assertEquals(listOf(8, 9, 10), task.ayahNumbers)
    }

    @Test
    fun aPartlyRatedSurahOffersOnlyItsUntouchedAyahs() {
        // Ayahs 1..3 were rated; 4..7 have no status at all.
        val tasks = plan(
            learning = listOf(1),
            todos = learning(1..3, surah = 1),
            reviewed = setOf(1, 2, 3),
            goal = 10,
            done = 3,
            focus = 60,
        )
        val task = tasks.single()
        assertEquals(listOf(4, 5, 6, 7), task.ayahNumbers)
        assertEquals(4, task.startAyah)
        assertEquals(7, task.endAyah)
    }

    @Test
    fun aMemorizingAyahWithAReviewRecordIsNeverNew() {
        val tasks = plan(
            todos = learning(9..10, surah = 2),
            reviewed = setOf(9),
        )
        // 9 has a record (repeated before, not due); 10 never was.
        assertEquals(1, tasks.size)
        assertEquals(listOf(10), tasks.single { it.kind == TodayKind.New }.ayahNumbers)
    }

    @Test
    fun aMemorizingAyahWithADueReviewIsOnlyAReview() {
        val tasks = plan(
            learning = listOf(2),
            todos = learning(9..9, surah = 2),
            due = listOf(review(global = 9, surah = 2)),
            reviewed = setOf(9),
            goal = 2,
            focus = 60,
        )
        assertEquals(listOf(9), tasks.single { it.kind == TodayKind.Review }.ayahNumbers)
        // The new ayahs skip 9 and continue with 8 and 10.
        assertEquals(listOf(8, 10), tasks.single { it.kind == TodayKind.New }.ayahNumbers)
    }

    @Test
    fun aDueReviewCountsAsReviewedEvenIfTheReviewedSetMissesIt() {
        val tasks = plan(
            todos = learning(9..9, surah = 2),
            due = listOf(review(global = 9, surah = 2)),
        )
        assertEquals(listOf(TodayKind.Review), tasks.map { it.kind })
    }

    @Test
    fun aWeakAyahThatIsNotDueIsOnlyWeak() {
        val tasks = plan(
            todos = learning(9..9, surah = 2),
            reviewed = setOf(9),
            weak = setOf("2:9"),
        )
        assertEquals(listOf(TodayKind.Weak), tasks.map { it.kind })
        assertEquals(listOf(9), tasks.single().ayahNumbers)
    }

    @Test
    fun aMemorizingAyahWithoutAReviewIsNewEvenOutsideAMemorizingSurah() {
        val task = plan(todos = learning(12..13, surah = 2)).single()
        assertEquals(TodayKind.New, task.kind)
        assertEquals(listOf(12, 13), task.ayahNumbers)
    }

    @Test
    fun anAyahWithAReviewButNoStatusIsNotNewEither() {
        // A review without a status (e.g. restored from a backup): it was repeated, so it is not "new".
        val task = plan(learning = listOf(1), reviewed = setOf(1, 2), goal = 3, focus = 60).single()
        assertEquals(listOf(3, 4, 5), task.ayahNumbers)
    }

    @Test
    fun nothingNewOnceTheGoalIsReached() {
        assertTrue(plan(learning = listOf(2), goal = 5, done = 5).isEmpty())
        assertTrue(plan(learning = listOf(2), goal = 5, done = 7).isEmpty())
        assertTrue(plan(todos = learning(12..13, surah = 2), goal = 3, done = 3).isEmpty())
        // Reviews and weak ayahs are not limited by the goal.
        val tasks = plan(
            learning = listOf(2),
            due = listOf(review(global = 1, surah = 1)),
            weak = setOf("1:3"),
            goal = 5,
            done = 9,
        )
        assertEquals(listOf(TodayKind.Review, TodayKind.Weak), tasks.map { it.kind })
    }

    @Test
    fun whatWasDoneTodayShrinksTheNewBudget() {
        assertEquals(2, plan(learning = listOf(2), goal = 5, done = 3, focus = 60).single().ayahCount)
        assertEquals(1, plan(learning = listOf(2), goal = 5, done = 4, focus = 60).single().ayahCount)
        // The focus minutes still cap a single session.
        assertEquals(2, plan(learning = listOf(2), goal = 10, done = 1, focus = 6).single().ayahCount)
    }

    @Test
    fun recentAndOldReviewsAreOneTaskPerSurah() {
        val tasks = plan(
            due = listOf(
                review(global = 9, surah = 2, interval = 0),
                review(global = 8, surah = 2, interval = 3),
                review(global = 3, surah = 1, interval = 1),
                review(global = 10, surah = 2, interval = 2),
            ),
        )
        assertEquals(listOf(TodayKind.Review, TodayKind.Review), tasks.map { it.kind })
        // Surahs come in the order of the most overdue review.
        assertEquals(listOf(2, 1), tasks.map { it.surah })
        val surah2 = tasks[0]
        assertEquals(listOf(8, 9, 10), surah2.ayahNumbers)
        assertEquals(1, surah2.startAyah)
        assertEquals(3, surah2.endAyah)
        assertEquals(listOf(3), tasks[1].ayahNumbers)
    }

    @Test
    fun aReviewSessionTakesAtMostFiveAyahsFromOneSurah() {
        val due = (8..17).map { review(global = it, surah = 2) }
        val task = plan(due = due).single()
        assertEquals(TodayPlanner.MaxReviewAyahsPerSurah, task.ayahCount)
        assertEquals(listOf(8, 9, 10, 11, 12), task.ayahNumbers)
    }

    @Test
    fun weakAyahsThatAreNotDueAreTheirOwnTasksSortedBySurah() {
        val tasks = plan(
            due = listOf(review(global = 9, surah = 2)),
            weak = setOf("2:9", "2:10", "1:3", "broken", "x:y"),
        )
        val weak = tasks.filter { it.kind == TodayKind.Weak }
        assertEquals(listOf(1, 2), weak.map { it.surah })
        assertEquals(listOf(3), weak[0].ayahNumbers)
        // 2:9 is already part of the review, so only 2:10 is weak.
        assertEquals(listOf(10), weak[1].ayahNumbers)
        assertEquals(3, weak[1].startAyah)
    }

    @Test
    fun nothingToDoGivesNoTasks() {
        assertTrue(plan().isEmpty())
        assertTrue(plan(learning = emptyList(), todos = listOf(todo(1, 1, AyahTodoStatus.LEARNED))).isEmpty())
    }

    @Test
    fun summaryHasOneCardPerKindInTheOrderNewReviewWeak() {
        val tasks = plan(
            learning = listOf(3),
            due = listOf(review(9, 2), review(10, 2), review(2, 1)),
            weak = setOf("1:5", "3:18"),
            goal = 2,
            focus = 60,
        )
        val cards = TodayPlanner.summarize(tasks)
        assertEquals(listOf(TodayKind.New, TodayKind.Review, TodayKind.Weak), cards.map { it.kind })

        val review = cards[1]
        assertEquals(3, review.totalAyahs)
        assertEquals(listOf(2, 1), review.surahs)
        assertEquals(2, review.head.surah)

        val weak = cards[2]
        assertEquals(2, weak.totalAyahs)
        assertEquals(listOf(1, 3), weak.surahs)
    }

    @Test
    fun summaryOfNoTasksIsEmpty() {
        assertTrue(TodayPlanner.summarize(emptyList()).isEmpty())
    }

    @Test
    fun doneTodayCountsDistinctAyahsFromBothTablesOnTheGivenDay() {
        val day = 1_000L..1_999L
        val todos = listOf(
            todo(1, 1, AyahTodoStatus.LEARNING, updatedAt = 1_500), // today
            todo(2, 1, AyahTodoStatus.LEARNED, updatedAt = 1_999), // today
            todo(3, 1, AyahTodoStatus.LEARNED, updatedAt = 500), // yesterday
            todo(4, 1, AyahTodoStatus.LEARNED, updatedAt = 0), // never touched
        )
        val reviews = listOf(
            review(2, 1, lastReviewedAt = 1_200), // also in todos: counted once
            review(5, 1, lastReviewedAt = 1_100), // today
            review(6, 1, lastReviewedAt = 2_500), // tomorrow
            review(7, 1, lastReviewedAt = 0),
        )
        assertEquals(3, TodayPlanner.countDoneToday(todos, reviews) { it in day })
        assertEquals(0, TodayPlanner.countDoneToday(emptyList(), emptyList()) { true })
    }
}
