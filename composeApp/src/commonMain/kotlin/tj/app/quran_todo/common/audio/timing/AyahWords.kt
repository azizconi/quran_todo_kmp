package tj.app.quran_todo.common.audio.timing

/**
 * The words of the text of an ayah as the screen draws it: the range of characters of every word, in reading order. The index of a
 * range is the word index of [WordSpan] (quran.com numbers the words of an ayah the same way: checked on all 6236 ayahs).
 *
 * A word is a run of characters between spaces that has a letter in it. A pause or sajda sign that the text keeps as a token of its
 * own (the small ۖ ۚ ۗ ۛ marks, ۩) has no letter, is not a word of quran.com either and is skipped; one that is glued to a word stays part
 * of that word. The text must be the one that is drawn: with the Bismillah of the first ayah cut off and the rub el hizb sign hidden
 * (`splitBismillah` of the reader), otherwise the indices are off by the words that are not drawn.
 */
fun wordRanges(text: String): List<IntRange> {
    val ranges = ArrayList<IntRange>()
    var index = 0
    while (index < text.length) {
        while (index < text.length && text[index].isWordSpace()) index++
        if (index >= text.length) break
        val start = index
        var hasLetter = false
        while (index < text.length && !text[index].isWordSpace()) {
            if (text[index].isLetter()) hasLetter = true
            index++
        }
        if (hasLetter) ranges += start until index
    }
    return ranges
}

/** The index of the word whose range holds [offset], or -1. */
fun wordIndexAt(ranges: List<IntRange>, offset: Int): Int = ranges.indexOfFirst { offset in it }

private fun Char.isWordSpace(): Boolean = isWhitespace() || this == '​' || this == '﻿'
