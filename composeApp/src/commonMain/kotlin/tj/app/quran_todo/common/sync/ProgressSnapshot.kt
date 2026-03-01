package tj.app.quran_todo.common.sync

import kotlinx.serialization.Serializable

@Serializable
data class ProgressSnapshot(
    val version: Int = 1,
    val createdAt: Long,
    val settings: SettingsSnapshot,
    val weakAyahKeys: Set<String>,
    val recitationMetricsJson: String?,
    val surahTodos: List<SurahTodoSnapshot>,
    val ayahTodos: List<AyahTodoSnapshot>,
    val ayahReviews: List<AyahReviewSnapshot>,
    val ayahNotes: List<AyahNoteSnapshot>,
)

@Serializable
data class SettingsSnapshot(
    val dailyGoal: Int,
    val focusMinutes: Int,
    val remindersEnabled: Boolean,
    val targetAyahs: Int,
    val targetEpochDay: Int,
    val examModeEnabled: Boolean,
)

@Serializable
data class SurahTodoSnapshot(
    val surahNumber: Int,
    val status: String,
)

@Serializable
data class AyahTodoSnapshot(
    val ayahNumber: Int,
    val surahNumber: Int,
    val status: String,
    val updatedAt: Long,
)

@Serializable
data class AyahReviewSnapshot(
    val ayahNumber: Int,
    val surahNumber: Int,
    val nextReviewAt: Long,
    val intervalIndex: Int,
    val lastReviewedAt: Long,
)

@Serializable
data class AyahNoteSnapshot(
    val ayahNumber: Int,
    val surahNumber: Int,
    val note: String,
    val updatedAt: Long,
)
