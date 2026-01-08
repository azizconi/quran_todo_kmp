package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.domain.repository.AyahTodoRepository

class AyahTodoDeleteByAyahUseCase(private val repository: AyahTodoRepository) {
    suspend operator fun invoke(ayahNumber: Int) = repository.deleteByAyahNumber(ayahNumber)
}
