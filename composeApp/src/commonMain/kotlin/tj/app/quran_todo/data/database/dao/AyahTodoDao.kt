package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity

@Dao
interface AyahTodoDao {

    @Upsert
    suspend fun upsert(entity: AyahTodoEntity)

    @Delete
    suspend fun delete(entity: AyahTodoEntity)

    @Query("SELECT * FROM ayah_todo WHERE surahNumber = :surahNumber")
    fun getBySurahNumber(surahNumber: Int): Flow<List<AyahTodoEntity>>

    @Query("SELECT * FROM ayah_todo WHERE surahNumber = :surahNumber")
    suspend fun getBySurahNumberOnce(surahNumber: Int): List<AyahTodoEntity>

    @Query("SELECT * FROM ayah_todo")
    fun getAll(): Flow<List<AyahTodoEntity>>

    @Query("DELETE FROM ayah_todo WHERE surahNumber = :surahNumber")
    suspend fun deleteBySurahNumber(surahNumber: Int)

    @Query("DELETE FROM ayah_todo WHERE ayahNumber = :ayahNumber")
    suspend fun deleteByAyahNumber(ayahNumber: Int)
}
