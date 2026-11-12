package tj.app.quran_todo.ui.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The vocabulary of docs/redesign/GLOSSARY.md (task U26), checked on the sources of the copy files: they all are `when (language)`
 * over the five languages, so the strings of each language are read from its block, without building every copy object.
 */
class GlossaryCopyTest {
    private val marker = Regex("""AppLanguage\.(RU|EN|TG|UZ|TR)\s*->""")
    private val literal = Regex(""""((?:[^"\\]|\\.)*)"""")

    private fun copyDirs(): List<File> {
        val roots = listOf(File("."), File("composeApp"))
        return roots.flatMap { root ->
            listOf(
                File(root, "src/androidMain/kotlin/tj/app/quran_todo/ui/i18n"),
                File(root, "src/voiceproto/kotlin/tj/app/quran_todo/ui/i18n"),
            )
        }.filter { it.isDirectory }
    }

    /** (file name, language, string) of every string literal in the copy files. */
    private fun allStrings(): List<Triple<String, String, String>> {
        val dirs = copyDirs()
        assertTrue(dirs.isNotEmpty(), "the copy sources were not found from ${File(".").absolutePath}")
        val result = mutableListOf<Triple<String, String, String>>()
        dirs.flatMap { it.listFiles { f -> f.extension == "kt" }!!.toList() }.forEach { file ->
            var language: String? = null
            file.readLines().forEach { line ->
                marker.find(line)?.let { language = it.groupValues[1] }
                val trimmed = line.trim()
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return@forEach
                val lang = language ?: return@forEach
                literal.findAll(line).forEach { result += Triple(file.name, lang, it.groupValues[1]) }
            }
        }
        return result
    }

    @Test
    fun theCopyFilesAreFound() {
        val strings = allStrings()
        AppLanguageNames.forEach { lang -> assertTrue(strings.count { it.second == lang } > 500, "too few strings for $lang") }
    }

    @Test
    fun russianAddressesThePersonWithVyNeverTy() {
        val forbidden = listOf(
            "ты", "тебе", "тебя", "твой", "твоя", "твои", "твоё", "твоего", "твоей", "твоих",
            "Послушай", "Прочитай", "Вспомни", "Нажми", "Нажимай", "Выбери", "Продолжи", "Различи", "Расставь", "Проверь",
            "Отметь", "Выучи", "Начни", "Открой", "Попробуй", "Запомни", "Закрой", "Скажи", "Сравни",
        )
        val regex = Regex("(?<![А-Яа-яЁё])(" + forbidden.joinToString("|") + ")(?![А-Яа-яЁё])", RegexOption.IGNORE_CASE)
        val hits = allStrings().filter { it.second == "RU" }.filter { regex.containsMatchIn(it.third) }
        assertEquals(emptyList(), hits.map { "${it.first}: ${it.third}" }, "ты-forms in the Russian copy")
    }

    @Test
    fun tajikAndTurkishAndUzbekQuizAndLessonUsePoliteForms() {
        val forbidden = mapOf(
            "TG" to listOf("кун", "деҳ", "биёр", "ёд ор", "гузор"),
            "UZ" to listOf("Tingla", "O‘qi", "Eslab ayt"),
            "TR" to listOf("hatırla", "dokun", "seç", "karşılaştır", "ayırt et", "Kendini"),
        )
        val files = setOf("QuizCopy.kt", "LessonCopy.kt", "SimilarCopy.kt", "DrillCopy.kt")
        val hits = mutableListOf<String>()
        allStrings().filter { it.first in files }.forEach { (file, lang, text) ->
            val words = forbidden[lang] ?: return@forEach
            words.forEach { w ->
                val r = Regex("(?<![\\p{L}])" + Regex.escape(w) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
                if (r.containsMatchIn(text)) hits += "$file/$lang [$w]: $text"
            }
        }
        assertEquals(emptyList(), hits)
    }

    @Test
    fun checkYourselfNamesOnlyTheRecallAndNeverTheTest() {
        val names = mapOf(
            "RU" to "Проверьте себя", "EN" to "Check yourself", "TG" to "Худро санҷед", "UZ" to "O‘zingizni tekshiring",
            "TR" to "Kendinizi sınayın",
        )
        val allowedFiles = setOf("HifzCopy.kt", "ReaderCopy.kt", "MushafCopy.kt")
        val misplaced = allStrings().filter { (file, lang, text) -> names[lang]?.let { text.contains(it, ignoreCase = true) } == true && file !in allowedFiles }
        assertEquals(emptyList(), misplaced.map { "${it.first}: ${it.third}" })
    }

    @Test
    fun theTestIsCalledTestAndRetentionIsNotCalledRetention() {
        val strings = allStrings()
        assertEquals("Тест", quizCopy(tj.app.quran_todo.common.i18n.AppLanguage.RU).title)
        assertEquals("Test", quizCopy(tj.app.quran_todo.common.i18n.AppLanguage.EN).title)
        assertTrue(strings.none { it.second == "RU" && it.third.contains("Удержание") }, "«Удержание» is gone")
        assertTrue(strings.any { it.second == "RU" && it.third == "Вспомнили с первого раза" })
        assertTrue(strings.none { it.second == "RU" && it.third.contains("Заучиваю") }, "learning status is «Учу»")
    }

    private companion object {
        val AppLanguageNames = listOf("RU", "EN", "TG", "UZ", "TR")
    }
}
