package com.dietaryops.manager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dietaryops.manager.data.model.CatalogItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogItemDao {
    @Query("SELECT * FROM catalog_items WHERE syscoUpc = :upc")
    suspend fun getByUpc(upc: String): CatalogItem?

    @Query("SELECT * FROM catalog_items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): CatalogItem?

    @Query("SELECT * FROM catalog_items")
    fun getAllCatalogItems(): Flow<List<CatalogItem>>

    @Query("SELECT * FROM catalog_items")
    suspend fun getAllCatalogItemsList(): List<CatalogItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: CatalogItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CatalogItem>)

    @Query("DELETE FROM catalog_items WHERE syscoUpc = :upc")
    suspend fun deleteByUpc(upc: String)
}
