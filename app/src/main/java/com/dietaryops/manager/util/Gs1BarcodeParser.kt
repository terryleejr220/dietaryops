package com.dietaryops.manager.util

import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToLong

data class ParsedBarcodeData(
    val rawUpc: String,
    val extractedUpc: String,
    val gtin: String? = null,
    val explicitExpirationDate: LocalDate? = null,
    val lotNumber: String? = null,
    val weightLbs: Double? = null
)

object Gs1BarcodeParser {

    /**
     * Parses any raw barcode string (UPC-A, GTIN-14, GS1-128) and extracts GS1 Application Identifiers.
     */
    fun parseBarcode(rawValue: String?): ParsedBarcodeData {
        if (rawValue.isNullOrBlank()) {
            return ParsedBarcodeData(rawUpc = "", extractedUpc = "")
        }

        val rawInput = rawValue.trim()
        var gtin: String? = null
        var explicitExpirationDate: LocalDate? = null
        var lotNumber: String? = null
        var weightLbs: Double? = null

        // Strip leading Symbology Identifiers if present (e.g., "]C1", "]e0", "]d2")
        val cleanInput = if (rawInput.startsWith("]") && rawInput.length >= 3) {
            rawInput.substring(3)
        } else {
            rawInput
        }

        // Check if barcode uses parenthesized AI format like "(01)10074865123408(17)260531"
        if (cleanInput.contains("(") && cleanInput.contains(")")) {
            val regex = Regex("""\(([0-9]{2,4})\)([^()]*)""")
            val matches = regex.findAll(cleanInput)
            for (match in matches) {
                val ai = match.groupValues[1]
                val valStr = match.groupValues[2].trim().replace("\u001D", "")
                when (ai) {
                    "01", "02" -> if (valStr.length >= 14) gtin = valStr.take(14)
                    "17", "15" -> if (explicitExpirationDate == null) {
                        parseYyMmDdDate(valStr.take(6))?.let { explicitExpirationDate = it }
                    }
                    "10" -> lotNumber = valStr
                    "3102", "3202" -> parseWeight(valStr.take(6))?.let { weightLbs = it }
                    else -> {
                        if (ai.startsWith("310") || ai.startsWith("320")) {
                            val decimals = ai.takeLast(1).toIntOrNull() ?: 2
                            parseWeightGeneric(valStr.take(6), decimals)?.let { weightLbs = it }
                        }
                    }
                }
            }
        } else {
            // Unformatted or GS / FNC1 separated GS1 data stream
            parseUnformattedGs1(cleanInput) { ai, value ->
                when (ai) {
                    "01", "02" -> if (gtin == null && value.length >= 14) gtin = value.take(14)
                    "17", "15" -> if (explicitExpirationDate == null) {
                        parseYyMmDdDate(value.take(6))?.let { explicitExpirationDate = it }
                    }
                    "10" -> if (lotNumber == null) lotNumber = value
                    "3102", "3202" -> if (weightLbs == null) parseWeight(value.take(6))?.let { weightLbs = it }
                    else -> {
                        if ((ai.startsWith("310") || ai.startsWith("320")) && weightLbs == null) {
                            val decimals = ai.takeLast(1).toIntOrNull() ?: 2
                            parseWeightGeneric(value.take(6), decimals)?.let { weightLbs = it }
                        }
                    }
                }
            }
        }

        // If no explicit GTIN AI (01/02) was found, but the input is a pure 14-digit GTIN
        val pureDigits = cleanInput.replace(Regex("[^0-9]"), "")
        if (gtin == null && pureDigits.length == 14) {
            gtin = pureDigits
        }

        // Determine extractedUpc (normalized 12-digit UPC)
        val extractedUpc = if (gtin != null) {
            SyscoUpcNormalizer.normalize(gtin)
        } else {
            SyscoUpcNormalizer.normalize(rawInput)
        }

        return ParsedBarcodeData(
            rawUpc = rawInput,
            extractedUpc = extractedUpc,
            gtin = gtin,
            explicitExpirationDate = explicitExpirationDate,
            lotNumber = lotNumber,
            weightLbs = weightLbs
        )
    }

