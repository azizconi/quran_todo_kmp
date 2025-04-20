package tj.app.quran_todo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Surah(
    val ayahs: List<Ayah>,
    val englishName: String,
    val englishNameTranslation: String,
    val name: String,
    val number: Int,
    val revelationType: String
)