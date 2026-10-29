package tj.app.quran_todo.hifz.backup

import tj.app.quran_todo.hifz.model.HifzScope

/**
 * What the person is shown before an import replaces the data of the phone: the numbers of the file, no sentences (the
 * wording and the plurals are the job of the screen, in five languages).
 *
 * "Copy of 12 March 2026: plan "Juz Amma", 34 passages, 2 hard ayahs, 120 notes."
 */
data class BackupPreview(
    /** When the file was made, epoch milliseconds. */
    val exportedAt: Long,
    /** The preset or custom plan of the file; null when the file has no plan. */
    val planScope: HifzScope?,
    /** Surahs in the queue of the plan (0 without a plan). */
    val planSurahs: Int,
    val passages: Int,
    /** Difficult ayahs (rows of `hard`). */
    val hardAyahs: Int,
    val notes: Int,
    /** Records of the review log. */
    val reviews: Int,
) {
    /** The file holds no data at all: importing it empties the phone. */
    val isEmpty: Boolean get() = planScope == null && passages == 0 && hardAyahs == 0 && notes == 0 && reviews == 0
}

fun VerifiedBackup.preview(): BackupPreview {
    val plan = file.hifz.plan
    return BackupPreview(
        exportedAt = file.exportedAt,
        planScope = plan?.let { p -> HifzScope.entries.first { it.name == p.scope } },
        planSurahs = plan?.surahOrder?.size ?: 0,
        passages = file.hifz.passages.size,
        hardAyahs = file.hifz.hard.size,
        notes = file.notes.size,
        reviews = file.hifz.reviewLog.size,
    )
}
