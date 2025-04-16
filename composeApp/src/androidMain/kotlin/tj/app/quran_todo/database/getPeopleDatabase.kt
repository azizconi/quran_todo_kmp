package tj.app.quran_todo.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import tj.app.quran_todo.data.database.QuranTodoDatabase

fun getQuranTodoDatabase(context: Context): QuranTodoDatabase {
    val dbFile = context.getDatabasePath("quran_todo.db")
    return Room.databaseBuilder<QuranTodoDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}