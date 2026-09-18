package com.dietaryops.manager.data.model

enum class AppPermission {
    RECEIVE_SCAN,       // Scan barcodes & parse delivery items
    PRINT_ZEBRA,        // Print 2x1 Zebra ZPL labels
    VIEW_LOGS,          // View inventory scan logs
    EDIT_CATALOG,       // Add/edit items in master catalog & shelf life rules
    EXPORT_SHEETS,      // Trigger manual sync or export to Google Sheets
    SETTINGS_ACCESS,    // Change printer, audit mode, and device configurations
    SYSTEM_CONFIG,      // Change backend spreadsheet URLs and Company details (Super Admin only)
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
    DEPT_ADMIN(
        displayName = "Department Admin",
        permissions = setOf(
            AppPermission.RECEIVE_SCAN,
            AppPermission.PRINT_ZEBRA,
            AppPermission.VIEW_LOGS,
            AppPermission.EDIT_CATALOG,
            AppPermission.EXPORT_SHEETS,
            AppPermission.SETTINGS_ACCESS
        )
    ),
    SUPER_ADMIN(
        displayName = "Super Admin",
        permissions = AppPermission.values().toSet()
    );

    companion object {
        fun fromString(role: String?): StaffRole {
            return when (role?.trim()?.uppercase()) {
                "SUPER_ADMIN", "SUPER ADMIN", "MAIN_ADMIN" -> SUPER_ADMIN
                "ADMIN", "ADMINISTRATOR", "DEPT_ADMIN" -> DEPT_ADMIN
                "SUPERVISOR", "LEAD", "MANAGER" -> SUPERVISOR
                else -> OPERATOR
            }
        }
    }
}
