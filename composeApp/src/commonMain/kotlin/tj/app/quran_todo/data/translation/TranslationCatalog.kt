package tj.app.quran_todo.data.translation

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code

/** Where the text of an edition comes from. The source stays behind this enum: the rest of the app asks the repository. */
enum class TranslationOrigin {
    /** `api.alquran.cloud`: `GET /v1/surah/{n}/{id}`, and the whole Quran with `GET /v1/quran/{id}`. */
    ALQURAN_CLOUD,

    /** `api.quran.com` v4: `GET /quran/translations/{id}` (`?chapter_number=n` for one surah). */
    QURAN_COM,

    /**
     * `quranenc.com`: `GET /api/v1/translation/sura/{key}/{sura}`; there is no request for the whole Quran, so a whole edition is 114
     * requests. Its terms: the text is shown unchanged, the source (QuranEnc.com) and the version are named, the latest version is used.
     */
    QURAN_ENC,
}

/** The script of an edition: the interface of Uzbek and Turkish is Latin, so a Cyrillic edition of them is marked in the sheet. */
enum class TranslationScript { LATIN, CYRILLIC }

/** A text in the five languages of the interface. */
class LocalizedText(val en: String, val ru: String, val tg: String, val uz: String, val tr: String) {
    fun of(language: AppLanguage): String = when (language) {
        AppLanguage.EN -> en
        AppLanguage.RU -> ru
        AppLanguage.TG -> tg
        AppLanguage.UZ -> uz
        AppLanguage.TR -> tr
    }

    val all: List<String> get() = listOf(en, ru, tg, uz, tr)
}

/**
 * One translation of the Quran that can be shown under the ayah.
 *
 * @property key the value of the column `languageCode` of the table `ayah_translation`: the code of the language for the edition
 *   that has always been used for it (`ru`, `en`, `uz`...: the cache of the people who have the app already stays valid), the
 *   identifier of the source for the others (`ru.abuadel`, `qc:139`).
 * @property language the language of the text.
 * @property remoteId the identifier at the [origin]: an edition of alquran.cloud (`ru.abuadel`) or a resource of quran.com (`139`).
 * @property wireKb how much is downloaded for the whole edition (kilobytes on the wire, measured 2026-10-31 with gzip).
 * @property version the version of the edition at its source (QuranEnc numbers them: `1.0.2`); null where the source has none. It is
 *   named in the sheet and in "Sources", and kept with a downloaded edition: an edition stored in an older version says "update available".
 */
class TranslationEdition(
    val key: String,
    val language: AppLanguage,
    val origin: TranslationOrigin,
    val remoteId: String,
    val name: LocalizedText,
    val author: LocalizedText,
    val script: TranslationScript,
    val wireKb: Int,
    val version: String? = null,
) {
    /** The text is in a script other than the one of the interface language: the sheet says "Cyrillic" next to it. */
    val scriptDiffersFromInterface: Boolean
        get() = script == TranslationScript.CYRILLIC && language != AppLanguage.RU && language != AppLanguage.TG
    /** Roughly how many bytes the download of the whole edition is. */
    val wireBytes: Long get() = wireKb * 1024L

    /** The edition the language has had so far; its key is the code of the language. */
    val isDefault: Boolean get() = key == language.code
}

/**
 * The editions of the translation, by language of the text, the popular ones first. Where an edition exists at both sources,
 * alquran.cloud is used. The order of the sources: alquran.cloud, QuranEnc, quran.com; an edition that both QuranEnc and
 * quran.com have is taken from QuranEnc. Uzbek: the Latin translation of Muhammad Sodiq is the default; the other Uzbek editions
 * (quran.com 101, 127; QuranEnc `uzbek_rwwad`, `uzbek_mansour`) are Cyrillic: the two of QuranEnc are offered with the mark
 * "Cyrillic", the two of quran.com are not (they are the same texts as `uzbek_mansour` and a copy of Sodiq).
 */
object TranslationCatalog {
    private fun l(en: String, ru: String = en, tg: String = ru, uz: String = en, tr: String = en) = LocalizedText(en, ru, tg, uz, tr)

