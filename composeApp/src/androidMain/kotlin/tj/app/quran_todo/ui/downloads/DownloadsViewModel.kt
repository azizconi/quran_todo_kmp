package tj.app.quran_todo.ui.downloads

import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.dao.HifzDao
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.repository.QuranRemoteRepository
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.TextMetrics
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.common.JuzIndex
import tj.app.quran_todo.ui.home.HomeRows
import tj.app.quran_todo.ui.home.HomeStateBuilder
import tj.app.quran_todo.ui.home.QuranIndex
import tj.app.quran_todo.ui.home.RowsInput
import tj.app.quran_todo.ui.home.SurahRowUi
import tj.app.quran_todo.ui.home.SurahSearch
import tj.app.quran_todo.ui.i18n.downloadsCopy
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * State and actions of "Downloads and storage". The files on disk are the truth about what is downloaded ([DownloadStorage] is read
 * again whenever it changes); the queue file says what is asked for ([DownloadQueue]); the work does the fetching in the background
 * ([DownloadsManager]), so nothing here has to stay alive for a download to go on.
 *
 * The shared [HomeViewModel] (names of surahs) and [HifzRepository] (the plan) are injected through the constructor, never through
 * `koinViewModel` (see docs/redesign/API.md).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class DownloadsViewModel(
    private val manager: DownloadsManager,
    private val hifz: HifzRepository,
    private val home: HomeViewModel,
    private val uiPrefs: UiPrefs,
    private val quran: QuranRemoteRepository,
    private val hifzDao: HifzDao,
    private val cache: AudioCacheStorage = AudioCacheStorage.shared,
    private val freeSpace: () -> Long = ::freeSpaceOfPhone,
) : ViewModel() {
    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())
    private val eventChannel = Channel<DownloadsEvent>(Channel.BUFFERED)

    /** One-shot events. */
    val events: Flow<DownloadsEvent> = eventChannel.receiveAsFlow()

    fun setLanguage(next: AppLanguage) {
        language.value = next
    }

    // -- Sources -------------------------------------------------------------------------------

    /** The length of every ayah of the bundled text, for the size estimates; null until it is read (or when it cannot be). */
    private val metrics: StateFlow<TextMetrics?> = flow { emit(loadMetrics()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun loadMetrics(): TextMetrics? = try {
        quran.ensureQuranInstalled()
        val rows = hifzDao.getMetricRows()
        if (rows.size == QuranFacts.AYAH_COUNT) TextMetrics.fromRows(rows) else null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    @Volatile
    private var cachedIndex: Pair<List<SurahWithAyahs>, QuranIndex>? = null

    private fun indexFor(complete: List<SurahWithAyahs>): QuranIndex {
        cachedIndex?.takeIf { it.first === complete }?.let { return it.second }
        return QuranIndex.from(complete).also { cachedIndex = complete to it }
    }

    /** The 114 rows with the names of surahs in the language of the app. */
    private val catalog: Flow<List<SurahRowUi>> = combine(
        home.uiState
            .map { Triple(it.surahList, it.chapterNames, it.completeQuran) }
            .distinctUntilChanged { a, b -> a.first === b.first && a.second === b.second && a.third === b.third },
        language,
    ) { (surahList, chapterNames, complete), language ->
        HomeRows.build(
            RowsInput(
                language = language,
                surahList = surahList,
                index = indexFor(complete),
                chapterNames = chapterNames,
                statuses = emptyMap(),
                coveredAyahs = emptyMap(),
                currentPlanSurah = null,
                pinned = emptySet(),
            ),
        )
    }

    /** The surahs of "My plan" (see [DownloadSelection.planSurahs]); empty without a plan. */
    private val planSurahs: Flow<List<Int>> = combine(
        hifz.plan().catch { emit(null) },
        hifz.surahStatuses().catch { emit(emptyMap<Int, SurahHifzStatus>()) },
        DownloadSelection::planSurahs,
    ).distinctUntilChanged()

    /** The files that are downloaded, read again (off the main thread) when the set changes; at most about three times a second. */
    private val files: Flow<Map<String, Long>> = manager.storage.revision
        .sample(FILES_SAMPLE_MS)
        .onStart { emit(manager.storage.revision.value) }
        .mapLatest { manager.storage.snapshot() }
        .flowOn(Dispatchers.IO)

    /** The work is "waiting" only when it has been for a moment: a work that is about to start is not "waiting for Wi-Fi". */
    private val phase: Flow<WorkPhase> = manager.phase
        .debounce { if (it == WorkPhase.WAITING) PHASE_SETTLE_MS else 0L }
        .onStart { emit(WorkPhase.NONE) }
        .distinctUntilChanged()

    private val cacheBytes = MutableStateFlow(0L)

    // -- The model of the rows -----------------------------------------------------------------

    private class RowDef(
        val id: String,
        val kind: DownloadKind,
        val number: Int,
        val name: String,
        val arabic: String,
        val segments: List<Segment>,
        val firstSurah: Int,
        val lastSurah: Int,
        val keys: List<String>,
        val estimate: Long?,
    )

    private class Defs(
        val reciter: Reciter,
        val plan: RowDef?,
        val planRows: List<RowDef>,
        val juz: List<RowDef>,
        val surahs: List<RowDef>,
        val searchIndex: SurahSearch.Index?,
    ) {
        val all: Map<String, RowDef> = buildMap {
            plan?.let { put(it.id, it) }
            (juz + surahs).forEach { put(it.id, it) }
        }
    }

    @Volatile
    private var latest: Defs? = null

    /** What does not depend on the files: the rows, their ayahs and sizes. Rebuilt when the reciter, the names, the plan or the text change. */
    private val defs: Flow<Defs> = combine(catalog, planSurahs, uiPrefs.reciter, metrics) { rows, planSurahs, reciter, metrics ->
        buildDefs(rows, planSurahs, reciter, metrics)
    }.flowOn(Dispatchers.Default)

    private fun buildDefs(rows: List<SurahRowUi>, planSurahs: List<Int>, reciter: Reciter, metrics: TextMetrics?): Defs {
        fun def(
            kind: DownloadKind,
            number: Int,
            name: String,
            arabic: String,
            segments: List<Segment>,
            firstSurah: Int,
            lastSurah: Int,
        ) = RowDef(
            id = DownloadRequest.idOf(kind, number.takeIf { kind != DownloadKind.PLAN }, reciter.id),
            kind = kind,
            number = number,
            name = name,
            arabic = arabic,
            segments = segments,
            firstSurah = firstSurah,
            lastSurah = lastSurah,
            keys = DownloadSelection.keys(segments, reciter),
            estimate = metrics?.let { DownloadSize.segmentsBytes(it, segments, reciter) },
        )

        val surahDefs = rows.map { row ->
            def(DownloadKind.SURAH, row.number, row.name, row.arabicName, DownloadSelection.surah(row.number), row.number, row.number)
        }
        val bySurah = surahDefs.associateBy { it.number }
        val juzDefs = (1..JuzIndex.COUNT).map { juz ->
            val range = JuzIndex.range(juz)
            def(DownloadKind.JUZ, juz, "", JuzIndex.arabicName(juz), DownloadSelection.juz(juz), range.startSurah, range.endSurah)
        }
        val planRows = planSurahs.mapNotNull { bySurah[it] }
        val plan = planRows.takeIf { it.isNotEmpty() }?.let {
            def(DownloadKind.PLAN, 0, "", "", DownloadSelection.surahs(it.map { row -> row.number }), it.minOf { row -> row.number }, it.maxOf { row -> row.number })
        }
        return Defs(
            reciter = reciter,
            plan = plan,
            planRows = planRows,
            juz = juzDefs,
            surahs = surahDefs,
            searchIndex = if (rows.isEmpty()) null else HomeStateBuilder.searchIndex(rows),
        ).also { latest = it }
    }

    private class Status(val files: Map<String, Long>, val queue: QueueState, val phase: WorkPhase)

    private class Options(val wifiOnly: Boolean, val withTranslation: Boolean)

    private val status: Flow<Status> = combine(files, manager.queue.state, phase, ::Status)

    private val options: Flow<Options> = combine(manager.prefs.wifiOnly, manager.prefs.withTranslation, ::Options)

    val ui: StateFlow<DownloadsUi> = combine(defs, status, options, cacheBytes, metrics) { defs, status, options, cache, metrics ->
        build(defs, status, options, cache, metrics)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUi())

    init {
        refreshCache()
    }

    private fun build(defs: Defs, status: Status, options: Options, cacheBytes: Long, metrics: TextMetrics?): DownloadsUi {
        val queue = status.queue
        val active = queue.active

        fun rowOf(def: RowDef): RowUi {
            val state = rowState(def.keys, status.files)
            val doneNow = when (state) {
                is RowState.Partial -> state.done
                is RowState.Complete -> state.total
                RowState.NotDownloaded -> 0
            }
            val inQueue = queue.contains(def.id)
            val rowQueue = when {
                !inQueue -> RowQueue.None
                queue.failures.any { it.requestId == def.id } -> RowQueue.Failed
                active?.id == def.id && queue.paused -> RowQueue.Paused(doneNow, def.keys.size)
                active?.id == def.id -> RowQueue.Active(doneNow, def.keys.size)
                else -> RowQueue.Waiting
            }
            return RowUi(
                id = def.id,
                kind = def.kind,
                number = def.number,
                name = def.name,
                arabicName = def.arabic,
                firstSurah = def.firstSurah,
                lastSurah = def.lastSurah,
                surahCount = if (def.kind == DownloadKind.PLAN) def.segments.size else def.lastSurah - def.firstSurah + 1,
                ayahCount = def.keys.size,
                estimateBytes = def.estimate,
                state = state,
                queue = rowQueue,
            )
        }

        val queueUi = if (queue.requests.isEmpty()) {
            null
        } else {
            val files = status.files
            val keys = active?.keys().orEmpty()
            val pending = queue.requests.filter { request -> queue.failures.none { it.requestId == request.id } }
            QueueUi(
                title = active?.title.orEmpty(),
                done = keys.count { it in files },
                total = keys.size,
                waiting = queue.waiting,
                remainingBytes = metrics?.let { DownloadSize.remainingBytes(pending, files, it) },
                paused = queue.paused,
                phase = status.phase,
                failedAyahs = queue.failedAyahs,
                failedTranslations = queue.failedTranslations,
                hasActive = active != null,
            )
        }

        return DownloadsUi(
            loaded = defs.surahs.isNotEmpty(),
            reciter = defs.reciter,
            wifiOnly = options.wifiOnly,
            withTranslation = options.withTranslation,
            queue = queueUi,
            storage = StorageUi(
                downloadsBytes = status.files.values.sum(),
                cacheBytes = cacheBytes,
                freeBytes = freeSpace(),
            ),
            planAll = defs.plan?.let(::rowOf),
            planRows = defs.planRows.map(::rowOf),
            juz = defs.juz.map(::rowOf),
            surahs = defs.surahs.map(::rowOf),
            searchIndex = defs.searchIndex,
        )
    }

    // -- Actions -------------------------------------------------------------------------------

    /** The person tapped "Download" on a row (or "Retry" on a failed one). */
    fun download(rowId: String) {
        val defs = latest ?: return
        val def = defs.all[rowId] ?: return
        val state = rowState(def.keys, manager.storage.snapshot())
        if (state is RowState.Complete) return
        val have = (state as? RowState.Partial)?.bytes ?: 0L
        val missing = ((def.estimate ?: 0L) - have).coerceAtLeast(0L)
        if (missing > 0 && freeSpace() < missing + SPACE_RESERVE_BYTES) {
            eventChannel.trySend(DownloadsEvent.NotEnoughSpace)
            return
        }
        val copy = downloadsCopy(language.value)
        val title = when (def.kind) {
            DownloadKind.SURAH -> def.name
            DownloadKind.JUZ -> copy.juzTitleLabel(def.number)
            DownloadKind.PLAN -> copy.sectionPlan
        }
        val request = DownloadRequest(
            id = def.id,
            kind = def.kind,
            title = title,
            reciterId = defs.reciter.id,
            language = language.value.name.takeIf { manager.prefs.withTranslation.value },
            segments = def.segments,
        )
        manager.enqueue(request, def.estimate ?: 0L)
    }

    /** True once: the first time the screen starts a download, it may ask for the permission to show the notification. */
    fun takeNotificationPermissionTurn(): Boolean {
        if (manager.prefs.notificationPermissionAsked) return false
        manager.prefs.notificationPermissionAsked = true
        return true
    }

    fun pauseAll() = manager.pauseAll()

    fun resume() = manager.resume()

    fun cancelAll() = manager.cancelAll()

    fun cancel(rowId: String) = manager.cancel(rowId)

    fun retry() = manager.retry()

    fun setWifiOnly(value: Boolean) {
        manager.prefs.setWifiOnly(value)
        manager.onNetworkChoiceChanged()
    }

    fun setWithTranslation(value: Boolean) = manager.prefs.setWithTranslation(value)

    /** Deletes the downloaded files of one row. */
    fun delete(rowId: String) {
        val def = latest?.all?.get(rowId) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manager.delete(def.keys) }
            eventChannel.trySend(DownloadsEvent.Deleted)
        }
    }

    /** "Delete all downloads": asked for audio is not wanted any more, so the queue goes too. */
    fun deleteAll() {
        manager.cancelAll()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { manager.deleteAll() }
            eventChannel.trySend(DownloadsEvent.Deleted)
        }
    }

    /** "Clear" the listening cache (the downloads are not touched). */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { cache.clear() }
            cacheBytes.value = 0L
            eventChannel.trySend(DownloadsEvent.CacheCleared)
        }
    }

    /** Reads the size of the cache again (it grows while the person listens). */
    fun refreshCache() {
        viewModelScope.launch { cacheBytes.value = withContext(Dispatchers.IO) { cache.totalBytes() } }
    }

    private companion object {
        const val FILES_SAMPLE_MS = 300L
        const val PHASE_SETTLE_MS = 1_500L

        /** A download does not start when it would leave less than this free on the phone. */
        const val SPACE_RESERVE_BYTES = 64L * 1024 * 1024
    }
}

/** Bytes free on the storage the downloads are written to. */
internal fun freeSpaceOfPhone(): Long = try {
    StatFs(AndroidContextHolder.context.noBackupFilesDir.path).availableBytes
} catch (_: Exception) {
    Long.MAX_VALUE
}
