package tj.app.quran_todo.presentation.surah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteByAyahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahOnceUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoUpsertUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus

class SurahViewModel(
    private val ayahTodoGetBySurahUseCase: AyahTodoGetBySurahUseCase,
    private val ayahTodoUpsertUseCase: AyahTodoUpsertUseCase,
    private val ayahTodoDeleteByAyahUseCase: AyahTodoDeleteByAyahUseCase,
    private val ayahTodoGetBySurahOnceUseCase: AyahTodoGetBySurahOnceUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    private val todoDeleteSurahByNumberUseCase: TodoDeleteSurahByNumberUseCase,
) : ViewModel() {

    fun ayahTodos(surahNumber: Int): Flow<List<AyahTodoEntity>> =
        ayahTodoGetBySurahUseCase(surahNumber)

    fun updateAyahStatus(
        ayahNumber: Int,
        surahNumber: Int,
        totalAyahs: Int,
        status: AyahTodoStatus,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ayahTodoUpsertUseCase(
                AyahTodoEntity(
                    ayahNumber = ayahNumber,
                    surahNumber = surahNumber,
                    status = status
                )
            )
            syncSurahStatusInternal(surahNumber, totalAyahs)
        }
    }

    fun clearAyahStatus(ayahNumber: Int, surahNumber: Int, totalAyahs: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            ayahTodoDeleteByAyahUseCase(ayahNumber)
            syncSurahStatusInternal(surahNumber, totalAyahs)
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
}
