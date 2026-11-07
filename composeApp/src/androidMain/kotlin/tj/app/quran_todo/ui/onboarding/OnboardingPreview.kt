package tj.app.quran_todo.ui.onboarding

import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.ui.settings.PreviewAyah

/** Ar-Rahman, the surah whose first ayahs the font step previews. */
private const val PreviewSurah = 55
private const val PreviewAyahCount = 4

/**
 * Ar-Rahman 1-4 for the font preview, used when the text is not loaded yet (a first launch without a
 * connection) or cannot be read.
 */
internal val FallbackRahmanAyahs: List<PreviewAyah> = listOf(
    PreviewAyah(1, "ٱلرَّحْمَٰنُ"),
    PreviewAyah(2, "عَلَّمَ ٱلْقُرْءَانَ"),
    PreviewAyah(3, "خَلَقَ ٱلْإِنسَٰنَ"),
    PreviewAyah(4, "عَلَّمَهُ ٱلْبَيَانَ"),
)

/** The first four ayahs of Ar-Rahman from the loaded Quran, or null when they are not all there. */
internal fun rahmanPreview(quran: List<SurahWithAyahs>): List<PreviewAyah>? {
    val surah = quran.firstOrNull { it.surah.number == PreviewSurah } ?: return null
    val ayahs = surah.ayahs
        .filter { it.numberInSurah in 1..PreviewAyahCount }
        .sortedBy { it.numberInSurah }
        .map { PreviewAyah(it.numberInSurah, if (it.numberInSurah == 1) stripBasmala(it.text) else it.text) }
    return ayahs.takeIf { it.size == PreviewAyahCount && it.none { ayah -> ayah.text.isBlank() } }
}

/** Al-Fatihah, the surah of the story slides: the first three ayahs are shown. */
private const val StorySurah = 1
private const val StoryAyahCount = 3

/**
 * Al-Fatihah 1-3 for the story slides, used while the text is not loaded yet (the slides never wait for it). The first ayah is the
 * bismillah, as in the `quran-uthmani` edition.
 */
internal val FallbackFatihaAyahs: List<PreviewAyah> = listOf(
    PreviewAyah(1, "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"),
    PreviewAyah(2, "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"),
    PreviewAyah(3, "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"),
)

/** The first three ayahs of Al-Fatihah from the loaded Quran, or null when they are not all there. */
internal fun fatihaPreview(quran: List<SurahWithAyahs>): List<PreviewAyah>? {
    val surah = quran.firstOrNull { it.surah.number == StorySurah } ?: return null
    val ayahs = surah.ayahs
        .filter { it.numberInSurah in 1..StoryAyahCount }
        .sortedBy { it.numberInSurah }
        .map { PreviewAyah(it.numberInSurah, it.text) }
    return ayahs.takeIf { it.size == StoryAyahCount && it.none { ayah -> ayah.text.isBlank() } }
}

private val Whitespace = Regex("[\\s\\u00A0\\u200B-\\u200F]+")

/** The four words of the Bismillah without marks: bismi, Allah, ar-Rahman, ar-Rahim. */
private val BasmalaSkeleton = listOf("بسم", "الله", "الرحمن", "الرحيم")

/**
 * In the `quran-uthmani` edition the first ayah of surahs 2-114 (except 9) starts with the Bismillah.
 * Returns [text] without that prefix; text that does not start with it, or is only it, is returned as is.
 * Words are compared without vowel marks and Quranic signs, with alef wasla read as alef, so variants
 * of the same letters match.
 */
internal fun stripBasmala(text: String): String {
    val words = text.trim().split(Whitespace).filter { it.isNotEmpty() }
    if (words.size <= BasmalaSkeleton.size) return text
    val startsWithBasmala = BasmalaSkeleton.indices.all { index -> skeleton(words[index]) == BasmalaSkeleton[index] }
    return if (startsWithBasmala) words.drop(BasmalaSkeleton.size).joinToString(" ") else text
}

private fun skeleton(word: String): String = buildString {
    for (char in word) {
        val code = char.code
        when {
            code in 0x064B..0x065F -> Unit // fathatan ... wavy hamza below, shadda, sukun
            code == 0x0670 -> Unit // superscript alef
            code in 0x06D6..0x06ED -> Unit // Quranic annotation signs
            code == 0x0640 -> Unit // tatweel
            code == 0x0671 -> append('ا') // alef wasla reads as alef
            else -> append(char)
        }
    }
}
