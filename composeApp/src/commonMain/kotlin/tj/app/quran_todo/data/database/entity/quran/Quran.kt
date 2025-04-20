package tj.app.quran_todo.data.database.entity.quran

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

// 1. Сущность для Edition
@Entity(tableName = "editions")
data class EditionEntity(
    @PrimaryKey val identifier: String,
    val englishName: String,
    val format: String,
    val language: String,
    val name: String,
    val type: String
)

// 2. Сущность для Surah
@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey val number: Int,
    val name: String,
    val englishName: String,
    val englishNameTranslation: String,
    val revelationType: String,
    // при желании: связь на Edition
    val editionId: String
)

// 3. Сущность для Ayah с FK на Surah
@Entity(
    tableName = "ayahs",
    foreignKeys = [
        ForeignKey(
            entity = SurahEntity::class,
            parentColumns = ["number"],
            childColumns = ["surahNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [ Index("surahNumber") ]
)
data class AyahEntity(
    @PrimaryKey val number: Int,
    val numberInSurah: Int,
    val juz: Int,
    val hizbQuarter: Int,
    val manzil: Int,
    val page: Int,
    val ruku: Int,
    val sajda: Sajda?,
    val text: String,
    val surahNumber: Int
)

// 4. DTO для выборки Surah + её Ayahs
data class SurahWithAyahs(
    @Embedded val surah: SurahEntity,
    @Relation(
        parentColumn = "number",
        entityColumn = "surahNumber"
    )
    val ayahs: List<AyahEntity>
)

@Serializable
data class Sajda(
    val id: Int,
    val recommended: Boolean,
    val obligatory: Boolean,
)