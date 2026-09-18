package com.dietaryops.manager.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var webAppUrl: String
        get() = prefs.getString(KEY_WEB_APP_URL, DEFAULT_WEB_APP_URL) ?: DEFAULT_WEB_APP_URL
        set(value) = prefs.edit().putString(KEY_WEB_APP_URL, value.trim()).apply()

    var sheetId: String
        get() = prefs.getString(KEY_SHEET_ID, DEFAULT_SHEET_ID) ?: DEFAULT_SHEET_ID
        set(value) = prefs.edit().putString(KEY_SHEET_ID, value.trim()).apply()

    var publishedWebUrl: String
        get() = prefs.getString(KEY_PUBLISHED_WEB_URL, DEFAULT_PUBLISHED_WEB_URL) ?: DEFAULT_PUBLISHED_WEB_URL
        set(value) = prefs.edit().putString(KEY_PUBLISHED_WEB_URL, value.trim()).apply()

    var autoPrint: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PRINT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PRINT, value).apply()

    private fun isEvsDepartment(): Boolean {
        return department.equals("Environmental Services", ignoreCase = true) || 
               department.equals("EVS", ignoreCase = true)
    }

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

    var staffName: String
        get() = prefs.getString(KEY_STAFF_NAME, "Terry") ?: "Terry"
        set(value) = prefs.edit().putString(KEY_STAFF_NAME, value.trim()).apply()

    var staffInitials: String
        get() = prefs.getString(KEY_STAFF_INITIALS, "DO") ?: "DO"
        set(value) = prefs.edit().putString(KEY_STAFF_INITIALS, value.trim()).apply()

    var companyCode: String
        get() = prefs.getString(KEY_COMPANY_CODE, "DOPS") ?: "DOPS"
        set(value) = prefs.edit().putString(KEY_COMPANY_CODE, value.trim().uppercase()).apply()

    var companyName: String
        get() = prefs.getString(KEY_COMPANY_NAME, "DietaryOps Enterprise") ?: "DietaryOps Enterprise"
        set(value) = prefs.edit().putString(KEY_COMPANY_NAME, value.trim()).apply()

    var department: String
        get() = prefs.getString(KEY_DEPARTMENT, "Dietary") ?: "Dietary"
        set(value) = prefs.edit().putString(KEY_DEPARTMENT, value.trim()).apply()

    var employeeId: String
        get() = prefs.getString(KEY_EMPLOYEE_ID, "TL01") ?: "TL01"
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_ID, value.trim().uppercase()).apply()

    var staffRole: String
        get() = prefs.getString(KEY_STAFF_ROLE, "ADMIN") ?: "ADMIN"
        set(value) = prefs.edit().putString(KEY_STAFF_ROLE, value.trim().uppercase()).apply()

    var departmentSheetTab: String
        get() = prefs.getString(KEY_DEPARTMENT_SHEET_TAB, "Delivery Log") ?: "Delivery Log"
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

        const val DEFAULT_WEB_APP_URL = "https://script.google.com/macros/s/AKfycbx0heDYU0f1XyDELM_DFuKdlKmFW_ZJD6cEGegpLHva19PLv-_2CBE_U2EmAuJt1_FxDg/exec"
        const val DEFAULT_SHEET_ID = "16dLMDsfBFH_qAcLk_ex9WVxW86LE5Uggsczn5arTgBY"
        const val DEFAULT_PUBLISHED_WEB_URL = "https://docs.google.com/spreadsheets/d/e/2PACX-1vShGiN4lxHStYAK_WegMQz_h4vyr602p0cSPY541GxaHbvMMbLyDx70G2sNlhK3_Ab9cjGB9LGoKJh_/pubhtml?gid=1819479005&single=true"

        // Standard Operational Default Shelf Life Guidelines
        const val SHELF_LIFE_DAIRY_FRESH = 7                 // Cooler / TCS / Fresh (dairy, sliced meats, fresh produce)
        const val SHELF_LIFE_PROTEINS_FROZEN = 14            // Freezer / Frozen raw or prepared proteins
        const val SHELF_LIFE_COMMERCIAL_SHELF_STABLE = 365   // Commercial Shelf-Stable / Unopened (canned goods, dry storage, condiments, misc dry, supplement, general)

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
