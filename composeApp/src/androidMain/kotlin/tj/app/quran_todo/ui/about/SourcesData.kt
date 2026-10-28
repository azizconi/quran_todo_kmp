package tj.app.quran_todo.ui.about

/**
 * Everything the "Sources" screen lists, in one place so it is easy to correct.
 *
 * Names of authors, editions, services and licenses are shown as the sources publish them and are
 * never translated; only the group titles and the [SourceNote] sentences are (`TrustCopy`).
 * When a translation, a reciter or a font is added to or removed from the app, change it here.
 */
internal enum class SourceGroupId { QuranText, Translations, SurahNames, Audio, Fonts, Libraries }

/** A sentence about the terms or the origin of a source; its text is localized in `TrustCopy`. */
internal enum class SourceNote { TanzilTerms, TranslatorRights, AppTeamNames, ReciterRights }

internal data class SourceItem(
    val title: String,
    /** Author, translator, reciter or vendor. */
    val author: String? = null,
    /** Where the app gets it: service, API host, edition identifier. */
    val provider: String? = null,
    /** Link shown on the row (https). */
    val url: String? = null,
    /** Short, untranslated license name such as "Apache 2.0". */
    val license: String? = null,
    val note: SourceNote? = null,
)

internal data class SourceGroup(val id: SourceGroupId, val items: List<SourceItem>)

private const val IslamicNetwork = "Islamic Network"
private const val QuranCom = "Quran.com"
private const val GoogleFonts = "Google Fonts"
private const val Ofl = "SIL OFL 1.1"
private const val Apache = "Apache 2.0"

