package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences

data class ReaderPlaybackStep(val index: Int, val repeatsRemaining: Int)

/** Range repetition counts whole passes, while the existing ayah mode repeats each ayah. */
fun nextReaderPlaybackStep(
    currentIndex: Int,
    repeatsRemaining: Int,
    ayahCount: Int,
    preferences: SurahReaderPreferences,
    practice: ReaderPracticeSettings,
): ReaderPlaybackStep? {
    if (ayahCount <= 0) return null
    val normalized = practice.normalized(ayahCount)
    if (normalized.rangeEnabled) {
        val first = normalized.startAyah - 1
        val last = normalized.endAyah - 1
        return when {
            currentIndex !in first..last -> ReaderPlaybackStep(first, normalized.rangeRepeatCount)
            currentIndex < last -> ReaderPlaybackStep(currentIndex + 1, repeatsRemaining)
            repeatsRemaining == -1 -> ReaderPlaybackStep(first, -1)
            repeatsRemaining > 1 -> ReaderPlaybackStep(first, repeatsRemaining - 1)
            else -> null
        }
    }
    return when {
        repeatsRemaining == -1 -> ReaderPlaybackStep(currentIndex, -1)
        repeatsRemaining > 1 -> ReaderPlaybackStep(currentIndex, repeatsRemaining - 1)
        preferences.autoAdvanceAyahs && currentIndex + 1 < ayahCount ->
            ReaderPlaybackStep(currentIndex + 1, preferences.repeatCount)
        else -> null
    }
}
