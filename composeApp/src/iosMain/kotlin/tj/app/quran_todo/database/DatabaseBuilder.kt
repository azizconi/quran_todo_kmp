package tj.app.quran_todo.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.NativeSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

import tj.app.quran_todo.data.database.QuranTodoDatabase
import tj.app.quran_todo.data.database.DatabaseMigrations


fun getDatabaseBuilder(): RoomDatabase.Builder<QuranTodoDatabase> {
    val dbFilePath = documentDirectory() + "/quran_todo.db"
    val driver = NativeSQLiteDriver()
    return Room.databaseBuilder<QuranTodoDatabase>(
        name = dbFilePath
    ).setDriver(driver)
        .addMigrations(
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_6_7,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_8_9
        )
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
