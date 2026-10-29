package tj.app.quran_todo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.common.utils.localDateFromEpoch
import tj.app.quran_todo.data.database.dao.AyahReviewDao
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.presentation.home.HomeUiState
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.home.PendingSurahStatus
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.playback.NowPlaying
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.LastReadPosition
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * State of the home screen. Gathers the shared [HomeViewModel] (injected, never obtained through
 * `koinViewModel`: see docs/redesign/API.md), [UiPrefs], the [PlaybackController] and the review table
 * into immutable UI models, off the main thread.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeUiViewModel internal constructor(
    private val home: HomeViewModel,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
    private val reviewDao: AyahReviewDao,
    private val localPrefs: HomeLocalPrefs,
) : ViewModel() {

    private val inputs = MutableStateFlow(HomeInputs.fromStorage())
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SurahFilter.All)
    private val resumeTick = MutableStateFlow(0)
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)

    /** One-shot events (a toast). */
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    @Volatile
    private var cachedIndex: Pair<List<SurahWithAyahs>, QuranIndex>? = null

    @Volatile
    private var cachedSearch: Pair<List<SurahRowUi>, SurahSearch.Index>? = null

    private val data: Flow<HomeData> = home.uiState
        .map { it.toData() }
        .distinctUntilChanged(::sameData)

    /**
     * Ayahs touched today and the ayahs that have a review record (those are never "new"), re-read when
     * the ayah table, the due reviews or the day change.
     */
    private val doneStats: Flow<DoneStats?> = home.uiState
        .map { Triple(it.ayahTodos, it.dueReviews, it.currentEpochDay) }
        .distinctUntilChanged { a, b -> a.first === b.first && a.second === b.second && a.third == b.third }
        .mapLatest { (todos, _, epochDay) ->
            val reviews = withContext(Dispatchers.IO) {
                runCatching { reviewDao.getAll() }.getOrDefault(emptyList())
            }
            DoneStats(
                doneToday = TodayPlanner.countDoneToday(todos, reviews) { millis ->
                    localDateFromEpoch(millis).toEpochDays() == epochDay
                },
                reviewedAyahs = reviews.mapTo(HashSet()) { it.ayahNumber },
            ) as DoneStats?
        }
        .onStart { emit(null) }

    private val prefs: Flow<PrefsSnapshot> = combine(
        uiPrefs.lastReadPosition,
        uiPrefs.recentSurahs,
        uiPrefs.pinnedSurahs,
        uiPrefs.onboardingGoals,
        localPrefs.hifzInviteDismissed,
        ::PrefsSnapshot,
    )

    /**
     * Ticks when the old audio position may have moved (a surah started or stopped, the ayah changed,
     * the screen resumed). It only matters until the reader has saved a reading position.
     */
    private val audioPosition: Flow<Int> = combine(
        resumeTick,
        playback.nowPlaying.map { it?.surahNumber }.distinctUntilChanged(),
        playback.state.map { it.index }.distinctUntilChanged(),
    ) { tick, _, _ -> tick }

    private val base: StateFlow<HomeBase> = combine(
        combine(data, inputs, doneStats, ::Triple),
        prefs,
        audioPosition,
    ) { (data, inputs, done), prefs, _ -> HomeStateBuilder.buildBase(sourcesOf(data, inputs, done, prefs)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialBase())

    private val debouncedQuery: Flow<String> = query
        .debounce { if (it.isBlank()) 0L else QueryDebounceMillis }
        .distinctUntilChanged()

    /** Everything the screen draws except audio. */
    val ui: StateFlow<HomeUi> = combine(base, debouncedQuery, filter) { base, query, filter ->
        assemble(base, query, filter)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, assemble(base.value, "", SurahFilter.All))

    /** The mini player; null when nothing was started or after it was stopped. */
    val player: StateFlow<MiniPlayerUi?> = combine(
        playback.nowPlaying,
        playback.state.map { PlayerCore(it.index, it.isPlaying, it.isPreparing, it.hasError) }.distinctUntilChanged(),
        base.map { it.rows }.distinctUntilChanged(),
        resumeTick,
    ) { now, core, rows, _ -> miniPlayerOf(now, core, rows) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, miniPlayerOf(playback.nowPlaying.value, playerCoreNow(), base.value.rows))

    /** Position inside the playing ayah, 0..1. Collected only by the mini player so nothing else recomposes. */
    val playbackProgress: StateFlow<Float> = playback.state
        .map { state ->
            if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PlaybackStopTimeoutMillis), 0f)

    init {
        home.loadChapterNames(inputs.value.language)
        viewModelScope.launch {
            home.uiState
                .map { it.statusUpdateError }
                .distinctUntilChanged()
                .filterNotNull()
                .collect {
                    eventChannel.trySend(HomeEvent.StatusUpdateFailed)
                    home.clearStatusUpdateError()
                }
        }
    }

    // -- Inputs from the screen ---------------------------------------------------------------

    /** The app language and the plan settings live in the app root; the screen forwards them. */
    fun setInputs(language: AppLanguage, dailyGoal: Int, focusMinutes: Int) {
        val next = HomeInputs(language, dailyGoal.coerceIn(1, 50), focusMinutes.coerceIn(5, 60))
        val previous = inputs.value
        if (previous == next) return
        inputs.value = next
        if (previous.language != language) home.loadChapterNames(language)
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: SurahFilter) {
        filter.value = value
    }

    fun onResume() {
        home.refreshDueReviewsNow()
        resumeTick.value += 1
    }

    // -- Commands ----------------------------------------------------------------------------

    /** The Quran failed to load (or the person wants to try again): reload it from the network. */
    fun retry() = home.refreshQuran(withLocalAction = false)

    fun setSurahStatus(surah: Int, status: SurahTodoStatus?) = home.setSurahStatus(surah, status)

    fun togglePin(surah: Int) = uiPrefs.togglePin(surah)

    fun dismissHifzInvite() = localPrefs.dismissHifzInvite()

    /**
     * Asks whether the burst for the reached goal should play now. True at most once per day: the day is
     * remembered at the moment the answer is yes.
     */
    fun consumeGoalCelebration(done: Int, goal: Int): Boolean {
        val day = currentLocalDate().toEpochDays()
        if (!shouldCelebrateGoal(done, goal, localPrefs.goalCelebratedDay, day)) return false
        localPrefs.markGoalCelebrated(day)
        return true
    }

    /**
     * "Listen": starts [surah] on the mini player without opening the reader. If the controller already
     * has this surah, toggles it (pause / resume). Otherwise starts from the last audio position when it
     * lies in this surah, else from [readAyah] (the reading position).
     */
    fun listen(surah: Int, readAyah: Int) {
        if (playback.isActiveFor(surah)) {
            playback.toggle()
            return
        }
        val complete = home.uiState.value.completeQuran.firstOrNull { it.surah.number == surah } ?: return
        val ayahs = complete.ayahs.sortedBy { it.numberInSurah }
        if (ayahs.isEmpty()) return
        val audioIndex = UserSettingsStorage.getLastPlaybackSurah()
            ?.takeIf { it == surah }
            ?.let { UserSettingsStorage.getLastPlaybackAyahNumber() }
            ?.let { global -> ayahs.indexOfFirst { it.number == global } }
            ?.takeIf { it >= 0 }
        val index = audioIndex ?: (readAyah - 1).coerceIn(0, ayahs.lastIndex)
        playback.start(
            surah = complete,
            prefs = SurahReaderPreferences.load(),
            practice = ReaderPracticeSettings.load(surah, ayahs.size),
            fromIndex = index,
        )
    }

    fun togglePlayback() = playback.toggle()

    fun stopPlayback() = playback.stop()

    // -- Assembly ----------------------------------------------------------------------------

    private fun assemble(base: HomeBase, query: String, filter: SurahFilter): HomeUi = HomeUi(
        phase = base.phase,
        continueItem = base.continueItem,
        today = base.today,
        recent = base.recent,
        rows = base.rows,
        listing = HomeStateBuilder.buildListing(base.rows, searchIndexFor(base.rows), query, filter),
        overallProgress = base.overallProgress,
    )

    private fun initialBase(): HomeBase = HomeStateBuilder.buildBase(
        sourcesOf(
            data = home.uiState.value.toData(),
            inputs = inputs.value,
            done = null,
            prefs = PrefsSnapshot(
                lastRead = uiPrefs.lastReadPosition.value,
                recent = uiPrefs.recentSurahs.value,
                pinned = uiPrefs.pinnedSurahs.value,
                goals = uiPrefs.onboardingGoals.value,
                inviteDismissed = localPrefs.hifzInviteDismissed.value,
            ),
        ),
    )

    private fun sourcesOf(data: HomeData, inputs: HomeInputs, done: DoneStats?, prefs: PrefsSnapshot) = HomeSources(
        language = inputs.language,
        dailyGoal = inputs.dailyGoal,
        focusMinutes = inputs.focusMinutes,
        surahList = data.surahList,
        index = indexFor(data.completeQuran),
        todoSurahs = data.todoSurahs,
        ayahTodos = data.ayahTodos,
        dueReviews = data.dueReviews,
        weakAyahKeys = data.weakAyahKeys,
        chapterNames = data.chapterNames,
        pending = data.pending,
        loadState = data.loadState,
        lastRead = HomeStateBuilder.lastReadWithLegacy(
            saved = prefs.lastRead,
            legacySurah = UserSettingsStorage.getLastPlaybackSurah(),
            legacyAyah = UserSettingsStorage.getLastPlaybackAyahNumber(),
        ),
        recentSurahs = prefs.recent,
        pinnedSurahs = prefs.pinned,
        onboardingGoals = prefs.goals,
        doneStats = done,
        inviteDismissed = prefs.inviteDismissed,
    )

    private fun indexFor(completeQuran: List<SurahWithAyahs>): QuranIndex {
        cachedIndex?.takeIf { it.first === completeQuran }?.let { return it.second }
        return QuranIndex.from(completeQuran).also { cachedIndex = completeQuran to it }
    }

    private fun searchIndexFor(rows: List<SurahRowUi>): SurahSearch.Index {
        cachedSearch?.takeIf { it.first === rows }?.let { return it.second }
        return HomeStateBuilder.searchIndex(rows).also { cachedSearch = rows to it }
    }

    private fun playerCoreNow() = playback.state.value.let { PlayerCore(it.index, it.isPlaying, it.isPreparing, it.hasError) }

    private fun miniPlayerOf(
        now: NowPlaying?,
        core: PlayerCore,
        rows: List<SurahRowUi>,
    ): MiniPlayerUi? {
        now ?: return null
        return MiniPlayerUi(
            surah = now.surahNumber,
            name = rows.firstOrNull { it.number == now.surahNumber }?.name ?: "",
            ayah = now.ayahs.getOrNull(core.index)?.numberInSurah ?: 1,
            speed = SurahReaderPreferences.load().playbackSpeed,
            isPlaying = core.isPlaying,
            isPreparing = core.isPreparing,
            hasError = core.hasError,
        )
    }

    private companion object {
        const val QueryDebounceMillis = 120L
        const val PlaybackStopTimeoutMillis = 3_000L
    }
}

