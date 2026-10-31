package tj.app.quran_todo.hifz.backup

import kotlinx.datetime.TimeZone
import kotlinx.serialization.descriptors.elementNames
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupCodecTest {
    // ---- A valid file ----

    private val settings = BackupSettings(
        language = "ru", themeMode = "DARK", themeFollowsSystem = false, palette = "FOREST", readingFont = "madinah_mushaf",
        readingFontSize = 30, dailyGoal = 7, showTranslation = true, highlightPlayingAyah = true, autoplayOnOpen = false,
        autoAdvanceAyahs = true, playbackSpeed = 1.25f, repeatCount = -1, readerLayout = "MUSHAF", keepScreenOn = true,
        reciter = "husary", lessonAyahRepeats = 3, lessonPassageRepeats = 2, hifzHint = "FIRST_WORD", hapticsEnabled = false,
        reduceMotion = true, tajweed = true, reminderEnabled = true, reminderMinuteOfDay = 1200,
    )

    private val passages = listOf(
        BackupPassage(1, 114, 1, 6, 20500, "R3", 20503, 20500, 0),
        BackupPassage(2, 113, 1, 5, 20400, "OLD", null, null, 2),
        BackupPassage(3, 2, 255, 255, 20499, "R1", 20501, 20499, 1),
    )

    private val file = BackupFile(
        format = BackupFormat.NAME,
        formatVersion = BackupFormat.VERSION,
        appVersion = "1.0.2 (7)",
        exportedAt = 1_773_312_000_000,
        dbVersion = BackupFormat.DB_VERSION,
        hifz = BackupHifz(
            plan = BackupPlan("JUZ_30", listOf(114, 113, 112), 5, null, 20500, 7),
            passages = passages,
            hard = listOf(BackupHard(1, 4, 2), BackupHard(3, 255, 1)),
            reviewLog = listOf(
                BackupLogEntry(1, 1, 114, 1, 6, "NEW", "GOOD", 20500, 1_772_000_000_000),
                BackupLogEntry(2, null, 2, 255, 255, "CHECK", "HARD", 20501, 1_772_086_400_000),
            ),
            meta = mapOf("legacy_import" to "done"),
        ),
        notes = listOf(BackupNote(2, 255, "Ayat al-Kursi", 1_772_000_000_000), BackupNote(114, 1, "", 5)),
        settings = settings,
    )

    private fun verified(f: BackupFile): VerifiedBackup {
        val parse = BackupCodec.verify(f)
        assertIs<BackupParse.Ok>(parse, "expected an accepted file but got ${(parse as? BackupParse.Failed)?.error}")
        return parse.backup
    }

    private fun errorOf(f: BackupFile): BackupError {
        val parse = BackupCodec.verify(f)
        assertIs<BackupParse.Failed>(parse, "expected a refused file")
        return parse.error
    }

    private fun decodeError(text: String): BackupError {
        val parse = BackupCodec.decode(text)
        assertIs<BackupParse.Failed>(parse, "expected a refused text")
        return parse.error
    }

    private fun withHifz(change: (BackupHifz) -> BackupHifz) = file.copy(hifz = change(file.hifz))

    private fun withPassages(change: (List<BackupPassage>) -> List<BackupPassage>) =
        withHifz { it.copy(passages = change(it.passages)) }

    // ---- Round trip ----

    @Test
    fun snapshotSurvivesTheTripThroughJson() {
        val text = BackupCodec.encode(file)
        val parse = BackupCodec.decode(text)
        assertIs<BackupParse.Ok>(parse)
        assertEquals(file, parse.backup.file)
    }

    @Test
    fun encodingIsDeterministicAndKeepsTheMarkersOfTheFormat() {
        val text = BackupCodec.encode(file)
        assertEquals(text, BackupCodec.encode(file))
        assertTrue("\"format\":\"quran-hifz-backup\"" in text)
        assertTrue("\"formatVersion\":1" in text)
        assertTrue("\"dbVersion\":11" in text)
        // Empty lists are written as lists (a missing list would make a file "damaged"), nulls are not written at all.
        assertTrue("\"hard\":[" in BackupCodec.encode(withHifz { it.copy(hard = emptyList()) }))
        assertFalse("null" in text, text)
    }

    @Test
    fun anEmptyBackupIsAValidBackup() {
        val empty = file.copy(
            hifz = BackupHifz(plan = null, passages = emptyList(), hard = emptyList(), reviewLog = emptyList(), meta = emptyMap()),
            notes = emptyList(),
            settings = BackupSettings(),
        )
        val parse = BackupCodec.decode(BackupCodec.encode(empty))
        assertIs<BackupParse.Ok>(parse)
        assertEquals(empty, parse.backup.file)
        assertTrue(parse.backup.preview().isEmpty)
    }

    @Test
    fun aByteOrderMarkInFrontIsIgnored() {
        assertIs<BackupParse.Ok>(BackupCodec.decode("﻿" + BackupCodec.encode(file)))
    }

    // ---- File name ----

    @Test
    fun fileNameHoldsTheCalendarDayOfTheExport() {
        val utc = TimeZone.UTC
        // 2026-03-12T10:00:00Z
        assertEquals("quran-hifz-2026-03-12.json", BackupFormat.fileName(1_773_309_600_000, utc))
        // The same instant at 22:00 +12:00 is still the 12th; at +14:00 it is already the 13th.
        assertEquals("quran-hifz-2026-03-12.json", BackupFormat.fileName(1_773_309_600_000, TimeZone.of("+12:00")))
        assertEquals("quran-hifz-2026-03-13.json", BackupFormat.fileName(1_773_309_600_000, TimeZone.of("+14:00")))
        assertEquals("quran-hifz-1970-01-01.json", BackupFormat.fileName(0, utc))
    }

    // ---- Which errors, in which order ----

    @Test
    fun aFileThatIsNotOursIsNotABackup() {
        assertEquals(BackupError.NotABackup, decodeError(""))
        assertEquals(BackupError.NotABackup, decodeError("   \n"))
        assertEquals(BackupError.NotABackup, decodeError("hello"))
        assertEquals(BackupError.NotABackup, decodeError("\u0089PNG\r\n"))
        assertEquals(BackupError.NotABackup, decodeError("null"))
        assertEquals(BackupError.NotABackup, decodeError("[1,2,3]"))
        assertEquals(BackupError.NotABackup, decodeError("{}"))
        assertEquals(BackupError.NotABackup, decodeError("""{"a":1}"""))
        assertEquals(BackupError.NotABackup, decodeError("""{"format":"other-app-backup","formatVersion":1}"""))
        assertEquals(BackupError.NotABackup, decodeError("""{"format":7,"formatVersion":1}"""))
        // Somebody else's JSON that happens to have a formatVersion.
        assertEquals(BackupError.NotABackup, decodeError("""{"formatVersion":1,"hifz":{}}"""))
    }

    @Test
    fun aNewerFormatIsRefusedWhateverItLooksLike() {
        val future = """{"format":"quran-hifz-backup","formatVersion":2,"somethingNew":[1,2,3]}"""
        assertEquals(BackupError.NewerVersion(2), decodeError(future))
        assertEquals(BackupError.NewerVersion(99), decodeError("""{"format":"quran-hifz-backup","formatVersion":99}"""))
        // A real file of today with the number raised.
        val raised = BackupCodec.encode(file).replace("\"formatVersion\":1", "\"formatVersion\":3")
        assertEquals(BackupError.NewerVersion(3), decodeError(raised))
        assertEquals(BackupError.NewerVersion(2), errorOf(file.copy(formatVersion = 2)))
    }

    @Test
    fun aDamagedFileIsCorrupted() {
        val text = BackupCodec.encode(file)
        // Cut off in the middle (a copy that did not finish): it is still recognised as ours.
        assertIs<BackupError.Corrupted>(decodeError(text.substring(0, text.length / 2)))
        // Valid JSON of ours with a field missing or of a wrong type.
        assertIs<BackupError.Corrupted>(decodeError("""{"format":"quran-hifz-backup","formatVersion":1}"""))
        assertIs<BackupError.Corrupted>(decodeError("""{"format":"quran-hifz-backup"}"""))
        assertIs<BackupError.Corrupted>(decodeError("""{"format":"quran-hifz-backup","formatVersion":"one"}"""))
        assertIs<BackupError.Corrupted>(decodeError("""{"format":"quran-hifz-backup","formatVersion":0}"""))
        assertIs<BackupError.Corrupted>(decodeError(text.replace("\"exportedAt\":1773312000000", "\"exportedAt\":\"yesterday\"")))
        assertIs<BackupError.Corrupted>(decodeError(text.replace("\"hifz\":", "\"hifz2\":")))
        assertIs<BackupError.Corrupted>(decodeError(text.replace("\"startAyah\":1,", "")))
        assertIs<BackupError.Corrupted>(decodeError(text.replace("\"passages\":[", "\"passages\":{\"x\":[")))
    }

    @Test
    fun fieldsThatTheAppDoesNotKnowAreIgnored() {
        val text = BackupCodec.encode(file)
            .replace("\"appVersion\"", "\"futureRoot\":{\"a\":[1,2]},\"appVersion\"")
            .replace("\"surahOrder\"", "\"futurePlan\":true,\"surahOrder\"")
            .replace("\"lapses\":0}", "\"lapses\":0,\"futurePassage\":\"x\"}")
            .replace("\"language\"", "\"futureSetting\":1.5,\"language\"")
        val parse = BackupCodec.decode(text)
        assertIs<BackupParse.Ok>(parse)
        assertEquals(file, parse.backup.file)
    }

    // ---- Ranges ----

    @Test
    fun surahsAndAyahsMustExist() {
        assertEquals(BackupError.OutOfRange("passage.surah"), errorOf(withPassages { it + BackupPassage(9, 0, 1, 1, 1, "OLD", null, null, 0) }))
        assertEquals(BackupError.OutOfRange("passage.surah"), errorOf(withPassages { it + BackupPassage(9, 115, 1, 1, 1, "OLD", null, null, 0) }))
        // Al-Fatiha has 7 ayahs, An-Nas 6.
        assertEquals(BackupError.OutOfRange("passage.ayah"), errorOf(withPassages { it + BackupPassage(9, 1, 1, 8, 1, "OLD", null, null, 0) }))
        assertEquals(BackupError.OutOfRange("passage.ayah"), errorOf(withPassages { it + BackupPassage(9, 1, 0, 3, 1, "OLD", null, null, 0) }))
        assertEquals(BackupError.OutOfRange("passage.ayah"), errorOf(withPassages { it + BackupPassage(9, 1, 5, 3, 1, "OLD", null, null, 0) }))
        assertEquals(BackupError.OutOfRange("passage.ayah"), errorOf(withPassages { it.dropLast(1) + BackupPassage(3, 114, 1, 7, 1, "OLD", null, null, 0) }))
        // The last ayah of the longest surah is fine.
        verified(withPassages { it + BackupPassage(9, 2, 286, 286, 1, "OLD", null, null, 0) })
        assertEquals(BackupError.OutOfRange("passage.ayah"), errorOf(withPassages { it + BackupPassage(9, 2, 286, 287, 1, "OLD", null, null, 0) }))
    }

    @Test
    fun stagesAndKindsMustBeOnTheList() {
        assertEquals(BackupError.OutOfRange("passage.stage"), errorOf(withPassages { it + BackupPassage(9, 112, 1, 4, 1, "R2", 5, null, 0) }))
        assertEquals(BackupError.OutOfRange("passage.stage"), errorOf(withPassages { it + BackupPassage(9, 112, 1, 4, 1, "r1", 5, null, 0) }))
        val stageText = BackupCodec.encode(file).replace("\"stage\":\"R3\"", "\"stage\":\"R60\"")
        assertEquals(BackupError.OutOfRange("passage.stage"), decodeError(stageText))
        PassageStage.entries.forEach { stage ->
            val due = if (stage == PassageStage.OLD) null else 20600
            verified(withPassages { it + BackupPassage(9, 112, 1, 4, 1, stage.name, due, null, 0) })
        }
        assertEquals(BackupError.OutOfRange("reviewLog.kind"), errorOf(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, kind = "SKIP")) }))
        assertEquals(BackupError.OutOfRange("reviewLog.grade"), errorOf(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, grade = "OK")) }))
        ReviewKind.entries.forEach { kind -> verified(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, kind = kind.name)) }) }
        ReviewGrade.entries.forEach { grade -> verified(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, grade = grade.name)) }) }
    }

    @Test
    fun aLadderPassageNeedsADueDay() {
        assertEquals(BackupError.OutOfRange("passage.nextDueEpochDay"), errorOf(withPassages { it + BackupPassage(9, 112, 1, 4, 1, "R7", null, null, 0) }))
    }

    @Test
    fun daysAndCountersMustBeSane() {
        assertEquals(BackupError.OutOfRange("passage.learnedEpochDay"), errorOf(withPassages { it + BackupPassage(9, 112, 1, 4, -1, "OLD", null, null, 0) }))
        assertEquals(BackupError.OutOfRange("passage.learnedEpochDay"), errorOf(withPassages { it + BackupPassage(9, 112, 1, 4, 5_000_000, "OLD", null, null, 0) }))
        assertEquals(BackupError.OutOfRange("passage.lastReviewedEpochDay"), errorOf(withPassages { it + BackupPassage(9, 112, 1, 4, 1, "OLD", null, -3, 0) }))
        assertEquals(BackupError.OutOfRange("passage.lapses"), errorOf(withPassages { it + BackupPassage(9, 112, 1, 4, 1, "OLD", null, null, -1) }))
        assertEquals(BackupError.OutOfRange("passage.id"), errorOf(withPassages { it + BackupPassage(0, 112, 1, 4, 1, "OLD", null, null, 0) }))
        assertEquals(BackupError.OutOfRange("reviewLog.epochDay"), errorOf(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, epochDay = -1)) }))
        assertEquals(BackupError.OutOfRange("reviewLog.timestamp"), errorOf(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, timestamp = -1)) }))
        assertEquals(BackupError.OutOfRange("reviewLog.surah"), errorOf(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, surah = 200)) }))
        assertEquals(BackupError.OutOfRange("reviewLog.ayah"), errorOf(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0].copy(id = 9, surah = 114, startAyah = 3, endAyah = 9)) }))
        assertEquals(BackupError.OutOfRange("exportedAt"), errorOf(file.copy(exportedAt = -5)))
    }

    @Test
    fun thePlanMustBeInsideItsRanges() {
        fun plan(change: (BackupPlan) -> BackupPlan) = withHifz { it.copy(plan = change(it.plan!!)) }
        assertEquals(BackupError.OutOfRange("plan.scope"), errorOf(plan { it.copy(scope = "JUZ_31") }))
        assertEquals(BackupError.OutOfRange("plan.surahOrder"), errorOf(plan { it.copy(surahOrder = emptyList()) }))
        assertEquals(BackupError.OutOfRange("plan.surahOrder"), errorOf(plan { it.copy(surahOrder = listOf(114, 115)) }))
        assertEquals(BackupError.OutOfRange("plan.surahOrder"), errorOf(plan { it.copy(surahOrder = listOf(0)) }))
        assertIs<BackupError.Corrupted>(errorOf(plan { it.copy(surahOrder = listOf(114, 113, 114)) }))
        assertEquals(BackupError.OutOfRange("plan.dailyNewAyahs"), errorOf(plan { it.copy(dailyNewAyahs = 0) }))
        assertEquals(BackupError.OutOfRange("plan.dailyNewAyahs"), errorOf(plan { it.copy(dailyNewAyahs = 21) }))
        assertEquals(BackupError.OutOfRange("plan.manzilDays"), errorOf(plan { it.copy(manzilDays = 2) }))
        assertEquals(BackupError.OutOfRange("plan.manzilDays"), errorOf(plan { it.copy(manzilDays = 31) }))
        assertEquals(BackupError.OutOfRange("plan.startedEpochDay"), errorOf(plan { it.copy(startedEpochDay = -1) }))
        assertEquals(BackupError.OutOfRange("plan.targetEpochDay"), errorOf(plan { it.copy(targetEpochDay = 9_999_999) }))
        verified(plan { it.copy(dailyNewAyahs = 20, manzilDays = 30, targetEpochDay = 21_000) })
        verified(plan { it.copy(dailyNewAyahs = 1, manzilDays = 3) })
        HifzScope.entries.forEach { scope -> verified(plan { it.copy(scope = scope.name) }) }
    }

    @Test
    fun difficultAyahsAndNotesMustBeInsideTheirRanges() {
        assertEquals(BackupError.OutOfRange("hard.ayah"), errorOf(withHifz { it.copy(hard = it.hard + BackupHard(1, 7, 2)) }))
        assertEquals(BackupError.OutOfRange("hard.clearsAfter"), errorOf(withHifz { it.copy(hard = it.hard + BackupHard(1, 5, 0)) }))
        assertEquals(BackupError.OutOfRange("hard.clearsAfter"), errorOf(withHifz { it.copy(hard = it.hard + BackupHard(1, 5, 3)) }))
        assertEquals(BackupError.OutOfRange("note.surah"), errorOf(file.copy(notes = file.notes + BackupNote(115, 1, "x", 1))))
        assertEquals(BackupError.OutOfRange("note.ayah"), errorOf(file.copy(notes = file.notes + BackupNote(1, 8, "x", 1))))
        assertEquals(BackupError.OutOfRange("note.ayah"), errorOf(file.copy(notes = file.notes + BackupNote(1, 0, "x", 1))))
        assertEquals(BackupError.OutOfRange("meta.key"), errorOf(withHifz { it.copy(meta = it.meta + (" " to "x")) }))
    }

    // ---- Structure that does not add up ----

    @Test
    fun brokenReferencesAndRepeatsAreCorrupted() {
        assertIs<BackupError.Corrupted>(errorOf(withPassages { it + it[0] }))
        assertIs<BackupError.Corrupted>(errorOf(withHifz { it.copy(hard = it.hard + BackupHard(77, 1, 2)) }))
        assertIs<BackupError.Corrupted>(errorOf(withHifz { it.copy(hard = it.hard + it.hard[0]) }))
        assertIs<BackupError.Corrupted>(errorOf(withHifz { it.copy(reviewLog = it.reviewLog + it.reviewLog[0]) }))
        assertIs<BackupError.Corrupted>(errorOf(file.copy(notes = file.notes + file.notes[0])))
    }

    @Test
    fun passagesOfOneSurahMayNotOverlap() {
        // 114:1-6 exists; 114:6 would be covered twice, 114:7 does not exist.
        assertIs<BackupError.Corrupted>(errorOf(withPassages { it + BackupPassage(9, 114, 6, 6, 1, "OLD", null, null, 0) }))
        assertIs<BackupError.Corrupted>(errorOf(withPassages { it + BackupPassage(9, 114, 1, 1, 1, "OLD", null, null, 0) }))
        // Neighbours are fine, and so is the same range of another surah.
        verified(withPassages { it.dropLast(1) + BackupPassage(9, 2, 1, 254, 1, "OLD", null, null, 0) + BackupPassage(10, 2, 256, 286, 1, "OLD", null, null, 0) + it.last() })
        verified(withPassages { it + BackupPassage(9, 113, 1, 5, 1, "OLD", null, null, 0).copy(surah = 112, endAyah = 4) })
    }

    // ---- Settings never fail an import ----

    @Test
    fun invalidSettingsAreDroppedAndTheImportGoesOn() {
        val bad = file.copy(
            settings = settings.copy(
                language = "xx", themeMode = "SEPIA", palette = "NEON", readingFont = "comic", readingFontSize = 99, dailyGoal = 0,
                playbackSpeed = 3f, repeatCount = 4, readerLayout = "GRID", reciter = "nobody", lessonAyahRepeats = 0,
                lessonPassageRepeats = 500, hifzHint = "ALL", reminderMinuteOfDay = 1440,
            ),
        )
        val applied = verified(bad).settings
        assertNull(applied.reminderMinuteOfDay)
        assertEquals(true, applied.reminderEnabled)
        assertNull(applied.language)
        assertNull(applied.themeMode)
        assertNull(applied.palette)
        assertNull(applied.readingFont)
        assertNull(applied.readingFontSize)
        assertNull(applied.dailyGoal)
        assertNull(applied.playbackSpeed)
        assertNull(applied.repeatCount)
        assertNull(applied.readerLayout)
        assertNull(applied.reciter)
        assertNull(applied.lessonAyahRepeats)
        assertNull(applied.lessonPassageRepeats)
        assertNull(applied.hifzHint)
        // The valid ones next to them stay.
        assertEquals(true, applied.showTranslation)
        assertEquals(false, applied.hapticsEnabled)
        assertEquals(true, applied.reduceMotion)
    }

    @Test
    fun everyHintLevelTravelsByNameAndAnUnknownOneIsDropped() {
        // F2a: the five levels of the "Recall" hint are kept by name; a name this app does not know is dropped, the rest of the file stays.
        listOf("NONE", "FIRST_WORD", "FIRST_LETTERS", "EVERY_OTHER", "PROGRESSIVE").forEach { name ->
            assertEquals(name, verified(file.copy(settings = BackupSettings(hifzHint = name))).settings.hifzHint, name)
        }
        listOf("SECOND_HALF", "first_word", "", "EVERY_OTHER ").forEach { name ->
            val applied = verified(file.copy(settings = BackupSettings(hifzHint = name, hapticsEnabled = false))).settings
            assertNull(applied.hifzHint, "'$name' is dropped")
            assertEquals(false, applied.hapticsEnabled)
        }
    }

    @Test
    fun theReminderTimeMustBeAMinuteOfTheDay() {
        listOf(-1, 1440, 100_000).forEach { minute ->
            assertNull(verified(file.copy(settings = BackupSettings(reminderMinuteOfDay = minute))).settings.reminderMinuteOfDay, "$minute")
        }
        listOf(0, 1, 1200, 1439).forEach { minute ->
            assertEquals(minute, verified(file.copy(settings = BackupSettings(reminderMinuteOfDay = minute))).settings.reminderMinuteOfDay)
        }
    }

    @Test
    fun aFileWrittenBeforeTheReminderExistedStillReads() {
        // The reminder fields are optional: a copy without them leaves the reminder of the phone as it is.
        val text = BackupCodec.encode(verified(file.copy(settings = BackupSettings(language = "ru"))))
        val parsed = (BackupCodec.decode(text) as BackupParse.Ok).backup.settings
        assertEquals("ru", parsed.language)
        assertNull(parsed.reminderEnabled)
        assertNull(parsed.reminderMinuteOfDay)
    }

    @Test
    fun theWordMeaningsSwitchAndTheTafsirChoiceAreOptionalAndAnUnknownSourceIsDropped() {
        // F4b: a copy written before them leaves the phone as it is; a new one carries both; a source this app does not know is dropped.
        val old = BackupCodec.encode(verified(file.copy(settings = BackupSettings(language = "ru"))))
        val oldSettings = (BackupCodec.decode(old) as BackupParse.Ok).backup.settings
        assertNull(oldSettings.wordMeanings)
        assertNull(oldSettings.tafsir)
        assertTrue(old.contains("wordMeanings").not() && old.contains("tafsir").not())

        val full = BackupCodec.encode(verified(file.copy(settings = BackupSettings(wordMeanings = true, tafsir = "ibn_kathir_en"))))
        val settings = (BackupCodec.decode(full) as BackupParse.Ok).backup.settings
        assertEquals(true, settings.wordMeanings)
        assertEquals("ibn_kathir_en", settings.tafsir)
        assertEquals("ibn_kathir_en", settings.sanitized().tafsir)
        assertNull(BackupSettings(tafsir = "someone_else").sanitized().tafsir)
        assertEquals(false, BackupSettings(wordMeanings = false).sanitized().wordMeanings)
    }

    @Test
    fun theTajweedSwitchIsOptionalAndTravelsAsABoolean() {
        // F3b: a copy written before the tajweed colours existed leaves the switch of the phone as it is; a new one carries it.
        val old = BackupCodec.encode(verified(file.copy(settings = BackupSettings(language = "ru"))))
        assertNull((BackupCodec.decode(old) as BackupParse.Ok).backup.settings.tajweed)
        assertTrue(old.contains("tajweed").not())

        val on = BackupCodec.encode(verified(file.copy(settings = BackupSettings(tajweed = true))))
        assertEquals(true, (BackupCodec.decode(on) as BackupParse.Ok).backup.settings.tajweed)
        val off = BackupCodec.encode(verified(file.copy(settings = BackupSettings(tajweed = false))))
        assertEquals(false, (BackupCodec.decode(off) as BackupParse.Ok).backup.settings.tajweed)
    }

    @Test
    fun theChosenEditionOfTheTranslationIsOptionalAndTravelsPerLanguage() {
        // F4a: a copy written before the choice existed leaves the editions of the phone as they are; a new one carries them.
        val old = BackupCodec.encode(verified(file.copy(settings = BackupSettings(language = "ru"))))
        assertNull((BackupCodec.decode(old) as BackupParse.Ok).backup.settings.translationChoice)
        assertTrue(old.contains("translationChoice").not())

        val choice = mapOf("ru" to "ru.abuadel", "tr" to "qc:210")
        val text = BackupCodec.encode(verified(file.copy(settings = BackupSettings(translationChoice = choice))))
        assertEquals(choice, (BackupCodec.decode(text) as BackupParse.Ok).backup.settings.translationChoice)
    }

    @Test
    fun anEditionThisAppDoesNotKnowIsDroppedAndTheRestStays() {
        val applied = verified(
            file.copy(
                settings = BackupSettings(
                    translationChoice = mapOf("ru" to "ru.abuadel", "xx" to "ru.kuliev", "tr" to "tr.nobody", "en" to "qc:101"),
                    hapticsEnabled = false,
                ),
            ),
        ).settings
        // quran.com 101 (Cyrillic Uzbek) is not offered; "xx" is not a language of the app.
        assertEquals(mapOf("ru" to "ru.abuadel"), applied.translationChoice)
        assertEquals(false, applied.hapticsEnabled)
        assertNull(verified(file.copy(settings = BackupSettings(translationChoice = mapOf("ru" to "nobody")))).settings.translationChoice)
    }

    @Test
    fun validSettingsPassUntouched() {
        assertEquals(settings, verified(file).settings)
        assertEquals(BackupSettings(), verified(file.copy(settings = BackupSettings())).settings)
    }

    @Test
    fun theFileHasNoSwitchForAnalytics() {
        // Analytics consent stays on the phone: there is no such field to transfer.
        assertFalse(BackupSettings.serializer().descriptor.elementNames.any { it.contains("analytic", ignoreCase = true) })
    }

    // ---- Preview ----

    @Test
    fun previewCountsWhatTheFileHolds() {
        val preview = verified(file).preview()
        assertEquals(1_773_312_000_000, preview.exportedAt)
        assertEquals(HifzScope.JUZ_30, preview.planScope)
        assertEquals(3, preview.planSurahs)
        assertEquals(3, preview.passages)
        assertEquals(2, preview.hardAyahs)
        assertEquals(2, preview.notes)
        assertEquals(2, preview.reviews)
        assertFalse(preview.isEmpty)
    }

    @Test
    fun previewOfAFileWithoutPlanOrData() {
        val noPlan = verified(withHifz { it.copy(plan = null) }).preview()
        assertNull(noPlan.planScope)
        assertEquals(0, noPlan.planSurahs)
        assertEquals(3, noPlan.passages)
    }

    // ---- Rows <-> file ----

    private val entityPassages = listOf(
        PassageWithHard(
            HifzPassageEntity(2, 113, 1, 5, 20400, PassageStage.OLD, null, null, 2),
            emptyList(),
        ),
        PassageWithHard(
            HifzPassageEntity(1, 114, 1, 6, 20500, PassageStage.R3, 20503, 20500, 0),
            listOf(PassageHardEntity(1, 5, 1), PassageHardEntity(1, 4, 2)),
        ),
    )

    @Test
    fun rowsOfTheDatabaseSurviveTheTripThroughTheFile() {
        val plan = HifzPlanEntity(1, HifzScope.JUZ_30, "114,113,112", 5, null, 20500, 7)
        val log = listOf(
            ReviewLogEntity(2, null, 2, 255, 255, ReviewKind.CHECK, ReviewGrade.HARD, 20501, 1_772_086_400_000),
            ReviewLogEntity(1, 1, 114, 1, 6, ReviewKind.NEW, ReviewGrade.GOOD, 20500, 1_772_000_000_000),
        )
        val meta = listOf(HifzMetaEntity("legacy_import", "done"), HifzMetaEntity("a_future_key", "1"))
        // Ayat al-Kursi is the 262nd ayah of the Quran (surah 2 starts at 8).
        val notes = listOf(AyahNoteEntity(262, 2, "Ayat al-Kursi", 9), AyahNoteEntity(6236, 114, "last", 10))

        val data = BackupMapping.toBackupData(plan, entityPassages, log, meta, notes)
        // The file is sorted whatever the order of the rows was.
        assertEquals(listOf(1L, 2L), data.hifz.passages.map { it.id })
        assertEquals(listOf(4 to 2, 5 to 1), data.hifz.hard.map { it.ayah to it.clearsAfter })
        assertEquals(listOf(1L, 2L), data.hifz.reviewLog.map { it.id })
        assertEquals(listOf("a_future_key", "legacy_import"), data.hifz.meta.keys.toList())
        assertEquals(listOf(2 to 255, 114 to 6), data.notes.map { it.surah to it.ayah })

        val backup = verified(file.copy(hifz = data.hifz, notes = data.notes))
        val parsed = BackupCodec.decode(BackupCodec.encode(backup))
        assertIs<BackupParse.Ok>(parsed)
        val rows = BackupMapping.toRows(parsed.backup.file)

        assertEquals(plan, rows.plan)
        assertEquals(entityPassages.map { it.passage }.sortedBy { it.id }, rows.passages)
        assertEquals(entityPassages.flatMap { it.hard }.sortedBy { it.ayahInSurah }, rows.hard)
        assertEquals(log.sortedBy { it.id }, rows.log)
        assertEquals(meta.sortedBy { it.key }.toSet(), rows.meta.toSet())
        assertEquals(notes.sortedBy { it.ayahNumber }, rows.notes.sortedBy { it.ayahNumber })
    }

    @Test
    fun noteRowsThatAreNotAyahsOfTheirSurahAreNotExported() {
        val data = BackupMapping.toBackupData(
            plan = null,
            passages = emptyList(),
            log = emptyList(),
            meta = emptyList(),
            notes = listOf(AyahNoteEntity(10, 1, "global 10 is not in Al-Fatiha", 1), AyahNoteEntity(1, 1, "ok", 2)),
        )
        assertEquals(listOf("ok"), data.notes.map { it.text })
    }

    @Test
    fun importAlwaysMarksTheLegacyImportDone() {
        val without = file.copy(hifz = file.hifz.copy(meta = emptyMap()))
        val rows = BackupMapping.toRows(verified(without).file)
        assertEquals(mapOf("legacy_import" to "done"), rows.meta.associate { it.key to it.value })
        // Even if the file says otherwise.
        val saysNotDone = file.copy(hifz = file.hifz.copy(meta = mapOf("legacy_import" to "pending", "x" to "y")))
        val rows2 = BackupMapping.toRows(verified(saysNotDone).file)
        assertEquals(mapOf("legacy_import" to "done", "x" to "y"), rows2.meta.associate { it.key to it.value })
    }

    @Test
    fun aLogRecordOfAPassageThatIsGoneLosesTheDanglingPointer() {
        val withDangling = withHifz {
            it.copy(reviewLog = it.reviewLog + BackupLogEntry(7, 999, 112, 1, 4, "RECENT", "GOOD", 20502, 1))
        }
        val rows = BackupMapping.toRows(verified(withDangling).file)
        assertEquals(1L, rows.log.first { it.id == 1L }.passageId)
        assertNull(rows.log.first { it.id == 2L }.passageId)
        assertNull(rows.log.first { it.id == 7L }.passageId)
    }

    @Test
    fun anOldPassageNeverGetsADueDayEvenIfTheFileHasOne() {
        val odd = withPassages { it.map { p -> if (p.id == 2L) p.copy(nextDueEpochDay = 5) else p } }
        val rows = BackupMapping.toRows(verified(odd).file)
        assertNull(rows.passages.first { it.id == 2L }.nextDueEpochDay)
    }
}
