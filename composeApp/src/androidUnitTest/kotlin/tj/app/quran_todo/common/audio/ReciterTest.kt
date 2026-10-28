package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class ReciterTest {
    @Test
    fun theDefaultReciterKeepsTheUrlThatWasHardcodedBeforeReciters() {
        // This exact URL was written by hand in the shared session, the offline package and the legacy screen.
        assertEquals(
            "https://cdn.islamic.network/quran/audio/128/ar.alafasy/6222.mp3",
            Reciters.default.audioUrl(6222),
        )
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3", Reciters.default.audioUrl(1))
    }

    @Test
    fun everyReciterUsesTheEditionAndBitrateVerifiedOnTheCdn() {
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.alafasy/1.mp3", Reciters.Alafasy.audioUrl(1))
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.husary/262.mp3", Reciters.Husary.audioUrl(262))
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.minshawi/6236.mp3", Reciters.Minshawi.audioUrl(6236))
        // The CDN keeps this murattal edition at 192 kbit/s (128 answers 403).
        assertEquals(
            "https://cdn.islamic.network/quran/audio/192/ar.abdulbasitmurattal/6222.mp3",
            Reciters.AbdulBasit.audioUrl(6222),
        )
    }

    @Test
    fun theDefaultReciterKeepsTheOldCacheKeySoDownloadedAudioIsNotLost() {
        assertEquals(ayahAudioCacheKey(78, 5), Reciters.Alafasy.cacheKey(78, 5))
        assertEquals("ayah_78_5", Reciters.Alafasy.cacheKey(78, 5))
    }

    @Test
    fun otherRecitersGetTheirOwnCacheKeys() {
        assertEquals("husary_78_5", Reciters.Husary.cacheKey(78, 5))
        assertEquals("minshawi_78_5", Reciters.Minshawi.cacheKey(78, 5))
        assertEquals("abdulbasit_78_5", Reciters.AbdulBasit.cacheKey(78, 5))
        val keys = Reciters.all.map { it.cacheKey(1, 1) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun anUnknownOrMissingSavedIdFallsBackToTheDefault() {
        assertSame(Reciters.Husary, Reciters.byId("husary"))
        assertSame(Reciters.default, Reciters.byId(null))
        assertSame(Reciters.default, Reciters.byId("someone-removed-later"))
    }

    @Test
    fun idsAreUniqueAndEditionsAreDistinct() {
        assertEquals(Reciters.all.size, Reciters.all.map { it.id }.toSet().size)
        assertEquals(Reciters.all.size, Reciters.all.map { it.edition }.toSet().size)
        assertEquals(4, Reciters.all.size)
    }

    @Test
    fun everyReciterHasANameInEveryLanguage() {
        for (reciter in Reciters.all) {
            for (language in AppLanguage.entries) {
                assertNotEquals("", reciter.name(language), "${reciter.id} ${language.name} full name")
                assertNotEquals("", reciter.shortName(language), "${reciter.id} ${language.name} short name")
            }
        }
        assertEquals("Мишари Аль-Афаси", Reciters.Alafasy.name(AppLanguage.RU))
        assertEquals("Абдуль-Басит Абдус-Самад", Reciters.AbdulBasit.name(AppLanguage.RU))
    }
}
