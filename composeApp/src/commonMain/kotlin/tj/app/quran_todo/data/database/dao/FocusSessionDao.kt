package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.entity.todo.FocusSessionEntity

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_session ORDER BY startedAt DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FocusSessionEntity)

    @Query("DELETE FROM focus_session")
    suspend fun clear()
}
