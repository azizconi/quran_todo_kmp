package tj.app.quran_todo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuranComChaptersResponse(
    val chapters: List<QuranComChapter>
)

@Serializable
data class QuranComChapter(
    val id: Int,
    @SerialName("name_simple")
    val nameSimple: String,
    @SerialName("name_arabic")
    val nameArabic: String,
    @SerialName("translated_name")
    val translatedName: QuranComTranslatedName,
)

@Serializable
data class QuranComTranslatedName(
    val name: String
)
