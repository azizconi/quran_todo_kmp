package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.data.bundled.QuranFacts

/** Whether the lesson of today finished a surah of the plan (task U13: "Surah X is complete" on the summary). */
object SurahCompletion {
    /**
     * The surah that the new passage learned today completed, or null. It is that surah when [newBlock] was learned today
     * ([LessonNewState.DONE]), the surah is in the plan ([planSurahs]), every ayah of it is covered now ([covered], the ayahs
     * per surah, with today's passage already in) and it was not fully covered before today's passage (the ayahs of the passage
     * taken out). A surah completed on an earlier day is not reported again: today's new passage is then of another surah.
     */
    fun finishedToday(
        newBlock: LessonNew?,
        planSurahs: Collection<Int>,
        covered: Map<Int, Set<Int>>,
        ayahCount: (Int) -> Int = { QuranFacts.ayahCount(it) },
    ): Int? {
        newBlock ?: return null
        if (newBlock.state != LessonNewState.DONE) return null
        val surah = newBlock.surah
        if (surah !in planSurahs) return null
        val total = ayahCount(surah)
        if (total <= 0) return null
        val now = covered[surah].orEmpty()
        if ((1..total).any { it !in now }) return null
        val today = newBlock.startAyah..newBlock.endAyah
        val before = now.count { it !in today }
        return if (before >= total) null else surah
    }
}
