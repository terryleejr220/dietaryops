package com.dietaryops.manager.util

import com.dietaryops.manager.data.model.ExpirationRule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class DateCalculationResult(
    val deliveryDate: LocalDate,
    val shelfLifeDays: Int,
    val useByDate: LocalDate,
    val deliveryDateIso: String,       // YYYY-MM-DD
    val deliveryDateFormatted: String,  // MM/DD/YYYY
    val useByDateIso: String,          // YYYY-MM-DD
    val useByDateFormatted: String,    // MM/DD/YYYY
    val useByDateShort: String         // MM/dd/yy
)

object DateCalculator {

    val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val US_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    val SHORT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yy")

    // Dietary Ops Manager operational shelf life guidelines defaults (ServSafe standards)
    private val CATEGORY_SHELF_LIFE_MAP = mapOf(
        "Canned Goods" to 365,
        "Dry Storage" to 365,
        "Condiments & Sauces" to 365,
        "Misc Dry & Cereal" to 365,
        "Dairy & Fresh" to 7,
        "Proteins & Frozen" to 14,
        "Supplement" to 365,
        "General" to 365,
        "Dry/Frozen" to 14,
        "Frozen" to 14,
        "Produce" to 7,
        "Meats" to 7,
        "Meat" to 7,
        "Seafood" to 3,
        "Prepared" to 3,
        "Bakery" to 5,
        "Dairy" to 7
    )

    /**
     * Get default ServSafe shelf life days based on storage area or category:
     * Cooler (+7d), Freezer (+14d), Dry Goods (+365d).
     */
    fun getDefaultServSafeDays(category: String, storageArea: String? = null): Int {
        val catLower = category.trim().lowercase()
        val areaLower = (storageArea ?: "").trim().lowercase()

        return when {
            areaLower.contains("freezer") || catLower.contains("frozen") || catLower.contains("freezer") || catLower.contains("ice cream") -> 14
            areaLower.contains("cooler") || catLower.contains("dairy") || catLower.contains("fresh") || catLower.contains("produce") || catLower.contains("meat") || catLower.contains("cooler") || catLower.contains("deli") || catLower.contains("egg") || catLower.contains("milk") || catLower.contains("cheese") || catLower.contains("yogurt") || catLower.contains("butter") || catLower.contains("prepared") || catLower.contains("seafood") -> 7
            else -> 365
        }
    }

    /**
     * Get recommended shelf life days for a given item category according to ServSafe guidelines.
     */
    fun getShelfLifeDaysForCategory(category: String, fallbackDays: Int = 365): Int {
        val mappedDays = CATEGORY_SHELF_LIFE_MAP.entries
            .firstOrNull { it.key.equals(category, ignoreCase = true) }?.value
            ?: fallbackDays.let { if (it > 0) it else getDefaultServSafeDays(category) }
        return clampShelfLifeDays(mappedDays, category)
    }

    /**
     * Get shelf life days from an ExpirationRule, clamped between 1 and 365 days.
     */
    fun getShelfLifeDays(rule: ExpirationRule): Int {
        return clampShelfLifeDays(rule.daysOffset, rule.category)
    }

    /**
     * Ensures shelf life is strictly positive and bounded between 1 and 365 days.
     * If days <= 0 or missing, applies ServSafe defaults (Cooler +7d, Freezer +14d, Dry Goods +365d).
     */
    fun clampShelfLifeDays(days: Int, category: String = "General", storageArea: String? = null): Int {
        if (days <= 0) {
            return getDefaultServSafeDays(category, storageArea)
        }
        return days.coerceIn(1, 365)
    }

    /**
     * Calculate Use-By date given delivery date and shelf life days.
     */
    fun calculateUseByDate(deliveryDate: LocalDate, shelfLifeDays: Int, category: String = "General"): LocalDate {
        val validDays = clampShelfLifeDays(shelfLifeDays, category)
        return deliveryDate.plusDays(validDays.toLong())
    }

    /**
     * Complete date calculation given scanDate (defaults to today) and shelf life days.
     */
    fun calculate(
        scanDate: LocalDate = LocalDate.now(),
        shelfLifeDays: Int = 7,
        category: String = "General"
    ): DateCalculationResult {
        val validShelfLife = clampShelfLifeDays(shelfLifeDays, category)
        val useBy = calculateUseByDate(scanDate, validShelfLife, category)

        return DateCalculationResult(
            deliveryDate = scanDate,
            shelfLifeDays = validShelfLife,
            useByDate = useBy,
            deliveryDateIso = scanDate.format(ISO_FORMATTER),
            deliveryDateFormatted = scanDate.format(US_FORMATTER),
            useByDateIso = useBy.format(ISO_FORMATTER),
            useByDateFormatted = useBy.format(US_FORMATTER),
            useByDateShort = useBy.format(SHORT_FORMATTER)
        )
    }

    /**
     * Complete calculation given scanDate and category name.
     */
    fun calculateForCategory(
        scanDate: LocalDate = LocalDate.now(),
        category: String,
        customDays: Int? = null
    ): DateCalculationResult {
        val days = if (customDays != null && customDays > 0) customDays else getShelfLifeDaysForCategory(category)
        return calculate(scanDate, days, category)
    }

    /**
     * Flexible parser supporting ISO (yyyy-MM-dd), US (MM/dd/yyyy), and Short (MM/dd/yy) formats.
     */
    fun parseDate(dateStr: String): LocalDate? {
        val clean = dateStr.trim()
        if (clean.isBlank()) return null
        val formatters = listOf(ISO_FORMATTER, US_FORMATTER, SHORT_FORMATTER)
        for (fmt in formatters) {
            try {
                return LocalDate.parse(clean, fmt)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    fun formatDateIso(date: LocalDate): String = date.format(ISO_FORMATTER)
    fun formatDateUs(date: LocalDate): String = date.format(US_FORMATTER)
    fun formatDateShort(date: LocalDate): String = date.format(SHORT_FORMATTER)
}
