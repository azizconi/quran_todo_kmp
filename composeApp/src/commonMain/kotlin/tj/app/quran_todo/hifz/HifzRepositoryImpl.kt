package tj.app.quran_todo.hifz

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.database.entity.hifz.HifzMetaEntity
import tj.app.quran_todo.data.database.entity.hifz.ReviewLogEntity
import tj.app.quran_todo.domain.repository.QuranRemoteRepository
import tj.app.quran_todo.hifz.backup.BackupData
import tj.app.quran_todo.hifz.backup.BackupMapping
import tj.app.quran_todo.hifz.backup.VerifiedBackup
import tj.app.quran_todo.hifz.legacy.LegacyImport
import tj.app.quran_todo.hifz.legacy.LegacyInput
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.NewPassageResult
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.model.ReviewLogEntry
import tj.app.quran_todo.hifz.model.SurahCoverage
import tj.app.quran_todo.hifz.model.SurahHifzStatus
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import tj.app.quran_todo.hifz.schedule.LegacyMirror
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.PassageOps
import tj.app.quran_todo.hifz.schedule.PlanForecast
import tj.app.quran_todo.hifz.schedule.Streak
import tj.app.quran_todo.hifz.schedule.TextMetrics

/**
 * [HifzRepository] on Room. Every write: take the mutex of the repository, open ONE writer transaction, read the state,
 * compute the new one with the pure functions of `hifz/schedule`, write the difference (passages, difficult ayahs, the log,
 * the plan) and the mirror in the old tables, commit. Nothing outside the transaction depends on what was read inside it.
 */
