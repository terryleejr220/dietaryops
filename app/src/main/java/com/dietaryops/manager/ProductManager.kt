package com.dietaryops.manager

import android.content.Context
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ScanRecord
import com.dietaryops.manager.data.repository.DeliveryRepository
import com.dietaryops.manager.util.DateCalculator
import com.dietaryops.manager.util.DateCalculationResult
import com.dietaryops.manager.util.Gs1BarcodeParser
import com.dietaryops.manager.util.SyscoUpcNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

import com.dietaryops.manager.util.toTitleCase

data class SyscoProduct(
    val upc: String,
    val name: String,
    val shelfLifeDays: Int,
    val category: String = "General",
    val unit: String = "EA",
    val lastOnHandAmount: Double = 1.0,
    val syscoItemNumber: String = "",
    val piazzaItemNumber: String = "",
    val vendorItemNumber: String = "",
    val vendorSku: String = "",
    val alternateBarcodes: String = "",
    val parLevel: Double = 0.0
)

data class ScannedItemState(
    val rawUpc: String,
    val normalizedUpc: String,
    val product: SyscoProduct,
    val dateCalculation: DateCalculationResult,
    val isKnownCatalog: Boolean,
    val onHandAmount: Double = 1.0
)

class ProductManager(context: Context) {
    val settingsManager: SettingsManager = SettingsManager(context)
    val repository: DeliveryRepository = DeliveryRepository(context)
    
    val webAppUrl: String
        get() = settingsManager.webAppUrl

    suspend fun processScan(rawUpc: String, scanDate: LocalDate = LocalDate.now()): ScannedItemState {
        val parsedData = Gs1BarcodeParser.parseBarcode(rawUpc)
        val extractedUpc = parsedData.extractedUpc.ifBlank { SyscoUpcNormalizer.normalize(rawUpc) }
        
        repository.seedDefaultsIfEmpty()

        // Catalog lookup: try extractedUpc first, then rawUpc, then gtin if present
        var catalogItem = repository.getCatalogItem(extractedUpc)
        if (catalogItem.name == "Unrecognized Item" && rawUpc != extractedUpc) {
            val rawItem = repository.getCatalogItem(rawUpc)
            if (rawItem.name != "Unrecognized Item") {
                catalogItem = rawItem
            } else if (!parsedData.gtin.isNullOrBlank()) {
                val gtinItem = repository.getCatalogItem(parsedData.gtin)
                if (gtinItem.name != "Unrecognized Item") {
                    catalogItem = gtinItem
                }
            }
        }

        val isKnownInDb = catalogItem.name != "Unrecognized Item" && 
                          !catalogItem.name.startsWith("Item (") && 
                          !catalogItem.name.startsWith("Sysco Item (") &&
                          !catalogItem.name.startsWith("Unrecognized Item")

        val effectiveName = if (isKnownInDb) {
            catalogItem.name
        } else {
            "Unrecognized Item"
        }.removePrefix("Sysco ").removePrefix("SYSCO ").trim().toTitleCase()

        val normalizedDbCategory = DeliveryRepository.normalizeCategory(catalogItem.category).toTitleCase()

        val effectiveCategory = if (isKnownInDb && normalizedDbCategory.isNotBlank()) {
            normalizedDbCategory
        } else {
            "General"
        }

        val effectiveDays = if (isKnownInDb && catalogItem.defaultShelfLifeDays > 0) {
            catalogItem.defaultShelfLifeDays
        } else {
            DateCalculator.getShelfLifeDaysForCategory(effectiveCategory)
        }

        val effectiveUnit = if (parsedData.weightLbs != null) {
            "LB"
        } else if (isKnownInDb && catalogItem.unit.isNotBlank()) {
            catalogItem.unit
        } else {
            "EA"
        }

        val effectiveOnHand = if (parsedData.weightLbs != null && parsedData.weightLbs > 0.0) {
            parsedData.weightLbs
        } else if (isKnownInDb) {
            catalogItem.lastOnHandAmount
        } else {
            1.0
        }

        val product = if (isKnownInDb) {
            catalogItem.toSyscoProduct().copy(
                upc = extractedUpc.ifBlank { rawUpc.trim() },
                name = catalogItem.name.toTitleCase(),
                shelfLifeDays = DateCalculator.clampShelfLifeDays(effectiveDays, effectiveCategory),
                category = effectiveCategory,
                unit = effectiveUnit,
                lastOnHandAmount = effectiveOnHand
            )
        } else {
            SyscoProduct(
                upc = extractedUpc.ifBlank { rawUpc.trim() },
                name = effectiveName,
                shelfLifeDays = DateCalculator.clampShelfLifeDays(effectiveDays, effectiveCategory),
                category = effectiveCategory,
                unit = effectiveUnit,
                lastOnHandAmount = effectiveOnHand,
                syscoItemNumber = catalogItem.syscoItemNumber,
                piazzaItemNumber = catalogItem.piazzaItemNumber,
                vendorItemNumber = catalogItem.vendorItemNumber,
                vendorSku = catalogItem.vendorSku,
                alternateBarcodes = catalogItem.alternateBarcodes
            )
        }

        // If explicitExpirationDate is present in GS1-128 barcode, use it directly as useByDate!
        val dateCalc = if (parsedData.explicitExpirationDate != null) {
            val explicitUseBy = parsedData.explicitExpirationDate
            val calculatedDays = ChronoUnit.DAYS.between(scanDate, explicitUseBy).toInt()
            val shelfDays = if (calculatedDays > 0) DateCalculator.clampShelfLifeDays(calculatedDays) else product.shelfLifeDays
            DateCalculationResult(
                deliveryDate = scanDate,
                shelfLifeDays = shelfDays,
                useByDate = explicitUseBy,
                deliveryDateIso = scanDate.format(DateCalculator.ISO_FORMATTER),
                deliveryDateFormatted = scanDate.format(DateCalculator.US_FORMATTER),
                useByDateIso = explicitUseBy.format(DateCalculator.ISO_FORMATTER),
                useByDateFormatted = explicitUseBy.format(DateCalculator.US_FORMATTER),
                useByDateShort = explicitUseBy.format(DateCalculator.SHORT_FORMATTER)
            )
        } else {
            DateCalculator.calculate(
                scanDate = scanDate,
                shelfLifeDays = product.shelfLifeDays
            )
        }

        return ScannedItemState(
            rawUpc = rawUpc,
            normalizedUpc = extractedUpc.ifBlank { rawUpc.trim() },
            product = product,
            dateCalculation = dateCalc,
            isKnownCatalog = isKnownInDb,
            onHandAmount = effectiveOnHand
        )
    }

