package tj.app.quran_todo.hifz

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.data.bundled.BundledQuranSource
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoStatus
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus
import tj.app.quran_todo.data.database.getRoomDatabase
import tj.app.quran_todo.domain.repository.QuranRemoteRepository
import tj.app.quran_todo.hifz.backup.BackupCodec
import tj.app.quran_todo.hifz.backup.BackupData
import tj.app.quran_todo.hifz.backup.BackupFile
import tj.app.quran_todo.hifz.backup.BackupFormat
import tj.app.quran_todo.hifz.backup.BackupHifz
import tj.app.quran_todo.hifz.backup.BackupParse
import tj.app.quran_todo.hifz.backup.VerifiedBackup
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.NewPassageResult
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.PlanPauses
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The repository on a real Room database (file in the app's databases directory, the same bundled SQLite driver as in the
 * app, the same builder). The Quran text is synthetic: 100 characters an ayah and 10 ayahs a page of a surah, with the real
 * ayah counts.
 *
 * Run: `./gradlew :composeApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=tj.app.quran_todo.hifz.HifzRepositoryTest`
 */
@RunWith(AndroidJUnit4::class)
class HifzRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = context.getDatabasePath("hifz-repository-test.db")
    private val day = 20_000
    private val dayMillis = 86_400_000L

    private lateinit var db: QuranTodoDatabase
    private lateinit var settings: FakeSettings
    private lateinit var repository: HifzRepositoryImpl

    private class FakeSettings(var weak: Set<String> = emptySet(), var goal: Int? = null, var failReading: Boolean = false) : HifzLegacySettings {
        override fun weakAyahKeys(): Set<String> {
            check(!failReading) { "preferences are not readable" }
            return weak
        }

        override fun dailyGoal(): Int? = goal

        override fun clearWeakAyahKeys() {
            weak = emptySet()
        }
    }

    /** Writes a synthetic Quran into the table `ayahs`, the way the bundled text is written in the app. */
    private class SyntheticQuran(private val db: QuranTodoDatabase) : QuranRemoteRepository {
        var installs = 0

        override fun getCompleteQuran(withLocalAction: Boolean): Flow<Resource<List<SurahWithAyahs>>> = emptyFlow()

        override suspend fun ensureQuranInstalled(force: Boolean) {
            val dao = db.getQuranDao()
            if (!force && dao.countAyahs() == QuranFacts.AYAH_COUNT) return
            installs++
            val edition = EditionEntity(BundledQuranSource.EDITION_ID, "Uthmani", "text", "ar", "name", "quran")
            val surahs = (1..QuranFacts.SURAH_COUNT).map { SurahEntity(it, "s$it", "S$it", "S$it", "Meccan", edition.identifier) }
            val ayahs = (1..QuranFacts.SURAH_COUNT).flatMap { surah ->
                (1..QuranFacts.ayahCount(surah)).map { ayah ->
                    AyahEntity(
                        number = QuranFacts.firstAyahNumber(surah) + ayah - 1,
                        numberInSurah = ayah,
                        juz = 1,
                        hizbQuarter = 1,
                        manzil = 1,
                        page = surah * 100 + (ayah - 1) / 10,
                        ruku = 1,
                        sajda = null,
                        text = "ا".repeat(100),
                        surahNumber = surah,
                    )
                }
            }
            dao.replaceQuran(edition, surahs, ayahs)
        }
    }

    @Before
    fun setUp() {
        deleteFiles()
        db = getRoomDatabase(Room.databaseBuilder<QuranTodoDatabase>(context = context, name = dbFile.absolutePath))
        settings = FakeSettings()
        repository = HifzRepositoryImpl(
            db = db,
            quran = SyntheticQuran(db),
            legacySettings = settings,
            clock = { day * dayMillis + 12 * 3_600_000L },
            zone = { TimeZone.UTC },
        )
    }

    @After
    fun tearDown() {
        db.close()
        deleteFiles()
    }

    private fun deleteFiles() {
        listOf("", "-wal", "-shm", "-journal").forEach { java.io.File(dbFile.path + it).delete() }
    }

    private fun global(surah: Int, ayah: Int) = QuranFacts.firstAyahNumber(surah) + ayah - 1

    private suspend fun ayahRows(surah: Int) = db.getAyahTodoDao().getBySurahNumberOnce(surah).sortedBy { it.ayahNumber }

    private suspend fun surahRows() = db.getSurahTodoDao().getAllSurahTodo().first().associate { it.surahNumber to it.status }

    // ---- the legacy import ----

    private suspend fun seedLegacy() {
        db.getSurahTodoDao().upsertAll(
            listOf(SurahTodoEntity(112, SurahTodoStatus.LEARNED), SurahTodoEntity(114, SurahTodoStatus.LEARNING)),
        )
        db.getAyahTodoDao().upsertAll(
            listOf(
                AyahTodoEntity(global(113, 1), 113, AyahTodoStatus.LEARNED, 5),
                AyahTodoEntity(global(113, 2), 113, AyahTodoStatus.LEARNED, 5),
                AyahTodoEntity(global(114, 1), 114, AyahTodoStatus.LEARNING, 5),
            ),
        )
        db.getAyahReviewDao().upsert(AyahReviewEntity(global(114, 1), 114, (day - 1) * dayMillis + 3_600_000L, 1, (day - 4) * dayMillis))
        settings.weak = setOf("113:${global(113, 2)}", "112:2", "114:77")
        settings.goal = 7
    }

    @Test
    fun theLegacyImportTurnsTheOldRowsIntoPassagesAPlanAndAMirror() = runBlocking {
        seedLegacy()
        repository.importLegacyIfNeeded()

        val s112 = repository.surahCoverage(112).first()
        assertEquals(SurahHifzStatus.LEARNED, s112.status)
        assertEquals(1, s112.passages.size)
        assertEquals(PassageStage.OLD, s112.passages.single().stage)
        assertNull(s112.passages.single().lastReviewedEpochDay)
        assertEquals(setOf(2), s112.hardAyahs)

        val s113 = repository.surahCoverage(113).first()
        assertEquals(setOf(1, 2), s113.coveredAyahs)
        assertEquals(setOf(2), s113.hardAyahs)
        assertEquals(SurahHifzStatus.LEARNING, s113.status)

        val s114 = repository.surahCoverage(114).first()
        val ladder = s114.passages.single()
        assertEquals(PassageStage.R3, ladder.stage)
        assertEquals(day - 1, ladder.nextDueEpochDay, "overdue by a day")

        val plan = assertNotNull(repository.plan().first())
        assertEquals(HifzScope.CUSTOM, plan.scope)
        assertEquals(listOf(114, 113), plan.surahOrder)
        assertEquals(7, plan.dailyNewAyahs)
        assertEquals(day, plan.startedEpochDay)

        assertEquals("done", db.getHifzDao().getMeta("legacy_import"))
        assertTrue(settings.weak.isEmpty(), "the weak ayahs of the preferences are cleared after the import")

        // The mirror: the old screens read these rows.
        assertEquals(4, ayahRows(112).size)
        assertEquals(listOf(global(113, 1), global(113, 2)), ayahRows(113).map { it.ayahNumber })
        assertEquals(AyahTodoStatus.LEARNED, ayahRows(114).single().status)
        assertEquals(
            mapOf(112 to SurahTodoStatus.LEARNED, 113 to SurahTodoStatus.LEARNING, 114 to SurahTodoStatus.LEARNING),
            surahRows(),
        )
        assertEquals(1, db.getAyahReviewDao().getAll().size, "ayah_review is not deleted")

        // The lesson of today has the overdue repetition, and the new passage is the first of the plan.
        val lesson = repository.lesson(day).first()
        assertEquals(listOf(ladder.id), lesson.recent.map { it.passageId })
        assertEquals(1, lesson.recent.single().overdueDays)
        assertEquals(LessonNewState.PENDING, lesson.newBlock?.state)
        assertEquals(114, lesson.newBlock?.surah)
        assertEquals(2, lesson.newBlock?.startAyah, "114:1 is covered already")
    }

    @Test
    fun aSecondImportDoesNothing() = runBlocking {
        seedLegacy()
        repository.importLegacyIfNeeded()
        val before = db.getHifzDao().getPassages().map { it.passage }
        val logBefore = db.getHifzDao().getLog(0, Int.MAX_VALUE)

        // The old tables change later and the preferences get new weak ayahs: the mark says the import has run.
        db.getSurahTodoDao().upsert(SurahTodoEntity(111, SurahTodoStatus.LEARNED))
        settings.weak = setOf("111:1")
        repository.importLegacyIfNeeded()

        assertEquals(before, db.getHifzDao().getPassages().map { it.passage })
        assertEquals(logBefore, db.getHifzDao().getLog(0, Int.MAX_VALUE))
        assertEquals(setOf("111:1"), settings.weak, "nothing is touched")
        assertNull(db.getAyahTodoDao().getBySurahNumberOnce(111).firstOrNull())
    }

    @Test
    fun aFailedImportLeavesNoMarkAndNoPassagesAndIsRepeated() = runBlocking {
        seedLegacy()
        settings.failReading = true
        assertFailsWith<IllegalStateException> { repository.importLegacyIfNeeded() }

        assertNull(db.getHifzDao().getMeta("legacy_import"))
        assertTrue(db.getHifzDao().getPassages().isEmpty())
        assertNull(db.getHifzDao().getPlan())
        assertEquals(mapOf(112 to SurahTodoStatus.LEARNED, 114 to SurahTodoStatus.LEARNING), surahRows())
        assertEquals(AyahTodoStatus.LEARNING, ayahRows(114).single().status, "the mirror did not run either")

        settings.failReading = false
        repository.importLegacyIfNeeded()
        assertEquals("done", db.getHifzDao().getMeta("legacy_import"))
        assertEquals(3, db.getHifzDao().getPassages().size)
    }

    @Test
    fun importingAnEmptyDatabaseOnlySetsTheMark() = runBlocking {
        repository.importLegacyIfNeeded()
        assertEquals("done", db.getHifzDao().getMeta("legacy_import"))
        assertTrue(db.getHifzDao().getPassages().isEmpty())
        assertNull(repository.plan().first())
        assertTrue(surahRows().isEmpty())
    }

    // ---- learning ----

    @Test
    fun completeNewAndReviewWriteThePassagesTheLogAndTheMirror() = runBlocking {
        repository.learnNext(114, day)
        assertEquals(mapOf(114 to SurahTodoStatus.LEARNING), surahRows(), "the current surah of the plan is being learned")
        assertTrue(ayahRows(114).isEmpty())

        val lessonBefore = repository.lesson(day).first()
        assertEquals(LessonNewState.PENDING, lessonBefore.newBlock?.state)
        assertEquals(1 to 6, lessonBefore.newBlock?.let { it.startAyah to it.endAyah })

        val created = assertIs<NewPassageResult.Created>(repository.completeNew(114, 1, 6, ReviewGrade.GOOD, setOf(3), day))
        val coverage = repository.surahCoverage(114).first()
        val passage = coverage.passages.single()
        assertEquals(created.passageId, passage.id)
        assertEquals(PassageStage.R1, passage.stage)
        assertEquals(day + 1, passage.nextDueEpochDay)
        assertEquals(mapOf(3 to 2), passage.hard)
        assertEquals(SurahHifzStatus.LEARNED, coverage.status)

        val log = repository.reviewLog(day, day).first()
        assertEquals(listOf(ReviewKind.NEW), log.map { it.kind })
        assertEquals(created.passageId, log.single().passageId)
        assertEquals(ReviewGrade.GOOD, log.single().grade)

        assertEquals(6, ayahRows(114).size)
        assertEquals(mapOf(114 to SurahTodoStatus.LEARNED), surahRows())
        assertEquals(mapOf(114 to SurahHifzStatus.LEARNED), repository.surahStatuses().first())

        val lessonAfter = repository.lesson(day).first()
        assertEquals(LessonNewState.DONE, lessonAfter.newBlock?.state)
        assertTrue(lessonAfter.planFinished)
        assertTrue(lessonAfter.isComplete)
        assertEquals(1, repository.streak(day).first())

        // The next day: the repetition is due; GOOD moves it to R3 and counts the difficult ayah down.
        assertEquals(listOf(created.passageId), repository.lesson(day + 1).first().recent.map { it.passageId })
        val result = assertNotNull(repository.review(created.passageId, ReviewGrade.GOOD, emptySet(), day + 1))
        assertEquals(ReviewKind.RECENT, result.kind)
        assertEquals(PassageStage.R3, result.stage)
        assertEquals(day + 4, result.nextDueEpochDay)
        val afterReview = repository.surahCoverage(114).first().passages.single()
        assertEquals(mapOf(3 to 1), afterReview.hard)
        assertEquals(listOf(ReviewKind.NEW, ReviewKind.RECENT), repository.reviewLog(day, day + 1).first().map { it.kind })
        assertEquals(2, repository.streak(day + 1).first())
        assertEquals(0, repository.streak(day + 5).first())
        assertNull(repository.review(9_999, ReviewGrade.GOOD, emptySet(), day + 1), "no such passage")
    }

    @Test
    fun aNewPassageThatOverlapsWhatIsCoveredIsRefusedAndWritesNothing() = runBlocking {
        repository.completeNew(78, 1, 5, ReviewGrade.GOOD, emptySet(), day)
        val refused = repository.completeNew(78, 4, 8, ReviewGrade.GOOD, emptySet(), day)
        assertEquals(NewPassageResult.Overlaps, refused)
        assertEquals(1, db.getHifzDao().getPassages().size)
        assertEquals(1, repository.reviewLog(day, day).first().size)
        assertEquals(5, ayahRows(78).size)
    }

    @Test
    fun invalidRangesAreRejected() = runBlocking {
        assertFailsWith<IllegalArgumentException> { repository.completeNew(114, 1, 7, ReviewGrade.GOOD, emptySet(), day) }
        assertFailsWith<IllegalArgumentException> { repository.completeNew(115, 1, 1, ReviewGrade.GOOD, emptySet(), day) }
        assertFailsWith<IllegalArgumentException> { repository.completeNew(114, 3, 2, ReviewGrade.GOOD, emptySet(), day) }
        assertTrue(db.getHifzDao().getPassages().isEmpty())
    }

    @Test
    fun aPassageBecomingOldIsMergedWithItsOldNeighbour() = runBlocking {
        repository.markKnown(2, 1, 10, day) // one OLD passage of a page (1000 characters)
        val created = assertIs<NewPassageResult.Created>(repository.completeNew(2, 11, 12, ReviewGrade.GOOD, emptySet(), day))
        // EASY: R1 -> R7 -> R30 -> OLD.
        assertEquals(PassageStage.R7, repository.review(created.passageId, ReviewGrade.EASY, emptySet(), day + 1)?.stage)
        assertEquals(PassageStage.R30, repository.review(created.passageId, ReviewGrade.EASY, emptySet(), day + 8)?.stage)
        val last = assertNotNull(repository.review(created.passageId, ReviewGrade.GOOD, emptySet(), day + 40))

        assertEquals(PassageStage.OLD, last.stage)
        assertNull(last.nextDueEpochDay)
        assertTrue(last.merged)
        val passage = repository.surahCoverage(2).first().passages.single()
        assertEquals(created.passageId, passage.id)
        assertEquals(1 to 12, passage.startAyah to passage.endAyah)
        assertNull(passage.lastReviewedEpochDay, "the never reviewed part makes it the oldest")
        assertEquals(12, ayahRows(2).size)
        assertEquals(4, repository.reviewLog(day, day + 40).first().size)
    }

    @Test
    fun logCheckWritesOnlyTheLogAndTheDifficultAyahs() = runBlocking {
        val created = assertIs<NewPassageResult.Created>(repository.completeNew(78, 1, 5, ReviewGrade.GOOD, emptySet(), day))
        repository.logCheck(78, 2, 4, ReviewGrade.HARD, setOf(3), day + 2)

        val passage = repository.surahCoverage(78).first().passages.single()
        assertEquals(PassageStage.R1, passage.stage)
        assertEquals(day + 1, passage.nextDueEpochDay)
        assertEquals(mapOf(3 to 2), passage.hard)
        val check = repository.reviewLog(day + 2, day + 2).first().single()
        assertEquals(ReviewKind.CHECK, check.kind)
        assertEquals(created.passageId, check.passageId)
        assertEquals(2 to 4, check.startAyah to check.endAyah)

        // A range that two passages cover has no single passage.
        repository.completeNew(78, 6, 8, ReviewGrade.GOOD, emptySet(), day + 2)
        repository.logCheck(78, 4, 7, ReviewGrade.GOOD, emptySet(), day + 3)
        assertNull(repository.reviewLog(day + 3, day + 3).first().single().passageId)
    }

    @Test
    fun drillHardChangesOnlyTheMarksAndWritesOneCheckRecordPerSurah() = runBlocking {
        val created = assertIs<NewPassageResult.Created>(repository.completeNew(78, 1, 8, ReviewGrade.GOOD, setOf(3, 6), day))
        val passageBefore = repository.surahCoverage(78).first().passages.single()
        assertEquals(mapOf(3 to 2, 6 to 2), passageBefore.hard)
        val lessonBefore = repository.lesson(day + 1).first()
        val mirrorBefore = ayahRows(78)

        // One ayah remembered (2 -> 1), one still hard (back to 2).
        repository.drillHard(78, remembered = setOf(3), stillHard = setOf(6), today = day + 1)
        val passage = repository.surahCoverage(78).first().passages.single()
        assertEquals(mapOf(3 to 1, 6 to 2), passage.hard)
        // Stage, due day, lastReviewed and lapses did not move.
        assertEquals(passageBefore.copy(hard = emptyMap()), passage.copy(hard = emptyMap()))
        val records = repository.reviewLog(day + 1, day + 1).first()
        assertEquals(1, records.size, "one record for the group")
        assertEquals(ReviewKind.CHECK, records.single().kind)
        // One of the two trained ayahs is still hard: half, so the record is FORGOT (the rule of the Recall step).
        assertEquals(ReviewGrade.FORGOT, records.single().grade)
        assertEquals(created.passageId, records.single().passageId)
        assertEquals(3 to 6, records.single().startAyah to records.single().endAyah)
        // The lesson is the same but for the marks of its card; the mirror did not change.
        val lessonAfter = repository.lesson(day + 1).first()
        assertEquals(
            lessonBefore.recent.map { it.copy(hardAyahs = emptySet()) },
            lessonAfter.recent.map { it.copy(hardAyahs = emptySet()) },
        )
        assertEquals(mirrorBefore, ayahRows(78))

        // The second time the mark of ayah 3 goes away, nothing is still hard: the record is a GOOD one.
        repository.drillHard(78, remembered = setOf(3), stillHard = emptySet(), today = day + 1)
        assertEquals(mapOf(6 to 2), repository.surahCoverage(78).first().passages.single().hard)
        val second = repository.reviewLog(day + 1, day + 1).first()
        assertEquals(2, second.size)
        assertEquals(ReviewGrade.GOOD, second.last().grade)
        assertEquals(3 to 3, second.last().startAyah to second.last().endAyah)

        // An ayah that is not marked any more, or one that is not covered, is skipped; with nothing left nothing is written.
        repository.drillHard(78, remembered = setOf(1, 3, 40), stillHard = setOf(2), today = day + 1)
        assertEquals(2, repository.reviewLog(day + 1, day + 1).first().size)
        // A surah without passages writes nothing either.
        repository.drillHard(112, remembered = setOf(1), stillHard = emptySet(), today = day + 1)
        assertEquals(2, repository.reviewLog(day + 1, day + 1).first().size)
        assertEquals(mapOf(78 to setOf(6)), repository.hardAyahs().first())
    }

    // ---- "I know it", "forget", plan ----

    @Test
    fun markKnownAndForgetKeepTheMirrorInStep() = runBlocking {
        repository.markKnown(2, 1, 25, day)
        val coverage = repository.surahCoverage(2).first()
        assertEquals(listOf(1 to 10, 11 to 20, 21 to 25), coverage.passages.map { it.startAyah to it.endAyah })
        assertTrue(coverage.passages.all { it.stage == PassageStage.OLD && it.lastReviewedEpochDay == null })
        assertEquals(25, ayahRows(2).size)
        assertEquals(mapOf(2 to SurahTodoStatus.LEARNING), surahRows())

        repository.forget(2, 11, 15)
        val after = repository.surahCoverage(2).first()
        assertEquals(listOf(1 to 10, 16 to 20, 21 to 25), after.passages.map { it.startAyah to it.endAyah })
        assertEquals(20, ayahRows(2).size)
        assertEquals((1..10) + (16..25), ayahRows(2).map { it.ayahNumber - global(2, 1) + 1 })

        repository.forget(2) // the whole surah
        assertTrue(repository.surahCoverage(2).first().passages.isEmpty())
        assertTrue(ayahRows(2).isEmpty())
        assertTrue(surahRows().isEmpty())
    }

    @Test
    fun markKnownOfAWholeSurahMakesItLearned() = runBlocking {
        repository.markKnown(112)
        assertEquals(mapOf(112 to SurahHifzStatus.LEARNED), repository.surahStatuses().first())
        assertEquals(mapOf(112 to SurahTodoStatus.LEARNED), surahRows())
        assertEquals(4, ayahRows(112).size)
        // A repeated "I know it" adds nothing.
        repository.markKnown(112)
        assertEquals(1, db.getHifzDao().getPassages().size)
    }

    @Test
    fun thePlanOperationsMoveTheCurrentSurahInTheMirror() = runBlocking {
        repository.setPlan(HifzScope.JUZ_30, HifzPresets.JUZ_30, dailyNewAyahs = 5, targetEpochDay = day + 100, manzilDays = 10, today = day)
        val plan = assertNotNull(repository.plan().first())
        assertEquals(HifzScope.JUZ_30, plan.scope)
        assertEquals(114, plan.surahOrder.first())
        assertEquals(78, plan.surahOrder.last())
        assertEquals(day + 100, plan.targetEpochDay)
        assertEquals(10, plan.manzilDays)
        assertEquals(mapOf(114 to SurahTodoStatus.LEARNING), surahRows())

        // "I am learning this": it goes to the front, the plan becomes CUSTOM, the old current surah loses its mark.
        repository.learnNext(80, day + 1)
        val moved = assertNotNull(repository.plan().first())
        assertEquals(HifzScope.CUSTOM, moved.scope)
        assertEquals(80, moved.surahOrder.first())
        assertEquals(37, moved.surahOrder.size)
        assertEquals(day, moved.startedEpochDay, "the plan is the same plan")
        assertEquals(mapOf(80 to SurahTodoStatus.LEARNING), surahRows())

        // Changing only the pace keeps the start day; changing the queue starts again.
        repository.setPlan(HifzScope.CUSTOM, moved.surahOrder, 8, null, 7, day + 2)
        assertEquals(day, assertNotNull(repository.plan().first()).startedEpochDay)
        assertEquals(8, assertNotNull(repository.plan().first()).dailyNewAyahs)
        repository.setPlan(HifzScope.CUSTOM, listOf(112, 111), 3, null, 7, day + 3)
        assertEquals(day + 3, assertNotNull(repository.plan().first()).startedEpochDay)
        assertEquals(mapOf(112 to SurahTodoStatus.LEARNING), surahRows())

        repository.clearPlan()
        assertNull(repository.plan().first())
        assertTrue(surahRows().isEmpty())
        assertNull(repository.forecast(day + 4).first())
    }

    @Test
    fun learnNextWithoutAPlanMakesACustomPlanOfFiveAyahsADay() = runBlocking {
        repository.learnNext(78, day)
        val plan = assertNotNull(repository.plan().first())
        assertEquals(HifzScope.CUSTOM, plan.scope)
        assertEquals(listOf(78), plan.surahOrder)
        assertEquals(5, plan.dailyNewAyahs)
        assertEquals(day, plan.startedEpochDay)
        val forecast = assertNotNull(repository.forecast(day).first())
        assertEquals(40, forecast.remainingAyahs)
        assertEquals(day + 8, forecast.finishEpochDay)
    }

    // ---- small reads for the plan screen ----

    @Test
    fun estimateMinutesUsesTheFormulasOfTheLesson() = runBlocking<Unit> {
        // 5 ayahs = 500 characters: a new passage 630 s -> 11 minutes, a repetition 191 s -> 4 minutes.
        assertEquals(11, repository.estimateMinutes(114, 1, 5, isNew = true))
        assertEquals(4, repository.estimateMinutes(114, 1, 5, isNew = false))
        // The lesson says the same about the same passage.
        repository.learnNext(114, day)
        val block = assertNotNull(repository.lesson(day).first().newBlock)
        assertEquals(
            repository.estimateMinutes(block.surah, block.startAyah, block.endAyah, isNew = true),
            (block.estimateSeconds + 59) / 60,
        )
        assertFailsWith<IllegalArgumentException> { repository.estimateMinutes(114, 1, 7, isNew = true) }
        assertFailsWith<IllegalArgumentException> { repository.estimateMinutes(200, 1, 1, isNew = false) }
    }

    @Test
    fun oldWeightIsTheTotalCharactersOfTheOldPassages() = runBlocking {
        assertEquals(0, repository.oldWeight().first())
        repository.markKnown(2, 1, 10, day) // one OLD passage, 1000 characters
        assertEquals(1_000, repository.oldWeight().first())
        repository.markKnown(112, today = day) // 4 ayahs
        assertEquals(1_400, repository.oldWeight().first())
        // A passage of the ladder is not OLD and does not count.
        repository.completeNew(114, 1, 6, ReviewGrade.GOOD, emptySet(), day)
        assertEquals(1_400, repository.oldWeight().first())
        repository.forget(2, 1, 5)
        assertEquals(900, repository.oldWeight().first())
    }

    @Test
    fun anOldPassageIsOfferedOnceInManzilDays() = runBlocking {
        repository.markKnown(112, today = day)
        val id = repository.surahCoverage(112).first().passages.single().id
        assertEquals(listOf(id), repository.lesson(day).first().old.map { it.passageId })
        repository.review(id, ReviewGrade.GOOD, emptySet(), day)
        assertTrue(repository.lesson(day + 1).first().old.isEmpty(), "the next day it is not offered again")
        assertTrue(repository.lesson(day + 6).first().old.isEmpty())
        assertEquals(listOf(id), repository.lesson(day + 7).first().old.map { it.passageId })
    }

    // ---- the Flows follow the tables ----

    @Test
    fun aLessonFlowEmitsAgainWhenAPassageIsWritten() = runBlocking {
        repository.learnNext(114, day)
        val done = async { withTimeout(10_000) { repository.lesson(day).first { it.newBlock?.state == LessonNewState.DONE } } }
        // Give the collector time to start, then write.
        delay(300)
        repository.completeNew(114, 1, 6, ReviewGrade.EASY, emptySet(), day)
        val lesson = done.await()
        assertEquals(ReviewGrade.EASY, lesson.newBlock?.grade)
    }

    @Test
    fun coveredCountsListsEverySurahWithACoveredAyahInOneRead() = runBlocking {
        assertTrue(repository.coveredCounts().first().isEmpty())
        repository.completeNew(114, 1, 4, ReviewGrade.GOOD, emptySet(), day)
        repository.markKnown(112, today = day)
        val counts = repository.coveredCounts().first()
        assertEquals(setOf(112, 114), counts.keys)
        assertEquals(4, counts[114])
        assertEquals(repository.surahCoverage(112).first().ayahCount, counts[112])
    }

    @Test
    fun coveredAyahsListsTheCoveredAyahNumbersOfEverySurahInOneRead() = runBlocking {
        assertTrue(repository.coveredAyahs().first().isEmpty())
        repository.completeNew(114, 1, 4, ReviewGrade.GOOD, emptySet(), day)
        repository.markKnown(112, today = day)
        val ayahs = repository.coveredAyahs().first()
        assertEquals(setOf(112, 114), ayahs.keys)
        assertEquals((1..4).toSet(), ayahs[114])
        assertEquals(repository.surahCoverage(112).first().coveredAyahs, ayahs[112])
    }

    @Test
    fun hardAyahsListsTheDifficultAyahsOfEverySurahInOneRead() = runBlocking {
        assertTrue(repository.hardAyahs().first().isEmpty())
        repository.completeNew(114, 1, 6, ReviewGrade.HARD, setOf(2, 5), day)
        repository.completeNew(112, 1, 4, ReviewGrade.GOOD, emptySet(), day)
        repository.logCheck(112, 1, 4, ReviewGrade.HARD, setOf(3), day + 1)
        val hard = repository.hardAyahs().first()
        assertEquals(setOf(112, 114), hard.keys)
        assertEquals(setOf(2, 5), hard[114])
        assertEquals(setOf(3), hard[112])
        assertEquals(repository.surahCoverage(114).first().hardAyahs, hard[114])
    }

    @Test
    fun aDraftPlanForecastUsesTheCoveredAyahsButStoresNothing() = runBlocking {
        repository.completeNew(114, 1, 3, ReviewGrade.GOOD, emptySet(), day)
        val draft = tj.app.quran_todo.hifz.model.HifzPlan(
            scope = HifzScope.CUSTOM,
            surahOrder = listOf(114),
            dailyNewAyahs = 3,
            targetEpochDay = null,
            startedEpochDay = day,
            manzilDays = 7,
        )
        val forecast = repository.forecast(draft, day).first()
        assertEquals(6, forecast.totalAyahs)
        assertEquals(3, forecast.remainingAyahs)
        assertEquals(day + 1 + 1, forecast.finishEpochDay, "today's new passage is learned: counting starts tomorrow")
        assertNull(repository.plan().first(), "the draft is not saved")
    }

    @Test
    fun aDayFlowMovesTheLessonToTheNextDay() = runBlocking {
        repository.completeNew(114, 1, 6, ReviewGrade.GOOD, emptySet(), day)
        val days = MutableStateFlow(day)
        val seen = mutableListOf<Int>()
        val collector = launch { repository.lesson(days).collect { seen += it.today } }
        delay(300)
        days.value = day + 1
        withTimeout(10_000) { while (day + 1 !in seen) delay(50) }
        collector.cancel()
        assertEquals(day, seen.first())
        // On the next day the repetition of the passage learned yesterday is due.
        assertEquals(1, repository.lesson(day + 1).first().recent.size)
    }

    // ---- the backup file (task B5) ----

    private fun fileOf(data: BackupData) = BackupFile(
        format = BackupFormat.NAME,
        formatVersion = BackupFormat.VERSION,
        appVersion = "test",
        exportedAt = day * dayMillis,
        dbVersion = BackupFormat.DB_VERSION,
        hifz = data.hifz,
        notes = data.notes,
    )

    private fun verifiedOf(data: BackupData): VerifiedBackup = assertIs<BackupParse.Ok>(BackupCodec.verify(fileOf(data))).backup

    /** A plan, a new passage with a difficult ayah that moved up the ladder, a known surah, a check, a note. */
    private suspend fun seedProgress() {
        repository.importLegacyIfNeeded()
        repository.setPlan(HifzScope.JUZ_30, emptyList(), 5, null, 7, day)
        repository.completeNew(114, 1, 4, ReviewGrade.GOOD, setOf(3), day)
        repository.markKnown(112, today = day)
        val id = repository.surahCoverage(114).first().passages.single().id
        repository.review(id, ReviewGrade.GOOD, emptySet(), day + 1)
        repository.logCheck(112, 1, 4, ReviewGrade.HARD, setOf(2), day + 1)
        db.getAyahNoteDao().upsert(AyahNoteEntity(global(2, 255), 2, "Ayat al-Kursi", 5))
    }

    /** The mirror as the old screens see it (without the clock-made `updatedAt`). */
    private suspend fun mirrorState() =
        db.getHifzDao().getAllAyahTodos().map { it.ayahNumber to it.status }.sortedBy { it.first } to surahRows()

    @Test
    fun aBackupRestoresTheLadderTheLogTheNotesAndTheMirror() = runBlocking {
        seedProgress()
        val exported = repository.exportBackupData()
        val text = BackupCodec.encode(verifiedOf(exported))
        val mirrorBefore = mirrorState()
        assertTrue(exported.hifz.passages.size >= 2 && exported.hifz.hard.isNotEmpty() && exported.hifz.reviewLog.size >= 3)

        // The phone changes afterwards: no plan, the new passage forgotten, another surah known, the note gone.
        repository.clearPlan()
        repository.forget(114)
        repository.markKnown(1, today = day + 2)
        db.getAyahNoteDao().clear()
        assertNotEquals(exported, repository.exportBackupData())

        repository.replaceWithBackup(assertIs<BackupParse.Ok>(BackupCodec.decode(text)).backup)

        assertEquals(exported, repository.exportBackupData(), "the passages, the ladder, the log, the plan, the notes")
        assertEquals(mirrorBefore, mirrorState(), "surah_todo and ayah_todo are rebuilt from the passages")
        assertEquals("done", db.getHifzDao().getMeta("legacy_import"))
        // The passage that was forgotten is back, with its stage and its difficult ayah.
        val s114 = repository.surahCoverage(114).first()
        assertEquals(PassageStage.R3, s114.passages.single().stage)
        assertEquals(setOf(3), s114.hardAyahs)
    }

    @Test
    fun aReplaceThatFailsInTheMiddleLeavesThePreviousDataAsItWas() = runBlocking {
        seedProgress()
        val before = repository.exportBackupData()
        val mirrorBefore = mirrorState()
        // Two passages with one id pass no check of the file, but they cannot be written: the transaction fails after the tables are emptied.
        val good = fileOf(before)
        val doubled = good.hifz.passages + good.hifz.passages.first()
        val broken = VerifiedBackup(good.copy(hifz = good.hifz.copy(passages = doubled)))

        assertFails { repository.replaceWithBackup(broken) }

        assertEquals(before, repository.exportBackupData())
        assertEquals(mirrorBefore, mirrorState())
        assertEquals("done", db.getHifzDao().getMeta("legacy_import"))
    }

    // ---- "Listen to the old" (task F1b): a LISTEN record changes nothing but its own line ----

    @Test
    fun listeningIsWrittenToTheListenLogOnlyAndMovesNothing() = runBlocking {
        seedProgress()
        val ids = repository.surahCoverage(112).first().passages.map { it.id }
        val lessonBefore = repository.lesson(day + 1).first()
        val streakBefore = repository.streak(day + 1).first()
        val studyBefore = repository.reviewLog(0, day + 5).first()
        val passagesBefore = db.getHifzDao().getPassages().map { it.passage }
        val mirrorBefore = mirrorState()

        // An unknown passage is skipped, the known ones get one record each.
        repository.logListened(ids + 9_999L, day + 1)

        assertEquals(passagesBefore, db.getHifzDao().getPassages().map { it.passage }, "stage, due day, last repetition: untouched")
        assertEquals(lessonBefore, repository.lesson(day + 1).first(), "done today, catching up, minutes, tomorrow: untouched")
        assertEquals(streakBefore, repository.streak(day + 1).first())
        assertEquals(studyBefore, repository.reviewLog(0, day + 5).first(), "the log of the study has no LISTEN")
        assertEquals(mirrorBefore, mirrorState())
        val listened = repository.listenLog(0, day + 5).first()
        assertEquals(ids.size, listened.size)
        assertTrue(listened.all { it.kind == ReviewKind.LISTEN && it.grade == ReviewGrade.GOOD && it.epochDay == day + 1 })
        assertEquals(ids.toSet(), listened.mapNotNull { it.passageId }.toSet())
        assertTrue(repository.exportBackupData().hifz.reviewLog.any { it.kind == "LISTEN" }, "the transfer file has it")
        // And listening to nothing writes nothing.
        repository.logListened(emptyList(), day + 1)
        assertEquals(ids.size, repository.listenLog(0, day + 5).first().size)
    }

    @Test
    fun aDayOfListeningAloneIsNotADayOfTheStreak() = runBlocking {
        seedProgress() // study on `day` and `day + 1`
        val ids = repository.surahCoverage(112).first().passages.map { it.id }
        assertEquals(2, repository.streak(day + 1).first())
        repository.logListened(ids, day + 2)
        assertEquals(2, repository.streak(day + 2).first(), "alive from yesterday: listening today did not make it 3")
        repository.logListened(ids, day + 4)
        assertEquals(0, repository.streak(day + 4).first(), "a day of listening alone does not keep the streak")
    }

    @Test
    fun aListenRecordSurvivesABackupRoundTrip() = runBlocking {
        seedProgress()
        repository.logListened(repository.surahCoverage(112).first().passages.map { it.id }, day + 1)
        val exported = repository.exportBackupData()
        val text = BackupCodec.encode(verifiedOf(exported))
        repository.replaceWithBackup(assertIs<BackupParse.Ok>(BackupCodec.decode(text)).backup)
        assertEquals(exported, repository.exportBackupData())
        assertTrue(repository.listenLog(0, day + 5).first().isNotEmpty())
        assertTrue(repository.reviewLog(0, day + 5).first().none { it.kind == ReviewKind.LISTEN })
    }

    // ---- "Quiz": markHard (task F2b) ----

    @Test
    fun markHardMarksTheCoveringPassagesAndChangesNothingElse() = runBlocking {
        seedProgress() // 114:1-4 on the ladder with 3 difficult, 112 known (OLD) with 2 difficult
        val passagesBefore = db.getHifzDao().getPassages().map { it.passage }
        val logBefore = db.getHifzDao().getAllLog()
        val streakBefore = repository.streak(day + 1).first()
        val studyBefore = repository.reviewLog(0, day + 5).first()
        val mirrorBefore = mirrorState()
        val lessonBefore = repository.lesson(day + 4).first()
        assertEquals(setOf(3), repository.surahCoverage(114).first().hardAyahs)

        // 1 and 4 are covered; 5 and 99 are not (skipped). A mark that is there starts over at 2.
        repository.markHard(114, setOf(1, 3, 4, 5, 99), day + 2)

        assertEquals(setOf(1, 3, 4), repository.surahCoverage(114).first().hardAyahs)
        assertEquals(mapOf(1 to 2, 3 to 2, 4 to 2), repository.surahCoverage(114).first().passages.single().hard)
        assertEquals(passagesBefore, db.getHifzDao().getPassages().map { it.passage }, "stage, due day, last repetition: untouched")
        assertEquals(logBefore, db.getHifzDao().getAllLog(), "the review log is not written")
        assertEquals(streakBefore, repository.streak(day + 1).first())
        assertEquals(studyBefore, repository.reviewLog(0, day + 5).first())
        assertEquals(mirrorBefore, mirrorState())
        // The lesson is the same but for the difficult marks of its cards.
        val lessonAfter = repository.lesson(day + 4).first()
        assertEquals(lessonBefore.copy(recent = emptyList(), old = emptyList()), lessonAfter.copy(recent = emptyList(), old = emptyList()))
        assertEquals(lessonBefore.recent.map { it.copy(hardAyahs = emptySet()) }, lessonAfter.recent.map { it.copy(hardAyahs = emptySet()) })
        assertEquals(lessonBefore.old.map { it.copy(hardAyahs = emptySet()) }, lessonAfter.old.map { it.copy(hardAyahs = emptySet()) })
    }

    @Test
    fun markHardWithNothingToMarkOrAnUnknownSurah() = runBlocking {
        seedProgress()
        val before = db.getHifzDao().getPassages().map { it.passage to it.hard }
        repository.markHard(114, emptySet(), day)
        repository.markHard(114, setOf(6), day) // 114:5-6 are not covered
        repository.markHard(111, setOf(1, 2), day) // a surah without passages
        assertEquals(before, db.getHifzDao().getPassages().map { it.passage to it.hard })
        assertFailsWith<IllegalArgumentException> { repository.markHard(0, setOf(1), day) }
        assertFailsWith<IllegalArgumentException> { repository.markHard(115, setOf(1), day) }
        Unit
    }

    // ---- The pause of the plan (task F2b) ----

    private suspend fun passages() = db.getHifzDao().getPassages().map { it.passage }.sortedBy { it.id }

    @Test
    fun aPauseMovesTheCalendarAndTheLessonThatComesBackIsTheOneThatWasWaiting() = runBlocking {
        seedProgress() // the plan, 114:1-4 on the ladder (due day + 4), 112 known (OLD, never repeated)
        repository.setPlan(HifzScope.JUZ_30, emptyList(), 5, day + 60, 7, day) // a target day, so that it moves too
        val planBefore = assertNotNull(repository.plan().first())
        val passagesBefore = passages()
        val lessonWithout = repository.lesson(day + 2).first()
        assertTrue(!lessonWithout.isEmpty)

        repository.pausePlan(3, day + 2)

        assertEquals(PlanPause(day + 2, day + 4), repository.pause(day + 2).first())
        assertEquals(PlanPause(day + 2, day + 4), repository.pause(day).first(), "ahead of today: still the pause")
        assertNull(repository.pause(day + 5).first(), "a pause that is over is null")
        assertEquals("${day + 2},${day + 4}", db.getHifzDao().getMeta(PlanPauses.META_KEY))

        // The calendar moved by three days; a null stays null, nothing else changed.
        passages().zip(passagesBefore).forEach { (after, before) ->
            assertEquals(before.copy(nextDueEpochDay = before.nextDueEpochDay?.plus(3), lastReviewedEpochDay = before.lastReviewedEpochDay?.plus(3)), after)
        }
        val planAfter = assertNotNull(repository.plan().first())
        assertEquals(planBefore.copy(targetEpochDay = day + 63, startedEpochDay = planBefore.startedEpochDay + 3), planAfter)

        // Inside the pause the lesson is empty and says so; the first day after it is the lesson that was waiting.
        for (inside in day + 2..day + 4) {
            val paused = repository.lesson(inside).first()
            assertTrue(paused.isEmpty)
            assertEquals(PlanPause(day + 2, day + 4), paused.paused)
        }
        val back = repository.lesson(day + 5).first()
        assertNull(back.paused)
        assertEquals(lessonWithout, back.copy(today = day + 2))
    }

    @Test
    fun aPauseIsExtendedAndEndedEarlyThroughTheRepository() = runBlocking {
        seedProgress()
        val original = passages()

        repository.pausePlan(3, day + 2) // day + 2 .. day + 4
        repository.pausePlan(5, day + 3) // the end becomes day + 7: three more days
        assertEquals(PlanPause(day + 2, day + 7), repository.pause(day + 3).first())
        passages().zip(original).forEach { (after, before) -> assertEquals(before.nextDueEpochDay?.plus(6), after.nextDueEpochDay) }

        repository.resumePlan(day + 4) // the pause was on for day + 2 and day + 3: two days stay
        assertEquals(PlanPause(day + 2, day + 3), db.getHifzDao().getMeta(PlanPauses.META_KEY)?.let { PlanPauses.decode(it).single() })
        assertNull(repository.pause(day + 4).first())
        passages().zip(original).forEach { (after, before) ->
            assertEquals(before.nextDueEpochDay?.plus(2), after.nextDueEpochDay)
            assertEquals(before.lastReviewedEpochDay?.plus(2), after.lastReviewedEpochDay)
        }
        assertNull(repository.lesson(day + 4).first().paused)

        // Nothing is left to end: it changes nothing.
        val settled = passages()
        repository.resumePlan(day + 4)
        assertEquals(settled, passages())
    }

    @Test
    fun endingAPauseOnItsFirstDayRestoresEverythingAndRemovesIt() = runBlocking {
        seedProgress()
        repository.setPlan(HifzScope.JUZ_30, emptyList(), 5, day + 40, 7, day)
        val planBefore = repository.plan().first()
        val passagesBefore = passages()
        val lessonBefore = repository.lesson(day + 2).first()

        repository.pausePlan(7, day + 2)
        assertNotNull(db.getHifzDao().getMeta(PlanPauses.BASE_START_KEY), "the day the plan really started is kept for the forecast")
        repository.resumePlan(day + 2)

        assertEquals(passagesBefore, passages())
        assertEquals(planBefore, repository.plan().first())
        assertNull(repository.pause(day + 2).first())
        assertNull(db.getHifzDao().getMeta(PlanPauses.META_KEY), "a pause that started today is removed whole")
        assertNull(db.getHifzDao().getMeta(PlanPauses.BASE_START_KEY))
        assertEquals(lessonBefore, repository.lesson(day + 2).first())
    }

    @Test
    fun aPauseDoesNotBreakTheStreakAndDoesNotAddToIt() = runBlocking {
        seedProgress() // study on day and day + 1: a streak of 2 at day + 1
        assertEquals(2, repository.streak(day + 1).first())
        assertEquals(0, repository.streak(day + 4).first(), "without a pause three quiet days break it")

        repository.pausePlan(3, day + 2) // day + 2 .. day + 4
        assertEquals(2, repository.streak(day + 2).first())
        assertEquals(2, repository.streak(day + 4).first(), "inside the pause: alive")
        assertEquals(2, repository.streak(day + 5).first(), "the first day after it, nothing done yet: alive")
        assertEquals(0, repository.streak(day + 6).first(), "the second quiet day after it breaks the streak")
        // A lesson on the first day after the pause: the pause days bridge, they do not count.
        repository.logCheck(112, 1, 4, ReviewGrade.GOOD, emptySet(), day + 5)
        assertEquals(3, repository.streak(day + 5).first())
    }

    @Test
    fun theForecastOfAPlanWithATargetIsTheSameAfterAPause() = runBlocking {
        seedProgress()
        repository.setPlan(HifzScope.JUZ_30, emptyList(), 5, day + 40, 7, day)
        val without = repository.forecast(day + 9).first()!!
        repository.pausePlan(3, day + 6) // the first six days of the plan were active
        val after = repository.forecast(day + 12).first()!!
        assertEquals(without.behindAyahs, after.behindAyahs)
        assertEquals(without.requiredPerDay, after.requiredPerDay)
        assertEquals(without.targetEpochDay?.plus(3), after.targetEpochDay)
    }

    @Test
    fun aPausedPlanWithoutAPlanStillMovesThePassages() = runBlocking {
        seedProgress()
        repository.clearPlan()
        val before = passages()
        repository.pausePlan(2, day + 2)
        passages().zip(before).forEach { (after, was) -> assertEquals(was.nextDueEpochDay?.plus(2), after.nextDueEpochDay) }
        val lesson = repository.lesson(day + 2).first()
        assertTrue(lesson.isEmpty)
        assertTrue(!lesson.hasPlan)
        assertEquals(PlanPause(day + 2, day + 3), lesson.paused)
        assertNull(db.getHifzDao().getMeta(PlanPauses.BASE_START_KEY))
    }

    @Test
    fun thePauseSurvivesABackupRoundTrip() = runBlocking {
        seedProgress()
        repository.pausePlan(4, day + 2)
        val exported = repository.exportBackupData()
        assertEquals("${day + 2},${day + 5}", exported.hifz.meta[PlanPauses.META_KEY])
        val text = BackupCodec.encode(verifiedOf(exported))

        // The phone changes afterwards: the pause is over and the plan gone.
        repository.resumePlan(day + 2)
        repository.clearPlan()
        assertNull(repository.pause(day + 2).first())

        repository.replaceWithBackup(assertIs<BackupParse.Ok>(BackupCodec.decode(text)).backup)
        assertEquals(exported, repository.exportBackupData())
        assertEquals(PlanPause(day + 2, day + 5), repository.pause(day + 2).first())
        assertTrue(repository.lesson(day + 3).first().isEmpty)
        assertEquals(PlanPause(day + 2, day + 5), repository.lesson(day + 3).first().paused)
    }

    @Test
    fun anImportedBackupStopsTheLegacyConversionFromRunningOverIt() = runBlocking {
        seedLegacy()
        val empty = BackupData(BackupHifz(plan = null, passages = emptyList(), hard = emptyList(), reviewLog = emptyList(), meta = emptyMap()), emptyList())
        repository.replaceWithBackup(verifiedOf(empty))

        repository.importLegacyIfNeeded()

        assertTrue(db.getHifzDao().getPassages().isEmpty(), "no passage came from the old rows")
        assertNull(repository.plan().first())
        assertTrue(surahRows().isEmpty() && db.getHifzDao().getAllAyahTodos().isEmpty(), "the mirror cleared the old rows")
        assertEquals(1, db.getAyahReviewDao().getAll().size, "ayah_review is not touched")
    }
}
