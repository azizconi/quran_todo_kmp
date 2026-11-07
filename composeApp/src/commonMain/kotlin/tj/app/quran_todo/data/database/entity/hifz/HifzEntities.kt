package tj.app.quran_todo.data.database.entity.hifz

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.model.ReviewKind

/** The one active plan; the row is `id = 1`. No plan means no row. */
@Entity(tableName = "hifz_plan")
data class HifzPlanEntity(
    @PrimaryKey
    val id: Int = PLAN_ID,
    val scope: HifzScope,
    /** Surah numbers separated by commas, in the order they are learned (no type converter needed). */
    val surahOrder: String,
    val dailyNewAyahs: Int,
    val targetEpochDay: Int?,
    val startedEpochDay: Int,
    val manzilDays: Int,
) {
    companion object {
        const val PLAN_ID = 1
    }
}

@Entity(
    tableName = "hifz_passage",
    indices = [Index("surah"), Index("stage", "nextDueEpochDay")],
)
data class HifzPassageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val learnedEpochDay: Int,
    val stage: PassageStage,
    /** Null for [PassageStage.OLD]. */
    val nextDueEpochDay: Int?,
    val lastReviewedEpochDay: Int?,
    val lapses: Int,
)

/** A difficult ayah of a passage; the mark goes away after [clearsAfter] successful repetitions. */
@Entity(
    tableName = "passage_hard",
    primaryKeys = ["passageId", "ayahInSurah"],
    foreignKeys = [
        ForeignKey(
            entity = HifzPassageEntity::class,
            parentColumns = ["id"],
            childColumns = ["passageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PassageHardEntity(
    val passageId: Long,
    val ayahInSurah: Int,
    val clearsAfter: Int,
)

/** The history. There is no foreign key on purpose: it survives the deletion of a passage. */
@Entity(
    tableName = "review_log",
    indices = [Index("epochDay")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val passageId: Long?,
    val surah: Int,
    val startAyah: Int,
    val endAyah: Int,
    val kind: ReviewKind,
    val grade: ReviewGrade,
    val epochDay: Int,
    val timestamp: Long,
)

/** Service values (the mark that the legacy import has run, ...). */
@Entity(tableName = "hifz_meta")
data class HifzMetaEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)

/** A passage with its difficult ayahs, read in one query. */
data class PassageWithHard(
    @Embedded
    val passage: HifzPassageEntity,
    @Relation(parentColumn = "id", entityColumn = "passageId")
    val hard: List<PassageHardEntity>,
)

/** One row of the text metrics: the length of an ayah in characters (code points) and its mushaf page. */
data class AyahMetricRow(
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val chars: Int,
)
