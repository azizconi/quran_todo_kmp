package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.domain.repository.QuranTodoRepository

class TodoDeleteSurahByNumberUseCase(private val repository: QuranTodoRepository) {
    suspend operator fun invoke(surahNumber: Int) = repository.deleteBySurahNumber(surahNumber)
}
