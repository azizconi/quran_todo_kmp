package tj.app.quran_todo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class QuranCompleteResponse(
    val code: Int,
    val `data`: Data,
    val status: String
)