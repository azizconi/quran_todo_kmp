package tj.app.quran_todo.ui.reader

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.audio.timing.wordRanges

/** The Bismillah as the reader draws it on its own line above the first ayah. */
const val BismillahText = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

/** The Bismillah has four words; in the `quran-uthmani` edition they open the first ayah of most surahs. */
private const val BismillahWordCount = 4

/** First word of the Bismillah without any marks. */
private const val BismillahFirstWord = "بسم"

/**
 * True for the surahs whose reader shows the Bismillah as a separate line: every surah except Al-Fatiha
 * (the Bismillah is its first ayah, shown in the list like any other) and At-Tawbah (it has none).
 */
fun hasBismillahLine(surahNumber: Int): Boolean = surahNumber in 2..114 && surahNumber != 9

/**
 * The sign of the start of a quarter of a hizb, U+06DE (rub el hizb). The bundled text puts it at the start of 199 ayahs, followed
 * by a space ("۞ إِنَّ ٱللَّهَ ..."), and it looks like an eight-pointed star: the app does not draw stars, and the start of a
 * quarter is shown by the quiet line above the ayah (`MushafMarks`).
 */
const val RubElHizbSign = '\u06DE'

/**
 * What the screens draw for the ayah [ayahNumberInSurah] of [surahNumber]: the text of the bundled database prepared for
 * display, in one place for the reader, the lesson, "Check yourself", the sheets and the previews:
 *
 * 1. the Bismillah that opens the first ayah is cut off ([cutBismillah]), the reader shows it on its own line;
 * 2. the rub el hizb sign is hidden ([hideRubSign]).
 *
 * Only the display changes: the database text stays as it is, and copy and share use [cutBismillah] (the sign stays in them).
 */
fun splitBismillah(surahNumber: Int, ayahNumberInSurah: Int, text: String): String =
    hideRubSign(cutBismillah(surahNumber, ayahNumberInSurah, text))

/**
 * The text of an ayah as the screens draw it and the words in it: [text] is what [splitBismillah] gives, [ranges] the character range of
 * each word in reading order (what the word-by-word highlight and the tap on a word work with). The index of a range is the word index
 * of the timing of the recitation.
 */
@Immutable
class AyahWords(val text: String, val ranges: List<IntRange>) {
    val wordCount: Int get() = ranges.size
}

/**
 * [AyahWords] of the ayah [ayahNumberInSurah] of [surahNumber]: the display text of [splitBismillah] (the Bismillah of the first ayah
 * is cut off, the rub el hizb sign is hidden) and the words of it. A token that has no letter (a pause mark that stands alone)
 * is not a word. Checked against quran.com: the words of this text are the words of its recitation, one to one.
 */
fun displayWords(surahNumber: Int, ayahNumberInSurah: Int, text: String): AyahWords {
    val shown = splitBismillah(surahNumber, ayahNumberInSurah, text)
    return AyahWords(shown, wordRanges(shown))
}

/**
 * [text] without the rub el hizb sign and the space after it, for display. A text without the sign is returned as it is. The
 * sign is a display problem only: callers that copy or share an ayah keep the original text.
 */
fun hideRubSign(text: String): String {
    if (text.indexOf(RubElHizbSign) < 0) return text
    return buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            index++
            if (char == RubElHizbSign) {
                while (index < text.length && text[index].isSpace()) index++
            } else {
                append(char)
            }
        }
    }.trim { it.isSpace() }
}

/**
 * In the `quran-uthmani` edition (api.alquran.cloud) the first ayah of surahs 2-114, except 9, starts with
 * the Bismillah: "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ". The reader shows the Bismillah on its own line, so it is
 * cut off here: the text of the ayah without its first four words.
 *
 * The first word is compared without harakat, Quranic annotation marks and tatweel, so Unicode variants of
 * the same spelling match. Anything else (other ayahs, Al-Fatiha, At-Tawbah, a text that does not start with
 * "بسم", or a text that would be left empty) is returned unchanged. The rub el hizb sign is not touched here (see
 * [splitBismillah] for the text to draw).
 */
fun cutBismillah(surahNumber: Int, ayahNumberInSurah: Int, text: String): String {
    if (ayahNumberInSurah != 1 || !hasBismillahLine(surahNumber)) return text

    var cursor = 0
    var wordEnd = 0
    var firstWord = ""
    repeat(BismillahWordCount) { word ->
        while (cursor < text.length && text[cursor].isSpace()) cursor++
        if (cursor >= text.length) return text // fewer than four words
        val start = cursor
        while (cursor < text.length && !text[cursor].isSpace()) cursor++
        wordEnd = cursor
        if (word == 0) firstWord = text.substring(start, cursor)
    }
    if (stripMarks(firstWord) != BismillahFirstWord) return text

    val rest = text.substring(wordEnd).trimStart { it.isSpace() }
    return rest.ifEmpty { text }
}

private fun Char.isSpace(): Boolean = isWhitespace() || this == '​' || this == '﻿'

private fun stripMarks(word: String): String = buildString(word.length) {
    for (c in word) if (!c.isMark()) append(c)
}

/** Harakat (064B-065F), dagger alef (0670), tatweel (0640), Quranic annotation signs (06D6-06ED), joiners. */
private fun Char.isMark(): Boolean =
    this in 'ً'..'ٟ' ||
        this == 'ٰ' ||
        this == 'ـ' ||
        this in 'ۖ'..'ۭ' ||
        this == '‌' ||
        this == '‍'