    private fun parseUnformattedGs1(input: String, onAiFound: (ai: String, value: String) -> Unit) {
        var pos = 0
        val len = input.length

        while (pos < len) {
            // Skip GS character or control chars
            if (input[pos] == '\u001D' || input[pos] == '\u001E' || input[pos] == '\u0000') {
                pos++
                continue
            }

            // Try matching AIs by prefix length (4 digits, 3 digits, 2 digits)
            var matched = false

            // 4-digit AIs (e.g., 3102, 3202, 8005)
            if (pos + 4 <= len) {
                val candidate4 = input.substring(pos, pos + 4)
                if (candidate4.startsWith("310") || candidate4.startsWith("320")) {
                    val dataStart = pos + 4
                    val dataEnd = (dataStart + 6).coerceAtMost(len)
                    val value = input.substring(dataStart, dataEnd)
                    onAiFound(candidate4, value)
                    pos = dataEnd
                    matched = true
                }
            }

            if (!matched && pos + 2 <= len) {
                when (val candidate2 = input.substring(pos, pos + 2)) {
                    "01", "02" -> {
                        val dataStart = pos + 2
                        val dataEnd = (dataStart + 14).coerceAtMost(len)
                        val value = input.substring(dataStart, dataEnd)
                        onAiFound(candidate2, value)
                        pos = dataEnd
                        matched = true
                    }
                    "17", "15", "11", "12", "13" -> {
                        val dataStart = pos + 2
                        val dataEnd = (dataStart + 6).coerceAtMost(len)
                        val value = input.substring(dataStart, dataEnd)
                        onAiFound(candidate2, value)
                        pos = dataEnd
                        matched = true
                    }
                    "10", "21" -> {
                        // Variable length data up to 20 chars, terminated by \u001D or end of string
                        val dataStart = pos + 2
                        var dataEnd = input.indexOf('\u001D', dataStart)
                        if (dataEnd == -1) dataEnd = input.indexOf('\u001E', dataStart)
                        if (dataEnd == -1) dataEnd = (dataStart + 20).coerceAtMost(len)
                        val value = input.substring(dataStart, dataEnd)
                        onAiFound(candidate2, value)
                        pos = dataEnd
                        matched = true
                    }
                    "30", "37" -> {
                        // Variable length numeric up to 8 digits
                        val dataStart = pos + 2
                        var dataEnd = dataStart
                        while (dataEnd < len && dataEnd < dataStart + 8 && input[dataEnd].isDigit()) {
                            dataEnd++
                        }
                        val value = input.substring(dataStart, dataEnd)
                        onAiFound(candidate2, value)
                        pos = dataEnd
                        matched = true
                    }
                }
            }

            if (!matched) {
                // Advance one character if no recognized AI prefix matched
                pos++
            }
        }
    }

    /**
     * Parses YYMMDD string into a valid LocalDate.
     * Special GS1 handling: DD="00" represents the last day of month MM.
     */
    fun parseYyMmDdDate(dateStr: String): LocalDate? {
        val digits = dateStr.replace(Regex("[^0-9]"), "")
        if (digits.length != 6) return null

        try {
            val yy = digits.substring(0, 2).toInt()
            val mm = digits.substring(2, 4).toInt()
            val dd = digits.substring(4, 6).toInt()

            if (mm !in 1..12) return null

            val currentYear2Digit = LocalDate.now().year % 100
            val year = if (yy <= currentYear2Digit + 50) 2000 + yy else 1900 + yy

            val lastDayOfMonth = LocalDate.of(year, mm, 1).lengthOfMonth()
            val day = if (dd == 0) {
                lastDayOfMonth
            } else {
                dd.coerceIn(1, lastDayOfMonth)
            }

            return LocalDate.of(year, mm, day)
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseWeight(weightStr: String): Double? {
        return parseWeightGeneric(weightStr, 2)
    }

    private fun parseWeightGeneric(weightStr: String, decimals: Int): Double? {
        val digits = weightStr.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return null
        return try {
            val value = digits.toDouble()
            val divisor = 10.0.pow(decimals)
            val result = value / divisor
            (result * 100.0).roundToLong() / 100.0
        } catch (_: Exception) {
            null
        }
    }
}
