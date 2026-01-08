package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tj.app.quran_todo.data.database.entity.quran.ChapterNameEntity

@Dao
interface ChapterNameDao {
    @Query("SELECT * FROM chapter_name WHERE languageCode = :languageCode")
    suspend fun getByLanguage(languageCode: String): List<ChapterNameEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChapterNameEntity>)
}
