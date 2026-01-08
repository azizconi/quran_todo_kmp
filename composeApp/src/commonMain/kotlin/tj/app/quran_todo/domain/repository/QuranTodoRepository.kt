package tj.app.quran_todo.domain.repository

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity

interface QuranTodoRepository {
    suspend fun upsert(entity: SurahTodoEntity)
    suspend fun delete(entity: SurahTodoEntity)
    suspend fun deleteBySurahNumber(surahNumber: Int)
    fun getAll(): Flow<List<SurahTodoEntity>>
}
