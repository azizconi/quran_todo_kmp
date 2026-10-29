package tj.app.quran_todo.hifz.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.schedule.HifzConstants

/** Why a file is not accepted. The texts are for the developer (tests, logs); the person sees only the kind. */
sealed interface BackupError {
    /** Not a backup of this app: not JSON, or JSON without our `format`. */
    data object NotABackup : BackupError

    /** Made by a newer app ([version] is its `formatVersion`): ask the person to update. */
    data class NewerVersion(val version: Int) : BackupError

    /** Ours, but broken: cut off, a field missing or of the wrong type, ids that do not match, passages that overlap. */
    data class Corrupted(val reason: String) : BackupError

    /** Ours and well-formed, but a value is out of its range: a surah that does not exist, an ayah past the end of its surah, an unknown stage. */
    data class OutOfRange(val field: String) : BackupError
}

/** A file that passed every check: the only thing `HifzRepository.replaceWithBackup` accepts. */
class VerifiedBackup internal constructor(val file: BackupFile) {
    /** What the importer applies to the settings of the phone (already [sanitized]). */
    val settings: BackupSettings get() = file.settings

    val data: BackupData get() = BackupData(file.hifz, file.notes)
}

sealed interface BackupParse {
    class Ok(val backup: VerifiedBackup) : BackupParse
    class Failed(val error: BackupError) : BackupParse
}

/**
 * "Snapshot -> JSON" and "JSON -> checked snapshot": pure functions, no database, no clock, no platform.
 *
 * [decode] answers with the first problem it finds, in this order: not our file, newer version, damaged structure, values out
 * of range. [verify] runs the same checks on a [BackupFile] that is already built (the exporter does it before it writes a file,
 * so the app never writes a file that it would refuse to read).
 */
@OptIn(ExperimentalSerializationApi::class)
object BackupCodec {
    private val json = Json {
        // Forward compatibility: a later app may add fields.
        ignoreUnknownKeys = true
        // `format` and `formatVersion` must be in the file even though they could be defaults.
        encodeDefaults = true
        // Absent = null, and nulls are not written, so the file does not carry "nextDueEpochDay": null of every OLD passage.
        explicitNulls = false
    }

    private const val BOM = '﻿'

    /** The JSON text of [file]. */
    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** The JSON text of a file that was checked. */
    fun encode(backup: VerifiedBackup): String = encode(backup.file)

    /** Parses and checks [text]. */
    fun decode(text: String): BackupParse {
        val source = text.trimStart(BOM)
        val root = try {
            json.parseToJsonElement(source)
        } catch (_: IllegalArgumentException) {
            // A file of ours that was cut off still has the name of the format in it; any other file does not.
            return if (source.contains(BackupFormat.NAME)) {
                failed(BackupError.Corrupted("not valid JSON"))
            } else {
                failed(BackupError.NotABackup)
            }
        }
        if (root !is JsonObject) return failed(BackupError.NotABackup)
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != BackupFormat.NAME) return failed(BackupError.NotABackup)
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull
            ?: return failed(BackupError.Corrupted("formatVersion"))
        if (version > BackupFormat.VERSION) return failed(BackupError.NewerVersion(version))
        if (version < 1) return failed(BackupError.Corrupted("formatVersion"))
        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (_: IllegalArgumentException) {
            return failed(BackupError.Corrupted("structure"))
        }
        return verify(file)
    }

    /** Checks a built [file]: the result holds the file with its settings sanitized. */
    fun verify(file: BackupFile): BackupParse {
        BackupValidator.check(file)?.let { return failed(it) }
        return BackupParse.Ok(VerifiedBackup(file.copy(settings = file.settings.sanitized())))
    }

    private fun failed(error: BackupError) = BackupParse.Failed(error)
}

/** The checks of a decoded file. Structural problems are [BackupError.Corrupted], values outside their range [BackupError.OutOfRange]. */
internal object BackupValidator {
    private const val MAX_DAY = 100_000
    private val STAGES = PassageStage.entries.mapTo(HashSet()) { it.name }
    private val SCOPES = HifzScope.entries.mapTo(HashSet()) { it.name }
    private val KINDS = ReviewKind.entries.mapTo(HashSet()) { it.name }
    private val GRADES = ReviewGrade.entries.mapTo(HashSet()) { it.name }

    fun check(file: BackupFile): BackupError? {
        if (file.format != BackupFormat.NAME) return BackupError.NotABackup
        if (file.formatVersion > BackupFormat.VERSION) return BackupError.NewerVersion(file.formatVersion)
        if (file.formatVersion < 1) return BackupError.Corrupted("formatVersion")
        if (file.exportedAt < 0) return BackupError.OutOfRange("exportedAt")
        return checkPlan(file.hifz.plan)
            ?: checkPassages(file.hifz.passages)
            ?: checkHard(file.hifz.hard, file.hifz.passages)
            ?: checkLog(file.hifz.reviewLog)
            ?: checkMeta(file.hifz.meta)
            ?: checkNotes(file.notes)
    }

    private fun isDay(day: Int) = day in 0..MAX_DAY

    private fun isRange(surah: Int, start: Int, end: Int): Boolean =
        surah in 1..QuranFacts.SURAH_COUNT && start >= 1 && end >= start && end <= QuranFacts.ayahCount(surah)

