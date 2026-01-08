package tj.app.quran_todo.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ayah_todo (
                    ayahNumber INTEGER NOT NULL,
                    surahNumber INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    PRIMARY KEY(ayahNumber)
                )
                """.trimIndent()
            )
        }
    }
}
