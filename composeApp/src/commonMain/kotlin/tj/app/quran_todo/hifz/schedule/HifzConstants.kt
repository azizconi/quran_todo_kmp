package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.PassageStage
import kotlin.math.ceil

/**
 * Every number of the scheduler in one place. The calibrated ones were measured on the bundled text
 * (`composeResources/files/quran/quran-uthmani.tsv`, 6236 ayahs, 715 000 characters = Unicode code points of the
 * Arabic text with the diacritics, exactly what SQLite `LENGTH(text)` returns); `RealTextCalibrationTest` checks
 * them against the file.
 */
object HifzConstants {
    /** "Next time in N days" of the ladder stages R1, R3, R7, R14, R30. */
    val LADDER_INTERVALS: List<Int> = listOf(1, 3, 7, 14, 30)

    /** A difficult ayah goes away after this many successful repetitions of its passage. */
    const val HARD_CLEARS_AFTER = 2

    // ---- Time estimate: seconds = chars / CHARS_PER_SECOND * multiplier + CARD_OVERHEAD_SECONDS ----

    /**
     * Speed of a calm recitation. Calibrated so that Al-Fatiha with the basmala (7 ayahs, 285 characters in the
     * bundled text) takes 50 seconds: 285 / 50 = 5.7 characters per second.
     */
    const val CHARS_PER_SECOND = 5.7

    /** New passage: 3 times per ayah + 2 times the whole passage + reading + recalling. */
    const val NEW_MULTIPLIER = 7

    /** A repetition (the ladder and the manzil): recall once, check against the text. */
    const val REVIEW_MULTIPLIER = 2

    /** Turning to the card, opening, grading: seconds per card. */
    const val CARD_OVERHEAD_SECONDS = 15

    /** A lesson never takes more than this (the estimate); the rest is moved to the next days. */
    const val LESSON_BUDGET_MINUTES = 25

    // ---- Passages ----

    /**
     * Characters on an average page of the mushaf: 715 000 characters / 604 pages = 1183.8 in the bundled text (median 1187).
     * The screens turn a weight into pages with it ("about N pages a day" = old weight / CHARS_PER_PAGE / manzilDays).
     */
    const val CHARS_PER_PAGE = 1184

    /** The biggest weight (characters) of an OLD passage after merging: about one page, [CHARS_PER_PAGE] rounded to 1200. */
    const val MAX_OLD_PASSAGE_CHARS = 1200

    // ---- Plan ----

    const val DAILY_NEW_MIN = 1
    const val DAILY_NEW_MAX = 20
    const val DAILY_NEW_DEFAULT = 5
    const val MANZIL_DAYS_MIN = 3
    const val MANZIL_DAYS_MAX = 30
    const val MANZIL_DAYS_DEFAULT = 7

    /** A surah of no more than `dailyNewAyahs * 1.5` ayahs that is not started is learned whole. */
    const val WHOLE_SURAH_FACTOR_NUM = 3 // 3 / 2 = 1.5
    const val WHOLE_SURAH_FACTOR_DEN = 2

    /** Seconds of a calm recitation of [chars] characters (the multiplier 1, no overhead). */
    fun recitationSeconds(chars: Int): Double = chars / CHARS_PER_SECOND

    /** Estimate of a new passage of [chars] characters, in seconds. */
    fun newSeconds(chars: Int): Int = ceil(recitationSeconds(chars) * NEW_MULTIPLIER).toInt() + CARD_OVERHEAD_SECONDS

    /** Estimate of a repetition of [chars] characters, in seconds. */
    fun reviewSeconds(chars: Int): Int =
        ceil(recitationSeconds(chars) * REVIEW_MULTIPLIER).toInt() + CARD_OVERHEAD_SECONDS

    const val LESSON_BUDGET_SECONDS = LESSON_BUDGET_MINUTES * 60

    /** Seconds to whole minutes, rounded up. */
    fun minutes(seconds: Int): Int = (seconds + 59) / 60

    /** Interval of a ladder stage in days (null for [PassageStage.OLD]). */
    fun intervalDays(stage: PassageStage): Int? = stage.intervalDays
}
