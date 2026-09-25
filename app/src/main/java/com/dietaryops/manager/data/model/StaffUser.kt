package com.dietaryops.manager.data.model

data class StaffUser(
    val employeeId: String = "",
    val displayName: String = "",
    val companyCode: String = "",
    val department: String = "Dietary",
    val role: StaffRole = StaffRole.OPERATOR,
    val pin: String = "",
    val badgeToken: String = "",
    val active: Boolean = true
) {
    val initials: String
        get() {
            val parts = displayName.trim().split("\\s+".toRegex())
            return when {
                parts.isEmpty() || displayName.isBlank() -> employeeId.take(2).uppercase()
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "${parts.first().first()}${parts.last().first()}".uppercase()
            }
        }

    fun hasPermission(permission: AppPermission): Boolean {
        if (!active) return false
        if (role == StaffRole.SUPER_ADMIN) return true
        return role.permissions.contains(permission)
    }

    /**
     * Encodes badge data for QR / Barcode scanning:
     * Format: DOPS-AUTH:<companyCode>:<employeeId>:<badgeToken>
     */
    fun toBadgePayload(): String {
        return "DOPS-AUTH:${companyCode.trim().uppercase()}:${employeeId.trim().uppercase()}:${badgeToken.trim()}"
    }

    companion object {
        const val BADGE_PREFIX = "DOPS-AUTH:"

        /**
         * Parses a scanned barcode. Returns Triple(companyCode, employeeId, badgeToken) if valid.
         */
        fun parseBadgePayload(barcode: String): Triple<String, String, String>? {
            val trimmed = barcode.trim()
            if (trimmed.startsWith(BADGE_PREFIX, ignoreCase = true)) {
                val parts = trimmed.substring(BADGE_PREFIX.length).split(":")
                if (parts.size >= 3) {
                    return Triple(
                        parts[0].trim().uppercase(),
                        parts[1].trim().uppercase(),
                        parts[2].trim()
                    )
                } else if (parts.size == 2) {
                    return Triple(
                        parts[0].trim().uppercase(),
                        parts[1].trim().uppercase(),
                        ""
                    )
                }
            }

            // Zebra printed staff badge sticker format: "CV-TL01-ADMIN" or "DOPS-AD99-SUPER_ADMIN"
            if (trimmed.contains("-")) {
                val parts = trimmed.split("-")
                if (parts.size >= 2) {
                    val code = parts[0].trim().uppercase()
                    val empId = parts[1].trim().uppercase()
                    val role = if (parts.size >= 3) parts[2].trim().uppercase() else "OPERATOR"
                    return Triple(code, empId, role)
                }
            }

            // Direct Employee ID (e.g. "AD99", "TL01", "AT01", "LS01")
            if (trimmed.matches("^[A-Za-z0-9_]{2,10}$".toRegex())) {
                return Triple("", trimmed.uppercase(), "")
            }

            return null
        }
    }
}
