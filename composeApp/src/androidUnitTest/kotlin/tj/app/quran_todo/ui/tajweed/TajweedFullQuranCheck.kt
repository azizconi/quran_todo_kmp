package tj.app.quran_todo.ui.tajweed

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import tj.app.quran_todo.common.tajweed.LetterUnits
import tj.app.quran_todo.common.tajweed.TajweedAlignment
import tj.app.quran_todo.common.tajweed.TajweedFormat
import tj.app.quran_todo.common.tajweed.TajweedMarkup
import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.ui.reader.splitBismillah

/**
 * The check of the alignment on ALL 6236 ayahs (task F3b). It needs the real answer of api.alquran.cloud `quran-tajweed`, which is network
 * data and so is not part of the unit tests: without the file this test is skipped. Run it with `scripts/check_tajweed_alignment.py`, which
 * fetches the answer, puts it where this test reads it (`composeApp/build/tajweed-check/quran-tajweed.json`, or the path in the environment
 * variable `TAJWEED_JSON`), runs this test and prints the report that it writes next to it (`report.txt`).
 *
 * What it checks, with the same functions the app uses (`splitBismillah` for the text that is drawn, `TajweedMarkup`, `TajweedAlignment`):
 * - the share of the rules of the markup that reach our text (the goal is 99.5 %);
 * - which ayahs lose a rule;
 * - that the letters under every moved range are the letters of the rule in their text, by skeleton: letters only, no harakat or other
 *   signs, spellings of one letter made equal (a skeleton is computed here from scratch, not with the alignment's own letter units);
 * - that every example of the legend is the rule it says it is.
 */
class TajweedFullQuranCheck {
    private fun input(): File? {
        System.getenv("TAJWEED_JSON")?.let { return File(it).takeIf(File::isFile) }
        return File("build/tajweed-check/quran-tajweed.json").takeIf(File::isFile)
    }

