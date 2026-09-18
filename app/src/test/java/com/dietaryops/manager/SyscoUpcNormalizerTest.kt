package com.dietaryops.manager

import com.dietaryops.manager.util.SyscoUpcNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SyscoUpcNormalizerTest {

    @Test
    fun testStandardUpcA() {
        val input = "074865123401"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(input))
    }

    @Test
    fun testEan13WithLeadingZero() {
        val input = "0074865123401"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(input))
    }

    @Test
    fun testItf14WithTwoLeadingZeros() {
        val input = "00074865123401"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(input))
    }

    @Test
    fun testGtin14Itf14StartingWith10And00() {
        val input10 = "10074865123401"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(input10))

        val input00 = "00074865123401"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(input00))
    }

    @Test
    fun testShortUpcPadding() {
        val input11 = "74865123401"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(input11))

        val input10 = "4865123401"
        assertEquals("004865123401", SyscoUpcNormalizer.normalize(input10))
    }

    @Test
    fun testNonDigitFiltering() {
        val inputWithSpaces = "  074865123401 \t"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(inputWithSpaces))

        val inputWithChars = "A-074865123401-B"
        assertEquals("074865123401", SyscoUpcNormalizer.normalize(inputWithChars))
    }

    @Test
    fun testPiazzaAndSyscoItemCodes() {
        // 5-digit Piazza code
        assertEquals("12345", SyscoUpcNormalizer.normalize("12345"))
        // 7-digit Sysco SUPC
        assertEquals("1234567", SyscoUpcNormalizer.normalize("1234567"))
    }

    @Test
    fun testBlankOrNullInput() {
        assertEquals("", SyscoUpcNormalizer.normalize(null))
        assertEquals("", SyscoUpcNormalizer.normalize("   "))
    }
}
