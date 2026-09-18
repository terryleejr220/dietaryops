package com.dietaryops.manager.data.remote

import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.CompanyProfile
import com.dietaryops.manager.data.model.ScanRecord
import com.dietaryops.manager.data.model.StaffRole
import com.dietaryops.manager.data.model.StaffUser
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

    fun getCompanyDocument(companyCode: String) =
        firestore?.collection("companies")?.document(companyCode.trim().uppercase())

    private fun getCatalogCollection(companyCode: String? = null) =
        if (!companyCode.isNullOrBlank()) {
            getCompanyDocument(companyCode)?.collection("catalog")
        } else {
            firestore?.collection("catalog")
        }

    private fun getScanLogsCollection(companyCode: String? = null) =
        if (!companyCode.isNullOrBlank()) {
            getCompanyDocument(companyCode)?.collection("scan_logs")
        } else {
            firestore?.collection("scan_logs")
        }

    private val catalogCollection
        get() = getCatalogCollection(null)

    private val scanLogsCollection
        get() = getScanLogsCollection(null)

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

    suspend fun fetchCompanyProfile(companyCode: String): Result<CompanyProfile?> {
        val fs = firestore ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val doc = fs.collection("companies").document(companyCode.trim().uppercase()).get().await()
            if (!doc.exists()) {
                return Result.success(null)
            }
            val profile = CompanyProfile(
                companyId = doc.getString("companyId") ?: doc.id,
                name = doc.getString("name") ?: "Company $companyCode",
                code = doc.getString("code") ?: companyCode.trim().uppercase(),
                spreadsheetId = doc.getString("spreadsheetId") ?: "",
                webAppUrl = doc.getString("webAppUrl") ?: "",
                departments = (doc.get("departments") as? List<*>)?.mapNotNull { it?.toString() } ?: listOf("Dietary"),
                departmentTabs = (doc.get("departmentTabs") as? Map<*, *>)?.entries?.associate {
                    it.key.toString() to it.value.toString()
                } ?: mapOf("Dietary" to "Delivery Log"),
                active = doc.getBoolean("active") ?: true
            )
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchStaffUser(companyCode: String, employeeId: String): Result<StaffUser?> {
        val fs = firestore ?: return Result.failure(Exception("Firestore unavailable"))
        return try {
            val doc = fs.collection("companies")
                .document(companyCode.trim().uppercase())
                .collection("users")
                .document(employeeId.trim().uppercase())
                .get()
                .await()

            if (!doc.exists()) {
                return Result.success(null)
            }

            val user = StaffUser(
                employeeId = doc.getString("employeeId") ?: doc.id,
                displayName = doc.getString("displayName") ?: "Staff $employeeId",
                companyCode = companyCode.trim().uppercase(),
                department = doc.getString("department") ?: "Dietary",
                role = StaffRole.fromString(doc.getString("role")),
                pin = doc.getString("pin") ?: "",
                badgeToken = doc.getString("badgeToken") ?: "",
                active = doc.getBoolean("active") ?: true
            )
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyBadgeLogin(companyCode: String, employeeId: String, badgeToken: String): Result<StaffUser> {
        val userResult = fetchStaffUser(companyCode, employeeId)
        val user = userResult.getOrNull()
            ?: return Result.failure(Exception("Employee ID '$employeeId' not found for facility '$companyCode'"))

        if (!user.active) {
            return Result.failure(Exception("Employee profile is currently inactive"))
        }

        // Verify badgeToken if configured on server, otherwise allow ID match
        if (user.badgeToken.isNotBlank() && badgeToken.isNotBlank() && user.badgeToken != badgeToken) {
            return Result.failure(Exception("Invalid or revoked badge token"))
        }

        return Result.success(user)
    }

    suspend fun verifyPinLogin(companyCode: String, employeeId: String, pin: String): Result<StaffUser> {
        val userResult = fetchStaffUser(companyCode, employeeId)
        val user = userResult.getOrNull()
            ?: return Result.failure(Exception("Employee ID '$employeeId' not found for facility '$companyCode'"))

        if (!user.active) {
            return Result.failure(Exception("Employee profile is currently inactive"))
        }

        if (user.pin.isNotBlank() && user.pin != pin.trim()) {
            return Result.failure(Exception("Incorrect PIN"))
        }

        return Result.success(user)
    }
}
