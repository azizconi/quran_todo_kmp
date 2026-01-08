package tj.app.quran_todo.data.database.entity.quran

import androidx.room.Entity

@Entity(
    tableName = "chapter_name",
    primaryKeys = ["languageCode", "surahNumber"]
)
data class ChapterNameEntity(
    val languageCode: String,
    val surahNumber: Int,
    val arabic: String,
    val transliteration: String,
    val translated: String,
)