    @Test
    fun everyRuleOfTheWholeQuranReachesTheBundledText() {
        val file = input()
        assumeTrue("no quran-tajweed.json: run scripts/check_tajweed_alignment.py", file != null)
        val surahs = assertNotNull(TajweedFormat.splitQuran(file!!.readText(Charsets.UTF_8)), "the file is not a whole Quran")
        val quran = QuranTsvParser.parse(BundledFiles.quranTsv())
        val oursOf = quran.ayahs.associateBy { it.surahNumber * 1000 + it.numberInSurah }

        var ayahs = 0
        var rules = 0
        var movedRules = 0
        var pieces = 0
        var movedPieces = 0
        var emptyPieces = 0
        var unmatchedOurs = 0
        var unmatchedTheirs = 0
        var totalOurs = 0
        var totalTheirs = 0
        var exactText = 0
        var exactSkeleton = 0
        val byCode = sortedMapOf<Char, IntArray>() // code -> [rules, moved]
        val lostRules = ArrayList<String>()
        val mismatches = ArrayList<String>()
        val worst = ArrayList<String>()
        val started = System.nanoTime()
        for ((surahIndex, markups) in surahs.withIndex()) {
            val surah = surahIndex + 1
            for ((ayahIndex, markup) in markups.withIndex()) {
                val ayah = ayahIndex + 1
                ayahs++
                val theirs = TajweedMarkup.parse(markup)
                val entity = assertNotNull(oursOf[surah * 1000 + ayah], "$surah:$ayah is not in the bundled text")
                val ours = splitBismillah(surah, ayah, entity.text)
                if (theirs.plain == ours) exactText++
                if (skeleton(theirs.plain) == skeleton(ours)) exactSkeleton++

                val moved = TajweedAlignment.alignDetailed(theirs, ours)
                rules += theirs.ruleCount
                pieces += theirs.spans.size
                movedPieces += moved.size
                val reached = moved.map { it.from.source }.toSet()
                movedRules += reached.size
                val firstOfSource = theirs.spans.groupBy { it.source }
                for ((source, spans) in firstOfSource) {
                    val counts = byCode.getOrPut(spans.first().rule.code) { IntArray(2) }
                    counts[0]++
                    if (source in reached) counts[1]++ else lostRules += "$surah:$ayah ${spans.first().rule.code} '${theirs.plain.substring(spans.first().range.first, spans.last().range.last + 1)}'"
                }
                emptyPieces += theirs.spans.count { span -> LetterUnits.of(theirs.plain.substring(span.range.first, span.range.last + 1)).size == 0 }

                for (move in moved) {
                    val theirSkeleton = skeleton(theirs.plain.substring(move.from.range.first, move.from.range.last + 1))
                    val ourSkeleton = skeleton(ours.substring(move.to.range.first, move.to.range.last + 1))
                    if (theirSkeleton != ourSkeleton) {
                        mismatches += "$surah:$ayah ${move.from.rule.code} theirs='$theirSkeleton' ours='$ourSkeleton'"
                    }
                }

                val a = LetterUnits.of(theirs.plain)
                val b = LetterUnits.of(ours)
                val partner = TajweedAlignment.match(a.letters, b.letters)
                if (partner != null) {
                    val mine = partner.count { it < 0 }
                    val used = partner.filter { it >= 0 }.toSet().size
                    unmatchedTheirs += mine
                    unmatchedOurs += b.size - used
                    totalTheirs += a.size
                    totalOurs += b.size
                    if (mine + (b.size - used) > 6) worst += "$surah:$ayah theirs-only=$mine ours-only=${b.size - used}"
                }
            }
        }
        val millis = (System.nanoTime() - started) / 1_000_000

        // The examples of the legend are what the markup says they are.
        val badExamples = ArrayList<String>()
        for ((rule, example) in TajweedExamples.byRule) {
            val (surah, ayah) = example.ref.split(':').map(String::toInt)
            val ours = splitBismillah(surah, ayah, oursOf.getValue(surah * 1000 + ayah).text)
            val at = ours.indexOf(example.text)
            val theirs = TajweedMarkup.parse(surahs[surah - 1][ayah - 1])
            val inPhrase = TajweedAlignment.align(theirs, ours).filter { it.range.first >= at && it.range.last < at + example.text.length }
            val mine = inPhrase.filter { it.rule == rule }.map { (it.range.first - at) until (it.range.last + 1 - at) }
            val expected = example.spans.map { it.first until it.last + 1 }
            if (at < 0 || mine != expected || inPhrase.any { it.rule != rule }) {
                badExamples += "$rule ${example.ref}: markup says ${inPhrase.map { it.rule.code to (it.range.first - at..it.range.last - at) }} for ${example.text}"
            }
        }

        val share = movedRules.toDouble() / rules
        val report = buildString {
            appendLine("Tajweed alignment on the whole Quran (${surahs.size} surahs, $ayahs ayahs), $millis ms")
            appendLine("Ayahs whose markup text equals the drawn text as it is: $exactText; by skeleton (letters only): $exactSkeleton")
            appendLine("Letters: theirs $totalTheirs, ours $totalOurs; without a partner: theirs $unmatchedTheirs, ours $unmatchedOurs")
            appendLine("Rules of the markup: $rules, moved onto our text: $movedRules (${"%.3f".format(share * 100)} %)")
            appendLine("Pieces (a rule cut in two by a rule inside it is two): $pieces, moved: $movedPieces, without a letter: $emptyPieces")
            appendLine("Rules lost: ${lostRules.size}${lostRules.joinToString(prefix = "\n  ", separator = "\n  ")}")
            appendLine("Skeleton check, moved ranges whose letters differ from the letters of the rule in their text: ${mismatches.size}")
            mismatches.take(20).forEach { appendLine("  $it") }
            appendLine("Ayahs with more than 6 letters without a partner: ${worst.size} ${worst.take(10)}")
            appendLine("Legend examples with a wrong rule or place: ${badExamples.size}")
            badExamples.forEach { appendLine("  $it") }
            appendLine("By code (rules, moved):")
            byCode.forEach { (code, counts) -> appendLine("  $code ${counts[0]} ${counts[1]}") }
        }
        val out = File("build/tajweed-check").also { it.mkdirs() }
        File(out, "report.txt").writeText(report, Charsets.UTF_8)
        println(report)

        assertEquals(QuranFacts.AYAH_COUNT, ayahs)
        assertTrue(share >= 0.995, "only ${share * 100} % of the rules reach our text")
        assertTrue(mismatches.isEmpty(), "letters of moved rules differ from theirs: ${mismatches.take(5)}")
        assertTrue(badExamples.isEmpty(), "legend examples are wrong: $badExamples")
    }

    /**
     * The letters of [text] and nothing else: no harakat, signs, tatweel or spaces; the spellings that stand for one letter in the two
     * texts made equal. Written out here and not shared with the alignment, so that the check does not agree with it by construction.
     */
    private fun skeleton(text: String): String = buildString {
        for (c in text) {
            val letter = when (c) {
                'ٱ', 'أ', 'إ', 'آ', 'ء', 'ٰ', 'ٲ', 'ٳ' -> 'ا'
                'ى', 'ٮ', 'ئ', 'ی' -> 'ي'
                'ؤ' -> 'و'
                'ۥ' -> 'و'
                'ۦ', 'ۧ' -> 'ي'
                'ـ' -> null
                else -> c.takeIf { it.isLetter() }
            }
            if (letter != null) append(letter)
        }
    }
}
