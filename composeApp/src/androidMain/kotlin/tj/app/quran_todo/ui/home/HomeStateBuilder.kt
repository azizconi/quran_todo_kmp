package tj.app.quran_todo.ui.home

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.hifz.quiz.QuizBuilder
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.ReturnInfo
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.plan.PlanMath
import tj.app.quran_todo.ui.state.LastReadPosition
import tj.app.quran_todo.ui.state.OnboardingGoal

/** Every input of the screen state, flattened. The ViewModel gathers it; [HomeStateBuilder] is pure. */
internal class HomeSources(
    val language: AppLanguage,
    val surahList: List<SurahModel>,
    val index: QuranIndex,
    /** Statuses derived from the passages of the hifz model; a surah that is not in the map is not started. */
    val statuses: Map<Int, SurahHifzStatus>,
    /** Covered (memorized) ayahs per surah; only surahs with some. */
    val coveredAyahs: Map<Int, Int>,
    /** The queue of the active plan; empty when there is no plan. */
    val planQueue: List<Int>,
    /** Null until the repository has answered; the lesson card stays hidden until then. */
    val lesson: LessonPlan?,
    val chapterNames: Map<Int, ChapterNameModel>,
    val loadState: QuranLoadState,
    val lastRead: LastReadPosition?,
    val recentSurahs: List<Int>,
    val pinnedSurahs: Set<Int>,
    val onboardingGoals: Set<String>,
    /** The covered ayah numbers of every surah (one read for all of them): the "Juz" tab counts inside juz boundaries with it. */
    val coveredAyahSets: Map<Int, Set<Int>> = emptyMap(),
    /** Learned ayahs per surah (the passage is past the 7-day repetition): the rings of the rows follow these. */
    val learnedAyahs: Map<Int, Int> = emptyMap(),
    /** The learned ayah numbers of every surah: the "Juz" tab counts inside juz boundaries with it. */
    val learnedAyahSets: Map<Int, Set<Int>> = emptyMap(),
    /** Passages with a "listened" record today ("Listen to the old"); only the lesson card shows it. */
    val listenedToday: Set<Long> = emptySet(),
    /** The active plan and its forecast (the numbers of the Progress plan section); the plan row under the lesson card reads them. */
    val plan: HifzPlan? = null,
    val forecast: PlanForecast? = null,
    /** The epoch day the forecast was made for. */
    val today: Int = 0,
    /** "Welcome back" inputs (the same the lesson screen reads): missed days, the day the screen was answered, the end of a lighter week. */
    val returnInfo: ReturnInfo? = null,
    val welcomeBackAnswered: Int? = null,
    val lighterUntil: Int? = null,
)

/** [HomeUi] without the listing, which also depends on the query and the filter. */
internal class HomeBase(
    val phase: HomePhase,
    val continueItem: ContinueUi?,
    val lesson: LessonCardUi?,
    val lessonFirst: Boolean,
    val recent: List<RecentUi>,
    val rows: List<SurahRowUi>,
    val overallProgress: Float,
    val juzRows: List<JuzRowUi>,
    val quizReady: Boolean,
    val planRow: PlanRowUi? = null,
)

internal object HomeStateBuilder {
    fun buildBase(s: HomeSources): HomeBase {
        val rows = HomeRows.build(
            RowsInput(
                language = s.language,
                surahList = s.surahList,
                index = s.index,
                chapterNames = s.chapterNames,
                statuses = s.statuses,
                coveredAyahs = s.learnedAyahs,
                currentPlanSurah = PlanMath.currentSurah(s.planQueue, s.coveredAyahs),
                pinned = s.pinnedSurahs,
            ),
        )
        val rowsByNumber = rows.associateBy { it.number }
        val phase = when {
            !s.index.isEmpty -> HomePhase.Content
            s.loadState is QuranLoadState.Error -> HomePhase.Error
            s.loadState is QuranLoadState.Loading -> HomePhase.Loading
            else -> HomePhase.Content
        }
        val content = phase == HomePhase.Content
        val lessonCard = if (content) s.lesson?.let { lesson -> 
            LessonCardBuilder.build(
                lesson = lesson,
                listenedToday = s.listenedToday,
                returnInfo = s.returnInfo,
                welcomeBackAnswered = s.welcomeBackAnswered,
                lighterUntil = s.lighterUntil,
                readGoal = !lessonFirst(s.onboardingGoals),
            ) { rowsByNumber[it]?.name ?: "Surah $it" }
        } else null
        return HomeBase(
            phase = phase,
            continueItem = if (content) HomeRows.resolveContinue(s.lastRead, s.index, rowsByNumber) else null,
            lesson = lessonCard,
            lessonFirst = lessonFirst(s.onboardingGoals),
            recent = if (content) HomeRows.recent(s.recentSurahs, s.pinnedSurahs, rowsByNumber) else emptyList(),
            rows = rows,
            overallProgress = HomeRows.overallProgress(s.coveredAyahs),
            juzRows = if (content) JuzRows.build(s.language, rowsByNumber, s.learnedAyahSets) else emptyList(),
            quizReady = content && s.coveredAyahs.values.sum() >= QuizBuilder.MIN_POOL,
            planRow = planRow(s.plan, s.forecast, lessonCard?.state, s.today),
        )
    }