    private fun checkPlan(plan: BackupPlan?): BackupError? {
        plan ?: return null
        if (plan.scope !in SCOPES) return BackupError.OutOfRange("plan.scope")
        if (plan.surahOrder.isEmpty()) return BackupError.OutOfRange("plan.surahOrder")
        if (plan.surahOrder.any { it !in 1..QuranFacts.SURAH_COUNT }) return BackupError.OutOfRange("plan.surahOrder")
        if (plan.surahOrder.toSet().size != plan.surahOrder.size) return BackupError.Corrupted("plan.surahOrder repeats a surah")
        if (plan.dailyNewAyahs !in HifzConstants.DAILY_NEW_MIN..HifzConstants.DAILY_NEW_MAX) {
            return BackupError.OutOfRange("plan.dailyNewAyahs")
        }
        if (plan.manzilDays !in HifzConstants.MANZIL_DAYS_MIN..HifzConstants.MANZIL_DAYS_MAX) {
            return BackupError.OutOfRange("plan.manzilDays")
        }
        if (!isDay(plan.startedEpochDay)) return BackupError.OutOfRange("plan.startedEpochDay")
        if (plan.targetEpochDay != null && !isDay(plan.targetEpochDay)) return BackupError.OutOfRange("plan.targetEpochDay")
        return null
    }

    private fun checkPassages(passages: List<BackupPassage>): BackupError? {
        val ids = HashSet<Long>()
        for (p in passages) {
            if (p.id <= 0) return BackupError.OutOfRange("passage.id")
            if (!ids.add(p.id)) return BackupError.Corrupted("passage.id repeats")
            if (p.surah !in 1..QuranFacts.SURAH_COUNT) return BackupError.OutOfRange("passage.surah")
            if (!isRange(p.surah, p.startAyah, p.endAyah)) return BackupError.OutOfRange("passage.ayah")
            if (p.stage !in STAGES) return BackupError.OutOfRange("passage.stage")
            if (!isDay(p.learnedEpochDay)) return BackupError.OutOfRange("passage.learnedEpochDay")
            if (p.lapses < 0) return BackupError.OutOfRange("passage.lapses")
            if (p.lastReviewedEpochDay != null && !isDay(p.lastReviewedEpochDay)) {
                return BackupError.OutOfRange("passage.lastReviewedEpochDay")
            }
            val ladder = p.stage != PassageStage.OLD.name
            if (ladder && p.nextDueEpochDay == null) return BackupError.OutOfRange("passage.nextDueEpochDay")
            if (p.nextDueEpochDay != null && !isDay(p.nextDueEpochDay)) return BackupError.OutOfRange("passage.nextDueEpochDay")
        }
        // A passage is a range of ONE surah and no ayah belongs to two of them.
        for ((_, ofSurah) in passages.groupBy { it.surah }) {
            val sorted = ofSurah.sortedBy { it.startAyah }
            for (i in 1 until sorted.size) {
                if (sorted[i].startAyah <= sorted[i - 1].endAyah) return BackupError.Corrupted("passages overlap")
            }
        }
        return null
    }

    private fun checkHard(hard: List<BackupHard>, passages: List<BackupPassage>): BackupError? {
        val byId = passages.associateBy { it.id }
        val seen = HashSet<Pair<Long, Int>>()
        for (h in hard) {
            val passage = byId[h.passageId] ?: return BackupError.Corrupted("hard.passageId has no passage")
            if (h.ayah !in passage.startAyah..passage.endAyah) return BackupError.OutOfRange("hard.ayah")
            if (h.clearsAfter !in 1..HifzConstants.HARD_CLEARS_AFTER) return BackupError.OutOfRange("hard.clearsAfter")
            if (!seen.add(h.passageId to h.ayah)) return BackupError.Corrupted("hard repeats an ayah")
        }
        return null
    }

    private fun checkLog(log: List<BackupLogEntry>): BackupError? {
        val ids = HashSet<Long>()
        for (e in log) {
            if (e.id <= 0) return BackupError.OutOfRange("reviewLog.id")
            if (!ids.add(e.id)) return BackupError.Corrupted("reviewLog.id repeats")
            if (e.surah !in 1..QuranFacts.SURAH_COUNT) return BackupError.OutOfRange("reviewLog.surah")
            if (!isRange(e.surah, e.startAyah, e.endAyah)) return BackupError.OutOfRange("reviewLog.ayah")
            if (e.kind !in KINDS) return BackupError.OutOfRange("reviewLog.kind")
            if (e.grade !in GRADES) return BackupError.OutOfRange("reviewLog.grade")
            if (!isDay(e.epochDay)) return BackupError.OutOfRange("reviewLog.epochDay")
            if (e.timestamp < 0) return BackupError.OutOfRange("reviewLog.timestamp")
        }
        return null
    }

    private fun checkMeta(meta: Map<String, String>): BackupError? =
        if (meta.keys.any { it.isBlank() }) BackupError.OutOfRange("meta.key") else null

    private fun checkNotes(notes: List<BackupNote>): BackupError? {
        val seen = HashSet<Pair<Int, Int>>()
        for (n in notes) {
            if (n.surah !in 1..QuranFacts.SURAH_COUNT) return BackupError.OutOfRange("note.surah")
            if (n.ayah !in 1..QuranFacts.ayahCount(n.surah)) return BackupError.OutOfRange("note.ayah")
            if (n.updatedAt < 0) return BackupError.OutOfRange("note.updatedAt")
            if (!seen.add(n.surah to n.ayah)) return BackupError.Corrupted("notes repeat an ayah")
        }
        return null
    }
}
