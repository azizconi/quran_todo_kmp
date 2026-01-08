package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity

@Dao
interface ChapterNameCacheDao {
    @Query("SELECT * FROM chapter_name_cache WHERE languageCode = :languageCode")
    suspend fun get(languageCode: String): ChapterNameCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChapterNameCacheEntity)
}
