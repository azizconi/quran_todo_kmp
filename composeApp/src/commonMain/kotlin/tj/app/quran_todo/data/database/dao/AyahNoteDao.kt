package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity

@Dao
interface AyahNoteDao {
    @Query("SELECT * FROM ayah_note WHERE surahNumber = :surahNumber")
    fun getBySurahNumber(surahNumber: Int): Flow<List<AyahNoteEntity>>

    @Query("SELECT * FROM ayah_note WHERE ayahNumber = :ayahNumber LIMIT 1")
    suspend fun getByAyahNumber(ayahNumber: Int): AyahNoteEntity?

    @Upsert
    suspend fun upsert(entity: AyahNoteEntity)

    @Delete
    suspend fun delete(entity: AyahNoteEntity)
}