/** The parts of [HomeUiState] this screen reads; compared by reference so progress updates do not recompute. */
private class HomeData(
    val surahList: List<SurahModel>,
    val todoSurahs: List<SurahTodoEntity>,
    val ayahTodos: List<AyahTodoEntity>,
    val completeQuran: List<SurahWithAyahs>,
    val chapterNames: Map<Int, ChapterNameModel>,
    val dueReviews: List<AyahReviewEntity>,
    val weakAyahKeys: Set<String>,
    val pending: Map<Int, PendingSurahStatus>,
    val loadState: QuranLoadState,
)

private fun HomeUiState.toData() = HomeData(
    surahList = surahList,
    todoSurahs = todoSurahs,
    ayahTodos = ayahTodos,
    completeQuran = completeQuran,
    chapterNames = chapterNames,
    dueReviews = dueReviews,
    weakAyahKeys = weakAyahKeys,
    pending = pendingSurahStatuses,
    loadState = quranLoadState,
)

private fun sameData(a: HomeData, b: HomeData): Boolean =
    a.surahList === b.surahList &&
        a.todoSurahs === b.todoSurahs &&
        a.ayahTodos === b.ayahTodos &&
        a.completeQuran === b.completeQuran &&
        a.chapterNames === b.chapterNames &&
        a.dueReviews === b.dueReviews &&
        a.weakAyahKeys === b.weakAyahKeys &&
        a.pending === b.pending &&
        a.loadState == b.loadState

private data class HomeInputs(val language: AppLanguage, val dailyGoal: Int, val focusMinutes: Int) {
    companion object {
        /** What the app root loads at start; the screen then forwards later changes. */
        fun fromStorage() = HomeInputs(
            language = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage(),
            dailyGoal = UserSettingsStorage.getDailyGoal()?.coerceIn(1, 50) ?: 5,
            focusMinutes = UserSettingsStorage.getFocusMinutes()?.coerceIn(5, 60) ?: 10,
        )
    }
}

private class PrefsSnapshot(
    val lastRead: LastReadPosition?,
    val recent: List<Int>,
    val pinned: Set<Int>,
    val goals: Set<String>,
    val inviteDismissed: Boolean,
)

private data class PlayerCore(val index: Int, val isPlaying: Boolean, val isPreparing: Boolean, val hasError: Boolean)
