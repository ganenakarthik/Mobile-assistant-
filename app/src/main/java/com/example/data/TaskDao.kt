package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY status DESC, priority = 'CRITICAL' DESC, priority = 'HIGH' DESC, priority = 'MEDIUM' DESC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksSync(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Query("SELECT * FROM tasks WHERE linkedItemId = :itemId AND status = 'PENDING'")
    suspend fun getPendingTasksForItem(itemId: Int): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: Int, status: String)

    @Query("UPDATE tasks SET status = 'COMPLETED' WHERE linkedItemId = :itemId AND title LIKE :titlePattern AND status = 'PENDING'")
    suspend fun autocompleteTasksForItem(itemId: Int, titlePattern: String)
}
