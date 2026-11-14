package tj.app.quran_todo.ui.common

import tj.app.quran_todo.common.i18n.AppLanguage

/** One naming rule for surahs across the Android UI (home rows, reader header, progress map). */
object SurahNames {
    /**
     * Name shown as the surah title: the bundled Russian name for RU, otherwise the localized
     * transliteration from the chapter API, falling back to [fallback] (English name or "Surah N").
     */
    fun displayName(
        language: AppLanguage,
        russianName: String?,
        transliteration: String?,
        fallback: String,
    ): String {
        val latin = transliteration?.takeIf { it.isNotBlank() } ?: fallback
        return if (language == AppLanguage.RU) {
            russianName?.takeIf { it.isNotBlank() }?.let(::cleanRussianName) ?: latin
        } else {
            latin
        }
    }

    /**
     * The bundled Russian names are inconsistent ("Сура Мухаммад", "Сура Аль-Баляд", "Ал - Лайл",
     * lower-case "аль-Фатиха"). Drops the "Сура" word, closes up spaced hyphens and capitalizes.
     */
    fun cleanRussianName(raw: String): String {
        var name = raw.trim().replace(SpacedHyphen, "-")
        name = name.replace(SuraPrefix, "")
        return name.replaceFirstChar { it.uppercase() }
    }

    private val SpacedHyphen = Regex("""\s*-\s*""")
    private val SuraPrefix = Regex("""^сура\s+""", RegexOption.IGNORE_CASE)
}
