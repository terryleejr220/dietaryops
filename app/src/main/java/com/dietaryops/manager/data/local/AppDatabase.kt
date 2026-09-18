package com.dietaryops.manager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ExpirationRule
import com.dietaryops.manager.data.model.ScanRecord

@Database(
    entities = [CatalogItem::class, ScanRecord::class, ExpirationRule::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogItemDao(): CatalogItemDao
    abstract fun scanRecordDao(): ScanRecordDao
    abstract fun expirationRuleDao(): ExpirationRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration 3 → 4: adds syncedToFirestore and syncRetryCount columns
         * introduced by the WorkManager background-sync implementation.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE scan_records ADD COLUMN syncedToFirestore INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE scan_records ADD COLUMN syncRetryCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = if (context.getDatabasePath("century_villa_delivery_db").exists() &&
                    !context.getDatabasePath("dietaryops_manager_db").exists()
                ) {
                    "century_villa_delivery_db"
                } else {
                    "dietaryops_manager_db"
                }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
