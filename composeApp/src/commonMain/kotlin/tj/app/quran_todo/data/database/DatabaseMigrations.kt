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

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chapter_name (
                    languageCode TEXT NOT NULL,
                    surahNumber INTEGER NOT NULL,
                    arabic TEXT NOT NULL,
                    transliteration TEXT NOT NULL,
                    translated TEXT NOT NULL,
                    PRIMARY KEY(languageCode, surahNumber)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chapter_name_cache (
                    languageCode TEXT NOT NULL,
                    lastUpdated INTEGER NOT NULL,
                    PRIMARY KEY(languageCode)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE ayah_todo ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ayah_note (
                    ayahNumber INTEGER NOT NULL,
                    surahNumber INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(ayahNumber)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ayah_review (
                    ayahNumber INTEGER NOT NULL,
                    surahNumber INTEGER NOT NULL,
                    nextReviewAt INTEGER NOT NULL,
                    intervalIndex INTEGER NOT NULL,
                    lastReviewedAt INTEGER NOT NULL,
                    PRIMARY KEY(ayahNumber)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS focus_session (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    durationMinutes INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}
