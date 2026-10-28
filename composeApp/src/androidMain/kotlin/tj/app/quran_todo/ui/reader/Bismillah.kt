package tj.app.quran_todo.ui.reader

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
 * In the `quran-uthmani` edition (api.alquran.cloud) the first ayah of surahs 2-114, except 9, starts with
 * the Bismillah: "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ". The reader shows the Bismillah on its own line, so it is
 * cut off here: the text of the ayah without its first four words.
 *
 * The first word is compared without harakat, Quranic annotation marks and tatweel, so Unicode variants of
 * the same spelling match. Anything else (other ayahs, Al-Fatiha, At-Tawbah, a text that does not start with
 * "بسم", or a text that would be left empty) is returned unchanged.
 */
fun splitBismillah(surahNumber: Int, ayahNumberInSurah: Int, text: String): String {
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
