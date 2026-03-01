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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.audio.AudioCache
import tj.app.quran_todo.common.sync.CloudSyncStorage
import tj.app.quran_todo.common.sync.ProgressSnapshot
import tj.app.quran_todo.common.sync.SettingsSnapshot
import tj.app.quran_todo.common.sync.SurahTodoSnapshot
import tj.app.quran_todo.common.sync.AyahTodoSnapshot
import tj.app.quran_todo.common.sync.AyahReviewSnapshot
import tj.app.quran_todo.common.sync.AyahNoteSnapshot
import tj.app.quran_todo.common.utils.Resource
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
import tj.app.quran_todo.common.settings.ReviewStateStore
import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.presentation.review.Sm2Scheduler

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
    val isLoadingQuran: Boolean = false,
    val errorMessage: String? = null,
    val offlineDownloadRunning: Boolean = false,
    val offlineDownloadDone: Int = 0,
    val offlineDownloadTotal: Int = 0,
    val offlineDownloadStatus: String? = null,
    val syncProviderLabel: String = "",
    val syncStatusMessage: String? = null,
    val lastSyncAt: Long? = null,
    val hasCloudSnapshot: Boolean = false,
    val restoredSettings: SettingsSnapshot? = null,
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
    private var lastChapterLanguage: AppLanguage? = null
    private val offlineDownloadMutex = Mutex()
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
                _uiState.value = _uiState.value.copy(todoSurahs = list)
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
                    refreshDueReviews()
                }
            }
        }

        refreshQuran(withLocalAction = true)
    }

    fun loadChapterNames(language: AppLanguage) {
        if (lastChapterLanguage == language && _uiState.value.chapterNames.isNotEmpty()) return
        lastChapterLanguage = language
        viewModelScope.launch(Dispatchers.IO) {
            val names = getChapterNamesUseCase(language)
            _uiState.value = _uiState.value.copy(chapterNames = names)
        }
    }

    fun deleteSurahFromTodo(entity: SurahTodoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            todoDeleteSurahUseCase(entity)
        }
    }

    fun upsertSurahToTodo(entity: SurahTodoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            todoUpsertSurahUseCase(entity)
        }
    }

    fun setSurahStatus(surahNumber: Int, status: SurahTodoStatus?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (status == null) {
                todoDeleteSurahByNumberUseCase(surahNumber)
                ayahTodoDeleteBySurahUseCase(surahNumber)
                ayahReviewDao.deleteBySurahNumber(surahNumber)
                val ayahNumbers = _uiState.value.completeQuran.firstOrNull {
                    it.surah.number == surahNumber
                }?.ayahs?.map { it.number }.orEmpty()
                ReviewStateStore.removeAll(ayahNumbers)
                val weak = weakAyahKeySet().filterNot { it.startsWith("$surahNumber:") }.toSet()
                UserSettingsStorage.saveWeakAyahKeys(weak)
                _uiState.value = _uiState.value.copy(weakAyahKeys = weak)
                return@launch
            }

            todoUpsertSurahUseCase(SurahTodoEntity(surahNumber, status))
            val ayahs = _uiState.value.completeQuran.firstOrNull {
                it.surah.number == surahNumber
            }?.ayahs ?: emptyList()

            ayahs.forEach { ayah ->
                ayahTodoUpsertUseCase(
                    AyahTodoEntity(
                        ayahNumber = ayah.number,
                        surahNumber = surahNumber,
                        status = when (status) {
                            SurahTodoStatus.LEARNED -> AyahTodoStatus.LEARNED
                            SurahTodoStatus.LEARNING -> AyahTodoStatus.LEARNING
                        },
                        updatedAt = getTimeMillis()
                    )
                )
                scheduleReview(
                    ayahNumber = ayah.number,
                    surahNumber = surahNumber,
                    status = status
                )
            }
            refreshDueReviews()
        }
    }

    fun completeReview(
        ayahNumber: Int,
        surahNumber: Int,
        quality: ReviewQuality = ReviewQuality.GOOD,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = getTimeMillis()
            val currentState = ReviewStateStore.get(ayahNumber)
            val nextState = Sm2Scheduler.nextState(currentState, quality)
            ReviewStateStore.put(ayahNumber, nextState)
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
                ReviewQuality.HARD -> addWeakAyah(surahNumber, ayahNumber)
                ReviewQuality.EASY -> removeWeakAyah(surahNumber, ayahNumber)
                ReviewQuality.GOOD -> Unit
            }
            _uiState.value = _uiState.value.copy(weakAyahKeys = weakAyahKeySet())
            refreshDueReviews()
        }
    }

    fun startOfflinePackage(language: AppLanguage) {
        viewModelScope.launch(Dispatchers.IO) {
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

                val cache = AudioCache()
                var done = 0
                targets.forEach { surah ->
                    runCatching {
                        getAyahTranslationsUseCase(
                            surahNumber = surah.surah.number,
                            language = language,
                            expectedCount = surah.ayahs.size
                        )
                    }

                    surah.ayahs.forEach { ayah ->
                        val url = "https://cdn.islamic.network/quran/audio/128/ar.alafasy/${ayah.number}.mp3"
                        val cacheKey = "ayah_${ayah.surahNumber}_${ayah.numberInSurah}"
                        cache.prefetch(url, cacheKey)
                        done += 1
                        _uiState.value = _uiState.value.copy(
                            offlineDownloadDone = done,
                            offlineDownloadTotal = total
                        )
                    }
                }

                _uiState.value = _uiState.value.copy(
                    offlineDownloadRunning = false,
                    offlineDownloadStatus = "READY"
                )
            }
        }
    }

    fun recordFocusSession(durationMinutes: Int) {
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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val snapshot = ProgressSnapshot(
                    createdAt = getTimeMillis(),
                    settings = SettingsSnapshot(
                        dailyGoal = settings.dailyGoal,
                        focusMinutes = settings.focusMinutes,
                        remindersEnabled = settings.remindersEnabled,
                        targetAyahs = settings.targetAyahs,
                        targetEpochDay = settings.targetEpochDay,
                        examModeEnabled = settings.examModeEnabled
                    ),
                    weakAyahKeys = weakAyahKeySet(),
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
                    ayahReviews = ayahReviewDao.getAll().map {
                        AyahReviewSnapshot(
                            ayahNumber = it.ayahNumber,
                            surahNumber = it.surahNumber,
                            nextReviewAt = it.nextReviewAt,
                            intervalIndex = it.intervalIndex,
                            lastReviewedAt = it.lastReviewedAt
                        )
                    },
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
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    syncStatusMessage = "Cloud sync failed."
                )
            }
        }
    }

    fun restoreProgressFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = CloudSyncStorage.loadSnapshot()
            if (raw.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    hasCloudSnapshot = false,
                    syncStatusMessage = "No cloud snapshot found."
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

                surahTodoDao.clear()
                ayahTodoDao.clear()
                ayahReviewDao.clear()
                ayahNoteDao.clear()

                if (surahTodos.isNotEmpty()) surahTodoDao.upsertAll(surahTodos)
                if (ayahTodos.isNotEmpty()) ayahTodoDao.upsertAll(ayahTodos)
                if (ayahReviews.isNotEmpty()) ayahReviewDao.upsertAll(ayahReviews)
                if (ayahNotes.isNotEmpty()) ayahNoteDao.upsertAll(ayahNotes)

                ReviewStateStore.clear()
                val now = getTimeMillis()
                ayahReviews.forEach { review ->
                    val daysLeft = ((review.nextReviewAt - now) / dayMillis).toInt().coerceAtLeast(1)
                    ReviewStateStore.put(
                        review.ayahNumber,
                        tj.app.quran_todo.common.settings.ReviewMemoryState(
                            repetitions = review.intervalIndex.coerceAtLeast(0),
                            intervalDays = daysLeft,
                            easiness = 2.5f
                        )
                    )
                }

                UserSettingsStorage.saveDailyGoal(snapshot.settings.dailyGoal)
                UserSettingsStorage.saveFocusMinutes(snapshot.settings.focusMinutes)
                UserSettingsStorage.saveReminderEnabled(snapshot.settings.remindersEnabled)
                UserSettingsStorage.saveTargetAyahs(snapshot.settings.targetAyahs)
                UserSettingsStorage.saveTargetEpochDay(snapshot.settings.targetEpochDay)
                UserSettingsStorage.saveExamModeEnabled(snapshot.settings.examModeEnabled)
                UserSettingsStorage.saveWeakAyahKeys(snapshot.weakAyahKeys)
                UserSettingsStorage.saveRecitationMetricsJson(snapshot.recitationMetricsJson ?: "")

                _uiState.value = _uiState.value.copy(
                    weakAyahKeys = snapshot.weakAyahKeys,
                    hasCloudSnapshot = true,
                    lastSyncAt = snapshot.createdAt,
                    restoredSettings = snapshot.settings,
                    syncStatusMessage = "Restored from ${CloudSyncStorage.providerLabel()}."
                )
                refreshDueReviews()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    syncStatusMessage = "Cloud restore failed."
                )
            }
        }
    }

    private suspend fun refreshDueReviews() {
        val due = ayahReviewDao.getDue(getTimeMillis())
        _uiState.value = _uiState.value.copy(dueReviews = due)
    }

    private suspend fun scheduleReview(
        ayahNumber: Int,
        surahNumber: Int,
        status: SurahTodoStatus,
    ) {
        val now = getTimeMillis()
        val state = ReviewStateStore.get(ayahNumber) ?: Sm2Scheduler.initialState().also {
            ReviewStateStore.put(ayahNumber, it)
        }
        val nextAt = now + state.intervalDays.toLong() * dayMillis
        if (status == SurahTodoStatus.LEARNING || status == SurahTodoStatus.LEARNED) {
            ayahReviewDao.upsert(
                AyahReviewEntity(
                    ayahNumber = ayahNumber,
                    surahNumber = surahNumber,
                    nextReviewAt = nextAt,
                    intervalIndex = state.repetitions,
                    lastReviewedAt = now
                )
            )
        }
    }

    private companion object {
        const val dayMillis = 24L * 60 * 60 * 1000
    }

    fun refreshQuran(withLocalAction: Boolean = true) {
        completeQuranJob?.cancel()
        completeQuranJob = viewModelScope.launch {
            getCompleteQuranUseCase(withLocalAction).collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingQuran = true,
                            errorMessage = null
                        )
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            completeQuran = result.data,
                            isLoadingQuran = false,
                            errorMessage = null
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingQuran = false,
                            errorMessage = result.errorMessage
                        )
                    }
                    is Resource.Idle -> Unit
                }
            }
        }
    }

    fun setFilter(filter: SurahTodoStatus?) {
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
        selected.forEach { number ->
            setSurahStatus(number, status)
        }
        _uiState.value = _uiState.value.copy(selectedSurahNumbers = emptySet())
    }

    fun openSurahDetail(surahNumber: Int) {
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
