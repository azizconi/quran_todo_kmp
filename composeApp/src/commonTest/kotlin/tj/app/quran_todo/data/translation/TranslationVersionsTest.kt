package tj.app.quran_todo.data.translation

import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationVersionsTest {
    private var json: String? = null
    private val versions = TranslationVersions({ json }, { json = it })
    private val shaban = TranslationCatalog.byKey("qe:turkish_shaban")!!

    @Test
    fun theVersionOfTheListWinsOverTheCatalogOne() {
        versions.record(shaban)
        assertEquals("1.1.0", versions.installed(shaban.key))
        assertEquals(false, versions.updateAvailable(shaban))

        versions.recordRemote(mapOf("qe:turkish_shaban" to "1.2.0"), now = 1_000)
        assertEquals("1.2.0", versions.latest(shaban))
        assertEquals(true, versions.updateAvailable(shaban))
        assertEquals("1.1.0", versions.installed(shaban.key))
    }

    @Test
    fun withoutACheckTheCatalogVersionIsTheFallback() {
        json = """{"qe:turkish_shaban":"1.0.0"}"""
        assertEquals("1.1.0", versions.latest(shaban))
        assertEquals(true, versions.updateAvailable(shaban))
    }

    @Test
    fun anEditionWithoutAStoredVersionOrWithoutASourceVersionIsNotFlagged() {
        assertEquals(false, versions.updateAvailable(shaban))
        val sodiq = TranslationCatalog.byKey("uz")!!
        versions.recordRemote(mapOf("qe:turkish_shaban" to "9.0.0"), now = 1)
        assertEquals(false, versions.updateAvailable(sodiq))
    }

    @Test
    fun theListIsAskedForAtMostOnceInSevenDays() {
        assertEquals(true, versions.checkIsDue(now = 5_000))
        versions.recordRemote(mapOf("qe:tajik_arifi" to "1.0.2"), now = 5_000)
        assertEquals(false, versions.checkIsDue(now = 5_000 + TranslationVersions.CHECK_INTERVAL_MS - 1))
        assertEquals(true, versions.checkIsDue(now = 5_000 + TranslationVersions.CHECK_INTERVAL_MS))
        // A clock that went back is not trusted either.
        assertEquals(true, versions.checkIsDue(now = 1_000))
    }

    @Test
    fun theListOfQuranEncIsParsedByEditionKey() {
        val body = """{"translations":[{"key":"tajik_arifi","version":"1.0.3","title":"x"},{"key":"turkish_shaban","version":"1.1.0"},{"key":"broken"}]}"""
        assertEquals(mapOf("qe:tajik_arifi" to "1.0.3", "qe:turkish_shaban" to "1.1.0"), TranslationVersions.parseList(body))
        assertEquals(emptyMap(), TranslationVersions.parseList("""{"other":1}"""))
    }

    @Test
    fun forgettingAnEditionKeepsTheRemoteVersions() {
        versions.record(shaban)
        versions.recordRemote(mapOf("qe:turkish_shaban" to "1.2.0"), now = 1)
        versions.forget(shaban.key)
        assertEquals(null, versions.installed(shaban.key))
        assertEquals("1.2.0", versions.latest(shaban))
    }
}
