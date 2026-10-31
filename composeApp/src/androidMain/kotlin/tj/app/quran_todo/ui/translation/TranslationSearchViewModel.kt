package tj.app.quran_todo.ui.translation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.translation.TranslationChoice
import tj.app.quran_todo.data.translation.TranslationEdition
import tj.app.quran_todo.data.translation.TranslationSearchIndex
import tj.app.quran_todo.data.translation.TranslationSearchResult
import tj.app.quran_todo.data.translation.SearchText
import tj.app.quran_todo.domain.repository.QuranReferenceRepository
import tj.app.quran_todo.ui.downloads.DownloadRequest
import tj.app.quran_todo.ui.downloads.DownloadsManager
import tj.app.quran_todo.ui.i18n.translationCopy

/** What the tab "In translation" can be in. */
enum class TranslationSearchPhase {
    /** The chosen edition is not stored whole: "Download it whole to search". */
    NeedDownload,

    /** The download is in the queue or on its way. */
    Downloading,

    /** The download did not work; "Retry". */
    Failed,

    /** The edition is stored; its text is being read into memory. */
    Loading,

    /** The edition is in memory: a query is answered. */
    Ready,
}

@Immutable
data class TranslationSearchUi(
    val edition: TranslationEdition,
    val phase: TranslationSearchPhase,
    /** The query is searchable (two letters or more) and the edition is ready: [result] is its answer. */
    val searched: Boolean,
    val result: TranslationSearchResult,
)

/**
 * The search "In translation" of the home screen (task F4a): over the edition chosen for the language of the interface, in memory,
 * without an index in the database. The 6236 rows are read once, in the background, when the tab is on screen, and released when it
 * is not; a query waits 250 ms after the last key. An edition that is not stored whole is offered for download (the same request as
 * the "Translations" section of the downloads).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class TranslationSearchViewModel(
    private val repository: QuranReferenceRepository,
    private val downloads: DownloadsManager,
) : ViewModel() {
    private val language = MutableStateFlow(AppLanguage.RU)
    private val query = MutableStateFlow("")
    private val active = MutableStateFlow(false)

    fun setLanguage(next: AppLanguage) {
        language.value = next
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /** True while the tab is on screen and a search is going on: the text of the edition is kept in memory only then. */
    fun setActive(value: Boolean) {
        active.value = value
    }

    private val edition: Flow<TranslationEdition> =
        combine(language, TranslationChoice.store.revision) { language, _ -> TranslationChoice.store.editionFor(language) }
            .distinctUntilChanged { a, b -> a.key == b.key }

    private val storedKeys: Flow<Set<String>> = repository.translationChanges
        .mapLatest { runCatching { repository.downloadedTranslations() }.getOrDefault(emptySet()) }
        .flowOn(Dispatchers.IO)

    /** The edition in memory: read when it is stored and the tab is active, dropped when the tab is not. */
    private val index: Flow<Pair<String, TranslationSearchIndex>?> = combine(edition, storedKeys, active) { edition, keys, active ->
        edition.takeIf { active && it.key in keys }
    }
        .distinctUntilChanged { a, b -> a?.key == b?.key }
        .mapLatest { edition ->
            edition?.let {
                val texts = repository.getStoredTranslation(it)
                if (texts.isEmpty()) null else it.key to TranslationSearchIndex(texts)
            }
        }
        .flowOn(Dispatchers.Default)

    private val results: Flow<TranslationSearchResult> =
        combine(index, query.debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }) { index, text ->
            if (index == null || !SearchText.isSearchable(text)) TranslationSearchResult.Empty else index.second.search(text)
        }.flowOn(Dispatchers.Default)

    val ui: StateFlow<TranslationSearchUi> = combine(edition, storedKeys, downloads.queue.state, index, results) { edition, keys, queue, index, result ->
        val id = DownloadRequest.translationId(edition.key)
        val phase = when {
            edition.key in keys -> if (index?.first == edition.key) TranslationSearchPhase.Ready else TranslationSearchPhase.Loading
            queue.failures.any { it.requestId == id } -> TranslationSearchPhase.Failed
            queue.contains(id) -> TranslationSearchPhase.Downloading
            else -> TranslationSearchPhase.NeedDownload
        }
        TranslationSearchUi(
            edition = edition,
            phase = phase,
            searched = phase == TranslationSearchPhase.Ready && index != null,
            result = if (phase == TranslationSearchPhase.Ready) result else TranslationSearchResult.Empty,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TranslationSearchUi(
            edition = TranslationChoice.store.editionFor(language.value),
            phase = TranslationSearchPhase.Loading,
            searched = false,
            result = TranslationSearchResult.Empty,
        ),
    )

    /** "Download": the whole translation, one request; the tab shows "Downloading…" and then the search. */
    fun download() {
        val edition = ui.value.edition
        val title = translationCopy(language.value).row(edition.name.of(language.value))
        downloads.enqueueTranslation(edition, title)
    }

    /** "Retry" after a failure. */
    fun retry() {
        downloads.retry()
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
