package com.centuryvilla.deliveryscanner.data.model

data class InventoryItem(
    val category: String,
    val name: String,
    var onHand: String,
    val previousCount: String = "",
    var isNew: Boolean = false
)
