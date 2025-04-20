package tj.app.quran_todo.domain.repository

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs

interface QuranRemoteRepository {
    fun getCompleteQuran(withLocalAction: Boolean): Flow<Resource<List<SurahWithAyahs>>>
}