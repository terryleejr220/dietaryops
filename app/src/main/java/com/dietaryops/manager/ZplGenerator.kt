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
        companyHeader: String = "DIETARY KITCHEN OPS",
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
^MNY
^MTD
^PW456
^LL254
^MD18
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
^FO10,165^A0N,20,20^FDBY: $cleanInitials^FS
^FO120,165^A0N,18,18^FDFIFO - ROTATE STOCK^FS

; --- 5. CODE 128 BARCODE (Shifted left to ^FO30 to prevent edge cutoff on 14-digit GTINs) ---
^FO30,185^BY1,2,30^BCN,30,Y,N,N^FD$upc^FS

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
            itemName = product.displayNameForLabel,
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
^MNY
^MTD
^PW456
^LL254
^MD18
^LH0,0

; 1. BARCODE AT TOP (Centered, 65 dots tall with human-readable numbers underneath)
^FO50,14^BY2,2.5,65^BCN,65,Y,N,N^FD$cleanUpc^FS

; Divider Line
^FO15,110^GB426,2,2^FS

; 2. PRODUCT NAME BELOW BARCODE (Bold & Prominent 30pt font, up to 2 lines auto-wrapped)
^FO15,122^A0N,30,28^FB426,2,0,L,0^FD$cleanItemName^FS

; Divider Line
^FO15,188^GB426,1,1^FS