class HifzRepositoryImpl(
    private val db: QuranTodoDatabase,
    private val quran: QuranRemoteRepository,
    private val legacySettings: HifzLegacySettings = PrefsHifzLegacySettings,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : HifzRepository {
    private val dao = db.getHifzDao()
    private val writeMutex = Mutex()
    private val metricsMutex = Mutex()
    private var cachedMetrics: TextMetrics? = null

    // ------------------------------------------------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------------------------------------------------

    /** The text metrics, read once from the table `ayahs` after the bundled text is made sure to be there. */
    private suspend fun metrics(): TextMetrics {
        cachedMetrics?.let { return it }
        return metricsMutex.withLock {
            cachedMetrics ?: run {
                quran.ensureQuranInstalled()
                val rows = dao.getMetricRows()
                check(rows.size == QuranFacts.AYAH_COUNT) { "the database has ${rows.size} ayahs" }
                TextMetrics.fromRows(rows).also { cachedMetrics = it }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------------------------------------------------

    private fun <T> observe(vararg tables: String, load: suspend () -> T): Flow<T> =
        db.invalidationTracker.createFlow(*tables)
            .map { read(load) }
            .distinctUntilChanged()

    /**
     * Like [observe] for results that need the text metrics. The metrics are read BEFORE the read transaction is opened:
     * the very first call may have to write the bundled Quran text into the database.
     */
    private fun <T> observeWithMetrics(vararg tables: String, load: suspend (TextMetrics) -> T): Flow<T> =
        db.invalidationTracker.createFlow(*tables)
            .map {
                val metrics = metrics()
                read { load(metrics) }
            }
            .distinctUntilChanged()

    /** Reads in one transaction, so that the tables a result is made of agree with each other. */
    private suspend fun <T> read(load: suspend () -> T): T =
        db.useReaderConnection { transactor -> transactor.deferredTransaction { load() } }

    private suspend fun loadPassages(): List<Passage> = dao.getPassages().map { it.toDomain() }

    private suspend fun loadPlan(): HifzPlan? = dao.getPlan()?.toDomain()

    override fun lesson(today: Int): Flow<LessonPlan> = observeWithMetrics(PASSAGES, HARD, PLAN, LOG) { metrics ->
        LessonBuilder.build(
            LessonInput(
                today = today,
                plan = loadPlan(),
                passages = loadPassages(),
                todayLog = dao.getLog(today, today).map { it.toDomain() },
                metrics = metrics,
            ),
        )
    }

    override fun lesson(today: Flow<Int>): Flow<LessonPlan> =
        today.distinctUntilChanged().flatMapLatest { lesson(it) }

    override fun plan(): Flow<HifzPlan?> = observe(PLAN) { loadPlan() }

    override fun forecast(today: Int): Flow<PlanForecast?> = observeWithMetrics(PASSAGES, PLAN, LOG) { metrics ->
        val plan = loadPlan()
        if (plan == null) {
            null
        } else {
            val log = dao.getNewLogSince(plan.startedEpochDay).map { it.toDomain() }
            Forecast.compute(plan, Coverage(loadPassages(), metrics), log, today)
        }
    }

    override fun forecast(today: Flow<Int>): Flow<PlanForecast?> =
        today.distinctUntilChanged().flatMapLatest { forecast(it) }

    override fun forecast(plan: HifzPlan, today: Int): Flow<PlanForecast> = observeWithMetrics(PASSAGES, LOG) { metrics ->
        val log = dao.getNewLogSince(plan.startedEpochDay).map { it.toDomain() }
        Forecast.compute(plan, Coverage(loadPassages(), metrics), log, today)
    }

    override fun streak(today: Int): Flow<Int> = observe(LOG) { Streak.compute(dao.getLogDays(today), today) }

    override fun streak(today: Flow<Int>): Flow<Int> = today.distinctUntilChanged().flatMapLatest { streak(it) }

    override fun surahStatuses(): Flow<Map<Int, SurahHifzStatus>> = observeWithMetrics(PASSAGES, PLAN) { metrics ->
        HifzStatuses.all(loadPassages(), loadPlan(), metrics)
    }

    override fun coveredCounts(): Flow<Map<Int, Int>> = observeWithMetrics(PASSAGES) { metrics ->
        val coverage = Coverage(loadPassages(), metrics)
        val counts = LinkedHashMap<Int, Int>()
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            val covered = coverage.coveredCount(surah)
            if (covered > 0) counts[surah] = covered
        }
        counts
    }

    override fun coveredAyahs(): Flow<Map<Int, Set<Int>>> = observeWithMetrics(PASSAGES) { metrics ->
        val coverage = Coverage(loadPassages(), metrics)
        val ayahs = LinkedHashMap<Int, Set<Int>>()
        for (surah in 1..QuranFacts.SURAH_COUNT) {
            if (coverage.coveredCount(surah) > 0) ayahs[surah] = coverage.coveredAyahs(surah)
        }
        ayahs
    }

    override fun hardAyahs(): Flow<Map<Int, Set<Int>>> = observe(PASSAGES, HARD) { HifzStatuses.hardAyahs(loadPassages()) }

    override fun surahCoverage(surah: Int): Flow<SurahCoverage> = observeWithMetrics(PASSAGES, HARD, PLAN) { metrics ->
        HifzStatuses.surahCoverage(surah, loadPassages(), loadPlan(), metrics)
    }

    override fun reviewLog(fromDay: Int, toDay: Int): Flow<List<ReviewLogEntry>> = observe(LOG) {
        dao.getLog(fromDay, toDay).map { it.toDomain() }
    }

    override fun oldWeight(): Flow<Int> = observeWithMetrics(PASSAGES) { metrics ->
        loadPassages().filter { it.stage == PassageStage.OLD }.sumOf { metrics.weight(it) }
    }

    override suspend fun estimateMinutes(surah: Int, startAyah: Int, endAyah: Int, isNew: Boolean): Int {
        val metrics = metrics()
        requireRange(metrics, surah, startAyah, endAyah)
        val chars = metrics.chars(surah, startAyah, endAyah)
        val seconds = if (isNew) HifzConstants.newSeconds(chars) else HifzConstants.reviewSeconds(chars)
        return HifzConstants.minutes(seconds)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------------------------------------------------

    private suspend fun <T> write(block: suspend () -> T): T =
        writeMutex.withLock {
            db.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
        }

    override suspend fun completeNew(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        grade: ReviewGrade,
        hardAyahs: Set<Int>,
        today: Int,
    ): NewPassageResult {
        val metrics = metrics()
        requireRange(metrics, surah, startAyah, endAyah)
        return write {
            val before = loadPassages()
            val plan = loadPlan()
            if (PassageOps.overlaps(before, surah, startAyah, endAyah)) return@write NewPassageResult.Overlaps
            val created = PassageOps.newPassage(surah, startAyah, endAyah, hardAyahs, today)
            val after = persist(before, before + created)
            val id = after.last().id
            insertLog(id, surah, startAyah, endAyah, ReviewKind.NEW, grade, today)
            mirror(affectedSurahs(before, after, plan, plan, metrics), after, plan, metrics)
            NewPassageResult.Created(id)
        }
    }

    override suspend fun review(
        passageId: Long,
        grade: ReviewGrade,
        hardAyahs: Set<Int>,
        today: Int,
    ): ReviewResult? {
        val metrics = metrics()
        return write {
            val before = loadPassages()
            val plan = loadPlan()
            val outcome = PassageOps.review(before, passageId, grade, hardAyahs, today, metrics) ?: return@write null
            val after = persist(before, outcome.passages)
            val was = outcome.before
            insertLog(passageId, was.surah, was.startAyah, was.endAyah, outcome.kind, grade, today)
            mirror(affectedSurahs(before, after, plan, plan, metrics), after, plan, metrics)
            val now = after.first { it.id == passageId }
            ReviewResult(
                passageId = passageId,
                kind = outcome.kind,
                stage = now.stage,
                nextDueEpochDay = now.nextDueEpochDay,
                merged = was.stage != PassageStage.OLD && now.stage == PassageStage.OLD &&
                    (now.startAyah != was.startAyah || now.endAyah != was.endAyah),
            )
        }
    }

    override suspend fun logCheck(
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        grade: ReviewGrade,
        hardAyahs: Set<Int>,
        today: Int,
    ) {
        val metrics = metrics()
        requireRange(metrics, surah, startAyah, endAyah)
        write {
            val before = loadPassages()
            val after = persist(before, PassageOps.check(before, surah, hardAyahs))
            val coveringId = PassageOps.coveringPassageId(after, surah, startAyah, endAyah)
            insertLog(coveringId, surah, startAyah, endAyah, ReviewKind.CHECK, grade, today)
        }
    }

    override suspend fun markKnown(surah: Int, startAyah: Int, endAyah: Int, today: Int) {
        val metrics = metrics()
        val last = metrics.ayahCount(surah)
        require(last > 0) { "no such surah: $surah" }
        val start = startAyah.coerceAtLeast(1)
        val end = endAyah.coerceAtMost(last)
        if (start > end) return
        write {
            val before = loadPassages()
            val plan = loadPlan()
            val after = persist(before, PassageOps.markKnown(before, surah, start, end, today, metrics))
            mirror(affectedSurahs(before, after, plan, plan, metrics), after, plan, metrics)
        }
    }

    override suspend fun forget(surah: Int, startAyah: Int, endAyah: Int) {
        val metrics = metrics()
        val last = metrics.ayahCount(surah)
        require(last > 0) { "no such surah: $surah" }
        val start = startAyah.coerceAtLeast(1)
        val end = endAyah.coerceAtMost(last)
        if (start > end) return
        write {
            val before = loadPassages()
            val plan = loadPlan()
            val after = persist(before, PassageOps.forget(before, surah, start, end))
            mirror(affectedSurahs(before, after, plan, plan, metrics), after, plan, metrics)
        }
    }

    override suspend fun setPlan(
        scope: HifzScope,
        surahOrder: List<Int>,
        dailyNewAyahs: Int,
        targetEpochDay: Int?,
        manzilDays: Int,
        today: Int,
    ) {
        val order = surahOrder.ifEmpty { HifzPresets.order(scope) }
            .filter { it in 1..QuranFacts.SURAH_COUNT }
            .distinct()
        require(order.isNotEmpty()) { "the plan has no surahs" }
        val metrics = metrics()
        write {
            val passages = loadPassages()
            val old = loadPlan()
            val started = if (old != null && old.surahOrder == order) old.startedEpochDay else today
            val new = HifzPlan(
                scope = scope,
                surahOrder = order,
                dailyNewAyahs = dailyNewAyahs.coerceIn(HifzConstants.DAILY_NEW_MIN, HifzConstants.DAILY_NEW_MAX),
                targetEpochDay = targetEpochDay,
                startedEpochDay = started,
                manzilDays = manzilDays.coerceIn(HifzConstants.MANZIL_DAYS_MIN, HifzConstants.MANZIL_DAYS_MAX),
            )
            dao.upsertPlan(new.toEntity())
            mirror(affectedSurahs(passages, passages, old, new, metrics), passages, new, metrics)
        }
    }

    override suspend fun learnNext(surah: Int, today: Int) {
        require(surah in 1..QuranFacts.SURAH_COUNT) { "no such surah: $surah" }
        val metrics = metrics()
        write {
            val passages = loadPassages()
            val old = loadPlan()
            val new = if (old == null) {
                HifzPlan(
                    scope = HifzScope.CUSTOM,
                    surahOrder = listOf(surah),
                    dailyNewAyahs = HifzConstants.DAILY_NEW_DEFAULT,
                    targetEpochDay = null,
                    startedEpochDay = today,
                    manzilDays = HifzConstants.MANZIL_DAYS_DEFAULT,
                )
            } else {
                old.copy(scope = HifzScope.CUSTOM, surahOrder = listOf(surah) + old.surahOrder.filter { it != surah })
            }
            dao.upsertPlan(new.toEntity())
            mirror(affectedSurahs(passages, passages, old, new, metrics), passages, new, metrics)
        }
    }

    override suspend fun clearPlan() {
        val metrics = metrics()
        write {
            val passages = loadPassages()
            val old = loadPlan() ?: return@write
            dao.deletePlan()
            mirror(affectedSurahs(passages, passages, old, null, metrics), passages, null, metrics)
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Legacy import
    // ------------------------------------------------------------------------------------------------------------

    override suspend fun importLegacyIfNeeded() {
        if (dao.getMeta(META_LEGACY_IMPORT) == META_DONE) return
        val metrics = metrics()
        val imported = write {
            if (dao.getMeta(META_LEGACY_IMPORT) == META_DONE) return@write false
            val today = EpochDays.ofMillis(clock(), zone())
            val input = LegacyInput(
                surahTodos = dao.getAllSurahTodos(),
                ayahTodos = dao.getAllAyahTodos(),
                ayahReviews = dao.getAllAyahReviews(),
                weakKeys = legacySettings.weakAyahKeys(),
                dailyGoal = legacySettings.dailyGoal(),
                today = today,
                zone = zone(),
            )
            val result = LegacyImport.run(input, metrics)
            val before = loadPassages()
            val existingPlan = loadPlan()
            val after = persist(before, before + result.passages)
            val plan = existingPlan ?: result.plan
            if (existingPlan == null && result.plan != null) dao.upsertPlan(result.plan.toEntity())
            // The old screens keep what they show now: only the surahs that got passages (and the current one) are rewritten.
            val surahs = after.map { it.surah }.toMutableSet()
            HifzStatuses.currentSurah(plan, Coverage(after, metrics))?.let { surahs += it }
            mirror(surahs, after, plan, metrics)
            dao.upsertMeta(HifzMetaEntity(META_LEGACY_IMPORT, META_DONE))
            true
        }
        // After the commit: if this fails the weak ayahs stay in the preferences, which is harmless.
        if (imported) legacySettings.clearWeakAyahKeys()
    }

    // ------------------------------------------------------------------------------------------------------------
    // Backup file
    // ------------------------------------------------------------------------------------------------------------

    override suspend fun exportBackupData(): BackupData = read {
        BackupMapping.toBackupData(
            plan = dao.getPlan(),
            passages = dao.getPassages(),
            log = dao.getAllLog(),
            meta = dao.getAllMeta(),
            notes = dao.getAllNotes(),
        )
    }

    override suspend fun replaceWithBackup(backup: VerifiedBackup) {
        val metrics = metrics()
        val rows = BackupMapping.toRows(backup.file)
        write {
            // The children first (passage_hard has a foreign key to the passages), then the rest.
            dao.deleteAllHard()
            dao.deleteAllPassages()
            dao.deleteAllLog()
            dao.deletePlan()
            dao.deleteAllMeta()
            dao.deleteAllNotes()
            if (rows.passages.isNotEmpty()) dao.insertPassages(rows.passages)
            if (rows.hard.isNotEmpty()) dao.insertHard(rows.hard)
            if (rows.log.isNotEmpty()) dao.insertLogs(rows.log)
            rows.plan?.let { dao.upsertPlan(it) }
            dao.upsertMetas(rows.meta)
            if (rows.notes.isNotEmpty()) dao.insertNotes(rows.notes)
            // The old screens read surah_todo / ayah_todo: rebuild them for every surah from what was just written.
            mirror((1..QuranFacts.SURAH_COUNT).toSet(), loadPassages(), loadPlan(), metrics)
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers inside a transaction
    // ------------------------------------------------------------------------------------------------------------

    private fun requireRange(metrics: TextMetrics, surah: Int, startAyah: Int, endAyah: Int) {
        val count = metrics.ayahCount(surah)
        require(count > 0) { "no such surah: $surah" }
        require(startAyah in 1..count && endAyah in startAyah..count) { "bad range $surah:$startAyah-$endAyah" }
    }

    /**
     * Writes the difference between [before] and [after] and returns [after] with the ids of the new passages filled in.
     * Deleted passages lose their difficult ayahs explicitly (the foreign key would do it too).
     */
    private suspend fun persist(before: List<Passage>, after: List<Passage>): List<Passage> {
        val beforeById = before.associateBy { it.id }
        val keptIds = after.mapNotNullTo(HashSet()) { it.id.takeIf { id -> id != 0L } }
        val deleted = beforeById.keys.filter { it !in keptIds }
        for (chunk in deleted.chunked(DELETE_CHUNK)) {
            dao.deleteHardOfAll(chunk)
            dao.deletePassages(chunk)
        }
        return after.map { passage ->
            when {
                passage.id == 0L -> {
                    val id = dao.insertPassage(passage.toEntity())
                    val rows = passage.hardRows(id)
                    if (rows.isNotEmpty()) dao.insertHard(rows)
                    passage.copy(id = id)
                }
                beforeById[passage.id] != passage -> {
                    dao.updatePassage(passage.toEntity())
                    dao.deleteHardOf(passage.id)
                    val rows = passage.hardRows(passage.id)
                    if (rows.isNotEmpty()) dao.insertHard(rows)
                    passage
                }
                else -> passage
            }
        }
    }

    private suspend fun insertLog(
        passageId: Long?,
        surah: Int,
        startAyah: Int,
        endAyah: Int,
        kind: ReviewKind,
        grade: ReviewGrade,
        today: Int,
    ) {
        dao.insertLog(
            ReviewLogEntity(
                passageId = passageId,
                surah = surah,
                startAyah = startAyah,
                endAyah = endAyah,
                kind = kind,
                grade = grade,
                epochDay = today,
                timestamp = clock(),
            ),
        )
    }

    /**
     * The surahs whose derived status or covered ayahs changed: those with a different coverage, and the current surah of the
     * plan before and after (a plan or a coverage change moves it).
     */
    private fun affectedSurahs(
        before: List<Passage>,
        after: List<Passage>,
        planBefore: HifzPlan?,
        planAfter: HifzPlan?,
        metrics: TextMetrics,
    ): Set<Int> {
        val coverageBefore = Coverage(before, metrics)
        val coverageAfter = Coverage(after, metrics)
        val candidates = (before.map { it.surah } + after.map { it.surah }).toSet()
        val result = candidates.filterTo(LinkedHashSet()) { coverageBefore.coveredAyahs(it) != coverageAfter.coveredAyahs(it) }
        val currentBefore = HifzStatuses.currentSurah(planBefore, coverageBefore)
        val currentAfter = HifzStatuses.currentSurah(planAfter, coverageAfter)
        if (currentBefore != currentAfter) {
            currentBefore?.let { result += it }
            currentAfter?.let { result += it }
        }
        return result
    }

    /** Rewrites `ayah_todo` and `surah_todo` for [surahs] so that they show the derived statuses of [passages]. */
    private suspend fun mirror(surahs: Set<Int>, passages: List<Passage>, plan: HifzPlan?, metrics: TextMetrics) {
        if (surahs.isEmpty()) return
        val coverage = Coverage(passages, metrics)
        val current = HifzStatuses.currentSurah(plan, coverage)
        val now = clock()
        for (surah in surahs.sorted()) {
            val delta = LegacyMirror.sync(
                surah = surah,
                coveredAyahs = coverage.coveredAyahs(surah),
                status = HifzStatuses.surahStatus(coverage, metrics, surah, current),
                existingAyahRows = dao.getAyahTodosOf(surah),
                existingSurahRow = dao.getSurahTodo(surah),
                now = now,
            )
            for (chunk in delta.deleteAyahNumbers.chunked(DELETE_CHUNK)) dao.deleteAyahTodos(chunk)
            if (delta.upsertAyahs.isNotEmpty()) dao.upsertAyahTodos(delta.upsertAyahs)
            delta.upsertSurah?.let { dao.upsertSurahTodo(it) }
            if (delta.deleteSurah) dao.deleteSurahTodo(surah)
        }
    }

    private companion object {
        const val META_LEGACY_IMPORT = "legacy_import"
        const val META_DONE = "done"
        const val DELETE_CHUNK = 500

        const val PASSAGES = "hifz_passage"
        const val HARD = "passage_hard"
        const val PLAN = "hifz_plan"
        const val LOG = "review_log"
    }
}
