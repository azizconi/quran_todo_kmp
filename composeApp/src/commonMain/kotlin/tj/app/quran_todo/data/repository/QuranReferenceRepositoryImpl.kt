package tj.app.quran_todo.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
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
import tj.app.quran_todo.data.translation.QuranEditionResponse
import tj.app.quran_todo.data.translation.TranslationEdition
import tj.app.quran_todo.data.translation.TranslationOrigin
import tj.app.quran_todo.data.translation.TranslationSelection
import tj.app.quran_todo.data.translation.QuranEncResponse
import tj.app.quran_todo.data.translation.TranslationText
import tj.app.quran_todo.data.translation.TranslationVersions
import tj.app.quran_todo.data.translation.WholeTranslation
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.repository.QuranReferenceRepository
import tj.app.quran_todo.domain.repository.WholeDownloadResult

class QuranReferenceRepositoryImpl(
    private val client: HttpClient,
    private val chapterNameDao: ChapterNameDao,
    private val chapterNameCacheDao: ChapterNameCacheDao,
    private val ayahTranslationDao: AyahTranslationDao,
    private val bundledChapterNames: BundledChapterNames = BundledChapterNames(),
    private val failureThrottle: FailureThrottle = FailureThrottle(),
    private val debug: DataDebug = DataDebug.Off,
    /** The edition shown for a language of the interface: the choice of the person (the defaults in tests). */
    private val selection: TranslationSelection = TranslationSelection.Defaults,
    /** Where the version of a stored edition is kept (a recorder in tests). */
    private val versions: TranslationVersions = TranslationVersions({ null }, {}),
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
            cachedMap.ifEmpty { offlineChapterNames(language) }
        }
    }

    /** Nothing cached and quran.com unreachable (the first launch offline): the copy of its names in the app. */
    private suspend fun offlineChapterNames(language: AppLanguage): Map<Int, ChapterNameModel> = try {
        bundledChapterNames.get(language)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppTelemetry.logError(
            throwable = error,
            context = "bundled_chapter_names_failed",
            params = mapOf("language" to language.name.lowercase())
        )
        emptyMap()
    }

    private val _translationChanges = MutableStateFlow(0)
    override val translationChanges: StateFlow<Int> = _translationChanges.asStateFlow()

    /**
     * Translations of ONE surah by global ayah number, in the edition the person chose for [language] ([selection]). The cache
     * (Room, by the key of the edition) is used when it holds the whole surah; otherwise the surah is downloaded: `GET
     * /v1/surah/{n}/{edition}` (alquran.cloud) or the chapter from quran.com. A failed request is remembered: the same (surah,
     * edition) is not requested again for 30 seconds unless [force] is true (the "Retry" button).
     */
    override suspend fun getAyahTranslations(
        surahNumber: Int,
        language: AppLanguage,
        expectedCount: Int,
        force: Boolean,
    ): Map<Int, String> {
        val edition = selection.editionFor(language)
        val cached = ayahTranslationDao.getBySurahAndLanguage(surahNumber, edition.key)
        // Uzbek used to be downloaded as Cyrillic (uz.sodik of alquran.cloud) and the interface is Latin:
        // such a cache is replaced the next time the surah is opened.
        val legacyScript = edition.key == AppLanguage.UZ.code && cached.any { TranslationText.containsCyrillic(it.text) }
        if (cached.isNotEmpty() &&
            !legacyScript &&
            (cached.size == expectedCount || expectedCount <= 0)
        ) {
            return cached.associate { it.ayahNumber to it.text }
        }

        val key = "$surahNumber:${edition.key}"
        if (!force && failureThrottle.isBlocked(key)) {
            debug.log { "translation $key: not requested again yet, it failed less than 30 s ago" }
            return emptyMap()
        }

        return try {
            val translations = when (edition.origin) {
                TranslationOrigin.QURAN_COM -> fetchQuranComChapter(surahNumber, edition)
                TranslationOrigin.QURAN_ENC -> fetchQuranEncSurah(surahNumber, edition)
                TranslationOrigin.ALQURAN_CLOUD -> fetchSurahEdition(surahNumber, edition)
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
                    "language" to language.name.lowercase(),
                    "edition" to edition.key,
                )
            )
            emptyMap()
        }
    }

    private suspend fun fetchSurahEdition(
        surahNumber: Int,
        edition: TranslationEdition,
    ): List<AyahTranslationEntity> {
        val httpResponse = client.get(Constants.QURAN_API_BASE_URL + "surah/$surahNumber/${edition.remoteId}")
        if (!httpResponse.status.isSuccess()) {
            throw IllegalStateException("surah $surahNumber/${edition.remoteId}: HTTP ${httpResponse.status.value}")
        }
        val response: SurahEditionResponse = httpResponse.body()
        return response.data.ayahs.map { ayah ->
            AyahTranslationEntity(
                ayahNumber = ayah.number,
                surahNumber = surahNumber,
                languageCode = edition.key,
                text = ayah.text,
            )
        }
    }

    /**
     * One chapter of a translation of quran.com v4. The response is the verses of the chapter in order without numbers, so they
     * are mapped to global ayah numbers by position; a chapter with an unexpected number of verses is rejected rather than shifted.
     */
    private suspend fun fetchQuranComChapter(surahNumber: Int, edition: TranslationEdition): List<AyahTranslationEntity> {
        val url = Constants.QURAN_COM_API_BASE_URL + "quran/translations/${edition.remoteId}?chapter_number=$surahNumber"
        val httpResponse = client.get(url)
        if (!httpResponse.status.isSuccess()) {
            throw IllegalStateException("quran.com ${edition.remoteId} $surahNumber: HTTP ${httpResponse.status.value}")
        }
        val response: QuranComTranslationsResponse = httpResponse.body()
        return WholeTranslation.fromQuranComChapter(response.translations, surahNumber, edition.key)
    }

    /** One surah of QuranEnc: `GET /api/v1/translation/sura/{key}/{sura}`. The text is stored as it comes. */
    private suspend fun fetchQuranEncSurah(surahNumber: Int, edition: TranslationEdition): List<AyahTranslationEntity> {
        val httpResponse = client.get(Constants.QURAN_ENC_API_BASE_URL + "translation/sura/${edition.remoteId}/$surahNumber")
        if (!httpResponse.status.isSuccess()) {
            throw IllegalStateException("quranenc ${edition.remoteId} $surahNumber: HTTP ${httpResponse.status.value}")
        }
        val response: QuranEncResponse = httpResponse.body()
        return WholeTranslation.fromQuranEncSurah(response, surahNumber, edition.key)
    }

    // -- A whole translation (F4a) ---------------------------------------------------------------------------------------

    override suspend fun downloadedTranslations(): Set<String> =
        ayahTranslationDao.countsByEdition().filter { it.total >= QuranFacts.AYAH_COUNT }.mapTo(HashSet()) { it.editionKey }

    /**
     * The whole Quran of an edition in ONE request (`GET /v1/quran/{edition}` of alquran.cloud, `GET /quran/translations/{id}` of
     * quran.com: 0.3-0.9 MB on the wire), checked (114 surahs, 6236 ayahs) and written in one go. Nothing is written when the
     * answer is not whole, and what is written cannot be cancelled half way.
     */
    override suspend fun downloadWholeTranslation(edition: TranslationEdition): WholeDownloadResult {
        if (edition.origin == TranslationOrigin.QURAN_ENC) return downloadWholeQuranEnc(edition)
        val url = when (edition.origin) {
            TranslationOrigin.ALQURAN_CLOUD -> Constants.QURAN_API_BASE_URL + "quran/${edition.remoteId}"
            else -> Constants.QURAN_COM_API_BASE_URL + "quran/translations/${edition.remoteId}"
        }
        val rows = try {
            val httpResponse = try {
                // 1-3 MB of JSON on a slow connection: more than the 60 s of a request for one surah.
                client.get(url) {
                    timeout {
                        requestTimeoutMillis = WHOLE_REQUEST_TIMEOUT_MS
                        socketTimeoutMillis = WHOLE_REQUEST_TIMEOUT_MS
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "translation_download_network", params = mapOf("edition" to edition.key))
                return WholeDownloadResult.NO_NETWORK
            }
            if (!httpResponse.status.isSuccess()) throw IllegalStateException("${edition.key}: HTTP ${httpResponse.status.value}")
            when (edition.origin) {
                TranslationOrigin.ALQURAN_CLOUD -> {
                    val response: QuranEditionResponse = httpResponse.body()
                    WholeTranslation.fromAlquranCloud(response, edition.key)
                }
                else -> {
                    val response: QuranComTranslationsResponse = httpResponse.body()
                    WholeTranslation.fromQuranCom(response.translations, edition.key)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppTelemetry.logError(throwable = error, context = "translation_download_failed", params = mapOf("edition" to edition.key))
            return WholeDownloadResult.FAILED
        }
        return store(edition, rows)
    }

    /** QuranEnc has no request for the whole Quran: 114 requests (one per surah), written together at the end, so a stop leaves nothing half. */
    private suspend fun downloadWholeQuranEnc(edition: TranslationEdition): WholeDownloadResult {
        val rows = ArrayList<AyahTranslationEntity>(QuranFacts.AYAH_COUNT)
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            try {
                val httpResponse = try {
                    client.get(Constants.QURAN_ENC_API_BASE_URL + "translation/sura/${edition.remoteId}/$surah")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppTelemetry.logError(throwable = error, context = "translation_download_network", params = mapOf("edition" to edition.key))
                    return WholeDownloadResult.NO_NETWORK
                }
                if (!httpResponse.status.isSuccess()) throw IllegalStateException("${edition.key} $surah: HTTP ${httpResponse.status.value}")
                val response: QuranEncResponse = httpResponse.body()
                rows += WholeTranslation.fromQuranEncSurah(response, surah, edition.key)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "translation_download_failed", params = mapOf("edition" to edition.key))
                return WholeDownloadResult.FAILED
            }
        }
        return store(edition, rows)
    }

    private suspend fun store(edition: TranslationEdition, rows: List<AyahTranslationEntity>): WholeDownloadResult {
        withContext(NonCancellable) {
            rows.chunked(WRITE_CHUNK).forEach { ayahTranslationDao.insertAll(it) }
            versions.record(edition)
        }
        _translationChanges.value += 1
        return WholeDownloadResult.DONE
    }

    override suspend fun checkTranslationUpdates() {
        val now = getTimeMillis()
        if (!versions.checkIsDue(now)) return
        try {
            val httpResponse = client.get(Constants.QURAN_ENC_API_BASE_URL + "translations/list")
            if (!httpResponse.status.isSuccess()) return
            val found = TranslationVersions.parseList(httpResponse.body<String>())
            if (found.isEmpty()) return
            versions.recordRemote(found, now)
            _translationChanges.value += 1
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Quiet: the version of the catalog stays the fallback, the next opening asks again.
        }
    }

    override suspend fun deleteTranslation(edition: TranslationEdition) {
        ayahTranslationDao.deleteByLanguage(edition.key)
        versions.forget(edition.key)
        _translationChanges.value += 1
    }

    override suspend fun getStoredTranslation(edition: TranslationEdition): Map<Int, String> {
        val rows = ayahTranslationDao.getAllByLanguage(edition.key)
        val map = LinkedHashMap<Int, String>(rows.size)
        rows.sortedBy { it.ayahNumber }.forEach { map[it.ayahNumber] = it.text }
        return map
    }

    private companion object {
        const val cacheTtlMs = 1000L * 60 * 60 * 24 * 30
        const val WRITE_CHUNK = 800
        const val WHOLE_REQUEST_TIMEOUT_MS = 180_000L
    }
}
