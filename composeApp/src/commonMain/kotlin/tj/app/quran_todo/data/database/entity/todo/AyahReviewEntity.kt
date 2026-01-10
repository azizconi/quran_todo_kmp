package tj.app.quran_todo.data.database.entity.todo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ayah_review")
data class AyahReviewEntity(
    @PrimaryKey(autoGenerate = false)
    val ayahNumber: Int,
    val surahNumber: Int,
    val nextReviewAt: Long,
    val intervalIndex: Int,
    val lastReviewedAt: Long
)
