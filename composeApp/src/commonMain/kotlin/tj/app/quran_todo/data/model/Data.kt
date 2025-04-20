package tj.app.quran_todo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Data(
    val edition: Edition,
    val surahs: List<Surah>
)