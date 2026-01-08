package tj.app.quran_todo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.dao.ChapterNameDao
import tj.app.quran_todo.data.database.dao.ChapterNameCacheDao
import tj.app.quran_todo.data.database.entity.quran.ChapterNameEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.model.QuranComChaptersResponse
import tj.app.quran_todo.domain.model.SurahModel
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoUpsertUseCase
import tj.app.quran_todo.domain.use_case.GetCompleteQuranUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code

data class HomeUiState(
    val surahList: List<SurahModel> = emptyList(),
    val todoSurahs: List<SurahTodoEntity> = emptyList(),
    val completeQuran: List<SurahWithAyahs> = emptyList(),
    val chapterNames: Map<Int, ChapterName> = emptyMap(),
    val selectedSurah: SurahWithAyahs? = null,
    val selectedSurahNumbers: Set<Int> = emptySet(),
    val filter: SurahTodoStatus? = null,
    val isLoadingQuran: Boolean = false,
    val errorMessage: String? = null,
)

data class ChapterName(
    val arabic: String,
    val transliteration: String,
    val translated: String,
)

class HomeViewModel(
    private val httpClient: HttpClient,
    private val chapterNameDao: ChapterNameDao,
    private val chapterNameCacheDao: ChapterNameCacheDao,
    private val todoDeleteSurahUseCase: TodoDeleteSurahUseCase,
    private val todoDeleteSurahByNumberUseCase: TodoDeleteSurahByNumberUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    private val getCompleteQuranUseCase: GetCompleteQuranUseCase,
    private val ayahTodoUpsertUseCase: AyahTodoUpsertUseCase,
    private val ayahTodoDeleteBySurahUseCase: AyahTodoDeleteBySurahUseCase,
): ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var completeQuranJob: Job? = null
    private var lastChapterLanguage: AppLanguage? = null
    private val cacheTtlMs = 1000L * 60 * 60 * 24 * 30

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val list = parseSurahList()
            _uiState.value = _uiState.value.copy(surahList = list)
        }

        viewModelScope.launch {
            todoGetSurahListUseCase().collect { list ->
                _uiState.value = _uiState.value.copy(todoSurahs = list)
            }
        }

        refreshQuran(withLocalAction = true)
    }

    fun loadChapterNames(language: AppLanguage) {
        if (lastChapterLanguage == language && _uiState.value.chapterNames.isNotEmpty()) return
        lastChapterLanguage = language
        viewModelScope.launch(Dispatchers.IO) {
            val cached = chapterNameDao.getByLanguage(language.code)
            if (cached.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    chapterNames = cached.associate { entity ->
                        entity.surahNumber to ChapterName(
                            arabic = entity.arabic,
                            transliteration = entity.transliteration,
                            translated = entity.translated
                        )
                    }
                )
                val meta = chapterNameCacheDao.get(language.code)
                val isFresh = meta != null && (getTimeMillis() - meta.lastUpdated) < cacheTtlMs
                if (isFresh) return@launch
            }

            try {
                val response: QuranComChaptersResponse = httpClient
                    .get("https://api.quran.com/api/v4/chapters?language=${language.code}")
                    .body()
                val map = response.chapters.associate { chapter ->
                    chapter.id to ChapterName(
                        arabic = chapter.nameArabic,
                        transliteration = chapter.nameSimple,
                        translated = chapter.translatedName.name
                    )
                }
                chapterNameDao.insertAll(
                    response.chapters.map { chapter ->
                        ChapterNameEntity(
                            languageCode = language.code,
                            surahNumber = chapter.id,
                            arabic = chapter.nameArabic,
                            transliteration = chapter.nameSimple,
                            translated = chapter.translatedName.name
                        )
                    }
                )
                chapterNameCacheDao.upsert(
                    ChapterNameCacheEntity(
                        languageCode = language.code,
                        lastUpdated = getTimeMillis()
                    )
                )
                _uiState.value = _uiState.value.copy(chapterNames = map)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(chapterNames = emptyMap())
            }
        }
    }

    fun deleteSurahFromTodo(entity: SurahTodoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            todoDeleteSurahUseCase(entity)
        }
    }

    fun upsertSurahToTodo(entity: SurahTodoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            todoUpsertSurahUseCase(entity)
        }
    }

    fun setSurahStatus(surahNumber: Int, status: SurahTodoStatus?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (status == null) {
                todoDeleteSurahByNumberUseCase(surahNumber)
                ayahTodoDeleteBySurahUseCase(surahNumber)
                return@launch
            }

            todoUpsertSurahUseCase(SurahTodoEntity(surahNumber, status))
            val ayahs = _uiState.value.completeQuran.firstOrNull {
                it.surah.number == surahNumber
            }?.ayahs ?: emptyList()

            ayahs.forEach { ayah ->
                ayahTodoUpsertUseCase(
                    AyahTodoEntity(
                        ayahNumber = ayah.number,
                        surahNumber = surahNumber,
                        status = when (status) {
                            SurahTodoStatus.LEARNED -> AyahTodoStatus.LEARNED
                            SurahTodoStatus.LEARNING -> AyahTodoStatus.LEARNING
                        }
                    )
                )
            }
        }
    }

    fun refreshQuran(withLocalAction: Boolean = true) {
        completeQuranJob?.cancel()
        completeQuranJob = viewModelScope.launch {
            getCompleteQuranUseCase(withLocalAction).collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingQuran = true,
                            errorMessage = null
                        )
                    }
                    is Resource.Success -> {
                        _uiState.value = _uiState.value.copy(
                            completeQuran = result.data,
                            isLoadingQuran = false,
                            errorMessage = null
                        )
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoadingQuran = false,
                            errorMessage = result.errorMessage
                        )
                    }
                    is Resource.Idle -> Unit
                }
            }
        }
    }

    fun setFilter(filter: SurahTodoStatus?) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun toggleSelection(surahNumber: Int) {
        val current = _uiState.value.selectedSurahNumbers
        val updated = if (current.contains(surahNumber)) {
            current - surahNumber
        } else {
            current + surahNumber
        }
        _uiState.value = _uiState.value.copy(selectedSurahNumbers = updated)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedSurahNumbers = emptySet())
    }

    fun markSelected(status: SurahTodoStatus) {
        val selected = _uiState.value.selectedSurahNumbers
        if (selected.isEmpty()) return
        selected.forEach { number ->
            setSurahStatus(number, status)
        }
        _uiState.value = _uiState.value.copy(selectedSurahNumbers = emptySet())
    }

    fun openSurahDetail(surahNumber: Int) {
        val surah = _uiState.value.completeQuran.firstOrNull {
            it.surah.number == surahNumber
        }
        _uiState.value = _uiState.value.copy(selectedSurah = surah)
    }

    fun dismissSurahDetail() {
        _uiState.value = _uiState.value.copy(selectedSurah = null)
    }
}
