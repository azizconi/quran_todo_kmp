package tj.app.quran_todo.domain.model

import kotlinx.serialization.Serializable
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoStatus

@Serializable
data class SurahModel(
    val surahNumber: Int,
    val alphabetOrder: Int,
    val name: String,
    val ayats: Int,
    val revelationOrder: Int,
    val revelationPlace: String
) {
    fun toEntity(status: SurahTodoStatus? = null): SurahTodoEntity {
        return SurahTodoEntity(
            surahNumber = surahNumber,
            status = status ?: SurahTodoStatus.LEARNED
        )
    }
}

@Serializable
data class SurahList(
    val surahs: List<SurahModel>
)
