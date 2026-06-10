package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Room Entity for persisting chat conversations locally (IndexedDB alternative)
@Entity(tableName = "conversation_messages")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "USER", "NOVA", "SYSTEM"
    val text: String,
    val isPlaybackActive: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// 2. Room Entity for general key-value local storage representing browser's IndexedDB schema
// Allows storing structured/unstructured JSON data representing user preferences or knowledge
@Entity(tableName = "indexeddb_local_store")
data class IndexedDbStore(
    @PrimaryKey val storeKey: String, // e.g., "user_preferences", "nova_local_state", "trained_memory_parameters"
    val storeValueJson: String,       // JSON payload
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface ConversationMessageDao {
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ConversationMessage>>

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ConversationMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessage): Long

    @Query("DELETE FROM conversation_messages")
    suspend fun clearHistory()
}

@Dao
interface IndexedDbStoreDao {
    @Query("SELECT * FROM indexeddb_local_store WHERE storeKey = :key")
    suspend fun getByKey(key: String): IndexedDbStore?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: IndexedDbStore)

    @Query("DELETE FROM indexeddb_local_store WHERE storeKey = :key")
    suspend fun deleteByKey(key: String)
}
