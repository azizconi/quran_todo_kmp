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

    /** Every stored row of one edition in the order of the Quran (the search reads them all). */
    @Query("SELECT * FROM ayah_translation WHERE languageCode = :languageCode ORDER BY ayahNumber")
    suspend fun getAllByLanguage(languageCode: String): List<AyahTranslationEntity>

    /** How many ayahs are stored of every edition: the editions with 6236 are downloaded whole. */
    @Query("SELECT languageCode AS editionKey, COUNT(*) AS total FROM ayah_translation GROUP BY languageCode")
    suspend fun countsByEdition(): List<EditionCount>

    @Query("DELETE FROM ayah_translation WHERE languageCode = :languageCode")
    suspend fun deleteByLanguage(languageCode: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AyahTranslationEntity>)
}

/** The result of [AyahTranslationDao.countsByEdition]. */
data class EditionCount(val editionKey: String, val total: Int)
