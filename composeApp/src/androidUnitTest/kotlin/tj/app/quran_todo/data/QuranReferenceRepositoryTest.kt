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
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity
import tj.app.quran_todo.data.remote.createHttpClient
import tj.app.quran_todo.data.repository.QuranReferenceRepositoryImpl
import tj.app.quran_todo.data.translation.FailureThrottle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuranReferenceRepositoryTest {
    private var now = 5_000_000L
    private val requests = mutableListOf<String>()
    private var respondWith: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
        error("unexpected request ${request.url}")
    }

    private val translationDao = FakeAyahTranslationDao()
    private val chapterNameDao = FakeChapterNameDao()
    private val chapterNameCacheDao = FakeChapterNameCacheDao()

    private val client: HttpClient = createHttpClient(
        MockEngine { request ->
            requests += request.url.toString()
            respondWith(request)
        },
    )

    private val repository = QuranReferenceRepositoryImpl(
        client = client,
        chapterNameDao = chapterNameDao,
        chapterNameCacheDao = chapterNameCacheDao,
        ayahTranslationDao = translationDao,
        bundledChapterNames = BundledChapterNames(readBytes = { path ->
            BundledFiles.file(path.removePrefix("files/quran/")).readBytes()
        }),
        failureThrottle = FailureThrottle(windowMs = 30_000, clock = { now }),
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"))

    /** What api.alquran.cloud answers for `/surah/112/{edition}`. */
    private fun alquranSurah112(prefix: String): String {
        val ayahs = (1..4).joinToString(",") { i ->
            """{"number":${6221 + i},"text":"$prefix $i","numberInSurah":$i,"juz":30,"manzil":7,"page":604,"ruku":554,"hizbQuarter":240,"sajda":false}"""
        }
        return """{"code":200,"status":"OK","data":{"number":112,"name":"x","englishName":"Al-Ikhlaas","numberOfAyahs":4,"ayahs":[$ayahs],"edition":{"identifier":"en.sahih"}}}"""
    }

    /** What quran.com answers for a chapter: the verses in order, with footnote markers. */
    private fun quranComVerses(count: Int, text: (Int) -> String = { "Oyat $it<sup foot_note=\"17$it\">1</sup>" }): String {
        val verses = (1..count).joinToString(",") { """{"resource_id":55,"text":"${text(it).replace("\"", "\\\"")}"}""" }
        return """{"translations":[$verses],"meta":{"translation_name":"Muhammad Sodiq Muhammad Yusuf (Latin) "}}"""
    }

    // -- One surah at a time ---------------------------------------------------------------------------------------

    @Test
    fun aSurahIsDownloadedAloneNotTheWholeQuran() = runBlocking {
        respondWith = { json(alquranSurah112("Say")) }

        val result = repository.getAyahTranslations(112, AppLanguage.EN, expectedCount = 4)

        assertEquals(listOf("https://api.alquran.cloud/v1/surah/112/en.sahih"), requests)
        assertEquals(mapOf(6222 to "Say 1", 6223 to "Say 2", 6224 to "Say 3", 6225 to "Say 4"), result)
        assertEquals(4, translationDao.rows.size)
        assertTrue(translationDao.rows.all { it.surahNumber == 112 && it.languageCode == "en" })
    }

    @Test
    fun everyLanguageUsesItsOwnEditionOfTheSurahEndpoint() = runBlocking {
        respondWith = { json(alquranSurah112("x")) }
        repository.getAyahTranslations(112, AppLanguage.RU, 4)
        repository.getAyahTranslations(112, AppLanguage.TG, 4)
        repository.getAyahTranslations(112, AppLanguage.TR, 4)
        assertEquals(
            listOf(
                "https://api.alquran.cloud/v1/surah/112/ru.kuliev",
                "https://api.alquran.cloud/v1/surah/112/tg.ayati",
                "https://api.alquran.cloud/v1/surah/112/tr.yazir",
            ),
            requests,
        )
    }

    @Test
    fun aCachedSurahIsNotRequestedAgain() = runBlocking {
        respondWith = { json(alquranSurah112("Say")) }
        repository.getAyahTranslations(112, AppLanguage.EN, 4)
        val again = repository.getAyahTranslations(112, AppLanguage.EN, 4)

        assertEquals(1, requests.size)
        assertEquals(4, again.size)
    }

    // -- A failure is remembered -------------------------------------------------------------------------------------

    @Test
    fun theSameSurahIsNotRequestedMoreOftenThanEveryThirtySecondsAfterAFailure() = runBlocking {
        respondWith = { json("{}", HttpStatusCode.InternalServerError) }

        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.EN, 4))
        assertEquals(1, requests.size)

        now += 10_000
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.EN, 4))
        now += 19_999
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.EN, 4))
        assertEquals(1, requests.size, "no new request within 30 s of the failure")

        now += 1
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.EN, 4))
        assertEquals(2, requests.size, "30 s later the surah is requested again")
    }

    @Test
    fun anExplicitRetryGoesThroughTheMemoryOfFailures() = runBlocking {
        respondWith = { json("{}", HttpStatusCode.ServiceUnavailable) }
        repository.getAyahTranslations(112, AppLanguage.EN, 4)
        assertEquals(1, requests.size)

        respondWith = { json(alquranSurah112("Say")) }
        val retried = repository.getAyahTranslations(112, AppLanguage.EN, 4, force = true)

        assertEquals(2, requests.size)
        assertEquals(4, retried.size)
        // and the failure is forgotten: the next ordinary call is served from the cache
        assertEquals(4, repository.getAyahTranslations(112, AppLanguage.EN, 4).size)
        assertEquals(2, requests.size)
    }

    @Test
    fun aFailureOfOneSurahOrLanguageDoesNotBlockTheOthers() = runBlocking {
        respondWith = { json("{}", HttpStatusCode.InternalServerError) }
        repository.getAyahTranslations(112, AppLanguage.EN, 4)

        respondWith = { json(alquranSurah112("Say")) }
        assertEquals(4, repository.getAyahTranslations(112, AppLanguage.RU, 4).size)
        assertEquals(2, requests.size)
    }

    @Test
    fun anAnswerWithoutAyahsCountsAsAFailure() = runBlocking {
        respondWith = { json("""{"code":200,"status":"OK","data":{"ayahs":[]}}""") }
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.EN, 4))
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.EN, 4))
        assertEquals(1, requests.size)
        assertTrue(translationDao.rows.isEmpty())
    }

    @Test
    fun aBrokenAnswerIsNotCachedAsATranslation() = runBlocking {
        respondWith = { json("this is not json") }
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.EN, 4))
        assertEquals(0, translationDao.insertCalls)
    }

    // -- Uzbek: the Latin translation from quran.com -------------------------------------------------------------------

    @Test
    fun uzbekComesFromQuranComInLatinWithoutFootnotes() = runBlocking {
        respondWith = { json(quranComVerses(4)) }

        val result = repository.getAyahTranslations(112, AppLanguage.UZ, expectedCount = 4)

        assertEquals(
            listOf("https://api.quran.com/api/v4/quran/translations/55?chapter_number=112"),
            requests,
        )
        // quran.com has no ayah numbers: the verses are numbered from the first global number of the surah
        assertEquals(
            mapOf(6222 to "Oyat 1", 6223 to "Oyat 2", 6224 to "Oyat 3", 6225 to "Oyat 4"),
            result,
        )
        assertTrue(translationDao.rows.none { '<' in it.text || "foot_note" in it.text })
        assertTrue(translationDao.rows.all { it.languageCode == "uz" && it.surahNumber == 112 })
    }

    @Test
    fun theFirstSurahIsNumberedFromOne() = runBlocking {
        respondWith = { json(quranComVerses(7)) }
        val result = repository.getAyahTranslations(1, AppLanguage.UZ, expectedCount = 7)
        assertEquals((1..7).toList(), result.keys.sorted())
    }

    @Test
    fun aChapterWithTheWrongNumberOfVersesIsRejectedNotShifted() = runBlocking {
        respondWith = { json(quranComVerses(3)) }
        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.UZ, expectedCount = 4))
        assertTrue(translationDao.rows.isEmpty())
    }

    @Test
    fun anOldCyrillicUzbekCacheIsReplacedByTheLatinTranslation() = runBlocking {
        // What the old app stored: uz.sodik of alquran.cloud, Cyrillic.
        translationDao.rows += (1..4).map { i ->
            AyahTranslationEntity(6221 + i, 112, "uz", "Айт: «У Аллоҳ ягонадир». $i")
        }
        respondWith = { json(quranComVerses(4)) }

        val result = repository.getAyahTranslations(112, AppLanguage.UZ, expectedCount = 4)

        assertEquals(1, requests.size)
        assertEquals("Oyat 1", result[6222])
        assertTrue(translationDao.rows.none { it.text.any { c -> c.code in 0x0400..0x04FF } }, "no Cyrillic is left in the cache")
        assertEquals(4, translationDao.rows.size)
    }

    @Test
    fun theCyrillicCacheIsNotShownWhenTheLatinTranslationCannotBeLoaded() = runBlocking {
        translationDao.rows += (1..4).map { i -> AyahTranslationEntity(6221 + i, 112, "uz", "Айт $i") }
        respondWith = { json("{}", HttpStatusCode.NotFound) }

        assertEquals(emptyMap(), repository.getAyahTranslations(112, AppLanguage.UZ, expectedCount = 4))
    }

    @Test
    fun aLatinUzbekCacheIsServedWithoutTheNetwork() = runBlocking {
        translationDao.rows += (1..4).map { i -> AyahTranslationEntity(6221 + i, 112, "uz", "Oyat $i") }

        val result = repository.getAyahTranslations(112, AppLanguage.UZ, expectedCount = 4)

        assertEquals(4, result.size)
        assertTrue(requests.isEmpty())
    }

    // -- Surah names -------------------------------------------------------------------------------------------------

    @Test
    fun tajikAndUzbekNamesAreBundledAndNeedNoNetwork() = runBlocking {
        val tg = repository.getChapterNames(AppLanguage.TG)
        val uz = repository.getChapterNames(AppLanguage.UZ)

        assertTrue(requests.isEmpty(), "no request: $requests")
        assertEquals(114, tg.size)
        assertEquals(114, uz.size)
        assertEquals("Фотиҳа", tg.getValue(1).transliteration)
        assertEquals("Кушоянда", tg.getValue(1).translated)
        assertEquals("Fotiha", uz.getValue(1).transliteration)
        assertEquals("Ochuvchi", uz.getValue(1).translated)
        assertEquals("الفاتحة", uz.getValue(1).arabic)
        assertTrue(chapterNameDao.rows.isEmpty(), "bundled names do not go through the database cache")
    }

    @Test
    fun otherLanguagesStillUseQuranCom() = runBlocking {
        respondWith = {
            json(
                """{"chapters":[{"id":1,"name_simple":"Al-Fatihah","name_arabic":"الفاتحة","translated_name":{"name":"Открывающая Коран"}}]}""",
            )
        }
        val names = repository.getChapterNames(AppLanguage.RU)

        assertEquals(listOf("https://api.quran.com/api/v4/chapters?language=ru"), requests)
        assertEquals("Открывающая Коран", names.getValue(1).translated)
        // the second call is served from the cache (fresh for 30 days)
        repository.getChapterNames(AppLanguage.RU)
        assertEquals(1, requests.size)
    }
}
