package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs

@Dao
interface QuranDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdition(edition: EditionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurahs(surahs: List<SurahEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahs(ayahs: List<AyahEntity>)

    @Transaction
    @Query("SELECT * FROM surahs WHERE editionId = :editionId")
    suspend fun getSurahsWithAyahs(editionId: String): List<SurahWithAyahs>

    // --- методы очистки отдельных таблиц ---
    @Query("DELETE FROM ayahs")
    suspend fun clearAyahs()

    @Query("DELETE FROM surahs")
    suspend fun clearSurahs()

    @Query("DELETE FROM editions")
    suspend fun clearEditions()

    // --- объединённая очистка всех трёх таблиц ---
    @Transaction
    suspend fun clearAllData() {
        clearAyahs()
        clearSurahs()
        clearEditions()
    }
}