package tj.app.quran_todo.ui.notes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.data.database.dao.AyahNoteDao
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.progress.SurahNameSource

/** What "My notes" shows: the notes that match the search, how many there are in all, and the names of the surahs. */
@Immutable
data class NotesUi(
    val ready: Boolean = false,
    val query: String = "",
    val total: Int = 0,
    val items: List<NoteItem> = emptyList(),
    val names: List<String> = emptyList(),
) {
    fun nameOf(surah: Int): String = names.getOrNull(surah - 1) ?: "№ $surah"
}

/**
 * "My notes" (task F5a): all notes of the ayahs (`ayah_note`), newest first, with a search over the text. Deleting removes the row at
 * once and [restore] writes the same row back ("Undo"). The shared [HomeViewModel] is only the source of the surah names.
 */
class NotesViewModel internal constructor(
    private val home: HomeViewModel,
    private val dao: AyahNoteDao,
) : ViewModel() {

    private val language = MutableStateFlow(LanguageStorage.getSavedLanguage() ?: LanguageStorage.getDeviceLanguage())
    private val names: Flow<List<String>> = SurahNameSource.names(home, language)
    private val query = MutableStateFlow("")

    val ui: StateFlow<NotesUi> = combine(
        dao.observeAll().catch { error ->
            AppTelemetry.logError(throwable = error, context = "notes_read_failed")
            emit(emptyList())
        },
        names,
        query,
    ) { entities, names, query ->
        val all = NotesLogic.items(entities)
        NotesUi(ready = true, query = query, total = all.size, items = NotesLogic.filter(all, query), names = names)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), NotesUi())

    init {
        home.loadChapterNames(language.value)
        AppTelemetry.logEvent("notes_opened")
    }

    fun setLanguage(value: AppLanguage) {
        if (language.value == value) return
        language.value = value
        home.loadChapterNames(value)
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun delete(item: NoteItem) {
        AppTelemetry.logEvent("note_deleted_from_list")
        viewModelScope.launch(Dispatchers.IO) { dao.delete(item.entity) }
    }

    fun restore(item: NoteItem) {
        viewModelScope.launch(Dispatchers.IO) { dao.upsert(item.entity) }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
