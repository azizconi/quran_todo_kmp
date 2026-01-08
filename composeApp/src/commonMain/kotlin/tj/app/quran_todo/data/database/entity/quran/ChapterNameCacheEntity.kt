package tj.app.quran_todo.data.database.entity.quran

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapter_name_cache")
data class ChapterNameCacheEntity(
    @PrimaryKey
    val languageCode: String,
    val lastUpdated: Long
)
