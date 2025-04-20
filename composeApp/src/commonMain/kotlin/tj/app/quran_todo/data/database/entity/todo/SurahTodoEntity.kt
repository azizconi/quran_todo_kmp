package tj.app.quran_todo.data.database.entity.todo

import androidx.room.Entity
import androidx.room.PrimaryKey
import tj.app.quran_todo.domain.model.SurahModel

@Entity(tableName = "surah_todo")
data class SurahTodoEntity(
    @PrimaryKey(autoGenerate = false)
    val surahNumber: Int,
    val status: SurahTodoStatus
)

enum class SurahTodoStatus {
    LEARNED, LEARNING
}
