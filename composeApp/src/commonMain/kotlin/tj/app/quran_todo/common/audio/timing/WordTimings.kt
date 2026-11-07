package tj.app.quran_todo.common.audio.timing

/**
 * One word of an ayah and when its recitation sounds: [wordIndex] counts from 0 in the words of the ayah as the screens draw them
 * (see [wordRanges]), the times are milliseconds from the start of the ayah's file.
 */
data class WordSpan(val wordIndex: Int, val startMs: Int, val endMs: Int)

/**
 * The word timings of one surah recited by one voice (a quran.com recitation): for every ayah of the surah the [WordSpan]s of the
 * words that the timing knows, in reading order. Words the timing does not know are simply missing, and an ayah can be missing
 * altogether: whoever uses the timings must live with holes ([usableSpans], [wordAt]).
 *
 * Built by [WordTimingsFormat]; it is a plain value and has no idea where it came from (the network, the cache or the downloads).
 */
class WordTimings(
    val recitationId: Int,
    val surah: Int,
    private val byAyah: Map<Int, List<WordSpan>>,
) {
    /** The spans of ayah [ayahInSurah], empty when the timing has none for it. */
    fun spans(ayahInSurah: Int): List<WordSpan> = byAyah[ayahInSurah].orEmpty()

    /** The ayahs that have spans, with their spans. */
    val ayahs: Map<Int, List<WordSpan>> get() = byAyah

    val isEmpty: Boolean get() = byAyah.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is WordTimings && other.recitationId == recitationId && other.surah == surah && other.byAyah == byAyah

    override fun hashCode(): Int = (recitationId * 31 + surah) * 31 + byAyah.hashCode()
}

/** A timing that covers fewer than this share of the words of an ayah is too patchy to follow: that ayah gets no highlight. */
const val MIN_COVERAGE_PERCENT = 60

/**
 * The spans of an ayah that has [wordCount] words, when they can be followed; empty when they cannot:
 *  - a span of a word that the ayah does not have means that the timing and the text disagree about the words (quran.com's timing of
 *    10:1 and 13:1 counts the pause sign after the opening letters as a word in some recitations, so every later word is shifted): the
 *    highlight would land on the wrong words, so the ayah gets none;
 *  - fewer than [MIN_COVERAGE_PERCENT] percent of the words covered is too patchy to follow.
 * Merged segments are already expanded to one span per word by [WordTimingsFormat].
 */
fun usableSpans(spans: List<WordSpan>, wordCount: Int): List<WordSpan> {
    if (wordCount <= 0 || spans.isEmpty()) return emptyList()
    if (spans.any { it.wordIndex !in 0 until wordCount }) return emptyList()
    val covered = spans.mapTo(HashSet()) { it.wordIndex }.size
    return if (covered * 100 >= wordCount * MIN_COVERAGE_PERCENT) spans else emptyList()
}

/**
 * The word being recited at [positionMs] of the ayah's file: the word of the last span that has started, so a short gap between two
 * words (or a pause the reciter makes) keeps the highlight on the word that was just said instead of blinking. -1 before the first
 * word starts and for no spans. [spans] must be in the order [WordTimingsFormat] gives them (starts never go back).
 */
fun wordAt(spans: List<WordSpan>, positionMs: Long): Int {
    if (spans.isEmpty() || positionMs < spans[0].startMs) return -1
    var low = 0
    var high = spans.lastIndex
    while (low < high) {
        val middle = (low + high + 1) ushr 1
        if (spans[middle].startMs <= positionMs) low = middle else high = middle - 1
    }
    return spans[low].wordIndex
}

/** The start of the word [wordIndex] in [spans], or null when the timing does not know that word. */
fun startOfWord(spans: List<WordSpan>, wordIndex: Int): Int? = spans.firstOrNull { it.wordIndex == wordIndex }?.startMs
