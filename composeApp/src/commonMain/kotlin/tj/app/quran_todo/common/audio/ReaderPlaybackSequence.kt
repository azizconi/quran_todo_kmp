package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.settings.ReaderPracticeSettings
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * One reading of an ayah. [echoRound] is which round of the echo mode this reading is (1 up to
 * [ReaderPracticeSettings.echoRepeats]); 0 outside the echo mode.
 */
data class ReaderPlaybackStep(val index: Int, val repeatsRemaining: Int, val echoRound: Int = 0)

/**
 * The echo mode, "I repeat after the reciter" (task F1b). The plan, as the session plays it:
 *
 * - the reciter reads an ayah; then silence of [silenceMs] (the length of the ayah times [SilenceFactor], at least
 *   [MinSilenceMs]; at a slower or faster playback speed the length it really takes) in which the person repeats it;
 * - that is one round; an ayah gets [ReaderPracticeSettings.echoRepeats] rounds (1..3), then the next ayah comes;
 * - the silence takes the place of the plain pause between ayahs ([ReaderPracticeSettings.pauseSeconds]);
 * - the last ayah is followed by its silence too, and only then the sequence is over: the person still has to repeat it;
 * - with a repeated passage ([ReaderPracticeSettings.rangeEnabled]) the rounds are per ayah and the passage repeats as before;
 *   without a passage the rounds replace the repeat of each ayah (an endless repeat stays endless);
 * - an explicit script (the lesson's "listen") has no rounds of its own: the silence follows every reading of the script.
 *
 * The functions here are pure; `ReaderAudioSession` does the playing and the waiting.
 */
object EchoPlan {
    /** The silence is this many times the length of the ayah. */
    const val SilenceFactor = 1.2

    /** The silence is never shorter than this, so a very short ayah still leaves time to say it. */
    const val MinSilenceMs = 2_000L

    /** The allowed numbers of rounds per ayah. */
    val Repeats: Set<Int> = setOf(1, 2, 3)

    /**
     * The silence after an ayah that lasts [durationMs] of the recording, at playback [speed] (1 = normal): the time the ayah
     * took, times [SilenceFactor], not under [MinSilenceMs]. An unknown length (0) gives the minimum.
     */
    fun silenceMs(durationMs: Long, speed: Float = 1f): Long {
        val taken = if (durationMs <= 0L) 0.0 else durationMs / speed.coerceIn(0.25f, 4f).toDouble()
        return maxOf(MinSilenceMs, (taken * SilenceFactor).roundToLong())
    }

    /** The seconds shown for a silence of [ms]: rounded up, so the last second does not read "0". */
    fun seconds(ms: Long): Int = ceil(ms / 1000.0).toInt()
}

/**
 * The repeat counter a fresh reading of an ayah starts with: the count of the passage in the passage mode; in the ayah mode
 * the repeat of the preferences, except that the echo mode has its own rounds and keeps only an endless repeat.
 */
fun freshRepeats(preferences: SurahReaderPreferences, practice: ReaderPracticeSettings): Int = when {
    practice.rangeEnabled -> practice.rangeRepeatCount
    practice.echo && preferences.repeatCount != -1 -> 1
    else -> preferences.repeatCount
}

/** The first step of an ayah: the repeat counter of [freshRepeats] and, in the echo mode, round 1. */
fun freshStep(index: Int, preferences: SurahReaderPreferences, practice: ReaderPracticeSettings): ReaderPlaybackStep =
    ReaderPlaybackStep(index, freshRepeats(preferences, practice), if (practice.echo) 1 else 0)

/**
 * Range repetition counts whole passes, while the existing ayah mode repeats each ayah. With [ReaderPracticeSettings.echo]
 * every ayah is read [ReaderPracticeSettings.echoRepeats] times (rounds, see [EchoPlan]) before the rules below move on;
 * [echoRound] is the round of the reading that has just finished. The caller adds the silence ([EchoPlan.silenceMs]) after
 * every reading, including the one that ends the sequence.
 */
fun nextReaderPlaybackStep(
    currentIndex: Int,
    repeatsRemaining: Int,
    ayahCount: Int,
    preferences: SurahReaderPreferences,
    practice: ReaderPracticeSettings,
    echoRound: Int = 0,
): ReaderPlaybackStep? {
    if (ayahCount <= 0) return null
    val normalized = practice.normalized(ayahCount)
    if (normalized.echo && echoRound in 1 until normalized.echoRepeats) {
        return ReaderPlaybackStep(currentIndex, repeatsRemaining, echoRound + 1)
    }
    val round = if (normalized.echo) 1 else 0
    if (normalized.rangeEnabled) {
        val first = normalized.startAyah - 1
        val last = normalized.endAyah - 1
        return when {
            currentIndex !in first..last -> ReaderPlaybackStep(first, normalized.rangeRepeatCount, round)
            currentIndex < last -> ReaderPlaybackStep(currentIndex + 1, repeatsRemaining, round)
            repeatsRemaining == -1 -> ReaderPlaybackStep(first, -1, round)
            repeatsRemaining > 1 -> ReaderPlaybackStep(first, repeatsRemaining - 1, round)
            else -> null
        }
    }
    return when {
        repeatsRemaining == -1 -> ReaderPlaybackStep(currentIndex, -1, round)
        // The rounds of the echo mode take the place of the repeat of each ayah (only "endless" is kept, above).
        !normalized.echo && repeatsRemaining > 1 -> ReaderPlaybackStep(currentIndex, repeatsRemaining - 1)
        preferences.autoAdvanceAyahs && currentIndex + 1 < ayahCount ->
            ReaderPlaybackStep(currentIndex + 1, freshRepeats(preferences, normalized), round)
        else -> null
    }
}
