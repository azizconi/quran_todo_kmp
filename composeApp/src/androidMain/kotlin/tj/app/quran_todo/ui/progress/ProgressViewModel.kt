package tj.app.quran_todo.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.settings.weakAyahKeySet
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.common.utils.localDateFromEpoch
import tj.app.quran_todo.data.database.dao.AyahReviewDao
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.presentation.home.HomeUiState
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.common.SurahNames

/**
 * State of the progress screen. The shared [HomeViewModel] (injected through Koin, never obtained with
 * `koinViewModel`: see docs/redesign/API.md) is the source of surah statuses, ayah statuses, due
 * reviews and surah names; weak ayahs are re-read from storage, the review table gives the days on
 * which something was reviewed.
 *
 * `StatsViewModel` is deliberately not used: it freezes "today" inside its last snapshot (the 14-day
 * window and the streak would be a day old when the app was left open overnight), ignores reviews
 * (the home screen's "done today" counts them) and names weak ayahs by their global number.
 * [ProgressCalculator] applies the same definitions (activity = ayahs with `updatedAt` on a day) and
 * fixes those three points.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel internal constructor(
    private val home: HomeViewModel,
    private val reviewDao: AyahReviewDao,
) : ViewModel() {

    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())
    private val today = MutableStateFlow(currentLocalDate().toEpochDays())
    private val weakTick = MutableStateFlow(0)

    @Volatile
    private var cachedEnglishNames: Pair<List<SurahWithAyahs>, Map<Int, String>>? = null

    private val data: Flow<ProgressData> = home.uiState
        .map { it.toData() }
        .distinctUntilChanged(::sameData)

    /** The review table, re-read when the ayah table or the due reviews change; empty until the first read. */
    private val reviews: Flow<List<AyahReviewEntity>> = home.uiState
        .map { it.ayahTodos to it.dueReviews }
        .distinctUntilChanged { a, b -> a.first === b.first && a.second === b.second }
        .mapLatest { withContext(Dispatchers.IO) { runCatching { reviewDao.getAll() }.getOrDefault(emptyList()) } }
        .onStart { emit(emptyList()) }

    /** Weak ayah keys straight from storage: the home state refreshes them only when the ayah table changes. */
    private val weakKeys: Flow<Set<String>> = combine(
        home.uiState.map { it.weakAyahKeys }.distinctUntilChanged(),
        weakTick,
    ) { keys, _ -> keys }
        .mapLatest { fromHome ->
            withContext(Dispatchers.IO) { runCatching { weakAyahKeySet().toSet() }.getOrDefault(fromHome) }
        }
        .onStart { emit(home.uiState.value.weakAyahKeys) }

    /** False while the shared view model is still reading the database (after a process restore). */
    private val ready: Flow<Boolean> = flow {
        if (home.uiState.value.hasProgressData()) {
            emit(true)
            return@flow
        }
        emit(false)
        withTimeoutOrNull(LoadGraceMillis) { home.uiState.first { it.hasProgressData() } }
        emit(true)
    }

    private val sources: Flow<ProgressSources> = combine(
        combine(data, language, ready, ::Triple),
        combine(weakKeys, reviews, today, ::Triple),
    ) { (data, language, ready), (weak, reviews, today) ->
        ProgressSources(data, language, ready, weak, reviews, today)
    }

    val ui: StateFlow<ProgressUi> = sources
        .map { build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialUi())

    init {
        home.loadChapterNames(language.value)
    }

    /** The app language lives in the app root; the screen forwards it. */
    fun setLanguage(value: AppLanguage) {
        if (language.value == value) return
        language.value = value
        home.loadChapterNames(value)
    }

    /** Back on screen: a new day may have begun, reviews may have come due, weak ayahs may have changed. */
    fun onResume() {
        today.value = currentLocalDate().toEpochDays()
        weakTick.value += 1
        home.refreshDueReviewsNow()
    }

    // -- Assembly ----------------------------------------------------------------------------------

    private fun initialUi(): ProgressUi {
        val state = home.uiState.value
        if (!state.hasProgressData()) return ProgressUi()
        return build(
            ProgressSources(
                data = state.toData(),
                language = language.value,
                ready = true,
                weakKeys = state.weakAyahKeys,
                reviews = emptyList(),
                today = today.value,
            ),
        )
    }

    private fun build(s: ProgressSources): ProgressUi {
        if (!s.ready) return ProgressUi()
        val statuses = s.data.todoSurahs.associate { it.surahNumber to it.status }
        val progress = ProgressCalculator.surahProgress(statuses, s.data.ayahTodos)
        val totals = ProgressCalculator.totals(progress)
        val names = surahNames(s)

        val cells = progress.mapIndexed { index, surah ->
            MapCellUi(
                number = surah.number,
                name = names[index],
                ayahCount = surah.ayahCount,
                learnedAyahs = surah.learnedAyahs,
                state = surah.state,
            )
        }
        val weak = ProgressCalculator.weakSpots(s.weakKeys).map { spot ->
            WeakSpotUi(surah = spot.surah, name = names[spot.surah - 1], ayahs = spot.ayahs)
        }
        val byDay = ProgressCalculator.activityByDay(s.data.ayahTodos, s.reviews) { millis ->
            localDateFromEpoch(millis).toEpochDays()
        }
        val activity = ActivityUi(ProgressCalculator.lastDays(byDay, s.today))
        val tiles = TilesUi(
            streakDays = ProgressCalculator.streak(byDay, s.today),
            dueToday = s.data.dueReviews.distinctBy { it.ayahNumber }.size,
            weakAyahs = weak.sumOf { it.ayahs.size },
            learningAyahs = totals.learningAyahs,
        )
        val nothingYet = totals.isUntouched && weak.isEmpty() && tiles.dueToday == 0 && activity.total == 0
        return ProgressUi(
            phase = if (nothingYet) ProgressPhase.Empty else ProgressPhase.Content,
            hero = HeroUi(
                learnedAyahs = totals.learnedAyahs,
                totalAyahs = totals.totalAyahs,
                learnedSurahs = totals.learnedSurahs,
                learningSurahs = totals.learningSurahs,
                percent = totals.percent,
                belowOnePercent = totals.belowOnePercent,
                fraction = totals.fraction,
            ),
            cells = cells,
            tiles = tiles,
            activity = activity,
            weak = weak,
        )
    }

    /** Names of the 114 surahs by the same rule as the home list and the reader header. */
    private fun surahNames(s: ProgressSources): List<String> {
        val russian = s.data.surahList.associateBy { it.surahNumber }
        val english = englishNames(s.data.completeQuran)
        return List(ProgressCalculator.StandardAyahCounts.size) { index ->
            val number = index + 1
            val ru = russian[number]
            val fallback = english[number]?.takeIf { it.isNotBlank() }
                ?: ru?.name?.let(SurahNames::cleanRussianName)
                ?: "Surah $number"
            SurahNames.displayName(
                language = s.language,
                russianName = ru?.name,
                transliteration = s.data.chapterNames[number]?.transliteration,
                fallback = fallback,
            )
        }
    }

    private fun englishNames(completeQuran: List<SurahWithAyahs>): Map<Int, String> {
        cachedEnglishNames?.takeIf { it.first === completeQuran }?.let { return it.second }
        val names = completeQuran.associate { it.surah.number to it.surah.englishName }
        cachedEnglishNames = completeQuran to names
        return names
    }

    private companion object {
        /** How long to wait for the database before concluding that there is nothing in it. */
        const val LoadGraceMillis = 700L
    }
}

