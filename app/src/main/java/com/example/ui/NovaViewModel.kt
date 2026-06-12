package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.InventoryItem
import com.example.data.InventoryRepository
import com.example.data.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NovaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = InventoryRepository(database.taskDao, database.inventoryDao)

    val tasksStream: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val routinesStream: StateFlow<List<com.example.data.Routine>> = database.routineDao.getAllRoutinesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val inventoryStream: StateFlow<List<InventoryItem>> = repository.allItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _ruleLogs = MutableStateFlow<List<String>>(emptyList())
    val ruleLogs: StateFlow<List<String>> = _ruleLogs.asStateFlow()

    private val _currentTab = MutableStateFlow("TASKS")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    init {
        refreshRuleLogs()
        viewModelScope.launch {
            // Trigger rule logic on start
            repository.runRulesForAll()
            refreshRuleLogs()
            // Automate dynamic launcher app discovery for on-device app matching and aliases
            com.example.AutomationEngine.discoverApps(application)

            // Prepopulate some premium default routines if empty
            try {
                val currentRoutines = database.routineDao.getAllRoutinesSync()
                if (currentRoutines.isEmpty()) {
                    database.routineDao.insertRoutine(com.example.data.Routine(
                        name = "College Mode",
                        triggerPhrase = "college mode",
                        actionsJson = com.example.data.Routine.serializeActions(listOf("Enable Do Not Disturb (DND)", "Open Google Chrome", "Set volume to 40%"))
                    ))
                    database.routineDao.insertRoutine(com.example.data.Routine(
                        name = "Study Mode",
                        triggerPhrase = "study mode",
                        actionsJson = com.example.data.Routine.serializeActions(listOf("Open Settings", "Set volume to 0%", "Open Google Chrome", "search studying tips"))
                    ))
                    database.routineDao.insertRoutine(com.example.data.Routine(
                        name = "Family Routine",
                        triggerPhrase = "family routine",
                        actionsJson = com.example.data.Routine.serializeActions(listOf("Call Mom", "open WhatsApp", "open YouTube"))
                    ))
                    database.routineDao.insertRoutine(com.example.data.Routine(
                        name = "Gaming Mode",
                        triggerPhrase = "gaming mode",
                        actionsJson = com.example.data.Routine.serializeActions(listOf("Enable Do Not Disturb (DND)", "open YouTube", "Set volume to 75%"))
                    ))
                    database.routineDao.insertRoutine(com.example.data.Routine(
                        name = "Night Protocol",
                        triggerPhrase = "night protocol",
                        actionsJson = com.example.data.Routine.serializeActions(listOf("Dim screen display to 5%", "Register wake alarm for 07:00 AM", "Enable Do Not Disturb (DND)"))
                    ))
                }
            } catch (e: Exception) {
                // Fail-safe
            }
        }
    }

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    fun refreshRuleLogs() {
        _ruleLogs.value = repository.ruleActivityLog.toList()
    }

    fun addTask(title: String, description: String, priority: String, category: String, dueDate: Long? = null) {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
                priority = priority,
                category = category,
                dueDate = dueDate,
                status = "PENDING"
            )
            repository.insertTask(task)
            refreshRuleLogs()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val newStatus = if (task.status == "COMPLETED") "PENDING" else "COMPLETED"
            repository.updateTaskStatus(task.id, newStatus)
            refreshRuleLogs()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            refreshRuleLogs()
        }
    }

    fun addInventoryItem(
        name: String,
        description: String,
        quantity: Int,
        minThreshold: Int,
        location: String,
        category: String,
        expiryDays: Int? = null
    ) {
        viewModelScope.launch {
            val expiryTime = expiryDays?.let {
                System.currentTimeMillis() + (it.toLong() * 24 * 60 * 60 * 1000)
            }
            val item = InventoryItem(
                name = name,
                description = description,
                quantity = quantity,
                minThreshold = minThreshold,
                location = location,
                category = category,
                expiryDate = expiryTime
            )
            repository.insertItem(item)
            refreshRuleLogs()
        }
    }

    fun updateStockLevel(item: InventoryItem, delta: Int) {
        viewModelScope.launch {
            val newQty = (item.quantity + delta).coerceAtLeast(0)
            repository.updateStock(item.id, newQty)
            refreshRuleLogs()
        }
    }

    fun deleteInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            refreshRuleLogs()
        }
    }

    fun forceEvaluateRules() {
        viewModelScope.launch {
            repository.runRulesForAll()
            refreshRuleLogs()
        }
    }

    fun insertRoutine(name: String, triggerPhrase: String, actions: List<String>) {
        viewModelScope.launch {
            val routine = com.example.data.Routine(
                name = name,
                triggerPhrase = triggerPhrase,
                actionsJson = com.example.data.Routine.serializeActions(actions)
            )
            database.routineDao.insertRoutine(routine)
        }
    }

    fun deleteRoutine(routineId: Int) {
        viewModelScope.launch {
            database.routineDao.deleteById(routineId)
        }
    }

    fun recordRoutineUsage(routineId: Int) {
        viewModelScope.launch {
            database.routineDao.incrementUsage(routineId, System.currentTimeMillis())
        }
    }
}
