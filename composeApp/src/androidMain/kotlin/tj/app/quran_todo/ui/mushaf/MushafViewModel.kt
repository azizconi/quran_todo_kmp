package tj.app.quran_todo.ui.mushaf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.mushaf.MushafPage
import tj.app.quran_todo.common.mushaf.MushafPages
import tj.app.quran_todo.ui.reader.displayWords
import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.data.database.dao.AyahNoteDao
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.hifz.AyahMark
import tj.app.quran_todo.ui.hifz.CheckAction
import tj.app.quran_todo.ui.hifz.CheckRouting
import tj.app.quran_todo.ui.hifz.CheckSaved
import tj.app.quran_todo.ui.home.MiniPlayerUi
import tj.app.quran_todo.ui.mushaf.data.LoadedPage
import tj.app.quran_todo.ui.mushaf.data.MushafPageStore
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.reader.AyahSchedule
import tj.app.quran_todo.ui.reader.components.AyahSheetModel
import tj.app.quran_todo.ui.reader.splitBismillah
import tj.app.quran_todo.ui.state.UiPrefs

/** What a page of the pager shows: the quiet skeleton while it loads, the page, or the placeholder when it cannot be had. */
internal sealed interface PageLoad {
    data object Loading : PageLoad

    class Ready(val loaded: LoadedPage) : PageLoad

    /** Neither on the phone nor reachable: "Page not loaded. The internet is needed once". */
    data object Missing : PageLoad
}

/** One-off things the screen reacts to with a toast or a haptic. */
internal sealed interface MushafEvent {
    data object StatusFailed : MushafEvent
}

