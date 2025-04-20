package tj.app.quran_todo.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.common.utils.asState
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase

class HomeViewModel(
    private val todoDeleteSurahUseCase: TodoDeleteSurahUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    private val getCompleteQuranUseCase: GetCompleteQuranUseCase
): ViewModel() {

    val surahList = todoGetSurahListUseCase()

    private val _completeQuranResult = mutableStateOf<Resource<List<SurahWithAyahs>>>(Resource.Loading())
    val completeQuranResult = _completeQuranResult.asState()

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


    fun getCompleteQuran(withLocalAction: Boolean = true) {
        viewModelScope.launch {
            getCompleteQuranUseCase.invoke(withLocalAction)
                .collect {
                    _completeQuranResult.value = it
                }
        }
    }
}