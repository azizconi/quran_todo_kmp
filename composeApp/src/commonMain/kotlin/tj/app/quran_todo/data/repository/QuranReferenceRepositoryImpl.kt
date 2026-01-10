package tj.app.quran_todo.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.util.date.getTimeMillis
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.common.i18n.editionForLanguage
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.data.database.dao.AyahTranslationDao
import tj.app.quran_todo.data.database.dao.ChapterNameCacheDao
import tj.app.quran_todo.data.database.dao.ChapterNameDao
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameEntity
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity
import tj.app.quran_todo.data.model.QuranComChaptersResponse
import tj.app.quran_todo.data.model.QuranCompleteResponse
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.repository.QuranReferenceRepository

class QuranReferenceRepositoryImpl(
    private val client: HttpClient,
    private val chapterNameDao: ChapterNameDao,
    private val chapterNameCacheDao: ChapterNameCacheDao,
    private val ayahTranslationDao: AyahTranslationDao,
) : QuranReferenceRepository {
    override suspend fun getChapterNames(language: AppLanguage): Map<Int, ChapterNameModel> {
        val cached = chapterNameDao.getByLanguage(language.code)
        val cachedMap = cached.associate { entity ->
            entity.surahNumber to ChapterNameModel(
                arabic = entity.arabic,
                transliteration = entity.transliteration,
                translated = entity.translated
            )
        }
        val meta = chapterNameCacheDao.get(language.code)
        val isFresh = meta != null && (getTimeMillis() - meta.lastUpdated) < cacheTtlMs
        if (cachedMap.isNotEmpty() && isFresh) {
            return cachedMap
        }

        return try {
            val response: QuranComChaptersResponse = client
                .get("https://api.quran.com/api/v4/chapters?language=${language.code}")
                .body()
            val map = response.chapters.associate { chapter ->
                chapter.id to ChapterNameModel(
                    arabic = chapter.nameArabic,
                    transliteration = chapter.nameSimple,
                    translated = chapter.translatedName.name
                )
            }
            chapterNameDao.insertAll(
                response.chapters.map { chapter ->
                    ChapterNameEntity(
                        languageCode = language.code,
                        surahNumber = chapter.id,
                        arabic = chapter.nameArabic,
                        transliteration = chapter.nameSimple,
                        translated = chapter.translatedName.name
                    )
                }
            )
            chapterNameCacheDao.upsert(
                ChapterNameCacheEntity(
                    languageCode = language.code,
                    lastUpdated = getTimeMillis()
                )
            )
            map
        } catch (_: Exception) {
            cachedMap
        }
    }

    override suspend fun getAyahTranslations(
        surahNumber: Int,
        language: AppLanguage,
        expectedCount: Int
    ): Map<Int, String> {
        val cached = ayahTranslationDao.getBySurahAndLanguage(surahNumber, language.code)
        if (cached.isNotEmpty() &&
            (cached.size == expectedCount || expectedCount <= 0)
        ) {
            return cached.associate { it.ayahNumber to it.text }
        }

        val cachedLanguageCount = ayahTranslationDao.countByLanguage(language.code)
        if (cachedLanguageCount > 0) {
            val fromDb = ayahTranslationDao.getBySurahAndLanguage(surahNumber, language.code)
            return fromDb.associate { it.ayahNumber to it.text }
        }

        return try {
            val edition = editionForLanguage(language)
            val response: QuranCompleteResponse = client
                .get(Constants.QURAN_API_BASE_URL + "quran/$edition")
                .body()
            val translationsBySurah = response.data.surahs.associate { surah ->
                surah.number to surah.ayahs.map { ayah ->
                    AyahTranslationEntity(
                        ayahNumber = ayah.number,
                        surahNumber = surah.number,
                        languageCode = language.code,
                        text = ayah.text
                    )
                }
            }
            translationsBySurah.values.forEach { ayahTranslationDao.insertAll(it) }
            val currentTranslations = translationsBySurah[surahNumber].orEmpty()
            currentTranslations.associate { it.ayahNumber to it.text }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private companion object {
        const val cacheTtlMs = 1000L * 60 * 60 * 24 * 30
    }
}
