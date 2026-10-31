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
    fun theSummaryExplainsTheMicrophoneAndThatTheRecordingsStayOnThePhone() {
        // Task F2a: the app asks for the microphone for "My reading". The summary says when it is used and where the recordings stay.
        val words = mapOf(
            AppLanguage.RU to listOf("микрофон"),
            AppLanguage.EN to listOf("microphone"),
            AppLanguage.TG to listOf("микрофон"),
            AppLanguage.UZ to listOf("mikrofon"),
            AppLanguage.TR to listOf("mikrofon"),
        )
        AppLanguage.entries.forEach { language ->
            val points = trustCopy(language).privacyPoints
            val mentions = points.filter { point -> words.getValue(language).any { it in (point.title + " " + point.text).lowercase() } }
            // The permissions say when it is used, and a point of its own says where the recordings stay.
            assertTrue(mentions.size >= 2, "$language: the microphone is named in the permissions and in a point of its own")
            assertTrue(mentions.any { it.text.length > 120 }, "$language: the point about the microphone is a full sentence")
            val permissions = points.joinToString(" ") { it.title + " " + it.text }.lowercase()
            // "No microphone" claims are gone: no point says the app does not use it.
            listOf("does not use the microphone", "не использует микрофон", "mikrofondan foydalanmaydi", "mikrofon kullanmaz").forEach {
                assertFalse(it in permissions, "$language still says: $it")
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
