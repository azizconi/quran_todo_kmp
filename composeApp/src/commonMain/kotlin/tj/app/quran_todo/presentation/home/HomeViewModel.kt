package tj.app.quran_todo.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.audio.ayahAudioCacheKey
import tj.app.quran_todo.common.sync.CloudSyncStorage
import tj.app.quran_todo.common.sync.ProgressSnapshot
import tj.app.quran_todo.common.sync.SettingsSnapshot
import tj.app.quran_todo.common.sync.SurahTodoSnapshot
import tj.app.quran_todo.common.sync.AyahTodoSnapshot
import tj.app.quran_todo.common.sync.AyahReviewSnapshot
import tj.app.quran_todo.common.sync.AyahNoteSnapshot
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.common.utils.currentLocalDate
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.dao.AyahNoteDao
import tj.app.quran_todo.data.database.dao.AyahTodoDao
import tj.app.quran_todo.data.database.dao.AyahReviewDao
import tj.app.quran_todo.data.database.dao.FocusSessionDao
import tj.app.quran_todo.data.database.dao.SurahTodoDao
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.FocusSessionEntity
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoUpsertUseCase
import tj.app.quran_todo.domain.use_case.GetAyahTranslationsUseCase
import tj.app.quran_todo.domain.use_case.GetChapterNamesUseCase
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetAllUseCase
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.addWeakAyah
import tj.app.quran_todo.common.settings.removeWeakAyah
import tj.app.quran_todo.common.settings.weakAyahKeySet
import tj.app.quran_todo.common.settings.ReviewMemoryState
import tj.app.quran_todo.common.settings.ReviewStateStore
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.presentation.review.Sm2Scheduler

sealed interface QuranLoadState {
    data object Loading : QuranLoadState
    data object Content : QuranLoadState
    data class Error(val message: String) : QuranLoadState
}

data class HomeUiState(
    val surahList: List<SurahModel> = emptyList(),
    val todoSurahs: List<SurahTodoEntity> = emptyList(),
    val ayahTodos: List<AyahTodoEntity> = emptyList(),
    val completeQuran: List<SurahWithAyahs> = emptyList(),
    val chapterNames: Map<Int, ChapterNameModel> = emptyMap(),
    val dueReviews: List<AyahReviewEntity> = emptyList(),
    val lastActivityAt: Long? = null,
    val selectedSurah: SurahWithAyahs? = null,
    val selectedSurahNumbers: Set<Int> = emptySet(),
    val weakAyahKeys: Set<String> = emptySet(),
    val filter: SurahTodoStatus? = null,
    val quranLoadState: QuranLoadState = QuranLoadState.Loading,
    val pendingSurahStatuses: Map<Int, PendingSurahStatus> = emptyMap(),
    val statusUpdateError: String? = null,
    val offlineDownloadRunning: Boolean = false,
    val offlineDownloadDone: Int = 0,
    val offlineDownloadTotal: Int = 0,
    val offlineDownloadStatus: String? = null,
    val syncProviderLabel: String = "",
    val syncStatusMessage: String? = null,
    val lastSyncAt: Long? = null,
    val hasCloudSnapshot: Boolean = false,
    val restoredSettings: SettingsSnapshot? = null,
    val currentEpochDay: Int = currentLocalDate().toEpochDays(),
) {
    /** Compatibility accessors for legacy screens; [quranLoadState] is the source of truth. */
    val isLoadingQuran: Boolean
        get() = quranLoadState is QuranLoadState.Loading

    val errorMessage: String?
        get() = (quranLoadState as? QuranLoadState.Error)?.message
}

data class PendingSurahStatus(
    val status: SurahTodoStatus?,
    val requestId: Long,
)

