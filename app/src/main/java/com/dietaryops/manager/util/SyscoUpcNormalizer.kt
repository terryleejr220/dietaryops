package com.dietaryops.manager.util

object SyscoUpcNormalizer {

    /**
     * Normalizes any scanned barcode string into a standard 12-digit Sysco UPC format.
     * Handles UPC-A, UPC-E, EAN-13, GTIN-14, Code 128, GS1-128, ITF, etc.
     */
    fun normalize(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""

        val trimmedInput = rawInput.trim()

        // If rawInput starts with "(" or "]C1" or contains parenthesized GS1 AIs, parse extractedUpc
        if (trimmedInput.startsWith("(") || trimmedInput.startsWith("]C1")) {
            val parsed = Gs1BarcodeParser.parseBarcode(trimmedInput)
            if (parsed.extractedUpc.isNotBlank()) {
                return parsed.extractedUpc
            }
        }

        // Strip any non-digit characters for barcode normalization
        val digits = trimmedInput.replace(Regex("[^0-9]"), "")
        if (digits.isEmpty()) return trimmedInput

        return when {
            // GTIN-14 / ITF-14 with 2 leading zeros ("00") or indicator "10" -> 12-digit UPC
            digits.length == 14 && (digits.startsWith("00") || digits.startsWith("10")) -> digits.substring(2)

            // GTIN-14 with 1-digit indicator (e.g. 1-9) -> 12-digit UPC (pos 1..12) or substring(2)
            digits.length == 14 -> {
                val sub = digits.substring(2)
                if (sub.length == 12) sub else digits.take(12)
            }

            // EAN-13 with 1 leading zero -> 12-digit UPC
            digits.length == 13 && digits.startsWith("0") -> digits.substring(1)

            // Standard 12-digit UPC-A
            digits.length == 12 -> digits

            // 11 digits (missing leading zero) -> pad start to 12 digits
            digits.length == 11 -> "0$digits"

            // 10 digits -> pad start to 12 digits
            digits.length == 10 -> "00$digits"

            // 7 digits -> Sysco SUPC (7-digit item number)
            digits.length == 7 -> digits

            // 5 digits -> Piazza item code (5-digit item number)
            digits.length == 5 -> digits

            // Barcode > 12 digits: trim leading zeros if present
            digits.length > 12 -> {
                val trimmedLeading = digits.dropWhile { it == '0' }
                when {
                    trimmedLeading.length == 12 -> trimmedLeading
                    trimmedLeading.length < 12 -> trimmedLeading.padStart(12, '0')
                    else -> trimmedLeading.take(12)
                }
            }

            // Fallback: return raw digits (e.g. 5-digit, 7-digit or custom codes)
            else -> digits
        }
    }
}
