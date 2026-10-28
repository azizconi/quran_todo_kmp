package tj.app.quran_todo.ui.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.TrustCopy
import tj.app.quran_todo.ui.i18n.trustCopy

class TrustContentTest {
    @Test
    fun everyLanguageHasEveryString() {
        AppLanguage.entries.forEach { language ->
            val copy = trustCopy(language)
            TrustCopy::class.java.declaredFields
                .filter { it.type == String::class.java }
                .forEach { field ->
                    field.isAccessible = true
                    assertTrue((field.get(copy) as String).isNotBlank(), "$language: ${field.name} is blank")
                }
            assertTrue(copy.privacyPoints.size in 6..8, "$language: the summary has 6-8 points")
            copy.privacyPoints.forEach { point ->
                assertTrue(point.title.isNotBlank() && point.text.isNotBlank(), "$language: blank point")
            }
        }
    }

    @Test
    fun everyLanguageHasTheSameNumberOfPrivacyPoints() {
        val english = trustCopy(AppLanguage.EN).privacyPoints.size
        AppLanguage.entries.forEach { language ->
            assertEquals(english, trustCopy(language).privacyPoints.size, "$language")
        }
    }

    @Test
    fun translationsAreNotCopiesOfTheEnglishText() {
        val english = trustCopy(AppLanguage.EN)
        AppLanguage.entries.filter { it != AppLanguage.EN }.forEach { language ->
            val other = trustCopy(language)
            assertTrue(other.sectionPrivacy != english.sectionPrivacy, "sectionPrivacy/$language")
            assertTrue(other.analyticsTitle != english.analyticsTitle, "analyticsTitle/$language")
            assertTrue(other.sourcesIntro != english.sourcesIntro, "sourcesIntro/$language")
            assertTrue(other.privacyPoints.first().text != english.privacyPoints.first().text, "points/$language")
        }
    }

    @Test
    fun theSummaryNeverMentionsTheMicrophone() {
        AppLanguage.entries.forEach { language ->
            val text = trustCopy(language).let { copy ->
                copy.privacyPoints.joinToString(" ") { it.title + " " + it.text } + copy.privacyIntro
            }.lowercase()
            listOf("microphone", "микрофон", "mikrofon", "микрофон").forEach { word ->
                assertFalse(word in text, "$language mentions $word")
            }
        }
    }

    @Test
    fun everySourceGroupIsNotEmptyAndLinksAreHttps() {
        assertEquals(SourceGroupId.entries.toSet(), SourceGroups.map { it.id }.toSet())
        SourceGroups.forEach { group ->
            assertTrue(group.items.isNotEmpty(), "${group.id} is empty")
            group.items.forEach { item ->
                assertTrue(item.title.isNotBlank(), "${group.id}: blank title")
                item.url?.let { assertTrue(it.startsWith("https://"), "${item.title}: $it is not https") }
            }
        }
    }

    @Test
    fun theSourcesNameWhatTheTaskRequires() {
        val titles = SourceGroups.flatMap { group -> group.items.flatMap { listOfNotNull(it.title, it.author, it.provider) } }
            .joinToString("\n")
        listOf(
            "Tanzil", "alquran.cloud", "Кулиев", "Saheeh International", "Оятӣ", "Hamdi Yazır",
            "Muhammad Sodiq Muhammad Yusuf", "quran.com", "cdn.islamic.network", "Alafasy", "Husary",
            "Minshawi", "Abdul Basit", "Manrope", "Amiri Quran", "Kotlin", "Compose Multiplatform", "AndroidX",
            "Room", "Ktor", "Koin", "Media3", "Firebase",
        ).forEach { expected ->
            assertTrue(expected.lowercase() in titles.lowercase(), "sources do not mention $expected")
        }
    }

    @Test
    fun theTanzilRowCarriesTheNoChangeCondition() {
        val tanzil = SourceGroups.first { it.id == SourceGroupId.QuranText }.items.single()
        assertEquals(SourceNote.TanzilTerms, tanzil.note)
        assertEquals("https://tanzil.net", tanzil.url)
    }

    @Test
    fun hostsAreShownWithoutSchemeAndPath() {
        assertEquals("tanzil.net", displayHost("https://tanzil.net"))
        assertEquals("fonts.google.com", displayHost("https://fonts.google.com/specimen/Amiri+Quran"))
        assertEquals("jetbrains.com", displayHost("https://www.jetbrains.com/compose-multiplatform/"))
    }
}
