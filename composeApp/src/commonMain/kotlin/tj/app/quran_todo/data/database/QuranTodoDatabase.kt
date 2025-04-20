package tj.app.quran_todo.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import tj.app.quran_todo.data.database.dao.QuranDao
import tj.app.quran_todo.data.database.dao.SurahTodoDao
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.type_converter.BaseTypeConverter


@Database(
    entities = [
        SurahTodoEntity::class,
        EditionEntity::class, SurahEntity::class, AyahEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(SajdaConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class QuranTodoDatabase : RoomDatabase() {
    abstract fun getSurahTodoDao(): SurahTodoDao
    abstract fun getQuranDao(): QuranDao

}

class SajdaConverter : BaseTypeConverter<Sajda>(Sajda.serializer())

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<QuranTodoDatabase> {
    override fun initialize(): QuranTodoDatabase
}

