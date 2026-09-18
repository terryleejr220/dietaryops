package com.dietaryops.manager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val syscoUpc: String,
    val itemName: String,
    val scanTimestamp: Long = System.currentTimeMillis(),
    val deliveryDate: String,
    val useByDate: String,
    val category: String = "General",
    val shelfLifeDays: Int = 365,
    val unit: String = "EA",
    val onHandAmount: Double = 1.0,
    val printed: Boolean = false,
    val syncedToSheets: Boolean = false,
    val isAudit: Boolean = false,
    val itemNumber: String = "",
    val receivedBy: String = ""
) {
    val effectiveOnHandAmount: Double
        get() = onHandAmount
}
