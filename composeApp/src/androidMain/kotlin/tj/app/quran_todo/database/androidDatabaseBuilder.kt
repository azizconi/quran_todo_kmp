package tj.app.quran_todo.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.database.DatabaseMigrations


fun androidDatabaseBuilder(ctx: Context): RoomDatabase.Builder<QuranTodoDatabase> {
    val appContext = ctx.applicationContext
    val dbFile = appContext.getDatabasePath("quran_todo.db")
    return Room.databaseBuilder<QuranTodoDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    ).addMigrations(DatabaseMigrations.MIGRATION_5_6)
}
