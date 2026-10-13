package com.dietaryops.manager.data.model

enum class AppPermission {
    PREP_LABELS,           // Print prep & expiration labels
    RECEIVING_SCAN,        // Scan barcodes & parse delivery items
    VIEW_INVENTORY,        // View inventory scan logs & stock counts
    SHELF_LABELS,          // Print shelf & bin labels
    EDIT_COUNTS,           // Update inventory stock counts
    EDIT_ITEMS,            // Add/edit items in master catalog & expiration rules
    PRINTER_SETTINGS,      // Pair/configure Bluetooth Zebra printers & auto-print
    BASIC_ADMIN_SETTINGS,  // Manage department, staff profile, & general preferences
    ADVANCED_SETTINGS,     // Advanced audit mode, local database rules, & diagnostics (No backend edits)
    BACKEND_SETTINGS       // Full access to backend Google Sheets IDs, Web App URLs, & Cloud config
}

enum class StaffRole(val displayName: String, val permissions: Set<AppPermission>) {
    AIDE(
        displayName = "Aide",
        permissions = setOf(
            AppPermission.PREP_LABELS,
            AppPermission.RECEIVING_SCAN
        )
    ),
    COOK(
        displayName = "Cook",
        permissions = setOf(
            AppPermission.PREP_LABELS,
            AppPermission.RECEIVING_SCAN,
            AppPermission.VIEW_INVENTORY
        )
    ),
    RECEIVER(
        displayName = "Receiver",
        permissions = setOf(
            AppPermission.PREP_LABELS,
            AppPermission.RECEIVING_SCAN,
            AppPermission.VIEW_INVENTORY,
            AppPermission.SHELF_LABELS,
            AppPermission.EDIT_COUNTS,
            AppPermission.EDIT_ITEMS
        )
    ),
    MANAGER(
        displayName = "Manager",
        permissions = setOf(
            AppPermission.PREP_LABELS,
            AppPermission.RECEIVING_SCAN,
            AppPermission.VIEW_INVENTORY,
            AppPermission.SHELF_LABELS,
            AppPermission.EDIT_COUNTS,
            AppPermission.EDIT_ITEMS,
            AppPermission.PRINTER_SETTINGS,
            AppPermission.BASIC_ADMIN_SETTINGS
        )
    ),
    ADMIN(
        displayName = "Admin",
        permissions = setOf(
            AppPermission.PREP_LABELS,
            AppPermission.RECEIVING_SCAN,
            AppPermission.VIEW_INVENTORY,
            AppPermission.SHELF_LABELS,
            AppPermission.EDIT_COUNTS,
            AppPermission.EDIT_ITEMS,
            AppPermission.PRINTER_SETTINGS,
            AppPermission.BASIC_ADMIN_SETTINGS,
            AppPermission.ADVANCED_SETTINGS
        )
    ),
    SUPER_ADMIN(
        displayName = "Super Admin",
        permissions = AppPermission.values().toSet()
    );

    companion object {
        fun fromString(role: String?): StaffRole {
            return when (role?.trim()?.uppercase()) {
                "SUPER_ADMIN", "SUPER ADMIN", "SUPERADMIN" -> SUPER_ADMIN
                "ADMIN", "ADMINISTRATOR" -> ADMIN
                "MANAGER", "DEPT_ADMIN", "SUPERVISOR", "LEAD" -> MANAGER
                "RECEIVER", "RECEIVING" -> RECEIVER
                "COOK", "CHEF" -> COOK
                "AIDE", "OPERATOR", "STAFF" -> AIDE
                else -> AIDE
            }
        }
    }
}
