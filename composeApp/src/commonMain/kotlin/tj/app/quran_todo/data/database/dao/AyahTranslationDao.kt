package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity

@Dao
interface AyahTranslationDao {
    @Query(
        "SELECT * FROM ayah_translation WHERE surahNumber = :surahNumber AND languageCode = :languageCode"
    )
    suspend fun getBySurahAndLanguage(
        surahNumber: Int,
        languageCode: String
    ): List<AyahTranslationEntity>

    @Query("SELECT COUNT(*) FROM ayah_translation WHERE languageCode = :languageCode")
    suspend fun countByLanguage(languageCode: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AyahTranslationEntity>)
}