class HomeViewModel(
    private val surahTodoDao: SurahTodoDao,
    private val ayahTodoDao: AyahTodoDao,
    private val ayahNoteDao: AyahNoteDao,
    private val ayahReviewDao: AyahReviewDao,
    private val focusSessionDao: FocusSessionDao,
    private val todoDeleteSurahUseCase: TodoDeleteSurahUseCase,
    private val todoDeleteSurahByNumberUseCase: TodoDeleteSurahByNumberUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    private val getCompleteQuranUseCase: GetCompleteQuranUseCase,
    private val getAyahTranslationsUseCase: GetAyahTranslationsUseCase,
    private val ayahTodoUpsertUseCase: AyahTodoUpsertUseCase,
    private val ayahTodoDeleteBySurahUseCase: AyahTodoDeleteBySurahUseCase,
    private val getChapterNamesUseCase: GetChapterNamesUseCase,
    ayahTodoGetAllUseCase: AyahTodoGetAllUseCase,
): ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var completeQuranJob: Job? = null
    private var chapterNamesJob: Job? = null
    private var lastChapterLanguage: AppLanguage? = null
    private var offlineDownloadJob: Job? = null
    private val offlineDownloadMutex = Mutex()
    private val surahStatusMutationMutex = Mutex()
    private val latestSurahStatusRequestIds = mutableMapOf<Int, Long>()
    private var nextSurahStatusRequestId = 0L
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        _uiState.value = _uiState.value.copy(
            weakAyahKeys = weakAyahKeySet(),
            syncProviderLabel = CloudSyncStorage.providerLabel(),
            hasCloudSnapshot = CloudSyncStorage.loadSnapshot() != null
        )

        viewModelScope.launch(Dispatchers.IO) {
            val list = parseSurahList()
            _uiState.value = _uiState.value.copy(surahList = list)
        }

        viewModelScope.launch {
            todoGetSurahListUseCase().collect { list ->
                val confirmedStatuses = list.associate { it.surahNumber to it.status }
                val pending = _uiState.value.pendingSurahStatuses.filter { (surahNumber, pendingStatus) ->
                    confirmedStatuses[surahNumber] != pendingStatus.status
                }
                _uiState.value = _uiState.value.copy(
                    todoSurahs = list,
                    pendingSurahStatuses = pending,
                )
            }
        }

        viewModelScope.launch {
            ayahTodoGetAllUseCase().collect { list ->
                val lastActivity = list.map { it.updatedAt }.filter { it > 0 }.maxOrNull()
                _uiState.value = _uiState.value.copy(
                    ayahTodos = list,
                    lastActivityAt = lastActivity
                )
                viewModelScope.launch(Dispatchers.IO) {
                    val weakAyahs = ReviewStateStore.withExclusiveAccess {
                        weakAyahKeySet()
                    }
                    _uiState.value = _uiState.value.copy(weakAyahKeys = weakAyahs)
                    refreshDueReviews()
                }
            }
        }

        refreshQuran(withLocalAction = true)
    }

    fun loadChapterNames(language: AppLanguage) {
        if (lastChapterLanguage == language && _uiState.value.chapterNames.isNotEmpty()) return
        lastChapterLanguage = language
        chapterNamesJob?.cancel()
        chapterNamesJob = viewModelScope.launch(Dispatchers.IO) {
            val names = getChapterNamesUseCase(language)
            if (lastChapterLanguage != language) return@launch
            _uiState.value = _uiState.value.copy(chapterNames = names)
        }
    }

    fun deleteSurahFromTodo(entity: SurahTodoEntity) {
        AppTelemetry.logEvent(
            name = "home_surah_deleted",
            params = mapOf("surah_number" to entity.surahNumber.toString())
        )
        viewModelScope.launch(Dispatchers.IO) {
            todoDeleteSurahUseCase(entity)
        }
    }

    fun upsertSurahToTodo(entity: SurahTodoEntity) {
        AppTelemetry.logEvent(
            name = "home_surah_saved",
            params = mapOf(
                "surah_number" to entity.surahNumber.toString(),
                "status" to entity.status.name.lowercase()
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            todoUpsertSurahUseCase(entity)
        }
    }

    fun clearStatusUpdateError() {
        _uiState.value = _uiState.value.copy(statusUpdateError = null)
    }

    fun setSurahStatus(surahNumber: Int, status: SurahTodoStatus?) {
        AppTelemetry.logEvent(
            name = "home_surah_status_changed",
            params = mapOf(
                "surah_number" to surahNumber.toString(),
                "status" to (status?.name?.lowercase() ?: "cleared")
            )
        )
        viewModelScope.launch {
            val requestId = surahStatusMutationMutex.withLock {
                (++nextSurahStatusRequestId).also { id ->
                    latestSurahStatusRequestIds[surahNumber] = id
                }
            }
            val previous = _uiState.value
            _uiState.value = previous.copy(
                pendingSurahStatuses = previous.pendingSurahStatuses +
                    (surahNumber to PendingSurahStatus(status, requestId)),
                statusUpdateError = null,
            )
            val result = withContext(Dispatchers.IO) {
                surahStatusMutationMutex.withLock {
                    if (latestSurahStatusRequestIds[surahNumber] != requestId) {
                        null
                    } else {
                        runCatching {
                            if (status == null) {
                                todoDeleteSurahByNumberUseCase(surahNumber)
                            } else {
                                todoUpsertSurahUseCase(SurahTodoEntity(surahNumber, status))
                            }
                        }
                    }
                }
            }
            result?.onFailure { throwable ->
                val isLatestRequest = surahStatusMutationMutex.withLock {
                    latestSurahStatusRequestIds[surahNumber] == requestId
                }
                val pendingStatus = _uiState.value.pendingSurahStatuses[surahNumber]
                if (isLatestRequest && pendingStatus?.requestId == requestId) {
                    _uiState.value = _uiState.value.copy(
                        pendingSurahStatuses = _uiState.value.pendingSurahStatuses - surahNumber,
                        statusUpdateError = throwable.message ?: "Не удалось изменить статус суры",
                    )
                    AppTelemetry.logError(
                        throwable = throwable,
                        context = "surah_status_update_failed",
                        params = mapOf("surah_number" to surahNumber.toString()),
                    )
                }
            }
        }
    }

    fun completeReview(
        ayahNumber: Int,
        surahNumber: Int,
        quality: ReviewQuality = ReviewQuality.GOOD,
    ) {
        AppTelemetry.logEvent(
            name = "home_review_completed",
            params = mapOf(
                "surah_number" to surahNumber.toString(),
                "ayah_number" to ayahNumber.toString(),
                "quality" to quality.name.lowercase()
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            ReviewStateStore.withExclusiveAccess {
                val now = getTimeMillis()
                val currentState = get(ayahNumber)
                val nextState = Sm2Scheduler.nextState(currentState, quality)
                put(ayahNumber, nextState)
                val nextAt = now + nextState.intervalDays.toLong() * dayMillis
                ayahReviewDao.upsert(
                    AyahReviewEntity(
                        ayahNumber = ayahNumber,
                        surahNumber = surahNumber,
                        nextReviewAt = nextAt,
                        intervalIndex = nextState.repetitions,
                        lastReviewedAt = now
                    )
                )
                when (quality) {
                    ReviewQuality.FORGOT,
                    ReviewQuality.HARD,
                    -> addWeakAyah(surahNumber, ayahNumber)
                    ReviewQuality.EASY -> removeWeakAyah(surahNumber, ayahNumber)
                    ReviewQuality.GOOD -> Unit
                }
                _uiState.value = _uiState.value.copy(weakAyahKeys = weakAyahKeySet())
                refreshDueReviews()
            }
        }
    }

    fun startOfflinePackage(language: AppLanguage) {
        if (offlineDownloadJob?.isActive == true || _uiState.value.offlineDownloadRunning) return
        offlineDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            offlineDownloadMutex.withLock {
                if (_uiState.value.offlineDownloadRunning) return@withLock

                val complete = _uiState.value.completeQuran
                if (complete.isEmpty()) return@withLock
                val learningSurahNumbers = _uiState.value.todoSurahs
                    .filter { it.status == SurahTodoStatus.LEARNING || it.status == SurahTodoStatus.LEARNED }
                    .map { it.surahNumber }
                    .toSet()
                val targets = if (learningSurahNumbers.isNotEmpty()) {
                    complete.filter { learningSurahNumbers.contains(it.surah.number) }
                } else {
                    complete.take(3)
                }
                val total = targets.sumOf { it.ayahs.size }
                if (total <= 0) return@withLock

                _uiState.value = _uiState.value.copy(
                    offlineDownloadRunning = true,
                    offlineDownloadDone = 0,
                    offlineDownloadTotal = total,
                    offlineDownloadStatus = "DOWNLOADING"
                )
                AppTelemetry.logEvent(
                    name = "home_offline_package_started",
                    params = mapOf(
                        "language" to language.name.lowercase(),
                        "total_ayahs" to total.toString()
                    )
                )

                val cache = AudioCache()
                var done = 0
                var translationFailures = 0
                targets.forEach { surah ->
                    val translations = runCatching {
                        getAyahTranslationsUseCase(
                            surahNumber = surah.surah.number,
                            language = language,
                            expectedCount = surah.ayahs.size
                        )
                    }.getOrNull().orEmpty()
                    if (translations.size < surah.ayahs.size) {
                        translationFailures += 1
                    }

                    surah.ayahs.forEach { ayah ->
                        val url = "https://cdn.islamic.network/quran/audio/128/ar.alafasy/${ayah.number}.mp3"
                        val cacheKey = ayahAudioCacheKey(
                            surahNumber = ayah.surahNumber,
                            ayahNumberInSurah = ayah.numberInSurah
                        )
                        if (cache.prefetch(url, cacheKey)) {
                            done += 1
                        }
                        _uiState.value = _uiState.value.copy(
                            offlineDownloadDone = done,
                            offlineDownloadTotal = total
                        )
                    }
                }

                val ready = done == total && translationFailures == 0
                _uiState.value = _uiState.value.copy(
                    offlineDownloadRunning = false,
                    offlineDownloadStatus = if (ready) "READY" else "PARTIAL"
                )
                AppTelemetry.logEvent(
                    name = if (ready) {
                        "home_offline_package_completed"
                    } else {
                        "home_offline_package_partial"
                    },
                    params = mapOf(
                        "cached_ayahs" to done.toString(),
                        "total_ayahs" to total.toString(),
                        "translation_failures" to translationFailures.toString()
                    )
                )
            }
        }
    }

    fun recordFocusSession(durationMinutes: Int) {
        AppTelemetry.logEvent(
            name = "home_focus_session_completed",
            params = mapOf("minutes" to durationMinutes.toString())
        )
        viewModelScope.launch(Dispatchers.IO) {
            focusSessionDao.insert(
                FocusSessionEntity(
                    startedAt = getTimeMillis(),
                    durationMinutes = durationMinutes
                )
            )
        }
    }

    fun refreshDueReviewsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshDueReviews()
        }
    }

    fun consumeRestoredSettings() {
        _uiState.value = _uiState.value.copy(restoredSettings = null)
    }

    fun syncProgressToCloud(settings: AppSettings) {
        AppTelemetry.logEvent(
            name = "home_cloud_sync_started",
            params = mapOf("provider" to CloudSyncStorage.providerLabel())
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val (weakAyahKeys, ayahReviews) = ReviewStateStore.withExclusiveAccess {
                    weakAyahKeySet() to ayahReviewDao.getAll().map {
                        AyahReviewSnapshot(
                            ayahNumber = it.ayahNumber,
                            surahNumber = it.surahNumber,
                            nextReviewAt = it.nextReviewAt,
                            intervalIndex = it.intervalIndex,
                            lastReviewedAt = it.lastReviewedAt
                        )
                    }
                }
                val snapshot = ProgressSnapshot(
                    createdAt = getTimeMillis(),
                    settings = SettingsSnapshot(
                        dailyGoal = settings.dailyGoal,
                        focusMinutes = settings.focusMinutes,
                        remindersEnabled = settings.remindersEnabled,
                        targetAyahs = settings.targetAyahs,
                        targetEpochDay = settings.targetEpochDay,
                        examModeEnabled = settings.examModeEnabled,
                        readingFontSize = settings.readingFontSize
                    ),
                    weakAyahKeys = weakAyahKeys,
                    recitationMetricsJson = UserSettingsStorage.getRecitationMetricsJson(),
                    surahTodos = _uiState.value.todoSurahs.map {
                        SurahTodoSnapshot(
                            surahNumber = it.surahNumber,
                            status = it.status.name
                        )
                    },
                    ayahTodos = _uiState.value.ayahTodos.map {
                        AyahTodoSnapshot(
                            ayahNumber = it.ayahNumber,
                            surahNumber = it.surahNumber,
                            status = it.status.name,
                            updatedAt = it.updatedAt
                        )
                    },
                    ayahReviews = ayahReviews,
                    ayahNotes = ayahNoteDao.getAll().map {
                        AyahNoteSnapshot(
                            ayahNumber = it.ayahNumber,
                            surahNumber = it.surahNumber,
                            note = it.note,
                            updatedAt = it.updatedAt
                        )
                    }
                )
                CloudSyncStorage.saveSnapshot(json.encodeToString(snapshot))
                _uiState.value = _uiState.value.copy(
                    hasCloudSnapshot = true,
                    lastSyncAt = snapshot.createdAt,
                    syncStatusMessage = "Synced to ${CloudSyncStorage.providerLabel()}."
                )
                AppTelemetry.logEvent(
                    name = "home_cloud_sync_success",
                    params = mapOf("provider" to CloudSyncStorage.providerLabel())
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    syncStatusMessage = "Cloud sync failed."
                )
                AppTelemetry.logError(
                    throwable = it,
                    context = "home_cloud_sync_failed",
                    params = mapOf("provider" to CloudSyncStorage.providerLabel())
                )
            }
        }
    }

    fun restoreProgressFromCloud() {
        AppTelemetry.logEvent(
            name = "home_cloud_restore_started",
            params = mapOf("provider" to CloudSyncStorage.providerLabel())
        )
        viewModelScope.launch(Dispatchers.IO) {
            val raw = CloudSyncStorage.loadSnapshot()
            if (raw.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    hasCloudSnapshot = false,
                    syncStatusMessage = "No cloud snapshot found."
                )
                AppTelemetry.logEvent(
                    name = "home_cloud_restore_skipped",
                    params = mapOf("reason" to "snapshot_missing")
                )
                return@launch
            }

            runCatching {
                json.decodeFromString<ProgressSnapshot>(raw)
            }.onSuccess { snapshot ->
                val surahTodos = snapshot.surahTodos.mapNotNull { item ->
                    runCatching {
                        SurahTodoEntity(
                            surahNumber = item.surahNumber,
                            status = SurahTodoStatus.valueOf(item.status)
                        )
                    }.getOrNull()
                }
                val ayahTodos = snapshot.ayahTodos.mapNotNull { item ->
                    runCatching {
                        AyahTodoEntity(
                            ayahNumber = item.ayahNumber,
                            surahNumber = item.surahNumber,
                            status = AyahTodoStatus.valueOf(item.status),
                            updatedAt = item.updatedAt
                        )
                    }.getOrNull()
                }
                val ayahReviews = snapshot.ayahReviews.map {
                    AyahReviewEntity(
                        ayahNumber = it.ayahNumber,
                        surahNumber = it.surahNumber,
                        nextReviewAt = it.nextReviewAt,
                        intervalIndex = it.intervalIndex,
                        lastReviewedAt = it.lastReviewedAt
                    )
                }
                val ayahNotes = snapshot.ayahNotes.map {
                    tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity(
                        ayahNumber = it.ayahNumber,
                        surahNumber = it.surahNumber,
                        note = it.note,
                        updatedAt = it.updatedAt
                    )
                }
                val now = getTimeMillis()
                val reviewMemoryStates = ayahReviews.associate { review ->
                    val daysLeft = ((review.nextReviewAt - now) / dayMillis)
                        .toInt()
                        .coerceAtLeast(1)
                    review.ayahNumber to
                        tj.app.quran_todo.common.settings.ReviewMemoryState(
                            repetitions = review.intervalIndex.coerceAtLeast(0),
                            intervalDays = daysLeft,
                            easiness = 2.5f
                        )
                }

                ReviewStateStore.withExclusiveAccess {
                    surahTodoDao.clear()
                    ayahTodoDao.clear()
                    ayahReviewDao.clear()
                    ayahNoteDao.clear()

                    if (surahTodos.isNotEmpty()) surahTodoDao.upsertAll(surahTodos)
                    if (ayahTodos.isNotEmpty()) ayahTodoDao.upsertAll(ayahTodos)
                    if (ayahReviews.isNotEmpty()) ayahReviewDao.upsertAll(ayahReviews)
                    if (ayahNotes.isNotEmpty()) ayahNoteDao.upsertAll(ayahNotes)

                    replaceAll(reviewMemoryStates)
                    UserSettingsStorage.saveWeakAyahKeys(snapshot.weakAyahKeys)
                }

                UserSettingsStorage.saveDailyGoal(snapshot.settings.dailyGoal)
                UserSettingsStorage.saveFocusMinutes(snapshot.settings.focusMinutes)
                UserSettingsStorage.saveReminderEnabled(snapshot.settings.remindersEnabled)
                UserSettingsStorage.saveTargetAyahs(snapshot.settings.targetAyahs)
                UserSettingsStorage.saveTargetEpochDay(snapshot.settings.targetEpochDay)
                UserSettingsStorage.saveExamModeEnabled(snapshot.settings.examModeEnabled)
                UserSettingsStorage.saveReadingFontSize(snapshot.settings.readingFontSize)
                UserSettingsStorage.saveRecitationMetricsJson(snapshot.recitationMetricsJson ?: "")

                _uiState.value = _uiState.value.copy(
                    weakAyahKeys = snapshot.weakAyahKeys,
                    hasCloudSnapshot = true,
                    lastSyncAt = snapshot.createdAt,
                    restoredSettings = snapshot.settings,
                    syncStatusMessage = "Restored from ${CloudSyncStorage.providerLabel()}."
                )
                AppTelemetry.logEvent(
                    name = "home_cloud_restore_success",
                    params = mapOf("provider" to CloudSyncStorage.providerLabel())
                )
                refreshDueReviews()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    syncStatusMessage = "Cloud restore failed."
                )
                AppTelemetry.logError(
                    throwable = it,
                    context = "home_cloud_restore_failed",
                    params = mapOf("provider" to CloudSyncStorage.providerLabel())
                )
            }
        }
    }

    private suspend fun refreshDueReviews() {
        val due = ayahReviewDao.getDue(getTimeMillis())
        _uiState.value = _uiState.value.copy(
            dueReviews = due,
            currentEpochDay = currentLocalDate().toEpochDays()
        )
    }

    private companion object {
        const val dayMillis = 24L * 60 * 60 * 1000
    }

    fun refreshQuran(withLocalAction: Boolean = true) {
        AppTelemetry.logEvent(
            name = "home_quran_refresh_started",
            params = mapOf("with_local_action" to withLocalAction.toString())
        )
        completeQuranJob?.cancel()
        completeQuranJob = viewModelScope.launch {
            getCompleteQuranUseCase(withLocalAction).collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(
                            quranLoadState = QuranLoadState.Loading,
                        )
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            completeQuran = result.data,
                            quranLoadState = QuranLoadState.Content,
                        )
                        AppTelemetry.logEvent(
                            name = "home_quran_refresh_success",
                            params = mapOf("surah_count" to result.data.size.toString())
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            quranLoadState = QuranLoadState.Error(
                                result.errorMessage ?: "Unable to load Quran",
                            ),
                        )
                        AppTelemetry.logEvent(
                            name = "home_quran_refresh_failed",
                            params = mapOf("message" to (result.errorMessage ?: "unknown"))
                        )
                    }
                    is Resource.Idle -> Unit
                }
            }
        }
    }

    fun setFilter(filter: SurahTodoStatus?) {
        AppTelemetry.logEvent(
            name = "home_filter_changed",
            params = mapOf("filter" to (filter?.name?.lowercase() ?: "all"))
        )
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun toggleSelection(surahNumber: Int) {
        val current = _uiState.value.selectedSurahNumbers
        val updated = if (current.contains(surahNumber)) {
            current - surahNumber
        } else {
            current + surahNumber
        }
        _uiState.value = _uiState.value.copy(selectedSurahNumbers = updated)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedSurahNumbers = emptySet())
    }

    fun markSelected(status: SurahTodoStatus) {
        val selected = _uiState.value.selectedSurahNumbers
        if (selected.isEmpty()) return
        AppTelemetry.logEvent(
            name = "home_bulk_status_changed",
            params = mapOf(
                "count" to selected.size.toString(),
                "status" to status.name.lowercase()
            )
        )
        selected.forEach { number ->
            setSurahStatus(number, status)
        }
        _uiState.value = _uiState.value.copy(selectedSurahNumbers = emptySet())
    }

    fun openSurahDetail(surahNumber: Int) {
        AppTelemetry.logEvent(
            name = "home_surah_opened",
            params = mapOf("surah_number" to surahNumber.toString())
        )
        val surah = _uiState.value.completeQuran.firstOrNull {
            it.surah.number == surahNumber
        }
        _uiState.value = _uiState.value.copy(selectedSurah = surah)
    }

    fun dismissSurahDetail() {
        _uiState.value = _uiState.value.copy(
            selectedSurah = null,
            weakAyahKeys = weakAyahKeySet()
        )
    }
}
