package com.dietaryops.manager

import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.CatalogItem
import com.dietaryops.manager.data.model.ScanRecord
import com.dietaryops.manager.data.remote.GoogleSheetsApiService
import com.dietaryops.manager.data.remote.SheetScanRecordDto
import com.dietaryops.manager.data.repository.DeliveryRepository
import com.dietaryops.manager.util.DateCalculator
import com.dietaryops.manager.util.SyscoUpcNormalizer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DeliveryScannerTest {

    @Test
    fun testScanRecordAndDtoMappingWithOnHandAmount() {
        val scanRecord = ScanRecord(
            id = "test-123",
            syscoUpc = "074865123401",
            itemName = "Sysco Sliced Roast Beef",
            deliveryDate = "2025-03-10",
            useByDate = "2025-03-15",
            category = "Proteins & Frozen",
            shelfLifeDays = 5,
            unit = "LB",
            onHandAmount = 4.5,
            printed = true,
            syncedToSheets = false
        )

        assertEquals("test-123", scanRecord.id)
        assertEquals("074865123401", scanRecord.syscoUpc)
        assertEquals("LB", scanRecord.unit)
        assertEquals("Proteins & Frozen", scanRecord.category)
        assertEquals(4.5, scanRecord.onHandAmount, 0.001)
        assertFalse(scanRecord.syncedToSheets)

        val dto = SheetScanRecordDto(
            id = scanRecord.id,
            syscoUpc = scanRecord.syscoUpc,
            itemName = scanRecord.itemName,
            deliveryDate = scanRecord.deliveryDate,
            useByDate = scanRecord.useByDate,
            category = scanRecord.category,
            shelfLifeDays = scanRecord.shelfLifeDays,
            unit = scanRecord.unit,
            onHandAmount = scanRecord.onHandAmount,
            scanTimestamp = scanRecord.scanTimestamp
        )

        assertEquals("test-123", dto.id)
        assertEquals("LB", dto.unit)
        assertEquals("Proteins & Frozen", dto.category)
        assertEquals(4.5, dto.onHandAmount!!, 0.001)
    }

    @Test
    fun testCatalogItemToSyscoProductWithLastOnHandAmount() {
        val catalogItem = CatalogItem(
            syscoUpc = "074865123402",
            name = "Sysco Liquid Eggs",
            category = "Dairy & Fresh",
            defaultShelfLifeDays = 7,
            unit = "CTN",
            lastOnHandAmount = 3.0
        )

        val prod = catalogItem.toSyscoProduct()
        assertEquals("074865123402", prod.upc)
        assertEquals("Sysco Liquid Eggs", prod.name)
        assertEquals("Dairy & Fresh", prod.category)
        assertEquals(7, prod.shelfLifeDays)
        assertEquals(3.0, prod.lastOnHandAmount, 0.001)
    }

    @Test
    fun testEightCategoriesListAndShelfLife() {
        val expectedCategories = listOf(
            "Canned Goods",
            "Dry Storage",
            "Condiments & Sauces",
            "Misc Dry & Cereal",
            "Dairy & Fresh",
            "Proteins & Frozen",
            "Supplement",
            "General"
        )

        assertEquals(8, SettingsManager.DEFAULT_CATEGORIES.size)
        assertEquals(expectedCategories, SettingsManager.DEFAULT_CATEGORIES)

        val expectedShelfLifeMap = mapOf(
            "Canned Goods" to 365,
            "Dry Storage" to 365,
            "Condiments & Sauces" to 365,
            "Misc Dry & Cereal" to 365,
            "Dairy & Fresh" to 7,
            "Proteins & Frozen" to 14,
            "Supplement" to 365,
            "General" to 365
        )

        expectedShelfLifeMap.forEach { (cat, expectedDays) ->
            val days = DateCalculator.getShelfLifeDaysForCategory(cat)
            assertEquals("Expected $expectedDays days for category $cat", expectedDays, days)
        }
    }

    @Test
    fun testUpcNormalizationAndCalculation() {
        val rawUpc = "  074865123401  "
        val cleanUpc = SyscoUpcNormalizer.normalize(rawUpc)
        assertEquals("074865123401", cleanUpc)

        val scanDate = LocalDate.of(2025, 3, 10)
        val calc = DateCalculator.calculate(scanDate = scanDate, shelfLifeDays = 5)

        assertEquals("2025-03-10", calc.deliveryDateIso)
        assertEquals("2025-03-15", calc.useByDateIso)
        assertEquals("03/10/2025", calc.deliveryDateFormatted)
        assertEquals("03/15/2025", calc.useByDateFormatted)
    }

    @Test
    fun testDefaultWebAppUrlConfiguration() {
        val expectedUrl = "https://script.google.com/macros/s/AKfycbx0heDYU0f1XyDELM_DFuKdlKmFW_ZJD6cEGegpLHva19PLv-_2CBE_U2EmAuJt1_FxDg/exec"
        assertEquals(expectedUrl, SettingsManager.DEFAULT_WEB_APP_URL)
        assertEquals(expectedUrl, GoogleSheetsApiService.DEFAULT_WEB_APP_URL)
    }

    @Test
    fun testPublishedSheetUrlConfiguration() {
        val expectedPublishedUrl = "https://docs.google.com/spreadsheets/d/e/2PACX-1vShGiN4lxHStYAK_WegMQz_h4vyr602p0cSPY541GxaHbvMMbLyDx70G2sNlhK3_Ab9cjGB9LGoKJh_/pubhtml?gid=1819479005&single=true"
        assertEquals(expectedPublishedUrl, SettingsManager.DEFAULT_PUBLISHED_WEB_URL)
    }

    @Test
    fun testHeaderRowDetectionForProductNames() {
        val headerLineTokens = listOf("Dairy & Fresh", "", "", "", "", "")
        val isHeader = headerLineTokens.drop(1).all { it.isBlank() }
        assertTrue("Category header line with blank quantity columns should be identified as header", isHeader)

        val productLineTokens = listOf("Strawberry Fresh", "0", "0", "", "", "")
        val isProductHeader = productLineTokens.drop(1).all { it.isBlank() }
        assertFalse("Product item row with quantity data should NOT be identified as header", isProductHeader)
    }

    @Test
    fun testCategoryNormalization() {
        assertEquals("Proteins & Frozen", DeliveryRepository.normalizeCategory("Proteins and Frozen"))
        assertEquals("Proteins & Frozen", DeliveryRepository.normalizeCategory("proteins & frozen"))
        assertEquals("Dry Storage", DeliveryRepository.normalizeCategory("dry storage"))
        assertEquals("Canned Goods", DeliveryRepository.normalizeCategory("Canned"))
        assertEquals("Dairy & Fresh", DeliveryRepository.normalizeCategory("Dairy and Fresh"))
        assertEquals("Condiments & Sauces", DeliveryRepository.normalizeCategory("Condiments & Sauces"))
        assertEquals("Supplement", DeliveryRepository.normalizeCategory("Thickened Beverages"))
        assertEquals("General", DeliveryRepository.normalizeCategory(null))
        assertEquals("General", DeliveryRepository.normalizeCategory("   "))
    }

    @Test
    fun testCatalogSyncDtoAlternateFieldMapping() {
        val jsonStr = """
            [
                {
                    "upc": "074861000001",
                    "itemName": "Brown Sugar",
                    "Category": "Canned Goods",
                    "shelfLifeDays": 14,
                    "unit": "CS",
                    "quantity": 5.0
                },
                {
                    "syscoUpc": "074861000002",
                    "productName": "Salt",
                    "item_category": "Condiments & Sauces",
                    "defaultShelfLifeDays": 14,
                    "unitOfMeasure": "CS",
                    "onHand": 12.0
                }
            ]
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromRawString(jsonStr)

        assertEquals(2, dtos.size)
        assertEquals("074861000001", dtos[0].syscoUpc)
        assertEquals("Brown Sugar", dtos[0].name)
        assertEquals("Canned Goods", dtos[0].category)
        assertEquals(14, dtos[0].defaultShelfLifeDays)
        assertEquals(5.0, dtos[0].lastOnHandAmount!!, 0.001)

        assertEquals("074861000002", dtos[1].syscoUpc)
        assertEquals("Salt", dtos[1].name)
        assertEquals("Condiments & Sauces", dtos[1].category)
        assertEquals(12.0, dtos[1].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testWrappedJsonObjectParsing() {
        val wrappedJson = """
            {
                "status": "success",
                "catalog": [
                    {
                        "barcode": "074861000003",
                        "title": "Lady Fingers",
                        "cat": "Misc Dry & Cereal",
                        "days": 10,
                        "onHandAmount": 2.0
                    }
                ]
            }
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromRawString(wrappedJson)

        assertEquals(1, dtos.size)
        assertEquals("074861000003", dtos[0].syscoUpc)
        assertEquals("Lady Fingers", dtos[0].name)
        assertEquals("Misc Dry & Cereal", dtos[0].category)
        assertEquals(10, dtos[0].defaultShelfLifeDays)
        assertEquals(2.0, dtos[0].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testCsvCatalogParsingUpdatesCategoryAndQuantity() {
        val csvStr = """
            UPC, Product, Category, Shelf Life, Unit, Quantity
            074861000001, Brown Sugar, Canned Goods, 14, CS, 8.5
            074861000002, Salt, Proteins & Frozen, 14, CS, 10
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromCsvString(csvStr)

        assertEquals(2, dtos.size)
        assertEquals("074861000001", dtos[0].syscoUpc)
        assertEquals("Brown Sugar", dtos[0].name)
        assertEquals("Canned Goods", dtos[0].category)
        assertEquals(8.5, dtos[0].lastOnHandAmount!!, 0.001)

        assertEquals("074861000002", dtos[1].syscoUpc)
        assertEquals("Salt", dtos[1].name)
        assertEquals("Proteins & Frozen", dtos[1].category)
        assertEquals(10.0, dtos[1].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testDefaultOnHandQuantityIsOne() {
        val defaultScanRecord = ScanRecord(
            syscoUpc = "074861000001",
            itemName = "Test Item",
            deliveryDate = "2025-03-10",
            useByDate = "2025-03-17"
        )
        assertEquals(1.0, defaultScanRecord.onHandAmount, 0.001)
        assertEquals(1.0, defaultScanRecord.effectiveOnHandAmount, 0.001)

        val defaultCatalogItem = CatalogItem(
            syscoUpc = "074861000001",
            name = "Test Catalog Item"
        )
        assertEquals(1.0, defaultCatalogItem.lastOnHandAmount, 0.001)

        val defaultProduct = SyscoProduct(
            upc = "074861000001",
            name = "Test Product",
            shelfLifeDays = 7
        )
        assertEquals(1.0, defaultProduct.lastOnHandAmount, 0.001)

        val zeroQtyCsvStr = """
            UPC, Product, Category, Shelf Life, Unit, Quantity
            074861000001, Brown Sugar, Dry Storage, 14, CS, 0
            074861000002, Salt, Dry Storage, 14, CS, 
        """.trimIndent()

        val zeroDtos = DeliveryRepository.parseCatalogDtosFromCsvString(zeroQtyCsvStr)
        assertEquals(2, zeroDtos.size)
        assertEquals(1.0, zeroDtos[0].lastOnHandAmount!!, 0.001)
        assertEquals(1.0, zeroDtos[1].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testPreviousCountAndPrintableCsvHeadersPopulateLastOnHandAmount() {
        val previousCountCsv = """
            UPC, Product, Previous Count, Category, Unit
            074861000001, Brown Sugar, 6.0, Dry Storage, CS
        """.trimIndent()
        val dtos1 = DeliveryRepository.parseCatalogDtosFromCsvString(previousCountCsv)
        assertEquals(1, dtos1.size)
        assertEquals("074861000001", dtos1[0].syscoUpc)
        assertEquals("Brown Sugar", dtos1[0].name)
        assertEquals(6.0, dtos1[0].lastOnHandAmount!!, 0.001)

        val prevCountCsv = """
            UPC, Product, Prev Count, Category, Unit
            074861000002, Salt, 14, Dry Storage, CS
        """.trimIndent()
        val dtos2 = DeliveryRepository.parseCatalogDtosFromCsvString(prevCountCsv)
        assertEquals(1, dtos2.size)
        assertEquals("074861000002", dtos2[0].syscoUpc)
        assertEquals("Salt", dtos2[0].name)
        assertEquals(14.0, dtos2[0].lastOnHandAmount!!, 0.001)

        val lastCountCsv = """
            UPC, Product, Last Count, Category, Unit
            074861000003, Lady Fingers, 3.5, Dry Storage, CS
        """.trimIndent()
        val dtos3 = DeliveryRepository.parseCatalogDtosFromCsvString(lastCountCsv)
        assertEquals(1, dtos3.size)
        assertEquals("074861000003", dtos3[0].syscoUpc)
        assertEquals("Lady Fingers", dtos3[0].name)
        assertEquals(3.5, dtos3[0].lastOnHandAmount!!, 0.001)

        val printableCsv = """
            UPC, Product, Printable, Category, Unit
            074861000004, Muffin Mix, 8, Dry Storage, CS
        """.trimIndent()
        val dtos4 = DeliveryRepository.parseCatalogDtosFromCsvString(printableCsv)
        assertEquals(1, dtos4.size)
        assertEquals("074861000004", dtos4[0].syscoUpc)
        assertEquals("Muffin Mix", dtos4[0].name)
        assertEquals(8.0, dtos4[0].lastOnHandAmount!!, 0.001)

        val countCsv = """
            UPC, Product, Count, Category, Unit
            074861000005, Yellow Cake, 11, Dry Storage, CS
        """.trimIndent()
        val dtos5 = DeliveryRepository.parseCatalogDtosFromCsvString(countCsv)
        assertEquals(1, dtos5.size)
        assertEquals("074861000005", dtos5[0].syscoUpc)
        assertEquals("Yellow Cake", dtos5[0].name)
        assertEquals(11.0, dtos5[0].lastOnHandAmount!!, 0.001)

        val onHandCsv = """
            UPC, Product, On Hand, Category, Unit
            074861000006, Brownie, 2.5, Dry Storage, CS
        """.trimIndent()
        val dtos6 = DeliveryRepository.parseCatalogDtosFromCsvString(onHandCsv)
        assertEquals(1, dtos6.size)
        assertEquals("074861000006", dtos6[0].syscoUpc)
        assertEquals("Brownie", dtos6[0].name)
        assertEquals(2.5, dtos6[0].lastOnHandAmount!!, 0.001)

        val noUpcCsv = """
            Product, Previous Count, Category, Unit
            Devil Food Cake, 9.0, Dry Storage, CS
        """.trimIndent()
        val dtos7 = DeliveryRepository.parseCatalogDtosFromCsvString(noUpcCsv)
        assertEquals(1, dtos7.size)
        assertEquals("Devil Food Cake", dtos7[0].name)
        assertEquals(9.0, dtos7[0].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testJsonAlternateFieldsPopulateLastOnHandAmount() {
        val jsonStr = """
            [
                {"syscoUpc": "001", "name": "Item 1", "previousCount": 4.0},
                {"syscoUpc": "002", "name": "Item 2", "prevCount": 7.5},
                {"syscoUpc": "003", "name": "Item 3", "lastCount": 9.0},
                {"syscoUpc": "004", "name": "Item 4", "printable": 15.0},
                {"syscoUpc": "005", "name": "Item 5", "printableCount": 2.0},
                {"syscoUpc": "006", "name": "Item 6", "previous": 5.0},
                {"syscoUpc": "007", "name": "Item 7", "On Hand": 10.0}
            ]
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromRawString(jsonStr)
        assertEquals(7, dtos.size)
        assertEquals(4.0, dtos[0].lastOnHandAmount!!, 0.001)
        assertEquals(7.5, dtos[1].lastOnHandAmount!!, 0.001)
        assertEquals(9.0, dtos[2].lastOnHandAmount!!, 0.001)
        assertEquals(15.0, dtos[3].lastOnHandAmount!!, 0.001)
        assertEquals(2.0, dtos[4].lastOnHandAmount!!, 0.001)
        assertEquals(5.0, dtos[5].lastOnHandAmount!!, 0.001)
        assertEquals(10.0, dtos[6].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testSyscoUpcAndSyscoItemNumberHeadersParsing() {
        val csvStr = """
            Sysco UPC, Sysco Item #, Sysco Item #:, SUPC, Barcode, UPC, Product, Category, Unit, Quantity
            123456789012, 987654, 987655, 111222, 333444555666, 777888999000, Test Product, Dry Storage, CS, 10.0
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromCsvString(csvStr)
        assertEquals(1, dtos.size)
        assertNotNull(dtos[0].syscoUpc)
        assertNotNull(dtos[0].syscoItemNumber)
        assertEquals("Test Product", dtos[0].name)
        assertEquals(10.0, dtos[0].lastOnHandAmount!!, 0.001)

        val jsonStr = """
            [
                {
                    "Sysco UPC": "074861000001",
                    "Sysco Item #": "123456",
                    "name": "Sysco Sugar"
                },
                {
                    "Sysco Item #:": "654321",
                    "SUPC": "999888",
                    "Barcode": "074861000002",
                    "UPC": "074861000002",
                    "name": "Sysco Salt"
                }
            ]
        """.trimIndent()

        val jsonDtos = DeliveryRepository.parseCatalogDtosFromRawString(jsonStr)
        assertEquals(2, jsonDtos.size)
        assertEquals("074861000001", jsonDtos[0].syscoUpc)
        assertEquals("123456", jsonDtos[0].syscoItemNumber)
        assertEquals("Sysco Sugar", jsonDtos[0].name)

        assertEquals("074861000002", jsonDtos[1].syscoUpc)
        assertEquals("654321", jsonDtos[1].syscoItemNumber)
        assertEquals("Sysco Salt", jsonDtos[1].name)
    }

    @Test
    fun testMultiVendorItemNumberParsing() {
        val csvStrClean = """
            UPC, Product, Piazza Item #, Vendor Item #, Vendor SKU, Alternate Barcodes, Category, Unit, Quantity
            074865027137, Multi-Vendor Item, PIAZZA-123, VEND-789, SKU-999, "074865027137, 074865027138", Dry Storage, CS, 5.0
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromCsvString(csvStrClean)
        assertEquals(1, dtos.size)
        assertEquals("074865027137", dtos[0].syscoUpc)
        assertEquals("Multi-Vendor Item", dtos[0].name)
        assertEquals("PIAZZA-123", dtos[0].piazzaItemNumber)
        assertEquals("VEND-789", dtos[0].vendorItemNumber)
        assertEquals("SKU-999", dtos[0].vendorSku)
        assertEquals("074865027137, 074865027138", dtos[0].alternateBarcodes)

        val jsonStr = """
            [
                {
                    "syscoUpc": "074865027137",
                    "name": "Json Multi-Vendor",
                    "Piazza #": "P-111",
                    "Vendor Item #": "V-222",
                    "Vendor SKU": "SKU-333",
                    "Alternate Barcodes": "074865027137, 074865027138"
                }
            ]
        """.trimIndent()

        val jsonDtos = DeliveryRepository.parseCatalogDtosFromRawString(jsonStr)
        assertEquals(1, jsonDtos.size)
        assertEquals("P-111", jsonDtos[0].piazzaItemNumber)
        assertEquals("V-222", jsonDtos[0].vendorItemNumber)
        assertEquals("SKU-333", jsonDtos[0].vendorSku)
        assertEquals("074865027137, 074865027138", jsonDtos[0].alternateBarcodes)
    }

    @Test
    fun testCommaSeparatedBarcodesMatchingLogic() {
        val catalogItem = CatalogItem(
            syscoUpc = "074865027137, 074865027138",
            name = "Dual Barcode Item",
            category = "Dry Storage",
            defaultShelfLifeDays = 14,
            unit = "CS",
            alternateBarcodes = "074865027139, 074865027140"
        )

        val scanned1 = SyscoUpcNormalizer.normalize("074865027137")
        val scanned2 = SyscoUpcNormalizer.normalize("074865027138")
        val scanned3 = SyscoUpcNormalizer.normalize("074865027139")

        val syscoParts = catalogItem.syscoUpc.split(",").map { SyscoUpcNormalizer.normalize(it.trim()) }
        val altParts = catalogItem.alternateBarcodes.split(",").map { SyscoUpcNormalizer.normalize(it.trim()) }

        assertTrue(syscoParts.contains(scanned1))
        assertTrue(syscoParts.contains(scanned2))
        assertTrue(altParts.contains(scanned3))
    }

    @Test
    fun test5DigitPiazzaItemNumberHandling() {
        val csvStr = """
            Piazza Item #, Product, Category, Unit, Quantity
            12044, Piazza Whole Milk, Dairy & Fresh, GAL, 3.0
            40191, Piazza Chicken Tenders, Proteins & Frozen, CS, 12.0
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromCsvString(csvStr)
        assertEquals(2, dtos.size)
        assertEquals("12044", dtos[0].piazzaItemNumber)
        assertEquals("Piazza Whole Milk", dtos[0].name)
        assertEquals("40191", dtos[1].piazzaItemNumber)
        assertEquals("Piazza Chicken Tenders", dtos[1].name)

        val jsonStr = """
            [
                {
                    "piazzaItemNumber": "12044",
                    "name": "Piazza Whole Milk",
                    "category": "Dairy & Fresh",
                    "defaultShelfLifeDays": 7,
                    "unit": "GAL",
                    "lastOnHandAmount": 3.0
                },
                {
                    "Piazza Item #": "40191",
                    "Product": "Piazza Chicken Tenders",
                    "Category": "Proteins & Frozen",
                    "defaultShelfLifeDays": 14,
                    "unit": "CS",
                    "quantity": 12.0
                }
            ]
        """.trimIndent()

        val jsonDtos = DeliveryRepository.parseCatalogDtosFromRawString(jsonStr)
        assertEquals(2, jsonDtos.size)
        assertEquals("12044", jsonDtos[0].piazzaItemNumber)
        assertEquals("40191", jsonDtos[1].piazzaItemNumber)

        val catalogItem1 = CatalogItem(
            syscoUpc = "000000012044",
            name = "Piazza Whole Milk",
            category = "Dairy & Fresh",
            defaultShelfLifeDays = 7,
            unit = "GAL",
            piazzaItemNumber = "12044"
        )
        val product1 = catalogItem1.toSyscoProduct()
        assertEquals("12044", product1.piazzaItemNumber)
        assertEquals("000000012044", product1.upc)

        val catalogItem2 = CatalogItem(
            syscoUpc = "000000040191",
            name = "Piazza Chicken Tenders",
            category = "Proteins & Frozen",
            defaultShelfLifeDays = 14,
            unit = "CS",
            piazzaItemNumber = "40191"
        )
        val product2 = catalogItem2.toSyscoProduct()
        assertEquals("40191", product2.piazzaItemNumber)
        assertEquals("000000040191", product2.upc)
    }

    @Test
    fun testAuditScanRecordFlag() {
        val normalRecord = ScanRecord(
            syscoUpc = "074865123401",
            itemName = "Test Item",
            deliveryDate = "2025-03-10",
            useByDate = "2025-03-15",
            isAudit = false
        )
        assertFalse(normalRecord.isAudit)

        val auditRecord = ScanRecord(
            syscoUpc = "074865123401",
            itemName = "Audit Test Item",
            deliveryDate = "2025-03-10",
            useByDate = "2025-03-15",
            isAudit = true
        )
        assertTrue(auditRecord.isAudit)
    }

    @Test
    fun testMenuColumnsIgnoredForOnHandAndCategoryCsv() {
        val csvStr = """
            UPC, Product, W3, Week 3, On Menu, W1, W2, W4, Recipe, Previous Count, Location
            074861000001, Apples, 10, 20, 30, 40, 50, 60, 70, 5.0, Dry Storage
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromCsvString(csvStr)
        assertEquals(1, dtos.size)
        assertEquals("074861000001", dtos[0].syscoUpc)
        assertEquals("Apples", dtos[0].name)
        assertEquals("Dry Storage", dtos[0].category)
        assertEquals(5.0, dtos[0].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testMenuColumnsIgnoredForOnHandAndCategoryJson() {
        val jsonStr = """
            [
                {
                    "syscoUpc": "074861000002",
                    "name": "Oranges",
                    "W3": 15.0,
                    "Week 3": 25.0,
                    "On Menu": 35.0,
                    "Recipe": "Soup",
                    "previousCount": 7.0,
                    "Location": "Dairy & Fresh"
                }
            ]
        """.trimIndent()

        val dtos = DeliveryRepository.parseCatalogDtosFromRawString(jsonStr)
        assertEquals(1, dtos.size)
        assertEquals("074861000002", dtos[0].syscoUpc)
        assertEquals("Oranges", dtos[0].name)
        assertEquals("Dairy & Fresh", dtos[0].category)
        assertEquals(7.0, dtos[0].lastOnHandAmount!!, 0.001)
    }

    @Test
    fun testCatalogFetchRequestPayload() {
        val request = com.dietaryops.manager.data.remote.CatalogFetchRequest(
            action = "fetch_catalog",
            spreadsheetId = "test-sheet-id",
            sheetTab = "Inventory Raw"
        )
        val gson = com.google.gson.Gson()
        val json = gson.toJson(request)

        assertTrue(json.contains("\"action\":\"fetch_catalog\""))
        assertTrue(json.contains("\"spreadsheetId\":\"test-sheet-id\""))
        assertTrue(json.contains("\"sheetTab\":\"Inventory Raw\""))
    }

    @Test
    fun testStaffAttributionInScanRecordAndDto() {
        val staff = "Terry (TL01)"
        val scanRecord = ScanRecord(
            syscoUpc = "074861000001",
            itemName = "Mayonnaise",
            deliveryDate = "2026-09-24",
            useByDate = "2027-09-24",
            category = "Condiments & Sauces",
            shelfLifeDays = 365,
            receivedBy = staff
        )

        assertEquals("Terry (TL01)", scanRecord.receivedBy)

        val dto = SheetScanRecordDto(
            id = scanRecord.id,
            syscoUpc = scanRecord.syscoUpc,
            itemName = scanRecord.itemName,
            deliveryDate = scanRecord.deliveryDate,
            useByDate = scanRecord.useByDate,
            category = scanRecord.category,
            shelfLifeDays = scanRecord.shelfLifeDays,
            receivedBy = scanRecord.receivedBy
        )

        assertEquals("Terry (TL01)", dto.receivedBy)
    }

    @Test
    fun testCondimentOpenedVsUnopenedDateCalculation() {
        val scanDate = LocalDate.of(2026, 9, 24)
        val category = "Condiments & Sauces"

        // Unopened commercial standard: 365 days
        val unopenedDays = DateCalculator.getShelfLifeDaysForCategory(category)
        assertEquals(365, unopenedDays)
        val unopenedCalc = DateCalculator.calculate(scanDate = scanDate, shelfLifeDays = unopenedDays, category = category)
        assertEquals("2027-09-24", unopenedCalc.useByDateIso)

        // Opened in-use standard: 14 days
        val openedDays = 14
        val openedCalc = DateCalculator.calculate(scanDate = scanDate, shelfLifeDays = openedDays, category = category)
        assertEquals("2026-10-08", openedCalc.useByDateIso)
    }

    @Test
    fun testStaffBadgePayloadParsing() {
        // Direct employee ID
        val adminParsed = com.dietaryops.manager.data.model.StaffUser.parseBadgePayload("AD99")
        assertNotNull(adminParsed)
        assertEquals("AD99", adminParsed!!.second)

        val terryParsed = com.dietaryops.manager.data.model.StaffUser.parseBadgePayload("TL01")
        assertNotNull(terryParsed)
        assertEquals("TL01", terryParsed!!.second)

        // Zebra printed format: COMPANY-EMPID-ROLE
        val zebraTerry = com.dietaryops.manager.data.model.StaffUser.parseBadgePayload("CV-TL01-ADMIN")
        assertNotNull(zebraTerry)
        assertEquals("CV", zebraTerry!!.first)
        assertEquals("TL01", zebraTerry.second)

        val zebraAdmin = com.dietaryops.manager.data.model.StaffUser.parseBadgePayload("DOPS-AD99-SUPER_ADMIN")
        assertNotNull(zebraAdmin)
        assertEquals("DOPS", zebraAdmin!!.first)
        assertEquals("AD99", zebraAdmin.second)

        // DOPS-AUTH:COMPANY:EMPID:TOKEN
        val authQr = com.dietaryops.manager.data.model.StaffUser.parseBadgePayload("DOPS-AUTH:CVILLA:TL01:secretToken123")
        assertNotNull(authQr)
        assertEquals("CVILLA", authQr!!.first)
        assertEquals("TL01", authQr.second)
        assertEquals("secretToken123", authQr.third)
    }

    @Test
    fun testMultiTenantPresetsAndAdminIdentity() {
        assertEquals("AD99", SettingsManager.ADMIN_EMPLOYEE_ID)
        assertEquals("System Administrator", SettingsManager.ADMIN_NAME)

        assertEquals("DOPS", SettingsManager.DEFAULT_COMPANY_CODE)
        assertEquals("DietaryOps Enterprise", SettingsManager.DEFAULT_COMPANY_NAME)

        assertEquals("CVILLA", SettingsManager.CVILLA_COMPANY_CODE)
        assertEquals("Century Villa Healthcare", SettingsManager.CVILLA_COMPANY_NAME)
        assertEquals("16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY", SettingsManager.CVILLA_SHEET_ID)
    }
}