; 3. PAR LEVEL & STORAGE SECTION (Side-by-Side on bottom line)
^FO15,198^A0N,24,22^FB210,1,0,L,0^FDPar: $resolvedPar $cleanUnit^FS
^FO230,198^A0N,24,22^FB211,1,0,R,0^FDSec: $cleanStorage^FS

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
            itemName = item.displayNameForLabel,
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

    /**
     * Helper overload taking SyscoProduct object.
     */
    fun generateShelfLabelZpl(
        item: SyscoProduct,
        storageLocation: String? = null,
        companyHeader: String = "DIETARY OPS SHELF LABEL",
        barcodeType: String = "BC"
    ): String {
        val location = storageLocation ?: getStorageLocationForCategory(item.category)
        return generateShelfLabelZpl(
            itemName = item.displayNameForLabel,
            upc = item.upc,
            storageLocation = location,
            syscoItemNumber = item.syscoItemNumber,
            piazzaItemNumber = "",
            parLevel = 0.0,
            lastOnHand = item.lastOnHandAmount,
            unit = item.unit,
            companyHeader = companyHeader,
            barcodeType = barcodeType
        )
    }

    /**
     * Generates a 2" x 1" (or 2.25" x 1.25") ZPL staff access badge thermal sticker (203 dpi Zebra QLn420).
     * Shortened payload (e.g. "CV-TL01") ensures the barcode fits cleanly on 2" x 1" labels without overflowing.
     */
    fun generateStaffBadgeZpl(
        displayName: String,
        employeeId: String,
        role: String = "STAFF",
        companyName: String = "DIETARY OPS"
    ): String {
        val cleanName = displayName.trim().uppercase()
        val cleanId = employeeId.trim().uppercase()
        val cleanRole = role.trim().uppercase()
        val cleanCompany = companyName.trim().uppercase()
        val barcodePayload = "$cleanCompany-$cleanId"

        return """
^XA
^MNY
^MTD
^PW416
^LL220
^MD15
^LH0,0

; 1. COMPANY HEADER
^FO10,8^A0N,20,20^FB396,1,0,C,0^FD$cleanCompany ACCESS BADGE^FS
^FO10,30^GB396,2,2^FS

; 2. STAFF NAME & ROLE
^FO10,38^A0N,26,26^FB396,1,0,C,0^FD$cleanName^FS
^FO10,66^A0N,18,18^FB396,1,0,C,0^FDROLE: $cleanRole  •  ID: $cleanId^FS

; 3. CODE 128 BARCODE (Short 7-char payload fits 2x1 label cleanly)
^FO35,88^BY2,2.0,55^BCN,55,Y,N,N^FD$barcodePayload^FS

; 4. FOOTER
^FO10,172^GB396,1,1^FS
^FO10,180^A0N,14,14^FB396,1,0,C,0^FDScan Badge to Sign-In / Audit^FS

^XZ
        """.trimIndent()
    }

    /**
     * Generates a 2.25" x 1.25" ZPL ServSafe "Pull to Thaw" label (203 dpi Zebra QLn420).
     * No barcodes, large item name, clear discard date, clean ASCII (no degree symbol / ?).
     */
    fun generateThawLabelZpl(
        itemName: String,
        upc: String = "",
        thawDate: LocalDate = LocalDate.now(),
        staffInitials: String = "DO"
    ): String {
        val thawStr = thawDate.format(dateFormatter)
        val useByStr = thawDate.plusDays(7).format(dateFormatter)
        val cleanName = itemName.trim()
        val cleanInitials = if (staffInitials.isBlank()) "DO" else staffInitials.trim()

        return """
^XA
^MNY
^MTD
^PW456
^LL254
^MD18
^LH10,10

; --- 1. ITEM NAME (Large & Prominent 34pt font) ---
^FO10,10^A0N,34,32^FB436,2,0,L,0^FD$cleanName^FS

; --- DIVIDER LINE ---
^FO10,95^GB436,2,2^FS

; --- 2. THAW HEADER & DATE ---
^FO10,108^A0N,22,22^FDPULLED: $thawStr^FS
^FO250,108^A0N,20,20^FD[ THAW COOLER ]^FS

; --- 3. DISCARD DATE (Largest 40pt font) ---
^FO10,145^A0N,28,26^FDDISCARD:^FS
^FO150,138^A0N,40,38^FD$useByStr^FS

; --- DIVIDER LINE ---
^FO10,190^GB436,2,2^FS

; --- 4. STAFF SIGNATURE & ROTATION ---
^FO10,202^A0N,20,20^FDBY: $cleanInitials^FS
^FO160,202^A0N,20,20^FDFIFO - ROTATE STOCK^FS

^XZ
        """.trimIndent()
    }

    /**
     * Generates a 2.25" x 1.25" ZPL "Opened Container" ServSafe TCS label (203 dpi Zebra QLn420).
     * No barcodes, large item name, clear discard date, clean ASCII (no degree symbol / ?).
     */
    fun generateOpenedContainerZpl(
        itemName: String,
        upc: String = "",
        openedDate: LocalDate = LocalDate.now(),
        staffInitials: String = "DO"
    ): String {
        val openedStr = openedDate.format(dateFormatter)
        val useByStr = openedDate.plusDays(7).format(dateFormatter)
        val cleanName = itemName.trim()
        val cleanInitials = if (staffInitials.isBlank()) "DO" else staffInitials.trim()

        return """
^XA
^MNY
^MTD
^PW456
^LL254
^MD18
^LH10,10

; --- 1. ITEM NAME (Large & Prominent 34pt font) ---
^FO10,10^A0N,34,32^FB436,2,0,L,0^FD$cleanName^FS

; --- DIVIDER LINE ---
^FO10,95^GB436,2,2^FS

; --- 2. OPENED DATE & TEMP ---
^FO10,108^A0N,22,22^FDOPENED: $openedStr^FS
^FO250,108^A0N,20,20^FDKEEP <= 41F^FS

; --- 3. DISCARD DATE (Largest 40pt font) ---
^FO10,145^A0N,28,26^FDDISCARD:^FS
^FO150,138^A0N,40,38^FD$useByStr^FS

; --- DIVIDER LINE ---
^FO10,190^GB436,2,2^FS

; --- 4. STAFF SIGNATURE & ROTATION ---
^FO10,202^A0N,20,20^FDBY: $cleanInitials^FS
^FO160,202^A0N,20,20^FDFIFO - ROTATE STOCK^FS

^XZ
        """.trimIndent()
    }

    /**
     * Generates a 2.25" x 1.25" ZPL ServSafe Prepared / Cooked Food Prep label (203 dpi Zebra QLn420).
     * No barcodes, large item name, clear discard date, clean ASCII (no degree symbol / ?).
     *
     * @param prepItemName Name of dish / prepared item (e.g. "Tuna Salad", "Cooked Marinara", "Sliced Turkey")
     * @param prepDate Date prepared (defaults to today)
     * @param prepType Category / Prep Type label (e.g. "PREPARED FOOD", "COOK & CHILL", "PORTIONED")
     * @param retentionDays Days until discard (defaults to 7 for ServSafe TCS)
     * @param staffInitials Staff initials for accountability
     */
    fun generatePrepLabelZpl(
        prepItemName: String,
        prepDate: LocalDate = LocalDate.now(),
        prepType: String = "PREPARED FOOD",
        retentionDays: Int = 7,
        staffInitials: String = "DO",
        companyHeader: String = "DIETARY KITCHEN PREP"
    ): String {
        val prepStr = prepDate.format(dateFormatter)
        val discardDate = prepDate.plusDays(retentionDays.toLong())
        val discardStr = discardDate.format(dateFormatter)
        val cleanName = prepItemName.trim()
        val cleanInitials = if (staffInitials.isBlank()) "DO" else staffInitials.trim()

        return """
^XA
^MNY
^MTD
^PW456
^LL254
^MD18
^LH10,10

; --- 1. PREP ITEM NAME (Large & Prominent 36pt font) ---
^FO10,10^A0N,36,34^FB436,2,0,L,0^FD$cleanName^FS

; --- DIVIDER LINE ---
^FO10,95^GB436,2,2^FS

; --- 2. PREPPED DATE & TEMP ---
^FO10,108^A0N,22,22^FDPREPPED: $prepStr^FS
^FO250,108^A0N,20,20^FDKEEP <= 41F^FS

; --- 3. DISCARD DATE (Largest 40pt font) ---
^FO10,145^A0N,28,26^FDDISCARD:^FS
^FO150,138^A0N,40,38^FD$discardStr^FS

; --- DIVIDER LINE ---
^FO10,190^GB436,2,2^FS

; --- 4. STAFF SIGNATURE & ROTATION ---
^FO10,202^A0N,20,20^FDBY: $cleanInitials^FS
^FO160,202^A0N,20,20^FDFIFO - ROTATE STOCK^FS

^XZ
        """.trimIndent()
    }

    /**
     * Generates a 2.25" x 1.25" ZPL ServSafe "USE FIRST" rotation thermal sticker (203 dpi Zebra QLn420).
     * Flashy bold "USE FIRST" border box, optional item name, print date, and small staff initials at the very bottom.
     */
    fun generateUseFirstLabelZpl(
        itemName: String = "",
        printDate: LocalDate = LocalDate.now(),
        staffInitials: String = "DO"
    ): String {
        val printDateStr = printDate.format(dateFormatter)
        val cleanName = itemName.trim().uppercase()
        val cleanInitials = if (staffInitials.isBlank()) "DO" else staffInitials.trim().uppercase()

        val hasName = cleanName.isNotBlank()
        val boxHeight = if (hasName) 115 else 145
        val textY = if (hasName) 20 else 32
        val subY = if (hasName) 80 else 98

        val nameZpl = if (hasName) {
            "^FO10,138^A0N,24,24^FB416,1,0,C,0^FD$cleanName^FS\n^FO10,170^GB416,1,1^FS"
        } else {
            ""
        }

        return """
^XA
^MNY
^MTD
^PW456
^LL254
^MD18
^LH10,10

; --- 1. FLASHY BOLD USE FIRST FRAME BOX ---
^FO10,10^GB416,$boxHeight,6^FS
^FO20,$textY^A0N,60,56^FB396,1,0,C,0^FDUSE FIRST^FS
^FO20,$subY^A0N,22,20^FB396,1,0,C,0^FDROTATE STOCK - SERVSAFE FIFO^FS

$nameZpl

; --- 2. PRINT DATE & SMALL INITIALS AT VERY BOTTOM ---
^FO10,192^GB416,1,1^FS
^FO10,208^A0N,18,18^FDDATED: $printDateStr^FS
^FO280,208^A0N,16,16^FDBY: $cleanInitials^FS

^XZ
        """.trimIndent()
    }
}
