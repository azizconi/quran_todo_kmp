package tj.app.quran_todo.ui.tafsir

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.tafsir.TafsirChapter
import tj.app.quran_todo.common.tafsir.TafsirPassage
import tj.app.quran_todo.common.tafsir.TafsirSource
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.domain.repository.QuranReferenceRepository
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.reader.splitBismillah
import tj.app.quran_todo.ui.state.UiPrefs

/** The commentary of the surah for the chosen source: on its way, on the screen, or neither on the phone nor reachable. */
internal sealed interface TafsirLoad {
    data object Loading : TafsirLoad

    data class Ready(val chapter: TafsirChapter) : TafsirLoad

    data object Missing : TafsirLoad
}

/** What the screen shows: the ayah in our text, and the passage of the commentary that explains it (null: this source has none for it). */
internal data class TafsirView(
    val ayah: Int,
    val arabic: String,
    val translation: String?,
    val passage: TafsirPassage?,
)

/**
 * State of the commentary screen (one `Dest.Tafsir` back stack entry): the ayah that is open (the arrows move it inside the surah), the
 * source (the chosen one is remembered in [UiPrefs]), the commentary of the surah from the [TafsirStore], and the text and translation
 * of the ayah. The text of the Quran and the names come from the shared view models (through the constructor, never `koinViewModel`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TafsirViewModel(
    val route: Dest.Tafsir,
    private val homeViewModel: HomeViewModel,
    private val surahViewModel: SurahViewModel,
    private val reference: QuranReferenceRepository,
    private val uiPrefs: UiPrefs,
    private val store: TafsirStore = TafsirStore.shared,
) : ViewModel() {
    val surah: Int = route.surah.coerceIn(1, QuranFacts.SURAH_COUNT)
    val ayahCount: Int = QuranFacts.ayahCount(surah)

    private val _ayah = MutableStateFlow(route.ayah.coerceIn(1, ayahCount.coerceAtLeast(1)))

    /** The ayah that is open. */
    val ayah: StateFlow<Int> = _ayah

    fun go(delta: Int) = _ayah.update { (it + delta).coerceIn(1, ayahCount.coerceAtLeast(1)) }

    // -- Source -------------------------------------------------------------------------------------------------

    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())

    /** A source picked on this screen, or the one the route asked for: wins over the setting until the screen closes. */
    private val picked = MutableStateFlow(TafsirSource.byKey(route.tafsirId))

    /** The source that is shown: the one picked, else the one chosen last, else the one of the language of the app. */
    val source: StateFlow<TafsirSource> = combine(picked, uiPrefs.tafsirSource, language) { picked, saved, language ->
        effectiveSource(picked, saved, language)
    }.distinctUntilChanged().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        effectiveSource(picked.value, uiPrefs.tafsirSource.value, language.value),
    )

    /** The source picked here; else the one chosen last when this language offers it as a chip; else the default of the language. */
    private fun effectiveSource(picked: TafsirSource?, saved: String?, language: AppLanguage): TafsirSource =
        picked ?: TafsirSource.byKey(saved)?.takeIf { it in TafsirSource.chipsFor(language) } ?: TafsirSource.defaultFor(language)

    fun select(next: TafsirSource) {
        picked.value = next
        uiPrefs.setTafsirSource(next.key)
    }

    /** Called with the language of the app: the default source and the names follow it. */
    fun onLanguage(next: AppLanguage) {
        language.value = next
        surahViewModel.loadChapterNames(next)
        loadTranslation(next, force = false)
    }

    // -- The commentary -----------------------------------------------------------------------------------------

    private val retries = MutableStateFlow(0)

    /** The commentary of the surah for [source]; a change of source or "Retry" loads again, the arrows do not (one surah is one file). */
    val load: StateFlow<TafsirLoad> = combine(source, retries) { source, retry -> source to retry }
        .flatMapLatest { (source, retry) ->
            flow {
                val local = store.local(source, surah)
                if (local != null) {
                    emit(TafsirLoad.Ready(local))
                } else {
                    emit(TafsirLoad.Loading)
                    val chapter = store.load(source, surah, force = retry > 0)
                    emit(if (chapter != null) TafsirLoad.Ready(chapter) else TafsirLoad.Missing)
                }
            }.flowOn(Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TafsirLoad.Loading)

    /** "Retry" of "An internet connection is needed to load the commentary": past the memory of the failed request. */
    fun retry() = retries.update { it + 1 }

    // -- The ayah ------------------------------------------------------------------------------------------------

    private val quranSurah = homeViewModel.uiState.map { state -> state.completeQuran.firstOrNull { it.surah.number == surah } }
        .distinctUntilChanged()

    private val _translations = MutableStateFlow<Map<Int, String>>(emptyMap())

    private var translationLanguage: AppLanguage? = null

    private fun loadTranslation(next: AppLanguage, force: Boolean) {
        if (!force && translationLanguage == next && _translations.value.isNotEmpty()) return
        translationLanguage = next
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = reference.getAyahTranslations(surah, next, ayahCount, force)
            if (translationLanguage == next && loaded.isNotEmpty()) _translations.value = loaded
        }
    }

    /** The ayah that is open: its text without the Bismillah and the sign of the hizb, its translation when it is there, and the commentary. */
    val page: StateFlow<TafsirView?> = combine(_ayah, quranSurah, _translations, load) { ayah, quran, translations, load ->
        val entity = quran?.ayahs?.firstOrNull { it.numberInSurah == ayah } ?: return@combine null
        TafsirView(
            ayah = ayah,
            arabic = splitBismillah(surah, ayah, entity.text),
            translation = translations[entity.number]?.takeIf { it.isNotBlank() },
            passage = (load as? TafsirLoad.Ready)?.chapter?.passage(ayah),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The name of the surah in the language of the app (the rule of [SurahNames], the same as the home screen and the pages). */
    val surahName: StateFlow<String> = combine(language, surahViewModel.chapterNames, homeViewModel.uiState) { language, chapters, home ->
        SurahNames.displayName(
            language = language,
            russianName = home.surahList.firstOrNull { it.surahNumber == surah }?.name,
            transliteration = chapters[surah]?.transliteration,
            fallback = home.completeQuran.firstOrNull { it.surah.number == surah }?.surah?.englishName?.takeIf { it.isNotBlank() } ?: "№ $surah",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "№ $surah")
}
