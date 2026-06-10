package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val quantity: Int,
    val minThreshold: Int, // If quantity <= minThreshold, rule triggers a task creation
    val location: String = "Main Storage",
    val category: String = "General",
    val expiryDate: Long? = null, // If non-null, near dates trigger warnings
    val lastUpdated: Long = System.currentTimeMillis()
)
