package com.dietaryops.manager.data.model

data class CompanyProfile(
    val companyId: String = "",
    val name: String = "",
    val code: String = "",
    val spreadsheetId: String = "",
    val webAppUrl: String = "",
    val departments: List<String> = listOf("Dietary", "Housekeeping", "General"),
    val departmentTabs: Map<String, String> = mapOf("Dietary" to "Delivery Log"),
    val active: Boolean = true
) {
    fun getSheetTabForDepartment(department: String): String {
        return departmentTabs[department] ?: departmentTabs["Dietary"] ?: "Delivery Log"
    }
}
