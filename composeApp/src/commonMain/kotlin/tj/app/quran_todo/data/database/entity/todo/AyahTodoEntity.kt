package tj.app.quran_todo.data.database.entity.todo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ayah_todo")
data class AyahTodoEntity(
    @PrimaryKey(autoGenerate = false)
    val ayahNumber: Int,
    val surahNumber: Int,
    val status: AyahTodoStatus
)

enum class AyahTodoStatus {
    LEARNED, LEARNING
}
