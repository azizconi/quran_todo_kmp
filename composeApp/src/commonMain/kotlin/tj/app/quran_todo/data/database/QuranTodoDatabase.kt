package tj.app.quran_todo.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import tj.app.quran_todo.data.database.dao.AyahNoteDao
import tj.app.quran_todo.data.database.dao.AyahReviewDao
import tj.app.quran_todo.data.database.dao.AyahTodoDao
import tj.app.quran_todo.data.database.dao.AyahTranslationDao
import tj.app.quran_todo.data.database.dao.ChapterNameCacheDao
import tj.app.quran_todo.data.database.dao.ChapterNameDao
import tj.app.quran_todo.data.database.dao.FocusSessionDao
import tj.app.quran_todo.data.database.dao.QuranDao
import tj.app.quran_todo.data.database.dao.SurahTodoDao
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameCacheEntity
import tj.app.quran_todo.data.database.entity.quran.ChapterNameEntity
import tj.app.quran_todo.data.database.entity.quran.EditionEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.todo.AyahNoteEntity
import tj.app.quran_todo.data.database.entity.todo.AyahReviewEntity
import tj.app.quran_todo.data.database.entity.todo.AyahTodoEntity
import tj.app.quran_todo.data.database.entity.todo.FocusSessionEntity
import tj.app.quran_todo.data.database.entity.todo.SurahTodoEntity
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity

@Database(
    entities = [
        SurahTodoEntity::class,
        AyahTodoEntity::class,
        AyahNoteEntity::class,
        AyahReviewEntity::class,
        FocusSessionEntity::class,
        ChapterNameEntity::class,
        ChapterNameCacheEntity::class,
        AyahTranslationEntity::class,
        EditionEntity::class,
        SurahEntity::class,
        AyahEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(SajdaConverter::class)
@ConstructedBy(QuranTodoDatabaseConstructor::class)
abstract class QuranTodoDatabase : RoomDatabase() {
    abstract fun getSurahTodoDao(): SurahTodoDao
    abstract fun getAyahTodoDao(): AyahTodoDao
    abstract fun getAyahNoteDao(): AyahNoteDao
    abstract fun getAyahReviewDao(): AyahReviewDao
    abstract fun getFocusSessionDao(): FocusSessionDao
    abstract fun getChapterNameDao(): ChapterNameDao
    abstract fun getChapterNameCacheDao(): ChapterNameCacheDao
    abstract fun getAyahTranslationDao(): AyahTranslationDao
    abstract fun getQuranDao(): QuranDao
}

@Suppress("KotlinNoActualForExpect")
expect object QuranTodoDatabaseConstructor : RoomDatabaseConstructor<QuranTodoDatabase> {
    override fun initialize(): QuranTodoDatabase
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<QuranTodoDatabase>
): QuranTodoDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
