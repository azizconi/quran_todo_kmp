package tj.app.quran_todo.ui.onboarding

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.OnboardingCopy
import tj.app.quran_todo.ui.i18n.onboardingCopy
import tj.app.quran_todo.ui.whatsnew.WhatsNewCopy
import tj.app.quran_todo.ui.whatsnew.whatsNewCopy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The words of the onboarding in the five languages: filled, with the plural forms and without leftover placeholders. */
class OnboardingCopyTest {
    private val russian = onboardingCopy(AppLanguage.RU)

    private fun strings(copy: OnboardingCopy): Map<String, String> = copy::class.java.declaredFields
        .filter { it.type == String::class.java }
        .associate { field ->
            field.isAccessible = true
            field.name to field.get(copy) as String
        }

    @Test
    fun everyStringIsFilledInEveryLanguage() {
        for (language in AppLanguage.entries) {
            val all = strings(onboardingCopy(language))
            assertTrue(all.size > 60, "the fields of $language were read")
            for ((name, text) in all) assertTrue(text.isNotBlank(), "$language.$name is empty")
        }
    }

    @Test
    fun patternsKeepTheSamePlaceholdersInEveryLanguage() {
        val english = strings(onboardingCopy(AppLanguage.EN))
        val placeholder = Regex("\\{[a-z]+}")
        for (language in AppLanguage.entries) {
            val other = strings(onboardingCopy(language))
            for ((name, text) in other) {
                if (!name.endsWith("Fmt") && name != "stepOf" && name != "slideOf") continue
                assertEquals(
                    placeholder.findAll(english.getValue(name)).map { it.value }.toSet(),
                    placeholder.findAll(text).map { it.value }.toSet(),
                    "$language.$name has other placeholders than the English one",
                )
            }
        }
    }

    @Test
    fun filledPatternsLeaveNoBracesBehind() {
        for (language in AppLanguage.entries) {
            val copy = onboardingCopy(language)
            val filled = listOf(
                copy.stepLabel(3, 9),
                copy.slideLabel(2, 3),
                copy.knownCount(5),
                copy.remove("Al-Falaq"),
                copy.presetStart("Al-Falaq", 5),
                copy.forecast(PaceSpan(SpanUnit.Month, 4), "14 March"),
                copy.readyLesson("An-Nas 1–6", 6),
                copy.readyLesson("An-Nas 1–6", null),
                copy.readyKnown(3),
                copy.readyForecast("Juz Amma", "14 March"),
                copy.readyReminder("20:00"),
            )
            filled.forEach { text -> assertFalse('{' in text || '}' in text, "$language: $text") }
        }
    }

    @Test
    fun russianCountsOfSurahsFollowThePluralForms() {
        assertEquals("Отмечено: 1 сура", russian.knownCount(1))
        assertEquals("Отмечено: 2 суры", russian.knownCount(2))
        assertEquals("Отмечено: 5 сур", russian.knownCount(5))
        assertEquals("Отмечено: 11 сур", russian.knownCount(11))
        assertEquals("Отмечено: 21 сура", russian.knownCount(21))
        assertEquals("Знаете: 22 суры — будем повторять", russian.readyKnown(22))
    }

    @Test
    fun russianAyahsOfTheFirstLessonFollowThePluralForms() {
        assertEquals("Начнём с Аль-Фалак · 5 аятов", russian.presetStart("Аль-Фалак", 5))
        assertEquals("Начнём с Ан-Нас · 6 аятов", russian.presetStart("Ан-Нас", 6))
        assertEquals("Начнём с Аль-Каусар · 1 аят", russian.presetStart("Аль-Каусар", 1))
        assertEquals("Начнём с Аль-Каусар · 21 аят", russian.presetStart("Аль-Каусар", 21))
        assertEquals("Начнём с Аль-Каусар · 22 аята", russian.presetStart("Аль-Каусар", 22))
    }

    @Test
    fun russianSpansFollowThePluralForms() {
        fun span(unit: SpanUnit, n: Int) = russian.span(PaceSpan(unit, n))
        assertEquals(listOf("1 день", "2 дня", "5 дней", "11 дней", "21 день"), listOf(1, 2, 5, 11, 21).map { span(SpanUnit.Day, it) })
        assertEquals(listOf("1 неделя", "2 недели", "5 недель", "11 недель", "21 неделя"), listOf(1, 2, 5, 11, 21).map { span(SpanUnit.Week, it) })
        assertEquals(listOf("1 месяц", "2 месяца", "5 месяцев", "11 месяцев", "21 месяц"), listOf(1, 2, 5, 11, 21).map { span(SpanUnit.Month, it) })
        assertEquals(listOf("1 год", "2 года", "5 лет", "11 лет", "21 год"), listOf(1, 2, 5, 11, 21).map { span(SpanUnit.Year, it) })
        assertEquals("≈ 4 месяца · ~14 марта", russian.forecast(PaceSpan(SpanUnit.Month, 4), "14 марта"))
    }

    @Test
    fun englishSpansAndFirstLessonLine() {
        val english = onboardingCopy(AppLanguage.EN)
        assertEquals("1 week", english.span(PaceSpan(SpanUnit.Week, 1)))
        assertEquals("3 weeks", english.span(PaceSpan(SpanUnit.Week, 3)))
        assertEquals("Lesson 1: An-Nas 1–6 · ~6 min", english.readyLesson("An-Nas 1–6", 6))
        assertEquals("Lesson 1: An-Nas 1–6", english.readyLesson("An-Nas 1–6", null))
        assertEquals("Marked: 1 surah", english.knownCount(1))
        assertEquals("Marked: 3 surahs", english.knownCount(3))
    }

    @Test
    fun theWhatsNewSheetIsFilledInEveryLanguage() {
        for (language in AppLanguage.entries) {
            val copy = whatsNewCopy(language)
            val all = WhatsNewCopy::class.java.declaredFields.filter { it.type == String::class.java }.map { field ->
                field.isAccessible = true
                field.name to field.get(copy) as String
            }
            assertEquals(6, all.size)
            for ((name, text) in all) assertTrue(text.isNotBlank(), "$language.$name is empty")
        }
    }

    @Test
    fun noStarAnywhereInTheWordsOrTheCode() {
        // The owner forbids stars of any kind: no star characters in the copy, no star or sparkle icons in the screens.
        val starGlyphs = "★☆✦✧✩✪✫✬✭✮✯✰⭐🌟✨⋆✡"
        val copies = AppLanguage.entries.flatMap { strings(onboardingCopy(it)).values } +
            AppLanguage.entries.flatMap { language ->
                val copy = whatsNewCopy(language)
                listOf(copy.title, copy.body, copy.withPlan, copy.withoutPlan, copy.openLesson, copy.later)
            }
        copies.forEach { text -> assertTrue(text.none { it in starGlyphs }, "a star in: $text") }

        val forbidden = Regex("""Icons\.\w+\.(\w*Star\w*|AutoAwesome\w*|TipsAndUpdates|Sparkle\w*|Stars)|drawStar|StarShape|starPath""")
        val roots = listOf("src/androidMain/kotlin/tj/app/quran_todo/ui/onboarding", "src/androidMain/kotlin/tj/app/quran_todo/ui/whatsnew")
        var scanned = 0
        for (root in roots) {
            val dir = File(root)
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                scanned++
                val hit = forbidden.find(file.readText())
                assertTrue(hit == null, "${file.name} uses a star: ${hit?.value}")
            }
        }
        assertTrue(scanned > 0 || !File("src/androidMain").exists(), "the sources were not found")
    }
}