    private fun qe(
        id: String,
        language: AppLanguage,
        name: LocalizedText,
        author: LocalizedText,
        kb: Int,
        version: String,
        script: TranslationScript = scriptOf(language),
    ) = TranslationEdition("qe:$id", language, TranslationOrigin.QURAN_ENC, id, name, author, script, kb, version)

    private fun scriptOf(language: AppLanguage) =
        if (language == AppLanguage.RU || language == AppLanguage.TG) TranslationScript.CYRILLIC else TranslationScript.LATIN

    private fun aq(
        id: String,
        language: AppLanguage,
        name: LocalizedText,
        author: LocalizedText,
        kb: Int,
        default: Boolean = false,
    ) = TranslationEdition(if (default) language.code else id, language, TranslationOrigin.ALQURAN_CLOUD, id, name, author, scriptOf(language), kb)

    private fun qc(
        id: Int,
        language: AppLanguage,
        name: LocalizedText,
        author: LocalizedText,
        kb: Int,
        default: Boolean = false,
    ) = TranslationEdition(if (default) language.code else "qc:$id", language, TranslationOrigin.QURAN_COM, id.toString(), name, author, scriptOf(language), kb)

    private val russian = listOf(
        aq(
            "ru.kuliev", AppLanguage.RU, l("Kuliev", "Кулиев", "Қулиев", "Quliyev", "Kuliyev"),
            l("Elmir Kuliev", "Эльмир Кулиев", "Элмир Қулиев", "Elmir Quliyev", "Elmir Kuliyev"), 440, default = true,
        ),
        aq(
            "ru.abuadel", AppLanguage.RU, l("Abu Adel", "Абу Адель", "Абу Адел", "Abu Adel", "Ebu Adel"),
            l("Abu Adel", "Абу Адель", "Абу Адел", "Abu Adel", "Ebu Adel"), 826,
        ),
        aq(
            "ru.muntahab", AppLanguage.RU, l("Al-Muntakhab", "Аль-Мунтахаб", "Ал-Мунтахаб", "Al-Muntaxab", "el-Müntehab"),
            l("Ministry of Awqaf of Egypt", "Министерство вакфов Египта", "Вазорати авқофи Миср", "Misr avqof vazirligi", "Mısır Evkaf Bakanlığı"), 899,
        ),
        aq(
            "ru.osmanov", AppLanguage.RU, l("Osmanov", "Османов", "Османов", "Osmanov", "Osmanov"),
            l("Magomed-Nuri Osmanov", "Магомед-Нури Османов", "Магомед-Нурӣ Османов", "Magomed-Nuri Osmanov", "Magomed-Nuri Osmanov"), 482,
        ),
        aq(
            "ru.porokhova", AppLanguage.RU, l("Porokhova", "Порохова", "Порохова", "Porohova", "Porohova"),
            l("V. Porokhova", "В. Порохова", "В. Порохова", "V. Porohova", "V. Porohova"), 523,
        ),
        aq(
            "ru.krachkovsky", AppLanguage.RU, l("Krachkovsky", "Крачковский", "Крачковский", "Krachkovskiy", "Kraçkovski"),
            l("Ignaty Krachkovsky", "Игнатий Крачковский", "Игнатий Крачковский", "Ignatiy Krachkovskiy", "Ignati Kraçkovski"), 407,
        ),
        aq(
            "ru.sablukov", AppLanguage.RU, l("Sablukov", "Саблуков", "Саблуков", "Sablukov", "Sablukov"),
            l("Gordy Sablukov", "Гордий Саблуков", "Гордий Саблуков", "Gordiy Sablukov", "Gordi Sablukov"), 447,
        ),
    )

