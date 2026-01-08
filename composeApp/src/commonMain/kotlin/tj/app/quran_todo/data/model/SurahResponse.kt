package tj.app.quran_todo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SurahResponse(
    val data: Surah
)
