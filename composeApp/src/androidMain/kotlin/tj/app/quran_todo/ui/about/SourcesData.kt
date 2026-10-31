package tj.app.quran_todo.ui.about

import tj.app.quran_todo.data.translation.TranslationCatalog
import tj.app.quran_todo.data.translation.TranslationOrigin

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
            // Similar verses (task F6): our own index, made from the Tanzil text above; no outside list is used.
            SourceItem(
                title = "Similar verses: computed by the app from the Tanzil text",
                provider = "scripts/build_mutashabihat.py",
            ),
            SourceItem(
                title = "Tajweed markup (quran-tajweed)",
                provider = "$IslamicNetwork · api.alquran.cloud · quran-tajweed",
                url = "https://alquran.cloud",
            ),
        ),
    ),
    SourceGroup(
        SourceGroupId.Translations,
        // Every edition of the choice of the translation (task F4a) with its author, in the language of the edition: the list is made
        // from `TranslationCatalog`, so an edition that is added there is named here at once.
        TranslationCatalog.all.map { edition ->
            SourceItem(
                title = "${edition.language.displayName} · ${edition.name.of(edition.language)}",
                author = edition.author.of(edition.language),
                provider = when (edition.origin) {
                    TranslationOrigin.ALQURAN_CLOUD -> "$IslamicNetwork · api.alquran.cloud · ${edition.remoteId}"
                    // The terms of QuranEnc ask for the source and the version.
                    TranslationOrigin.QURAN_ENC -> "QuranEnc.com · quranenc.com · ${edition.remoteId} · v${edition.version}"
                    TranslationOrigin.QURAN_COM -> "$QuranCom · api.quran.com · translation ${edition.remoteId}"
                },
                url = when (edition.origin) {
                    TranslationOrigin.ALQURAN_CLOUD -> "https://alquran.cloud"
                    TranslationOrigin.QURAN_ENC -> "https://quranenc.com"
                    TranslationOrigin.QURAN_COM -> "https://quran.com"
                },
                note = SourceNote.TranslatorRights,
            )
        } + listOf(
            // The commentary and the meanings of the words (task F4b): both from quran.com, fetched when a person opens them.
            SourceItem(
                title = "Tafsir: as-Sa'di (Russian), Ibn Kathir (abridged, English), At-Tafsir al-Muyassar (Arabic)",
                provider = "$QuranCom · api.quran.com",
                url = "https://quran.com",
            ),
            SourceItem(
                title = "Al-Mukhtasar fi Tafsir al-Quran (Russian, English, Turkish, Uzbek in Cyrillic): Center for Tafsir Studies",
                provider = "QuranEnc.com · quranenc.com (as published; the version is shown under the text when the source gives one)",
                url = "https://quranenc.com",
            ),
            SourceItem(
                title = "Word meanings (in English)",
                author = "Quran Foundation",
                provider = "$QuranCom · api.quran.com",
                url = "https://quran.com",
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
            SourceItem(
                title = "Abdur-Rahman As-Sudais, Abu Bakr Ash-Shatri, Hani Ar-Rifai, Saud Ash-Shuraim",
                provider = "$QuranCom · verses.quran.com",
                url = "https://quran.com",
                note = SourceNote.ReciterRights,
            ),
            SourceItem(
                title = "Muhammad Al-Tablawi, Mahmoud Khalil Al-Husary (Muallim)",
                provider = "quranicaudio.com · mirrors.quranicaudio.com",
                url = "https://quranicaudio.com",
                note = SourceNote.ReciterRights,
            ),
            SourceItem(
                title = "Abdul Basit Abdus-Samad (Mujawwad), Muhammad Siddiq Al-Minshawi (Mujawwad)",
                provider = "$QuranCom · verses.quran.com",
                url = "https://quran.com",
                note = SourceNote.ReciterRights,
            ),
            SourceItem(
                title = "Word timings of the recitations (recitations, segments)",
                author = "Quran Foundation",
                provider = "$QuranCom · api.quran.com",
                url = "https://quran.com",
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
            SourceItem("Aref Ruqaa", provider = GoogleFonts, url = "https://fonts.google.com/specimen/Aref+Ruqaa", license = Ofl),
            SourceItem(
                title = "KFGQPC Uthmanic Hafs (Madinah Mushaf)",
                author = "King Fahd Glorious Quran Printing Complex",
                url = "https://qurancomplex.gov.sa",
            ),
            // The pages of the mushaf (task F3a): the lines of every page come from quran.com, the glyphs from the page fonts it serves.
            SourceItem(
                title = "Mushaf pages: layout and page fonts (KFGQPC, v1)",
                author = "King Fahd Glorious Quran Printing Complex",
                provider = "$QuranCom · api.quran.com (layout) · verses.quran.com (fonts)",
                url = "https://quran.com",
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
