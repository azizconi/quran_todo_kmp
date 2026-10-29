package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import tj.app.quran_todo.data.database.entity.hifz.AyahMetricRow
import tj.app.quran_todo.data.database.entity.hifz.HifzMetaEntity
import tj.app.quran_todo.data.database.entity.hifz.HifzPassageEntity
import tj.app.quran_todo.data.database.entity.hifz.HifzPlanEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageHardEntity
import tj.app.quran_todo.data.database.entity.hifz.PassageWithHard
import tj.app.quran_todo.data.database.entity.hifz.ReviewLogEntity
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity

/**
 * Plain reads and writes of the hifz tables, plus the few reads and writes of the old progress tables that the
 * repository needs in the same transaction (the mirror and the one-time import). The logic is NOT here: the
 * repository reads, computes with pure functions and writes inside one Room transaction
 * (`useWriterConnection { immediateTransaction { ... } }`).
 */
@Dao
interface HifzDao {
    // Plan
    @Query("SELECT * FROM hifz_plan WHERE id = 1")
    suspend fun getPlan(): HifzPlanEntity?

    @Upsert
    suspend fun upsertPlan(plan: HifzPlanEntity)

    @Query("DELETE FROM hifz_plan")
    suspend fun deletePlan()

    // Passages
    @Transaction
    @Query("SELECT * FROM hifz_passage")
    suspend fun getPassages(): List<PassageWithHard>

    @Insert
    suspend fun insertPassage(passage: HifzPassageEntity): Long

    @Update
    suspend fun updatePassage(passage: HifzPassageEntity)

    @Query("DELETE FROM hifz_passage WHERE id IN (:ids)")
    suspend fun deletePassages(ids: List<Long>)

    @Query("DELETE FROM passage_hard WHERE passageId = :passageId")
    suspend fun deleteHardOf(passageId: Long)

    @Query("DELETE FROM passage_hard WHERE passageId IN (:ids)")
    suspend fun deleteHardOfAll(ids: List<Long>)

    @Insert
    suspend fun insertHard(rows: List<PassageHardEntity>)

    // Review log
    @Insert
    suspend fun insertLog(entry: ReviewLogEntity): Long

    @Query("SELECT * FROM review_log WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY id")
    suspend fun getLog(fromDay: Int, toDay: Int): List<ReviewLogEntity>

    @Query("SELECT * FROM review_log WHERE kind = 'NEW' AND epochDay >= :sinceDay ORDER BY id")
    suspend fun getNewLogSince(sinceDay: Int): List<ReviewLogEntity>

    /** The days (newest first) up to [toDay] with at least one record. */
    @Query("SELECT DISTINCT epochDay FROM review_log WHERE epochDay <= :toDay ORDER BY epochDay DESC")
    suspend fun getLogDays(toDay: Int): List<Int>

    // Meta
    @Query("SELECT value FROM hifz_meta WHERE `key` = :key")
    suspend fun getMeta(key: String): String?

    @Upsert
    suspend fun upsertMeta(meta: HifzMetaEntity)

    // Text metrics: one projection of the Arabic text (the table `ayahs` holds only the bundled edition)
    @Query(
        "SELECT surahNumber AS surah, numberInSurah AS ayah, page AS page, LENGTH(text) AS chars " +
            "FROM ayahs ORDER BY number",
    )
    suspend fun getMetricRows(): List<AyahMetricRow>

    // The old progress tables: reads for the import, reads and writes for the mirror
    @Query("SELECT * FROM surah_todo")
    suspend fun getAllSurahTodos(): List<SurahTodoEntity>

    @Query("SELECT * FROM ayah_todo")
    suspend fun getAllAyahTodos(): List<AyahTodoEntity>

    @Query("SELECT * FROM ayah_review")
    suspend fun getAllAyahReviews(): List<AyahReviewEntity>

    @Query("SELECT * FROM ayah_todo WHERE surahNumber = :surah")
    suspend fun getAyahTodosOf(surah: Int): List<AyahTodoEntity>

    @Query("SELECT * FROM surah_todo WHERE surahNumber = :surah")
    suspend fun getSurahTodo(surah: Int): SurahTodoEntity?

    @Upsert
    suspend fun upsertAyahTodos(rows: List<AyahTodoEntity>)

    @Query("DELETE FROM ayah_todo WHERE ayahNumber IN (:numbers)")
    suspend fun deleteAyahTodos(numbers: List<Int>)

    @Upsert
    suspend fun upsertSurahTodo(row: SurahTodoEntity)

    @Query("DELETE FROM surah_todo WHERE surahNumber = :surah")
    suspend fun deleteSurahTodo(surah: Int)
}
