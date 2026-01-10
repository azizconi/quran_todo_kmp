package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity

@Dao
interface SurahTodoDao {
    @Upsert
    suspend fun upsert(entity: SurahTodoEntity)

    @Delete
    suspend fun delete(entity: SurahTodoEntity)

    @Query("DELETE FROM surah_todo WHERE surahNumber = :surahNumber")
    suspend fun deleteBySurahNumber(surahNumber: Int)

    @Query("SELECT * FROM surah_todo")
    fun getAllSurahTodo(): Flow<List<SurahTodoEntity>>
}
