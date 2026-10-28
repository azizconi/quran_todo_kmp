package tj.app.quran_todo.common.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A range belongs to its surah; it must never leak into the next surah. */
@Serializable
data class ReaderPracticeSettings(
    val rangeEnabled: Boolean = false,
    val startAyah: Int = 1,
    val endAyah: Int = 1,
    val rangeRepeatCount: Int = 5,
    val pauseSeconds: Int = 0,
) {
    fun normalized(ayahCount: Int): ReaderPracticeSettings {
        val last = ayahCount.coerceAtLeast(1)
        val start = startAyah.coerceIn(1, last)
        return copy(
            startAyah = start,
            endAyah = endAyah.coerceIn(start, last),
            rangeRepeatCount = rangeRepeatCount.takeIf { it in setOf(1, 3, 5, -1) } ?: 5,
            pauseSeconds = pauseSeconds.takeIf { it in setOf(0, 3, 5, 10) } ?: 0,
        )
    }

    fun save(surahNumber: Int) {
        val values = savedValues().toMutableMap()
        values[surahNumber] = this
        UserSettingsStorage.saveReaderPracticeJson(json.encodeToString(values))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun savedValues(): Map<Int, ReaderPracticeSettings> = runCatching {
            json.decodeFromString<Map<Int, ReaderPracticeSettings>>(
                UserSettingsStorage.getReaderPracticeJson() ?: "{}"
            )
        }.getOrDefault(emptyMap())

        fun load(surahNumber: Int, ayahCount: Int): ReaderPracticeSettings =
            (savedValues()[surahNumber] ?: ReaderPracticeSettings(
                endAyah = ayahCount.coerceAtMost(10),
            )).normalized(ayahCount)
    }
}
