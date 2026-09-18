package com.dietaryops.manager.util

import java.util.Locale

/**
 * Converts ALL-CAPS or mixed case product names and categories (e.g., "GREEN BEANS CUT")
 * into clean Title Case ("Green Beans Cut").
 */
fun String.toTitleCase(): String {
    if (isBlank()) return this
    val trimmed = this.trim()

    // Words that should stay lowercase unless at the start or end of the string
    val minorWords = setOf(
        "a", "an", "and", "as", "at", "but", "by", "for", "in", "nor", "of", "on", "or", "so", "the", "to", "up", "yet", "v", "vs", "via", "with", "w/"
    )

    // Preserve standard unit/code abbreviations in uppercase
    val upperAbbreviations = setOf("UPC", "GTIN", "CS", "LB", "GAL", "EA", "CTN", "BOX", "SUPC")

    val tokens = trimmed.split(Regex("\\s+"))
    val result = tokens.mapIndexed { index, token ->
        if (token.isEmpty()) ""
        else {
            val upperToken = token.uppercase(Locale.ROOT)
            val cleanLower = token.lowercase(Locale.ROOT)

            if (upperAbbreviations.contains(upperToken)) {
                upperToken
            } else if (index > 0 && index < tokens.size - 1 && minorWords.contains(cleanLower)) {
                cleanLower
            } else {
                cleanLower.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }
    }

    return result.joinToString(" ")
}
