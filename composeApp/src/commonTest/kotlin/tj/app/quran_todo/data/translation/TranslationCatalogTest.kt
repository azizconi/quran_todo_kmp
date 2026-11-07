package tj.app.quran_todo.data.translation

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationCatalogTest {
    @Test
    fun everyLanguageHasExactlyOneDefaultEditionWhoseKeyIsTheOldCacheKey() {
        AppLanguage.entries.forEach { language ->
            val defaults = TranslationCatalog.editionsOf(language).filter { it.isDefault }
            assertEquals(1, defaults.size, "$language has ${defaults.size} defaults")
            // The cache of everybody who has the app already is under the code of the language: it stays valid.
            assertEquals(language.code, defaults.single().key)
            assertEquals(defaults.single().key, TranslationCatalog.defaultFor(language).key)
        }
    }

    @Test
    fun theDefaultsAreTheEditionsTheAppHasAlwaysUsed() {
        val expected = mapOf(
            AppLanguage.EN to ("en.sahih" to TranslationOrigin.ALQURAN_CLOUD),
            AppLanguage.RU to ("ru.kuliev" to TranslationOrigin.ALQURAN_CLOUD),
            AppLanguage.TG to ("tg.ayati" to TranslationOrigin.ALQURAN_CLOUD),
            AppLanguage.TR to ("tr.yazir" to TranslationOrigin.ALQURAN_CLOUD),
            // Uzbek: the Latin translation of Muhammad Sodiq, quran.com resource 55.
            AppLanguage.UZ to ("55" to TranslationOrigin.QURAN_COM),
        )
        expected.forEach { (language, pair) ->
            val edition = TranslationCatalog.defaultFor(language)
            assertEquals(pair.first, edition.remoteId, "$language")
            assertEquals(pair.second, edition.origin, "$language")
        }
    }

    @Test
    fun keysAreUniqueAndFindable() {
        val keys = TranslationCatalog.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate keys: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}")
        TranslationCatalog.all.forEach { assertEquals(it.key, TranslationCatalog.byKey(it.key)?.key) }
        assertNull(TranslationCatalog.byKey("xx.nobody"))
        assertNull(TranslationCatalog.byKey(null))
    }

    @Test
    fun theSameRemoteEditionIsNotListedTwice() {
        val sources = TranslationCatalog.all.map { it.origin to it.remoteId }
        assertEquals(sources.size, sources.toSet().size)
    }

    @Test
    fun nonDefaultKeysNameTheirSource() {
        TranslationCatalog.all.filterNot { it.isDefault }.forEach { edition ->
            val expected = when (edition.origin) {
                TranslationOrigin.ALQURAN_CLOUD -> edition.remoteId
                TranslationOrigin.QURAN_COM -> "qc:${edition.remoteId}"
                TranslationOrigin.QURAN_ENC -> "qe:${edition.remoteId}"
            }
            assertEquals(expected, edition.key)
        }
    }

    @Test
    fun everyNameAndAuthorIsFilledInAllFiveLanguages() {
        TranslationCatalog.all.forEach { edition ->
            AppLanguage.entries.forEach { language ->
                assertTrue(edition.name.of(language).isNotBlank(), "${edition.key} name $language")
                assertTrue(edition.author.of(language).isNotBlank(), "${edition.key} author $language")
            }
        }
    }

    @Test
    fun theNamesFollowTheScriptOfTheLanguage() {
        TranslationCatalog.all.forEach { edition ->
            listOf(AppLanguage.RU, AppLanguage.TG).forEach { cyrillic ->
                assertTrue(edition.name.of(cyrillic).any { it.code in 0x0400..0x04FF }, "${edition.key} name in $cyrillic is not Cyrillic")
            }
            listOf(AppLanguage.EN, AppLanguage.UZ, AppLanguage.TR).forEach { latin ->
                assertTrue(edition.name.of(latin).none { it.code in 0x0400..0x04FF }, "${edition.key} name in $latin has Cyrillic")
            }
        }
    }

    @Test
    fun uzbekDefaultIsLatinAndTheCyrillicEditionsAreMarked() {
        val uzbek = TranslationCatalog.editionsOf(AppLanguage.UZ)
        assertEquals(TranslationScript.LATIN, uzbek.first().script)
        assertTrue(uzbek.first().isDefault)
        val cyrillic = uzbek.filter { it.script == TranslationScript.CYRILLIC }
        assertEquals(setOf("qe:uzbek_rwwad", "qe:uzbek_mansour"), cyrillic.map { it.key }.toSet())
        assertTrue(cyrillic.all { it.scriptDiffersFromInterface }, "the sheet says \"Cyrillic\" next to them")
        assertTrue(uzbek.first().scriptDiffersFromInterface.not())
        // quran.com 101 and 127 are Cyrillic too, and 101 is uzbek_mansour of QuranEnc: they are not offered a second time.
        assertNull(TranslationCatalog.byKey("qc:101"))
        assertNull(TranslationCatalog.byKey("qc:127"))
    }

    @Test
    fun russianAndTajikAreCyrillicTheRestIsLatinExceptTheMarkedUzbek() {
        TranslationCatalog.all.forEach { edition ->
            val cyrillic = edition.language == AppLanguage.RU || edition.language == AppLanguage.TG || edition.key.startsWith("qe:uzbek")
            assertEquals(if (cyrillic) TranslationScript.CYRILLIC else TranslationScript.LATIN, edition.script, edition.key)
        }
    }

    @Test
    fun quranEncEditionsNameTheirVersionTheOthersHaveNone() {
        val quranEnc = TranslationCatalog.all.filter { it.origin == TranslationOrigin.QURAN_ENC }
        assertEquals(
            setOf("tajik_arifi", "uzbek_rwwad", "uzbek_mansour", "turkish_rwwad", "turkish_shaban", "turkish_shahin"),
            quranEnc.map { it.remoteId }.toSet(),
        )
        quranEnc.forEach { assertTrue(Regex("\\d+\\.\\d+\\.\\d+").matches(it.version.orEmpty()), "${it.key}: ${it.version}") }
        TranslationCatalog.all.filter { it.origin != TranslationOrigin.QURAN_ENC }.forEach { assertNull(it.version, it.key) }
    }

    @Test
    fun anEditionOfBothQuranEncAndQuranComIsTakenFromQuranEnc() {
        // tajik_arifi = quran.com 223, turkish_shaban = quran.com 112: only the QuranEnc ones are in the list.
        assertNull(TranslationCatalog.byKey("qc:223"))
        assertNull(TranslationCatalog.byKey("qc:112"))
        assertEquals(TranslationOrigin.QURAN_ENC, TranslationCatalog.byKey("qe:tajik_arifi")?.origin)
        assertEquals(TranslationOrigin.QURAN_ENC, TranslationCatalog.byKey("qe:turkish_shaban")?.origin)
    }

    @Test
    fun popularEditionsComeFirst() {
        assertEquals(listOf("ru.kuliev", "ru.abuadel", "ru.muntahab"), TranslationCatalog.editionsOf(AppLanguage.RU).take(3).map { it.remoteId })
        assertEquals(listOf("tr.diyanet", "tr.yazir"), TranslationCatalog.editionsOf(AppLanguage.TR).take(2).map { it.remoteId })
        assertEquals("tg.ayati", TranslationCatalog.editionsOf(AppLanguage.TG).first().remoteId)
        assertEquals("55", TranslationCatalog.editionsOf(AppLanguage.UZ).first().remoteId)
        assertEquals(listOf("en.sahih", "85"), TranslationCatalog.editionsOf(AppLanguage.EN).take(2).map { it.remoteId })
    }

    @Test
    fun whereBothSourcesHaveAnEditionAlquranCloudIsUsed() {
        // en.pickthall, en.yusufali and en.hilali exist at alquran.cloud: quran.com 19, 22 and 203 are not used.
        listOf("19", "22", "203", "78", "79", "45", "77", "52").forEach { id ->
            assertTrue(TranslationCatalog.all.none { it.origin == TranslationOrigin.QURAN_COM && it.remoteId == id }, "quran.com $id")
        }
    }

    @Test
    fun otherLanguagesAreEveryEditionOfTheOtherLanguages() {
        AppLanguage.entries.forEach { language ->
            val others = TranslationCatalog.otherLanguages(language)
            assertTrue(others.none { it.language == language })
            assertEquals(TranslationCatalog.all.size, others.size + TranslationCatalog.editionsOf(language).size)
        }
        assertNotNull(TranslationCatalog.otherLanguages(AppLanguage.RU).firstOrNull { it.key == "en" }, "en Saheeh is offered to a Russian interface")
    }

    @Test
    fun theDownloadSizeIsKnown() {
        TranslationCatalog.all.forEach { edition ->
            assertTrue(edition.wireKb in 100..2_000, "${edition.key}: ${edition.wireKb} KB")
            assertEquals(edition.wireKb * 1024L, edition.wireBytes)
        }
    }
}
