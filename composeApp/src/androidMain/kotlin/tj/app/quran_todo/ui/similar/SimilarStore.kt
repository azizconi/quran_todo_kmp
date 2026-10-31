package tj.app.quran_todo.ui.similar

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import qurantodo.composeapp.generated.resources.Res
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.similar.SimilarIndex
import tj.app.quran_todo.common.similar.wordDiff
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.presentation.surah.SurahViewModel
import tj.app.quran_todo.ui.common.SurahNames
import tj.app.quran_todo.ui.reader.splitBismillah

/** One place in the sheet of similar ayahs: [differing] are the character ranges of the words that [text] does not share with the ayah asked about. */
@Immutable
data class SimilarPlace(
    val key: AyahKey,
    val text: String,
    val learned: Boolean,
    val differing: List<IntRange>,
    val score: Int,
)

/** A refrain: [places] are the other places that have the same words as the ayah, [total] counts the ayah itself. */
@Immutable
data class SimilarRepeats(val places: List<AyahKey>, val total: Int, val inOneSurah: Boolean)

/** What the sheet of similar ayahs shows (task F6). [canTellApart]: the ayah and at least one of its similar ayahs are memorized. */
@Immutable
data class SimilarSheetUi(
    val current: SimilarPlace,
    val similar: List<SimilarPlace>,
    val repeats: SimilarRepeats?,
    val canTellApart: Boolean,
)

/**
 * The similar ayahs of the app (task F6), one Koin single: the index of `files/quran/similar.tsv` (read once, in the background), what is
 * memorized and what is difficult (for the marks of the sheet, the line of the lesson and the question "Tell apart"), the text as the
 * screens draw it, and the state of the sheet "Similar verses", which one host at the root of the app shows over any screen.
 *
 * The index is data only; it never leaves the app and is made from our own text (`scripts/build_mutashabihat.py`).
 */
class SimilarStore(
    private val hifz: HifzRepository,
    private val home: HomeViewModel,
    private val surahViewModel: SurahViewModel,
    private val loadIndex: suspend () -> String = { Res.readBytes(INDEX_PATH).decodeToString() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val indexState = MutableStateFlow<SimilarIndex?>(null)

    /** The index; null while the file is being read (the marks simply wait), [SimilarIndex.Empty] if it could not be read. */
    val index: StateFlow<SimilarIndex?> = indexState.asStateFlow()

    /** Surah -> the ayahs that are memorized or being learned (`HifzRepository.coveredAyahs`). */
    val covered: StateFlow<Map<Int, Set<Int>>> = hifz.coveredAyahs()
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Surah -> the ayahs marked as difficult. */
    val hard: StateFlow<Map<Int, Set<Int>>> = hifz.hardAyahs()
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val quran: StateFlow<List<SurahWithAyahs>> = home.uiState
        .map { it.completeQuran }
        .distinctUntilChanged { old, new -> old === new }
        .stateIn(scope, SharingStarted.Eagerly, home.uiState.value.completeQuran)

    init {
        scope.launch {
            indexState.value = try {
                SimilarIndex.parse(loadIndex())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "similar_index_failed")
                SimilarIndex.Empty
            }
        }
    }

    /** The ayah as the screens draw it (the Bismillah of the first ayah cut off, the rub el hizb sign hidden); null while the Quran is not loaded. */
    fun text(key: AyahKey): String? = textIn(quran.value, key)

    private fun textIn(quran: List<SurahWithAyahs>, key: AyahKey): String? {
        val surah = quran.firstOrNull { it.surah.number == key.surah } ?: return null
        val ayah = surah.ayahs.firstOrNull { it.numberInSurah == key.ayah } ?: return null
        return splitBismillah(key.surah, key.ayah, ayah.text)
    }

    // -- Names -----------------------------------------------------------------------------------------

    val chapterNames: StateFlow<Map<Int, ChapterNameModel>> = surahViewModel.chapterNames

    private val russianNames: StateFlow<Map<Int, String>> = home.uiState
        .map { state -> state.surahList.associate { it.surahNumber to it.name } }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, home.uiState.value.surahList.associate { it.surahNumber to it.name })

    fun loadNames(language: AppLanguage) {
        surahViewModel.loadChapterNames(language)
    }

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

    // -- What is similar to an ayah that is being learned -------------------------------------------------

    /**
     * The best similar ayah of [key] that is already memorized, for the line of the lesson ("Similar to Al-A'raf 19"); null when there is
     * none or the index is not read yet. [exclude]: the ayahs of the passage being learned, which are not "already memorized" yet.
     */
    fun memorizedLike(key: AyahKey, exclude: Set<AyahKey> = emptySet()): AyahKey? {
        val similar = index.value?.similar(key) ?: return null
        val memorized = covered.value
        return similar.firstOrNull { it.ref !in exclude && memorized[it.ref.surah]?.contains(it.ref.ayah) == true }?.ref
    }

    // -- The sheet ---------------------------------------------------------------------------------------

    private val sheetKey = MutableStateFlow<AyahKey?>(null)

    /** The ayah whose sheet of similar ayahs is open, or null. */
    val sheetAyah: StateFlow<AyahKey?> = sheetKey.asStateFlow()

    fun openSheet(key: AyahKey) {
        sheetKey.value = key
    }

    fun closeSheet() {
        sheetKey.value = null
    }

    /** What the sheet shows; null while it is closed, while the index is read or while the text is not there. */
    val sheet: StateFlow<SimilarSheetUi?> = combine(sheetKey, index, covered, hard, quran) { key, index, covered, _, quran ->
        if (key == null || index == null) return@combine null
        buildSheet(key, index, covered, quran)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildSheet(key: AyahKey, index: SimilarIndex, covered: Map<Int, Set<Int>>, quran: List<SurahWithAyahs>): SimilarSheetUi? {
        val currentText = textIn(quran, key) ?: return null
        fun learned(ref: AyahKey) = covered[ref.surah]?.contains(ref.ayah) == true
        val similar = index.similar(key).mapNotNull { other ->
            val text = textIn(quran, other.ref) ?: return@mapNotNull null
            val diff = wordDiff(currentText, text)
            SimilarPlace(other.ref, text, learned(other.ref), diff.b.differingRanges, other.score)
        }
        val group = index.repeats(key)
        val repeats = group?.let { members ->
            SimilarRepeats(
                places = members.filter { it != key },
                total = members.size,
                inOneSurah = members.all { it.surah == key.surah },
            )
        }
        return SimilarSheetUi(
            current = SimilarPlace(key, currentText, learned(key), emptyList(), 100),
            similar = similar,
            repeats = repeats,
            canTellApart = learned(key) && similar.any { it.learned },
        )
    }

    companion object {
        const val INDEX_PATH = "files/quran/similar.tsv"
    }
}
