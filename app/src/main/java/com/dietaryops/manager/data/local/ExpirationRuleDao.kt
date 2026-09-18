package com.dietaryops.manager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dietaryops.manager.data.model.ExpirationRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpirationRuleDao {
    @Query("SELECT * FROM expiration_rules")
    fun getAllRules(): Flow<List<ExpirationRule>>

    @Query("SELECT * FROM expiration_rules WHERE category = :category")
    suspend fun getRuleByCategory(category: String): ExpirationRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ExpirationRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ExpirationRule>)
}