    /**
     * The row under the lesson card (task U15): only for a plan that is running. With no plan, a pause or a finished plan the
     * card itself says so, and before the card is known there is nothing to show yet. The numbers are the forecast's (coverage:
     * "covered", not "memorized"), the same the Progress plan section uses.
     */
    fun planRow(plan: HifzPlan?, forecast: PlanForecast?, card: LessonCardState?, today: Int): PlanRowUi? {
        plan ?: return null
        forecast ?: return null
        when (card) {
            null, LessonCardState.NoPlan, LessonCardState.Paused, LessonCardState.PlanFinished -> return null
            LessonCardState.Start, LessonCardState.Continue, LessonCardState.Done, LessonCardState.Returning -> Unit
        }
        if (forecast.finished || forecast.totalAyahs <= 0) return null
        return PlanRowUi(
            scope = plan.scope,
            covered = (forecast.totalAyahs - forecast.remainingAyahs).coerceIn(0, forecast.totalAyahs),
            total = forecast.totalAyahs,
            finishEpochDay = forecast.finishEpochDay,
            today = today,
        )
    }

    /**
     * With the goal "memorize", or when no goal was chosen, "Today's lesson" is the first card and "Continue
     * reading" the second, more compact; with only "read" / "listen" the hero stays first and the lesson is a line.
     */
    fun lessonFirst(goals: Set<String>): Boolean = goals.isEmpty() || OnboardingGoal.MEMORIZE in goals

    /**
     * The reading position the hero follows. The reader's saved position wins; until the reader has
     * saved one, the old audio position ([legacySurah] and the GLOBAL [legacyAyah]) stands in, so
     * listening from the hero already turns "Start with Al-Fatihah" into "Continue".
     */
    fun lastReadWithLegacy(saved: LastReadPosition?, legacySurah: Int?, legacyAyah: Int?): LastReadPosition? {
        if (saved?.ayahInSurah != null) return saved
        val surah = legacySurah ?: return saved
        return LastReadPosition(surah = surah, ayahInSurah = null, legacyGlobalAyah = legacyAyah)
    }

    /** The search index for [rows]; build it once per list and reuse it while the person types. */
    fun searchIndex(rows: List<SurahRowUi>): SurahSearch.Index = SurahSearch.Index.build(
        rows.map { row ->
            SearchItem(
                number = row.number,
                names = listOf(row.name, row.latinName),
                meanings = listOfNotNull(row.translatedName.takeIf { it.isNotBlank() }),
                arabic = listOfNotNull(row.arabicName.takeIf { it.isNotBlank() }),
            )
        },
    )

    /**
     * Applies the query and the filter. A query ignores the filter (it searches everything) and, when
     * it looks like `2:255`, adds the smart result for that ayah.
     */
    fun buildListing(
        rows: List<SurahRowUi>,
        searchIndex: SurahSearch.Index,
        query: String,
        filter: SurahFilter,
    ): ListingUi {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val visible = when (filter) {
                SurahFilter.All -> rows
                SurahFilter.Learning -> rows.filter { it.status == SurahTodoStatus.LEARNING }
                SurahFilter.Learned -> rows.filter { it.status == SurahTodoStatus.LEARNED }
            }
            return ListingUi(rows = visible, filter = filter)
        }
        val byNumber = rows.associateBy { it.number }
        val matches = searchIndex.search(trimmed).mapNotNull { byNumber[it] }
        val reference = SurahSearch.parseAyahReference(trimmed) { surah ->
            byNumber[surah]?.ayahCount ?: QuranMeta.ayahCount(surah)
        }
        val smart = reference?.let { ref ->
            byNumber[ref.surah]?.let { row ->
                SmartResultUi(row = row, ayah = ref.ayah, sharesElements = matches.none { it.number == row.number })
            }
        }
        return ListingUi(rows = matches, smartResult = smart, isSearching = true, filter = filter)
    }
}
