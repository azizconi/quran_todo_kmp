package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun theFourFirstRecitersKeepTheEditionAndBitrateVerifiedOnTheCdn() {
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
    fun anAyahIsAskedForBySurahAndNumberToo() {
        // The same files as by the global number: 2:255 is the 262nd ayah, 112:1 the 6222nd.
        assertEquals(Reciters.Alafasy.audioUrl(262), Reciters.Alafasy.audioUrl(2, 255))
        assertEquals(Reciters.AbdulBasit.audioUrl(6222), Reciters.AbdulBasit.audioUrl(112, 1))
        assertEquals(Reciters.Husary.audioUrl(1), Reciters.Husary.audioUrl(1, 1))
        assertEquals(Reciters.Minshawi.audioUrl(6236), Reciters.Minshawi.audioUrl(114, 6))
    }

    @Test
    fun theRecitersOfQuranComAreBuiltFromTheTemplateOfTheFile() {
        assertEquals("https://verses.quran.com/Sudais/mp3/112001.mp3", Reciters.Sudais.audioUrl(112, 1))
        assertEquals("https://verses.quran.com/Shatri/mp3/002255.mp3", Reciters.Shatri.audioUrl(2, 255))
        assertEquals("https://verses.quran.com/Rifai/mp3/001001.mp3", Reciters.Rifai.audioUrl(1, 1))
        assertEquals("https://verses.quran.com/Shuraym/mp3/114006.mp3", Reciters.Shuraym.audioUrl(114, 6))
        assertEquals("https://verses.quran.com/AbdulBaset/Mujawwad/mp3/018010.mp3", Reciters.AbdulBasitMujawwad.audioUrl(18, 10))
        assertEquals("https://verses.quran.com/Minshawi/Mujawwad/mp3/002001.mp3", Reciters.MinshawiMujawwad.audioUrl(2, 1))
        // These two are on another host that quran.com prints without a scheme.
        assertEquals(
            "https://mirrors.quranicaudio.com/everyayah/Mohammad_al_Tablaway_128kbps/112001.mp3",
            Reciters.Tablawi.audioUrl(112, 1),
        )
        assertEquals(
            "https://mirrors.quranicaudio.com/everyayah/Husary_Muallim_128kbps/002255.mp3",
            Reciters.HusaryMuallim.audioUrl(2, 255),
        )
    }

    @Test
    fun aGlobalNumberReachesTheSameFileAsSurahAndAyah() {
        // 2:255 is 262, 18:10 is 2150, 114:6 is 6236, 112:1 is 6222, 1:1 is 1.
        val expected = mapOf(262 to (2 to 255), 2150 to (18 to 10), 6236 to (114 to 6), 6222 to (112 to 1), 1 to (1 to 1), 8 to (2 to 1))
        for ((global, place) in expected) {
            for (reciter in listOf(Reciters.Sudais, Reciters.Tablawi, Reciters.HusaryMuallim)) {
                assertEquals(reciter.audioUrl(place.first, place.second), reciter.audioUrl(global), "${reciter.id} $global")
            }
        }
    }

    @Test
    fun theTemplateKeepsThreeDigitsForEverySurahAndAyah() {
        val source = ReciterSource.QuranCom("X/{sss}{aaa}.mp3")
        assertEquals("https://verses.quran.com/X/001001.mp3", source.url(1, 1))
        assertEquals("https://verses.quran.com/X/114006.mp3", source.url(114, 6))
        assertEquals("https://verses.quran.com/X/002286.mp3", source.url(2, 286))
        assertEquals("https://example.org/X/009129.mp3", ReciterSource.QuranCom("//example.org/X/{sss}{aaa}.mp3").url(9, 129))
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
        assertEquals("sudais_78_5", Reciters.Sudais.cacheKey(78, 5))
        assertEquals("husary_muallim_78_5", Reciters.HusaryMuallim.cacheKey(78, 5))
        assertEquals("abdulbasit_mujawwad_78_5", Reciters.AbdulBasitMujawwad.cacheKey(78, 5))
        val keys = Reciters.all.map { it.cacheKey(1, 1) }
        assertEquals(keys.size, keys.toSet().size)
        // No key is the beginning of another one plus a number, so the files of two reciters never collide.
        val many = Reciters.all.flatMap { reciter -> listOf(reciter.cacheKey(1, 11), reciter.cacheKey(11, 1)) }
        assertEquals(many.size, many.toSet().size)
    }

    @Test
    fun theIdsAndCacheKeysOfTheFourFirstRecitersNeverChange() {
        // Saved settings, backups and downloaded files refer to these.
        assertEquals(listOf("alafasy", "husary", "minshawi", "abdulbasit"), listOf(Reciters.Alafasy, Reciters.Husary, Reciters.Minshawi, Reciters.AbdulBasit).map { it.id })
        assertEquals("ayah_2_255", Reciters.Alafasy.cacheKey(2, 255))
        assertEquals("husary_2_255", Reciters.Husary.cacheKey(2, 255))
        assertEquals("minshawi_2_255", Reciters.Minshawi.cacheKey(2, 255))
        assertEquals("abdulbasit_2_255", Reciters.AbdulBasit.cacheKey(2, 255))
        for (reciter in listOf(Reciters.Alafasy, Reciters.Husary, Reciters.Minshawi, Reciters.AbdulBasit)) {
            assertTrue(reciter.source is ReciterSource.IslamicNetwork, "${reciter.id} keeps the files of the CDN")
        }
    }

    @Test
    fun anUnknownOrMissingSavedIdFallsBackToTheDefault() {
        assertSame(Reciters.Husary, Reciters.byId("husary"))
        assertSame(Reciters.Sudais, Reciters.byId("sudais"))
        assertSame(Reciters.HusaryMuallim, Reciters.byId("husary_muallim"))
        assertSame(Reciters.default, Reciters.byId(null))
        assertSame(Reciters.default, Reciters.byId("someone-removed-later"))
    }

    @Test
    fun thereAreTwelveRecitersOfQuranComWithAnIdAndATimingEach() {
        assertEquals(12, Reciters.all.size)
        assertEquals(Reciters.all.size, Reciters.all.map { it.id }.toSet().size)
        // The recitations of quran.com (api/v4/resources/recitations) are numbered 1..12, each once.
        assertEquals((1..12).toList(), Reciters.all.map { it.recitationId }.sorted())
        assertEquals(7, Reciters.Alafasy.recitationId)
        assertEquals(6, Reciters.Husary.recitationId)
        assertEquals(9, Reciters.Minshawi.recitationId)
        assertEquals(2, Reciters.AbdulBasit.recitationId)
        assertEquals(12, Reciters.HusaryMuallim.recitationId)
        // Every file address is its own.
        val urls = Reciters.all.map { it.audioUrl(112, 1) }
        assertEquals(urls.size, urls.toSet().size)
        assertTrue(urls.all { it.startsWith("https://") })
    }

    @Test
    fun theListIsInGroupsTheTeachingVoiceFirstAndTheDefaultFirstOfTheMurattal() {
        assertEquals(listOf(Reciters.HusaryMuallim), Reciters.inGroup(ReciterGroup.Teaching))
        assertEquals(
            listOf("alafasy", "husary", "minshawi", "abdulbasit", "sudais", "shatri", "rifai", "shuraym", "tablawi"),
            Reciters.inGroup(ReciterGroup.Murattal).map { it.id },
        )
        assertEquals(listOf("abdulbasit_mujawwad", "minshawi_mujawwad"), Reciters.inGroup(ReciterGroup.Mujawwad).map { it.id })
        // The order of `all` is the order of the groups.
        assertEquals(ReciterGroup.entries.flatMap { Reciters.inGroup(it) }, Reciters.all)
        assertSame(Reciters.default, Reciters.inGroup(ReciterGroup.Murattal).first())
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
        assertEquals("Абдуррахман ас-Судейс", Reciters.Sudais.name(AppLanguage.RU))
    }

    @Test
    fun theNamesOfTheTwoStylesOfOneReciterDiffer() {
        for (language in AppLanguage.entries) {
            assertNotEquals(Reciters.AbdulBasit.shortName(language), Reciters.AbdulBasitMujawwad.shortName(language))
            assertNotEquals(Reciters.Minshawi.shortName(language), Reciters.MinshawiMujawwad.shortName(language))
            assertNotEquals(Reciters.Husary.shortName(language), Reciters.HusaryMuallim.shortName(language))
            assertNotEquals(Reciters.Husary.name(language), Reciters.HusaryMuallim.name(language))
        }
    }

    @Test
    fun allShortNamesOfOneLanguageAreDifferent() {
        for (language in AppLanguage.entries) {
            val names = Reciters.all.map { it.shortName(language) }
            assertEquals(names.size, names.toSet().size, "short names in ${language.name}")
        }
    }
}
