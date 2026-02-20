package tj.app.quran_todo.common.recitation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tj.app.quran_todo.common.settings.UserSettingsStorage

@Serializable
data class SurahRecitationStats(
    val sessions: Int = 0,
    val attempts: Int = 0,
    val matched: Int = 0,
    val confidenceSum: Float = 0f,
    val issueCount: Int = 0,
    val bestStreak: Int = 0,
    val lastSessionAt: Long = 0L,
)

@Serializable
data class RecitationStatsSnapshot(
    val sessions: Int = 0,
    val attempts: Int = 0,
    val matched: Int = 0,
    val confidenceSum: Float = 0f,
    val issueCount: Int = 0,
    val bestStreak: Int = 0,
    val lastSessionAt: Long = 0L,
    val surahStats: Map<Int, SurahRecitationStats> = emptyMap(),
)

object RecitationPerformanceStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val _snapshot = MutableStateFlow(loadSnapshot())
    val snapshot: StateFlow<RecitationStatsSnapshot> = _snapshot.asStateFlow()

    fun recordSession(
        surahNumber: Int,
        attempts: Int,
        matched: Int,
        confidenceSum: Float,
        issueCount: Int,
        bestStreak: Int,
    ) {
        if (attempts <= 0 || surahNumber <= 0) return
        val safeAttempts = attempts.coerceAtLeast(0)
        val safeMatched = matched.coerceIn(0, safeAttempts)
        val safeConfidenceSum = confidenceSum.coerceAtLeast(0f)
        val safeIssueCount = issueCount.coerceAtLeast(0)
        val sessionTime = Clock.System.now().toEpochMilliseconds()

        val current = _snapshot.value
        val currentSurah = current.surahStats[surahNumber] ?: SurahRecitationStats()
        val updatedSurah = currentSurah.copy(
            sessions = currentSurah.sessions + 1,
            attempts = currentSurah.attempts + safeAttempts,
            matched = currentSurah.matched + safeMatched,
            confidenceSum = currentSurah.confidenceSum + safeConfidenceSum,
            issueCount = currentSurah.issueCount + safeIssueCount,
            bestStreak = maxOf(currentSurah.bestStreak, bestStreak.coerceAtLeast(0)),
            lastSessionAt = sessionTime
        )

        val updated = current.copy(
            sessions = current.sessions + 1,
            attempts = current.attempts + safeAttempts,
            matched = current.matched + safeMatched,
            confidenceSum = current.confidenceSum + safeConfidenceSum,
            issueCount = current.issueCount + safeIssueCount,
            bestStreak = maxOf(current.bestStreak, bestStreak.coerceAtLeast(0)),
            lastSessionAt = sessionTime,
            surahStats = current.surahStats + (surahNumber to updatedSurah)
        )

        _snapshot.value = updated
        persist(updated)
    }

    fun refresh() {
        _snapshot.value = loadSnapshot()
    }

    private fun loadSnapshot(): RecitationStatsSnapshot {
        val raw = UserSettingsStorage.getRecitationMetricsJson() ?: return RecitationStatsSnapshot()
        return runCatching {
            json.decodeFromString<RecitationStatsSnapshot>(raw)
        }.getOrElse {
            RecitationStatsSnapshot()
        }
    }

    private fun persist(snapshot: RecitationStatsSnapshot) {
        runCatching {
            UserSettingsStorage.saveRecitationMetricsJson(json.encodeToString(snapshot))
        }
    }
}
