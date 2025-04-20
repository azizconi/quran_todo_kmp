package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.domain.repository.QuranTodoRepository

class TodoDeleteSurahUseCase(private val repository: QuranTodoRepository) {
    suspend operator fun invoke(entity: SurahTodoEntity) = repository.delete(entity)
}