/**
 * State of the mushaf pages (one `Dest.Mushaf` back stack entry): which pages are loaded, what sounds, the sheet of an ayah and "Check
 * yourself" on the page. The text of the Quran, the names and the notes come from the shared view models (Koin singletons, through the
 * constructor, never `koinViewModel`), progress only from the [HifzRepository]; the pages themselves from the [MushafPageStore].
 *
 * Audio belongs to the shared [PlaybackController] like in the reader: a session is one surah, so listening from a page goes to the end
 * of that surah, and the page follows it ([MushafScreen]).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class MushafViewModel(
    val route: Dest.Mushaf,
    private val homeViewModel: HomeViewModel,
    private val surahViewModel: SurahViewModel,
    private val ayahNoteDao: AyahNoteDao,
    private val uiPrefs: UiPrefs,
    private val playback: PlaybackController,
    private val hifz: HifzRepository,
    private val ticker: EpochDayTicker,
    private val store: MushafPageStore = MushafPageStore.shared,
) : ViewModel() {
    /** The page the screen opens on (1..604). */
    val initialPage: Int = (AyahKey.parse(route.ayahKey)?.let { MushafPages.pageOf(it.surah, it.ayah) } ?: route.page)
        .coerceIn(1, MushafPages.COUNT)

    /** The ayah to mark for a moment on opening (a search result, "continue", the mini player), or null. */
    val initialFlash: AyahKey? = AyahKey.parse(route.ayahKey)

    // -- Pages ----------------------------------------------------------------------------------------------

    private val _loads = MutableStateFlow<Map<Int, PageLoad>>(emptyMap())

    /** The state of the pages the pager asked for. */
    val loads: StateFlow<Map<Int, PageLoad>> = _loads

    /** The pager shows [page] (or is about to): load it unless it is loaded or loading. A page that was missing is tried again. */
    fun ensure(page: Int) {
        if (page !in 1..MushafPages.COUNT) return
        val current = _loads.value[page]
        if (current is PageLoad.Ready || current == PageLoad.Loading) return
        load(page, force = false)
    }

    /** "Retry" of the placeholder: past the memory of a failed request. */
    fun retry(page: Int) = load(page, force = true)

    private fun load(page: Int, force: Boolean) {
        _loads.update { it + (page to PageLoad.Loading) }
        viewModelScope.launch {
            val loaded = store.load(page, force)
            _loads.update { it + (page to (if (loaded != null) PageLoad.Ready(loaded) else PageLoad.Missing)) }
        }
    }

    private val settled = MutableStateFlow(initialPage)
    private var prefetchJob: Job? = null

    init {
        // The place the screen opened on is already the reading position (Home's "Continue" must not wait for a page turn).
        commitPage(initialPage)
        viewModelScope.launch { settled.debounce(READING_POSITION_DELAY_MS).collect { commitPage(it) } }
    }

    /** The pager came to rest on [page]: the pages near it are fetched quietly, the far ones leave the state, the position is kept. */
    fun onPageSettled(page: Int) {
        settled.value = page
        _loads.update { map -> map.filterKeys { kotlin.math.abs(it - page) <= KEEP_PAGES } }
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch { store.prefetch(listOf(page + 1, page - 1, page + 2, page - 2)) }
    }

    /** Writes the pending reading position right away (the screen is being left). */
    fun flushReadingPosition() = commitPage(settled.value)

    private var lastSaved: AyahKey? = null

    /** "Continue": the first ayah of the visible page, the same place the reader keeps (a page starts with an ayah, none goes over). */
    private fun commitPage(page: Int) {
        val first = MushafPages.firstAyahOf(page)
        if (first == lastSaved) return
        lastSaved = first
        uiPrefs.saveLastRead(first.surah, first.ayah)
        uiPrefs.recordRecentSurah(first.surah)
    }

    // -- Names ------------------------------------------------------------------------------------------------

    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())

    /** Called with the language of the app; loads the names of the surahs for it. */
    fun onLanguage(next: AppLanguage) {
        language.value = next
        surahViewModel.loadChapterNames(next)
    }

    private val chapterNames = surahViewModel.chapterNames

    private val russianNames: Flow<Map<Int, String>> = homeViewModel.uiState
        .map { it.surahList }
        .distinctUntilChanged()
        .map { list -> list.associate { it.surahNumber to it.name } }

    /** The names of the 114 surahs in the language of the app (the rule of [SurahNames], the same as the home screen). */
    val names: StateFlow<Map<Int, String>> = combine(language, chapterNames, russianNames) { language, chapters, russian ->
        (1..114).associateWith { number ->
            SurahNames.displayName(
                language = language,
                russianName = russian[number],
                transliteration = chapters[number]?.transliteration,
                fallback = surahOf(number)?.surah?.englishName?.takeIf { it.isNotBlank() } ?: "№ $number",
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The names written in the headers of the pages: the Arabic names of the bundled text ("سُورَةُ البَقَرَةِ"). */
    val arabicNames: StateFlow<Map<Int, String>> = homeViewModel.uiState
        .map { state -> state.completeQuran.associate { it.surah.number to it.surah.name } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun surahOf(number: Int): SurahWithAyahs? =
        homeViewModel.uiState.value.completeQuran.firstOrNull { it.surah.number == number }

    /**
     * The words of the bundled text of every ayah on [page], in reading order ([displayWords]: the Bismillah of a first ayah cut off,
     * the rub el hizb sign hidden), for a page drawn in the reading font of the settings. An ayah whose number of words is not the one
     * of the layout (10:1, 13:1, 50:34 count a pause sign as a word) is left out: its words keep the glyphs of the page font. Empty
     * while the text is not loaded yet.
     */
    fun wordTexts(page: MushafPage): Map<AyahKey, List<String>> {
        val quran = homeViewModel.uiState.value.completeQuran
        if (quran.isEmpty()) return emptyMap()
        val counts = page.wordLines.flatMap { it.words }.filterNot { it.isAyahEnd }.groupingBy { it.key }.eachCount()
        return buildMap {
            for ((key, count) in counts) {
                val ayah = quran.firstOrNull { it.surah.number == key.surah }?.ayahs?.firstOrNull { it.numberInSurah == key.ayah } ?: continue
                val words = displayWords(key.surah, key.ayah, ayah.text)
                if (words.wordCount != count) continue
                put(key, words.ranges.map { words.text.substring(it) })
            }
        }
    }

    private fun nameOf(number: Int): String = names.value[number].orEmpty()

    // -- What sounds ----------------------------------------------------------------------------------------------

    private data class Core(val index: Int, val isPlaying: Boolean, val canResume: Boolean, val isPreparing: Boolean, val hasError: Boolean)

    private val core: Flow<Core> = playback.state
        .map { Core(it.index, it.isPlaying, it.canResume, it.isPreparing, it.hasError) }
        .distinctUntilChanged()

    /** The ayah that sounds (or is paused halfway): the underlay of the page marks it; null when nothing is on. */
    val playing: StateFlow<AyahKey?> = combine(playback.nowPlaying, core) { now, core ->
        if (now == null || !(core.isPlaying || core.canResume || core.isPreparing)) {
            null
        } else {
            now.ayahs.getOrNull(core.index)?.let { AyahKey(now.surahNumber, it.numberInSurah) }
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The mini player of the app (the same pill as on the home screen); null before anything was started and after it was stopped. */
    val miniPlayer: StateFlow<MiniPlayerUi?> = combine(playback.nowPlaying, core, names) { now, core, names ->
        now?.let {
            MiniPlayerUi(
                surah = it.surahNumber,
                name = names[it.surahNumber].orEmpty(),
                ayah = it.ayahs.getOrNull(core.index)?.numberInSurah ?: 1,
                speed = SurahReaderPreferences.load().playbackSpeed,
                isPlaying = core.isPlaying,
                isPreparing = core.isPreparing,
                hasError = core.hasError,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Position inside the playing ayah, 0..1; read only by the ring of the mini player. */
    val playbackProgress: StateFlow<Float> = playback.state
        .map { if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** Audio plays right now (keeps the screen awake). */
    val anythingPlaying: StateFlow<Boolean> = playback.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playback.state.value.isPlaying)

    fun togglePlayback() = playback.toggle()

    fun stopPlayback() = playback.stop()

    /**
     * The play button of the top bar: pauses or resumes what is on; when nothing is, starts at [from] (the picked ayah), otherwise at the
     * first ayah of [page].
     */
    fun playPage(page: MushafPage?, from: AyahKey?) {
        val state = playback.state.value
        if (playback.nowPlaying.value != null && (state.isPlaying || state.canResume || state.isPreparing)) {
            playback.toggle()
            return
        }
        val start = from ?: page?.firstAyah ?: return
        startAt(start)
    }

    /** "Listen from here" in the sheet of an ayah. */
    fun listenFrom(key: AyahKey) = startAt(key)

    /** "Repeat this ayah": a passage of that one ayah, repeated with the repeat count the person chose (3 when it was 1), the way the reader does. */
    fun repeatAyah(key: AyahKey) {
        val surah = surahOf(key.surah) ?: return
        val count = surah.ayahs.size
        val chosen = ReaderPracticeSettings.load(key.surah, count).rangeRepeatCount.takeIf { it != 1 } ?: 3
        val practice = ReaderPracticeSettings.load(key.surah, count)
            .copy(rangeEnabled = true, startAyah = key.ayah, endAyah = key.ayah, rangeRepeatCount = chosen)
            .normalized(count)
        // A passage repeat replaces the per-ayah repeat of the preferences.
        val prefs = SurahReaderPreferences.load().copy(repeatCount = 1)
        prefs.save()
        practice.save(key.surah)
        start(surah, prefs, practice, key)
    }

    private fun startAt(key: AyahKey) {
        val surah = surahOf(key.surah) ?: return
        start(surah, SurahReaderPreferences.load(), ReaderPracticeSettings.load(key.surah, surah.ayahs.size), key)
    }

    private fun start(
        surah: SurahWithAyahs,
        prefs: SurahReaderPreferences,
        practice: ReaderPracticeSettings,
        key: AyahKey,
    ) {
        val sorted = surah.ayahs.sortedBy { it.numberInSurah }
        val index = sorted.indexOfFirst { it.numberInSurah == key.ayah }.coerceAtLeast(0)
        playback.start(surah.copy(ayahs = sorted), prefs, practice, index, autoplay = true)
    }

    // -- The sheet of an ayah ---------------------------------------------------------------------------------------

    private val sheetKey = MutableStateFlow<AyahKey?>(null)

    /** The ayah whose sheet is open, or null. */
    val sheetAyah: StateFlow<AyahKey?> = sheetKey

    fun openSheet(key: AyahKey) {
        sheetKey.value = key
    }

    fun closeSheet() {
        sheetKey.value = null
    }

    /** What the sheet of the ayah shows; null while the text is not there or no sheet is open. */
    val sheet: StateFlow<AyahSheetModel?> = sheetKey.flatMapLatest { key ->
        if (key == null) {
            flowOf(null)
        } else {
            combine(
                hifz.surahCoverage(key.surah),
                ayahNoteDao.getBySurahNumber(key.surah),
                names,
            ) { coverage, notes, names ->
                val ayah = surahOf(key.surah)?.ayahs?.firstOrNull { it.numberInSurah == key.ayah } ?: return@combine null
                AyahSheetModel(
                    ayah = ayah,
                    index = key.ayah - 1,
                    surahName = names[key.surah].orEmpty(),
                    arabic = splitBismillah(key.surah, key.ayah, ayah.text),
                    translation = null,
                    learned = key.ayah in coverage.coveredAyahs,
                    hard = key.ayah in coverage.hardAyahs,
                    schedule = scheduleOf(coverage, key.ayah),
                    note = notes.firstOrNull { it.ayahNumber == ayah.number }?.note.orEmpty(),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun scheduleOf(coverage: tj.app.quran_todo.hifz.model.SurahCoverage, ayah: Int): AyahSchedule? {
        val passage = coverage.passages.firstOrNull { ayah in it } ?: return null
        if (passage.stage == PassageStage.OLD) return AyahSchedule.Circle
        val days = (passage.nextDueEpochDay ?: return AyahSchedule.Circle) - ticker.day.value
        return if (days <= 0) AyahSchedule.Due else AyahSchedule.InDays(days)
    }

    private val eventChannel = Channel<MushafEvent>(Channel.BUFFERED)
    val events: Flow<MushafEvent> = eventChannel.receiveAsFlow()

    /** "I know this ayah": the ayah becomes an OLD passage of its own (or joins the neighbours). */
    fun markAyahKnown(key: AyahKey) {
        viewModelScope.launch {
            try {
                hifz.markKnown(key.surah, key.ayah, key.ayah, ticker.day.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(MushafEvent.StatusFailed)
            }
        }
    }

    /** "Remove from learned": the ayah leaves its passage. */
    fun forgetAyah(key: AyahKey) {
        viewModelScope.launch {
            try {
                hifz.forget(key.surah, key.ayah, key.ayah)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(MushafEvent.StatusFailed)
            }
        }
    }

    fun saveNote(key: AyahKey, note: String) {
        val ayah = surahOf(key.surah)?.ayahs?.firstOrNull { it.numberInSurah == key.ayah } ?: return
        surahViewModel.upsertNote(ayah.number, key.surah, note)
    }

    // -- "Check yourself" on the page --------------------------------------------------------------------------------

    private val _check = MutableStateFlow<PageCheck?>(null)

    /** The running check of a page; null when the mode is off. */
    val check: StateFlow<PageCheck?> = _check

    private val _exitPrompt = MutableStateFlow(false)

    /** The "leave Check yourself?" question is open. */
    val exitPrompt: StateFlow<Boolean> = _exitPrompt

    private val _saving = MutableStateFlow(false)

    /** The result is being written. */
    val saving: StateFlow<Boolean> = _saving

    private val savedChannel = Channel<CheckSaved>(Channel.BUFFERED)

    /** One value per result written. */
    val saved: Flow<CheckSaved> = savedChannel.receiveAsFlow()

    /** The eye button and "Check yourself from here": the ayahs of [page] from [from] (or the first) to its end, all hidden. */
    fun startCheck(page: MushafPage, from: AyahKey?) {
        PageCheck.of(page, from)?.let { _check.value = it }
    }

    fun reveal() {
        _check.value = _check.value?.reveal()
    }

    fun mark(mark: AyahMark) {
        _check.value = _check.value?.mark(mark)
    }

    /** The close button and the back gesture: asks first when ayahs were answered but some remain. */
    fun requestExit() {
        val check = _check.value ?: return
        if (!check.finished && check.markedCount > 0) _exitPrompt.value = true else exitCheck()
    }

    fun dismissExitPrompt() {
        _exitPrompt.value = false
    }

    fun exitCheck() {
        _exitPrompt.value = false
        _check.value = null
    }

    /**
     * Writes the result of the finished check: [grade] for what was checked, [hard] as the difficult ayahs. Every surah part of the page
     * is its own record: it counts as the lesson's repetition when the range is exactly a passage that is due today (`CheckRouting`, B2),
     * otherwise it is only logged (stages and due days do not change).
     */
    fun finishCheck(grade: ReviewGrade, hard: Set<AyahKey>) {
        val check = _check.value ?: return
        if (!check.finished || _saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val today = ticker.day.value
            try {
                val lesson = runCatching { hifz.lesson(today).first() }.getOrNull()
                var asReview = false
                for (part in check.parts(hard)) {
                    val range = AyahRange(part.surah, part.first, part.last)
                    when (val action = CheckRouting.choose(lesson, range)) {
                        is CheckAction.Review -> {
                            hifz.review(action.passageId, grade, part.hard, today)
                            asReview = true
                        }
                        CheckAction.LogCheck -> hifz.logCheck(range.surah, range.startAyah, range.endAyah, grade, part.hard, today)
                    }
                }
                savedChannel.trySend(if (asReview) CheckSaved.Review else CheckSaved.Check)
                AppTelemetry.logEvent("check_completed", mapOf("grade" to grade.name.lowercase()))
                exitCheck()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the sheet open: the person can try again.
                AppTelemetry.logError(e, context = "mushaf_check_save_failed")
            } finally {
                _saving.value = false
            }
        }
    }

    override fun onCleared() {
        flushReadingPosition()
        super.onCleared()
    }

    private companion object {
        /** The position is written this long after the pager stops changing. */
        const val READING_POSITION_DELAY_MS = 400L

        /** Pages farther than this from the one on screen leave the state of the screen (the store still has them). */
        const val KEEP_PAGES = 6
    }
}
