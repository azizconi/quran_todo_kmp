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

/**
 * What the day looks like in storage: how much was done today, and which ayahs (global numbers) have a
 * review record, due or not.
 */
internal data class DoneStats(val doneToday: Int, val reviewedAyahs: Set<Int> = emptySet()) {
    /** Something was reviewed at some point: "all done" is a fair thing to say when nothing is left. */
    val hasReviews: Boolean get() = reviewedAyahs.isNotEmpty()
}

/** Every input of the screen state, flattened. The ViewModel gathers it; [HomeStateBuilder] is pure. */
internal class HomeSources(
    val language: AppLanguage,
    val dailyGoal: Int,
    val focusMinutes: Int,
    val surahList: List<SurahModel>,
    val index: QuranIndex,
    val todoSurahs: List<SurahTodoEntity>,
    val ayahTodos: List<AyahTodoEntity>,
    val dueReviews: List<AyahReviewEntity>,
    val weakAyahKeys: Set<String>,
    val chapterNames: Map<Int, ChapterNameModel>,
    val pending: Map<Int, PendingSurahStatus>,
    val loadState: QuranLoadState,
    val lastRead: LastReadPosition?,
    val recentSurahs: List<Int>,
    val pinnedSurahs: Set<Int>,
    val onboardingGoals: Set<String>,
    /** Null until the first read of the review table; Today stays hidden until then. */
    val doneStats: DoneStats?,
    val inviteDismissed: Boolean,
)

/** [HomeUi] without the listing, which also depends on the query and the filter. */
internal class HomeBase(
    val phase: HomePhase,
    val continueItem: ContinueUi?,
    val today: TodayUi?,
    val recent: List<RecentUi>,
    val rows: List<SurahRowUi>,
    val overallProgress: Float,
)

internal object HomeStateBuilder {
    fun buildBase(s: HomeSources): HomeBase {
        val learnedAyahs = s.ayahTodos
            .filter { it.status == AyahTodoStatus.LEARNED }
            .groupingBy { it.surahNumber }
            .eachCount()
        val rows = HomeRows.build(
            RowsInput(
                language = s.language,
                surahList = s.surahList,
                index = s.index,
                chapterNames = s.chapterNames,
                statuses = s.todoSurahs.associate { it.surahNumber to it.status },
                pending = s.pending,
                learnedAyahs = learnedAyahs,
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
        return HomeBase(
            phase = phase,
            continueItem = if (content) HomeRows.resolveContinue(s.lastRead, s.index, rowsByNumber) else null,
            today = if (content) buildToday(s, rowsByNumber) else null,
            recent = if (content) HomeRows.recent(s.recentSurahs, s.pinnedSurahs, rowsByNumber) else emptyList(),
            rows = rows,
            overallProgress = HomeRows.overallProgress(rows),
        )
    }

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

    /**
     * Today is shown when there is something being memorized, a review scheduled, a weak ayah, or the
     * person chose "memorize" as a goal. Then: the cards of the work left; or "all done" when there
     * was work; or an invitation to mark a surah when memorizing is only a goal.
     */
    internal fun buildToday(s: HomeSources, rowsByNumber: Map<Int, SurahRowUi>): TodayUi? {
        val stats = s.doneStats ?: return null
        val learningSurahs = s.todoSurahs.filter { it.status == SurahTodoStatus.LEARNING }.map { it.surahNumber }.sorted()
        val tasks = TodayPlanner.plan(
            PlannerInput(
                index = s.index,
                learningSurahs = learningSurahs,
                ayahTodos = s.ayahTodos,
                dueReviews = s.dueReviews,
                weakAyahKeys = s.weakAyahKeys,
                reviewedAyahs = stats.reviewedAyahs,
                dailyGoal = s.dailyGoal,
                doneToday = stats.doneToday,
                focusMinutes = s.focusMinutes,
            ),
        )
        val hasWork = learningSurahs.isNotEmpty() ||
            s.ayahTodos.any { it.status == AyahTodoStatus.LEARNING } ||
            s.dueReviews.isNotEmpty() ||
            s.weakAyahKeys.isNotEmpty() ||
            stats.hasReviews
        val memorizeGoal = OnboardingGoal.MEMORIZE in s.onboardingGoals
        val body: TodayBody = when {
            tasks.isNotEmpty() -> TodayBody.Cards(cards(tasks, rowsByNumber))
            hasWork -> TodayBody.AllDone
            memorizeGoal && !s.inviteDismissed -> TodayBody.Invite
            else -> return null
        }
        return TodayUi(done = stats.doneToday, goal = s.dailyGoal.coerceAtLeast(1), body = body)
    }

    private fun cards(tasks: List<TodayTask>, rowsByNumber: Map<Int, SurahRowUi>): List<TodayCardUi> {
        val usedSurahs = HashSet<Int>()
        return TodayPlanner.summarize(tasks).map { summary ->
            val surah = summary.head.surah
            TodayCardUi(
                kind = summary.kind,
                surah = surah,
                surahName = rowsByNumber[surah]?.name ?: "Surah $surah",
                startAyah = summary.head.startAyah,
                endAyah = summary.head.endAyah,
                totalAyahs = summary.totalAyahs,
                surahCount = summary.surahs.size,
                // Two cards may point at the same surah; shared element keys must stay unique.
                sharesElements = usedSurahs.add(surah),
            )
        }
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
