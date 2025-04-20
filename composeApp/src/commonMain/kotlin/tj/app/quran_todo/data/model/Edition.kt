package tj.app.quran_todo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Edition(
    val englishName: String,
    val format: String,
    val identifier: String,
    val language: String,
    val name: String,
    val type: String
)