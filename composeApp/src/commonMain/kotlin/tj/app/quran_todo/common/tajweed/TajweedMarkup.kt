package tj.app.quran_todo.common.tajweed

/**
 * One coloured piece of an ayah: [rule] applies to the characters [range] (UTF-16 indices, both ends inclusive) of the text it was made for.
 * [source] is the number of the rule in the markup it comes from (in the order the rules open): the pieces of a rule that has another
 * rule inside it (see [TajweedMarkup]) share it, so a screen or a check can still tell how many rules the markup had.
 */
data class TajweedSpan(val rule: TajweedRule, val range: IntRange, val source: Int = 0)

/**
 * An ayah of the markup after parsing: [plain] is its text with the markup taken off, [spans] the pieces in reading order that
 * do not overlap, [ruleCount] the number of rules the markup holds (a rule that was cut in two by a rule inside it counts once).
 */
class TajweedAyah(val plain: String, val spans: List<TajweedSpan>, val ruleCount: Int)

/**
 * The markup of api.alquran.cloud `quran-tajweed`: `[code[text]` or `[code:number[text]`, for example
 * `ذ[n[َٲ]لِكَ [h:11[ٱ]لْكِتَ[n[ـٰ]بُ`. The code is one letter ([TajweedRule.code]), the optional number is a counter of the source that
 * means nothing here, the text is the stretch the rule colours (it may start in the middle of a word and run over a space:
 * `هُ[u:12[دًى ل]ِّلْمُتَّق`).
 *
 * What [parse] does, checked on all 6236 ayahs:
 * - **Rules inside a rule.** 33 ayahs have a rule that opens inside another one (`[o[ُوٓ[s[اْ]‌ۚ]`: a silent alif inside the madd). The
 *   inner rule wins on its own characters, the outer one keeps the rest, so the result never overlaps; the pieces of the outer rule share
 *   [TajweedSpan.source].
 * - **Damaged markup.** `[` that does not open a rule and `]` that closes nothing stay in [TajweedAyah.plain] as the text they are
 *   (32:3 has `[ٮٰ]` without a code), a rule that is never closed ends with the text. Nothing throws.
 * - **Unknown code.** The text of such a rule stays, the rule is dropped.
 */
object TajweedMarkup {
    fun parse(markup: String): TajweedAyah {
        val plain = StringBuilder(markup.length)
        // A rule that is open: its rule (null for an unknown code), where its text starts, its number.
        val openRules = ArrayList<OpenRule>()
        val closed = ArrayList<Piece>()
        var opened = 0
        var index = 0
        while (index < markup.length) {
            val char = markup[index]
            if (char == '[') {
                val end = opening(markup, index)
                if (end > 0) {
                    openRules += OpenRule(TajweedRule.fromCode(markup[index + 1]), plain.length, opened++)
                    index = end
                    continue
                }
            } else if (char == ']' && openRules.isNotEmpty()) {
                closed += openRules.removeAt(openRules.lastIndex).close(plain.length)
                index++
                continue
            }
            plain.append(char)
            index++
        }
        // A rule that was never closed runs to the end of the text.
        while (openRules.isNotEmpty()) closed += openRules.removeAt(openRules.lastIndex).close(plain.length)

        val text = plain.toString()
        val rules = closed.filter { it.rule != null }
        return TajweedAyah(text, flatten(text.length, rules), ruleCount = rules.size)
    }

    /**
     * The index after an opening `[code[` or `[code:number[` that starts at [start], or -1 when [start] is not the start of one.
     * The code is a lower-case latin letter, the number digits.
     */
    private fun opening(markup: String, start: Int): Int {
        var index = start + 1
        if (index >= markup.length || markup[index] !in 'a'..'z') return -1
        index++
        if (index < markup.length && markup[index] == ':') {
            index++
            val digits = index
            while (index < markup.length && markup[index] in '0'..'9') index++
            if (index == digits) return -1
        }
        if (index >= markup.length || markup[index] != '[') return -1
        return index + 1
    }

    /** The pieces without overlaps: every character belongs to the innermost rule that holds it (the shortest, the last opened on a tie). */
    private fun flatten(length: Int, rules: List<Piece>): List<TajweedSpan> {
        if (rules.isEmpty()) return emptyList()
        // Index into [rules] of the innermost rule at each character; the longest rules are painted first, so a rule inside another one paints over it.
        val owner = IntArray(length) { -1 }
        val order = rules.indices.sortedWith(
            compareBy<Int>({ -(rules[it].end - rules[it].start) }, { rules[it].start }, { rules[it].number }),
        )
        for (id in order) {
            val piece = rules[id]
            for (position in piece.start until piece.end) owner[position] = id
        }
        val spans = ArrayList<TajweedSpan>()
        var position = 0
        while (position < length) {
            val id = owner[position]
            if (id < 0) {
                position++
                continue
            }
            var next = position + 1
            while (next < length && owner[next] == id) next++
            val piece = rules[id]
            spans += TajweedSpan(piece.rule!!, position until next, piece.number)
            position = next
        }
        return spans
    }

    private class OpenRule(val rule: TajweedRule?, val start: Int, val number: Int) {
        fun close(end: Int) = Piece(rule, start, end, number)
    }

    private class Piece(val rule: TajweedRule?, val start: Int, val end: Int, val number: Int)
}
