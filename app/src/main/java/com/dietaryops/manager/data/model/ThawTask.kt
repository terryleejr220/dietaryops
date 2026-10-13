package com.dietaryops.manager.data.model

import java.util.UUID

enum class ThawTaskStatus(val displayName: String) {
    SCHEDULED("Scheduled"),
    IN_PREP("In Prep"),
    COMPLETED("Completed")
}

data class ThawTask(
    val id: String = UUID.randomUUID().toString(),
    val itemName: String,
    val batchQty: String,
    val station: String,
    val scheduledTime: String,
    val status: ThawTaskStatus = ThawTaskStatus.SCHEDULED,
    val assignedRole: String = "Cook",
    val upc: String = ""
) {
    companion object {
        fun defaultSampleTasks(): List<ThawTask> {
            return listOf(
                ThawTask(
                    itemName = "Ham Chubs (Pork)",
                    batchQty = "4 Chubs (20 lbs)",
                    station = "Cooler #2 (Thaw Shelf)",
                    scheduledTime = "8:00 AM",
                    status = ThawTaskStatus.IN_PREP,
                    assignedRole = "Cook"
                ),
                ThawTask(
                    itemName = "Chicken Breast Tenderloin",
                    batchQty = "2 Cases (40 lbs)",
                    station = "Thaw Sink / Cooler #1",
                    scheduledTime = "1:30 PM",
                    status = ThawTaskStatus.SCHEDULED,
                    assignedRole = "Cook"
                ),
                ThawTask(
                    itemName = "Ground Beef 80/20",
                    batchQty = "3 Cases (60 lbs)",
                    station = "Cooler #2 (Thaw Shelf)",
                    scheduledTime = "3:00 PM",
                    status = ThawTaskStatus.SCHEDULED,
                    assignedRole = "Receiver"
                )
            )
        }
    }
}
