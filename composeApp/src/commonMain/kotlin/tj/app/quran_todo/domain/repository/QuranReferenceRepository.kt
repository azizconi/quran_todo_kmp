package tj.app.quran_todo.domain.repository

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.domain.model.ChapterNameModel

interface QuranReferenceRepository {
    suspend fun getChapterNames(language: AppLanguage): Map<Int, ChapterNameModel>

    suspend fun getAyahTranslations(
        surahNumber: Int,
        language: AppLanguage,
        expectedCount: Int
    ): Map<Int, String>
}
