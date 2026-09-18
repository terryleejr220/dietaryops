package com.dietaryops.manager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dietaryops.manager.data.model.ScanRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanRecordDao {
    @Query("SELECT * FROM scan_records ORDER BY scanTimestamp DESC")
    fun getAllScanRecords(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE syncedToSheets = 0")
    suspend fun getUnsyncedScanRecords(): List<ScanRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRecord(record: ScanRecord)

    @Query("UPDATE scan_records SET syncedToSheets = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE scan_records SET printed = :printed WHERE id = :id")
    suspend fun markPrinted(id: String, printed: Boolean)

    @Query("UPDATE scan_records SET onHandAmount = :onHandAmount, syncedToSheets = 0 WHERE id = :id")
    suspend fun updateOnHandAmount(id: String, onHandAmount: Double)

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun deleteRecord(id: String)

    @Query("DELETE FROM scan_records")
    suspend fun clearAll()
}
