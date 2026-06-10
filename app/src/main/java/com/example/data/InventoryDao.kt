package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllItemsFlow(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items")
    suspend fun getAllItemsSync(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getItemById(id: Int): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("UPDATE inventory_items SET quantity = :quantity, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateStock(id: Int, quantity: Int, timestamp: Long = System.currentTimeMillis())
}
