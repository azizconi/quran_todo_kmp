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
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.dao.AyahReviewDao
import tj.app.quran_todo.data.database.dao.FocusSessionDao
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
import tj.app.quran_todo.domain.use_case.GetChapterNamesUseCase
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetAllUseCase
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.domain.model.ChapterNameModel

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
    val filter: SurahTodoStatus? = null,
    val isLoadingQuran: Boolean = false,
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val ayahReviewDao: AyahReviewDao,
    private val focusSessionDao: FocusSessionDao,
    private val todoDeleteSurahUseCase: TodoDeleteSurahUseCase,
    private val todoDeleteSurahByNumberUseCase: TodoDeleteSurahByNumberUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    private val getCompleteQuranUseCase: GetCompleteQuranUseCase,
    private val ayahTodoUpsertUseCase: AyahTodoUpsertUseCase,
    private val ayahTodoDeleteBySurahUseCase: AyahTodoDeleteBySurahUseCase,
    private val getChapterNamesUseCase: GetChapterNamesUseCase,
    ayahTodoGetAllUseCase: AyahTodoGetAllUseCase,
): ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var completeQuranJob: Job? = null
    private var lastChapterLanguage: AppLanguage? = null

    init {
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

    fun completeReview(ayahNumber: Int, surahNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = getTimeMillis()
            val review = _uiState.value.dueReviews.firstOrNull { it.ayahNumber == ayahNumber }
            val nextIndex = ((review?.intervalIndex ?: 0) + 1)
                .coerceAtMost(reviewIntervalsDays.lastIndex)
            val nextAt = now + reviewIntervalsDays[nextIndex] * dayMillis
            ayahReviewDao.upsert(
                AyahReviewEntity(
                    ayahNumber = ayahNumber,
                    surahNumber = surahNumber,
                    nextReviewAt = nextAt,
                    intervalIndex = nextIndex,
                    lastReviewedAt = now
                )
            )
            refreshDueReviews()
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
        val intervalIndex = 0
        val nextAt = now + reviewIntervalsDays[intervalIndex] * dayMillis
        if (status == SurahTodoStatus.LEARNING || status == SurahTodoStatus.LEARNED) {
            ayahReviewDao.upsert(
                AyahReviewEntity(
                    ayahNumber = ayahNumber,
                    surahNumber = surahNumber,
                    nextReviewAt = nextAt,
                    intervalIndex = intervalIndex,
                    lastReviewedAt = now
                )
            )
        }
    }

    private companion object {
        val reviewIntervalsDays = listOf(1L, 3L, 7L, 14L, 30L)
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
        _uiState.value = _uiState.value.copy(selectedSurah = null)
    }
}
