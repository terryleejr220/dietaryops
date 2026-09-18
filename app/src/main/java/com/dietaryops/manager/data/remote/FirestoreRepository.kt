package com.dietaryops.manager.data.remote

import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ScanRecord
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val lazyFirestore: Lazy<FirebaseFirestore?> = lazy {
        try {
            val instance = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder().build()
                )
                .build()
            instance.firestoreSettings = settings
            instance
        } catch (_: Exception) {
            null
        }
    }
) {
    val firestore: FirebaseFirestore?
        get() = lazyFirestore.value

    private val catalogCollection
        get() = firestore?.collection("catalog")

    private val scanLogsCollection
        get() = firestore?.collection("scan_logs")

    suspend fun saveCatalogItem(item: CatalogItem): Result<Unit> {
        val col = catalogCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            val docId = item.syscoUpc.ifBlank { item.name }
            val data = mapOf(
                "syscoUpc" to item.syscoUpc,
                "name" to item.name,
                "category" to item.category,
                "defaultShelfLifeDays" to item.defaultShelfLifeDays,
                "unit" to item.unit,
                "lastOnHandAmount" to item.lastOnHandAmount,
                "syscoItemNumber" to item.syscoItemNumber,
                "piazzaItemNumber" to item.piazzaItemNumber,
                "vendorItemNumber" to item.vendorItemNumber,
                "vendorSku" to item.vendorSku,
                "alternateBarcodes" to item.alternateBarcodes
            )
            col.document(docId).set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addScanRecord(record: ScanRecord): Result<Unit> {
        val col = scanLogsCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            val docId = record.id
            val data = mapOf(
                "id" to record.id,
                "syscoUpc" to record.syscoUpc,
                "itemName" to record.itemName,
                "deliveryDate" to record.deliveryDate,
                "useByDate" to record.useByDate,
                "category" to record.category,
                "shelfLifeDays" to record.shelfLifeDays,
                "unit" to record.unit,
                "onHandAmount" to record.onHandAmount,
                "scanTimestamp" to record.scanTimestamp,
                "printed" to record.printed,
                "syncedToSheets" to record.syncedToSheets,
                "isAudit" to record.isAudit
            )
            col.document(docId).set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateScanRecordOnHandAmount(recordId: String, onHandAmount: Double): Result<Unit> {
        val col = scanLogsCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            col.document(recordId).update("onHandAmount", onHandAmount).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markRecordPrinted(recordId: String): Result<Unit> {
        val col = scanLogsCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            col.document(recordId).update("printed", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteScanRecord(recordId: String): Result<Unit> {
        val col = scanLogsCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            col.document(recordId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCatalogItem(upc: String): Result<Unit> {
        val col = catalogCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            col.document(upc).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCatalog(): Result<List<CatalogItem>> {
        val col = catalogCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            val snapshot = col.get().await()
            val items = snapshot.documents.mapNotNull { doc ->
                val upc = doc.getString("syscoUpc") ?: doc.id
                val name = doc.getString("name") ?: return@mapNotNull null
                CatalogItem(
                    syscoUpc = upc,
                    name = name,
                    category = doc.getString("category") ?: "General",
                    defaultShelfLifeDays = (doc.getLong("defaultShelfLifeDays") ?: 7L).toInt(),
                    unit = doc.getString("unit") ?: "EA",
                    lastOnHandAmount = doc.getDouble("lastOnHandAmount") ?: 1.0,
                    syscoItemNumber = doc.getString("syscoItemNumber") ?: "",
                    piazzaItemNumber = doc.getString("piazzaItemNumber") ?: "",
                    vendorItemNumber = doc.getString("vendorItemNumber") ?: "",
                    vendorSku = doc.getString("vendorSku") ?: "",
                    alternateBarcodes = doc.getString("alternateBarcodes") ?: ""
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchScanRecords(): Result<List<ScanRecord>> {
        val col = scanLogsCollection ?: return Result.failure(Exception("Firestore instance unavailable"))
        return try {
            val snapshot = col.get().await()
            val records = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val upc = doc.getString("syscoUpc") ?: ""
                val name = doc.getString("itemName") ?: ""
                val deliveryDate = doc.getString("deliveryDate") ?: ""
                val useByDate = doc.getString("useByDate") ?: ""
                ScanRecord(
                    id = id,
                    syscoUpc = upc,
                    itemName = name,
                    deliveryDate = deliveryDate,
                    useByDate = useByDate,
                    category = doc.getString("category") ?: "General",
                    shelfLifeDays = (doc.getLong("shelfLifeDays") ?: 7L).toInt(),
                    unit = doc.getString("unit") ?: "EA",
                    onHandAmount = doc.getDouble("onHandAmount") ?: 1.0,
                    scanTimestamp = doc.getLong("scanTimestamp") ?: System.currentTimeMillis(),
                    printed = doc.getBoolean("printed") ?: false,
                    syncedToSheets = doc.getBoolean("syncedToSheets") ?: false,
                    isAudit = doc.getBoolean("isAudit") ?: false
                )
            }
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
