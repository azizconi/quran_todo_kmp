package tj.app.quran_todo.hifz

import tj.app.quran_todo.common.settings.UserSettingsStorage
import tj.app.quran_todo.data.database.entity.hifz.HifzPassageEntity
import tj.app.quran_todo.data.database.entity.hifz.HifzPlanEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageHardEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageWithHard
import tj.app.quran_todo.data.database.entity.hifz.ReviewLogEntity
import tj.app.quran_todo.hifz.model.HifzPlan
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.ReviewLogEntry

internal fun PassageWithHard.toDomain() = Passage(
    id = passage.id,
    surah = passage.surah,
    startAyah = passage.startAyah,
    endAyah = passage.endAyah,
    learnedEpochDay = passage.learnedEpochDay,
    stage = passage.stage,
    nextDueEpochDay = passage.nextDueEpochDay,
    lastReviewedEpochDay = passage.lastReviewedEpochDay,
    lapses = passage.lapses,
    hard = hard.sortedBy { it.ayahInSurah }.associate { it.ayahInSurah to it.clearsAfter },
)

internal fun Passage.toEntity() = HifzPassageEntity(
    id = id,
    surah = surah,
    startAyah = startAyah,
    endAyah = endAyah,
    learnedEpochDay = learnedEpochDay,
    stage = stage,
    nextDueEpochDay = nextDueEpochDay,
    lastReviewedEpochDay = lastReviewedEpochDay,
    lapses = lapses,
)

internal fun Passage.hardRows(storedId: Long): List<PassageHardEntity> =
    hard.entries.sortedBy { it.key }.map { PassageHardEntity(storedId, it.key, it.value) }

internal fun HifzPlanEntity.toDomain() = HifzPlan(
    scope = scope,
    surahOrder = surahOrder.split(',').mapNotNull { it.trim().toIntOrNull() },
    dailyNewAyahs = dailyNewAyahs,
    targetEpochDay = targetEpochDay,
    startedEpochDay = startedEpochDay,
    manzilDays = manzilDays,
)

internal fun HifzPlan.toEntity() = HifzPlanEntity(
    id = HifzPlanEntity.PLAN_ID,
    scope = scope,
    surahOrder = surahOrder.joinToString(","),
    dailyNewAyahs = dailyNewAyahs,
    targetEpochDay = targetEpochDay,
    startedEpochDay = startedEpochDay,
    manzilDays = manzilDays,
)

internal fun ReviewLogEntity.toDomain() = ReviewLogEntry(
    id = id,
    passageId = passageId,
    surah = surah,
    startAyah = startAyah,
    endAyah = endAyah,
    kind = kind,
    grade = grade,
    epochDay = epochDay,
    timestamp = timestamp,
)

/**
 * What the legacy import reads outside the database: the weak ayahs and the daily goal live in the preferences.
 * A parameter of the repository so that tests do not need the platform preferences.
 */
interface HifzLegacySettings {
    /** Keys `"surah:number"` of the weak ayahs. */
    fun weakAyahKeys(): Set<String>

    fun dailyGoal(): Int?

    fun clearWeakAyahKeys()
}

/** The real preferences (`UserSettingsStorage`). */
object PrefsHifzLegacySettings : HifzLegacySettings {
    override fun weakAyahKeys(): Set<String> = UserSettingsStorage.getWeakAyahKeys().orEmpty()

    override fun dailyGoal(): Int? = UserSettingsStorage.getDailyGoal()

    override fun clearWeakAyahKeys() = UserSettingsStorage.saveWeakAyahKeys(emptySet())
}
