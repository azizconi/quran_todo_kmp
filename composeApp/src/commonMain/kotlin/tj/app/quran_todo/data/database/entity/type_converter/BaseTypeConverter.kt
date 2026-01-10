package tj.app.quran_todo.data.database.entity.type_converter

import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

abstract class BaseTypeConverter<T>(private val serializer: KSerializer<T>) {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun from(value: T): String =
        json.encodeToString(serializer, value)

    @TypeConverter
    fun to(value: String): T =
        json.decodeFromString(serializer, value)
}
