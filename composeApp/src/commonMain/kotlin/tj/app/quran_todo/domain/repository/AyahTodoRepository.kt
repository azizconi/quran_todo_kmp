package tj.app.quran_todo.domain.repository

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity

interface AyahTodoRepository {
    suspend fun upsert(entity: AyahTodoEntity)
    suspend fun delete(entity: AyahTodoEntity)
    fun getBySurahNumber(surahNumber: Int): Flow<List<AyahTodoEntity>>
    suspend fun getBySurahNumberOnce(surahNumber: Int): List<AyahTodoEntity>
    fun getAll(): Flow<List<AyahTodoEntity>>
    suspend fun deleteBySurahNumber(surahNumber: Int)
    suspend fun deleteByAyahNumber(ayahNumber: Int)
}
