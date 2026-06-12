package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val triggerPhrase: String,
    val actionsJson: String, // Serialize List<String> to JSON Array string
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    val usageCount: Int = 0
) {
    @Ignore
    fun getActions(): List<String> {
        val list = mutableListOf<String>()
        try {
            if (actionsJson.isNotBlank()) {
                val array = JSONArray(actionsJson)
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
            }
        } catch (e: Exception) {
            // Fallback for custom delimiter
            if (actionsJson.contains("|~|")) {
                list.addAll(actionsJson.split("|~|"))
            } else if (actionsJson.contains(",")) {
                list.addAll(actionsJson.split(","))
            } else {
                list.add(actionsJson)
            }
        }
        return list.map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        fun serializeActions(actions: List<String>): String {
            return JSONArray(actions).toString()
        }
    }
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun getAllRoutinesFlow(): Flow<List<Routine>>

    @Query("SELECT * FROM routines")
    suspend fun getAllRoutinesSync(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineById(id: Int): Routine?

    @Query("SELECT * FROM routines WHERE LOWER(triggerPhrase) = LOWER(:triggerPhrase) LIMIT 1")
    suspend fun getRoutineByTrigger(triggerPhrase: String): Routine?

    @Query("SELECT * FROM routines WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getRoutineByName(name: String): Routine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: Routine): Long

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE routines SET lastUsed = :lastUsed, usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Int, lastUsed: Long)
}
