package tj.app.quran_todo.ui.progress

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.common.SurahNames

/**
 * The names of the 114 surahs by the one naming rule of the Android UI (the same as the home list and the reader header), from the
 * shared [HomeViewModel] and the language of the app. Shared by the progress, the detailed analytics and "My notes".
 */
internal object SurahNameSource {
    /** What the names are made of; compared by reference so unrelated updates of the shared view model are ignored. */
    private class NameData(
        val surahList: List<SurahModel>,
        val chapterNames: Map<Int, ChapterNameModel>,
        val completeQuran: List<SurahWithAyahs>,
    )

    /** The 114 names; index 0 is surah 1. A flow per caller, each with its own cache of the English names. */
    fun names(home: HomeViewModel, language: Flow<AppLanguage>): Flow<List<String>> {
        var cache: Pair<List<SurahWithAyahs>, Map<Int, String>>? = null
        fun english(completeQuran: List<SurahWithAyahs>): Map<Int, String> {
            cache?.takeIf { it.first === completeQuran }?.let { return it.second }
            val names = completeQuran.associate { it.surah.number to it.surah.englishName }
            cache = completeQuran to names
            return names
        }
        return combine(
            home.uiState
                .map { NameData(it.surahList, it.chapterNames, it.completeQuran) }
                .distinctUntilChanged { a, b ->
                    a.surahList === b.surahList && a.chapterNames === b.chapterNames && a.completeQuran === b.completeQuran
                },
            language,
        ) { data, lang ->
            val russian = data.surahList.associateBy { it.surahNumber }
            val english = english(data.completeQuran)
            List(QuranFacts.SURAH_COUNT) { index ->
                val number = index + 1
                val ru = russian[number]
                val fallback = english[number]?.takeIf { it.isNotBlank() }
                    ?: ru?.name?.let(SurahNames::cleanRussianName)
                    ?: "Surah $number"
                SurahNames.displayName(
                    language = lang,
                    russianName = ru?.name,
                    transliteration = data.chapterNames[number]?.transliteration,
                    fallback = fallback,
                )
            }
        }
    }
}
