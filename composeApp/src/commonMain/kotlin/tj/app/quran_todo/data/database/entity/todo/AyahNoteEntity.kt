package tj.app.quran_todo.data.database.entity.todo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ayah_note")
data class AyahNoteEntity(
    @PrimaryKey(autoGenerate = false)
    val ayahNumber: Int,
    val surahNumber: Int,
    val note: String,
    val updatedAt: Long
)
