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
import tj.app.quran_todo.common.recitation.RecitationPerformanceStore
import tj.app.quran_todo.common.settings.parseAyahKey
import tj.app.quran_todo.common.settings.UserSettingsStorage
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
    val weakAyahCount: Int = 0,
    val last14DailyCounts: List<Int> = List(14) { 0 },
    val recitationSessions: Int = 0,
    val recitationAttempts: Int = 0,
    val recitationMatches: Int = 0,
    val recitationAccuracy: Float = 0f,
    val recitationConfidence: Float = 0f,
    val recitationIssueRate: Float = 0f,
    val recitationBestStreak: Int = 0,
    val recitationScore: Int = 0,
    val recitationTopSurahs: List<RecitationTopSurahUi> = emptyList(),
    val ayahUpdates7: Int = 0,
    val ayahUpdates30: Int = 0,
    val ayahUpdates90: Int = 0,
    val focusMinutes7: Int = 0,
    val focusMinutes30: Int = 0,
    val focusMinutes90: Int = 0,
    val surahPeriodActivity: List<SurahPeriodActivityUi> = emptyList(),
    val weakSurahReport: List<WeakSurahReportUi> = emptyList(),
) {
    val idleSurahs: Int get() = (totalSurahs - learnedSurahs - learningSurahs).coerceAtLeast(0)
    val idleAyahs: Int get() = (totalAyahs - learnedAyahs - learningAyahs).coerceAtLeast(0)
    val hasRecitationData: Boolean
        get() = recitationSessions > 0 || recitationAttempts > 0
}

data class RecitationTopSurahUi(
    val surahNumber: Int,
    val surahLabel: String,
    val attempts: Int,
    val accuracy: Float,
)

data class SurahPeriodActivityUi(
    val surahNumber: Int,
    val surahLabel: String,
    val count7: Int,
    val count30: Int,
    val count90: Int,
)

data class WeakSurahReportUi(
    val surahNumber: Int,
    val surahLabel: String,
    val weakCount: Int,
    val sampleAyahNumbers: List<Int>,
)

