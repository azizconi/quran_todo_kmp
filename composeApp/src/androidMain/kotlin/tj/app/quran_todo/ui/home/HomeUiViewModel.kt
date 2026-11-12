package tj.app.quran_todo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.SurahCoverage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.ReturnInfo
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.presentation.home.HomeUiState
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.home.QuranLoadState
import tj.app.quran_todo.ui.playback.NowPlaying
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.state.LastReadPosition
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * State of the home screen. Gathers the shared [HomeViewModel] (injected, never obtained through
 * `koinViewModel`: see docs/redesign/API.md; it still provides the text and the names), [UiPrefs], the
 * [PlaybackController] and the hifz model ([HifzRepository]: the lesson of the day, the derived statuses and
 * the coverage of the surahs) into immutable UI models, off the main thread. The screen changes the
 * progress only through [HifzRepository] ([applyStatus]).
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeUiViewModel internal constructor(
    private val home: HomeViewModel,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
) : ViewModel() {

    private val language = MutableStateFlow(HomeInputs.fromStorage())
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
     * What the hifz model says: the derived statuses, which ayahs of each surah are memorized (one read: the surah rows take
     * the counts, the "Juz" tab counts inside the juz), the queue of the plan and the lesson of the day (it follows the
     * changing day through the ticker). A failing read leaves the previous kind of answer ("nothing") instead of freezing
     * the whole screen.
     */
    private val hifzSnapshot: Flow<HifzSnapshot> = combine(
        hifz.surahStatuses().orFallback(emptyMap()),
        combine(
            hifz.coveredAyahs().orFallback(emptyMap()),
            hifz.learnedAyahs().orFallback(emptyMap()),
        ) { covered, learned -> covered to learned },
        // The plan, its forecast and the day of the forecast: the queue feeds the rows, the rest the plan row under the lesson card.
        combine(
            hifz.plan().orFallback(null),
            hifz.forecast(ticker.day).orFallback(null),
            ticker.day,
            ::Triple,
        ),
        hifz.lesson(ticker.day).map<LessonPlan, LessonPlan?> { it }.orFallback(null),
        combine(
            // The passages listened to today ("Listen to the old"): the card says "Listened" and nothing else reads this.
            ticker.day.flatMapLatest { day -> hifz.listenLog(day, day) }
                .map { log -> log.mapNotNullTo(HashSet()) { it.passageId } }
                .orFallback(emptySet<Long>()),
            // "Welcome back" inputs, the same the lesson screen reads: the card shows "Returning" by the same rule.
            hifz.returnInfo(ticker.day).map<ReturnInfo, ReturnInfo?> { it }.orFallback(null),
            hifz.lighterUntil().orFallback(null),
            uiPrefs.welcomeBackAnswered,
            ::WelcomeSnapshot,
        ),
    ) { statuses, ayahs, planState, lesson, welcome ->
        val (plan, forecast, today) = planState
        HifzSnapshot(
            statuses, ayahs.first, plan?.surahOrder.orEmpty(), lesson, welcome.listened,
            learnedSets = ayahs.second, plan = plan, forecast = forecast, today = today,
            returnInfo = welcome.returnInfo, lighterUntil = welcome.lighterUntil, welcomeBackAnswered = welcome.answered,
        )
    }

    private val prefs: Flow<PrefsSnapshot> = combine(
        uiPrefs.lastReadPosition,
        uiPrefs.recentSurahs,
        uiPrefs.pinnedSurahs,
        uiPrefs.onboardingGoals,
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
        combine(data, language, hifzSnapshot, ::Triple),
        prefs,
        audioPosition,
    ) { (data, language, snapshot), prefs, _ -> HomeStateBuilder.buildBase(sourcesOf(data, language, snapshot, prefs)) }
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

    /**
     * "Listen to the old" while the queue plays or stands (the card says "Listening to the old · 2 of 5"), null otherwise: the
     * passage it is at (from 1), how many there are, and whether the sound is on.
     */
    val listening: StateFlow<ListeningUi?> = combine(
        playback.listenQueue,
        playback.state.map { it.isPlaying || it.isPreparing }.distinctUntilChanged(),
    ) { queue, playing ->
        // A queue of "Juz of the night" (task F5b) has negative passage ids: it is not the listening of the old.
        queue?.takeIf { !it.finished && it.items.firstOrNull()?.passageId?.let { id -> id < 0 } != true }?.let { ListeningUi(position = it.position + 1, total = it.items.size, playing = playing) }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Position inside the playing ayah, 0..1. Collected only by the mini player so nothing else recomposes. */
    val playbackProgress: StateFlow<Float> = playback.state
        .map { state ->
            if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PlaybackStopTimeoutMillis), 0f)

    init {
        home.loadChapterNames(language.value)
    }

    // -- Inputs from the screen ---------------------------------------------------------------

    /** The app language lives in the app root; the screen forwards it. */
    fun setLanguage(next: AppLanguage) {
        val previous = language.value
        if (previous == next) return
        language.value = next
        home.loadChapterNames(next)
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: SurahFilter) {
        filter.value = value
    }

    fun onResume() {
        resumeTick.value += 1
    }

    // -- Commands ----------------------------------------------------------------------------

    /** The Quran failed to load (or the person wants to try again): reload it from the network. */
    fun retry() = home.refreshQuran(withLocalAction = false)

    fun togglePin(surah: Int) = uiPrefs.togglePin(surah)

    /** "Continue now" on the card of a paused plan (task F2b): the pause ends today and the calendar moves back. */
    fun resumePlan() {
        viewModelScope.launch {
            try {
                hifz.resumePlan(ticker.day.value)
                AppTelemetry.logEvent("plan_resumed_early")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "home_resume_plan_failed")
                eventChannel.trySend(HomeEvent.ResumeFailed)
            }
        }
    }

    /** The memorized ayahs and the passages of [surah], for the line under the status in the surah sheet. */
    fun coverage(surah: Int): Flow<SurahCoverage> = hifz.surahCoverage(surah).catch { error ->
        // The line under the status is a nicety: without it the sheet still works.
        AppTelemetry.logError(throwable = error, context = "home_coverage_read_failed")
    }

    /**
     * Applies the choice of the surah sheet through [HifzRepository] (see [SurahStatusChange] for what each choice
     * does). The screen has asked for the confirmation already when ayahs are taken out of the repetitions.
     */
    fun applyStatus(surah: Int, target: SurahTarget) {
        viewModelScope.launch {
            try {
                val today = ticker.day.value
                val current = hifz.surahStatuses().first()[surah]
                val plan = hifz.plan().first()
                val steps = SurahStatusChange.steps(current, target, inPlan = plan != null && surah in plan.surahOrder)
                for (step in steps) {
                    when (step) {
                        StatusStep.Forget -> hifz.forget(surah)
                        StatusStep.LearnNext -> hifz.learnNext(surah, today)
                        StatusStep.MarkKnown -> hifz.markKnown(surah, today = today)
                        StatusStep.LeaveThePlan -> if (plan != null) {
                            val rest = plan.surahOrder - surah
                            if (rest.isEmpty()) {
                                hifz.clearPlan()
                            } else {
                                hifz.setPlan(
                                    scope = HifzScope.CUSTOM,
                                    surahOrder = rest,
                                    dailyNewAyahs = plan.dailyNewAyahs,
                                    targetEpochDay = plan.targetEpochDay,
                                    manzilDays = plan.manzilDays,
                                    today = today,
                                )
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "home_status_change_failed")
                eventChannel.trySend(HomeEvent.StatusUpdateFailed)
            }
        }
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
        lesson = base.lesson,
        lessonFirst = base.lessonFirst,
        recent = base.recent,
        rows = base.rows,
        listing = HomeStateBuilder.buildListing(base.rows, searchIndexFor(base.rows), query, filter),
        overallProgress = base.overallProgress,
        juzRows = base.juzRows,
        juzListing = JuzRows.search(base.juzRows, query),
        quizReady = base.quizReady,
        planRow = base.planRow,
    )

    private fun initialBase(): HomeBase = HomeStateBuilder.buildBase(
        sourcesOf(
            data = home.uiState.value.toData(),
            language = language.value,
            snapshot = HifzSnapshot(),
            prefs = PrefsSnapshot(
                lastRead = uiPrefs.lastReadPosition.value,
                recent = uiPrefs.recentSurahs.value,
                pinned = uiPrefs.pinnedSurahs.value,
                goals = uiPrefs.onboardingGoals.value,
            ),
        ),
    )

    private fun sourcesOf(data: HomeData, language: AppLanguage, snapshot: HifzSnapshot, prefs: PrefsSnapshot) = HomeSources(
        language = language,
        surahList = data.surahList,
        index = indexFor(data.completeQuran),
        statuses = snapshot.statuses,
        coveredAyahs = snapshot.covered,
        coveredAyahSets = snapshot.coveredSets,
        learnedAyahs = snapshot.learned,
        learnedAyahSets = snapshot.learnedSets,
        planQueue = snapshot.planQueue,
        lesson = snapshot.lesson,
        listenedToday = snapshot.listened,
        plan = snapshot.plan,
        forecast = snapshot.forecast,
        today = snapshot.today,
        returnInfo = snapshot.returnInfo,
        welcomeBackAnswered = snapshot.welcomeBackAnswered,
        lighterUntil = snapshot.lighterUntil,
        chapterNames = data.chapterNames,
        loadState = data.loadState,
        lastRead = HomeStateBuilder.lastReadWithLegacy(
            saved = prefs.lastRead,
            legacySurah = UserSettingsStorage.getLastPlaybackSurah(),
            legacyAyah = UserSettingsStorage.getLastPlaybackAyahNumber(),
        ),
        recentSurahs = prefs.recent,
        pinnedSurahs = prefs.pinned,
        onboardingGoals = prefs.goals,
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

/** A read of the hifz model that does not end the stream when it fails: it reports and continues with [fallback]. */
private fun <T> Flow<T>.orFallback(fallback: T): Flow<T> = catch { error ->
    AppTelemetry.logError(throwable = error, context = "home_hifz_read_failed")
    emit(fallback)
}

/** The passages listened to today and the "Welcome back" inputs of the lesson card, read together. */
private class WelcomeSnapshot(
    val listened: Set<Long>,
    val returnInfo: ReturnInfo?,
    val lighterUntil: Int?,
    val answered: Int?,
)

/** What the home screen reads from the hifz model; empty until the first answer. */
internal class HifzSnapshot(
    val statuses: Map<Int, SurahHifzStatus> = emptyMap(),
    /** The covered ayahs of each surah (numbers in the surah); the counts per surah are derived from them. */
    val coveredSets: Map<Int, Set<Int>> = emptyMap(),
    val planQueue: List<Int> = emptyList(),
    val lesson: LessonPlan? = null,
    /** The passages with a "listened" record today. */
    val listened: Set<Long> = emptySet(),
    /** The learned ayahs of each surah (their passage is past the 7-day repetition); a subset of [coveredSets]. */
    val learnedSets: Map<Int, Set<Int>> = emptyMap(),
    val plan: HifzPlan? = null,
    val forecast: PlanForecast? = null,
    val today: Int = 0,
    val returnInfo: ReturnInfo? = null,
    val lighterUntil: Int? = null,
    val welcomeBackAnswered: Int? = null,
) {
    val covered: Map<Int, Int> = coveredSets.mapValues { it.value.size }
    val learned: Map<Int, Int> = learnedSets.mapValues { it.value.size }
}

/** The parts of [HomeUiState] this screen reads; compared by reference so progress updates do not recompute. */
private class HomeData(
    val surahList: List<SurahModel>,
    val completeQuran: List<SurahWithAyahs>,
    val chapterNames: Map<Int, ChapterNameModel>,
    val loadState: QuranLoadState,
)

private fun HomeUiState.toData() = HomeData(
    surahList = surahList,
    completeQuran = completeQuran,
    chapterNames = chapterNames,
    loadState = quranLoadState,
)

private fun sameData(a: HomeData, b: HomeData): Boolean =
    a.surahList === b.surahList &&
        a.completeQuran === b.completeQuran &&
        a.chapterNames === b.chapterNames &&
        a.loadState == b.loadState

private object HomeInputs {
    /** What the app root loads at start; the screen then forwards later changes. */
    fun fromStorage(): AppLanguage = LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage()
}

private class PrefsSnapshot(
    val lastRead: LastReadPosition?,
    val recent: List<Int>,
    val pinned: Set<Int>,
    val goals: Set<String>,
)

private data class PlayerCore(val index: Int, val isPlaying: Boolean, val isPreparing: Boolean, val hasError: Boolean)
