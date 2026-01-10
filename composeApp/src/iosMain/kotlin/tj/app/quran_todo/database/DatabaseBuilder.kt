package tj.app.quran_todo.database

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import tj.app.quran_todo.data.database.QuranTodoDatabase

fun getDatabaseBuilder(): RoomDatabase.Builder<QuranTodoDatabase> {
    val dbFilePath = databasePath()
    logDatabaseFile("before build", dbFilePath)
    return Room.databaseBuilder<QuranTodoDatabase>(
        name = dbFilePath,
    )
}

fun databasePath(): String = applicationSupportDirectory() + "/quran_todo.db"

fun logDatabaseFile(stage: String, path: String) {
    val exists = NSFileManager.defaultManager.fileExistsAtPath(path)
    NSLog("Room DB (%s): %s exists=%s", stage, path, exists.toString())
}

@OptIn(ExperimentalForeignApi::class)
private fun applicationSupportDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) as NSURL?
    val path = requireNotNull(url?.path)
    NSFileManager.defaultManager.createDirectoryAtPath(
        path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    return path
}
