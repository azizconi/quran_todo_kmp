package tj.app.quran_todo.data.repository

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.data.database.dao.AyahTodoDao
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.domain.repository.AyahTodoRepository

class AyahTodoRepositoryImpl(
    private val dao: AyahTodoDao
) : AyahTodoRepository {
    override suspend fun upsert(entity: AyahTodoEntity) {
        dao.upsert(entity)
    }

    override suspend fun delete(entity: AyahTodoEntity) {
        dao.delete(entity)
    }

    override fun getBySurahNumber(surahNumber: Int): Flow<List<AyahTodoEntity>> =
        dao.getBySurahNumber(surahNumber)

    override suspend fun getBySurahNumberOnce(surahNumber: Int): List<AyahTodoEntity> =
        dao.getBySurahNumberOnce(surahNumber)

    override fun getAll(): Flow<List<AyahTodoEntity>> = dao.getAll()

    override suspend fun deleteBySurahNumber(surahNumber: Int) {
        dao.deleteBySurahNumber(surahNumber)
    }

    override suspend fun deleteByAyahNumber(ayahNumber: Int) {
        dao.deleteByAyahNumber(ayahNumber)
    }
}