internal val SourceGroups: List<SourceGroup> = listOf(
    SourceGroup(
        SourceGroupId.QuranText,
        listOf(
            SourceItem(
                title = "Tanzil Quran Text (Uthmani)",
                author = "Tanzil Project",
                provider = "$IslamicNetwork · api.alquran.cloud · quran-uthmani",
                url = "https://tanzil.net",
                note = SourceNote.TanzilTerms,
            ),
        ),
    ),
    SourceGroup(
        SourceGroupId.Translations,
        listOf(
            SourceItem(
                title = "Русский",
                author = "Эльмир Кулиев",
                provider = "$IslamicNetwork · api.alquran.cloud · ru.kuliev",
                url = "https://alquran.cloud",
                note = SourceNote.TranslatorRights,
            ),
            SourceItem(
                title = "English",
                author = "Saheeh International",
                provider = "$IslamicNetwork · api.alquran.cloud · en.sahih",
                url = "https://alquran.cloud",
                note = SourceNote.TranslatorRights,
            ),
            SourceItem(
                title = "Тоҷикӣ",
                author = "Абдулмуҳаммад Оятӣ",
                provider = "$IslamicNetwork · api.alquran.cloud · tg.ayati",
                url = "https://alquran.cloud",
                note = SourceNote.TranslatorRights,
            ),
            SourceItem(
                title = "O‘zbek",
                author = "Muhammad Sodiq Muhammad Yusuf",
                provider = "$QuranCom · api.quran.com",
                url = "https://quran.com",
                note = SourceNote.TranslatorRights,
            ),
            SourceItem(
                title = "Türkçe",
                author = "Elmalılı Hamdi Yazır",
                provider = "$IslamicNetwork · api.alquran.cloud · tr.yazir",
                url = "https://alquran.cloud",
                note = SourceNote.TranslatorRights,
            ),
        ),
    ),
    SourceGroup(
        SourceGroupId.SurahNames,
        listOf(
            SourceItem(
                title = "English, Русский, Türkçe",
                provider = "$QuranCom · api.quran.com",
                url = "https://quran.com",
            ),
            SourceItem(
                title = "Тоҷикӣ, O‘zbek",
                note = SourceNote.AppTeamNames,
            ),
        ),
    ),
    SourceGroup(
        SourceGroupId.Audio,
        listOf(
            SourceItem(
                title = "Mishary Rashid Alafasy",
                provider = "$IslamicNetwork CDN · cdn.islamic.network",
                url = "https://islamic.network",
                note = SourceNote.ReciterRights,
            ),
            SourceItem(
                title = "Mahmoud Khalil Al-Husary",
                provider = "$IslamicNetwork CDN · cdn.islamic.network",
                url = "https://islamic.network",
                note = SourceNote.ReciterRights,
            ),
            SourceItem(
                title = "Muhammad Siddiq Al-Minshawi",
                provider = "$IslamicNetwork CDN · cdn.islamic.network",
                url = "https://islamic.network",
                note = SourceNote.ReciterRights,
            ),
            SourceItem(
                title = "Abdul Basit Abdus-Samad",
                provider = "$IslamicNetwork CDN · cdn.islamic.network",
                url = "https://islamic.network",
                note = SourceNote.ReciterRights,
            ),
        ),
    ),
    SourceGroup(
        SourceGroupId.Fonts,
        listOf(
            SourceItem("Amiri Quran", provider = GoogleFonts, url = "https://fonts.google.com/specimen/Amiri+Quran", license = Ofl),
            SourceItem("Scheherazade New", provider = GoogleFonts, url = "https://fonts.google.com/specimen/Scheherazade+New", license = Ofl),
            SourceItem("Noto Naskh Arabic", provider = GoogleFonts, url = "https://fonts.google.com/noto/specimen/Noto+Naskh+Arabic", license = Ofl),
            SourceItem("Lateef", provider = GoogleFonts, url = "https://fonts.google.com/specimen/Lateef", license = Ofl),
            SourceItem("Noto Nastaliq Urdu", provider = GoogleFonts, url = "https://fonts.google.com/noto/specimen/Noto+Nastaliq+Urdu", license = Ofl),
            SourceItem("Aref Ruqaa Ink", provider = GoogleFonts, url = "https://fonts.google.com/specimen/Aref+Ruqaa+Ink", license = Ofl),
            SourceItem(
                title = "KFGQPC Uthmanic Hafs (Madinah Mushaf)",
                author = "King Fahd Glorious Quran Printing Complex",
                url = "https://qurancomplex.gov.sa",
            ),
            SourceItem(
                title = "Manrope",
                author = "Mikhail Sharanda",
                provider = GoogleFonts,
                url = "https://fonts.google.com/specimen/Manrope",
                license = Ofl,
            ),
        ),
    ),
    SourceGroup(
        SourceGroupId.Libraries,
        listOf(
            SourceItem("Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime", "JetBrains", url = "https://kotlinlang.org", license = Apache),
            SourceItem("Compose Multiplatform", "JetBrains", url = "https://www.jetbrains.com/compose-multiplatform/", license = Apache),
            SourceItem("AndroidX (Activity, Core, Lifecycle, Navigation)", "Google", url = "https://developer.android.com/jetpack/androidx", license = Apache),
            SourceItem("Room and SQLite", "Google", url = "https://developer.android.com/training/data-storage/room", license = Apache),
            SourceItem("Ktor", "JetBrains", url = "https://ktor.io", license = Apache),
            SourceItem("Koin", "Kotzilla and contributors", url = "https://insert-koin.io", license = Apache),
            SourceItem("Media3", "Google", url = "https://developer.android.com/media/media3", license = Apache),
            SourceItem("Firebase SDK (Analytics, Crashlytics)", "Google", url = "https://firebase.google.com", license = "$Apache · Firebase Terms of Service"),
            SourceItem("Calf", "Mohamed Rejeb", url = "https://github.com/MohamedRejeb/Calf", license = Apache),
            SourceItem("Decompose", "Arkadii Ivanov", url = "https://github.com/arkivanov/Decompose", license = Apache),
        ),
    ),
)

/** The host of [url] without scheme and path: "https://tanzil.net/docs" becomes "tanzil.net". */
internal fun displayHost(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