    private val turkish = listOf(
        aq(
            "tr.diyanet", AppLanguage.TR, l("Diyanet", "Диянет", "Диёнат", "Diyanet", "Diyanet İşleri"),
            l(
                "Presidency of Religious Affairs (Diyanet)", "Управление по делам религий Турции", "Раёсати умури динии Туркия",
                "Turkiya Diniy ishlar boshqarmasi", "Diyanet İşleri Başkanlığı",
            ),
            361,
        ),
        aq(
            "tr.yazir", AppLanguage.TR, l("Elmalılı", "Эльмалылы", "Элмалылӣ", "Elmalili", "Elmalılı"),
            l("Elmalılı Hamdi Yazır", "Эльмалылы Хамди Языр", "Элмалылӣ Ҳамдӣ Язир", "Elmalili Hamdi Yazir", "Elmalılı Hamdi Yazır"), 402,
            default = true,
        ),
        aq(
            "tr.vakfi", AppLanguage.TR, l("Diyanet Foundation", "Фонд Диянет", "Бунёди Диёнат", "Diyanet Vakfi", "Diyanet Vakfı"),
            l("Diyanet Foundation", "Фонд Диянет", "Бунёди Диёнат", "Diyanet Vakfi", "Diyanet Vakfı"), 403,
        ),
        aq(
            "tr.ates", AppLanguage.TR, l("Süleyman Ateş", "Сулейман Атеш", "Сулаймон Атеш", "Sulaymon Ateş", "Süleyman Ateş"),
            l("Süleyman Ateş", "Сулейман Атеш", "Сулаймон Атеш", "Sulaymon Ateş", "Süleyman Ateş"), 396,
        ),
        aq(
            "tr.bulac", AppLanguage.TR, l("Ali Bulaç", "Али Булач", "Алӣ Булач", "Ali Bulaç", "Ali Bulaç"),
            l("Ali Bulaç", "Али Булач", "Алӣ Булач", "Ali Bulaç", "Ali Bulaç"), 406,
        ),
        aq(
            "tr.golpinarli", AppLanguage.TR, l("Gölpınarlı", "Гёльпынарлы", "Гёлпинарлӣ", "Gölpınarlı", "Gölpınarlı"),
            l("Abdülbaki Gölpınarlı", "Абдулбаки Гёльпынарлы", "Абдулбоқӣ Гёлпинарлӣ", "Abdülbaki Gölpınarlı", "Abdülbâki Gölpınarlı"), 379,
        ),
        aq(
            "tr.ozturk", AppLanguage.TR, l("Yaşar Nuri Öztürk", "Яшар Нури Озтюрк", "Яшар Нурӣ Озтурк", "Yaşar Nuri Öztürk", "Yaşar Nuri Öztürk"),
            l("Yaşar Nuri Öztürk", "Яшар Нури Озтюрк", "Яшар Нурӣ Озтурк", "Yaşar Nuri Öztürk", "Yaşar Nuri Öztürk"), 405,
        ),
        aq(
            "tr.yildirim", AppLanguage.TR, l("Suat Yıldırım", "Суат Йылдырым", "Суат Йилдирим", "Suat Yıldırım", "Suat Yıldırım"),
            l("Suat Yıldırım", "Суат Йылдырым", "Суат Йилдирим", "Suat Yıldırım", "Suat Yıldırım"), 494,
        ),
        aq(
            "tr.yuksel", AppLanguage.TR, l("Edip Yüksel", "Эдип Юксель", "Адип Юксел", "Edip Yüksel", "Edip Yüksel"),
            l("Edip Yüksel", "Эдип Юксель", "Адип Юксел", "Edip Yüksel", "Edip Yüksel"), 354,
        ),
        qc(
            210, AppLanguage.TR, l("Dar Al-Salam", "Дар ас-Салам", "Дор ус-Салом", "Dar as-Salam", "Dârüsselâm"),
            l("Dar Al-Salam Center", "Центр «Дар ас-Салам»", "Маркази «Дор ус-Салом»", "«Dar as-Salam» markazi", "Dârüsselâm Merkezi"), 297,
        ),
        qc(
            124, AppLanguage.TR, l("Muslim Shahin", "Муслим Шахин", "Муслим Шоҳин", "Muslim Shahin", "Müslim Şahin"),
            l("Muslim Shahin", "Муслим Шахин", "Муслим Шоҳин", "Muslim Shahin", "Müslim Şahin"), 316,
        ),
        qe(
            "turkish_rwwad", AppLanguage.TR, l("Rowwad Center", "Центр «Руввод»", "Маркази «Руввод»", "«Rowwad» markazi", "Rowwad Çeviri Merkezi"),
            l("Rowwad Translation Center", "Центр перевода «Руввод»", "Маркази тарҷумаи «Руввод»", "«Rowwad» tarjima markazi", "Rowwad Çeviri Merkezi"),
            951, "1.0.4",
        ),
        qe(
            "turkish_shaban", AppLanguage.TR, l("Shaban Britch", "Шабан Бритч", "Шабон Бритч", "Shaban Britch", "Şaban Britch"),
            l("Shaaban British", "Шабан Бритч", "Шабон Бритч", "Shaban Britch", "Şaban Britch"), 926, "1.1.0",
        ),
        qe(
            "turkish_shahin", AppLanguage.TR, l("Ali Özek and others", "Али Озек и другие", "Алӣ Озек ва дигарон", "Ali Özek va boshqalar", "Ali Özek ve arkadaşları"),
            l("Dr. Ali Özek and others", "Д-р Али Озек и другие", "Доктор Алӣ Озек ва дигарон", "Dr. Ali Özek va boshqalar", "Dr. Ali Özek ve arkadaşları"), 977, "1.0.0",
        ),
    )

