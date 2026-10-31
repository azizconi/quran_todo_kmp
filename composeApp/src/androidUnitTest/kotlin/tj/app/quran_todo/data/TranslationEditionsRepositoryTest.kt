package tj.app.quran_todo.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.BundledChapterNames
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity
import tj.app.quran_todo.data.remote.createHttpClient
import tj.app.quran_todo.data.repository.QuranReferenceRepositoryImpl
import tj.app.quran_todo.data.translation.FailureThrottle
import tj.app.quran_todo.data.translation.TranslationCatalog
import tj.app.quran_todo.data.translation.TranslationChoiceStore
import tj.app.quran_todo.data.translation.TranslationVersions
import tj.app.quran_todo.domain.repository.WholeDownloadResult
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The choice of the edition, the cache keys of the editions, and the whole translation in one request (task F4a). */
class TranslationEditionsRepositoryTest {
    private val requests = mutableListOf<String>()
    private var respondWith: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { error("unexpected request ${it.url}") }

    private val dao = FakeAyahTranslationDao()
    private var stored: String? = null
    private val choice = TranslationChoiceStore(read = { stored }, write = { stored = it })
    private var versionsJson: String? = null
    private val versions = TranslationVersions(read = { versionsJson }, write = { versionsJson = it })

    private val client: HttpClient = createHttpClient(
        MockEngine { request ->
            requests += request.url.toString()
            respondWith(request)
        },
    )

    private val repository = QuranReferenceRepositoryImpl(
        client = client,
        chapterNameDao = FakeChapterNameDao(),
        chapterNameCacheDao = FakeChapterNameCacheDao(),
        ayahTranslationDao = dao,
        bundledChapterNames = BundledChapterNames(readBytes = { path -> BundledFiles.file(path.removePrefix("files/quran/")).readBytes() }),
        failureThrottle = FailureThrottle(windowMs = 30_000, clock = { 0L }),
        selection = choice,
        versions = versions,
    )

    private fun edition(key: String) = TranslationCatalog.byKey(key)!!

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    private fun alquranSurah112(prefix: String): String {
        val ayahs = (1..4).joinToString(",") { i -> """{"number":${6221 + i},"text":"$prefix $i","numberInSurah":$i}""" }
        return """{"code":200,"status":"OK","data":{"number":112,"ayahs":[$ayahs]}}"""
    }

    private fun quranComChapter(prefix: String): String {
        val verses = (1..4).joinToString(",") { """{"resource_id":139,"text":"$prefix $it"}""" }
        return """{"translations":[$verses]}"""
    }

    private fun alquranWhole(prefix: String): String {
        val surahs = (1..114).joinToString(",") { surah ->
            val ayahs = (1..QuranFacts.ayahCount(surah)).joinToString(",") { n ->
                """{"number":${QuranFacts.firstAyahNumber(surah) + n - 1},"text":"$prefix $surah:$n","numberInSurah":$n}"""
            }
            """{"number":$surah,"ayahs":[$ayahs]}"""
        }
        return """{"code":200,"status":"OK","data":{"surahs":[$surahs]}}"""
    }

    private fun quranComWhole(prefix: String, count: Int = QuranFacts.AYAH_COUNT): String {
        val verses = (1..count).joinToString(",") { """{"resource_id":139,"text":"$prefix $it<sup foot_note=1>1</sup>"}""" }
        return """{"translations":[$verses]}"""
    }

    // -- The cache key of an edition -------------------------------------------------------------------------------------

    @Test
    fun theDefaultEditionKeepsTheOldCacheKeySoTheOldCacheIsStillGood() = runBlocking {
        // What the app stored before the choice existed: Kuliev under "ru".
        dao.rows += (1..4).map { AyahTranslationEntity(6221 + it, 112, "ru", "Старый кэш $it") }

        val result = repository.getAyahTranslations(112, AppLanguage.RU, expectedCount = 4)

        assertEquals("Старый кэш 1", result[6222])
        assertTrue(requests.isEmpty(), "the old cache is served without the network")
    }

