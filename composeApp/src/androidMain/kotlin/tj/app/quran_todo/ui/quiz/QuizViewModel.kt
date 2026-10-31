package tj.app.quran_todo.ui.quiz

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.quiz.QuizAnswer
import tj.app.quran_todo.hifz.quiz.QuizAnswers
import tj.app.quran_todo.hifz.quiz.QuizBuilder
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.reader.displayWords
import kotlin.random.Random

/** What the quiz screen shows. */
@Immutable
sealed interface QuizUi {
    /** The covered ayahs and the text of the Quran are being read. */
    data object Loading : QuizUi

    /** Fewer than [QuizBuilder.MIN_POOL] ayahs are memorized: there is nothing to ask. */
    data object TooLittle : QuizUi

    /** A quiz is on ([round] counts from 1: "5 more questions" starts a new one). When [QuizSession.finished] it is the result. */
    data class Playing(val session: QuizSession, val round: Int) : QuizUi
}

/**
 * State and commands of the quiz "Check" (task F2b). The questions are drawn once from the covered ayahs
 * ([QuizBuilder.build]); the session lives here, so a rotation does not lose it. A quiz is a help, not a lesson: it writes nothing to
 * the review log. The one trace is that a missed ayah becomes difficult (`HifzRepository.markHard`, written the moment the answer
 * is given, so leaving in the middle keeps what was marked). `quiz_completed` (`known`, `types`) is the only event.
 *
 * The shared view models (Koin singletons) are injected, never obtained through `koinViewModel`.
 */
class QuizViewModel(
    private val hifz: HifzRepository,
    private val home: HomeViewModel,
    private val surahViewModel: SurahViewModel,
) : ViewModel() {
    private val quran: StateFlow<List<SurahWithAyahs>> = home.uiState
        .map { it.completeQuran }
        .distinctUntilChanged { old, new -> old === new }
        .stateIn(viewModelScope, SharingStarted.Eagerly, home.uiState.value.completeQuran)

    val chapterNames: StateFlow<Map<Int, ChapterNameModel>> = surahViewModel.chapterNames

    private val russianNames: StateFlow<Map<Int, String>> = home.uiState
        .map { state -> state.surahList.associate { it.surahNumber to it.name } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, home.uiState.value.surahList.associate { it.surahNumber to it.name })

    private val state = MutableStateFlow<QuizUi>(QuizUi.Loading)

    /** What the screen shows. */
    val ui: StateFlow<QuizUi> = state.asStateFlow()

    /** The text of every ayah, surah -> ayah in the surah -> text; filled when the Quran is there. */
    private var texts: Map<Int, Map<Int, String>> = emptyMap()
    private var round = 0
    private var completedLogged = false

    init {
        viewModelScope.launch { start() }
    }

    fun onLanguage(language: AppLanguage) {
        surahViewModel.loadChapterNames(language)
    }

    /** "5 more questions": a new quiz from what is memorized now. */
    fun again() {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val covered = try {
            hifz.coveredAyahs().first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppTelemetry.logError(throwable = error, context = "quiz_covered_failed")
            emptyMap()
        }
        if (!QuizBuilder.isAvailable(covered)) {
            state.value = QuizUi.TooLittle
            return
        }
        if (texts.isEmpty()) {
            val complete = quran.first { it.isNotEmpty() }
            texts = complete.associate { surah -> surah.surah.number to surah.ayahs.associate { it.numberInSurah to it.text } }
        }
        val questions = QuizBuilder.build(
            covered = covered,
            words = { surah, ayah -> ayahWords(surah, ayah)?.wordCount ?: 0 },
            count = QuizBuilder.DEFAULT_COUNT,
            random = Random.Default,
        )
        round++
        completedLogged = false
        state.value = if (questions.isEmpty()) QuizUi.TooLittle else QuizUi.Playing(QuizSession(questions), round)
    }

    // -- Text and names -------------------------------------------------------------------------------

    /** The ayah as the reader draws it (the Bismillah of the first ayah cut off) and its words; null when the text is missing. */
    fun ayahWords(surah: Int, ayah: Int): AyahWords? =
        texts[surah]?.get(ayah)?.let { displayWords(surah, ayah, it) }

    /** The name of a surah by the one rule of the Android UI ([SurahNames]). */
    fun surahTitle(number: Int, language: AppLanguage, names: Map<Int, ChapterNameModel>): String {
        val info = quran.value.firstOrNull { it.surah.number == number }?.surah
        return SurahNames.displayName(
            language = language,
            russianName = russianNames.value[number],
            transliteration = names[number]?.transliteration,
            fallback = info?.englishName?.takeIf { it.isNotBlank() } ?: "№ $number",
        )
    }

    fun surahArabicName(number: Int, names: Map<Int, ChapterNameModel>): String =
        names[number]?.arabic ?: quran.value.firstOrNull { it.surah.number == number }?.surah?.name.orEmpty()

    // -- Steps of the session -------------------------------------------------------------------------

    fun reveal() = edit { it.reveal() }

    fun answerSelf(knew: Boolean) = edit { it.answerSelf(knew) }

    fun pick(ayah: Int) = edit { it.pick(ayah) }

    fun clearPicks() = edit { it.clearPicks() }

    fun checkOrder() = edit { it.checkOrder() }

    fun choose(surah: Int) = edit { it.choose(surah) }

    fun next() = edit { it.next() }

    private fun edit(change: (QuizSession) -> QuizSession) {
        val current = state.value as? QuizUi.Playing ?: return
        val before = current.session
        val after = change(before)
        if (after == before) return
        state.value = current.copy(session = after)
        if (after.answers.size > before.answers.size) mark(after.answers.last())
        if (after.finished && !completedLogged) {
            completedLogged = true
            AppTelemetry.logEvent(
                "quiz_completed",
                mapOf("known" to after.known.toString(), "types" to QuizAnswers.types(after.answers).toString()),
            )
        }
    }

    /** A missed ayah becomes difficult at once. Nothing else is written. */
    private fun mark(answer: QuizAnswer) {
        if (answer.hardAyahs.isEmpty()) return
        viewModelScope.launch {
            try {
                hifz.markHard(answer.question.surah, answer.hardAyahs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "quiz_mark_hard_failed")
            }
        }
    }
}