    private val tajik = listOf(
        aq(
            "tg.ayati", AppLanguage.TG, l("Ayati", "Аяти", "Оятӣ", "Ayotiy", "Âyetî"),
            l("Abdolmohammad Ayati", "Абдолмухаммад Аяти", "Абдулмуҳаммад Оятӣ", "Abdolmuhammad Ayotiy", "Abdolmuhammed Âyetî"), 419,
            default = true,
        ),
        qc(
            139, AppLanguage.TG,
            l("Khawaja Mirof & Khawaja Mir", "Хаджа Мироф и Хаджа Мир", "Хоҷа Мироф ва Хоҷа Мир", "Xoja Mirof va Xoja Mir", "Hâce Mirof ve Hâce Mir"),
            l("Khawaja Mirof & Khawaja Mir", "Хаджа Мироф и Хаджа Мир", "Хоҷа Мироф ва Хоҷа Мир", "Xoja Mirof va Xoja Mir", "Hâce Mirof ve Hâce Mir"),
            514,
        ),
        qe(
            "tajik_arifi", AppLanguage.TG,
            l("Rowwad Center", "Центр «Руввод»", "Маркази «Руввод»", "«Rowwad» markazi", "Rowwad Çeviri Merkezi"),
            l("Rowwad Translation Center", "Центр перевода «Руввод»", "Маркази тарҷумаи «Руввод»", "«Rowwad» tarjima markazi", "Rowwad Çeviri Merkezi"),
            1197, "1.0.2",
        ),
    )

    private val uzbek = listOf(
        qc(
            55, AppLanguage.UZ, l("Muhammad Sodiq", "Мухаммад Содик", "Муҳаммад Содиқ", "Muhammad Sodiq", "Muhammed Sadık"),
            l(
                "Muhammad Sodiq Muhammad Yusuf", "Мухаммад Содик Мухаммад Юсуф", "Муҳаммад Содиқ Муҳаммад Юсуф",
                "Muhammad Sodiq Muhammad Yusuf", "Muhammed Sadık Muhammed Yusuf",
            ),
            272, default = true,
        ),
        qe(
            "uzbek_rwwad", AppLanguage.UZ, l("Rowwad Center", "Центр «Руввод»", "Маркази «Руввод»", "«Rowwad» markazi", "Rowwad Çeviri Merkezi"),
            l("Rowwad Translation Center", "Центр перевода «Руввод»", "Маркази тарҷумаи «Руввод»", "«Rowwad» tarjima markazi", "Rowwad Çeviri Merkezi"),
            1046, "1.0.4", script = TranslationScript.CYRILLIC,
        ),
        qe(
            "uzbek_mansour", AppLanguage.UZ, l("Alauddin Mansour", "Алоуддин Мансур", "Алоуддин Мансур", "Alouddin Mansur", "Alaaddin Mansur"),
            l("Alauddin Mansour", "Алоуддин Мансур", "Алоуддин Мансур", "Alouddin Mansur", "Alaaddin Mansur"),
            1424, "1.0.0", script = TranslationScript.CYRILLIC,
        ),
    )

