package tj.app.quran_todo.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import tj.app.quran_todo.common.utils.parseSurahList
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.database.dao.FocusSessionDao
import tj.app.quran_todo.domain.use_case.AyahTodoGetAllUseCase
import tj.app.quran_todo.domain.use_case.TodoGetSurahListUseCase

data class StatsUiState(
    val totalSurahs: Int = 0,
    val totalAyahs: Int = 0,
    val learnedSurahs: Int = 0,
    val learningSurahs: Int = 0,
    val learnedAyahs: Int = 0,
    val learningAyahs: Int = 0,
    val streakDays: Int = 0,
    val bestDayCount: Int = 0,
    val avgPerDay: Int = 0,
    val focusMinutes: Int = 0,
) {
    val idleSurahs: Int get() = (totalSurahs - learnedSurahs - learningSurahs).coerceAtLeast(0)
    val idleAyahs: Int get() = (totalAyahs - learnedAyahs - learningAyahs).coerceAtLeast(0)
}

class StatsViewModel(
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    ayahTodoGetAllUseCase: AyahTodoGetAllUseCase,
    focusSessionDao: FocusSessionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val totals = MutableStateFlow(StatsUiState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val surahs = parseSurahList()
            totals.value = totals.value.copy(
                totalSurahs = surahs.size,
                totalAyahs = surahs.sumOf { it.ayats }
            )
        }

        viewModelScope.launch {
            combine(
                totals,
                todoGetSurahListUseCase(),
                ayahTodoGetAllUseCase(),
                focusSessionDao.getAll()
            ) { totalsState, surahTodos, ayahTodos, focusSessions ->
                val learnedSurahs = surahTodos.count { it.status == SurahTodoStatus.LEARNED }
                val learningSurahs = surahTodos.count { it.status == SurahTodoStatus.LEARNING }
                val learnedAyahs = ayahTodos.count { it.status == AyahTodoStatus.LEARNED }
                val learningAyahs = ayahTodos.count { it.status == AyahTodoStatus.LEARNING }
                val activityDays = ayahTodos
                    .filter { it.updatedAt > 0 }
                    .groupBy { epochToLocalDay(it.updatedAt) }
                    .mapValues { it.value.size }
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val streakDays = calculateStreak(activityDays.keys, today)
                val bestDayCount = activityDays.values.maxOrNull() ?: 0
                val avgPerDay = calculateAveragePerDay(activityDays, today, 30)
                val focusMinutes = focusSessions.sumOf { it.durationMinutes }

                totalsState.copy(
                    learnedSurahs = learnedSurahs,
                    learningSurahs = learningSurahs,
                    learnedAyahs = learnedAyahs,
                    learningAyahs = learningAyahs,
                    streakDays = streakDays,
                    bestDayCount = bestDayCount,
                    avgPerDay = avgPerDay,
                    focusMinutes = focusMinutes
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun epochToLocalDay(epochMillis: Long) =
        kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date

    private fun calculateStreak(activityDays: Set<kotlinx.datetime.LocalDate>, today: kotlinx.datetime.LocalDate): Int {
        var streak = 0
        var current = today
        while (activityDays.contains(current)) {
            streak++
            current = current.minus(DatePeriod(days = 1))
        }
        return streak
    }

    private fun calculateAveragePerDay(
        activityDays: Map<kotlinx.datetime.LocalDate, Int>,
        today: kotlinx.datetime.LocalDate,
        windowDays: Int,
    ): Int {
        val windowStart = today.minus(DatePeriod(days = windowDays - 1))
        val total = activityDays.filterKeys { it >= windowStart }.values.sum()
        return (total.toDouble() / windowDays).toInt()
    }
}
