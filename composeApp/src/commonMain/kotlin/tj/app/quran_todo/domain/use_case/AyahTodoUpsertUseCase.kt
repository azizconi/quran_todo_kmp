package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.domain.repository.AyahTodoRepository

class AyahTodoUpsertUseCase(private val repository: AyahTodoRepository) {
    suspend operator fun invoke(entity: AyahTodoEntity) = repository.upsert(entity)
}
