package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SourceEntity::class, TrackEntity::class, SettingsEntity::class],
    version = 3,
    exportSchema = false
)
abstract class TrackAggregatorDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun trackDao(): TrackDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: TrackAggregatorDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN uiDarkMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE settings SET tileStyle = 'LIGHT_STREET', uiDarkMode = 0 WHERE id = 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN isVisible INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN mergeExportItems INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): TrackAggregatorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackAggregatorDatabase::class.java,
                    "trackforge_gpx.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
