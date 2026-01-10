package tj.app.quran_todo.domain.use_case

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.domain.repository.QuranReferenceRepository

class GetAyahTranslationsUseCase(private val repository: QuranReferenceRepository) {
    suspend operator fun invoke(
        surahNumber: Int,
        language: AppLanguage,
        expectedCount: Int
    ) = repository.getAyahTranslations(surahNumber, language, expectedCount)
}
