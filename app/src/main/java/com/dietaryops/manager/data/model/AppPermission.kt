package com.dietaryops.manager.data.model

enum class AppPermission {
    RECEIVE_SCAN,       // Scan barcodes & parse delivery items
    PRINT_ZEBRA,        // Print 2x1 Zebra ZPL labels
    VIEW_LOGS,          // View inventory scan logs
    EDIT_CATALOG,       // Add/edit items in master catalog & shelf life rules
    EXPORT_SHEETS,      // Trigger manual sync or export to Google Sheets
    SETTINGS_ACCESS,    // Change printer, audit mode, and device configurations
    ADMIN_ALL           // Full administrative bypass
}

enum class StaffRole(val displayName: String, val permissions: Set<AppPermission>) {
    OPERATOR(
        displayName = "Operator",
        permissions = setOf(
            AppPermission.RECEIVE_SCAN,
            AppPermission.PRINT_ZEBRA,
            AppPermission.VIEW_LOGS
        )
    ),
    SUPERVISOR(
        displayName = "Supervisor",
        permissions = setOf(
            AppPermission.RECEIVE_SCAN,
            AppPermission.PRINT_ZEBRA,
            AppPermission.VIEW_LOGS,
            AppPermission.EDIT_CATALOG,
            AppPermission.EXPORT_SHEETS
        )
    ),
    ADMIN(
        displayName = "Administrator",
        permissions = AppPermission.values().toSet()
    );

    companion object {
        fun fromString(role: String?): StaffRole {
            return when (role?.trim()?.uppercase()) {
                "ADMIN", "ADMINISTRATOR" -> ADMIN
                "SUPERVISOR", "LEAD", "MANAGER" -> SUPERVISOR
                else -> OPERATOR
            }
        }
    }
}
