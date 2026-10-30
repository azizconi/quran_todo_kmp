package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts

/**
 * Where the verse-by-verse files of a [Reciter] come from. The one place that knows how a file address is built.
 */
sealed interface ReciterSource {
    /**
     * The Islamic Network CDN: `https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{globalAyah}.mp3`.
     *
     * @property edition CDN edition identifier (`api.alquran.cloud/v1/edition?format=audio`).
     * @property bitrate kbit/s folder of the CDN that really holds this edition (not every edition has every bitrate).
     */
    data class IslamicNetwork(val edition: String, val bitrate: Int) : ReciterSource

    /**
     * The file that quran.com lists for the recitation (`/recitations/{id}/by_chapter/{surah}` answers with a path per ayah).
     * [pathTemplate] is that path with the numbers replaced by `{sss}` (surah) and `{aaa}` (ayah), each of three digits:
     * `Sudais/mp3/{sss}{aaa}.mp3` is `https://verses.quran.com/Sudais/mp3/112001.mp3`. A template that starts with `//` names
     * another host (`//mirrors.quranicaudio.com/everyayah/...`, quran.com prints those protocol-relative) and is used with `https:`.
     */
    data class QuranCom(val pathTemplate: String) : ReciterSource {
        fun url(surah: Int, ayahInSurah: Int): String {
            val path = pathTemplate
                .replace("{sss}", surah.toString().padStart(3, '0'))
                .replace("{aaa}", ayahInSurah.toString().padStart(3, '0'))
            return if (path.startsWith("//")) "https:$path" else VERSES_BASE_URL + path
        }

        companion object {
            const val VERSES_BASE_URL = "https://verses.quran.com/"
        }
    }
}

/** The kinds of recitation the reciter list is grouped by. */
enum class ReciterGroup {
    /** Made for learning by heart: slow, with pauses to repeat after the reciter. */
    Teaching,

    /** Murattal: a measured, clear reading. */
    Murattal,

    /** Mujawwad: a melodic, drawn-out reading. */
    Mujawwad,
}

/**
 * A reciter whose verse-by-verse recordings the app plays.
 *
 * The one source of audio URLs and cache keys: nothing else builds them by hand.
 *
 * @property id stable identifier that is persisted in the preferences and in backups; never change an existing one.
 * @property source where the files come from.
 * @property recitationId the recitation of quran.com ([recitation list](https://api.quran.com/api/v4/resources/recitations)) whose
 *   word timings belong to this voice (the highlight follows the recitation word by word).
 * @property group the kind of recitation the list groups it under.
 */
class Reciter(
    val id: String,
    val source: ReciterSource,
    val recitationId: Int,
    val group: ReciterGroup,
    private val fullNames: Map<AppLanguage, String>,
    private val shortNames: Map<AppLanguage, String>,
) {
    /** Audio file of the ayah with the global number [globalAyah] (1..6236). */
    fun audioUrl(globalAyah: Int): String = when (val source = source) {
        is ReciterSource.IslamicNetwork -> islamicNetworkUrl(source, globalAyah)
        is ReciterSource.QuranCom -> {
            val (surah, ayah) = surahAndAyah(globalAyah)
            source.url(surah, ayah)
        }
    }

    /** Audio file of ayah [ayahInSurah] of [surah]. */
    fun audioUrl(surah: Int, ayahInSurah: Int): String =
        audioUrl(surah, ayahInSurah, QuranFacts.firstAyahNumber(surah) + ayahInSurah - 1)

    /**
     * Audio file of an ayah when both its place and its global number are known (the playlist of the player carries both): the
     * CDN is asked by the global number, quran.com by the place.
     */
    fun audioUrl(surah: Int, ayahInSurah: Int, globalAyah: Int): String = when (val source = source) {
        is ReciterSource.IslamicNetwork -> islamicNetworkUrl(source, globalAyah)
        is ReciterSource.QuranCom -> source.url(surah, ayahInSurah)
    }

    /**
     * Key of the ayah in the audio cache. The default reciter keeps the historical key
     * (`ayah_{surah}_{ayah}`) so audio downloaded before reciters existed is still found.
     */
    fun cacheKey(surahNumber: Int, ayahNumberInSurah: Int): String =
        if (id == Reciters.default.id) {
            ayahAudioCacheKey(surahNumber, ayahNumberInSurah)
        } else {
            "${id}_${surahNumber}_$ayahNumberInSurah"
        }

    /** Name for lists and the notification ("Mishary Alafasy"). */
    fun name(language: AppLanguage): String = fullNames[language] ?: fullNames.getValue(AppLanguage.EN)

    /** Name for one line next to other text ("Alafasy"). */
    fun shortName(language: AppLanguage): String = shortNames[language] ?: shortNames.getValue(AppLanguage.EN)

    override fun equals(other: Any?): Boolean = other is Reciter && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Reciter($id)"

    private fun islamicNetworkUrl(source: ReciterSource.IslamicNetwork, globalAyah: Int): String =
        "https://cdn.islamic.network/quran/audio/${source.bitrate}/${source.edition}/$globalAyah.mp3"

    private fun surahAndAyah(globalAyah: Int): Pair<Int, Int> {
        var surah = 1
        while (surah < QuranFacts.SURAH_COUNT && QuranFacts.firstAyahNumber(surah + 1) <= globalAyah) surah++
        return surah to (globalAyah - QuranFacts.firstAyahNumber(surah) + 1)
    }
}

