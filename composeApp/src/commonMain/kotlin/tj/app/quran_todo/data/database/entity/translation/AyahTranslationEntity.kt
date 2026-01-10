package tj.app.quran_todo.data.database.entity.translation

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "ayah_translation",
    primaryKeys = ["languageCode", "ayahNumber"],
    indices = [Index("surahNumber"), Index("languageCode")]
)
data class AyahTranslationEntity(
    val ayahNumber: Int,
    val surahNumber: Int,
    val languageCode: String,
    val text: String
)