/** The parts of [HomeUiState] this screen reads; compared by reference so unrelated updates are ignored. */
internal class ProgressData(
    val surahList: List<SurahModel>,
    val todoSurahs: List<SurahTodoEntity>,
    val ayahTodos: List<AyahTodoEntity>,
    val completeQuran: List<SurahWithAyahs>,
    val chapterNames: Map<Int, ChapterNameModel>,
    val dueReviews: List<AyahReviewEntity>,
)

internal class ProgressSources(
    val data: ProgressData,
    val language: AppLanguage,
    val ready: Boolean,
    val weakKeys: Set<String>,
    val reviews: List<AyahReviewEntity>,
    val today: Int,
)

private fun HomeUiState.toData() = ProgressData(
    surahList = surahList,
    todoSurahs = todoSurahs,
    ayahTodos = ayahTodos,
    completeQuran = completeQuran,
    chapterNames = chapterNames,
    dueReviews = dueReviews,
)

private fun sameData(a: ProgressData, b: ProgressData): Boolean =
    a.surahList === b.surahList &&
        a.todoSurahs === b.todoSurahs &&
        a.ayahTodos === b.ayahTodos &&
        a.completeQuran === b.completeQuran &&
        a.chapterNames === b.chapterNames &&
        a.dueReviews === b.dueReviews

/**
 * The shared view model starts with empty lists and fills them from the database; a list that is no
 * longer the initial empty one means the database answered (also when the answer is "nothing").
 */
private fun HomeUiState.hasProgressData(): Boolean =
    todoSurahs !== InitialTodoSurahs && ayahTodos !== InitialAyahTodos

private val InitialTodoSurahs: List<SurahTodoEntity> = emptyList()
private val InitialAyahTodos: List<AyahTodoEntity> = emptyList()
