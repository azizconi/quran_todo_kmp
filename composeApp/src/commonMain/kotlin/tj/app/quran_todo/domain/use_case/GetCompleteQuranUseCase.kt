package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.domain.repository.QuranRemoteRepository

class GetCompleteQuranUseCase(private val quranRemoteRepository: QuranRemoteRepository) {
    operator fun invoke(withLocalAction: Boolean) = quranRemoteRepository.getCompleteQuran(withLocalAction)
}