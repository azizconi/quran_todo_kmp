package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.domain.repository.AyahTodoRepository

class AyahTodoGetAllUseCase(private val repository: AyahTodoRepository) {
    operator fun invoke() = repository.getAll()
}
