package tj.app.quran_todo.data.translation

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationChoiceTest {
    private var stored: String? = null
    private val store = TranslationChoiceStore(read = { stored }, write = { stored = it })

    private fun edition(key: String) = TranslationCatalog.byKey(key)!!

    @Test
    fun withoutAChoiceEveryLanguageShowsItsDefault() {
        AppLanguage.entries.forEach { language ->
            assertEquals(TranslationCatalog.defaultFor(language).key, store.editionFor(language).key)
        }
        assertTrue(store.choices().isEmpty(), "defaults are not choices")
    }

    @Test
    fun theChoiceIsKeptPerLanguageOfTheInterface() {
        store.select(AppLanguage.RU, edition("ru.abuadel"))
        store.select(AppLanguage.TR, edition("qc:210"))

        assertEquals("ru.abuadel", store.editionFor(AppLanguage.RU).key)
        assertEquals("qc:210", store.editionFor(AppLanguage.TR).key)
        // The others are untouched: a change of the language of the interface takes the choice of that language.
        assertEquals("en", store.editionFor(AppLanguage.EN).key)
        assertEquals("tg", store.editionFor(AppLanguage.TG).key)
    }

    @Test
    fun aTranslationInAnotherLanguageCanBeChosen() {
        store.select(AppLanguage.RU, edition("en"))
        assertEquals("en", store.editionFor(AppLanguage.RU).key)
        assertEquals(AppLanguage.EN, store.editionFor(AppLanguage.RU).language)
    }

    @Test
    fun theChoiceSurvivesARestart() {
        store.select(AppLanguage.RU, edition("ru.muntahab"))
        val reopened = TranslationChoiceStore(read = { stored }, write = { stored = it })
        assertEquals("ru.muntahab", reopened.editionFor(AppLanguage.RU).key)
    }

    @Test
    fun anUnknownOrDamagedChoiceFallsBackToTheDefault() {
        stored = """{"ru":"ru.nobody","tr":"tr.yazir"}"""
        assertEquals("ru", store.editionFor(AppLanguage.RU).key)
        // The default edition lives under the code of its language, never under its source id: "tr.yazir" is not a key.
        assertEquals("tr", store.editionFor(AppLanguage.TR).key)
        val broken = TranslationChoiceStore(read = { "not json" }, write = {})
        assertEquals("ru", broken.editionFor(AppLanguage.RU).key)
    }

    @Test
    fun everyChoiceChangesTheRevision() {
        val before = store.revision.value
        store.select(AppLanguage.RU, edition("ru.abuadel"))
        assertEquals(before + 1, store.revision.value)
        store.restore(mapOf("tr" to "tr.diyanet"))
        assertEquals(before + 2, store.revision.value)
    }

    @Test
    fun restoreTakesTheValidChoicesAndKeepsTheOthers() {
        store.select(AppLanguage.EN, edition("qc:85"))
        store.restore(mapOf("ru" to "ru.osmanov", "xx" to "ru.abuadel", "tr" to "nobody"))

        assertEquals("ru.osmanov", store.editionFor(AppLanguage.RU).key)
        assertEquals("qc:85", store.editionFor(AppLanguage.EN).key, "a language that the file does not mention keeps its choice")
        assertEquals(mapOf("en" to "qc:85", "ru" to "ru.osmanov"), store.choices())
    }

    @Test
    fun twoEditionsOfOneLanguageHaveTwoCacheKeys() {
        // The cache of Kuliev (under "ru") and of Abu Adel (under "ru.abuadel") cannot be mixed up.
        assertTrue(edition("ru").key != edition("ru.abuadel").key)
        store.select(AppLanguage.RU, edition("ru.abuadel"))
        val chosen = store.editionFor(AppLanguage.RU)
        store.select(AppLanguage.RU, edition("ru"))
        assertTrue(chosen.key != store.editionFor(AppLanguage.RU).key)
    }
}
