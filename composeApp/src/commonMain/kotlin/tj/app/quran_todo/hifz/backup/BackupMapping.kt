package tj.app.quran_todo.hifz.backup

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.hifz.HifzMetaEntity
import tj.app.quran_todo.data.database.entity.hifz.HifzPassageEntity
import tj.app.quran_todo.data.database.entity.hifz.HifzPlanEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageHardEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageWithHard
import tj.app.quran_todo.data.database.entity.hifz.ReviewLogEntity
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind

/** The rows an import writes: the checked file turned into table entities. */
internal class ImportRows(
    val plan: HifzPlanEntity?,
    val passages: List<HifzPassageEntity>,
    val hard: List<PassageHardEntity>,
    val log: List<ReviewLogEntity>,
    val meta: List<HifzMetaEntity>,
    val notes: List<AyahNoteEntity>,
)

/**
 * Table rows <-> the records of the file. Pure functions; the repository only reads and writes the rows.
 *
 * The file is deterministic: records are sorted (passages and log by id, hard by passage and ayah, notes by surah and ayah,
 * meta by key), so two exports of the same data are the same text.
 */
internal object BackupMapping {
    const val META_LEGACY_IMPORT = "legacy_import"
    const val META_DONE = "done"

    fun toBackupData(
        plan: HifzPlanEntity?,
        passages: List<PassageWithHard>,
        log: List<ReviewLogEntity>,
        meta: List<HifzMetaEntity>,
        notes: List<AyahNoteEntity>,
    ): BackupData {
        val sortedPassages = passages.sortedBy { it.passage.id }
        return BackupData(
            hifz = BackupHifz(
                plan = plan?.let {
                    BackupPlan(
                        scope = it.scope.name,
                        surahOrder = it.surahOrder.split(',').mapNotNull { number -> number.trim().toIntOrNull() },
                        dailyNewAyahs = it.dailyNewAyahs,
                        targetEpochDay = it.targetEpochDay,
                        startedEpochDay = it.startedEpochDay,
                        manzilDays = it.manzilDays,
                    )
                },
                passages = sortedPassages.map { (p, _) ->
                    BackupPassage(
                        id = p.id,
                        surah = p.surah,
                        startAyah = p.startAyah,
                        endAyah = p.endAyah,
                        learnedEpochDay = p.learnedEpochDay,
                        stage = p.stage.name,
                        nextDueEpochDay = p.nextDueEpochDay,
                        lastReviewedEpochDay = p.lastReviewedEpochDay,
                        lapses = p.lapses,
                    )
                },
                hard = sortedPassages
                    .flatMap { it.hard }
                    .sortedWith(compareBy({ it.passageId }, { it.ayahInSurah }))
                    .map { BackupHard(passageId = it.passageId, ayah = it.ayahInSurah, clearsAfter = it.clearsAfter) },
                reviewLog = log.sortedBy { it.id }.map {
                    BackupLogEntry(
                        id = it.id,
                        passageId = it.passageId,
                        surah = it.surah,
                        startAyah = it.startAyah,
                        endAyah = it.endAyah,
                        kind = it.kind.name,
                        grade = it.grade.name,
                        epochDay = it.epochDay,
                        timestamp = it.timestamp,
                    )
                },
                meta = meta.sortedBy { it.key }.associate { it.key to it.value },
            ),
            notes = notes.mapNotNull { note ->
                // The table keys a note by the global ayah number; the file by (surah, ayah in the surah).
                val ayah = note.ayahNumber - QuranFacts.firstAyahNumber(note.surahNumber) + 1
                if (ayah in 1..QuranFacts.ayahCount(note.surahNumber)) {
                    BackupNote(surah = note.surahNumber, ayah = ayah, text = note.note, updatedAt = note.updatedAt)
                } else {
                    // A row of a number that is not an ayah of its surah cannot be shown anywhere: it is not written.
                    null
                }
            }.sortedWith(compareBy({ it.surah }, { it.ayah })),
        )
    }

    /**
     * The rows of a checked file. A record of the log that points to a passage that is not in the file (it was deleted or merged
     * on the phone the file comes from) loses the pointer: a database hands out ids that count up, and a fresh phone could give
     * the same id to a new passage, which would then wear the history of the old one.
     * `legacy_import = done` is always set, so that the one-time conversion of the old tables never runs over the imported data.
     */
    fun toRows(file: BackupFile): ImportRows {
        val hifz = file.hifz
        val passageIds = hifz.passages.mapTo(HashSet()) { it.id }
        val meta = LinkedHashMap(hifz.meta)
        meta[META_LEGACY_IMPORT] = META_DONE
        return ImportRows(
            plan = hifz.plan?.let {
                HifzPlanEntity(
                    id = HifzPlanEntity.PLAN_ID,
                    scope = HifzScope.valueOf(it.scope),
                    surahOrder = it.surahOrder.joinToString(","),
                    dailyNewAyahs = it.dailyNewAyahs,
                    targetEpochDay = it.targetEpochDay,
                    startedEpochDay = it.startedEpochDay,
                    manzilDays = it.manzilDays,
                )
            },
            passages = hifz.passages.map {
                HifzPassageEntity(
                    id = it.id,
                    surah = it.surah,
                    startAyah = it.startAyah,
                    endAyah = it.endAyah,
                    learnedEpochDay = it.learnedEpochDay,
                    stage = PassageStage.valueOf(it.stage),
                    nextDueEpochDay = if (it.stage == PassageStage.OLD.name) null else it.nextDueEpochDay,
                    lastReviewedEpochDay = it.lastReviewedEpochDay,
                    lapses = it.lapses,
                )
            },
            hard = hifz.hard.map {
                PassageHardEntity(passageId = it.passageId, ayahInSurah = it.ayah, clearsAfter = it.clearsAfter)
            },
            log = hifz.reviewLog.map {
                ReviewLogEntity(
                    id = it.id,
                    passageId = it.passageId?.takeIf { id -> id in passageIds },
                    surah = it.surah,
                    startAyah = it.startAyah,
                    endAyah = it.endAyah,
                    kind = ReviewKind.valueOf(it.kind),
                    grade = ReviewGrade.valueOf(it.grade),
                    epochDay = it.epochDay,
                    timestamp = it.timestamp,
                )
            },
            meta = meta.map { (key, value) -> HifzMetaEntity(key, value) },
            notes = file.notes.map {
                AyahNoteEntity(
                    ayahNumber = QuranFacts.firstAyahNumber(it.surah) + it.ayah - 1,
                    surahNumber = it.surah,
                    note = it.text,
                    updatedAt = it.updatedAt,
                )
            },
        )
    }
}
