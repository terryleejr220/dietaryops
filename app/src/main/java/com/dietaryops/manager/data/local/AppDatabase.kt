package com.dietaryops.manager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ExpirationRule
import com.dietaryops.manager.data.model.ScanRecord

@Database(
    entities = [CatalogItem::class, ScanRecord::class, ExpirationRule::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogItemDao(): CatalogItemDao
    abstract fun scanRecordDao(): ScanRecordDao
    abstract fun expirationRuleDao(): ExpirationRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "century_villa_delivery_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
