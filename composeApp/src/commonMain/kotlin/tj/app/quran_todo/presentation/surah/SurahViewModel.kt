package tj.app.quran_todo.presentation.surah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.dao.ChapterNameDao
import tj.app.quran_todo.data.database.dao.ChapterNameCacheDao
import tj.app.quran_todo.data.database.dao.AyahNoteDao
import tj.app.quran_todo.data.database.dao.AyahReviewDao
import tj.app.quran_todo.data.database.entity.quran.ChapterNameEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.model.QuranComChaptersResponse
import tj.app.quran_todo.data.model.SurahResponse
import tj.app.quran_todo.domain.use_case.AyahTodoDeleteByAyahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahOnceUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoGetBySurahUseCase
import tj.app.quran_todo.domain.use_case.AyahTodoUpsertUseCase
import tj.app.quran_todo.domain.use_case.TodoDeleteSurahByNumberUseCase
import tj.app.quran_todo.domain.use_case.TodoUpsertSurahUseCase
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.code
import tj.app.quran_todo.common.i18n.editionForLanguage
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.presentation.ChapterName

class SurahViewModel(
    private val httpClient: HttpClient,
    private val chapterNameDao: ChapterNameDao,
    private val chapterNameCacheDao: ChapterNameCacheDao,
    private val ayahNoteDao: AyahNoteDao,
    private val ayahReviewDao: AyahReviewDao,
    private val ayahTodoGetBySurahUseCase: AyahTodoGetBySurahUseCase,
    private val ayahTodoUpsertUseCase: AyahTodoUpsertUseCase,
    private val ayahTodoDeleteByAyahUseCase: AyahTodoDeleteByAyahUseCase,
    private val ayahTodoGetBySurahOnceUseCase: AyahTodoGetBySurahOnceUseCase,
    private val todoUpsertSurahUseCase: TodoUpsertSurahUseCase,
    private val todoDeleteSurahByNumberUseCase: TodoDeleteSurahByNumberUseCase,
) : ViewModel() {

    private val _ayahTranslations = MutableStateFlow<Map<Int, String>>(emptyMap())
    val ayahTranslations: StateFlow<Map<Int, String>> = _ayahTranslations.asStateFlow()

    private val _ruSurahNames = MutableStateFlow<Map<Int, String>>(emptyMap())
    val ruSurahNames: StateFlow<Map<Int, String>> = _ruSurahNames.asStateFlow()

    private val _chapterNames = MutableStateFlow<Map<Int, ChapterName>>(emptyMap())
    val chapterNames: StateFlow<Map<Int, ChapterName>> = _chapterNames.asStateFlow()

    private val _notes = MutableStateFlow<Map<Int, AyahNoteEntity>>(emptyMap())
    val notes: StateFlow<Map<Int, AyahNoteEntity>> = _notes.asStateFlow()

    private val _reviews = MutableStateFlow<Map<Int, AyahReviewEntity>>(emptyMap())
    val reviews: StateFlow<Map<Int, AyahReviewEntity>> = _reviews.asStateFlow()

    private var lastLoaded: Pair<Int, AppLanguage>? = null
    private var lastChapterLanguage: AppLanguage? = null
    private val cacheTtlMs = 1000L * 60 * 60 * 24 * 30

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val list = parseSurahList()
            _ruSurahNames.value = list.associate { it.surahNumber to it.name }
        }
    }

    fun ayahTodos(surahNumber: Int): Flow<List<AyahTodoEntity>> =
        ayahTodoGetBySurahUseCase(surahNumber)

    fun observeNotes(surahNumber: Int) {
        viewModelScope.launch {
            ayahNoteDao.getBySurahNumber(surahNumber).collect { list ->
                _notes.value = list.associateBy { it.ayahNumber }
            }
        }
    }

    fun observeReviews(surahNumber: Int) {
        viewModelScope.launch {
            ayahReviewDao.getBySurahNumber(surahNumber).collect { list ->
                _reviews.value = list.associateBy { it.ayahNumber }
            }
        }
    }

    fun loadTranslations(surahNumber: Int, language: AppLanguage) {
        if (lastLoaded == surahNumber to language && _ayahTranslations.value.isNotEmpty()) return
        lastLoaded = surahNumber to language

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val edition = editionForLanguage(language)
                val response: SurahResponse = httpClient
                    .get(Constants.QURAN_API_BASE_URL + "surah/$surahNumber/$edition")
                    .body()
                val translations = response.data.ayahs.associate { it.number to it.text }
                _ayahTranslations.value = translations
            } catch (_: Exception) {
                _ayahTranslations.value = emptyMap()
            }
        }
    }

    fun loadChapterNames(language: AppLanguage) {
        if (lastChapterLanguage == language && _chapterNames.value.isNotEmpty()) return
        lastChapterLanguage = language
        viewModelScope.launch(Dispatchers.IO) {
            val cached = chapterNameDao.getByLanguage(language.code)
            if (cached.isNotEmpty()) {
                _chapterNames.value = cached.associate { entity ->
                    entity.surahNumber to ChapterName(
                        arabic = entity.arabic,
                        transliteration = entity.transliteration,
                        translated = entity.translated
                    )
                }
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
                _chapterNames.value = map
            } catch (_: Exception) {
                _chapterNames.value = emptyMap()
            }
        }
    }

    fun updateAyahStatus(
        ayahNumber: Int,
        surahNumber: Int,
        totalAyahs: Int,
        status: AyahTodoStatus,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ayahTodoUpsertUseCase(
                AyahTodoEntity(
                    ayahNumber = ayahNumber,
                    surahNumber = surahNumber,
                    status = status,
                    updatedAt = getTimeMillis()
                )
            )
            scheduleReview(ayahNumber, surahNumber)
            syncSurahStatusInternal(surahNumber, totalAyahs)
        }
    }

    fun clearAyahStatus(ayahNumber: Int, surahNumber: Int, totalAyahs: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            ayahTodoDeleteByAyahUseCase(ayahNumber)
            ayahReviewDao.deleteByAyahNumber(ayahNumber)
            syncSurahStatusInternal(surahNumber, totalAyahs)
        }
    }

    fun upsertNote(ayahNumber: Int, surahNumber: Int, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (note.isBlank()) {
                ayahNoteDao.getByAyahNumber(ayahNumber)?.let { ayahNoteDao.delete(it) }
            } else {
                ayahNoteDao.upsert(
                    AyahNoteEntity(
                        ayahNumber = ayahNumber,
                        surahNumber = surahNumber,
                        note = note.trim(),
                        updatedAt = getTimeMillis()
                    )
                )
            }
        }
    }

    fun completeReview(ayahNumber: Int, surahNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = getTimeMillis()
            val current = _reviews.value[ayahNumber]
            val nextIndex = ((current?.intervalIndex ?: 0) + 1)
                .coerceAtMost(reviewIntervalsDays.lastIndex)
            val nextAt = now + reviewIntervalsDays[nextIndex] * dayMillis
            ayahReviewDao.upsert(
                AyahReviewEntity(
                    ayahNumber = ayahNumber,
                    surahNumber = surahNumber,
                    nextReviewAt = nextAt,
                    intervalIndex = nextIndex,
                    lastReviewedAt = now
                )
            )
        }
    }

    private suspend fun syncSurahStatusInternal(surahNumber: Int, totalAyahs: Int) {
        val ayahTodos = ayahTodoGetBySurahOnceUseCase(surahNumber)
        if (ayahTodos.isEmpty()) {
            todoDeleteSurahByNumberUseCase(surahNumber)
            return
        }

        val allLearned = ayahTodos.size == totalAyahs &&
            ayahTodos.all { it.status == AyahTodoStatus.LEARNED }

        val status = if (allLearned) {
            SurahTodoStatus.LEARNED
        } else {
            SurahTodoStatus.LEARNING
        }

        todoUpsertSurahUseCase(SurahTodoEntity(surahNumber, status))
    }

    private suspend fun scheduleReview(ayahNumber: Int, surahNumber: Int) {
        val now = getTimeMillis()
        val nextAt = now + reviewIntervalsDays.first() * dayMillis
        ayahReviewDao.upsert(
            AyahReviewEntity(
                ayahNumber = ayahNumber,
                surahNumber = surahNumber,
                nextReviewAt = nextAt,
                intervalIndex = 0,
                lastReviewedAt = now
            )
        )
    }

    private companion object {
        val reviewIntervalsDays = listOf(1L, 3L, 7L, 14L, 30L)
        const val dayMillis = 24L * 60 * 60 * 1000
    }
}
