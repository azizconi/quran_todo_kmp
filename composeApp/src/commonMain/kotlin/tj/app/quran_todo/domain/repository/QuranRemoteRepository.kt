package tj.app.quran_todo.domain.repository

import kotlinx.coroutines.flow.Flow
import tj.app.quran_todo.common.utils.Resource
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs

interface QuranRemoteRepository {
    fun getCompleteQuran(withLocalAction: Boolean): Flow<Resource<List<SurahWithAyahs>>>

    /**
     * Makes sure the bundled Quran text is in the database (writes it when it is missing, incomplete or damaged; offline).
     * [force] writes it again even when it looks complete. Used by code that reads the text straight from the tables,
     * e.g. the hifz scheduler, which needs the length and the page of every ayah.
     */
    suspend fun ensureQuranInstalled(force: Boolean = false)
}