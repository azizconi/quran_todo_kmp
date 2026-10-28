package tj.app.quran_todo.domain.repository

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.domain.model.ChapterNameModel

interface QuranReferenceRepository {
    suspend fun getChapterNames(language: AppLanguage): Map<Int, ChapterNameModel>

    /**
     * Translations of one surah by global ayah number; an empty map when they could not be loaded.
     * A surah (and language) that failed is not requested again for 30 seconds unless [force] is true
     * (an explicit "Retry").
     */
    suspend fun getAyahTranslations(
        surahNumber: Int,
        language: AppLanguage,
        expectedCount: Int,
        force: Boolean = false,
    ): Map<Int, String>
}
