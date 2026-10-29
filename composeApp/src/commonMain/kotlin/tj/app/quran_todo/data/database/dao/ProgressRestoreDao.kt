package tj.app.quran_todo.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity

/**
 * Writes a restored backup into the user's progress tables atomically: the old rows are deleted and the new
 * ones inserted in one transaction, so a failure in the middle leaves the previous progress untouched.
 */
@Dao
interface ProgressRestoreDao {
    @Query("DELETE FROM surah_todo")
    suspend fun clearSurahTodos()

    @Query("DELETE FROM ayah_todo")
    suspend fun clearAyahTodos()

    @Query("DELETE FROM ayah_review")
    suspend fun clearAyahReviews()

    @Query("DELETE FROM ayah_note")
    suspend fun clearAyahNotes()

    @Upsert
    suspend fun upsertSurahTodos(entities: List<SurahTodoEntity>)

    @Upsert
    suspend fun upsertAyahTodos(entities: List<AyahTodoEntity>)

    @Upsert
    suspend fun upsertAyahReviews(entities: List<AyahReviewEntity>)

    @Upsert
    suspend fun upsertAyahNotes(entities: List<AyahNoteEntity>)

    @Transaction
    suspend fun replaceUserProgress(
        surahTodos: List<SurahTodoEntity>,
        ayahTodos: List<AyahTodoEntity>,
        ayahReviews: List<AyahReviewEntity>,
        ayahNotes: List<AyahNoteEntity>,
    ) {
        clearSurahTodos()
        clearAyahTodos()
        clearAyahReviews()
        clearAyahNotes()
        if (surahTodos.isNotEmpty()) upsertSurahTodos(surahTodos)
        if (ayahTodos.isNotEmpty()) upsertAyahTodos(ayahTodos)
        if (ayahReviews.isNotEmpty()) upsertAyahReviews(ayahReviews)
        if (ayahNotes.isNotEmpty()) upsertAyahNotes(ayahNotes)
    }
}
