package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val time: String,      // e.g. "8:00 PM"
    val date: String,      // e.g. "2026-06-10" or "Today" or "Tomorrow"
    val triggerTime: Long, // Target trigger time in Epoch milliseconds
    val status: String,    // "PENDING", "COMPLETED"
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY status DESC, triggerTime ASC")
    fun getAllRemindersFlow(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersSync(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Int): Reminder?

    @Query("SELECT * FROM reminders WHERE status = 'PENDING'")
    suspend fun getPendingReminders(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE reminders SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateReminderStatus(id: Int, status: String, completedAt: Long?)
}
