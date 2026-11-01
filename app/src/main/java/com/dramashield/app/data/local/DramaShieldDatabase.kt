package com.dramashield.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [InterceptLogEntity::class],
    version = 2,
    exportSchema = true
)
abstract class DramaShieldDatabase : RoomDatabase() {
    abstract fun interceptLogDao(): InterceptLogDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE intercept_log ADD COLUMN detail TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
