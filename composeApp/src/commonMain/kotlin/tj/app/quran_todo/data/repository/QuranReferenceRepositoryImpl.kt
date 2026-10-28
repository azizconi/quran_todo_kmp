package tj.app.quran_todo.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CancellationException
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.common.i18n.editionForLanguage
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.data.bundled.BundledChapterNames
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.dao.AyahTranslationDao
import tj.app.quran_todo.data.database.dao.ChapterNameCacheDao
import tj.app.quran_todo.data.database.dao.ChapterNameDao
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameEntity
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity
import tj.app.quran_todo.data.model.QuranComChaptersResponse
import tj.app.quran_todo.data.model.QuranComTranslationsResponse
import tj.app.quran_todo.data.model.SurahEditionResponse
import tj.app.quran_todo.data.remote.DataDebug
import tj.app.quran_todo.data.translation.FailureThrottle
import tj.app.quran_todo.data.translation.TranslationText
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.repository.QuranReferenceRepository

class QuranReferenceRepositoryImpl(
    private val client: HttpClient,
    private val chapterNameDao: ChapterNameDao,
    private val chapterNameCacheDao: ChapterNameCacheDao,
    private val ayahTranslationDao: AyahTranslationDao,
    private val bundledChapterNames: BundledChapterNames = BundledChapterNames(),
    private val failureThrottle: FailureThrottle = FailureThrottle(),
    private val debug: DataDebug = DataDebug.Off,
) : QuranReferenceRepository {
    override suspend fun getChapterNames(language: AppLanguage): Map<Int, ChapterNameModel> {
        if (bundledChapterNames.isBundled(language)) {
            // quran.com answers in English for these languages: the names ship with the app.
            try {
                return bundledChapterNames.get(language)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppTelemetry.logError(
                    throwable = error,
                    context = "bundled_chapter_names_failed",
                    params = mapOf("language" to language.name.lowercase())
                )
            }
        }

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
                .get(Constants.QURAN_COM_API_BASE_URL + "chapters?language=${language.code}")
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppTelemetry.logError(
                throwable = error,
                context = "chapter_names_load_failed",
                params = mapOf("language" to language.name.lowercase())
            )
            cachedMap
        }
    }

    /**
     * Translations of ONE surah by global ayah number. The cache (Room) is used when it holds the whole
     * surah; otherwise the surah is downloaded: `GET /v1/surah/{n}/{edition}` (alquran.cloud), and for Uzbek
     * the Latin translation from quran.com. A failed request is remembered: the same (surah, language) is not
     * requested again for 30 seconds unless [force] is true (the "Retry" button).
     */
    override suspend fun getAyahTranslations(
        surahNumber: Int,
        language: AppLanguage,
        expectedCount: Int,
        force: Boolean,
    ): Map<Int, String> {
        val cached = ayahTranslationDao.getBySurahAndLanguage(surahNumber, language.code)
        // Uzbek used to be downloaded as Cyrillic (uz.sodik of alquran.cloud) and the interface is Latin:
        // such a cache is replaced the next time the surah is opened.
        val legacyScript = language == AppLanguage.UZ && cached.any { TranslationText.containsCyrillic(it.text) }
        if (cached.isNotEmpty() &&
            !legacyScript &&
            (cached.size == expectedCount || expectedCount <= 0)
        ) {
            return cached.associate { it.ayahNumber to it.text }
        }

        val key = "$surahNumber:${language.code}"
        if (!force && failureThrottle.isBlocked(key)) {
            debug.log { "translation $key: not requested again yet, it failed less than 30 s ago" }
            return emptyMap()
        }

        return try {
            val translations = if (language == AppLanguage.UZ) {
                fetchUzbekLatin(surahNumber)
            } else {
                fetchSurahEdition(surahNumber, language)
            }
            if (translations.isEmpty()) throw IllegalStateException("empty translation of surah $surahNumber")
            ayahTranslationDao.insertAll(translations)
            failureThrottle.clear(key)
            translations.associate { it.ayahNumber to it.text }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failureThrottle.markFailed(key)
            AppTelemetry.logError(
                throwable = error,
                context = "ayah_translations_load_failed",
                params = mapOf(
                    "surah_number" to surahNumber.toString(),
                    "language" to language.name.lowercase()
                )
            )
            emptyMap()
        }
    }

    private suspend fun fetchSurahEdition(
        surahNumber: Int,
        language: AppLanguage,
    ): List<AyahTranslationEntity> {
        val edition = editionForLanguage(language)
        val httpResponse = client.get(Constants.QURAN_API_BASE_URL + "surah/$surahNumber/$edition")
        if (!httpResponse.status.isSuccess()) {
            throw IllegalStateException("surah $surahNumber/$edition: HTTP ${httpResponse.status.value}")
        }
        val response: SurahEditionResponse = httpResponse.body()
        return response.data.ayahs.map { ayah ->
            AyahTranslationEntity(
                ayahNumber = ayah.number,
                surahNumber = surahNumber,
                languageCode = language.code,
                text = ayah.text,
            )
        }
    }

    /**
     * The Latin Uzbek translation of Muhammad Sodiq Muhammad Yusuf from quran.com v4. The response is the
     * verses of the chapter in order without numbers, so they are mapped to global ayah numbers by position;
     * a chapter with an unexpected number of verses is rejected rather than shifted.
     */
    private suspend fun fetchUzbekLatin(surahNumber: Int): List<AyahTranslationEntity> {
        val url = Constants.QURAN_COM_API_BASE_URL +
            "quran/translations/${Constants.QURAN_COM_UZBEK_LATIN_TRANSLATION_ID}?chapter_number=$surahNumber"
        val httpResponse = client.get(url)
        if (!httpResponse.status.isSuccess()) {
            throw IllegalStateException("quran.com uz $surahNumber: HTTP ${httpResponse.status.value}")
        }
        val response: QuranComTranslationsResponse = httpResponse.body()
        val expected = QuranFacts.ayahCount(surahNumber)
        if (expected == 0 || response.translations.size != expected) {
            throw IllegalStateException(
                "quran.com uz $surahNumber: ${response.translations.size} verses, expected $expected"
            )
        }
        val first = QuranFacts.firstAyahNumber(surahNumber)
        return response.translations.mapIndexed { index, verse ->
            AyahTranslationEntity(
                ayahNumber = first + index,
                surahNumber = surahNumber,
                languageCode = AppLanguage.UZ.code,
                text = TranslationText.cleanQuranCom(verse.text),
            )
        }
    }

    private companion object {
        const val cacheTtlMs = 1000L * 60 * 60 * 24 * 30
    }
}
