package tj.app.quran_todo.ui.reader

import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.ui.state.LastReadPosition

/**
 * Index (in ayah order) of the ayah a reader opens on, decided before the first composition so the list
 * starts in the right place with no visible scroll:
 * the requested ayah, else the start of the requested range, else where the person stopped reading this
 * surah, else the beginning (0 means the header is shown).
 *
 * [ayahs] must be sorted by number in the surah. A legacy position only has the global ayah number.
 */
fun resolveInitialAyahIndex(
    surahNumber: Int,
    requestedAyah: Int?,
    rangeStart: Int?,
    lastRead: LastReadPosition?,
    ayahs: List<AyahEntity>,
): Int {
    fun byNumberInSurah(number: Int?): Int? =
        number?.let { n -> ayahs.indexOfFirst { it.numberInSurah == n }.takeIf { it >= 0 } }

    byNumberInSurah(requestedAyah)?.let { return it }
    byNumberInSurah(rangeStart)?.let { return it }
    if (lastRead != null && lastRead.surah == surahNumber) {
        byNumberInSurah(lastRead.ayahInSurah)?.let { return it }
        lastRead.legacyGlobalAyah?.let { global ->
            ayahs.indexOfFirst { it.number == global }.takeIf { it >= 0 }?.let { return it }
        }
    }
    return 0
}

/**
 * Text that [copy and share][ayahShareText] put out for one ayah: Arabic, translation (when there is one) and
 * the reference "Quran 2:255".
 */
fun ayahShareText(
    arabic: String,
    translation: String?,
    quranLabel: String,
    surahNumber: Int,
    ayahNumberInSurah: Int,
): String = buildString {
    append(arabic.trim())
    translation?.trim()?.takeIf { it.isNotEmpty() }?.let {
        append("\n\n")
        append(it)
    }
    append("\n\n— ")
    append(quranLabel)
    append(' ')
    append(surahNumber)
    append(':')
    append(ayahNumberInSurah)
}

/** "1:05" style time of the audio position. */
fun formatAudioTime(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
