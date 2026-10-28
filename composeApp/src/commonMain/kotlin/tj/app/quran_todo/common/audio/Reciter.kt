package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * A reciter whose verse-by-verse recordings are served by the Islamic Network CDN
 * (`https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{globalAyah}.mp3`).
 *
 * The one source of audio URLs and cache keys: nothing else builds them by hand.
 *
 * @property id stable identifier that is persisted in the preferences; never change an existing one.
 * @property edition CDN edition identifier (`api.alquran.cloud/v1/edition?format=audio`).
 * @property bitrate kbit/s folder of the CDN that really holds this edition (not every edition has every bitrate).
 */
class Reciter(
    val id: String,
    val edition: String,
    val bitrate: Int,
    private val fullNames: Map<AppLanguage, String>,
    private val shortNames: Map<AppLanguage, String>,
) {
    /** Audio file of the ayah with the global number [globalAyah] (1..6236). */
    fun audioUrl(globalAyah: Int): String =
        "https://cdn.islamic.network/quran/audio/$bitrate/$edition/$globalAyah.mp3"

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
}

/** The reciters the app offers. Every one of them is a murattal (measured, clear) recitation, not mujawwad. */
object Reciters {
    /** Mishary Alafasy: the voice of the app since its first version, a medium tempo. */
    val Alafasy = Reciter(
        id = "alafasy",
        edition = "ar.alafasy",
        bitrate = 128,
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
        edition = "ar.husary",
        bitrate = 128,
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
        edition = "ar.minshawi",
        bitrate = 128,
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
        edition = "ar.abdulbasitmurattal",
        bitrate = 192,
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

    /** The reciter used on every platform before the choice existed, and the fallback for an unknown saved id. */
    val default: Reciter get() = Alafasy

    /** In the order they are offered. */
    val all: List<Reciter> = listOf(Alafasy, Husary, Minshawi, AbdulBasit)

    /** The reciter with [id], or [default] for a missing or unknown id. */
    fun byId(id: String?): Reciter = all.firstOrNull { it.id == id } ?: default
}
