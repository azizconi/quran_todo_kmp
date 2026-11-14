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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.similar.TellApartPairs
import tj.app.quran_todo.common.similar.WordDiff
import tj.app.quran_todo.common.similar.wordDiff
import tj.app.quran_todo.hifz.quiz.TellApartQuestion
import tj.app.quran_todo.hifz.quiz.toQuestion
import tj.app.quran_todo.ui.similar.SimilarStore
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.quiz.QuizAnswer
import tj.app.quran_todo.hifz.quiz.QuizAnswers
import tj.app.quran_todo.hifz.quiz.QuizBuilder
import tj.app.quran_todo.hifz.quiz.QuizQuestion
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

    /** "Tell apart" was asked for, and none of the similar places is memorized on both sides (task F6). */
    data object NoPairs : QuizUi

    /**
     * A quiz is on ([round] counts from 1: "5 more questions" starts a new one). When [QuizSession.finished] it is the result.
     * [pairs] is how many pairs of similar places the person could tell apart (the line "Tell apart" of the result), 0 in the drill itself.
     */
    data class Playing(val session: QuizSession, val round: Int, val pairs: Int = 0) : QuizUi
}

/** What the screen asks of the quiz: the usual five kinds, or only "Tell apart" (all pairs, or the group of one ayah). */
sealed interface QuizMode {
    data object Mixed : QuizMode

    data class TellApart(val focus: AyahKey? = null) : QuizMode
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
    private val similar: SimilarStore,
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

    /** What this quiz is; set by [begin], once. */
    var mode: QuizMode = QuizMode.Mixed
        private set
    private var began = false

    /**
     * Starts the quiz of [asked] (the screen calls it when it appears; a rotation calls it again and nothing happens, the session lives
     * here).
     */
    fun begin(asked: QuizMode) {
        if (began) return
        began = true
        mode = asked
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
        val asked = mode
        if (asked is QuizMode.Mixed && !QuizBuilder.isAvailable(covered)) {
            state.value = QuizUi.TooLittle
            return
        }
        if (texts.isEmpty()) {
            val complete = quran.first { it.isNotEmpty() }
            texts = complete.associate { surah -> surah.surah.number to surah.ayahs.associate { it.numberInSurah to it.text } }
        }
        val hard = try {
            hifz.hardAyahs().first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AppTelemetry.logError(throwable = error, context = "quiz_hard_failed")
            emptyMap()
        }
        val index = similar.index.filterNotNull().first()
        val text: (AyahKey) -> String? = { key -> ayahWords(key.surah, key.ayah)?.text }
        val random = Random.Default
        val questions: List<QuizQuestion>
        var pairCount = 0
        when (asked) {
            QuizMode.Mixed -> {
                val pairs = TellApartPairs.pick(index, covered, hard, text, count = TELL_APART_POOL, random = random)
                questions = QuizBuilder.build(
                    covered = covered,
                    words = { surah, ayah -> ayahWords(surah, ayah)?.wordCount ?: 0 },
                    count = QuizBuilder.DEFAULT_COUNT,
                    random = random,
                    pairs = pairs,
                )
                pairCount = TellApartPairs.count(index, covered)
            }
            is QuizMode.TellApart -> {
                val pairs = if (asked.focus != null) {
                    TellApartPairs.forAyah(index, asked.focus, covered, hard, text, QuizBuilder.DEFAULT_COUNT, random)
                } else {
                    TellApartPairs.pick(index, covered, hard, text, QuizBuilder.DEFAULT_COUNT, random)
                }
                questions = pairs.map { it.toQuestion() }
            }
        }
        round++
        completedLogged = false
        state.value = when {
            questions.isNotEmpty() -> QuizUi.Playing(QuizSession(questions), round, pairCount)
            asked is QuizMode.TellApart -> QuizUi.NoPairs
            else -> QuizUi.TooLittle
        }
    }

    /** The words of the two places of a "Tell apart" question side by side, or null while a text is missing. */
    fun tellApartDiff(question: TellApartQuestion): WordDiff? {
        val a = ayahWords(question.surah, question.ayah)?.text ?: return null
        val b = ayahWords(question.otherSurah, question.otherAyah)?.text ?: return null
        return wordDiff(a, b)
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

    /** A missed ayah becomes difficult at once (both places of a "Tell apart" question, each in its own surah). Nothing else is written. */
    private fun mark(answer: QuizAnswer) {
        val bySurah = QuizAnswers.hardBySurah(listOf(answer))
        if (bySurah.isEmpty()) return
        viewModelScope.launch {
            try {
                for ((surah, ayahs) in bySurah) hifz.markHard(surah, ayahs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "quiz_mark_hard_failed")
            }
        }
    }

    private companion object {
        /** The pairs the builder draws its questions of "Tell apart" from. */
        const val TELL_APART_POOL = 6
    }
}
