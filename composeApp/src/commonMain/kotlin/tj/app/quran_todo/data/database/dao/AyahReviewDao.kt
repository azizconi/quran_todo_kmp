package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity

@Dao
interface AyahReviewDao {
    @Query("SELECT * FROM ayah_review WHERE surahNumber = :surahNumber")
    fun getBySurahNumber(surahNumber: Int): Flow<List<AyahReviewEntity>>

    @Query("SELECT * FROM ayah_review WHERE ayahNumber = :ayahNumber LIMIT 1")
    suspend fun getByAyahNumber(ayahNumber: Int): AyahReviewEntity?

    @Query("SELECT * FROM ayah_review WHERE nextReviewAt <= :now")
    suspend fun getDue(now: Long): List<AyahReviewEntity>

    @Upsert
    suspend fun upsert(entity: AyahReviewEntity)

    @Delete
    suspend fun delete(entity: AyahReviewEntity)

    @Query("DELETE FROM ayah_review WHERE ayahNumber = :ayahNumber")
    suspend fun deleteByAyahNumber(ayahNumber: Int)

    @Query("DELETE FROM ayah_review WHERE surahNumber = :surahNumber")
    suspend fun deleteBySurahNumber(surahNumber: Int)
}
