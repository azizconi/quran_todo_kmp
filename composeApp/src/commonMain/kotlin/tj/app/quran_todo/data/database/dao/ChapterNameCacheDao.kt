package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity

@Dao
interface ChapterNameCacheDao {
    @Query("SELECT * FROM chapter_name_cache WHERE languageCode = :languageCode LIMIT 1")
    suspend fun get(languageCode: String): ChapterNameCacheEntity?

    @Upsert
    suspend fun upsert(entity: ChapterNameCacheEntity)
}
