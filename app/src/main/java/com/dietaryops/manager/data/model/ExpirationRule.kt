package com.dietaryops.manager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expiration_rules")
data class ExpirationRule(
    @PrimaryKey
    val category: String,
    val daysOffset: Int,
    val description: String = ""
)
