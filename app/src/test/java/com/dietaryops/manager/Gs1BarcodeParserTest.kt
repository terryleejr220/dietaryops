package com.dietaryops.manager

import com.dietaryops.manager.util.Gs1BarcodeParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Gs1BarcodeParserTest {

    @Test
    fun testStandardUpcA() {
        val rawUpc = "074865123401"
        val parsed = Gs1BarcodeParser.parseBarcode(rawUpc)

        assertEquals("074865123401", parsed.rawUpc)
        assertEquals("074865123401", parsed.extractedUpc)
        assertNull(parsed.gtin)
        assertNull(parsed.explicitExpirationDate)
        assertNull(parsed.lotNumber)
        assertNull(parsed.weightLbs)
    }

    @Test
    fun testGtin14WithAi01Parentheses() {
        val rawInput = "(01)10074865123408"
        val parsed = Gs1BarcodeParser.parseBarcode(rawInput)

        assertEquals("(01)10074865123408", parsed.rawUpc)
        assertEquals("10074865123408", parsed.gtin)
        assertEquals("074865123408", parsed.extractedUpc)
        assertNull(parsed.explicitExpirationDate)
        assertNull(parsed.lotNumber)
        assertNull(parsed.weightLbs)
    }

    @Test
    fun testGtin14WithAi01Unformatted() {
        val rawInput = "0110074865123408"
        val parsed = Gs1BarcodeParser.parseBarcode(rawInput)

        assertEquals("0110074865123408", parsed.rawUpc)
        assertEquals("10074865123408", parsed.gtin)
        assertEquals("074865123408", parsed.extractedUpc)
    }

    @Test
    fun testGs1128WithExplicitExpirationDateAi17() {
        val rawInput = "(01)10074865123408(17)260531"
        val parsed = Gs1BarcodeParser.parseBarcode(rawInput)

        assertEquals("10074865123408", parsed.gtin)
        assertEquals("074865123408", parsed.extractedUpc)
        assertNotNull(parsed.explicitExpirationDate)
        assertEquals(LocalDate.of(2026, 5, 31), parsed.explicitExpirationDate)
    }

    @Test
    fun testGs1128WithBestBeforeDateAi15() {
        val rawInput = "(01)10074865123408(15)251231"
        val parsed = Gs1BarcodeParser.parseBarcode(rawInput)

        assertEquals("10074865123408", parsed.gtin)
        assertEquals("074865123408", parsed.extractedUpc)
        assertNotNull(parsed.explicitExpirationDate)
        assertEquals(LocalDate.of(2025, 12, 31), parsed.explicitExpirationDate)
    }

    @Test
    fun testGs1128WithLotNumberAi10() {
        val rawInput = "(01)10074865123408(10)LOT98765A"
        val parsed = Gs1BarcodeParser.parseBarcode(rawInput)

        assertEquals("10074865123408", parsed.gtin)
        assertEquals("074865123408", parsed.extractedUpc)
        assertEquals("LOT98765A", parsed.lotNumber)
    }

    @Test
    fun testGs1128WithCatchWeightAi3102And3202() {
        val rawInput3102 = "(01)10074865123408(3102)001250"
        val parsed3102 = Gs1BarcodeParser.parseBarcode(rawInput3102)

        assertEquals("10074865123408", parsed3102.gtin)
        assertEquals("074865123408", parsed3102.extractedUpc)
        assertNotNull(parsed3102.weightLbs)
        assertEquals(12.50, parsed3102.weightLbs!!, 0.001)

        val rawInput3202 = "(01)10074865123408(3202)002475"
        val parsed3202 = Gs1BarcodeParser.parseBarcode(rawInput3202)

        assertEquals(24.75, parsed3202.weightLbs!!, 0.001)
    }

    @Test
    fun testCombinedGs1128WithParentheses() {
        val rawInput = "(01)10074865123408(17)260531(10)LOT12345(3102)001250"
        val parsed = Gs1BarcodeParser.parseBarcode(rawInput)

        assertEquals("10074865123408", parsed.gtin)
        assertEquals("074865123408", parsed.extractedUpc)
        assertEquals(LocalDate.of(2026, 5, 31), parsed.explicitExpirationDate)
        assertEquals("LOT12345", parsed.lotNumber)
        assertEquals(12.50, parsed.weightLbs!!, 0.001)
    }

    @Test
    fun testCombinedGs1128WithGroupSeparator() {
        val rawInput = "01100748651234081726053110LOT12345\u001D3102001250"
        val parsed = Gs1BarcodeParser.parseBarcode(rawInput)

        assertEquals("10074865123408", parsed.gtin)
        assertEquals("074865123408", parsed.extractedUpc)
        assertEquals(LocalDate.of(2026, 5, 31), parsed.explicitExpirationDate)
        assertEquals("LOT12345", parsed.lotNumber)
        assertEquals(12.50, parsed.weightLbs!!, 0.001)
    }

    @Test
    fun testGs1DateParsingMonthEndZeroDay() {
        val parsedDate = Gs1BarcodeParser.parseYyMmDdDate("260200")
        assertNotNull(parsedDate)
        assertEquals(LocalDate.of(2026, 2, 28), parsedDate)
    }
}
