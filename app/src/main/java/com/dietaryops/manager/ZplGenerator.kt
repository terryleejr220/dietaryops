package com.dietaryops.manager

import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.util.DateCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ZplGenerator {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yy")

    /**
     * Determines dietary storage location based on category:
     * Returns "COOLER", "FREEZER", or "DRY STORAGE".
     */
    fun getStorageLocationForCategory(category: String): String {
        val catLower = category.trim().lowercase()
        return when {
            catLower.contains("frozen") || catLower.contains("freezer") || catLower.contains("ice cream") -> "FREEZER"
            catLower.contains("dairy") || catLower.contains("fresh") || catLower.contains("produce") || catLower.contains("meat") || catLower.contains("cooler") || catLower.contains("deli") || catLower.contains("egg") || catLower.contains("milk") || catLower.contains("cheese") || catLower.contains("yogurt") || catLower.contains("butter") || catLower.contains("prepared") -> "COOLER"
            else -> "DRY STORAGE"
        }
    }

    /**
     * Generates a 2.25" x 1.25" ZPL receiving label formatted for 203 dpi Zebra printers (QLn420)
     * using the exact ZPL template provided.
     *
     * @param itemName Item Name
     * @param upc UPC barcode
     * @param deliveryDate Delivery / Received Date
     * @param useByDate Calculated Use-By / Expiration Date
     * @param category Category for storage location fallback
     * @param storageLocation Storage Location (e.g. COOLER, FREEZER, DRY STORAGE)
     * @param staffInitials Staff Initials (with setting/fallback, e.g. "CV")
     */
    fun generateLabel(
        itemName: String,
        upc: String,
        deliveryDate: LocalDate,
        useByDate: LocalDate,
        category: String = "General",
        storageLocation: String? = null,
        companyHeader: String = "DIETARY OPS KITCHEN",
        barcodeType: String = "BC",
        onHandAmount: Double? = null,
        staffInitials: String = "DO"
    ): String {
        val location = storageLocation ?: getStorageLocationForCategory(category)
        val delivDateStr = deliveryDate.format(dateFormatter)
        
        // Guarantee useByDate is strictly after deliveryDate (positive ServSafe shelf life minimum)
        val effectiveUseBy = if (!useByDate.isAfter(deliveryDate)) {
            val fallbackDays = DateCalculator.getDefaultServSafeDays(category, location)
            deliveryDate.plusDays(fallbackDays.toLong())
        } else {
            useByDate
        }
        val useByStr = effectiveUseBy.format(dateFormatter)
        val cleanInitials = if (staffInitials.isBlank()) "DO" else staffInitials.trim()
        val cleanItemName = itemName.trim()

        return """
^XA
^PW456
^LL254
^MD15
^LH10,10

; --- 1. ITEM NAME (Auto-wrapping up to 2 lines) ---
^FO10,10^A0N,28,28^FB416,2,0,L,0^FD$cleanItemName^FS

; --- DIVIDER LINE ---
^FO10,72^GB416,2,2^FS

; --- 2. DELIVERED DATE & STORAGE LOCATION ---
^FO10,82^A0N,22,22^FDDELIVERED: $delivDateStr^FS
^FO230,82^A0N,20,20^FDLOC: $location^FS

; --- 3. CRITICAL USE-BY / DISCARD DATE (High Visibility) ---
^FO10,118^A0N,24,24^FDUSE BY:^FS
^FO125,110^A0N,38,38^FD$useByStr^FS

; --- DIVIDER LINE ---
^FO10,158^GB416,2,2^FS

; --- 4. STAFF INITIALS & ROTATION NOTICE ---
^FO10,168^A0N,20,20^FDBY: $cleanInitials^FS
^FO120,168^A0N,18,18^FDFIFO - ROTATE STOCK^FS
^FO10,192^A0N,16,16^FDDIETARY KITCHEN OPS^FS

^XZ
        """.trimIndent()
    }

    /**
     * Helper overload taking SyscoProduct object.
     */
    fun generateLabel(
        product: SyscoProduct,
        deliveryDate: LocalDate = LocalDate.now(),
        storageLocation: String? = null,
        onHandAmount: Double? = null,
        staffInitials: String = "DO"
    ): String {
        val validDays = DateCalculator.clampShelfLifeDays(
            days = product.shelfLifeDays,
            category = product.category,
            storageArea = storageLocation
        )
        val useByDate = deliveryDate.plusDays(validDays.toLong())
        return generateLabel(
            itemName = product.name,
            upc = product.upc,
            deliveryDate = deliveryDate,
            useByDate = useByDate,
            category = product.category,
            storageLocation = storageLocation,
            onHandAmount = onHandAmount ?: if (product.lastOnHandAmount > 0) product.lastOnHandAmount else null,
            staffInitials = staffInitials
        )
    }

    /**
     * Generates a 2.25" x 1.25" ZPL shelf/bin label formatted for 203 dpi Zebra printers (QLn420)
     * using the exact ZPL template provided.
     */
    fun generateShelfLabelZpl(
        itemName: String,
        upc: String,
        storageLocation: String,
        syscoItemNumber: String = "",
        piazzaItemNumber: String = "",
        parLevel: Double = 0.0,
        lastOnHand: Double = 0.0,
        unit: String = "EA",
        companyHeader: String = "DIETARY OPS SHELF LABEL",
        barcodeType: String = "BC"
    ): String {
        val cleanUpc = if (upc.isNotBlank()) upc.trim() else if (syscoItemNumber.isNotBlank()) syscoItemNumber.trim() else "000000000000"
        val cleanItemName = itemName.trim()
        val resolvedPar = if (parLevel > 0) {
            if (parLevel % 1.0 == 0.0) parLevel.toInt().toString() else parLevel.toString()
        } else {
            "1"
        }
        val cleanUnit = if (unit.isNotBlank()) unit.trim().lowercase() else "ea"
        val cleanStorage = if (storageLocation.isNotBlank()) storageLocation.trim() else "DRY"

        return """
^XA
^PW456
^LL254
^MD15
^LH0,0

; 1. PRODUCT NAME (Bold & Prominent)
^FO15,12^A0N,32,32^FB426,1,0,L,0^FD$cleanItemName^FS

; 2. UPC / BARCODE (Centered, 60 dots tall with human-readable numbers below)
^FO55,55^BY2,2.5,60^BCN,60,Y,N,N^FD$cleanUpc^FS

; Divider
^FO15,145^GB426,2,2^FS

; 3. PAR LEVEL & SHELF/SECTION (Side-by-Side on bottom)
^FO15,165^A0N,28,26^FB210,2,0,L,0^FDPar: $resolvedPar $cleanUnit^FS
^FO230,165^A0N,26,20^FB211,2,0,R,0^FDSec: $cleanStorage^FS

^XZ
        """.trimIndent()
    }

    /**
     * Helper overload taking CatalogItem object.
     */
    fun generateShelfLabelZpl(
        item: CatalogItem,
        storageLocation: String? = null,
        companyHeader: String = "DIETARY OPS SHELF LABEL",
        barcodeType: String = "BC"
    ): String {
        val location = storageLocation ?: getStorageLocationForCategory(item.category)
        return generateShelfLabelZpl(
            itemName = item.name,
            upc = item.syscoUpc,
            storageLocation = location,
            syscoItemNumber = item.syscoItemNumber,
            piazzaItemNumber = item.piazzaItemNumber,
            parLevel = item.parLevel,
            lastOnHand = item.lastOnHandAmount,
            unit = item.unit,
            companyHeader = companyHeader,
            barcodeType = barcodeType
        )
    }
}