    @Test
    fun theDefaultEditionIsStoredUnderTheCodeOfTheLanguage() = runBlocking {
        respondWith = { json(alquranSurah112("Кулиев")) }
        repository.getAyahTranslations(112, AppLanguage.RU, 4)
        assertEquals(listOf("https://api.alquran.cloud/v1/surah/112/ru.kuliev"), requests)
        assertTrue(dao.rows.all { it.languageCode == "ru" })
    }

    @Test
    fun anotherEditionIsFetchedAndStoredUnderItsOwnKey() = runBlocking {
        choice.select(AppLanguage.RU, edition("ru.abuadel"))
        respondWith = { json(alquranSurah112("Абу Адель")) }

        val result = repository.getAyahTranslations(112, AppLanguage.RU, 4)

        assertEquals(listOf("https://api.alquran.cloud/v1/surah/112/ru.abuadel"), requests)
        assertEquals("Абу Адель 1", result[6222])
        assertTrue(dao.rows.all { it.languageCode == "ru.abuadel" })
    }

    @Test
    fun theCachesOfTwoEditionsOfOneLanguageAreNotMixedAndSwitchingBackNeedsNoNewDownload() = runBlocking {
        respondWith = { json(alquranSurah112("Кулиев")) }
        repository.getAyahTranslations(112, AppLanguage.RU, 4)

        choice.select(AppLanguage.RU, edition("ru.abuadel"))
        respondWith = { json(alquranSurah112("Абу Адель")) }
        val abuAdel = repository.getAyahTranslations(112, AppLanguage.RU, 4)
        assertEquals("Абу Адель 1", abuAdel[6222])
        assertEquals(2, requests.size)

        choice.select(AppLanguage.RU, edition("ru"))
        val kuliev = repository.getAyahTranslations(112, AppLanguage.RU, 4)
        assertEquals("Кулиев 1", kuliev[6222])
        assertEquals(2, requests.size, "back to Kuliev: the cache answers, nothing is downloaded again")

        choice.select(AppLanguage.RU, edition("ru.abuadel"))
        assertEquals("Абу Адель 1", repository.getAyahTranslations(112, AppLanguage.RU, 4)[6222])
        assertEquals(2, requests.size)
        assertEquals(setOf("ru", "ru.abuadel"), dao.rows.map { it.languageCode }.toSet())
        assertEquals(4, dao.rows.count { it.languageCode == "ru" })
    }

    @Test
    fun aQuranComEditionIsFetchedByChapterAndStoredUnderItsKey() = runBlocking {
        choice.select(AppLanguage.TG, edition("qc:139"))
        respondWith = { json(quranComChapter("Мироф")) }

        val result = repository.getAyahTranslations(112, AppLanguage.TG, 4)

        assertEquals(listOf("https://api.quran.com/api/v4/quran/translations/139?chapter_number=112"), requests)
        assertEquals(setOf(6222, 6223, 6224, 6225), result.keys)
        assertTrue(dao.rows.all { it.languageCode == "qc:139" })
    }

    @Test
    fun aTranslationInAnotherLanguageIsShownUnderTheInterfaceOfThisOne() = runBlocking {
        choice.select(AppLanguage.RU, edition("en"))
        respondWith = { json(alquranSurah112("Saheeh")) }

        val result = repository.getAyahTranslations(112, AppLanguage.RU, 4)

        assertEquals(listOf("https://api.alquran.cloud/v1/surah/112/en.sahih"), requests)
        assertEquals("Saheeh 1", result[6222])
        assertTrue(dao.rows.all { it.languageCode == "en" })
    }