    private val english = listOf(
        aq(
            "en.sahih", AppLanguage.EN, l("Saheeh International", "Сахих Интернешнл", "Саҳеҳ Интернешнл", "Saheeh International", "Saheeh International"),
            l("Saheeh International", "Сахих Интернешнл", "Саҳеҳ Интернешнл", "Saheeh International", "Saheeh International"), 360,
            default = true,
        ),
        qc(
            85, AppLanguage.EN, l("Abdel Haleem", "Абдель Халим", "Абдул Ҳалим", "Abdel Haleem", "Abdel Haleem"),
            l("M. A. S. Abdel Haleem", "М. А. С. Абдель Халим", "М. А. С. Абдул Ҳалим", "M. A. S. Abdel Haleem", "M. A. S. Abdel Haleem"), 266,
        ),
        qc(
            84, AppLanguage.EN, l("Taqi Usmani", "Таки Усмани", "Тақӣ Усмонӣ", "Taqi Usmoniy", "Takî Usmânî"),
            l("Mufti Taqi Usmani", "Муфтий Таки Усмани", "Муфтӣ Тақӣ Усмонӣ", "Mufti Taqi Usmoniy", "Müftü Takî Usmânî"), 292,
        ),
        aq(
            "en.yusufali", AppLanguage.EN, l("Yusuf Ali", "Юсуф Али", "Юсуф Алӣ", "Yusuf Ali", "Yusuf Ali"),
            l("Abdullah Yusuf Ali", "Абдулла Юсуф Али", "Абдуллоҳ Юсуф Алӣ", "Abdullah Yusuf Ali", "Abdullah Yusuf Ali"), 407,
        ),
        aq(
            "en.pickthall", AppLanguage.EN, l("Pickthall", "Пикталл", "Пикталл", "Pickthall", "Pickthall"),
            l("Marmaduke Pickthall", "Мармадьюк Пикталл", "Мармадюк Пикталл", "Marmaduke Pickthall", "Marmaduke Pickthall"), 361,
        ),
        aq(
            "en.hilali", AppLanguage.EN, l("Hilali & Khan", "Хилали и Хан", "Ҳилолӣ ва Хон", "Hilali va Khan", "Hilali ve Khan"),
            l(
                "Muhammad Taqi-ud-Din al-Hilali and Muhammad Muhsin Khan", "Мухаммад Такиюддин аль-Хилали и Мухаммад Мухсин Хан",
                "Муҳаммад Тақиуддин ал-Ҳилолӣ ва Муҳаммад Муҳсин Хон", "Muhammad Taqiyuddin al-Hilali va Muhammad Muhsin Khan",
                "Muhammed Takıyyüddin el-Hilali ve Muhammed Muhsin Han",
            ),
            452,
        ),
        aq(
            "en.itani", AppLanguage.EN, l("Clear Quran (Itani)", "Ясный Коран (Итани)", "Қуръони равшан (Итанӣ)", "Clear Quran (Itani)", "Clear Quran (Itani)"),
            l("Talal Itani", "Талал Итани", "Талал Итанӣ", "Talal Itani", "Talal Itani"), 337,
        ),
        aq(
            "en.asad", AppLanguage.EN, l("Asad", "Асад", "Асад", "Asad", "Esed"),
            l("Muhammad Asad", "Мухаммад Асад", "Муҳаммад Асад", "Muhammad Asad", "Muhammed Esed"), 463,
        ),
    )

    /** Every edition, by language of the text in the order of the pickers. */
    val all: List<TranslationEdition> = russian + turkish + tajik + uzbek + english

    private val byKey: Map<String, TranslationEdition> = all.associateBy { it.key }

    fun byKey(key: String?): TranslationEdition? = key?.let(byKey::get)

    /** The editions of one language of the text, the popular ones first. */
    fun editionsOf(language: AppLanguage): List<TranslationEdition> = all.filter { it.language == language }

    /** The edition the language of the interface had before there was a choice. */
    fun defaultFor(language: AppLanguage): TranslationEdition = all.first { it.language == language && it.isDefault }

    /** The picker, below "Other languages": every edition that is not in the language of the interface. */
    fun otherLanguages(language: AppLanguage): List<TranslationEdition> = all.filter { it.language != language }
}
