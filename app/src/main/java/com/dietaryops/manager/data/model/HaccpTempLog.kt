package com.dietaryops.manager.data.model

import java.util.UUID

data class HaccpTempLog(
    val id: String = UUID.randomUUID().toString(),
    val itemOrStationName: String,
    val stationType: StationType = StationType.STEAM_TABLE_HOT,
    val recordedTempF: Double,
    val targetMinTempF: Double = stationType.defaultTargetMin,
    val targetMaxTempF: Double = stationType.defaultTargetMax,
    val isPass: Boolean = recordedTempF in targetMinTempF..targetMaxTempF,
    val staffInitials: String = "DO",
    val timestamp: Long = System.currentTimeMillis()
)

enum class StationType(
    val displayName: String,
    val defaultTargetMin: Double,
    val defaultTargetMax: Double
) {
    STEAM_TABLE_HOT("Steam Table (Hot Food)", 140.0, 210.0),
    COLD_WELL("Cold Well / Salad Bar", 0.0, 41.0),
    WALK_IN_COOLER("Walk-in Cooler", 32.0, 41.0),
    WALK_IN_FREEZER("Walk-in Freezer", -10.0, 0.0),
    ICE_CREAM_FREEZER("Ice Cream Freezer", -10.0, 0.0),
    TRAYLINE_CART("Trayline Cart Departure", 140.0, 210.0);

    fun evaluateStatus(tempF: Double): Boolean {
        return tempF in defaultTargetMin..defaultTargetMax
    }
}
