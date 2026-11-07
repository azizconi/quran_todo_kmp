package tj.app.quran_todo.common.mushaf

import java.io.File

/**
 * The answers of quran.com used by the tests of the mushaf pages (2026-10-31), trimmed to what the app reads (`verse_key`, `words` with
 * `position`, `char_type_name`, `code_v1`, `line_number`, `page_number`, `pagination`): pages 1, 2, 3, 50 (a surah in the middle of the
 * Quran), 76 and 77 (a header at the bottom of a page and its Bismillah at the top of the next), 187 (At-Tawbah, no Bismillah), 586 and
 * 587 (the same exception, with two surahs on a page) and 604 (three surahs); and `page_shapes.txt`, one line per page of all 604: which
 * lines hold words, where a surah starts, how the page ends.
 */
internal object MushafFixtures {
    /** The folder `src/androidUnitTest/resources/mushaf`, found from the working directory of the test (the module or the repository). */
    private val folder: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .flatMap { dir ->
            sequenceOf(
                File(dir, "src/androidUnitTest/resources/mushaf"),
                File(dir, "composeApp/src/androidUnitTest/resources/mushaf"),
            )
        }
        .first { it.isDirectory }

    fun answer(page: Int): String = File(folder, "page_$page.json").readText(Charsets.UTF_8)

    /** What the shape file says about a page: lines with words, the surahs that start on it (surah to line), the last word. */
    class Shape(val page: Int, val lines: List<Int>, val starts: Map<Int, Int>, val lastSurah: Int, val lastAyah: Int, val lastIsEnd: Boolean)

    val shapes: List<Shape> by lazy {
        File(folder, "page_shapes.txt").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { row ->
                val parts = row.split('|')
                val last = parts[3].split(':')
                Shape(
                    page = parts[0].toInt(),
                    lines = parts[1].split(',').filter { it.isNotEmpty() }.map { it.toInt() },
                    starts = parts[2].split(',').filter { it.isNotEmpty() }.associate { entry ->
                        val (surah, line) = entry.split('@')
                        surah.toInt() to line.toInt()
                    },
                    lastSurah = last[0].toInt(),
                    lastAyah = last[1].toInt(),
                    lastIsEnd = last[2] == "1",
                )
            }
    }
}