/**
 * The reciters the app offers, in the order of the list: first the one made for learning, then the murattal readings (the
 * default one first), then the mujawwad ones.
 */
object Reciters {
    /** Mishary Alafasy: the voice of the app since its first version, a medium tempo. */
    val Alafasy = Reciter(
        id = "alafasy",
        source = ReciterSource.IslamicNetwork(edition = "ar.alafasy", bitrate = 128),
        recitationId = 7,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Mishary Alafasy",
            AppLanguage.RU to "Мишари Аль-Афаси",
            AppLanguage.TG to "Мишорӣ Алафосӣ",
            AppLanguage.UZ to "Mishari Alafasi",
            AppLanguage.TR to "Mişari el-Afasi",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Al-Afasy",
            AppLanguage.RU to "Аль-Афаси",
            AppLanguage.TG to "Ал-Афасӣ",
            AppLanguage.UZ to "Al-Afasi",
            AppLanguage.TR to "El-Afasi",
        ),
    )

    /** Mahmoud Khalil Al-Husary: the classic teaching recitation, slow and very clear. */
    val Husary = Reciter(
        id = "husary",
        source = ReciterSource.IslamicNetwork(edition = "ar.husary", bitrate = 128),
        recitationId = 6,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Mahmoud Khalil Al-Husary",
            AppLanguage.RU to "Махмуд Халиль аль-Хусари",
            AppLanguage.TG to "Маҳмуд Халил ал-Ҳусарӣ",
            AppLanguage.UZ to "Mahmud Xalil al-Husariy",
            AppLanguage.TR to "Mahmud Halil el-Husari",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Al-Husary",
            AppLanguage.RU to "Аль-Хусари",
            AppLanguage.TG to "Ал-Ҳусарӣ",
            AppLanguage.UZ to "Al-Husariy",
            AppLanguage.TR to "El-Husari",
        ),
    )

    /** Muhammad Siddiq Al-Minshawi: a calm, unhurried murattal. */
    val Minshawi = Reciter(
        id = "minshawi",
        source = ReciterSource.IslamicNetwork(edition = "ar.minshawi", bitrate = 128),
        recitationId = 9,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Muhammad Siddiq Al-Minshawi",
            AppLanguage.RU to "Мухаммад Сиддик аль-Минышави",
            AppLanguage.TG to "Муҳаммад Сиддиқ ал-Миншовӣ",
            AppLanguage.UZ to "Muhammad Siddiq al-Minshoviy",
            AppLanguage.TR to "Muhammed Siddik el-Minşâvî",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Al-Minshawi",
            AppLanguage.RU to "Аль-Минышави",
            AppLanguage.TG to "Ал-Миншовӣ",
            AppLanguage.UZ to "Al-Minshoviy",
            AppLanguage.TR to "El-Minşâvî",
        ),
    )

    /** Abdul Basit Abdus-Samad (murattal): a classic voice, a lively tempo. The CDN keeps this edition at 192 kbit/s. */
    val AbdulBasit = Reciter(
        id = "abdulbasit",
        source = ReciterSource.IslamicNetwork(edition = "ar.abdulbasitmurattal", bitrate = 192),
        recitationId = 2,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Abdul Basit Abdus-Samad",
            AppLanguage.RU to "Абдуль-Басит Абдус-Самад",
            AppLanguage.TG to "Абдулбосит Абдуссамад",
            AppLanguage.UZ to "Abdulbosit Abdussamad",
            AppLanguage.TR to "Abdülbasit Abdüssamed",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Abdul Basit",
            AppLanguage.RU to "Абдуль-Басит",
            AppLanguage.TG to "Абдулбосит",
            AppLanguage.UZ to "Abdulbosit",
            AppLanguage.TR to "Abdülbasit",
        ),
    )

    /** Abdur-Rahman As-Sudais, imam of the Sacred Mosque in Makkah: a clear, flowing murattal. */
    val Sudais = Reciter(
        id = "sudais",
        source = ReciterSource.QuranCom("Sudais/mp3/{sss}{aaa}.mp3"),
        recitationId = 3,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Abdur-Rahman As-Sudais",
            AppLanguage.RU to "Абдуррахман ас-Судейс",
            AppLanguage.TG to "Абдурраҳмон ас-Судайс",
            AppLanguage.UZ to "Abdurrahmon as-Sudays",
            AppLanguage.TR to "Abdurrahman es-Sudeys",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "As-Sudais",
            AppLanguage.RU to "Ас-Судейс",
            AppLanguage.TG to "Ас-Судайс",
            AppLanguage.UZ to "As-Sudays",
            AppLanguage.TR to "Es-Sudeys",
        ),
    )

    /** Abu Bakr Ash-Shatri: a soft, quiet murattal. */
    val Shatri = Reciter(
        id = "shatri",
        source = ReciterSource.QuranCom("Shatri/mp3/{sss}{aaa}.mp3"),
        recitationId = 4,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Abu Bakr Ash-Shatri",
            AppLanguage.RU to "Абу Бакр аш-Шатри",
            AppLanguage.TG to "Абубакр аш-Шотрӣ",
            AppLanguage.UZ to "Abu Bakr ash-Shatriy",
            AppLanguage.TR to "Ebubekir eş-Şâtırî",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Ash-Shatri",
            AppLanguage.RU to "Аш-Шатри",
            AppLanguage.TG to "Аш-Шотрӣ",
            AppLanguage.UZ to "Ash-Shatriy",
            AppLanguage.TR to "Eş-Şâtırî",
        ),
    )

    /** Hani Ar-Rifai: a tender, unhurried murattal. */
    val Rifai = Reciter(
        id = "rifai",
        source = ReciterSource.QuranCom("Rifai/mp3/{sss}{aaa}.mp3"),
        recitationId = 5,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Hani Ar-Rifai",
            AppLanguage.RU to "Хани ар-Рифаи",
            AppLanguage.TG to "Ҳонӣ ар-Рифоӣ",
            AppLanguage.UZ to "Hani ar-Rifoiy",
            AppLanguage.TR to "Hâni er-Rifâî",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Ar-Rifai",
            AppLanguage.RU to "Ар-Рифаи",
            AppLanguage.TG to "Ар-Рифоӣ",
            AppLanguage.UZ to "Ar-Rifoiy",
            AppLanguage.TR to "Er-Rifâî",
        ),
    )

    /** Saud Ash-Shuraim, imam of the Sacred Mosque in Makkah: a fast, strong murattal. */
    val Shuraym = Reciter(
        id = "shuraym",
        source = ReciterSource.QuranCom("Shuraym/mp3/{sss}{aaa}.mp3"),
        recitationId = 10,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Saud Ash-Shuraim",
            AppLanguage.RU to "Сауд аш-Шурейм",
            AppLanguage.TG to "Саъуд аш-Шурайм",
            AppLanguage.UZ to "Saud ash-Shuraym",
            AppLanguage.TR to "Suud eş-Şureym",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Ash-Shuraim",
            AppLanguage.RU to "Аш-Шурейм",
            AppLanguage.TG to "Аш-Шурайм",
            AppLanguage.UZ to "Ash-Shuraym",
            AppLanguage.TR to "Eş-Şureym",
        ),
    )

    /** Muhammad Al-Tablawi: a calm murattal in the Egyptian school. */
    val Tablawi = Reciter(
        id = "tablawi",
        source = ReciterSource.QuranCom("//mirrors.quranicaudio.com/everyayah/Mohammad_al_Tablaway_128kbps/{sss}{aaa}.mp3"),
        recitationId = 11,
        group = ReciterGroup.Murattal,
        fullNames = mapOf(
            AppLanguage.EN to "Muhammad Al-Tablawi",
            AppLanguage.RU to "Мухаммад ат-Таблави",
            AppLanguage.TG to "Муҳаммад ат-Таблавӣ",
            AppLanguage.UZ to "Muhammad at-Tablaviy",
            AppLanguage.TR to "Muhammed et-Tablâvî",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "At-Tablawi",
            AppLanguage.RU to "Ат-Таблави",
            AppLanguage.TG to "Ат-Таблавӣ",
            AppLanguage.UZ to "At-Tablaviy",
            AppLanguage.TR to "Et-Tablâvî",
        ),
    )

    /** Al-Husary "Muallim" (the teacher): his recitation for learning, slow, with a pause after every phrase to repeat it. */
    val HusaryMuallim = Reciter(
        id = "husary_muallim",
        source = ReciterSource.QuranCom("//mirrors.quranicaudio.com/everyayah/Husary_Muallim_128kbps/{sss}{aaa}.mp3"),
        recitationId = 12,
        group = ReciterGroup.Teaching,
        fullNames = mapOf(
            AppLanguage.EN to "Mahmoud Khalil Al-Husary · for learning",
            AppLanguage.RU to "Махмуд Халиль аль-Хусари · для заучивания",
            AppLanguage.TG to "Маҳмуд Халил ал-Ҳусарӣ · барои азбар кардан",
            AppLanguage.UZ to "Mahmud Xalil al-Husariy · yodlash uchun",
            AppLanguage.TR to "Mahmud Halil el-Husari · ezber için",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Al-Husary (learning)",
            AppLanguage.RU to "Аль-Хусари (заучивание)",
            AppLanguage.TG to "Ал-Ҳусарӣ (азбар кардан)",
            AppLanguage.UZ to "Al-Husariy (yodlash)",
            AppLanguage.TR to "El-Husari (ezber)",
        ),
    )

    /** Abdul Basit Abdus-Samad (mujawwad): the melodic, drawn-out reading. */
    val AbdulBasitMujawwad = Reciter(
        id = "abdulbasit_mujawwad",
        source = ReciterSource.QuranCom("AbdulBaset/Mujawwad/mp3/{sss}{aaa}.mp3"),
        recitationId = 1,
        group = ReciterGroup.Mujawwad,
        fullNames = mapOf(
            AppLanguage.EN to "Abdul Basit Abdus-Samad · mujawwad",
            AppLanguage.RU to "Абдуль-Басит Абдус-Самад · муджаввад",
            AppLanguage.TG to "Абдулбосит Абдуссамад · муҷаввад",
            AppLanguage.UZ to "Abdulbosit Abdussamad · mujavvad",
            AppLanguage.TR to "Abdülbasit Abdüssamed · mücevved",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Abdul Basit (mujawwad)",
            AppLanguage.RU to "Абдуль-Басит (муджаввад)",
            AppLanguage.TG to "Абдулбосит (муҷаввад)",
            AppLanguage.UZ to "Abdulbosit (mujavvad)",
            AppLanguage.TR to "Abdülbasit (mücevved)",
        ),
    )

    /** Muhammad Siddiq Al-Minshawi (mujawwad): the melodic, drawn-out reading. */
    val MinshawiMujawwad = Reciter(
        id = "minshawi_mujawwad",
        source = ReciterSource.QuranCom("Minshawi/Mujawwad/mp3/{sss}{aaa}.mp3"),
        recitationId = 8,
        group = ReciterGroup.Mujawwad,
        fullNames = mapOf(
            AppLanguage.EN to "Muhammad Siddiq Al-Minshawi · mujawwad",
            AppLanguage.RU to "Мухаммад Сиддик аль-Минышави · муджаввад",
            AppLanguage.TG to "Муҳаммад Сиддиқ ал-Миншовӣ · муҷаввад",
            AppLanguage.UZ to "Muhammad Siddiq al-Minshoviy · mujavvad",
            AppLanguage.TR to "Muhammed Siddik el-Minşâvî · mücevved",
        ),
        shortNames = mapOf(
            AppLanguage.EN to "Al-Minshawi (mujawwad)",
            AppLanguage.RU to "Аль-Минышави (муджаввад)",
            AppLanguage.TG to "Ал-Миншовӣ (муҷаввад)",
            AppLanguage.UZ to "Al-Minshoviy (mujavvad)",
            AppLanguage.TR to "El-Minşâvî (mücevved)",
        ),
    )

    /** The reciter used on every platform before the choice existed, and the fallback for an unknown saved id. */
    val default: Reciter get() = Alafasy

    /** In the order they are offered: the group of the teaching reciter, then murattal, then mujawwad. */
    val all: List<Reciter> = listOf(
        HusaryMuallim,
        Alafasy, Husary, Minshawi, AbdulBasit, Sudais, Shatri, Rifai, Shuraym, Tablawi,
        AbdulBasitMujawwad, MinshawiMujawwad,
    )

    /** The reciters of [group], in list order. */
    fun inGroup(group: ReciterGroup): List<Reciter> = all.filter { it.group == group }

    /** The reciter with [id], or [default] for a missing or unknown id. */
    fun byId(id: String?): Reciter = all.firstOrNull { it.id == id } ?: default
}
