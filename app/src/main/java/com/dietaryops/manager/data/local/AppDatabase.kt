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
    version = 7,
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
         * Migration 3 → 4: adds syncedToFirestore and syncRetryCount columns.
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

        /**
         * Migration 4 → 5: adds casePackQty and labelNickname columns.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE catalog_items ADD COLUMN casePackQty INTEGER NOT NULL DEFAULT 1"
                )
                database.execSQL(
                    "ALTER TABLE catalog_items ADD COLUMN labelNickname TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * Migration 5 → 6: adds vendorName column.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE catalog_items ADD COLUMN vendorName TEXT NOT NULL DEFAULT 'Sysco'"
                )
            }
        }

        /**
         * Migration 6 → 7: adds linkedInventoryItem column.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE catalog_items ADD COLUMN linkedInventoryItem TEXT NOT NULL DEFAULT ''"
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
