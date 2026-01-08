package tj.app.quran_todo.data.repository

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.dao.SurahTodoDao
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.domain.repository.QuranTodoRepository

class QuranTodoRepositoryImpl(
    private val dao: SurahTodoDao
): QuranTodoRepository {
    override suspend fun upsert(entity: SurahTodoEntity) {
        dao.upsert(entity)
    }

    override suspend fun delete(entity: SurahTodoEntity) {
        dao.delete(entity)
    }

    override suspend fun deleteBySurahNumber(surahNumber: Int) {
        dao.deleteBySurahNumber(surahNumber)
    }

    override fun getAll(): Flow<List<SurahTodoEntity>> = dao.getAllSurahTodo()
}
