package tj.app.quran_todo.presentation.surah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.dao.AyahNoteDao
import tj.app.quran_todo.data.database.dao.AyahReviewDao
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteByAyahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahOnceUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoUpsertUseCase
import tj.app.quran_todo.domain.use_case.GetAyahTranslationsUseCase
import tj.app.quran_todo.domain.use_case.GetChapterNamesUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.addWeakAyah
import tj.app.quran_todo.common.settings.removeWeakAyah
import tj.app.quran_todo.common.settings.ReviewStateStore
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.presentation.review.ReviewQuality
import tj.app.quran_todo.presentation.review.Sm2Scheduler

class SurahViewModel(
    private val ayahNoteDao: AyahNoteDao,
    private val ayahReviewDao: AyahReviewDao,
    private val ayahTodoGetBySurahUseCase: AyahTodoGetBySurahUseCase,
    private val ayahTodoUpsertUseCase: AyahTodoUpsertUseCase,
    private val ayahTodoDeleteByAyahUseCase: AyahTodoDeleteByAyahUseCase,
    private val ayahTodoGetBySurahOnceUseCase: AyahTodoGetBySurahOnceUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    private val todoDeleteSurahByNumberUseCase: TodoDeleteSurahByNumberUseCase,
    private val todoGetSurahListUseCase: TodoGetSurahListUseCase,
    private val getChapterNamesUseCase: GetChapterNamesUseCase,
    private val getAyahTranslationsUseCase: GetAyahTranslationsUseCase,
) : ViewModel() {

    private val _ayahTranslations = MutableStateFlow<Map<Int, String>>(emptyMap())
    val ayahTranslations: StateFlow<Map<Int, String>> = _ayahTranslations.asStateFlow()

    private val _ruSurahNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val ruSurahNames: StateFlow<Map<Int, String>> = _ruSurahNames.asStateFlow()

    private val _chapterNames = MutableStateFlow<Map<Int, ChapterNameModel>>(emptyMap())
    val chapterNames: StateFlow<Map<Int, ChapterNameModel>> = _chapterNames.asStateFlow()

    private val _notes = MutableStateFlow<Map<Int, AyahNoteEntity>>(emptyMap())
    val notes: StateFlow<Map<Int, AyahNoteEntity>> = _notes.asStateFlow()

    private val _reviews = MutableStateFlow<Map<Int, AyahReviewEntity>>(emptyMap())
    val reviews: StateFlow<Map<Int, AyahReviewEntity>> = _reviews.asStateFlow()

    private var lastLoaded: Pair<Int, AppLanguage>? = null
    private var lastChapterLanguage: AppLanguage? = null
    private var translationJob: Job? = null
    private var chapterNamesJob: Job? = null
    private val surahStatusMutationMutex = Mutex()
    private val latestSurahStatusRequestIds = mutableMapOf<Int, Long>()
    private var nextSurahStatusRequestId = 0L
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val list = parseSurahList()
            _ruSurahNames.value = list.associate { it.surahNumber to it.name }
        }
    }

    fun ayahTodos(surahNumber: Int): Flow<List<AyahTodoEntity>> =
        ayahTodoGetBySurahUseCase(surahNumber)

    fun surahStatus(surahNumber: Int): Flow<SurahTodoStatus?> =
        todoGetSurahListUseCase().map { list ->
            list.firstOrNull { it.surahNumber == surahNumber }?.status
        }

    fun setSurahStatus(
        surahNumber: Int,
        status: SurahTodoStatus?,
        onCompleted: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        AppTelemetry.logEvent(
            name = "surah_status_changed",
            params = mapOf(
                "surah_number" to surahNumber.toString(),
                "status" to (status?.name?.lowercase() ?: "not_started"),
            ),
        )
        viewModelScope.launch {
            val requestId = surahStatusMutationMutex.withLock {
                (++nextSurahStatusRequestId).also { id ->
                    latestSurahStatusRequestIds[surahNumber] = id
                }
            }
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
            if (surahStatusMutationMutex.withLock {
                    latestSurahStatusRequestIds[surahNumber] == requestId
                }
            ) {
                result?.onSuccess { onCompleted() }?.onFailure { throwable ->
                    AppTelemetry.logError(
                        throwable = throwable,
                        context = "surah_status_update_failed",
                        params = mapOf("surah_number" to surahNumber.toString()),
                    )
                    onError(throwable.message ?: "Unable to update surah status")
                }
            }
        }
    }

    fun observeNotes(surahNumber: Int) {
        viewModelScope.launch {
            ayahNoteDao.getBySurahNumber(surahNumber).collect { list ->
                _notes.value = list.associateBy { it.ayahNumber }
            }
        }
    }

    fun observeReviews(surahNumber: Int) {
        viewModelScope.launch {
            ayahReviewDao.getBySurahNumber(surahNumber).collect { list ->
                _reviews.value = list.associateBy { it.ayahNumber }
            }
        }
    }

    fun loadTranslations(surahNumber: Int, language: AppLanguage, expectedCount: Int) {
        if (lastLoaded == surahNumber to language &&
            _ayahTranslations.value.isNotEmpty() &&
            (_ayahTranslations.value.size == expectedCount || expectedCount <= 0)
        ) {
            return
        }
        lastLoaded = surahNumber to language
        _ayahTranslations.value = emptyMap()
        translationJob?.cancel()

        translationJob = viewModelScope.launch(Dispatchers.IO) {
            val translations = getAyahTranslationsUseCase(
                surahNumber = surahNumber,
                language = language,
                expectedCount = expectedCount
            )
            if (lastLoaded != (surahNumber to language)) return@launch
            _ayahTranslations.value = translations
            AppTelemetry.logEvent(
                name = "surah_translations_loaded",
                params = mapOf(
                    "surah_number" to surahNumber.toString(),
                    "language" to language.name.lowercase(),
                    "count" to translations.size.toString()
                )
            )
        }
    }

    fun loadChapterNames(language: AppLanguage) {
        if (lastChapterLanguage == language && _chapterNames.value.isNotEmpty()) return
        lastChapterLanguage = language
        chapterNamesJob?.cancel()
        chapterNamesJob = viewModelScope.launch(Dispatchers.IO) {
            val names = getChapterNamesUseCase(language)
            if (lastChapterLanguage != language) return@launch
            _chapterNames.value = names
        }
    }

    fun updateAyahStatus(
        ayahNumber: Int,
        surahNumber: Int,
        totalAyahs: Int,
        status: AyahTodoStatus,
        scheduleNextReview: Boolean = true,
        weakState: Boolean? = null,
        onCompleted: () -> Unit = {},
    ) {
        AppTelemetry.logEvent(
            name = "surah_ayah_status_changed",
            params = mapOf(
                "surah_number" to surahNumber.toString(),
                "ayah_number" to ayahNumber.toString(),
                "status" to status.name.lowercase()
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            ReviewStateStore.withExclusiveAccess {
                ayahTodoUpsertUseCase(
                    AyahTodoEntity(
                        ayahNumber = ayahNumber,
                        surahNumber = surahNumber,
                        status = status,
                        updatedAt = getTimeMillis()
                    )
                )
                if (scheduleNextReview) {
                    scheduleReview(ayahNumber, surahNumber)
                }
                when (weakState) {
                    true -> addWeakAyah(surahNumber, ayahNumber)
                    false -> removeWeakAyah(surahNumber, ayahNumber)
                    null -> Unit
                }
                syncSurahStatusInternal(surahNumber, totalAyahs)
                onCompleted()
            }
        }
    }

    fun clearAyahStatus(
        ayahNumber: Int,
        surahNumber: Int,
        totalAyahs: Int,
        onCompleted: () -> Unit = {},
    ) {
        AppTelemetry.logEvent(
            name = "surah_ayah_status_cleared",
            params = mapOf(
                "surah_number" to surahNumber.toString(),
                "ayah_number" to ayahNumber.toString()
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            ReviewStateStore.withExclusiveAccess {
                ayahTodoDeleteByAyahUseCase(ayahNumber)
                ayahReviewDao.deleteByAyahNumber(ayahNumber)
                remove(ayahNumber)
                syncSurahStatusInternal(surahNumber, totalAyahs)
                onCompleted()
            }
        }
    }

    fun upsertNote(ayahNumber: Int, surahNumber: Int, note: String) {
        AppTelemetry.logEvent(
            name = if (note.isBlank()) "surah_note_cleared" else "surah_note_saved",
            params = mapOf(
                "surah_number" to surahNumber.toString(),
                "ayah_number" to ayahNumber.toString()
            )
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (note.isBlank()) {
                ayahNoteDao.getByAyahNumber(ayahNumber)?.let { ayahNoteDao.delete(it) }
            } else {
                ayahNoteDao.upsert(
                    AyahNoteEntity(
                        ayahNumber = ayahNumber,
                        surahNumber = surahNumber,
                        note = note.trim(),
                        updatedAt = getTimeMillis()
                    )
                )
            }
        }
    }

    fun completeReview(
        ayahNumber: Int,
        surahNumber: Int,
        quality: ReviewQuality = ReviewQuality.GOOD,
        forceWeak: Boolean = false,
    ) {
        AppTelemetry.logEvent(
            name = "surah_review_completed",
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
                if (forceWeak) {
                    addWeakAyah(surahNumber, ayahNumber)
                } else {
                    when (quality) {
                        ReviewQuality.FORGOT,
                        ReviewQuality.HARD,
                        -> addWeakAyah(surahNumber, ayahNumber)
                        ReviewQuality.EASY -> removeWeakAyah(surahNumber, ayahNumber)
                        ReviewQuality.GOOD -> Unit
                    }
                }
            }
        }
    }

    private suspend fun syncSurahStatusInternal(surahNumber: Int, totalAyahs: Int) {
        val ayahTodos = ayahTodoGetBySurahOnceUseCase(surahNumber)
        if (ayahTodos.isEmpty()) {
            todoDeleteSurahByNumberUseCase(surahNumber)
            return
        }

        val allLearned = ayahTodos.size == totalAyahs &&
            ayahTodos.all { it.status == AyahTodoStatus.LEARNED }

        val status = if (allLearned) {
            SurahTodoStatus.LEARNED
        } else {
            SurahTodoStatus.LEARNING
        }

        todoUpsertSurahUseCase(SurahTodoEntity(surahNumber, status))
    }

    private suspend fun ReviewStateStore.ExclusiveAccess.scheduleReview(
        ayahNumber: Int,
        surahNumber: Int,
    ) {
        val now = getTimeMillis()
        val state = get(ayahNumber) ?: Sm2Scheduler.initialState().also {
            put(ayahNumber, it)
        }
        val nextAt = now + state.intervalDays.toLong() * dayMillis
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

    private companion object {
        const val dayMillis = 24L * 60 * 60 * 1000
    }
}
