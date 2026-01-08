package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.domain.repository.AyahTodoRepository

class AyahTodoDeleteBySurahUseCase(private val repository: AyahTodoRepository) {
    suspend operator fun invoke(surahNumber: Int) = repository.deleteBySurahNumber(surahNumber)
}