    @Test
    fun aFailureOfOneEditionDoesNotBlockTheOther() = runBlocking {
        choice.select(AppLanguage.RU, edition("ru.abuadel"))
        respondWith = { json("{}", HttpStatusCode.InternalServerError) }
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.RU, 4))

        choice.select(AppLanguage.RU, edition("ru"))
        respondWith = { json(alquranSurah112("Кулиев")) }
        assertEquals(4, repository.getAyahTranslations(112, AppLanguage.RU, 4).size)
    }

    // -- The whole translation in one request ----------------------------------------------------------------------------

    @Test
    fun aWholeAlquranCloudEditionIsOneRequestAndIsStored() = runBlocking {
        respondWith = { json(alquranWhole("AA")) }

        val result = repository.downloadWholeTranslation(edition("ru.abuadel"))

        assertEquals(WholeDownloadResult.DONE, result)
        assertEquals(listOf("https://api.alquran.cloud/v1/quran/ru.abuadel"), requests)
        assertEquals(QuranFacts.AYAH_COUNT, dao.rows.size)
        assertTrue(dao.rows.all { it.languageCode == "ru.abuadel" })
        assertEquals(setOf("ru.abuadel"), repository.downloadedTranslations())
    }

    @Test
    fun aWholeQuranComEditionIsOneRequestWithoutAChapter() = runBlocking {
        respondWith = { json(quranComWhole("QC")) }

        assertEquals(WholeDownloadResult.DONE, repository.downloadWholeTranslation(edition("qc:139")))

        assertEquals(listOf("https://api.quran.com/api/v4/quran/translations/139"), requests)
        assertEquals(QuranFacts.AYAH_COUNT, dao.rows.size)
        assertTrue(dao.rows.none { "<" in it.text }, "footnotes are cleaned")
        assertEquals(setOf("qc:139"), repository.downloadedTranslations())
    }

    @Test
    fun aTranslationDownloadedWholeIsReadAndSearchedWithoutTheNetwork() = runBlocking {
        respondWith = { json(alquranWhole("AA")) }
        repository.downloadWholeTranslation(edition("ru.abuadel"))
        requests.clear()
        choice.select(AppLanguage.RU, edition("ru.abuadel"))

        // Any surah, from the cache.
        val fatiha = repository.getAyahTranslations(1, AppLanguage.RU, 7)
        assertEquals("AA 1:1", fatiha[1])
        assertEquals(286, repository.getAyahTranslations(2, AppLanguage.RU, 286).size)
        // The whole text for the search, in the order of the Quran.
        val stored = repository.getStoredTranslation(edition("ru.abuadel"))
        assertEquals(QuranFacts.AYAH_COUNT, stored.size)
        assertEquals((1..QuranFacts.AYAH_COUNT).toList(), stored.keys.toList())
        assertTrue(requests.isEmpty(), "nothing was requested: $requests")
    }

    @Test
    fun noAnswerFromTheNetworkIsNoNetworkAndStoresNothing() = runBlocking {
        respondWith = { throw IOException("no route to host") }

        assertEquals(WholeDownloadResult.NO_NETWORK, repository.downloadWholeTranslation(edition("ru.abuadel")))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun aServerErrorAShortAnswerAndABrokenAnswerFailWithoutStoringAnything() = runBlocking {
        respondWith = { json("{}", HttpStatusCode.ServiceUnavailable) }
        assertEquals(WholeDownloadResult.FAILED, repository.downloadWholeTranslation(edition("ru.abuadel")))

        respondWith = { json(quranComWhole("QC", count = 6000)) }
        assertEquals(WholeDownloadResult.FAILED, repository.downloadWholeTranslation(edition("qc:139")))

        respondWith = { json("this is not json") }
        assertEquals(WholeDownloadResult.FAILED, repository.downloadWholeTranslation(edition("ru.abuadel")))

        assertTrue(dao.rows.isEmpty())
        assertTrue(repository.downloadedTranslations().isEmpty())
    }

    @Test
    fun surahsDownloadedOneByOneAreNotAWholeTranslation() = runBlocking {
        respondWith = { json(alquranSurah112("Кулиев")) }
        repository.getAyahTranslations(112, AppLanguage.RU, 4)
        assertTrue(repository.downloadedTranslations().isEmpty())
    }

    @Test
    fun aWholeDownloadReplacesWhatWasStoredOfTheEdition() = runBlocking {
        dao.rows += AyahTranslationEntity(1, 1, "ru.abuadel", "old")
        respondWith = { json(alquranWhole("AA")) }
        repository.downloadWholeTranslation(edition("ru.abuadel"))
        assertEquals(QuranFacts.AYAH_COUNT, dao.rows.size)
        assertEquals("AA 1:1", dao.rows.first { it.ayahNumber == 1 }.text)
    }

    @Test
    fun deletingAnEditionRemovesOnlyItsRowsAndTellsTheScreens() = runBlocking {
        respondWith = { json(alquranWhole("AA")) }
        repository.downloadWholeTranslation(edition("ru.abuadel"))
        dao.rows += (1..4).map { AyahTranslationEntity(6221 + it, 112, "ru", "Кулиев $it") }
        val before = repository.translationChanges.value

        repository.deleteTranslation(edition("ru.abuadel"))

        assertTrue(dao.rows.none { it.languageCode == "ru.abuadel" })
        assertEquals(4, dao.rows.count { it.languageCode == "ru" }, "the other edition stays")
        assertTrue(repository.downloadedTranslations().isEmpty())
        assertEquals(before + 1, repository.translationChanges.value)
    }

    @Test
    fun theDownloadAnnouncesItself() = runBlocking {
        val before = repository.translationChanges.value
        respondWith = { json(alquranWhole("AA")) }
        repository.downloadWholeTranslation(edition("ru.abuadel"))
        assertEquals(before + 1, repository.translationChanges.value)
    }

    // -- QuranEnc: the third source ---------------------------------------------------------------------------------------

    private fun quranEncSurah(surah: Int, prefix: String): String {
        val rows = (1..QuranFacts.ayahCount(surah)).joinToString(",") {
            """{"id":"x","sura":"$surah","aya":"$it","arabic_text":"x","translation":"$prefix $surah:$it","footnotes":""}"""
        }
        return """{"result":[$rows]}"""
    }

    @Test
    fun aQuranEncEditionIsFetchedByTheSurahEndpointAndStoredUnderItsKey() = runBlocking {
        choice.select(AppLanguage.TG, edition("qe:tajik_arifi"))
        respondWith = { json(quranEncSurah(112, "QE")) }

        val result = repository.getAyahTranslations(112, AppLanguage.TG, 4)

        assertEquals(listOf("https://quranenc.com/api/v1/translation/sura/tajik_arifi/112"), requests)
        assertEquals("QE 112:1", result[6222])
        assertTrue(dao.rows.all { it.languageCode == "qe:tajik_arifi" })
    }

    @Test
    fun aWholeQuranEncEditionIsOneRequestPerSurahWrittenTogetherAndItsVersionIsKept() = runBlocking {
        respondWith = { request ->
            val surah = request.url.encodedPath.substringAfterLast('/').toInt()
            json(quranEncSurah(surah, "QE"))
        }

        assertEquals(WholeDownloadResult.DONE, repository.downloadWholeTranslation(edition("qe:turkish_shaban")))

        assertEquals(114, requests.size)
        assertEquals("https://quranenc.com/api/v1/translation/sura/turkish_shaban/1", requests.first())
        assertEquals(QuranFacts.AYAH_COUNT, dao.rows.size)
        assertEquals(setOf("qe:turkish_shaban"), repository.downloadedTranslations())
        assertEquals("1.1.0", versions.installed("qe:turkish_shaban"))
        assertTrue(!versions.updateAvailable(edition("qe:turkish_shaban")))
    }

    @Test
    fun aQuranEncDownloadThatBreaksHalfWayStoresNothing() = runBlocking {
        respondWith = { request ->
            val surah = request.url.encodedPath.substringAfterLast('/').toInt()
            if (surah == 50) json("{}", HttpStatusCode.InternalServerError) else json(quranEncSurah(surah, "QE"))
        }
        assertEquals(WholeDownloadResult.FAILED, repository.downloadWholeTranslation(edition("qe:turkish_shaban")))
        assertTrue(dao.rows.isEmpty())

        respondWith = { throw IOException("offline") }
        assertEquals(WholeDownloadResult.NO_NETWORK, repository.downloadWholeTranslation(edition("qe:turkish_shaban")))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun anOlderStoredVersionIsFlaggedAndDeletingForgetsIt() = runBlocking {
        versionsJson = """{"qe:turkish_shaban":"1.0.0"}"""
        assertTrue(versions.updateAvailable(edition("qe:turkish_shaban")))
        // An edition stored before versions were kept is not flagged.
        assertTrue(!versions.updateAvailable(edition("qe:tajik_arifi")))
        repository.deleteTranslation(edition("qe:turkish_shaban"))
        assertEquals(null, versions.installed("qe:turkish_shaban"))
    }
}
