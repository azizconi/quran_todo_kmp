package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.domain.repository.QuranTodoRepository

class TodoGetSurahListUseCase(private val repository: QuranTodoRepository) {
    operator fun invoke() = repository.getAll()
}