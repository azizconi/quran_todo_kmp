package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.presentation.home.PendingSurahStatus
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.state.LastReadPosition
import tj.app.quran_todo.ui.state.OnboardingGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeStateBuilderTest {
    private val index = QuranIndex.fromCounts(mapOf(1 to 7, 2 to 10, 3 to 6))

    private val surahList = listOf(
        SurahModel(1, 21, "аль-Фатиха", 7, 5, "Мекка"),
        SurahModel(2, 15, "аль-Бакара", 10, 87, "Медина"),
        SurahModel(3, 6, "Сура Аль Имран", 6, 89, "Медина"),
    )

    private val chapterNames = mapOf(
        1 to ChapterNameModel("الفاتحة", "Al-Fatihah", "The Opener"),
        2 to ChapterNameModel("البقرة", "Al-Baqarah", "The Cow"),
        3 to ChapterNameModel("آل عمران", "Ali 'Imran", "Family of Imran"),
    )

    private fun sources(
        language: AppLanguage = AppLanguage.EN,
        index: QuranIndex = this.index,
        loadState: QuranLoadState = QuranLoadState.Content,
        todoSurahs: List<SurahTodoEntity> = emptyList(),
        ayahTodos: List<AyahTodoEntity> = emptyList(),
        dueReviews: List<AyahReviewEntity> = emptyList(),
        weak: Set<String> = emptySet(),
        pending: Map<Int, PendingSurahStatus> = emptyMap(),
        lastRead: LastReadPosition? = null,
        recent: List<Int> = emptyList(),
        pinned: Set<Int> = emptySet(),
        goals: Set<String> = emptySet(),
        done: DoneStats? = DoneStats(0),
        goal: Int = 5,
        inviteDismissed: Boolean = false,
        names: Map<Int, ChapterNameModel> = chapterNames,
    ) = HomeSources(
        language = language,
        dailyGoal = goal,
        focusMinutes = 15,
        surahList = surahList,
        index = index,
        todoSurahs = todoSurahs,
        ayahTodos = ayahTodos,
        dueReviews = dueReviews,
        weakAyahKeys = weak,
        chapterNames = names,
        pending = pending,
        loadState = loadState,
        lastRead = lastRead,
        recentSurahs = recent,
        pinnedSurahs = pinned,
        onboardingGoals = goals,
        doneStats = done,
        inviteDismissed = inviteDismissed,
    )

    private fun row(number: Int, status: SurahTodoStatus? = null, name: String = "S$number", pinned: Boolean = false) =
        SurahRowUi(
            number = number,
            name = name,
            latinName = name,
            translatedName = "",
            arabicName = "",
            ayahCount = QuranMeta.ayahCount(number).coerceAtLeast(1),
            status = status,
            learnedFraction = 0f,
            canOpen = true,
            isPinned = pinned,
        )

    // -- Phase ------------------------------------------------------------------------------------

    @Test
    fun phaseFollowsTheLoadStateWhileTheTextIsMissing() {
        assertEquals(HomePhase.Loading, HomeStateBuilder.buildBase(sources(index = QuranIndex.Empty, loadState = QuranLoadState.Loading)).phase)
        assertEquals(HomePhase.Error, HomeStateBuilder.buildBase(sources(index = QuranIndex.Empty, loadState = QuranLoadState.Error("x"))).phase)
        assertEquals(HomePhase.Content, HomeStateBuilder.buildBase(sources()).phase)
        // A failed refresh does not hide a Quran that is already there.
        assertEquals(HomePhase.Content, HomeStateBuilder.buildBase(sources(loadState = QuranLoadState.Error("x"))).phase)
    }

    @Test
    fun nothingButRowsIsBuiltWhileLoading() {
        val base = HomeStateBuilder.buildBase(sources(index = QuranIndex.Empty, loadState = QuranLoadState.Loading, goals = setOf(OnboardingGoal.MEMORIZE)))
        assertNull(base.continueItem)
        assertNull(base.today)
        assertTrue(base.recent.isEmpty())
        // The bundled list still names the surahs, but they cannot be opened yet.
        assertEquals(3, base.rows.size)
        assertTrue(base.rows.none { it.canOpen })
    }

    // -- Rows -------------------------------------------------------------------------------------

    @Test
    fun rowsUseTheRussianNameForRuAndTheTransliterationOtherwise() {
        val ru = HomeStateBuilder.buildBase(sources(language = AppLanguage.RU)).rows
        assertEquals(listOf("Аль-Фатиха", "Аль-Бакара", "Аль Имран"), ru.map { it.name })
        assertEquals(listOf("Al-Fatihah", "Al-Baqarah", "Ali 'Imran"), ru.map { it.latinName })
        assertEquals("The Opener", ru[0].translatedName)

        val en = HomeStateBuilder.buildBase(sources(language = AppLanguage.EN)).rows
        assertEquals(listOf("Al-Fatihah", "Al-Baqarah", "Ali 'Imran"), en.map { it.name })
        assertEquals(listOf("الفاتحة", "البقرة", "آل عمران"), en.map { it.arabicName })
        assertEquals(listOf(7, 10, 6), en.map { it.ayahCount })
        assertTrue(en.all { it.canOpen })
    }

    @Test
    fun theTranslatedNameIsDroppedWhenItRepeatsTheName() {
        val names = mapOf(1 to ChapterNameModel("الفاتحة", "Al-Fatihah", "al-fatihah"))
        val rows = HomeStateBuilder.buildBase(sources(names = names)).rows
        assertEquals("", rows[0].translatedName)
    }

    @Test
    fun russianNamesAreCleanedUp() {
        assertEquals("Мухаммад", HomeRows.cleanRussianName("Сура Мухаммад"))
        assertEquals("Аль-Баляд", HomeRows.cleanRussianName("Сура Аль-Баляд"))
        assertEquals("Ал-Лайл", HomeRows.cleanRussianName("Ал - Лайл"))
        assertEquals("Аль-Фатиха", HomeRows.cleanRussianName("аль-Фатиха"))
        assertEquals("Йа Син", HomeRows.cleanRussianName(" Йа Син "))
    }

    @Test
    fun aPendingStatusWinsOverTheSavedOne() {
        val rows = HomeStateBuilder.buildBase(
            sources(
                todoSurahs = listOf(SurahTodoEntity(1, SurahTodoStatus.LEARNING), SurahTodoEntity(2, SurahTodoStatus.LEARNED)),
                pending = mapOf(1 to PendingSurahStatus(SurahTodoStatus.LEARNED, 1L), 2 to PendingSurahStatus(null, 2L)),
            ),
        ).rows
        assertEquals(SurahTodoStatus.LEARNED, rows[0].status)
        assertNull(rows[1].status)
    }

    @Test
    fun learnedFractionCountsMemorizedAyahsAndIsFullForAMemorizedSurah() {
        val rows = HomeStateBuilder.buildBase(
            sources(
                todoSurahs = listOf(SurahTodoEntity(1, SurahTodoStatus.LEARNING), SurahTodoEntity(2, SurahTodoStatus.LEARNED)),
                ayahTodos = listOf(
                    AyahTodoEntity(1, 1, AyahTodoStatus.LEARNED, 1),
                    AyahTodoEntity(2, 1, AyahTodoStatus.LEARNED, 1),
                    AyahTodoEntity(3, 1, AyahTodoStatus.LEARNING, 1),
                ),
            ),
        ).rows
        assertEquals(2f / 7f, rows[0].learnedFraction)
        assertEquals(1f, rows[1].learnedFraction)
        assertEquals(0f, rows[2].learnedFraction)
    }

    @Test
    fun overallProgressCountsMemorizedSurahsInFull() {
        val rows = listOf(
            row(1, SurahTodoStatus.LEARNED), // 7
            row(2, SurahTodoStatus.LEARNING).copy(learnedFraction = 0.5f), // 143
            row(3),
        )
        assertEquals((7 + 143) / 6236f, HomeRows.overallProgress(rows))
        assertEquals(0f, HomeRows.overallProgress(emptyList()))
    }

    @Test
    fun pinnedSurahsAreMarkedOnTheirRow() {
        val rows = HomeStateBuilder.buildBase(sources(pinned = setOf(2))).rows
        assertEquals(listOf(false, true, false), rows.map { it.isPinned })
    }

    // -- Continue ---------------------------------------------------------------------------------

    @Test
    fun aNewPersonStartsWithAlFatihahAyahOne() {
        val item = HomeStateBuilder.buildBase(sources(lastRead = null)).continueItem!!
        assertEquals(1, item.surah)
        assertEquals(1, item.ayah)
        assertTrue(item.isFresh)
        assertEquals(0f, item.progress)
    }

    @Test
    fun theReadingPositionIsUsedAsSaved() {
        val item = HomeStateBuilder.buildBase(sources(lastRead = LastReadPosition(2, 8, null))).continueItem!!
        assertEquals(2, item.surah)
        assertEquals(8, item.ayah)
        assertEquals(10, item.ayahCount)
        assertFalse(item.isFresh)
        assertEquals(0.8f, item.progress)
        assertEquals("Al-Baqarah", item.name)
        assertEquals("البقرة", item.arabicName)
    }

    @Test
    fun aLegacyGlobalAyahIsConvertedToTheNumberInTheSurah() {
        // Global ayah 12 is ayah 5 of surah 2 (surah 2 starts at global 8).
        val item = HomeStateBuilder.buildBase(sources(lastRead = LastReadPosition(2, null, 12))).continueItem!!
        assertEquals(2, item.surah)
        assertEquals(5, item.ayah)
        assertFalse(item.isFresh)
    }

    @Test
    fun aLegacyAyahOfAnotherSurahFallsBackToTheFirstAyah() {
        val item = HomeStateBuilder.buildBase(sources(lastRead = LastReadPosition(3, null, 12))).continueItem!!
        assertEquals(3, item.surah)
        assertEquals(1, item.ayah)
    }

    @Test
    fun aSavedAyahIsKeptInsideTheSurah() {
        assertEquals(10, HomeStateBuilder.buildBase(sources(lastRead = LastReadPosition(2, 999, null))).continueItem!!.ayah)
        assertEquals(1, HomeStateBuilder.buildBase(sources(lastRead = LastReadPosition(2, 0, null))).continueItem!!.ayah)
    }

    @Test
    fun anUnknownSurahInTheSavedPositionStartsFresh() {
        val item = HomeStateBuilder.buildBase(sources(lastRead = LastReadPosition(77, 3, null))).continueItem!!
        assertEquals(1, item.surah)
        assertTrue(item.isFresh)
    }

    // -- Recent -----------------------------------------------------------------------------------

    @Test
    fun pinnedSurahsComeFirstInTheRecentRowWithoutDuplicates() {
        val recent = HomeStateBuilder.buildBase(sources(recent = listOf(3, 1, 2), pinned = setOf(2, 3))).recent
        assertEquals(listOf(2, 3, 1), recent.map { it.surah })
        assertEquals(listOf(true, true, false), recent.map { it.pinned })
        assertEquals("Al-Baqarah", recent[0].name)
    }

    @Test
    fun recentSurahsThatAreNotInTheQuranAreDropped() {
        assertEquals(listOf(1), HomeStateBuilder.buildBase(sources(recent = listOf(500, 1))).recent.map { it.surah })
        assertTrue(HomeStateBuilder.buildBase(sources()).recent.isEmpty())
    }

    // -- Today ------------------------------------------------------------------------------------

    @Test
    fun todayIsHiddenWhenNothingIsInProgressAndNoGoalWasChosen() {
        assertNull(HomeStateBuilder.buildBase(sources()).today)
        assertNull(HomeStateBuilder.buildBase(sources(goals = setOf(OnboardingGoal.READ, OnboardingGoal.LISTEN))).today)
    }

    @Test
    fun todayWaitsForTheFirstReadOfTheReviewTable() {
        val learning = listOf(SurahTodoEntity(2, SurahTodoStatus.LEARNING))
        assertNull(HomeStateBuilder.buildBase(sources(todoSurahs = learning, done = null)).today)
        assertTrue(HomeStateBuilder.buildBase(sources(todoSurahs = learning)).today != null)
    }

    @Test
    fun aMemorizingSurahProducesNewCards() {
        val today = HomeStateBuilder.buildBase(
            sources(todoSurahs = listOf(SurahTodoEntity(2, SurahTodoStatus.LEARNING)), done = DoneStats(3)),
        ).today!!
        assertEquals(3, today.done)
        assertEquals(5, today.goal)
        val cards = assertIs<TodayBody.Cards>(today.body).cards
        assertEquals(1, cards.size)
        val card = cards.single()
        assertEquals(TodayKind.New, card.kind)
        assertEquals(2, card.surah)
        assertEquals("Al-Baqarah", card.surahName)
        assertEquals(1, card.startAyah)
        // The goal of five minus the three ayahs already done today leaves two new ones.
        assertEquals(2, card.endAyah)
        assertEquals(2, card.totalAyahs)
        assertTrue(card.sharesElements)
    }

    @Test
    fun dueReviewsAndWeakAyahsGetTheirCards() {
        val today = HomeStateBuilder.buildBase(
            sources(
                dueReviews = listOf(
                    AyahReviewEntity(9, 2, 0, 0, 0),
                    AyahReviewEntity(10, 2, 0, 3, 0),
                    AyahReviewEntity(1, 1, 0, 1, 0),
                ),
                weak = setOf("3:20"),
            ),
        ).today!!
        val cards = assertIs<TodayBody.Cards>(today.body).cards
        assertEquals(listOf(TodayKind.Review, TodayKind.Weak), cards.map { it.kind })
        val review = cards[0]
        assertEquals(3, review.totalAyahs)
        assertEquals(2, review.surahCount)
        assertEquals(2, review.surah)
        val weak = cards[1]
        assertEquals(1, weak.totalAyahs)
        assertEquals(3, weak.surah)
        assertEquals(3, weak.startAyah)
    }

    @Test
    fun twoCardsOnTheSameSurahShareElementKeysOnlyOnce() {
        val cards = assertIs<TodayBody.Cards>(
            HomeStateBuilder.buildBase(
                sources(
                    todoSurahs = listOf(SurahTodoEntity(2, SurahTodoStatus.LEARNING)),
                    dueReviews = listOf(AyahReviewEntity(8, 2, 0, 0, 0)),
                ),
            ).today!!.body,
        ).cards
        assertEquals(listOf(TodayKind.New, TodayKind.Review), cards.map { it.kind })
        assertEquals(listOf(true, false), cards.map { it.sharesElements })
    }

    @Test
    fun whenAllWorkIsDoneTodayShowsTheDoneCard() {
        val learning = listOf(SurahTodoEntity(1, SurahTodoStatus.LEARNING))
        val learned = (1..7).map { AyahTodoEntity(it, 1, AyahTodoStatus.LEARNED, 5) }
        val today = HomeStateBuilder.buildBase(
            sources(todoSurahs = learning, ayahTodos = learned, done = DoneStats(7, (1..7).toSet())),
        ).today!!
        assertIs<TodayBody.AllDone>(today.body)
        assertEquals(7, today.done)
    }

    @Test
    fun reviewedBeforeButNothingDueShowsTheDoneCard() {
        val today = HomeStateBuilder.buildBase(sources(done = DoneStats(0, setOf(1)))).today!!
        assertIs<TodayBody.AllDone>(today.body)
    }

    @Test
    fun rightAfterAMemorizeSessionTodayDoesNotOfferTheRatedAyahsAsNewAgain() {
        // The seven ayahs of Al-Fatihah were rated: each is "memorizing" with a review in the future
        // (nothing is due), and 7 ayahs against a goal of 5 were done today.
        val learning = listOf(SurahTodoEntity(1, SurahTodoStatus.LEARNING))
        val rated = (1..7).map { AyahTodoEntity(it, 1, AyahTodoStatus.LEARNING, 5) }
        val today = HomeStateBuilder.buildBase(
            sources(todoSurahs = learning, ayahTodos = rated, done = DoneStats(7, (1..7).toSet())),
        ).today!!
        assertIs<TodayBody.AllDone>(today.body)
        assertEquals(7, today.done)
        assertEquals(5, today.goal)
    }

    @Test
    fun afterASessionWithGoalLeftTodayOffersOnlyAyahsThatWereNeverRepeated() {
        val learning = listOf(SurahTodoEntity(1, SurahTodoStatus.LEARNING), SurahTodoEntity(2, SurahTodoStatus.LEARNING))
        val rated = (1..7).map { AyahTodoEntity(it, 1, AyahTodoStatus.LEARNING, 5) }
        val today = HomeStateBuilder.buildBase(
            sources(todoSurahs = learning, ayahTodos = rated, done = DoneStats(7, (1..7).toSet()), goal = 10),
        ).today!!
        val card = assertIs<TodayBody.Cards>(today.body).cards.single()
        assertEquals(TodayKind.New, card.kind)
        assertEquals(2, card.surah)
        assertEquals(1, card.startAyah)
        assertEquals(3, card.endAyah)
    }

    @Test
    fun aReachedGoalShowsNoNewCardButStillTheDueReviews() {
        val learning = listOf(SurahTodoEntity(2, SurahTodoStatus.LEARNING))
        val onlyNew = HomeStateBuilder.buildBase(sources(todoSurahs = learning, done = DoneStats(5))).today!!
        assertIs<TodayBody.AllDone>(onlyNew.body)
        val withReview = HomeStateBuilder.buildBase(
            sources(
                todoSurahs = learning,
                dueReviews = listOf(AyahReviewEntity(1, 1, 0, 0, 0)),
                done = DoneStats(6, setOf(1)),
            ),
        ).today!!
        assertEquals(6, withReview.done)
        assertEquals(listOf(TodayKind.Review), assertIs<TodayBody.Cards>(withReview.body).cards.map { it.kind })
    }

    @Test
    fun theMemorizeGoalAloneInvitesToMarkASurahUntilItIsDismissed() {
        val goals = setOf(OnboardingGoal.MEMORIZE)
        assertIs<TodayBody.Invite>(HomeStateBuilder.buildBase(sources(goals = goals)).today!!.body)
        assertNull(HomeStateBuilder.buildBase(sources(goals = goals, inviteDismissed = true)).today)
    }

    @Test
    fun theMemorizeGoalWithWorkShowsTheCardsNotTheInvitation() {
        val today = HomeStateBuilder.buildBase(
            sources(goals = setOf(OnboardingGoal.MEMORIZE), todoSurahs = listOf(SurahTodoEntity(1, SurahTodoStatus.LEARNING))),
        ).today!!
        assertIs<TodayBody.Cards>(today.body)
    }

    // -- Listing ----------------------------------------------------------------------------------

    private val rows = listOf(
        row(1, SurahTodoStatus.LEARNED, name = "Al-Fatihah"),
        row(2, SurahTodoStatus.LEARNING, name = "Al-Baqarah"),
        row(3, null, name = "Ali 'Imran"),
    )
    private val searchIndex = HomeStateBuilder.searchIndex(rows)

    @Test
    fun theFilterKeepsMatchingStatuses() {
        assertEquals(listOf(1, 2, 3), HomeStateBuilder.buildListing(rows, searchIndex, "", SurahFilter.All).rows.map { it.number })
        assertEquals(listOf(2), HomeStateBuilder.buildListing(rows, searchIndex, "", SurahFilter.Learning).rows.map { it.number })
        assertEquals(listOf(1), HomeStateBuilder.buildListing(rows, searchIndex, "  ", SurahFilter.Learned).rows.map { it.number })
        assertFalse(HomeStateBuilder.buildListing(rows, searchIndex, "", SurahFilter.All).isSearching)
    }

    @Test
    fun aQuerySearchesEverythingAndIgnoresTheFilter() {
        val listing = HomeStateBuilder.buildListing(rows, searchIndex, "baqarah", SurahFilter.Learned)
        assertEquals(listOf(2), listing.rows.map { it.number })
        assertTrue(listing.isSearching)
        assertEquals(SurahFilter.Learned, listing.filter)
    }

    @Test
    fun anAyahReferenceAddsTheSmartResult() {
        val listing = HomeStateBuilder.buildListing(rows, searchIndex, "2:7", SurahFilter.All)
        val smart = listing.smartResult!!
        assertEquals(2, smart.row.number)
        assertEquals(7, smart.ayah)
        assertTrue(smart.sharesElements)
        assertTrue(listing.rows.isEmpty())
    }

    @Test
    fun theSmartResultUsesTheAyahCountOfTheRows() {
        val short = listOf(row(1), row(2), row(3).copy(ayahCount = 6))
        val shortIndex = HomeStateBuilder.searchIndex(short)
        assertNull(HomeStateBuilder.buildListing(short, shortIndex, "3:7", SurahFilter.All).smartResult)
        assertEquals(6, HomeStateBuilder.buildListing(short, shortIndex, "3 6", SurahFilter.All).smartResult?.ayah)
    }

    @Test
    fun aSurahNumberFindsTheRowWithoutASmartResult() {
        val listing = HomeStateBuilder.buildListing(rows, searchIndex, "3", SurahFilter.All)
        assertEquals(listOf(3), listing.rows.map { it.number })
        assertNull(listing.smartResult)
    }
}
