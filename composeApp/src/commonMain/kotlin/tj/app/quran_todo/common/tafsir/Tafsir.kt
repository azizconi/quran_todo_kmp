package tj.app.quran_todo.common.tafsir

import tj.app.quran_todo.common.i18n.AppLanguage

/** Where a commentary comes from: quran.com (`tafsirs/{id}/by_chapter`) or QuranEnc.com (`translation/sura/{key}/{sura}`). */
enum class TafsirProvider { QuranCom, QuranEnc }

/**
 * The commentaries (tafsir) the app can show. [key] is what the settings and the backup file keep; [remoteId] is how the [provider] names
 * it (the number of quran.com, the key of QuranEnc); [languageCode] is the language the commentary is written in; [cyrillic] is a text in
 * Cyrillic script that the app shows as it is (the terms of QuranEnc forbid any change, so no transliteration).
 */
enum class TafsirSource(
    val key: String,
    val provider: TafsirProvider,
    val remoteId: String,
    val languageCode: String,
    val cyrillic: Boolean = false,
) {
    /** Abd ar-Rahman as-Sa'di, Russian (quran.com 170): plain text. */
    Saadi("saadi_ru", TafsirProvider.QuranCom, "170", "ru"),

    /** Ibn Kathir, abridged, English (quran.com 169): HTML with headings and paragraphs. */
    IbnKathir("ibn_kathir_en", TafsirProvider.QuranCom, "169", "en"),

    /** At-Tafsir al-Muyassar, Arabic (quran.com 16): short, with a little HTML. */
    Muyassar("muyassar_ar", TafsirProvider.QuranCom, "16", "ar"),

    /** Al-Mukhtasar fi Tafsir al-Quran, Russian (QuranEnc). */
    MokhtasarRu("mokhtasar_ru", TafsirProvider.QuranEnc, "russian_mokhtasar", "ru"),

    /** Al-Mukhtasar fi Tafsir al-Quran, English (QuranEnc). */
    MokhtasarEn("mokhtasar_en", TafsirProvider.QuranEnc, "english_mokhtasar", "en"),

    /** Al-Mukhtasar fi Tafsir al-Quran, Turkish (QuranEnc). */
    MokhtasarTr("mokhtasar_tr", TafsirProvider.QuranEnc, "turkish_mokhtasar", "tr"),

    /** Al-Mukhtasar fi Tafsir al-Quran, Uzbek, in Cyrillic script only (QuranEnc). */
    MokhtasarUz("mokhtasar_uz", TafsirProvider.QuranEnc, "uzbek_mokhtasar", "uz", cyrillic = true);

    companion object {
        /** The source of [key] (what [TafsirSource.key] wrote), or null for an unknown name. */
        fun byKey(key: String?): TafsirSource? = entries.firstOrNull { it.key == key }

        /**
         * The commentary shown first for the language of the app: Turkish and Uzbek readers get the Mukhtasar in their language (QuranEnc),
         * Russian and Tajik readers get as-Sa'di in Russian (there is none in Tajik), English readers get Ibn Kathir.
         */
        fun defaultFor(language: AppLanguage): TafsirSource = when (language) {
            AppLanguage.RU, AppLanguage.TG -> Saadi
            AppLanguage.UZ -> MokhtasarUz
            AppLanguage.TR -> MokhtasarTr
            AppLanguage.EN -> IbnKathir
        }

        /** The chips of the screen for the language of the app: the default first. */
        fun chipsFor(language: AppLanguage): List<TafsirSource> = when (language) {
            AppLanguage.RU -> listOf(Saadi, MokhtasarRu, IbnKathir, Muyassar)
            AppLanguage.TR -> listOf(MokhtasarTr, IbnKathir, MokhtasarEn, Muyassar)
            AppLanguage.UZ -> listOf(MokhtasarUz, Saadi, MokhtasarRu, IbnKathir)
            AppLanguage.TG -> listOf(Saadi, MokhtasarRu, IbnKathir)
            AppLanguage.EN -> listOf(IbnKathir, MokhtasarEn, Muyassar)
        }

        /** The commentary is written in the language of the app. */
        fun isInLanguageOf(source: TafsirSource, language: AppLanguage): Boolean = source.languageCode == when (language) {
            AppLanguage.RU -> "ru"
            AppLanguage.EN -> "en"
            AppLanguage.TG -> "tg"
            AppLanguage.UZ -> "uz"
            AppLanguage.TR -> "tr"
        }
    }
}

/** What the screen shows under the ayah: the [text] and the ayahs [first]..[last] it explains (more than one ayah for a group). */
data class TafsirPassage(val text: String, val first: Int, val last: Int) {
    val isGroup: Boolean get() = last > first
}

/**
 * The commentary of one surah: [texts] by number of the ayah (only ayahs that have a text), [ayahCount] the number of ayahs of the surah
 * (what quran.com listed).
 *
 * **Groups.** A commentary often explains several ayahs together. quran.com lists every ayah of the surah and gives the text to one of
 * the group only, the others have an empty `text` (as-Sa'di: 2:18 holds the text of 2:18-19 and 2:19 is empty; Ibn Kathir: 2:19 holds
 * the text of 2:19-20). The text of an ayah without one is therefore the text of the nearest ayah before it that has one (the nearest
 * after it for empty ayahs at the start of the surah), and the group is that ayah up to the one before the next ayah that has a text
 * ([passage]). Checked with `by_ayah`, whose `verses` lists the same group.
 */
class TafsirChapter(
    val surah: Int,
    val source: TafsirSource,
    val ayahCount: Int,
    texts: Map<Int, String>,
    /** The edition the text is from (QuranEnc gives a number); null where the source has none. Kept in the copy of the app. */
    val version: String? = null,
) {
    val texts: Map<Int, String> = texts.filterValues { it.isNotBlank() }
    private val heads: List<Int> = this.texts.keys.sorted()

    /** The text for [ayah] and the ayahs it covers, or null when the surah has no commentary at all or [ayah] is not in it. */
    fun passage(ayah: Int): TafsirPassage? {
        if (ayah !in 1..ayahCount || heads.isEmpty()) return null
        val head = heads.lastOrNull { it <= ayah } ?: heads.first()
        val next = heads.firstOrNull { it > head }
        val first = if (head == heads.first()) 1 else head
        val last = if (next == null) ayahCount else next - 1
        return TafsirPassage(texts.getValue(head), first, last)
    }
}

/** One page of the answer of `by_chapter`: the ayahs of it with their texts (empty ones too) and where the next page is. */
class TafsirPage(val entries: List<Pair<Int, String>>, val surah: Int, val nextPage: Int?, val totalRecords: Int?)
