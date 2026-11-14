package tj.app.quran_todo.bridge

import kotlinx.coroutines.flow.first
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.hifz.EpochDays
import tj.app.quran_todo.hifz.HifzPresets
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.khatm.KhatmPlan
import tj.app.quran_todo.ui.platform.iosPrefs
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * DEBUG ONLY (the Swift side calls it behind `#if DEBUG` and the launch argument `-qhSeedDemo`): passes the onboarding and fills a
 * fresh simulator with a believable history, so a screenshot of the plan, the progress, the drill, the quiz and the analytics has
 * something to show. Never reachable in a release build.
 */
object IosDebugSeed {
    /** Marks the onboarding as passed. Call before `IosStartup.resolveGate()`. */
    fun passOnboarding() {
        iosPrefs(UiPrefs.PREFS_NAME).putBoolean(UiPrefs.KEY_ONBOARDING_COMPLETED, true)
        iosPrefs(UiPrefs.PREFS_NAME).putBoolean(UiPrefs.KEY_WHATS_NEW_SHOWN, true)
    }

    /**
     * Juz Amma at 5 ayahs a day, the short surahs 112..114 and 105..111 known by heart (OLD), a few recent passages learned on past
     * days, reviews over the last weeks, some difficult ayahs. [withKhatm] adds a reading plan too. Skips when a plan already exists.
     */
    suspend fun seed(withKhatm: Boolean) {
        val koin = KoinPlatform.getKoin()
        val hifz: HifzRepository = koin.get()
        val today = EpochDays.now()
        if (withKhatm && hifz.khatm().first() == null) {
            hifz.saveKhatm(KhatmPlan(startEpochDay = today - 5, targetEpochDay = today + 24))
            hifz.markKhatmUpTo(40, today)
        }
        if (hifz.plan().first() != null) return

        hifz.setPlan(HifzScope.JUZ_30, HifzPresets.order(HifzScope.JUZ_30), dailyNewAyahs = 5, targetEpochDay = null, manzilDays = 7, today = today - 40)
        for (surah in 105..114) hifz.markKnown(surah, today = today - 60)
        // New passages on past days, then reviews of them: the log, the streak, the heat map.
        val learned = listOf(104 to (1 to 5), 103 to (1 to 3), 102 to (1 to 8), 101 to (1 to 11))
        learned.forEachIndexed { index, (surah, range) ->
            val day = today - 30 + index * 4
            hifz.completeNew(surah, range.first, range.second, ReviewGrade.GOOD, today = day)
        }
        for (offset in 24 downTo 1) {
            if (offset % 5 == 3) continue
            val day = today - offset
            if (offset % 2 == 0) {
                // A check is the simplest record on any day (the scheduler logs the rest).
                hifz.logCheck(105 + (offset % 8), 1, 3, if (offset % 7 == 0) ReviewGrade.HARD else ReviewGrade.GOOD, today = day)
            }
        }
        hifz.markHard(114, setOf(2, 3))
        hifz.markHard(108, setOf(2))
        hifz.markHard(111, setOf(3, 4))
        hifz.markHard(105, setOf(3))
    }
}
