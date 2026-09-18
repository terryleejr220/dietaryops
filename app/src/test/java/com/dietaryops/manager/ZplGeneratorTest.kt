package com.dietaryops.manager

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ZplGeneratorTest {

    @Test
    fun testExactZplTemplateStructure() {
        val deliveryDate = LocalDate.of(2025, 3, 10)
        val useByDate = LocalDate.of(2025, 3, 15)

        val zpl = ZplGenerator.generateLabel(
            itemName = "Sysco Sliced Roast Beef",
            upc = "074865123401",
            deliveryDate = deliveryDate,
            useByDate = useByDate,
            category = "Meats",
            staffInitials = "CV"
        )

        assertTrue("ZPL must start with ^XA", zpl.startsWith("^XA"))
        assertTrue("ZPL must end with ^XZ", zpl.endsWith("^XZ"))
        assertTrue("ZPL must define width ^PW456", zpl.contains("^PW456"))
        assertTrue("ZPL must define length ^LL254", zpl.contains("^LL254"))
        assertTrue("ZPL must define darkness ^MD15", zpl.contains("^MD15"))
        assertTrue("ZPL must define label home ^LH10,10", zpl.contains("^LH10,10"))
        assertTrue("ZPL must contain item name auto-wrap field block ^FB416,2,0,L,0", zpl.contains("^FB416,2,0,L,0"))
        assertTrue("ZPL must contain item name", zpl.contains("Sysco Sliced Roast Beef"))
        assertTrue("ZPL must contain DELIVERED date", zpl.contains("DELIVERED: 03/10/25"))
        assertTrue("ZPL must contain STORAGE LOC", zpl.contains("LOC: COOLER"))
        assertTrue("ZPL must contain USE BY header", zpl.contains("USE BY:"))
        assertTrue("ZPL must contain USE BY date", zpl.contains("03/15/25"))
        assertTrue("ZPL must contain staff initials BY: CV", zpl.contains("BY: CV"))
        assertTrue("ZPL must contain FIFO rotation notice", zpl.contains("FIFO - ROTATE STOCK"))
        assertTrue("ZPL must contain DIETARY KITCHEN OPS", zpl.contains("DIETARY KITCHEN OPS"))
    }

    @Test
    fun testStaffInitialsCustom() {
        val deliveryDate = LocalDate.of(2025, 3, 10)
        val useByDate = LocalDate.of(2025, 3, 15)

        val zpl = ZplGenerator.generateLabel(
            itemName = "Sysco Liquid Eggs",
            upc = "074865123402",
            deliveryDate = deliveryDate,
            useByDate = useByDate,
            category = "Dairy",
            staffInitials = "JD"
        )

        assertTrue("ZPL must contain custom staff initials BY: JD", zpl.contains("BY: JD"))
    }

    @Test
    fun testCategoryAndStorageLocationMapping() {
        val deliveryDate = LocalDate.of(2025, 3, 10)
        val useByDate = LocalDate.of(2025, 3, 15)

        val coolerZpl = ZplGenerator.generateLabel(
            itemName = "Sysco Sliced Roast Beef",
            upc = "074865123401",
            deliveryDate = deliveryDate,
            useByDate = useByDate,
            category = "Meats"
        )
        assertTrue("Meats category should default to COOLER", coolerZpl.contains("LOC: COOLER"))

        val freezerZpl = ZplGenerator.generateLabel(
            itemName = "Sysco Frozen Peas",
            upc = "074865123499",
            deliveryDate = deliveryDate,
            useByDate = useByDate,
            category = "Dry/Frozen",
            storageLocation = "FREEZER"
        )
        assertTrue("Specified storage location FREEZER should be used", freezerZpl.contains("LOC: FREEZER"))

        val dryZpl = ZplGenerator.generateLabel(
            itemName = "Sysco Flour",
            upc = "074865123488",
            deliveryDate = deliveryDate,
            useByDate = useByDate,
            category = "Bakery"
        )
        assertTrue("Bakery category should map to DRY STORAGE", dryZpl.contains("LOC: DRY STORAGE"))
    }

    @Test
    fun testStorageLocationMappingHelper() {
        assertEquals("COOLER", ZplGenerator.getStorageLocationForCategory("Meats"))
        assertEquals("COOLER", ZplGenerator.getStorageLocationForCategory("Dairy"))
        assertEquals("COOLER", ZplGenerator.getStorageLocationForCategory("Produce"))
        assertEquals("COOLER", ZplGenerator.getStorageLocationForCategory("Prepared"))
        assertEquals("FREEZER", ZplGenerator.getStorageLocationForCategory("Frozen"))
        assertEquals("FREEZER", ZplGenerator.getStorageLocationForCategory("Dry/Frozen"))
        assertEquals("DRY STORAGE", ZplGenerator.getStorageLocationForCategory("Bakery"))
        assertEquals("DRY STORAGE", ZplGenerator.getStorageLocationForCategory("Dry Storage"))
        assertEquals("DRY STORAGE", ZplGenerator.getStorageLocationForCategory("Canned Goods"))
        assertEquals("DRY STORAGE", ZplGenerator.getStorageLocationForCategory("Supplement"))
        assertEquals("DRY STORAGE", ZplGenerator.getStorageLocationForCategory("General"))
    }

    @Test
    fun testSyscoProductOverload() {
        val product = SyscoProduct(
            upc = "074865123401",
            name = "Sysco Sliced Roast Beef",
            shelfLifeDays = 5,
            category = "Meats"
        )
        val deliveryDate = LocalDate.of(2025, 3, 10)

        val zpl = ZplGenerator.generateLabel(product, deliveryDate, staffInitials = "AB")

        assertTrue(zpl.contains("^PW456"))
        assertTrue(zpl.contains("^LL254"))
        assertTrue(zpl.contains("^MD15"))
        assertTrue(zpl.contains("Sysco Sliced Roast Beef"))
        assertTrue(zpl.contains("DELIVERED: 03/10/25"))
        assertTrue(zpl.contains("03/15/25")) // Use by date (+5 days)
        assertTrue(zpl.contains("LOC: COOLER"))
        assertTrue(zpl.contains("BY: AB"))
        assertTrue(zpl.contains("FIFO - ROTATE STOCK"))
    }

    @Test
    fun testShelfLabelZplGeneration() {
        val zpl = ZplGenerator.generateShelfLabelZpl(
            itemName = "Sysco Whole Milk",
            upc = "074864000003",
            storageLocation = "COOLER",
            syscoItemNumber = "123456",
            piazzaItemNumber = "7890",
            parLevel = 10.0,
            lastOnHand = 4.0,
            unit = "GAL"
        )

        assertTrue("Shelf ZPL must start with ^XA", zpl.startsWith("^XA"))
        assertTrue("Shelf ZPL must end with ^XZ", zpl.endsWith("^XZ"))
        assertTrue("Shelf ZPL must contain width ^PW456", zpl.contains("^PW456"))
        assertTrue("Shelf ZPL must contain length ^LL254", zpl.contains("^LL254"))
        assertTrue("Shelf ZPL must contain darkness ^MD15", zpl.contains("^MD15"))
        assertTrue("Shelf ZPL must contain label home ^LH0,0", zpl.contains("^LH0,0"))
        assertTrue("Shelf ZPL must contain product name field block ^FB426,1,0,L,0", zpl.contains("^FB426,1,0,L,0"))
        assertTrue("Shelf ZPL must contain item name Sysco Whole Milk", zpl.contains("Sysco Whole Milk"))
        assertTrue("Shelf ZPL must contain barcode command ^BCN,60,Y,N,N", zpl.contains("^BCN,60,Y,N,N"))
        assertTrue("Shelf ZPL must contain barcode value 074864000003", zpl.contains("074864000003"))
        assertTrue("Shelf ZPL and divider line ^GB426,2,2", zpl.contains("^GB426,2,2"))
        assertTrue("Shelf ZPL must contain Par level with unit", zpl.contains("Par: 10 gal"))
        assertTrue("Shelf ZPL must contain Sec: label", zpl.contains("Sec:"))
        assertTrue("Shelf ZPL must contain storage location COOLER", zpl.contains("COOLER"))
    }

    @Test
    fun testShelfLabelParFormattingWithUnits() {
        val zplCs = ZplGenerator.generateShelfLabelZpl("Tomato Paste", "123456789012", "DRY STORAGE", parLevel = 2.0, unit = "cs")
        assertTrue("Par level must format as 'Par: 2 cs'", zplCs.contains("Par: 2 cs"))

        val zplJug = ZplGenerator.generateShelfLabelZpl("Apple Juice", "123456789012", "COOLER", parLevel = 1.0, unit = "jug")
        assertTrue("Par level must format as 'Par: 1 jug'", zplJug.contains("Par: 1 jug"))

        val zplB = ZplGenerator.generateShelfLabelZpl("Butter Foil", "123456789012", "COOLER", parLevel = 3.0, unit = "b")
        assertTrue("Par level must format as 'Par: 3 b'", zplB.contains("Par: 3 b"))

        val zplEa = ZplGenerator.generateShelfLabelZpl("Large Eggs", "123456789012", "COOLER", parLevel = 12.0, unit = "ea")
        assertTrue("Par level must format as 'Par: 12 ea'", zplEa.contains("Par: 12 ea"))

        val zplEmptyUnit = ZplGenerator.generateShelfLabelZpl("Generic Item", "123456789012", "DRY STORAGE", parLevel = 5.0, unit = "")
        assertTrue("Par level with empty unit must default to 'ea'", zplEmptyUnit.contains("Par: 5 ea"))
    }
}