    suspend fun getCatalogItem(query: String): CatalogItem {
        return repository.getCatalogItem(query)
    }

    fun getAllCatalogItems(): Flow<List<CatalogItem>> {
        return repository.getAllCatalogItems()
    }

    suspend fun saveCatalogItem(item: CatalogItem) {
        val normCategory = DeliveryRepository.normalizeCategory(item.category).toTitleCase()
        val cleanItem = item.copy(
            syscoUpc = SyscoUpcNormalizer.normalize(item.syscoUpc),
            name = item.name.removePrefix("Sysco ").removePrefix("SYSCO ").trim().toTitleCase(),
            category = normCategory,
            defaultShelfLifeDays = DateCalculator.clampShelfLifeDays(item.defaultShelfLifeDays, normCategory),
            lastOnHandAmount = item.lastOnHandAmount
        )
        repository.saveCatalogItem(cleanItem)
    }

    suspend fun saveProduct(item: CatalogItem) {
        saveCatalogItem(item)
    }

    suspend fun saveProduct(product: SyscoProduct) {
        val catalogItem = CatalogItem.fromSyscoProduct(product)
        saveCatalogItem(catalogItem)
    }

    /**
     * Links a scanned barcode to a target catalog item name, registering the scanned UPC
     * as an alternate barcode mapping for that item in Room DB if it differs from the primary UPC.
     */
    suspend fun linkBarcodeToCatalogItem(
        scannedUpc: String,
        productName: String,
        category: String,
        unit: String,
        shelfLifeDays: Int,
        onHandAmount: Double
    ): CatalogItem {
        val normUpc = SyscoUpcNormalizer.normalize(scannedUpc).ifBlank { scannedUpc.trim() }
        val cleanName = productName.removePrefix("Sysco ").removePrefix("SYSCO ").trim().ifBlank { "Unrecognized Item" }.toTitleCase()
        val normCategory = DeliveryRepository.normalizeCategory(category).toTitleCase()
        val validDays = DateCalculator.clampShelfLifeDays(shelfLifeDays, normCategory)
        val validOnHand = if (onHandAmount > 0.0) onHandAmount else 1.0

        val existingItem = repository.getCatalogItemByName(cleanName)

        if (existingItem != null) {
            val currentAlt = existingItem.alternateBarcodes
            val altList = if (currentAlt.isNotBlank()) {
                currentAlt.split(",").map { SyscoUpcNormalizer.normalize(it.trim()) }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val isPrimaryMatch = existingItem.syscoUpc.equals(normUpc, ignoreCase = true)
            val newAltBarcodes = if (!isPrimaryMatch && !altList.contains(normUpc) && normUpc.isNotBlank()) {
                if (currentAlt.isBlank()) normUpc else "$currentAlt, $normUpc"
            } else {
                currentAlt
            }

            val updatedExisting = existingItem.copy(
                alternateBarcodes = newAltBarcodes,
                category = normCategory,
                defaultShelfLifeDays = validDays,
                unit = unit,
                lastOnHandAmount = validOnHand
            )
            repository.saveCatalogItem(updatedExisting)

            if (!isPrimaryMatch && normUpc.isNotBlank()) {
                val directCatalogItem = CatalogItem(
                    syscoUpc = normUpc,
                    name = cleanName,
                    category = normCategory,
                    defaultShelfLifeDays = validDays,
                    unit = unit,
                    lastOnHandAmount = validOnHand,
                    syscoItemNumber = existingItem.syscoItemNumber,
                    piazzaItemNumber = existingItem.piazzaItemNumber,
                    vendorItemNumber = existingItem.vendorItemNumber,
                    vendorSku = existingItem.vendorSku,
                    alternateBarcodes = newAltBarcodes
                )
                repository.saveCatalogItem(directCatalogItem)
            }
            return updatedExisting
        } else {
            val newCatalogItem = CatalogItem(
                syscoUpc = normUpc,
                name = cleanName,
                category = normCategory,
                defaultShelfLifeDays = validDays,
                unit = unit,
                lastOnHandAmount = validOnHand,
                alternateBarcodes = ""
            )
            repository.saveCatalogItem(newCatalogItem)
            return newCatalogItem
        }
    }

    suspend fun logScanToSheets(scanRecord: ScanRecord) {
        repository.addScanRecord(scanRecord)
        val url = webAppUrl
        if (url.isNotBlank()) {
            try {
                repository.syncWithGoogleSheets(url)
            } catch (_: Exception) {}
        }
    }

    /**
     * Persists [scanRecord] to Room and fires a background Sheets sync.
     * Use this in UI coroutine scopes instead of calling repository directly.
     * Returns immediately — the sync result is delivered via the returned [Result] or
     * can be observed through [DeliveryRepository.syncWithGoogleSheets] if [onSyncResult] is provided.
     */
    fun logScanAsync(
        scanRecord: ScanRecord,
        scope: CoroutineScope,
        onSyncResult: ((Result<Int>) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            repository.addScanRecord(scanRecord)
            val url = webAppUrl
            if (url.isNotBlank()) {
                val result = try {
                    repository.syncWithGoogleSheets(url)
                } catch (e: Exception) {
                    Result.failure(e)
                }
                onSyncResult?.invoke(result)
            } else {
                onSyncResult?.invoke(Result.success(0))
            }
        }
    }

    // ─── Repository Proxy Methods ──────────────────────────────────────────────
    // These expose frequently-needed repository operations so UI screens never
    // reach through productManager.repository directly.
    // NOTE: getCatalogItem, saveCatalogItem, getAllCatalogItems already exist above.

    /** Insert or update a scan record in Room. */
    suspend fun addScanRecord(record: ScanRecord) = repository.addScanRecord(record)

    /** Mark a previously saved scan record as printed. */
    suspend fun markRecordPrinted(recordId: String) = repository.markRecordPrinted(recordId)

    /** Update the on-hand amount of a scan record and keep catalog in sync. */
    suspend fun updateOnHandAmount(recordId: String, upc: String, quantity: Double) =
        repository.updateScanRecordOnHandAmount(recordId, upc, quantity)

    /** Seed the default catalog if the database is empty (called once at startup). */
    suspend fun seedDefaultsIfEmpty() = repository.seedDefaultsIfEmpty()

    /** Sync unsynced records to Google Sheets. Returns count of records pushed. */
    suspend fun syncWithGoogleSheets(webAppUrl: String = this.webAppUrl): Result<Int> =
        repository.syncWithGoogleSheets(webAppUrl)
}
