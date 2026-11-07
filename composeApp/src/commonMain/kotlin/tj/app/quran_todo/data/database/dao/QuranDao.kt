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

    @Query("SELECT identifier FROM editions")
    suspend fun getEditionIds(): List<String>

    @Query("DELETE FROM ayahs")
    suspend fun clearAyahs()

    @Query("DELETE FROM surahs")
    suspend fun clearSurahs()

    @Query("DELETE FROM editions")
    suspend fun clearEditions()

    @Query("SELECT COUNT(*) FROM ayahs")
    suspend fun countAyahs(): Int

    @Query("SELECT COUNT(*) FROM surahs")
    suspend fun countSurahs(): Int

    /** Text of the very first ayah (1:1); null when there is none. Used to spot the old byte order mark. */
    @Query("SELECT text FROM ayahs WHERE number = 1")
    suspend fun getFirstAyahText(): String?

    @Transaction
    suspend fun clearAllData() {
        clearAyahs()
        clearSurahs()
        clearEditions()
    }

    /**
     * Replaces the whole Quran text in ONE transaction: either the new text is in the database completely, or
     * nothing changed. The tables are emptied first (`ayahs` before `surahs`, and a surah delete would cascade
     * to its ayahs anyway), because inserting a surah with REPLACE would cascade-delete its ayahs.
     * The user tables (`surah_todo`, `ayah_todo`, `ayah_note`, `ayah_review`) have no foreign keys to these
     * tables and are not touched.
     */
    @Transaction
    suspend fun replaceQuran(
        edition: EditionEntity,
        surahs: List<SurahEntity>,
        ayahs: List<AyahEntity>,
    ) {
        clearAyahs()
        clearSurahs()
        clearEditions()
        insertEdition(edition)
        insertSurahs(surahs)
        insertAyahs(ayahs)
    }
}
