package com.dietaryops.manager

import com.dietaryops.manager.util.DateCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateCalculatorTest {

    @Test
    fun testDefaultCategoryShelfLifeMapping() {
        assertEquals(365, DateCalculator.getShelfLifeDaysForCategory("Canned Goods"))
        assertEquals(365, DateCalculator.getShelfLifeDaysForCategory("Dry Storage"))
        assertEquals(365, DateCalculator.getShelfLifeDaysForCategory("Condiments & Sauces"))
        assertEquals(365, DateCalculator.getShelfLifeDaysForCategory("Misc Dry & Cereal"))
        assertEquals(7, DateCalculator.getShelfLifeDaysForCategory("Dairy & Fresh"))
        assertEquals(14, DateCalculator.getShelfLifeDaysForCategory("Proteins & Frozen"))
        assertEquals(365, DateCalculator.getShelfLifeDaysForCategory("Supplement"))
        assertEquals(365, DateCalculator.getShelfLifeDaysForCategory("General"))
        assertEquals(365, DateCalculator.getShelfLifeDaysForCategory("UnknownCategory"))
    }

    @Test
    fun testClampShelfLifeDays() {
        // Less than 1 or 0 should apply ServSafe category defaults (Cooler +7, Freezer +14, Dry Goods/General +365)
        assertEquals(365, DateCalculator.clampShelfLifeDays(0, "General"))
        assertEquals(7, DateCalculator.clampShelfLifeDays(0, "Dairy & Fresh"))
        assertEquals(14, DateCalculator.clampShelfLifeDays(-5, "Proteins & Frozen"))

        // Between 1 and 365 should remain unchanged
        assertEquals(1, DateCalculator.clampShelfLifeDays(1))
        assertEquals(7, DateCalculator.clampShelfLifeDays(7))
        assertEquals(14, DateCalculator.clampShelfLifeDays(14))
        assertEquals(365, DateCalculator.clampShelfLifeDays(365))

        // Greater than 365 should be clamped to 365
        assertEquals(365, DateCalculator.clampShelfLifeDays(400))
    }

    @Test
    fun testCalculateUseByDate() {
        val deliveryDate = LocalDate.of(2025, 3, 1)
        val useBy5 = DateCalculator.calculateUseByDate(deliveryDate, 5)
        assertEquals(LocalDate.of(2025, 3, 6), useBy5)

        val useBy14 = DateCalculator.calculateUseByDate(deliveryDate, 14)
        assertEquals(LocalDate.of(2025, 3, 15), useBy14)
    }

    @Test
    fun testCalculateResultFormatting() {
        val deliveryDate = LocalDate.of(2025, 3, 10)
        val result = DateCalculator.calculate(scanDate = deliveryDate, shelfLifeDays = 7)

        assertEquals("2025-03-10", result.deliveryDateIso)
        assertEquals("03/10/2025", result.deliveryDateFormatted)

        assertEquals("2025-03-17", result.useByDateIso)
        assertEquals("03/17/2025", result.useByDateFormatted)
        assertEquals("03/17/25", result.useByDateShort)
    }

    @Test
    fun testParseDateFormats() {
        val isoParsed = DateCalculator.parseDate("2025-05-20")
        assertNotNull(isoParsed)
        assertEquals(LocalDate.of(2025, 5, 20), isoParsed)

        val usParsed = DateCalculator.parseDate("05/20/2025")
        assertNotNull(usParsed)
        assertEquals(LocalDate.of(2025, 5, 20), usParsed)

        val shortParsed = DateCalculator.parseDate("05/20/25")
        assertNotNull(shortParsed)
        assertEquals(LocalDate.of(2025, 5, 20), shortParsed)

        assertNull(DateCalculator.parseDate("invalid-date"))
        assertNull(DateCalculator.parseDate(""))
    }
}