class StatsViewModel(
    todoGetSurahListUseCase: TodoGetSurahListUseCase,
    ayahTodoGetAllUseCase: AyahTodoGetAllUseCase,
    focusSessionDao: FocusSessionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val totals = MutableStateFlow(StatsUiState())
    private var surahNameByNumber: Map<Int, String> = emptyMap()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val surahs = parseSurahList()
            totals.value = totals.value.copy(
                totalSurahs = surahs.size,
                totalAyahs = surahs.sumOf { it.ayats }
            )
            surahNameByNumber = surahs.associate { it.surahNumber to it.name }
        }

        viewModelScope.launch {
            combine(
                totals,
                todoGetSurahListUseCase(),
                ayahTodoGetAllUseCase(),
                focusSessionDao.getAll(),
                RecitationPerformanceStore.snapshot
            ) { totalsState, surahTodos, ayahTodos, focusSessions, recitationStats ->
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
                val focusActivityDays = focusSessions
                    .filter { it.startedAt > 0 }
                    .groupBy { epochToLocalDay(it.startedAt) }
                    .mapValues { (_, list) -> list.sumOf { it.durationMinutes } }
                val weakKeys = UserSettingsStorage.getWeakAyahKeys().orEmpty()
                val weakAyahCount = weakKeys.size
                val last14DailyCounts = buildWindowCounts(activityDays, today, 14)
                val ayahUpdates7 = sumInWindow(activityDays, today, 7)
                val ayahUpdates30 = sumInWindow(activityDays, today, 30)
                val ayahUpdates90 = sumInWindow(activityDays, today, 90)
                val focusMinutes7 = sumInWindow(focusActivityDays, today, 7)
                val focusMinutes30 = sumInWindow(focusActivityDays, today, 30)
                val focusMinutes90 = sumInWindow(focusActivityDays, today, 90)

                val surahPeriodActivity = ayahTodos
                    .filter { it.updatedAt > 0 }
                    .groupBy { it.surahNumber }
                    .map { (surahNumber, entries) ->
                        val countsByDay = entries
                            .groupBy { epochToLocalDay(it.updatedAt) }
                            .mapValues { it.value.size }
                        SurahPeriodActivityUi(
                            surahNumber = surahNumber,
                            surahLabel = surahNameByNumber[surahNumber] ?: "Surah $surahNumber",
                            count7 = sumInWindow(countsByDay, today, 7),
                            count30 = sumInWindow(countsByDay, today, 30),
                            count90 = sumInWindow(countsByDay, today, 90)
                        )
                    }
                    .sortedByDescending { it.count30 }

                val weakSurahReport = weakKeys
                    .mapNotNull { parseAyahKey(it) }
                    .groupBy { it.surahNumber }
                    .map { (surahNumber, ayahs) ->
                        WeakSurahReportUi(
                            surahNumber = surahNumber,
                            surahLabel = surahNameByNumber[surahNumber] ?: "Surah $surahNumber",
                            weakCount = ayahs.size,
                            sampleAyahNumbers = ayahs.map { it.ayahNumber }.sorted().take(6)
                        )
                    }
                    .sortedByDescending { it.weakCount }
                val recitationAttempts = recitationStats.attempts
                val recitationMatches = recitationStats.matched
                val recitationAccuracy = if (recitationAttempts > 0) {
                    recitationMatches.toFloat() / recitationAttempts.toFloat()
                } else {
                    0f
                }
                val recitationConfidence = if (recitationMatches > 0) {
                    recitationStats.confidenceSum / recitationMatches.toFloat()
                } else {
                    0f
                }
                val recitationIssueRate = if (recitationMatches > 0) {
                    recitationStats.issueCount.toFloat() / recitationMatches.toFloat()
                } else {
                    0f
                }
                val issuePenalty = (recitationIssueRate / 3f).coerceIn(0f, 0.35f)
                val recitationScore = (
                    (recitationAccuracy * 0.45f) +
                        (recitationConfidence * 0.55f) -
                        issuePenalty
                    ).coerceIn(0f, 1f).times(100f).toInt()
                val recitationTopSurahs = recitationStats.surahStats
                    .entries
                    .sortedByDescending { it.value.attempts }
                    .take(3)
                    .map { (surahNumber, value) ->
                        val accuracy = if (value.attempts > 0) {
                            value.matched.toFloat() / value.attempts.toFloat()
                        } else {
                            0f
                        }
                        RecitationTopSurahUi(
                            surahNumber = surahNumber,
                            surahLabel = surahNameByNumber[surahNumber] ?: "Surah $surahNumber",
                            attempts = value.attempts,
                            accuracy = accuracy
                        )
                    }

                totalsState.copy(
                    learnedSurahs = learnedSurahs,
                    learningSurahs = learningSurahs,
                    learnedAyahs = learnedAyahs,
                    learningAyahs = learningAyahs,
                    streakDays = streakDays,
                    bestDayCount = bestDayCount,
                    avgPerDay = avgPerDay,
                    focusMinutes = focusMinutes,
                    weakAyahCount = weakAyahCount,
                    last14DailyCounts = last14DailyCounts,
                    recitationSessions = recitationStats.sessions,
                    recitationAttempts = recitationAttempts,
                    recitationMatches = recitationMatches,
                    recitationAccuracy = recitationAccuracy,
                    recitationConfidence = recitationConfidence,
                    recitationIssueRate = recitationIssueRate,
                    recitationBestStreak = recitationStats.bestStreak,
                    recitationScore = recitationScore,
                    recitationTopSurahs = recitationTopSurahs,
                    ayahUpdates7 = ayahUpdates7,
                    ayahUpdates30 = ayahUpdates30,
                    ayahUpdates90 = ayahUpdates90,
                    focusMinutes7 = focusMinutes7,
                    focusMinutes30 = focusMinutes30,
                    focusMinutes90 = focusMinutes90,
                    surahPeriodActivity = surahPeriodActivity,
                    weakSurahReport = weakSurahReport
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

    private fun sumInWindow(
        valuesByDay: Map<kotlinx.datetime.LocalDate, Int>,
        today: kotlinx.datetime.LocalDate,
        windowDays: Int,
    ): Int {
        val windowStart = today.minus(DatePeriod(days = windowDays - 1))
        return valuesByDay.filterKeys { it >= windowStart }.values.sum()
    }

    private fun buildWindowCounts(
        activityDays: Map<kotlinx.datetime.LocalDate, Int>,
        today: kotlinx.datetime.LocalDate,
        windowDays: Int,
    ): List<Int> {
        return (windowDays - 1 downTo 0).map { offset ->
            val day = today.minus(DatePeriod(days = offset))
            activityDays[day] ?: 0
        }
    }
}
