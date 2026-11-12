package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.LessonTomorrow
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.state.LastReadPosition
import tj.app.quran_todo.ui.state.OnboardingGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        statuses: Map<Int, SurahHifzStatus> = emptyMap(),
        covered: Map<Int, Int> = emptyMap(),
        planQueue: List<Int> = emptyList(),
        lesson: LessonPlan? = null,
        lastRead: LastReadPosition? = null,
        recent: List<Int> = emptyList(),
        pinned: Set<Int> = emptySet(),
        goals: Set<String> = emptySet(),
        names: Map<Int, ChapterNameModel> = chapterNames,
    ) = HomeSources(
        language = language,
        surahList = surahList,
        index = index,
        statuses = statuses,
        coveredAyahs = covered,
        learnedAyahs = covered,
        planQueue = planQueue,
        lesson = lesson,
        chapterNames = names,
        loadState = loadState,
        lastRead = lastRead,
        recentSurahs = recent,
        pinnedSurahs = pinned,
        onboardingGoals = goals,
    )

    /** A lesson with nothing in it but a plan: the card says "plan finished" or "no plan" by the flags. */
    private fun lesson(hasPlan: Boolean = true, finished: Boolean = false) = LessonPlan(
        today = 20_000,
        hasPlan = hasPlan,
        planFinished = finished,
        newBlock = null,
        recent = emptyList(),
        old = emptyList(),
        totalMinutes = 0,
        remainingMinutes = 0,
        catchingUp = false,
        deferredCount = 0,
        isComplete = false,
        isEmpty = true,
        tomorrow = LessonTomorrow(newPassage = AyahRange(2, 1, 5), dueCount = 0),
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

    // -- The quiz row (task F2b) ----------------------------------------------------------------------

    @Test
    fun theQuizRowShowsFromFiveMemorizedAyahs() {
        assertFalse(HomeStateBuilder.buildBase(sources(covered = emptyMap())).quizReady)
        assertFalse(HomeStateBuilder.buildBase(sources(covered = mapOf(1 to 4))).quizReady)
        assertTrue(HomeStateBuilder.buildBase(sources(covered = mapOf(1 to 5))).quizReady)
        // The ayahs of several surahs add up.
        assertTrue(HomeStateBuilder.buildBase(sources(covered = mapOf(1 to 3, 2 to 2))).quizReady)
        assertFalse(HomeStateBuilder.buildBase(sources(covered = mapOf(1 to 3, 2 to 1))).quizReady)
        // While the Quran is not there, nothing but the rows is built.
        assertFalse(
            HomeStateBuilder.buildBase(
                sources(index = QuranIndex.Empty, loadState = QuranLoadState.Loading, covered = mapOf(1 to 7)),
            ).quizReady,
        )
    }

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
        assertNull(base.lesson)
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
    fun theStatusOfARowIsTheOneDerivedFromThePassages() {
        val rows = HomeStateBuilder.buildBase(
            sources(statuses = mapOf(1 to SurahHifzStatus.LEARNING, 2 to SurahHifzStatus.LEARNED)),
        ).rows
        assertEquals(SurahTodoStatus.LEARNING, rows[0].status)
        assertEquals(SurahTodoStatus.LEARNED, rows[1].status)
        assertNull(rows[2].status)
    }

    @Test
    fun learnedFractionCountsMemorizedAyahsAndIsFullForAMemorizedSurah() {
        val rows = HomeStateBuilder.buildBase(
            sources(
                statuses = mapOf(1 to SurahHifzStatus.LEARNING, 2 to SurahHifzStatus.LEARNED),
                covered = mapOf(1 to 2, 2 to 10),
            ),
        ).rows
        assertEquals(2f / 7f, rows[0].learnedFraction)
        assertEquals(1f, rows[1].learnedFraction)
        assertEquals(0f, rows[2].learnedFraction)
    }

    @Test
    fun theSurahThePlanIsLearningNowIsMarkedInPlan() {
        // Surah 1 is covered completely: the plan is on surah 2, and only that row carries the label.
        val rows = HomeStateBuilder.buildBase(
            sources(
                statuses = mapOf(1 to SurahHifzStatus.LEARNED, 2 to SurahHifzStatus.LEARNING),
                covered = mapOf(1 to 7),
                planQueue = listOf(1, 2, 3),
            ),
        ).rows
        assertEquals(listOf(false, true, false), rows.map { it.inPlan })
        assertTrue(HomeStateBuilder.buildBase(sources()).rows.none { it.inPlan }, "no plan, no label")
    }

    @Test
    fun overallProgressIsTheShareOfCoveredAyahs() {
        assertEquals((7 + 143) / 6236f, HomeRows.overallProgress(mapOf(1 to 7, 2 to 143)))
        assertEquals(0f, HomeRows.overallProgress(emptyMap()))
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

    // -- Lesson -----------------------------------------------------------------------------------

    @Test
    fun theLessonCardWaitsForTheRepositoryAndIsBuiltOnceItAnswered() {
        assertNull(HomeStateBuilder.buildBase(sources(lesson = null)).lesson)
        val card = HomeStateBuilder.buildBase(sources(lesson = lesson(hasPlan = false))).lesson!!
        assertEquals(LessonCardState.NoPlan, card.state)
    }

    @Test
    fun theLessonCardNamesSurahsInTheLanguageOfTheList() {
        val lesson = lesson().copy(
            isEmpty = false,
            newBlock = tj.app.quran_todo.hifz.schedule.LessonNew(
                surah = 2, startAyah = 1, endAyah = 5,
                state = tj.app.quran_todo.hifz.schedule.LessonNewState.PENDING,
                grade = null, passageId = null, estimateSeconds = 120,
            ),
            remainingMinutes = 2,
            totalMinutes = 2,
        )
        val ru = HomeStateBuilder.buildBase(sources(language = AppLanguage.RU, lesson = lesson)).lesson!!
        assertEquals("Аль-Бакара", ru.lines.single().surahName)
        val en = HomeStateBuilder.buildBase(sources(language = AppLanguage.EN, lesson = lesson)).lesson!!
        assertEquals("Al-Baqarah", en.lines.single().surahName)
    }

    @Test
    fun noLessonCardWhileTheQuranIsNotThere() {
        val base = HomeStateBuilder.buildBase(sources(index = QuranIndex.Empty, loadState = QuranLoadState.Loading, lesson = lesson()))
        assertNull(base.lesson)
    }

    @Test
    fun theLessonComesFirstForTheMemorizeGoalAndWhenNoGoalWasChosen() {
        assertTrue(HomeStateBuilder.lessonFirst(emptySet()))
        assertTrue(HomeStateBuilder.lessonFirst(setOf(OnboardingGoal.MEMORIZE)))
        assertTrue(HomeStateBuilder.lessonFirst(setOf(OnboardingGoal.READ, OnboardingGoal.MEMORIZE)))
        assertFalse(HomeStateBuilder.lessonFirst(setOf(OnboardingGoal.READ)))
        assertFalse(HomeStateBuilder.lessonFirst(setOf(OnboardingGoal.READ, OnboardingGoal.LISTEN)))
        assertFalse(HomeStateBuilder.buildBase(sources(goals = setOf(OnboardingGoal.LISTEN))).lessonFirst)
        assertTrue(HomeStateBuilder.buildBase(sources(goals = setOf(OnboardingGoal.MEMORIZE))).lessonFirst)
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
