package tj.app.quran_todo.ui.reader

import tj.app.quran_todo.ui.home.QuranMeta
import tj.app.quran_todo.ui.home.SurahSearch

/** Where the sheet "Go to" sends the person: a surah and an ayah inside it. */
data class GoToTarget(val surah: Int, val ayah: Int)

/**
 * The rules of the ayah field of the "Go to" sheet (task U21): the number is limited to the ayah count of the chosen surah, a change of the
 * surah moves the limit (and pulls a number that no longer fits back to the new last ayah), and a reference `2:255` typed into the field
 * names the surah too. Pure functions, no state: the sheet keeps the text.
 */
internal object GoToRules {
    /** The longest text the field takes ("114:286" is 7 characters). */
    const val MaxLength = 7

    private const val Separators = ":.,/ "

    /** The last ayah that may be typed for [surah]. */
    fun limit(surah: Int): Int = QuranMeta.ayahCount(surah).coerceAtLeast(1)

    /**
     * What the field shows after the person typed [raw]: Arabic-Indic digits become ASCII, anything but digits and one separator goes, and a
     * plain number above the limit of [surah] becomes the limit. A reference with a separator is left as typed until [resolve] reads it.
     */
    fun sanitize(surah: Int, raw: String): String {
        val kept = StringBuilder()
        var separators = 0
        for (ch in raw) {
            val digit = when (ch) {
                in '0'..'9' -> ch
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> null
            }
            when {
                digit != null -> kept.append(digit)
                ch in Separators && separators == 0 && kept.isNotEmpty() -> {
                    separators++
                    kept.append(':')
                }
            }
            if (kept.length >= MaxLength) break
        }
        return clampPlain(surah, kept.toString())
    }

    /** The ayah text after the surah changed to [surah]: a plain number is kept inside the new limit, a reference is left alone. */
    fun onSurahChanged(surah: Int, text: String): String = clampPlain(surah, text)

    /**
     * The place [text] points at while [surah] is chosen: a plain number is an ayah of [surah] (1..limit); `s:a` names its own surah and
     * must be a real ayah of the Quran. Null when the field is empty or points nowhere (the button stays off).
     */
    fun resolve(surah: Int, text: String): GoToTarget? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains(':')) {
            return SurahSearch.parseAyahReference(trimmed)?.let { GoToTarget(it.surah, it.ayah) }
        }
        val number = trimmed.toIntOrNull() ?: return null
        if (surah !in 1..QuranMeta.SurahCount || number !in 1..limit(surah)) return null
        return GoToTarget(surah, number)
    }

    /** A plain number is cut to the limit of [surah]; a reference (with a colon) and an empty text stay as they are. */
    private fun clampPlain(surah: Int, text: String): String {
        if (text.contains(':')) return text
        val number = text.toIntOrNull() ?: return text
        return number.coerceAtMost(limit(surah)).toString()
    }
}
