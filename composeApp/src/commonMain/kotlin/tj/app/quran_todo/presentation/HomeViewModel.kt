package tj.app.quran_todo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoUpsertUseCase
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase

data class HomeUiState(
    val surahList: List<SurahModel> = emptyList(),
    val todoSurahs: List<SurahTodoEntity> = emptyList(),
    val completeQuran: List<SurahWithAyahs> = emptyList(),
    val selectedSurah: SurahWithAyahs? = null,
    val selectedSurahNumbers: Set<Int> = emptySet(),
    val filter: SurahTodoStatus? = null,
    val isLoadingQuran: Boolean = false,
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val todoDeleteSurahUseCase: TodoDeleteSurahUseCase,
    private val todoDeleteSurahByNumberUseCase: TodoDeleteSurahByNumberUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    private val getCompleteQuranUseCase: GetCompleteQuranUseCase,
    private val ayahTodoUpsertUseCase: AyahTodoUpsertUseCase,
    private val ayahTodoDeleteBySurahUseCase: AyahTodoDeleteBySurahUseCase,
): ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var completeQuranJob: Job? = null

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

        refreshQuran(withLocalAction = true)
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
                        }
                    )
                )
            }
        }
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
