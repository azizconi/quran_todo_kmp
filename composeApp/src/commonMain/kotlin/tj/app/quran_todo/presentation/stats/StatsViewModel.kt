package tj.app.quran_todo.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.domain.use_case.AyahTodoGetAllUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase

data class StatsUiState(
    val totalSurahs: Int = 0,
    val totalAyahs: Int = 0,
    val learnedSurahs: Int = 0,
    val learningSurahs: Int = 0,
    val learnedAyahs: Int = 0,
    val learningAyahs: Int = 0,
) {
    val idleSurahs: Int get() = (totalSurahs - learnedSurahs - learningSurahs).coerceAtLeast(0)
    val idleAyahs: Int get() = (totalAyahs - learnedAyahs - learningAyahs).coerceAtLeast(0)
}

class StatsViewModel(
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    ayahTodoGetAllUseCase: AyahTodoGetAllUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val totals = MutableStateFlow(StatsUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val surahs = parseSurahList()
            totals.value = totals.value.copy(
                totalSurahs = surahs.size,
                totalAyahs = surahs.sumOf { it.ayats }
            )
        }

        viewModelScope.launch {
            combine(
                totals,
                todoGetSurahListUseCase(),
                ayahTodoGetAllUseCase()
            ) { totalsState, surahTodos, ayahTodos ->
                val learnedSurahs = surahTodos.count { it.status == SurahTodoStatus.LEARNED }
                val learningSurahs = surahTodos.count { it.status == SurahTodoStatus.LEARNING }
                val learnedAyahs = ayahTodos.count { it.status == AyahTodoStatus.LEARNED }
                val learningAyahs = ayahTodos.count { it.status == AyahTodoStatus.LEARNING }

                totalsState.copy(
                    learnedSurahs = learnedSurahs,
                    learningSurahs = learningSurahs,
                    learnedAyahs = learnedAyahs,
                    learningAyahs = learningAyahs
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
