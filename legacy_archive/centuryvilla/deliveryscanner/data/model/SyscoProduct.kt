package com.centuryvilla.deliveryscanner.data.model

data class SyscoProduct(
    val upc: String,
    val name: String,
    val shelfLifeDays: Int,
    val openedShelfLifeDays: Int = 7,
    val packSize: Int = 1, // Units per case (e.g. 6 for #10 cans)
    val itemNumber: String = "",
    val packInfo: String = "",
    val location: String = "COOLER"
) {
    fun getFormattedPackInfo(): String {
        if (packInfo.isNotBlank()) return packInfo
        return if (packSize > 1) "$packSize Units/CS (EA = Case)" else "1 Unit/CS"
    }
}
