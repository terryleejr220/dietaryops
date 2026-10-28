package com.dietaryops.manager.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var webAppUrl: String
        get() = prefs.getString(KEY_WEB_APP_URL, DEFAULT_WEB_APP_URL) ?: DEFAULT_WEB_APP_URL
        set(value) = prefs.edit().putString(KEY_WEB_APP_URL, value.trim()).apply()

    var sheetId: String
        get() = prefs.getString(KEY_SHEET_ID, DEFAULT_SHEET_ID) ?: DEFAULT_SHEET_ID
        set(value) = prefs.edit().putString(KEY_SHEET_ID, value.trim()).apply()

    var publishedWebUrl: String
        get() = prefs.getString(KEY_PUBLISHED_WEB_URL, DEFAULT_PUBLISHED_WEB_URL)
            ?: DEFAULT_PUBLISHED_WEB_URL
        set(value) = prefs.edit().putString(KEY_PUBLISHED_WEB_URL, value.trim()).apply()

    var liveDashboardUrl: String
        get() = prefs.getString(KEY_LIVE_DASHBOARD_URL, DEFAULT_LIVE_DASHBOARD_URL)
            ?: DEFAULT_LIVE_DASHBOARD_URL
        set(value) = prefs.edit().putString(KEY_LIVE_DASHBOARD_URL, value.trim()).apply()

    var kitchenDashboardUrl: String
        get() = prefs.getString(KEY_KITCHEN_DASHBOARD_URL, DEFAULT_KITCHEN_DASHBOARD_URL)
            ?: DEFAULT_KITCHEN_DASHBOARD_URL
        set(value) = prefs.edit().putString(KEY_KITCHEN_DASHBOARD_URL, value.trim()).apply()

    var autoPrint: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PRINT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PRINT, value).apply()

    fun isEvsDepartment(): Boolean {
        return department.equals("Environmental Services", ignoreCase = true) ||
                department.equals("EVS", ignoreCase = true)
    }

    val defaultTabForCurrentDepartment: String
        get() = if (isEvsDepartment()) "EVS Scan Log" else "Delivery Scan Log"

    val categories: List<String>
        get() = if (isEvsDepartment()) EVS_CATEGORIES else DEFAULT_CATEGORIES

    var preferredPrinterAddress: String?
        get() = prefs.getString(KEY_PREFERRED_PRINTER_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_PREFERRED_PRINTER_ADDRESS, value).apply()

    var isAuditMode: Boolean
        get() = prefs.getBoolean(KEY_AUDIT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIT_MODE, value).apply()

    var defaultLabelQuantity: Int
        get() = prefs.getInt(KEY_DEFAULT_LABEL_QUANTITY, 1)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_LABEL_QUANTITY, value.coerceAtLeast(1)).apply()

    // Operational Shift Staff Profile (e.g. Terry Little Jr.)
    var staffName: String
        get() = prefs.getString(KEY_STAFF_NAME, "Terry Little Jr.") ?: "Terry Little Jr."
        set(value) = prefs.edit().putString(KEY_STAFF_NAME, value.trim()).apply()

    var staffInitials: String
        get() = prefs.getString(KEY_STAFF_INITIALS, "TL") ?: "TL"
        set(value) = prefs.edit().putString(KEY_STAFF_INITIALS, value.trim()).apply()

    var employeeId: String
        get() = prefs.getString(KEY_EMPLOYEE_ID, "TL01") ?: "TL01"
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_ID, value.trim().uppercase()).apply()

    var staffRole: String
        get() = prefs.getString(KEY_STAFF_ROLE, "RECEIVER") ?: "RECEIVER"
        set(value) = prefs.edit().putString(KEY_STAFF_ROLE, value.trim().uppercase()).apply()

    // Facility Identity & Sheet Routing
    var companyCode: String
        get() = prefs.getString(KEY_COMPANY_CODE, DEFAULT_COMPANY_CODE) ?: DEFAULT_COMPANY_CODE
        set(value) = prefs.edit().putString(KEY_COMPANY_CODE, value.trim().uppercase()).apply()

    var companyName: String
        get() = prefs.getString(KEY_COMPANY_NAME, DEFAULT_COMPANY_NAME) ?: DEFAULT_COMPANY_NAME
        set(value) = prefs.edit().putString(KEY_COMPANY_NAME, value.trim()).apply()

    var department: String
        get() = prefs.getString(KEY_DEPARTMENT, "Dietary") ?: "Dietary"
        set(value) {
            val clean = value.trim()
            prefs.edit().putString(KEY_DEPARTMENT, clean).apply()
            // Automatically switch sheet tab when department toggles to maintain total isolation
            departmentSheetTab =
                if (clean.equals("Environmental Services", ignoreCase = true) || clean.equals(
                        "EVS",
                        ignoreCase = true
                    )
                ) {
                    "EVS Scan Log"
                } else {
                    "Delivery Scan Log"
                }
        }

    var departmentSheetTab: String
        get() = prefs.getString(KEY_DEPARTMENT_SHEET_TAB, "")
            ?.ifBlank { defaultTabForCurrentDepartment } ?: defaultTabForCurrentDepartment
        set(value) = prefs.edit().putString(KEY_DEPARTMENT_SHEET_TAB, value.trim()).apply()

    var currentStaffUser: com.dietaryops.manager.data.model.StaffUser
        get() = com.dietaryops.manager.data.model.StaffUser(
            employeeId = employeeId,
            displayName = staffName,
            companyCode = companyCode,
            department = department,
            role = com.dietaryops.manager.data.model.StaffRole.fromString(staffRole)
        )
        set(value) {
            employeeId = value.employeeId
            staffName = value.displayName
            staffInitials = value.initials
            companyCode = value.companyCode
            department = value.department
            staffRole = value.role.name
        }

    fun loadCenturyVillaPreset() {
        companyCode = CVILLA_COMPANY_CODE
        companyName = CVILLA_COMPANY_NAME
        sheetId = CVILLA_SHEET_ID
        webAppUrl = DEFAULT_WEB_APP_URL
        publishedWebUrl = DEFAULT_PUBLISHED_WEB_URL
        departmentSheetTab = defaultTabForCurrentDepartment
    }

    fun loadDietaryOpsDefaultPreset() {
        companyCode = DEFAULT_COMPANY_CODE
        companyName = DEFAULT_COMPANY_NAME
        sheetId = DEFAULT_SHEET_ID
        webAppUrl = DEFAULT_WEB_APP_URL
        publishedWebUrl = DEFAULT_PUBLISHED_WEB_URL
        departmentSheetTab = defaultTabForCurrentDepartment
    }

    fun applyFacilitySetupQr(rawQrPayload: String): Boolean {
        val trimmed = rawQrPayload.trim()
        if (trimmed.startsWith("DOPS-SETUP:", ignoreCase = true)) {
            val parts = trimmed.substring("DOPS-SETUP:".length).split(":")
            if (parts.size >= 4) {
                companyCode = parts[0].trim().uppercase()
                companyName = parts[1].trim()
                sheetId = parts[2].trim()
                webAppUrl = parts.subList(3, parts.size).joinToString(":")
                departmentSheetTab = defaultTabForCurrentDepartment
                return true
            }
        }
        return false
    }

    fun openUrl(context: Context, url: String) {
        try {
            val cleanUrl = url.ifBlank { "https://docs.google.com/spreadsheets/d/$sheetId" }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val KEY_COMPANY_CODE = "company_code"
        private const val KEY_COMPANY_NAME = "company_name"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_EMPLOYEE_ID = "employee_id"
        private const val KEY_STAFF_ROLE = "staff_role"
        private const val KEY_DEPARTMENT_SHEET_TAB = "department_sheet_tab"
        private const val KEY_WEB_APP_URL = "web_app_url"
        private const val KEY_SHEET_ID = "sheet_id"
        private const val KEY_PUBLISHED_WEB_URL = "published_web_url"
        private const val KEY_AUTO_PRINT = "auto_print"
        private const val KEY_PREFERRED_PRINTER_ADDRESS = "preferred_printer_address"
        private const val KEY_AUDIT_MODE = "is_audit_mode"
        private const val KEY_DEFAULT_LABEL_QUANTITY = "default_label_quantity"
        private const val KEY_STAFF_NAME = "staff_name"
        private const val KEY_STAFF_INITIALS = "staff_initials"
        private const val KEY_LIVE_DASHBOARD_URL = "live_dashboard_url"
        private const val KEY_KITCHEN_DASHBOARD_URL = "kitchen_dashboard_url"

        const val DEFAULT_COMPANY_CODE = "DOPS"
        const val DEFAULT_COMPANY_NAME = "DietaryOps Enterprise"

        const val CVILLA_COMPANY_CODE = "CVILLA"
        const val CVILLA_COMPANY_NAME = "Century Villa Healthcare"
        const val CVILLA_SHEET_ID = "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY"

        const val ADMIN_EMPLOYEE_ID = "AD99"
        const val ADMIN_NAME = "System Administrator"

        const val DEFAULT_WEB_APP_URL =
            "https://script.google.com/macros/s/AKfycbwvHRDz_7_635MtPnq_ac4gRscE4F_G5N72prO4g4oKheo8kYVUM0XD76jEwoQYHm-H9w/exec"
        const val DEFAULT_SHEET_ID = "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY"
        const val DEFAULT_PUBLISHED_WEB_URL =
            "https://docs.google.com/spreadsheets/d/e/2PACX-1vShGiN4lxHStYAK_WegMQz_h4vyr602p0cSPY541GxaHbvMMbLyDx70G2sNlhK3_Ab9cjGB9LGoKJh_/pubhtml?gid=1819479005"
        const val DEFAULT_LIVE_DASHBOARD_URL =
            "https://docs.google.com/spreadsheets/d/e/2PACX-1vShGiN4lxHStYAK_WegMQz_h4vyr602p0cSPY541GxaHbvMMbLyDx70G2sNlhK3_Ab9cjGB9LGoKJh_/pubhtml?gid=999999"
        const val DEFAULT_KITCHEN_DASHBOARD_URL =
            "https://docs.google.com/spreadsheets/d/e/2PACX-1vShGiN4lxHStYAK_WegMQz_h4vyr602p0cSPY541GxaHbvMMbLyDx70G2sNlhK3_Ab9cjGB9LGoKJh_/pubhtml?gid=777002"

        // Standard Operational Default Shelf Life Guidelines
        const val SHELF_LIFE_DAIRY_FRESH =
            7                 // Cooler / TCS / Fresh (dairy, sliced meats, fresh produce)
        const val SHELF_LIFE_PROTEINS_FROZEN =
            14            // Freezer / Frozen raw or prepared proteins
        const val SHELF_LIFE_COMMERCIAL_SHELF_STABLE =
            365   // Commercial Shelf-Stable / Unopened (canned goods, dry storage, condiments, misc dry, supplement, general)

        val DEFAULT_CATEGORIES = listOf(
            "Canned Goods",
            "Dry Storage",
            "Condiments & Sauces",
            "Misc Dry & Cereal",
            "Dairy & Fresh",
            "Proteins & Frozen",
            "Supplement",
            "General"
        )

        val EVS_CATEGORIES = listOf(
            "Gloves & PPE",
            "Incontinence (Depends)",
            "Chemicals & Cleaners",
            "Paper Goods",
            "Liners & Trash Bags",
            "Equipment",
            "General"
        )
    }
}
