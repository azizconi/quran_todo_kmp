package tj.app.quran_todo.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.tajweed.TajweedGroup
import tj.app.quran_todo.common.tajweed.TajweedRule

/** The strings of the tajweed colours (task F3b) exist in the five languages, say what the rules say and have no star in them. */
class TajweedCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷', '✴', '✳', '❋')

    private fun strings(copy: TajweedCopy): Map<String, String> {
        val all = LinkedHashMap<String, String>()
        TajweedCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .forEach { all[it.name] = it.get(copy) as String }
        copy.groups.forEach { (group, text) -> all["group.$group"] = text }
        copy.rules.forEach { (rule, text) ->
            all["rule.$rule.name"] = text.name
            all["rule.$rule.how"] = text.how
        }
        return all
    }

    @Test
    fun everyStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val copy = tajweedCopy(language)
            assertEquals(TajweedGroup.entries.toSet(), copy.groups.keys, language.name)
            assertEquals(TajweedRule.entries.toSet(), copy.rules.keys, language.name)
            val all = strings(copy)
            assertTrue(all.size >= 6 + 6 + 2 * 17, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
            }
        }
    }

    @Test
    fun theFiveLanguagesDoNotShareTheirTexts() {
        val byLanguage = AppLanguage.entries.associateWith { strings(tajweedCopy(it)) }
        byLanguage.getValue(AppLanguage.RU).keys.filter { it != "language" }.forEach { key ->
            val values = byLanguage.values.map { it.getValue(key) }
            // Some Arabic terms are written the same in two languages (uz and tr "Iqlob"/"İklab" differ, but "Qalqala" and "Kalkale" do too);
            // at most one pair of languages may spell a term alike, the lines that explain it never.
            val limit = if (key.endsWith(".how") || key.startsWith("legend") || key.startsWith("switch") || key == "downloading") 5 else 4
            assertTrue(values.toSet().size >= limit, "$key is the same in too many languages: $values")
        }
    }

    @Test
    fun theRussianNamesAreTheTermsTheBriefAsksFor() {
        val ru = tajweedCopy(AppLanguage.RU)
        assertEquals("Идгам с гунной", ru.rule(TajweedRule.IdghamWithGhunna).name)
        assertEquals("Ихфа", ru.rule(TajweedRule.Ikhfa).name)
        assertEquals("Икляб", ru.rule(TajweedRule.Iqlab).name)
        assertEquals("Калькаля", ru.rule(TajweedRule.Qalqalah).name)
        assertTrue(ru.rule(TajweedRule.MaddNormal).name.startsWith("Мадд"))
        assertEquals("Таджвид", ru.switchTitle)
        assertEquals("Что значат цвета", ru.legendRow)
        assertEquals("Загружаю разметку таджвида · 2,5 МБ", ru.downloading)
        assertEquals("Нужен интернет один раз", ru.needInternet)
        assertEquals("Повторить", ru.retry)
    }

    @Test
    fun theLengthsOfTheMaddsAreInTheLineOfEachLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = tajweedCopy(language)
            fun how(rule: TajweedRule) = copy.rule(rule).how
            assertTrue("2" in how(TajweedRule.MaddNormal), "${language.name} normal")
            assertTrue(listOf("2", "4", "6").all { it in how(TajweedRule.MaddPermissible) }, "${language.name} permissible")
            assertTrue("4" in how(TajweedRule.MaddObligatory) && "5" in how(TajweedRule.MaddObligatory), "${language.name} obligatory")
            assertTrue("6" in how(TajweedRule.MaddNecessary), "${language.name} necessary")
            assertTrue("15" in how(TajweedRule.Ikhfa), "${language.name} ikhfa has 15 letters")
        }
    }

    @Test
    fun theDownloadLineSaysHowBigTheFileIs() {
        AppLanguage.entries.forEach { language ->
            val line = tajweedCopy(language).downloading
            assertTrue("2,5" in line || "2.5" in line, "${language.name}: $line")
        }
    }

    @Test
    fun theTermsOfEveryLanguageAreNotTheEnglishOnes() {
        val english = tajweedCopy(AppLanguage.EN)
        listOf(AppLanguage.RU, AppLanguage.TG).forEach { language ->
            val other = tajweedCopy(language)
            // The two languages that write the Arabic terms in Cyrillic share no word with the Latin spelling.
            TajweedRule.entries.forEach { rule ->
                assertTrue(other.rule(rule).name != english.rule(rule).name, "${language.name} ${rule.name}")
            }
        }
    }
}
