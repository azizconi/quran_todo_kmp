package tj.app.quran_todo.hifz.backup

import tj.app.quran_todo.data.database.entity.hifz.HifzPassageEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageWithHard
import tj.app.quran_todo.data.database.entity.hifz.ReviewLogEntity
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `ReviewKind.LISTEN` ("Listen to the old", task F1b) in the transfer file (B5): a record of listening is exported and imported
 * like any other, and a file written before it existed (no `LISTEN` anywhere) is read as it always was. The format version stays 1:
 * a new value of an existing field, the file keeps every other field as it was.
 */
class BackupListenTest {
    private val passage = PassageWithHard(HifzPassageEntity(2, 113, 1, 5, 20400, PassageStage.OLD, null, 20490, 0), emptyList())

    private val log = listOf(
        ReviewLogEntity(1, 2, 113, 1, 5, ReviewKind.OLD, ReviewGrade.GOOD, 20490, 1_772_000_000_000),
        ReviewLogEntity(2, 2, 113, 1, 5, ReviewKind.LISTEN, ReviewGrade.GOOD, 20502, 1_772_900_000_000),
    )

    private fun exported(): BackupFile {
        val data = BackupMapping.toBackupData(null, listOf(passage), log, emptyList(), emptyList())
        return BackupFile(
            format = BackupFormat.NAME,
            formatVersion = BackupFormat.VERSION,
            appVersion = "1.0.2 (7)",
            exportedAt = 1_773_312_000_000,
            dbVersion = BackupFormat.DB_VERSION,
            hifz = data.hifz,
            notes = data.notes,
        )
    }

    @Test
    fun aListenRecordIsExportedByName() {
        val text = BackupCodec.encode(exported())
        assertTrue("\"kind\":\"LISTEN\"" in text, text)
        assertTrue("\"formatVersion\":1" in text)
    }

    @Test
    fun aListenRecordComesBackThroughAnImportUnchanged() {
        val parse = BackupCodec.decode(BackupCodec.encode(exported()))
        assertIs<BackupParse.Ok>(parse)
        val rows = BackupMapping.toRows(parse.backup.file)
        assertEquals(log, rows.log)
        assertEquals(ReviewKind.LISTEN, rows.log.last().kind)
        // The passage it is about is in the file, so the record keeps its pointer.
        assertEquals(2L, rows.log.last().passageId)
    }

    @Test
    fun aListenRecordOfAPassageThatIsGoneLosesOnlyThePointer() {
        val withoutPassage = exported().let { it.copy(hifz = it.hifz.copy(passages = emptyList())) }
        val rows = BackupMapping.toRows(withoutPassage)
        val listen = rows.log.last()
        assertEquals(ReviewKind.LISTEN, listen.kind)
        assertEquals(null, listen.passageId)
        assertEquals(113, listen.surah)
    }

    @Test
    fun aFileFromBeforeListeningIsStillRead() {
        val old = """{"format":"quran-hifz-backup","formatVersion":1,"appVersion":"1.0.1 (6)","exportedAt":1773312000000,"dbVersion":11,
            |"hifz":{"passages":[{"id":1,"surah":114,"startAyah":1,"endAyah":6,"learnedEpochDay":20500,"stage":"R3",
            |"nextDueEpochDay":20503,"lastReviewedEpochDay":20500,"lapses":0}],"hard":[],
            |"reviewLog":[{"id":1,"passageId":1,"surah":114,"startAyah":1,"endAyah":6,"kind":"NEW","grade":"GOOD","epochDay":20500,"timestamp":1772000000000},
            |{"id":2,"surah":2,"startAyah":255,"endAyah":255,"kind":"CHECK","grade":"HARD","epochDay":20501,"timestamp":1772086400000}],
            |"meta":{"legacy_import":"done"}},"notes":[]}""".trimMargin()
        val parse = BackupCodec.decode(old)
        assertIs<BackupParse.Ok>(parse)
        val rows = BackupMapping.toRows(parse.backup.file)
        assertEquals(listOf(ReviewKind.NEW, ReviewKind.CHECK), rows.log.map { it.kind })
        assertEquals(1, BackupFormat.VERSION)
    }
}
