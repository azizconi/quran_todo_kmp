package tj.app.quran_todo.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.tafsir.TafsirSource

/** The strings of the commentary and the word meanings (task F4b) exist in the five languages, are told apart and have no star. */
class TafsirCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷', '✴', '✳', '❋')

    private fun strings(copy: TafsirCopy): Map<String, String> {
        val all = LinkedHashMap<String, String>()
        TafsirCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .forEach { all[it.name] = it.get(copy) as String }
        return all
    }

    @Test
    fun everyStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(tafsirCopy(language))
            assertTrue(all.size >= 20, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
            }
        }
    }

    @Test
    fun theFiveLanguagesDoNotShareTheirSentences() {
        val byLanguage = AppLanguage.entries.associateWith { strings(tafsirCopy(it)) }
        byLanguage.getValue(AppLanguage.RU).keys.filter { it != "language" }.forEach { key ->
            val values = byLanguage.values.map { it.getValue(key) }
            // Short words that are written alike in two languages ("Tafsir" in en and uz, "Тафсир" in ru and tg) are allowed; sentences are not.
            val sentence = values.first().length > 18
            val limit = if (key.startsWith("chip")) 3 else if (sentence) 5 else 3
            assertTrue(values.toSet().size >= limit, "$key is the same in too many languages: $values")
        }
    }

    @Test
    fun theTemplatesTakeTheirNumbers() {
        AppLanguage.entries.forEach { language ->
            val copy = tafsirCopy(language)
            assertTrue("2:1–5" in copy.group(2, 1, 5), language.name)
            assertTrue("%s" !in copy.group(2, 1, 5), language.name)
            val word = copy.word(3, 7)
            assertTrue("3" in word && "7" in word && "{" !in word, "${language.name}: $word")
        }
    }

    @Test
    fun everySourceHasAChipAndALanguageLine() {
        AppLanguage.entries.forEach { language ->
            val copy = tafsirCopy(language)
            assertEquals(TafsirSource.entries.size, TafsirSource.entries.map { copy.chip(it) }.toSet().size)
            // Ru, en, ar, tr and uz are the five languages a commentary is written in.
            assertEquals(5, TafsirSource.entries.map { copy.writtenIn(it) }.toSet().size)
        }
    }

    @Test
    fun theRussianChipsAreTheOnesTheBriefAsksFor() {
        val ru = tafsirCopy(AppLanguage.RU)
        assertEquals("ас-Саади · рус.", ru.chip(TafsirSource.Saadi))
        assertEquals("Ибн Касир · англ.", ru.chip(TafsirSource.IbnKathir))
        assertEquals("Муяссар · араб.", ru.chip(TafsirSource.Muyassar))
        assertEquals("Толкование на русском", ru.writtenIn(TafsirSource.Saadi))
        assertEquals("Толкование аятов 2:1–5", ru.group(2, 1, 5))
        assertEquals("Аль-Мухтасар фи тафсир аль-Куран · QuranEnc.com · версия 1.2", ru.creditLine("1.2"))
        assertEquals("Аль-Мухтасар фи тафсир аль-Куран · QuranEnc.com", ru.creditLine(null))
        assertEquals("Нужен интернет один раз для этой суры", ru.wordsNeedInternet)
        assertEquals("Для этого аята значения слов недоступны", ru.wordsUnavailable)
    }
}
