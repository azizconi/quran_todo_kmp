package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.domain.repository.AyahTodoRepository

class AyahTodoGetBySurahUseCase(private val repository: AyahTodoRepository) {
    operator fun invoke(surahNumber: Int) = repository.getBySurahNumber(surahNumber)
}
