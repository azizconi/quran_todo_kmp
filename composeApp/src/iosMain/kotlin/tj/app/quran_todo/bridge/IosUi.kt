package tj.app.quran_todo.bridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.EpochDayTicker
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.downloads.DownloadsViewModel
import tj.app.quran_todo.ui.drill.DrillViewModel
import tj.app.quran_todo.ui.hifz.HifzViewModel
import tj.app.quran_todo.ui.home.HomeUiViewModel
import tj.app.quran_todo.ui.khatm.KhatmViewModel
import tj.app.quran_todo.ui.lesson.LessonViewModel
import tj.app.quran_todo.ui.listen.ListenOldViewModel
import tj.app.quran_todo.ui.mushaf.MushafViewModel
import tj.app.quran_todo.ui.navigation.Dest
import tj.app.quran_todo.ui.notes.NotesViewModel
import tj.app.quran_todo.ui.onboarding.OnboardingViewModel
import tj.app.quran_todo.ui.plan.PlanViewModel
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.progress.ProgressViewModel
import tj.app.quran_todo.ui.progress.analytics.AnalyticsViewModel
import tj.app.quran_todo.ui.quiz.QuizViewModel
import tj.app.quran_todo.ui.reader.ReaderViewModel
import tj.app.quran_todo.ui.settings.SettingsViewModel
import tj.app.quran_todo.ui.similar.SimilarStore
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.tafsir.TafsirViewModel
import tj.app.quran_todo.ui.translation.TranslationSearchViewModel
import tj.app.quran_todo.ui.whatsnew.WhatsNewViewModel
import kotlin.reflect.KClass

/**
 * The door of the SwiftUI app into the shared UI layer: one function per view model, resolved from Koin, and the shared singletons
 * the screens read. Koin must be started first (`InitKoinIos()`).
 *
 * A view model lives as long as its screen: a SwiftUI view makes it in `onAppear` / a `@State` initializer and calls [clear] when it
 * goes away, which runs `onCleared()` and cancels `viewModelScope`, exactly as the back stack entry does on Android. Call everything
 * on the main thread. The shared singletons ([homeViewModel], [surahViewModel], [playback], [uiPrefs], [hifz], [ticker], [similar])
 * are never cleared.
 */
object IosUi {
    private val koin get() = KoinPlatform.getKoin()

    // -- view models --------------------------------------------------------------------------------------------------

    private val stores = HashMap<ViewModel, ViewModelStore>()

    private fun <T : ViewModel> create(type: KClass<T>, make: () -> T): T {
        val store = ViewModelStore()
        val factory = viewModelFactory { addInitializer(type) { make() } }
        val created = ViewModelProvider.create(store, factory)[type]
        stores[created] = store
        return created
    }

    /** Ends the life of [viewModel]: `onCleared()` runs and its coroutines are cancelled. Safe to call twice. */
    fun clear(viewModel: ViewModel) {
        stores.remove(viewModel)?.clear()
    }

    fun home(): HomeUiViewModel = create(HomeUiViewModel::class) { koin.get() }
    fun reader(route: Dest.Reader): ReaderViewModel = create(ReaderViewModel::class) { koin.get { parametersOf(route) } }
    fun mushaf(route: Dest.Mushaf): MushafViewModel = create(MushafViewModel::class) { koin.get { parametersOf(route) } }
    fun hifz(route: Dest.Reader): HifzViewModel = create(HifzViewModel::class) { koin.get { parametersOf(route) } }
    fun tafsir(route: Dest.Tafsir): TafsirViewModel = create(TafsirViewModel::class) { koin.get { parametersOf(route) } }
    fun lesson(): LessonViewModel = create(LessonViewModel::class) { koin.get() }
    fun listenOld(): ListenOldViewModel = create(ListenOldViewModel::class) { koin.get() }
    fun drill(): DrillViewModel = create(DrillViewModel::class) { koin.get() }
    fun quiz(): QuizViewModel = create(QuizViewModel::class) { koin.get() }
    fun plan(): PlanViewModel = create(PlanViewModel::class) { koin.get() }
    fun khatm(): KhatmViewModel = create(KhatmViewModel::class) { koin.get() }
    fun progress(): ProgressViewModel = create(ProgressViewModel::class) { koin.get() }
    fun analytics(): AnalyticsViewModel = create(AnalyticsViewModel::class) { koin.get() }
    fun notes(): NotesViewModel = create(NotesViewModel::class) { koin.get() }
    fun settings(): SettingsViewModel = create(SettingsViewModel::class) { koin.get() }
    fun downloads(): DownloadsViewModel = create(DownloadsViewModel::class) { koin.get() }
    fun translationSearch(): TranslationSearchViewModel = create(TranslationSearchViewModel::class) { koin.get() }
    fun onboarding(): OnboardingViewModel = create(OnboardingViewModel::class) { koin.get() }
    fun whatsNew(): WhatsNewViewModel = create(WhatsNewViewModel::class) { koin.get() }

    // -- shared singletons ---------------------------------------------------------------------------------------------

    val homeViewModel: HomeViewModel get() = koin.get()
    val surahViewModel: SurahViewModel get() = koin.get()
    val playback: PlaybackController get() = koin.get()
    val uiPrefs: UiPrefs get() = koin.get()
    val hifz: HifzRepository get() = koin.get()
    val ticker: EpochDayTicker get() = koin.get()
    val similar: SimilarStore get() = koin.get()

    // -- language -------------------------------------------------------------------------------------------------------

    private val _language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())

    /** The language of the interface; read the copy (`IosCopy`) again when it changes. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** The person picked [language] in the app: it is saved and every screen that observes [language] redraws. */
    fun setLanguage(language: AppLanguage) {
        LanguageStorage.saveLanguage(language)
        _language.value = language
    }
}
